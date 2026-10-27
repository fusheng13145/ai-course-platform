import { computed, reactive, ref } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import taskApi from '@/api/task'
import submissionApi from '@/api/submission'
import commonApi from '@/api/common'
import { unwrap } from '@/utils/response'

/**
 * 学生端作业域 composable（S1 提交入口 + S2 成绩/评语展示，最小可用面）。
 *
 * 数据流：
 * - 任务列表：taskApi.getAllTasks({ courseId }) → 仅展示已发布任务；
 * - 我的提交：submissionApi.getAllMySubmissions() → 按 taskId 取最新一次 attempt 分组；
 * - 提交：submissionApi.submitTask(taskId, { content, fileUrls })（后端 POST /submissions 白名单 DTO）；
 * - 附件：commonApi.uploadFile 逐个上传 → 收集返回的 url 列表。
 *
 * 状态契约与教师端共用 Submission.status 枚举 name；退回（RETURNED）后允许「重新提交」。
 */

/** 学生视角的作业行（任务 + 我的最新提交 + 派生状态） */
export interface StudentAssignmentRow {
  taskId: number
  title: string
  description?: string | null
  dueDate?: string | null
  maxScore?: number | null
  /** 派生提交状态（NOT_SUBMITTED 为前端合成值，其余与后端 Submission.status 一致） */
  status: 'NOT_SUBMITTED' | 'SUBMITTED' | 'RESUBMITTED' | 'GRADED' | 'RETURNED'
  statusLabel: string
  /** 我的最新提交（未提交为 null） */
  submission: {
    id: number
    status: string
    score?: number | null
    maxScore?: number | null
    feedback?: string | null
    content?: string | null
    fileUrls: string[]
    submittedAt?: string | null
  } | null
}

export const STUDENT_ASSIGNMENT_STATUS_LABELS: Record<StudentAssignmentRow['status'], string> = {
  NOT_SUBMITTED: '未提交',
  SUBMITTED: '待批改',
  RESUBMITTED: '重新提交待批改',
  GRADED: '已批改',
  RETURNED: '已退回',
}

/** 允许发起提交动作的状态：未提交或被退回 */
export function canSubmitStatus(status: StudentAssignmentRow['status']): boolean {
  return status === 'NOT_SUBMITTED' || status === 'RETURNED'
}

export interface UseStudentAssignmentsOptions {
  courseId: Ref<string | number | null>
  onSubmitted?: () => void | Promise<void>
}

/** 归一化附件字段：兼容 JSON 数组字符串 / 逗号分隔 / 数组（与教师端 normalizeFileUrls 同口径） */
export function normalizeAttachmentUrls(value: unknown): string[] {
  if (Array.isArray(value))
    return value.filter((item): item is string => typeof item === 'string' && item.trim() !== '')
  if (typeof value !== 'string' || !value.trim()) return []
  const raw = value.trim()
  try {
    const parsed: unknown = JSON.parse(raw)
    if (Array.isArray(parsed))
      return parsed.filter((item): item is string => typeof item === 'string' && item.trim() !== '')
  } catch {
    // 历史数据为逗号分隔 URL，非 JSON
  }
  return raw
    .split(',')
    .map((url) => url.trim())
    .filter(Boolean)
}

/**
 * 学生作业域逻辑：任务+提交聚合、提交弹层、附件上传、提交动作。
 */
export function useStudentAssignments(options: UseStudentAssignmentsOptions) {
  const { courseId, onSubmitted } = options

  const loading = ref(false)
  const loadError = ref('')
  const tasks = ref<Array<Record<string, unknown>>>([])
  const mySubmissions = ref<Array<Record<string, unknown>>>([])

  /** 按 taskId 取最新一次提交（后端 attemptNumber 递增、最新一次为准） */
  function latestSubmissionByTask(taskId: number): Record<string, unknown> | null {
    const rows = mySubmissions.value.filter((row) => String(row.taskId ?? '') === String(taskId))
    if (!rows.length) return null
    return rows.reduce((latest, row) => {
      const a = Number(row.attemptNumber ?? 0)
      const b = Number(latest.attemptNumber ?? 0)
      return a >= b ? row : latest
    })
  }

  function toRow(task: Record<string, unknown>): StudentAssignmentRow {
    const taskId = Number(task.id)
    const submission = latestSubmissionByTask(taskId)
    const rawStatus = String((submission?.status as string) || '').toUpperCase()
    const status = (
      submission && rawStatus in STUDENT_ASSIGNMENT_STATUS_LABELS ? rawStatus : 'NOT_SUBMITTED'
    ) as StudentAssignmentRow['status']
    return {
      taskId,
      title: String(task.title ?? `任务 #${taskId}`),
      description: (task.description as string | null) ?? null,
      dueDate: (task.dueDate ?? task.deadline ?? null) as string | null,
      maxScore: (task.maxScore ?? task.totalScore ?? null) as number | null,
      status,
      statusLabel: STUDENT_ASSIGNMENT_STATUS_LABELS[status],
      submission: submission
        ? {
            id: Number(submission.id),
            status: rawStatus,
            score: (submission.score ?? null) as number | null,
            maxScore: (submission.maxScore ?? task.maxScore ?? null) as number | null,
            feedback: (submission.feedback ?? null) as string | null,
            content: (submission.content ?? null) as string | null,
            fileUrls: normalizeAttachmentUrls(submission.fileUrls),
            submittedAt: (submission.submittedAt ?? null) as string | null,
          }
        : null,
    }
  }

  const rows: ComputedRef<StudentAssignmentRow[]> = computed(() =>
    // 仅展示已发布任务；isPublished 缺失时按发布处理（兼容历史数据）
    tasks.value.filter((task) => task.isPublished !== false).map((task) => toRow(task)),
  )

  /** 加载任务列表 + 我的全部提交 */
  async function load(): Promise<void> {
    const cid = courseId.value
    if (cid == null || `${cid}` === '' || Number.isNaN(Number(cid))) {
      loadError.value = '课程信息无效，无法加载作业任务。'
      return
    }
    loading.value = true
    loadError.value = ''
    try {
      const [taskRows, submissions] = await Promise.all([
        taskApi.getAllTasks({ courseId: Number(cid) }),
        submissionApi.getAllMySubmissions(),
      ])
      tasks.value = (Array.isArray(taskRows) ? taskRows : []) as Array<Record<string, unknown>>
      mySubmissions.value = (Array.isArray(submissions) ? submissions : []) as Array<
        Record<string, unknown>
      >
    } catch (error) {
      logger.error('学生作业任务加载失败', error)
      tasks.value = []
      mySubmissions.value = []
      loadError.value = '作业任务加载失败，请刷新页面重试。'
    } finally {
      loading.value = false
    }
  }

  // ---- 提交弹层 ----
  const submitModal = reactive({
    visible: false,
    task: null as StudentAssignmentRow | null,
    form: { content: '', fileUrls: [] as string[] },
  })
  const submitting = ref(false)
  const uploading = ref(false)

  function openSubmit(row: StudentAssignmentRow): void {
    submitModal.task = row
    submitModal.form.content = row.submission?.content ?? ''
    submitModal.form.fileUrls = []
    submitModal.visible = true
  }

  function closeSubmit(): void {
    submitModal.visible = false
    submitModal.task = null
    submitModal.form.content = ''
    submitModal.form.fileUrls = []
  }

  /** 附件上传：逐个调 /common/upload，收集 url；失败项跳过并提示 */
  async function uploadFiles(files: FileList | File[]): Promise<void> {
    const list = Array.from(files)
    if (!list.length) return
    uploading.value = true
    try {
      for (const file of list) {
        const res = await commonApi.uploadFile(file)
        const data = unwrap<Record<string, string> | string>(res)
        const url =
          typeof data === 'string' ? data : (data?.url as string) || (data?.fileUrl as string) || ''
        if (url) submitModal.form.fileUrls.push(url)
      }
      ElMessage.success('附件上传成功')
    } catch (error) {
      logger.error('附件上传失败', error)
      ElMessage.error('附件上传失败，请重试')
    } finally {
      uploading.value = false
    }
  }

  function removeAttachment(url: string): void {
    submitModal.form.fileUrls = submitModal.form.fileUrls.filter((item) => item !== url)
  }

  /**
   * 提交作业：content 与附件至少其一非空 → POST /submissions（白名单 DTO）。
   * @returns 是否提交成功
   */
  async function submitAssignment(): Promise<boolean> {
    const task = submitModal.task
    if (!task || submitting.value) return false
    const content = submitModal.form.content.trim()
    if (!content && submitModal.form.fileUrls.length === 0) {
      ElMessage.warning('请填写作答内容或上传附件')
      return false
    }
    submitting.value = true
    try {
      await submissionApi.submitTask(task.taskId, {
        content,
        fileUrls: submitModal.form.fileUrls,
      })
      ElMessage.success('提交成功，等待教师批改')
      closeSubmit()
      await load()
      await onSubmitted?.()
      return true
    } catch (error) {
      logger.error('作业提交失败', error)
      ElMessage.error('提交失败，请稍后重试')
      return false
    } finally {
      submitting.value = false
    }
  }

  return {
    loading,
    loadError,
    rows,
    load,
    submitModal,
    submitting,
    uploading,
    openSubmit,
    closeSubmit,
    uploadFiles,
    removeAttachment,
    submitAssignment,
  }
}
