import { computed, reactive, ref } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import { overlayHost } from '@/bootstrap/overlayHost'
import logger from '@/utils/logger'
import submissionApi from '@/api/submission'
import { unwrap } from '@/utils/response'

/**
 * 批阅域 composable（FE-14：编排层留 .vue，域逻辑进这里）。
 *
 * 职责：
 * - 提交列表状态筛选（待批集合 = SUBMITTED + RESUBMITTED，禁魔法字符串）；
 * - 批改流：openGrade → [AI 预批 runAiPreview → 建议区展示 → applyAiSuggestion 回填] → submitGrade；
 * - 退回流：openReturn（reason 必填）→ submitReturn。
 *
 * AI 建议契约（后端 F2）：aiGrade 只把建议写入 aiFeedback（评语文本）与
 * submissionData JSON（aiSuggestedScore / aiHighlights / aiSuggestions），不动 score/status。
 * 前端 parse 失败一律静默降级为「无建议」，不阻塞批改表单；教师最终确认走 POST /grade。
 */

/** Submission.status 契约（与后端枚举 name 一致） */
export type SubmissionStatus = 'SUBMITTED' | 'GRADED' | 'RETURNED' | 'RESUBMITTED'

/** 状态筛选值 */
export type GradingFilter = 'ALL' | 'PENDING' | 'GRADED' | 'RETURNED'

/** 提交记录（entity/Submission.java 的前端视角，字段按需收窄） */
export interface Submission {
  id: number
  taskId: number
  studentId: number
  studentName?: string | null
  studentNo?: string | null
  content?: string | null
  fileUrls?: string[] | string | null
  status: string
  score?: number | null
  maxScore?: number | null
  feedback?: string | null
  aiFeedback?: string | null
  submissionData?: string | null
  attemptNumber?: number | null
  isLate?: boolean | null
  submittedAt?: string | null
  gradedAt?: string | null
  returnedAt?: string | null
  [key: string]: unknown
}

/** 待批集合 = SUBMITTED + RESUBMITTED（状态契约钉死处） */
export const PENDING_STATUSES: readonly string[] = ['SUBMITTED', 'RESUBMITTED']

/** 状态 → 中文标签（展示唯一出口） */
export const SUBMISSION_STATUS_LABELS: Record<string, string> = {
  SUBMITTED: '待批改',
  RESUBMITTED: '重新提交待批改',
  GRADED: '已批改',
  RETURNED: '已退回',
}

/** 状态 → 标签色 class（与现有 tag 色板对齐：green/amber/red） */
export const SUBMISSION_STATUS_CLASSES: Record<string, string> = {
  SUBMITTED: 'amber',
  RESUBMITTED: 'amber',
  GRADED: 'green',
  RETURNED: 'red',
}

/** 筛选 chips 选项 */
export const SUBMISSION_FILTER_OPTIONS: ReadonlyArray<{ value: GradingFilter; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'PENDING', label: '待批阅' },
  { value: 'GRADED', label: '已批阅' },
  { value: 'RETURNED', label: '已退回' },
]

/** AI 预批建议（从 aiFeedback + submissionData 解析出的结构化视图） */
export interface AiSuggestionResult {
  /** 建议分数（submissionData.aiSuggestedScore，后端可能是字符串数字） */
  suggestedScore: number | null
  /** 亮点 */
  highlights: string
  /** 改进建议 */
  suggestions: string
  /** AI 评语（aiFeedback 字段） */
  feedback: string
}

export interface UseSubmissionGradingOptions {
  /** 课程 ID（宿主上下文，预留用于领域扩展与埋点） */
  courseId: Ref<number | null>
  /** 提交列表（宿主持有数据源，composable 只做派生） */
  submissions: Ref<Submission[]>
  /** 满分兜底值（row.maxScore 缺失时使用，默认 1000 与后端 GradeDTO 上限一致） */
  fallbackMaxScore?: Ref<number>
  /** 批改/退回成功后的宿主回调（刷新提交列表 / 通知 tab） */
  onGraded?: () => void | Promise<void>
}

/**
 * 解析 AI 建议失败安全：任何字段缺失/类型不符都静默降级，绝不抛异常。
 */
export function parseAiSuggestion(
  submission: Partial<Submission> | null | undefined,
): AiSuggestionResult | null {
  if (!submission) return null
  let suggestedScore: number | null = null
  let highlights = ''
  let suggestions = ''
  // submissionData 是 JSON 字符串；parse 失败静默降级（共享知识 §7-5）
  const raw = submission.submissionData
  if (typeof raw === 'string' && raw.trim()) {
    try {
      const parsed: unknown = JSON.parse(raw)
      if (parsed && typeof parsed === 'object') {
        const data = parsed as Record<string, unknown>
        const scoreValue = data.aiSuggestedScore
        if (typeof scoreValue === 'number' && Number.isFinite(scoreValue)) {
          suggestedScore = scoreValue
        } else if (typeof scoreValue === 'string' && scoreValue.trim() !== '') {
          const parsedScore = Number(scoreValue)
          if (Number.isFinite(parsedScore)) suggestedScore = parsedScore
        }
        if (typeof data.aiHighlights === 'string') highlights = data.aiHighlights
        if (typeof data.aiSuggestions === 'string') suggestions = data.aiSuggestions
      }
    } catch {
      // 非 JSON 或半截 JSON：按「无结构化建议」处理
    }
  }
  const feedback = typeof submission.aiFeedback === 'string' ? submission.aiFeedback : ''
  // 连评语都没有则视为无有效建议
  if (suggestedScore == null && !feedback && !highlights && !suggestions) return null
  return { suggestedScore, highlights, suggestions, feedback }
}

/** 状态标签视图（label + 色板 class），未知状态静默降级 */
export function submissionStatusView(status?: string | null): { label: string; tagClass: string } {
  const key = String(status || '').toUpperCase()
  return {
    label: SUBMISSION_STATUS_LABELS[key] || '状态未知',
    tagClass: SUBMISSION_STATUS_CLASSES[key] || '',
  }
}

/**
 * 批阅域逻辑：状态筛选 + 批改流（含 AI 预批回填）+ 退回流。
 */
export function useSubmissionGrading(options: UseSubmissionGradingOptions) {
  const { submissions, onGraded } = options
  const fallbackMax = options.fallbackMaxScore ?? ref(1000)

  // ---- 状态筛选 ----
  const statusFilter = ref<GradingFilter>('ALL')

  const matchesFilter = (row: Submission, filter: GradingFilter): boolean => {
    if (filter === 'ALL') return true
    const status = String(row.status || '').toUpperCase()
    if (filter === 'PENDING') return PENDING_STATUSES.includes(status)
    return status === filter
  }

  const filteredSubmissions: ComputedRef<Submission[]> = computed(() =>
    submissions.value.filter((row) => matchesFilter(row, statusFilter.value)),
  )

  const pendingCount: ComputedRef<number> = computed(
    () =>
      submissions.value.filter((row) =>
        PENDING_STATUSES.includes(String(row.status || '').toUpperCase()),
      ).length,
  )

  // ---- 批改流 ----
  const gradeModal = reactive({
    visible: false,
    row: null as Submission | null,
    form: { score: null as number | string | null, feedback: '' },
    aiLoading: false,
    aiResult: null as AiSuggestionResult | null,
    get maxScore(): number {
      const value = Number(this.row?.maxScore ?? fallbackMax.value)
      return Number.isFinite(value) && value >= 0 ? value : 1000
    },
  })

  /** 该提交是否有可批内容（文本或附件）；空内容禁用 AI 预批（后端会抛「提交内容为空」） */
  const gradeRowHasContent = computed(() => {
    const row = gradeModal.row
    if (!row) return false
    const files = Array.isArray(row.fileUrls)
      ? row.fileUrls
      : typeof row.fileUrls === 'string' && row.fileUrls.trim()
        ? [row.fileUrls]
        : []
    return Boolean((row.content && String(row.content).trim()) || files.length > 0)
  })

  function openGrade(row: Submission): void {
    gradeModal.row = row
    gradeModal.form.score = row.score != null ? Number(row.score) : null
    gradeModal.form.feedback = row.feedback || ''
    gradeModal.aiResult = null
    gradeModal.visible = true
  }

  /**
   * AI 预批：调用 ai-grade 并解析建议到 aiResult；不自动改表单，不落任何分数。
   */
  async function runAiPreview(): Promise<void> {
    const row = gradeModal.row
    if (!row || gradeModal.aiLoading) return
    gradeModal.aiLoading = true
    try {
      const response = await submissionApi.aiGrade(row.id)
      const payload = unwrap<Submission>(response)
      const suggestion = parseAiSuggestion(payload)
      gradeModal.aiResult = suggestion
      if (suggestion) {
        overlayHost().message.success('AI 预批完成，请查看建议后采纳或修改')
      } else {
        overlayHost().message.warning('AI 预批完成，本次未产出结构化建议，请人工批改')
      }
    } catch (error) {
      logger.error('AI 预批失败', error)
      overlayHost().message.error('AI 预批失败，请稍后重试')
    } finally {
      gradeModal.aiLoading = false
    }
  }

  /** 「采纳建议」：建议分/评语一键回填表单（可再改），最终提交仍走 /grade */
  function applyAiSuggestion(): void {
    const suggestion = gradeModal.aiResult
    if (!suggestion) {
      overlayHost().message.warning('暂无 AI 建议，请先点击「AI 预批」')
      return
    }
    if (suggestion.suggestedScore != null) gradeModal.form.score = suggestion.suggestedScore
    if (suggestion.feedback) gradeModal.form.feedback = suggestion.feedback
    else if (!gradeModal.form.feedback && (suggestion.highlights || suggestion.suggestions)) {
      // 无独立评语时，用亮点/建议拼一份可编辑的底稿
      const parts = [suggestion.highlights, suggestion.suggestions].filter(Boolean)
      gradeModal.form.feedback = parts.join('\n')
    }
  }

  /**
   * 提交批改：校验分数 0~maxScore → POST /grade。
   * @returns 是否提交成功
   */
  async function submitGrade(): Promise<boolean> {
    if (gradeModal.form.score == null || gradeModal.form.score === '') {
      overlayHost().message.warning('请填写分数')
      return false
    }
    const score = Number(gradeModal.form.score)
    if (!gradeModal.row || !Number.isFinite(score)) {
      overlayHost().message.warning('请输入有效分数')
      return false
    }
    const maxScore = gradeModal.maxScore
    if (score < 0 || score > maxScore) {
      overlayHost().message.warning(`分数需在 0 至 ${maxScore} 之间`)
      return false
    }
    try {
      await submissionApi.gradeSubmission(gradeModal.row.id, {
        score,
        feedback: gradeModal.form.feedback,
      })
      overlayHost().message.success('批改已提交')
      gradeModal.visible = false
      gradeModal.row = null
      gradeModal.form.score = null
      gradeModal.form.feedback = ''
      gradeModal.aiResult = null
      await onGraded?.()
      return true
    } catch (error) {
      logger.error('批改失败', error)
      overlayHost().message.error('提交批改失败，请稍后重试')
      return false
    }
  }

  // ---- 退回流 ----
  const returnModal = reactive({
    visible: false,
    row: null as Submission | null,
    form: { reason: '' },
  })

  function openReturn(row: Submission): void {
    returnModal.row = row
    returnModal.form.reason = ''
    returnModal.visible = true
  }

  /**
   * 提交退回：reason trim 后必填 → POST /return。
   * @returns 是否提交成功
   */
  async function submitReturn(): Promise<boolean> {
    if (!returnModal.row) return false
    if (!returnModal.form.reason.trim()) {
      overlayHost().message.warning('请填写退回原因')
      return false
    }
    try {
      await submissionApi.returnSubmission(returnModal.row.id, { reason: returnModal.form.reason })
      overlayHost().message.success('已退回')
      returnModal.visible = false
      returnModal.row = null
      returnModal.form.reason = ''
      await onGraded?.()
      return true
    } catch (error) {
      logger.error('退回失败', error)
      overlayHost().message.error('退回失败，请稍后重试')
      return false
    }
  }

  /** 关闭全部弹层并重置（路由切换/取消时调用） */
  function closeAll(): void {
    gradeModal.visible = false
    gradeModal.row = null
    gradeModal.form.score = null
    gradeModal.form.feedback = ''
    gradeModal.aiResult = null
    gradeModal.aiLoading = false
    returnModal.visible = false
    returnModal.row = null
    returnModal.form.reason = ''
  }

  return {
    statusFilter,
    filteredSubmissions,
    pendingCount,
    gradeModal,
    gradeRowHasContent,
    returnModal,
    openGrade,
    runAiPreview,
    applyAiSuggestion,
    submitGrade,
    openReturn,
    submitReturn,
    closeAll,
  }
}
