/**
 * 试卷状态契约（ExamPaperStatus 枚举 name 的唯一展示出口）。
 *
 * 背景（设计 F1）：后端 `ExamPaper.status` 持久化为枚举 name
 * （DRAFT/PENDING_REVIEW/REJECTED/PUBLISHED/ARCHIVED），
 * 而 CeExamTable 曾直接判 `row.status === '已发布'`，导致已发布卷恒显示「草稿」、
 * 发布按钮恒显（重复发布会被后端「试卷已发布」异常打回）。
 *
 * 本常量是状态展示/按钮判定的唯一出口：禁止在组件里手写魔法字符串判断状态。
 */
export interface ExamPaperStatusView {
  /** 中文标签 */
  label: string
  /** 标签色（el-tag type / 项目 tag class 语义） */
  tagType: 'info' | 'success' | 'warning' | 'danger'
  /** 是否允许提交审核/发布（草稿与已驳回可提交；待审核/已发布/已归档不可） */
  canPublish: boolean
}

/** ExamPaper.status 枚举 name → 展示视图。key 与后端枚举 name 严格一致。 */
export const EXAM_PAPER_STATUS: Record<
  'DRAFT' | 'PENDING_REVIEW' | 'REJECTED' | 'PUBLISHED' | 'ARCHIVED',
  ExamPaperStatusView
> = {
  DRAFT: { label: '草稿', tagType: 'info', canPublish: true },
  PENDING_REVIEW: { label: '待审核', tagType: 'warning', canPublish: false },
  REJECTED: { label: '已驳回', tagType: 'danger', canPublish: true },
  PUBLISHED: { label: '已发布', tagType: 'success', canPublish: false },
  ARCHIVED: { label: '已归档', tagType: 'info', canPublish: false },
}

const FALLBACK_VIEW: ExamPaperStatusView = { label: '状态未知', tagType: 'info', canPublish: false }

/**
 * 读取试卷状态展示视图（未知/空值静默降级为「状态未知」，不抛异常）。
 * @param status 后端 ExamPaperStatus 枚举 name
 */
export function examPaperStatusView(status?: string | null): ExamPaperStatusView {
  if (!status) return FALLBACK_VIEW
  const key = String(status).toUpperCase() as keyof typeof EXAM_PAPER_STATUS
  return EXAM_PAPER_STATUS[key] ?? FALLBACK_VIEW
}
