<template>
  <!-- 学生视图：课程作业列表 + 详情 + 真实提交（数据源 GET /tasks?courseId + GET /submissions） -->
  <section v-if="isStudent" class="tasks">
    <div class="course-tab-bar">
      <div>
        <p class="card-kicker">COURSE / TASKS</p>
        <h2>
          课程作业 <span class="chip chip-mute">{{ studentRows.length }} 项</span>
        </h2>
      </div>
      <div class="course-tab-actions">
        <button class="btn btn-ghost" :disabled="studentLoading" @click="loadStudent">刷新</button>
      </div>
    </div>

    <div v-if="studentError" class="callout danger">
      <span>ERROR / 加载失败</span>
      <p>{{ studentError }}</p>
      <button class="btn btn-sm" @click="loadStudent">重试</button>
    </div>

    <section class="card table-card">
      <div v-if="studentRows.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>作业</th>
              <th class="w-140">状态</th>
              <th class="w-220">截止时间</th>
              <th class="w-140">得分</th>
              <th class="w-140">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in studentRows" :key="row.taskId">
              <td>
                <b class="t-title">{{ row.title }}</b>
                <p v-if="row.description" class="t-sub">{{ excerpt(row.description) }}</p>
              </td>
              <td>
                <span class="chip" :class="row.chip">{{ row.statusLabel }}</span>
              </td>
              <td>{{ fmtDue(row.dueDate) }}</td>
              <td>{{ scoreText(row) }}</td>
              <td>
                <button class="text-btn" @click="openStudentDetail(row)">查看详情</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="studentLoading" class="empty-state compact"><b>正在加载作业…</b></div>
      <div v-else-if="!studentError" class="empty-state compact">
        <span class="e-ico"><V2Icon name="check" /></span>
        <b>本课程暂无已发布作业</b>
        <p>教师发布作业后会出现在这里。</p>
      </div>
      <div v-else class="empty-state compact"><b>作业列表未能加载</b></div>
    </section>

    <V2Modal
      v-model:open="studentDetailOpen"
      :title="studentDetail?.title || '作业详情'"
      kicker="TASK / DETAIL"
      width="680px"
    >
      <div v-if="studentDetailLoading" class="empty-state compact"><b>正在加载作业详情…</b></div>
      <template v-else-if="studentDetail">
        <p v-if="studentDetailError" class="callout danger">{{ studentDetailError }}</p>
        <p class="detail-meta">
          <span class="chip" :class="studentDetail.chip">{{ studentDetail.statusLabel }}</span>
          <span>截止 {{ fmtDue(studentDetail.dueDate) }}</span>
          <span v-if="studentDetail.maxScore != null">满分 {{ studentDetail.maxScore }}</span>
        </p>
        <p v-if="studentDetail.description" class="detail-body">{{ studentDetail.description }}</p>
        <p v-if="studentDetail.requirements" class="detail-body">
          <span class="detail-label">作业要求：</span>{{ studentDetail.requirements }}
        </p>
        <p v-if="studentDetail.attachmentUrl" class="detail-body">
          <span class="detail-label">作业附件：</span>
          <a
            v-if="safeUrl(studentDetail.attachmentUrl)"
            :href="safeUrl(studentDetail.attachmentUrl)"
            target="_blank"
            rel="noopener noreferrer"
            class="detail-link"
            >下载附件</a
          >
          <span v-else>附件地址不可用</span>
        </p>

        <div v-if="studentDetailSubmission" class="submission-box">
          <p class="detail-label">我的提交</p>
          <p class="detail-body">
            得分：<b>{{ studentDetailSubmission.score ?? '—' }}</b>
            <span v-if="studentDetailSubmission.maxScore != null">
              / {{ studentDetailSubmission.maxScore }}</span
            >
          </p>
          <p v-if="studentDetailSubmission.content" class="detail-body">
            <span class="detail-label">作答：</span>{{ studentDetailSubmission.content }}
          </p>
          <p v-if="studentDetailSubmission.fileUrls.length" class="detail-body">
            <span class="detail-label">附件：</span>
            <a
              v-for="(url, index) in studentDetailSubmission.fileUrls"
              :key="url"
              :href="safeUrl(url)"
              target="_blank"
              rel="noopener noreferrer"
              class="detail-link"
              >附件 {{ index + 1 }}</a
            >
          </p>
          <p v-if="studentDetailSubmission.feedback" class="detail-body">
            <span class="detail-label">教师评语：</span>{{ studentDetailSubmission.feedback }}
          </p>
        </div>

        <div v-if="canSubmitDetail" class="submit-box">
          <label class="field span-2">
            <span>作答内容</span>
            <textarea
              v-model="studentForm.content"
              class="textarea"
              rows="5"
              maxlength="50000"
              placeholder="填写你的作答内容…"
              :disabled="studentSubmitting"
            ></textarea>
          </label>
          <label class="field span-2">
            <span>附件（可选）</span>
            <input
              type="file"
              multiple
              class="input"
              :disabled="studentUploading || studentSubmitting"
              @change="onStudentFileChange"
            />
          </label>
          <div v-if="studentForm.fileUrls.length" class="uploaded-list">
            <span v-for="(url, index) in studentForm.fileUrls" :key="url" class="chip chip-mute">
              附件 {{ index + 1 }}
              <button type="button" class="text-btn" @click="removeStudentAttachment(url)">
                移除
              </button>
            </span>
          </div>
          <p v-if="studentUploading" class="detail-body">附件上传中…</p>
          <p v-if="studentFormError" class="form-error">{{ studentFormError }}</p>
        </div>
        <p v-else class="detail-body detail-hint">该作业当前不可再提交；如需补交请联系任课教师。</p>
      </template>
      <template #footer>
        <button class="btn btn-ghost" @click="studentDetailOpen = false">关闭</button>
        <button
          v-if="canSubmitDetail"
          class="btn btn-primary"
          :disabled="studentSubmitting || studentUploading"
          @click="submitStudent"
        >
          {{ studentSubmitting ? '提交中…' : '提交作业' }}
        </button>
      </template>
    </V2Modal>
  </section>

  <!-- 教师视图：任务管理（行为保持不变） -->
  <section v-else class="tasks">
    <div class="course-tab-bar">
      <div>
        <p class="card-kicker">COURSE / TASKS</p>
        <h2>
          课程任务 <span class="chip chip-mute">{{ total }} 项</span>
        </h2>
      </div>
      <div class="course-tab-actions">
        <button class="btn btn-ghost" :disabled="loading" @click="load">刷新</button>
        <button class="btn btn-primary" @click="openCreate">新建任务</button>
      </div>
    </div>

    <div v-if="loadError" class="callout">
      <span>ERROR / 加载失败</span>
      <p>{{ loadError }}</p>
    </div>

    <section class="card table-card">
      <div v-if="pagedTasks.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>标题</th>
              <th class="w-96">类型</th>
              <th class="w-96">发布状态</th>
              <th class="w-220">截止日期</th>
              <th class="w-140">关联章节</th>
              <th class="w-260">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in pagedTasks" :key="rowKey(row, index)">
              <td>
                <b class="t-title">{{ row.title }}</b>
              </td>
              <td>
                <span class="chip chip-info">{{ row.taskType || '—' }}</span>
              </td>
              <td>
                <span class="chip" :class="row.isPublished ? 'chip-ok' : 'chip-mute'">
                  {{ row.isPublished ? '已发布' : '草稿' }}
                </span>
              </td>
              <td>{{ fmtDue(row.dueDate) }}</td>
              <td>{{ chapterLabel(row.chapterId) }}</td>
              <td>
                <div class="row-actions">
                  <button class="text-btn" @click="togglePublish(row)">
                    {{ row.isPublished ? '取消发布' : '发布' }}
                  </button>
                  <button class="text-btn" @click="openEdit(row)">编辑</button>
                  <button class="text-btn" @click="openExtend(row)">延长</button>
                  <button class="text-btn danger" @click="askRemove(row)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载任务…</b></div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><V2Icon name="check" /></span>
        <b>{{ loadError ? '任务列表未能加载' : '暂无任务' }}</b>
        <p>{{ loadError ? '请查看上方提示后重试。' : '点击右上角「新建任务」发布第一个任务。' }}</p>
      </div>

      <footer v-if="tasks.length" class="card-foot">
        <span>第 {{ page }} / {{ pageCount }} 页 · 共 {{ total }} 项</span>
        <div class="v2-pager">
          <select v-model.number="size" class="input page-size" aria-label="每页条数">
            <option :value="10">10 / 页</option>
            <option :value="20">20 / 页</option>
            <option :value="50">50 / 页</option>
          </select>
          <button class="btn btn-sm" :disabled="page <= 1" @click="go(-1)">上一页</button>
          <button class="btn btn-sm" :disabled="page >= pageCount" @click="go(1)">下一页</button>
        </div>
      </footer>
    </section>

    <!-- 新建 / 编辑 -->
    <V2Modal
      v-model:open="dialogOpen"
      :title="editingId ? '编辑任务' : '新建任务'"
      kicker="TASK / FORM"
      width="600px"
    >
      <div class="form-grid">
        <label class="field span-2">
          <span>标题 *</span>
          <input v-model="form.title" class="input" maxlength="80" placeholder="请输入任务标题" />
        </label>
        <label class="field">
          <span>类型</span>
          <select v-model="form.taskType" class="input">
            <option v-for="type in TASK_TYPES" :key="type" :value="type">{{ type }}</option>
          </select>
        </label>
        <label class="field">
          <span>截止日期</span>
          <input v-model="form.dueDate" class="input" type="datetime-local" />
        </label>
        <label class="field span-2">
          <span>关联章节</span>
          <select v-model="form.chapterId" class="input">
            <option :value="null">未关联</option>
            <option v-for="chapter in chapters" :key="chapter.id" :value="chapter.id">
              {{ chapter.title || `章节 #${chapter.id}` }}
            </option>
          </select>
        </label>
        <label class="field span-2">
          <span>描述</span>
          <textarea
            v-model="form.description"
            class="textarea"
            rows="3"
            placeholder="任务说明（可选）"
          ></textarea>
        </label>
      </div>
      <p v-if="formError" class="form-error">{{ formError }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="dialogOpen = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : editingId ? '保存' : '创建' }}
        </button>
      </template>
    </V2Modal>

    <!-- 延长截止 -->
    <V2Modal v-model:open="extendOpen" title="延长截止日期" kicker="TASK / EXTEND" width="460px">
      <div class="form-grid">
        <label class="field span-2">
          <span>新截止时间 *</span>
          <input v-model="extendForm.newDueDate" class="input" type="datetime-local" />
        </label>
        <label class="field span-2">
          <span>原因</span>
          <textarea
            v-model="extendForm.reason"
            class="textarea"
            rows="3"
            placeholder="延长截止的原因（可选）"
          ></textarea>
        </label>
      </div>
      <p v-if="extendError" class="form-error">{{ extendError }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="extendOpen = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="saveExtend">确定延长</button>
      </template>
    </V2Modal>

    <!-- 删除确认 -->
    <V2Modal v-model:open="confirm.open" title="删除任务" kicker="CONFIRM / DELETE" width="460px">
      <p class="modal-text">{{ confirmText }}</p>
      <p class="modal-hint">将一并移除该任务的附件、提交记录与学生成绩，且无法恢复。</p>
      <template #footer>
        <button class="btn btn-ghost" @click="confirm.open = false">取消</button>
        <button class="btn btn-danger" :disabled="confirm.busy" @click="runRemove">确定删除</button>
      </template>
    </V2Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import V2Modal from '../../../components/V2Modal.vue'
import V2Icon from '../../../components/V2Icon.vue'
import { useV2Ui } from '../../../composables/ui'
import type { V2Role } from '../../../nav'
import taskApi from '@/api/task'
import chapterApi from '@/api/chapter'
import submissionApi from '@/api/submission'
import studentApi from '@/api/student'
import commonApi from '@/api/common'
import { unwrap } from '@/utils/response'
import logger from '@/utils/logger'

interface TaskRow {
  id: number
  title?: string
  taskType?: string
  description?: string
  dueDate?: string
  chapterId?: number | null
  isPublished?: boolean
  [key: string]: unknown
}

const TASK_TYPES = ['视频', '测验', '作业', '文档', '讨论']

const props = withDefaults(defineProps<{ courseId: number; role?: V2Role }>(), { role: 'teacher' })
const { toast } = useV2Ui()

const isStudent = computed(() => props.role === 'student')

/* ==================== 学生作业（真实接口） ==================== */

type StudentStatus =
  'NOT_SUBMITTED' | 'OVERDUE' | 'SUBMITTED' | 'RESUBMITTED' | 'GRADED' | 'RETURNED'

const STUDENT_STATUS_LABEL: Record<StudentStatus, string> = {
  NOT_SUBMITTED: '待完成',
  OVERDUE: '逾期',
  SUBMITTED: '已提交待批改',
  RESUBMITTED: '重新提交待批改',
  GRADED: '已完成',
  RETURNED: '已退回待重交',
}

const STUDENT_STATUS_CHIP: Record<StudentStatus, string> = {
  NOT_SUBMITTED: 'chip-warn',
  OVERDUE: 'chip-danger',
  SUBMITTED: 'chip-info',
  RESUBMITTED: 'chip-info',
  GRADED: 'chip-ok',
  RETURNED: 'chip-danger',
}

/** 后端 Submission.status 的可识别值；未提交由前端合成 NOT_SUBMITTED。 */
const SUBMISSION_STATUSES: StudentStatus[] = ['SUBMITTED', 'RESUBMITTED', 'GRADED', 'RETURNED']

interface StudentSubmissionView {
  id: number
  status: string
  score: number | null
  maxScore: number | null
  feedback: string | null
  content: string | null
  fileUrls: string[]
  submittedAt: string | null
}

interface StudentAssignmentRow {
  taskId: number
  title: string
  taskType: string
  description: string
  requirements: string
  attachmentUrl: string
  dueDate: string
  maxScore: number | null
  status: StudentStatus
  statusLabel: string
  chip: string
  submission: StudentSubmissionView | null
}

const studentRows = ref<StudentAssignmentRow[]>([])
const studentLoading = ref(false)
const studentError = ref('')

const studentDetailOpen = ref(false)
const studentDetailLoading = ref(false)
const studentDetailError = ref('')
const studentDetail = ref<StudentAssignmentRow | null>(null)
const studentDetailSubmission = ref<StudentSubmissionView | null>(null)
const studentForm = reactive({ content: '', fileUrls: [] as string[] })
const studentFormError = ref('')
const studentSubmitting = ref(false)
const studentUploading = ref(false)

/** 旧端 useStudentAssignments 同口径：fileUrls 可能是 JSON 数组串 / 逗号分隔 / 数组 */
function normalizeAttachmentUrls(value: unknown): string[] {
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

function toStudentSubmission(raw: Record<string, unknown> | null): StudentSubmissionView | null {
  if (!raw) return null
  return {
    id: Number(raw.id),
    status: String(raw.status ?? '').toUpperCase(),
    score: raw.score == null ? null : Number(raw.score),
    maxScore: raw.maxScore == null ? null : Number(raw.maxScore),
    feedback: (raw.feedback as string | null) ?? null,
    content: (raw.content as string | null) ?? null,
    fileUrls: normalizeAttachmentUrls(raw.fileUrls),
    submittedAt: (raw.submittedAt as string | null) ?? null,
  }
}

function latestSubmissionOf(
  rows: Record<string, unknown>[],
  taskId: number,
): Record<string, unknown> | null {
  const matched = rows.filter((row) => String(row.taskId ?? '') === String(taskId))
  if (!matched.length) return null
  return matched.reduce((latest, row) =>
    Number(row.attemptNumber ?? 0) >= Number(latest.attemptNumber ?? 0) ? row : latest,
  )
}

function isOverdue(dueDate: string): boolean {
  if (!dueDate) return false
  const time = new Date(dueDate.replace(' ', 'T')).getTime()
  return Number.isFinite(time) && time < Date.now()
}

function toStudentRow(
  task: Record<string, unknown>,
  submissionRaw: Record<string, unknown> | null,
): StudentAssignmentRow {
  const taskId = Number(task.id)
  const submission = toStudentSubmission(submissionRaw)
  const rawStatus = submission?.status || ''
  let status: StudentStatus = 'NOT_SUBMITTED'
  if (SUBMISSION_STATUSES.includes(rawStatus as StudentStatus)) {
    status = rawStatus as StudentStatus
  }
  const dueDate = String(task.dueDate ?? task.deadline ?? '')
  if (status === 'NOT_SUBMITTED' && isOverdue(dueDate)) status = 'OVERDUE'
  return {
    taskId,
    title: String(task.title ?? `作业 #${taskId}`),
    taskType: String(task.taskType ?? ''),
    description: String(task.description ?? ''),
    requirements: String(task.requirements ?? ''),
    attachmentUrl: String(task.attachmentUrl ?? ''),
    dueDate,
    maxScore: task.maxScore == null ? null : Number(task.maxScore),
    status,
    statusLabel: STUDENT_STATUS_LABEL[status],
    chip: STUDENT_STATUS_CHIP[status],
    submission,
  }
}

async function loadStudent() {
  if (!props.courseId) {
    studentRows.value = []
    return
  }
  studentLoading.value = true
  studentError.value = ''
  try {
    // 任务列表与我的提交分开取：旧端 CduAssignmentTab 同口径（task + submission 聚合出状态）
    const [taskRows, submissions] = await Promise.all([
      taskApi.getAllTasks({ courseId: props.courseId }),
      submissionApi.getAllMySubmissions(),
    ])
    const taskList = (Array.isArray(taskRows) ? taskRows : []) as Record<string, unknown>[]
    const submissionList = (Array.isArray(submissions) ? submissions : []) as Record<
      string,
      unknown
    >[]
    studentRows.value = taskList
      // 仅展示已发布任务；isPublished 缺失时按发布处理（与旧端一致）
      .filter((task) => task.isPublished !== false)
      .map((task) => toStudentRow(task, latestSubmissionOf(submissionList, Number(task.id))))
      .sort((a, b) => {
        const rank = (row: StudentAssignmentRow) =>
          row.status === 'GRADED' || row.status === 'SUBMITTED' ? 1 : 0
        const byRank = rank(a) - rank(b)
        if (byRank !== 0) return byRank
        return String(a.dueDate).localeCompare(String(b.dueDate))
      })
  } catch (error) {
    logger.error('加载学生作业列表失败', error)
    studentRows.value = []
    studentError.value = '作业列表加载失败，请检查网络后重试'
  } finally {
    studentLoading.value = false
  }
}

function excerpt(value: string): string {
  const text = String(value || '')
    .replace(/\s+/g, ' ')
    .trim()
  return text.length > 60 ? `${text.slice(0, 60)}…` : text
}

function scoreText(row: StudentAssignmentRow): string {
  const score = row.submission?.score
  if (score == null) return '—'
  const max = row.submission?.maxScore ?? row.maxScore
  return max == null ? String(score) : `${score} / ${max}`
}

/** 附件链接安全化：仅放行 http(s)，非法值返回空串（与旧端同口径） */
function safeUrl(value?: string): string {
  if (typeof value !== 'string' || !value.trim()) return ''
  try {
    const url = new URL(value.trim(), window.location.origin)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : ''
  } catch {
    return ''
  }
}

const canSubmitDetail = computed(() => {
  const status = studentDetail.value?.status
  // 逾期仍可提交：后端按「允许迟交但计逾期扣分」处理（SubmissionWriteComponent）
  return status === 'NOT_SUBMITTED' || status === 'OVERDUE' || status === 'RETURNED'
})

async function fetchStudentDetail(taskId: number) {
  const response = await studentApi.getAssignmentDetail(taskId)
  return unwrap<{ assignment?: Record<string, unknown>; submission?: Record<string, unknown> }>(
    response,
  )
}

async function openStudentDetail(row: StudentAssignmentRow) {
  studentDetail.value = { ...row }
  studentDetailSubmission.value = row.submission
  studentDetailError.value = ''
  studentFormError.value = ''
  studentForm.content = row.submission?.content ?? ''
  studentForm.fileUrls = []
  studentDetailOpen.value = true
  studentDetailLoading.value = true
  try {
    const body = await fetchStudentDetail(row.taskId)
    if (body?.assignment) {
      // 详情接口返回任务实体 + 当前学生提交（AssignmentDetailVO）
      const merged = toStudentRow(body.assignment, body.submission ?? null)
      Object.assign(studentDetail.value, merged)
      studentDetailSubmission.value = merged.submission
    }
  } catch (error) {
    logger.error('加载作业详情失败', error)
    studentDetailError.value = '作业详情加载失败；以上信息来自列表，可关闭后重试。'
  } finally {
    studentDetailLoading.value = false
  }
}

async function onStudentFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  const files = input.files ? Array.from(input.files) : []
  input.value = ''
  if (!files.length) return
  studentUploading.value = true
  studentFormError.value = ''
  try {
    for (const file of files) {
      const res = await commonApi.uploadFile(file)
      const data = unwrap<Record<string, string> | string>(res)
      const url = typeof data === 'string' ? data : String(data?.url || data?.fileUrl || '')
      if (url) studentForm.fileUrls.push(url)
    }
  } catch (error) {
    logger.error('附件上传失败', error)
    studentFormError.value = '附件上传失败，请重试（已上传的附件仍保留）'
  } finally {
    studentUploading.value = false
  }
}

function removeStudentAttachment(url: string) {
  studentForm.fileUrls = studentForm.fileUrls.filter((item) => item !== url)
}

async function submitStudent() {
  const detail = studentDetail.value
  if (!detail || studentSubmitting.value) return
  const content = studentForm.content.trim()
  if (!content && !studentForm.fileUrls.length) {
    studentFormError.value = '请填写作答内容或上传附件'
    return
  }
  studentSubmitting.value = true
  studentFormError.value = ''
  try {
    // 旧端 CduAssignmentTab 同口径：POST /submissions 白名单 DTO —— { taskId, content, fileUrls }
    await submissionApi.submitTask(detail.taskId, {
      content,
      fileUrls: [...studentForm.fileUrls],
    })
    toast('提交成功，等待教师批改', 'ok')
    await loadStudent()
    const refreshed = studentRows.value.find((row) => row.taskId === detail.taskId)
    if (refreshed) {
      studentDetail.value = { ...refreshed }
      studentDetailSubmission.value = refreshed.submission
      studentForm.content = refreshed.submission?.content ?? ''
      studentForm.fileUrls = []
    }
    try {
      const body = await fetchStudentDetail(detail.taskId)
      if (body?.assignment) {
        const merged = toStudentRow(body.assignment, body.submission ?? null)
        Object.assign(studentDetail.value, merged)
        studentDetailSubmission.value = merged.submission
      }
    } catch (error) {
      logger.warn('提交后刷新作业详情失败', error)
    }
  } catch (error) {
    logger.error('作业提交失败', error)
    // 失败保留表单与已上传附件，允许直接重试
    studentFormError.value = '提交失败，请稍后重试（已保留填写内容）'
  } finally {
    studentSubmitting.value = false
  }
}

/* ==================== 教师任务管理（行为不变） ==================== */

const tasks = ref<TaskRow[]>([])
const chapters = ref<{ id: number; title?: string }[]>([])
const loading = ref(false)
const loadError = ref('')
const page = ref(1)
const size = ref(10)

const dialogOpen = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)
const formError = ref('')
const form = reactive({
  title: '',
  taskType: '作业',
  description: '',
  dueDate: '',
  chapterId: null as number | null,
})

const extendOpen = ref(false)
const extendId = ref<number | null>(null)
const extendError = ref('')
const extendForm = reactive({ newDueDate: '', reason: '' })

const confirm = reactive({ open: false, row: null as TaskRow | null, busy: false })

const total = computed(() => tasks.value.length)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const pagedTasks = computed(() =>
  tasks.value.slice((page.value - 1) * size.value, page.value * size.value),
)
const confirmText = computed(() => `确定删除任务「${confirm.row?.title || ''}」？`)

function rowKey(row: { id?: number }, index: number): string {
  return String(row.id ?? index)
}

function chapterLabel(chapterId?: number | null): string {
  if (!chapterId) return '未关联'
  const chapter = chapters.value.find((item) => Number(item.id) === Number(chapterId))
  return chapter?.title ? chapter.title : `章节 #${chapterId}`
}

/** datetime-local 给的是 YYYY-MM-DDTHH:mm，后端契约要 YYYY-MM-DD HH:mm:ss */
function toBackendDate(value: string): string | null {
  if (!value) return null
  const normalized = value.replace('T', ' ')
  return normalized.length === 16 ? `${normalized}:00` : normalized
}

/** extend-deadline 走 LocalDateTime.parse，只收 ISO-8601（空格格式会被判 400） */
function toIsoDateTime(value: string): string | null {
  if (!value) return null
  return value.length === 16 ? `${value}:00` : value
}

function fmtDue(value?: string): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

async function load() {
  if (!props.courseId) return
  loading.value = true
  loadError.value = ''
  try {
    const response = await taskApi.getAllTasks({ courseId: props.courseId })
    tasks.value = Array.isArray(response) ? (response as TaskRow[]) : []
    if (page.value > pageCount.value) page.value = 1
  } catch (error) {
    logger.error('加载任务列表失败', error)
    tasks.value = []
    loadError.value = '任务列表加载失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

async function loadChapters() {
  if (!props.courseId) return
  try {
    const body = unwrap(await chapterApi.getChapters(props.courseId))
    const data = body as {
      list?: { id: number; title?: string }[]
      records?: { id: number; title?: string }[]
    }
    const rows = Array.isArray(body) ? body : data?.list || data?.records || []
    chapters.value = rows as { id: number; title?: string }[]
  } catch (error) {
    logger.error('加载章节列表失败', error)
    chapters.value = []
  }
}

function go(step: number) {
  page.value = Math.min(pageCount.value, Math.max(1, page.value + step))
}

function resetForm() {
  form.title = ''
  form.taskType = '作业'
  form.description = ''
  form.dueDate = ''
  form.chapterId = null
  editingId.value = null
}

function openCreate() {
  formError.value = ''
  dialogMode.value = 'create'
  resetForm()
  dialogOpen.value = true
}

function openEdit(row: TaskRow) {
  formError.value = ''
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.title = row.title || ''
  form.taskType = row.taskType || '作业'
  form.description = row.description || ''
  form.dueDate = (row.dueDate || '').slice(0, 16).replace(' ', 'T')
  form.chapterId = row.chapterId || null
  dialogOpen.value = true
}

async function save() {
  if (!form.title.trim()) {
    formError.value = '请填写任务标题'
    return
  }
  saving.value = true
  try {
    const payload = {
      courseId: props.courseId,
      title: form.title.trim(),
      taskType: form.taskType,
      description: form.description,
      dueDate: toBackendDate(form.dueDate),
      chapterId: form.chapterId || null,
    }
    if (dialogMode.value === 'create') {
      await taskApi.createTask(payload)
      toast('任务已创建', 'ok')
    } else {
      await taskApi.updateTask(editingId.value as number, payload)
      toast('任务已更新', 'ok')
    }
    dialogOpen.value = false
    await load()
  } catch (error) {
    formError.value = '保存失败，请稍后重试'
    logger.error('保存任务失败', error)
  } finally {
    saving.value = false
  }
}

async function togglePublish(row: TaskRow) {
  try {
    await taskApi.publishTask(row.id)
    toast(row.isPublished ? '已取消发布' : '已发布', 'ok')
    await load()
  } catch (error) {
    toast('切换发布状态失败', 'danger')
    logger.error('切换发布状态失败', error)
  }
}

function openExtend(row: TaskRow) {
  extendError.value = ''
  extendId.value = row.id
  extendForm.newDueDate = ''
  extendForm.reason = ''
  extendOpen.value = true
}

async function saveExtend() {
  if (!extendForm.newDueDate) {
    extendError.value = '请选择新的截止日期'
    return
  }
  saving.value = true
  try {
    await taskApi.extendDeadline(extendId.value as number, {
      newDueDate: toIsoDateTime(extendForm.newDueDate) as string,
      reason: extendForm.reason,
    })
    toast('截止日期已延长', 'ok')
    extendOpen.value = false
    await load()
  } catch (error) {
    extendError.value = '延长失败，请稍后重试'
    logger.error('延长截止失败', error)
  } finally {
    saving.value = false
  }
}

function askRemove(row: TaskRow) {
  confirm.row = row
  confirm.open = true
}

async function runRemove() {
  if (!confirm.row) return
  confirm.busy = true
  try {
    await taskApi.deleteTask(confirm.row.id)
    toast('已删除', 'ok')
    confirm.open = false
    confirm.row = null
    await load()
  } catch (error) {
    toast('删除失败', 'danger')
    logger.error('删除任务失败', error)
  } finally {
    confirm.busy = false
  }
}

watch(
  [() => props.courseId, isStudent],
  () => {
    page.value = 1
    if (isStudent.value) {
      void loadStudent()
    } else {
      void load()
      void loadChapters()
    }
  },
  { immediate: true },
)
</script>

<style scoped>
.tasks {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.t-title {
  display: block;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 550;
}
.t-sub {
  margin: 3px 0 0;
  max-width: 360px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 10px;
  color: var(--ink-3);
}
.page-size {
  height: 30px;
  width: auto;
  font-size: 11px;
}
.detail-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin: 0 0 10px;
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--ink-3);
}
.callout.danger {
  border-left-color: var(--danger);
}
.callout.danger > span {
  color: var(--danger);
}
.detail-body {
  margin: 6px 0;
  font-size: 12px;
  line-height: 1.8;
  color: var(--ink-2);
}
.detail-label {
  font-weight: 600;
  color: var(--ink-2);
}
.detail-hint {
  color: var(--ink-3);
}
.detail-link {
  margin-right: 10px;
  color: var(--accent);
  text-decoration: underline;
  text-underline-offset: 2px;
}
.submission-box {
  margin: 12px 0;
  padding: 10px 12px;
  border: 1px dashed var(--line-strong);
  border-radius: 8px;
  background: var(--surface-2);
}
.submit-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 14px;
}
.uploaded-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
