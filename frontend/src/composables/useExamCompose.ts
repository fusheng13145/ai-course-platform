import { computed, ref } from 'vue'
import type { ComputedRef, Ref } from 'vue'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import questionApi from '@/api/question'
import examApi from '@/api/exam'
import { pickList, readTotal, unwrap } from '@/utils/response'

/**
 * 试卷选题域 composable（FE-14：编排层留 .vue，域逻辑进这里）。
 *
 * 职责（设计 §3.2）：
 * - 题库列表：按课程 + 题型 + 分页加载（选题区数据源）；
 * - 勾选题：selectedQuestions + selectedTotalScore 实时合计，供与 form.totalScore 校验；
 * - 编辑回显：restoreFromPaper → getExamQuestions（API 层已归一化为扁平题目）；
 * - 自动组卷：autoCompose → autoComposeExamPaper（后端直接建卷并写题目）→
 *   form.id 切到新卷 → restoreFromPaper 回显（设计 D6）。
 *
 * 契约：
 * - typeRules / difficultyRatio 的 key 必须是题库 Question.type / Question.difficulty
 *   实际值（实库为 *_CHOICE 风格 + 小写难度；后端按原值精确分组，错 key 直接 400。
 *   2026-10-26 QA 终验更正设计 F3 原前提，证据见 constants/questionTypes.ts 头注）；
 * - 列表读取一律走 utils/response 的 unwrap/pickList/readTotal，禁止手搓 res.data.records。
 */

/** 题库题目（GET /questions 返回项的字段按需收窄，教师视角其余字段原样透传） */
export interface BankQuestion {
  id: number
  type: string
  content?: string | null
  stem?: string | null
  title?: string | null
  score?: number | null
  difficulty?: string | null
  [key: string]: unknown
}

/** 已选题（勾选/回显的统一形状；score 为该题在本卷中的分值，可改） */
export interface SelectedQuestion {
  questionId: number
  type: string
  content: string
  score: number
}

/** ExamPaper（entity/ExamPaper.java 的前端视角，字段按需收窄） */
export interface ExamPaper {
  id?: number
  courseId?: number
  title?: string
  description?: string | null
  duration?: number | null
  totalScore?: number | null
  status?: string
  questionCount?: number | null
  [key: string]: unknown
}

/** 自动组卷请求体（AutoComposeDTO；typeRules key = 题库 type 实际值、difficultyRatio key = 小写难度） */
export interface AutoComposePayload {
  courseId: number
  title: string
  description?: string
  totalScore?: number
  duration?: number
  typeRules: Record<string, number>
  difficultyRatio?: Record<string, number>
  shuffleQuestions?: boolean
  shuffleOptions?: boolean
}

/** 试卷表单保存 payload：题目清单 + 题目是否已从后端回显（宿主据此决定是否批量替换题目） */
export interface ExamFormSavePayload {
  selectedQuestions: SelectedQuestion[]
  /** true = 本次会话已成功回显该卷已有题目（此时保存按批量替换语义提交，含清空） */
  questionsLoaded: boolean
}

export interface UseExamComposeOptions {
  /** 课程 ID（题库按课程过滤；null 时不加载） */
  courseId: Ref<number | null>
  /** 宿主表单的 id / totalScore 引用：自动组卷切编辑态要写 id，合计校验要读 totalScore */
  form: { id: Ref<number | null>; totalScore: Ref<number> }
}

/** 题库分页大小（选题区在 640px 弹层内，单页 10 行 + 分页器） */
export const BANK_PAGE_SIZE = 10

/** 分值缺省值：题目未带 score 时的兜底（与题库默认分值一致） */
const DEFAULT_POINTS = 1

/** 归一化单题分值：非正数/非有限值回落默认分 */
function toPoints(value: unknown): number {
  const num = Number(value)
  return Number.isFinite(num) && num > 0 ? num : DEFAULT_POINTS
}

/** 读取题干展示文本（content 为主，历史字段 stem/title 兜底，与查看题目对话框同口径） */
export function questionContentOf(question: Partial<BankQuestion> | null | undefined): string {
  if (!question) return ''
  return String(question.content || question.stem || question.title || '').trim()
}

/**
 * 试卷选题域逻辑：题库加载 / 勾选合计 / 编辑回显 / 自动组卷。
 */
export function useExamCompose(options: UseExamComposeOptions) {
  const { courseId, form } = options

  // ---- 题库列表状态 ----
  const bankQuestions = ref<BankQuestion[]>([])
  const bankLoading = ref(false)
  const bankTotal = ref(0)
  const bankPage = ref(1)
  /** 题型筛选（'' = 全部题型；value 为题库 type 实际值） */
  const bankType = ref('')

  /**
   * 按当前课程/题型/页码加载题库列表。
   * 失败时清空列表并给出明确提示（失败不得伪装成「暂无题目」）。
   */
  async function loadBank(): Promise<void> {
    if (courseId.value == null) return
    bankLoading.value = true
    try {
      const params: Record<string, unknown> = {
        courseId: courseId.value,
        page: bankPage.value,
        size: BANK_PAGE_SIZE,
      }
      if (bankType.value) params.type = bankType.value
      const response = await questionApi.getQuestionList(params)
      const payload = unwrap<unknown>(response)
      const list = pickList<BankQuestion>(payload)
      bankQuestions.value = list.filter((item) => item && item.id != null)
      bankTotal.value = readTotal(payload, bankQuestions.value.length)
    } catch (error) {
      logger.error('加载题库失败', error)
      bankQuestions.value = []
      bankTotal.value = 0
      ElMessage.error('题库加载失败，请检查网络后重试')
    } finally {
      bankLoading.value = false
    }
  }

  // ---- 勾选与合计 ----
  const selectedQuestions = ref<SelectedQuestion[]>([])

  /** 已选题目分值合计（供与 form.totalScore 校验） */
  const selectedTotalScore: ComputedRef<number> = computed(() =>
    selectedQuestions.value.reduce((sum, q) => sum + (Number(q.score) || 0), 0),
  )

  function isSelected(questionId: number): boolean {
    return selectedQuestions.value.some((q) => q.questionId === questionId)
  }

  /** 勾选/取消勾选一题；勾选时分值默认取题目 score（可再改） */
  function toggleQuestion(question: BankQuestion): void {
    if (!question || question.id == null) return
    if (isSelected(question.id)) {
      selectedQuestions.value = selectedQuestions.value.filter((q) => q.questionId !== question.id)
      return
    }
    selectedQuestions.value.push({
      questionId: Number(question.id),
      type: String(question.type || ''),
      content: questionContentOf(question),
      score: toPoints(question.score),
    })
  }

  /** 修改已选题分值；非法值（非正数/非有限）保持原分值不动 */
  function updatePoints(questionId: number, points: number): void {
    const target = selectedQuestions.value.find((q) => q.questionId === questionId)
    if (!target) return
    const value = Number(points)
    if (!Number.isFinite(value) || value <= 0) return
    target.score = value
  }

  // ---- 编辑回显 ----
  /**
   * 编辑回显：getExamQuestions（API 层已归一化为扁平题目对象，score = 关联行 points）。
   * 失败时抛出由调用方处理（宿主会提示「保存时不改动已有题目」），不静默清空。
   */
  async function restoreFromPaper(paperId: number): Promise<void> {
    const response = await examApi.getExamQuestions(Number(paperId))
    const payload = unwrap<unknown>(response)
    const list = Array.isArray(payload) ? payload : pickList<Record<string, unknown>>(payload)
    selectedQuestions.value = list
      .map((item) => {
        const row = item as Partial<BankQuestion> & Record<string, unknown>
        return {
          questionId: Number(row.id),
          type: String(row.type || ''),
          content: questionContentOf(row),
          score: toPoints(row.score),
        }
      })
      .filter((q) => Number.isFinite(q.questionId))
  }

  // ---- 自动组卷 ----
  const autoComposing = ref(false)

  /**
   * 自动组卷（设计 D6）：autoComposeExamPaper → 后端直接建卷并写题目（DRAFT）→
   * form.id 切到新卷（宿主据 adopted 事件切编辑态）→ restoreFromPaper 回显题目清单。
   * @returns 新建 ExamPaper；失败返回 null（不抛异常，调用方按 null 分支处理）
   */
  async function autoCompose(payload: AutoComposePayload): Promise<ExamPaper | null> {
    if (autoComposing.value) return null
    autoComposing.value = true
    try {
      const response = await examApi.autoComposeExamPaper(payload)
      const paper = unwrap<ExamPaper>(response)
      if (!paper || paper.id == null) {
        ElMessage.error('自动组卷返回数据异常，请稍后重试')
        return null
      }
      form.id.value = Number(paper.id)
      // 回显失败不吞掉建卷结果：新卷已创建，仅题目清单展示失败
      try {
        await restoreFromPaper(Number(paper.id))
      } catch (error) {
        logger.error('自动组卷题目回显失败', error)
        ElMessage.warning('自动组卷完成，但题目清单回显失败，请重新打开该试卷查看')
      }
      return paper
    } catch (error) {
      logger.error('自动组卷失败', error)
      ElMessage.error('自动组卷失败，请稍后重试')
      return null
    } finally {
      autoComposing.value = false
    }
  }

  /** 重置全部状态（关闭弹层/切换新建时调用） */
  function reset(): void {
    bankQuestions.value = []
    bankTotal.value = 0
    bankPage.value = 1
    bankType.value = ''
    selectedQuestions.value = []
  }

  return {
    bankQuestions,
    bankLoading,
    bankTotal,
    bankPage,
    bankType,
    loadBank,
    selectedQuestions,
    selectedTotalScore,
    isSelected,
    toggleQuestion,
    updatePoints,
    restoreFromPaper,
    autoCompose,
    autoComposing,
    reset,
  }
}
