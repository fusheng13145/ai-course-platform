import { ref, computed } from 'vue'
import logger from '@/utils/logger'
import { type ApiData } from '@/utils/response'
import examApi from '@/api/exam'

/**
 * 学生考试·作答域（useStudentExams 内部拆分，FE-06）。
 *
 * <p>承载题目装载、四类作答容器（单选/判断 answers、多选 multiAnswers、
 * 主观题文本 textAnswers、填空题 fillAnswers）、题目导航、作答判定、按题目ID
 * 收集与回填（服务端落库形态归一）、题型展示映射。不持有会话/提交/定时器状态。</p>
 *
 * <p>主观题包含简答（SHORT_ANSWER）与论述（ESSAY），后端自动阅卷把它们与
 * 填空（FILL_BLANK）一并归入人工评分（ExamSessionGradingService）。</p>
 *
 * <p>仅由 useStudentExams 编排消费，不单独对外承诺导出面。</p>
 */

/** 多空填空的落库分隔符：服务端按纯文本存，教师阅卷时按此符号辨认各空 */
const FILL_SEPARATOR = '；'
/** 单题最多渲染的空位数，避免题干误判导致页面出现大量输入框 */
const MAX_FILL_BLANKS = 6

/**
 * 从题干推断填空题的空位数。
 *
 * <p>学生端题目接口不返回 correctAnswers（防泄露），无法按标准答案个数决定输入框数量，
 * 只能看题干占位符：连续下划线优先；中文/英文空括号只有出现两次以上才算多空
 * （单次括号多为普通标点，例如「（写出一个）」）。识别不到时按单空处理。</p>
 */
export function countFillBlanks(content: unknown): number {
  const text = String(content ?? '')
  const underscores = text.match(/_{2,}/g)
  if (underscores && underscores.length > 0) {
    return Math.min(underscores.length, MAX_FILL_BLANKS)
  }
  const brackets = text.match(/[（(]\s*[）)]/g)
  if (brackets && brackets.length > 1) {
    return Math.min(brackets.length, MAX_FILL_BLANKS)
  }
  return 1
}

/** 主观题：需要教师人工评分（自动阅卷只判客观题） */
export function isSubjectiveType(type: unknown): boolean {
  const normalized = String(type ?? '').toUpperCase()
  return normalized === 'SHORT_ANSWER' || normalized === 'ESSAY' || normalized === 'FILL_BLANK'
}

export function useStudentExamSheet() {
  // 题目行尚未 per-endpoint 建模，沿用 ApiData 口径（题型/题目ID 访问点见 collectAnswers 等）
  const questions = ref<ApiData[]>([])
  const questionsLoading = ref(false)
  const currentIndex = ref(0)
  const answers = ref({} as Record<number, string | number | boolean>) // 单选/判断答案（单选为选项字母）
  const multiAnswers = ref({} as Record<number, string[]>) // 多选答案（选项字母数组）
  const textAnswers = ref({} as Record<number, string>) // 简答/论述答案
  const fillAnswers = ref({} as Record<number, string[]>) // 填空题各空答案（下标 → 每个空的文本）

  /** 当前题目 */
  const currentQuestion = computed(() => questions.value[currentIndex.value] || null)

  async function loadQuestions(paperId: string | number) {
    questionsLoading.value = true
    try {
      const res = await examApi.getExamQuestions(paperId)
      const data = res.data || res || []
      if (!Array.isArray(data) || data.length === 0) {
        throw new Error('该试卷暂无可用题目，请联系老师确认后重试')
      }
      questions.value = data
      // 初始化答案对象
      answers.value = {}
      multiAnswers.value = {}
      textAnswers.value = {}
      fillAnswers.value = {}
    } catch (e) {
      logger.error('加载题目失败:', e)
      throw e
    } finally {
      questionsLoading.value = false
    }
  }

  /* ---- 题目导航 ---- */

  function goToQuestion(index: number) {
    saveCurrentAnswer()
    currentIndex.value = index
  }

  function prevQuestion() {
    if (currentIndex.value > 0) {
      goToQuestion(currentIndex.value - 1)
    }
  }

  function nextQuestion() {
    if (currentIndex.value < questions.value.length - 1) {
      goToQuestion(currentIndex.value + 1)
    }
  }

  function isQuestionAnswered(index: number) {
    const q = questions.value[index]
    if (!q) return false
    if (q.type === 'MULTIPLE_CHOICE') {
      return multiAnswers.value[index]?.length > 0
    }
    if (q.type === 'SHORT_ANSWER' || q.type === 'ESSAY') {
      return !!textAnswers.value[index]?.trim()
    }
    if (q.type === 'FILL_BLANK') {
      // 至少填出一个空就算已作答：空位数是按题干推断的，过严会把猜错空位数误判成未答
      return (fillAnswers.value[index] || []).some((item) => String(item ?? '').trim().length > 0)
    }
    const value = answers.value[index]
    if (value === undefined || value === null) return false
    // 自动保存会把未答题写成空串；空串不是作答，判断题的 false 才是
    if (typeof value === 'string') return value.trim().length > 0
    return true
  }

  /* ---- 题目类型展示 ---- */

  function getQuestionTypeName(type: string) {
    const map: Record<string, string> = {
      SINGLE_CHOICE: '单选题',
      MULTIPLE_CHOICE: '多选题',
      TRUE_FALSE: '判断题',
      SHORT_ANSWER: '简答题',
      ESSAY: '论述题',
      FILL_BLANK: '填空题',
    }
    return map[type] || '未知题型'
  }

  function getQuestionTypeTag(type: string) {
    const map: Record<string, string> = {
      SINGLE_CHOICE: '',
      MULTIPLE_CHOICE: 'warning',
      TRUE_FALSE: 'info',
      SHORT_ANSWER: 'success',
      ESSAY: 'success',
      FILL_BLANK: 'info',
    }
    return map[type] || ''
  }

  function parseOptions(options: ApiData) {
    if (!options) return []
    if (typeof options === 'string') {
      try {
        const parsed = JSON.parse(options)
        return Array.isArray(parsed) ? parsed : [options]
      } catch {
        return options.split('\n').filter(Boolean)
      }
    }
    return Array.isArray(options) ? options : [options]
  }

  /* ---- 作答收集与回填 ---- */

  /**
   * 收集当前作答，按「题目ID → 答案」组织。
   *
   * <p><b>必须是对象而不是数组</b>：后端 ExamSubmitDTO.answers 声明为 {@code Map<String,Object>}，
   * 阅卷（ExamSessionGradingService.autoGrade）按 {@code String.valueOf(questionId)} 取值。
   * 历史上此处送的是数组，Jackson 无法把 JSON 数组反序列化为 Map → 直接 400，
   * 导致自动保存与正式交卷双双失效（退出后恢复也就拿不到任何作答）。</p>
   */
  function collectAnswers(): Record<string, unknown> {
    const result: Record<string, unknown> = {}
    questions.value.forEach((q, idx) => {
      const questionId = q.id ?? q.questionId
      if (questionId === undefined || questionId === null) return
      if (q.type === 'MULTIPLE_CHOICE') {
        result[String(questionId)] = multiAnswers.value[idx] || []
      } else if (q.type === 'SHORT_ANSWER' || q.type === 'ESSAY') {
        result[String(questionId)] = textAnswers.value[idx] || ''
      } else if (q.type === 'FILL_BLANK') {
        // 服务端 answers 是 Map<String,Object>，多空按分隔符拼成一段文本，
        // 教师阅卷时能逐空对照参考答案（填空本身也是人工评分）
        const blanks = fillAnswers.value[idx] || []
        result[String(questionId)] = blanks.map((item) => String(item ?? '').trim()).join(FILL_SEPARATOR)
      } else {
        result[String(questionId)] = answers.value[idx] ?? ''
      }
    })
    return result
  }

  /**
   * 解析服务端落库的 answers 字段，归一为「题目ID → 答案」映射。
   *
   * <p>兼容历史/异常形态：JSON 字符串、对象、{@code { answers: ... }} 包裹、
   * 以及 {@code [ { questionId, answer } ]} 数组。解析不出来时返回空对象（不阻断进入考试）。</p>
   */
  function parseSavedAnswers(raw: unknown, depth = 0): Record<string, unknown> {
    if (raw === null || raw === undefined || raw === '') return {}

    let parsed: unknown = raw
    if (typeof raw === 'string') {
      try {
        parsed = JSON.parse(raw)
      } catch {
        logger.warn('已保存的作答不是合法 JSON，跳过回填')
        return {}
      }
    }

    // 数组形态：按 questionId 重新索引
    if (Array.isArray(parsed)) {
      const mapped: Record<string, unknown> = {}
      for (const item of parsed) {
        const record = item as { questionId?: number | string; answer?: unknown }
        if (record && record.questionId !== undefined && record.questionId !== null) {
          mapped[String(record.questionId)] = record.answer
        }
      }
      return mapped
    }

    if (typeof parsed !== 'object') return {}

    const container = parsed as Record<string, unknown>
    // 仅解一层：历史数据可能是 { answers: {...} } 包裹
    if (depth === 0 && container.answers !== undefined && container.answers !== null) {
      return parseSavedAnswers(container.answers, depth + 1)
    }
    return container
  }

  /**
   * 把落库的填空作答还原成「每个空一段文本」。
   *
   * <p>单空题整体作为一个答案，不按分隔符拆（答案自身可能含分号）；
   * 多空题按 {@link FILL_SEPARATOR}（兼容半角分号）切分，缺的空补空串以对齐输入框。</p>
   */
  function splitFillValue(value: unknown, count: number): string[] {
    if (Array.isArray(value)) {
      return value.map((item) => String(item ?? '')).slice(0, count)
    }
    const text = typeof value === 'string' ? value : String(value ?? '')
    if (count <= 1) return text ? [text] : []
    const parts = text.split(/[；;]/).map((item) => item.trim())
    return Array.from({ length: count }, (_, i) => parts[i] ?? '')
  }

  /**
   * 把已保存作答按题目ID回填到当前题目列表。
   * <p>按题目ID匹配而非下标匹配，可容忍题目顺序变化（后端存在题目乱序）。</p>
   */
  function restoreAnswers(saved: Record<string, unknown>) {
    if (Object.keys(saved).length === 0) return
    questions.value.forEach((q, idx) => {
      const questionId = q.id ?? q.questionId
      if (questionId === undefined || questionId === null) return
      const key = String(questionId)
      if (!Object.prototype.hasOwnProperty.call(saved, key)) return

      const value = saved[key]
      if (q.type === 'MULTIPLE_CHOICE') {
        multiAnswers.value[idx] = Array.isArray(value) ? (value as string[]) : []
      } else if (q.type === 'SHORT_ANSWER' || q.type === 'ESSAY') {
        textAnswers.value[idx] = typeof value === 'string' ? value : String(value ?? '')
      } else if (q.type === 'FILL_BLANK') {
        // 空位数以题干为准重新推断，再按分隔符还原；单空题整体作为一个答案，避免误拆
        fillAnswers.value[idx] = splitFillValue(
          value,
          countFillBlanks(q.content ?? q.questionContent),
        )
      } else {
        answers.value[idx] = value as string | number | boolean
      }
    })
  }

  function saveCurrentAnswer() {
    // 当前答案已在响应式数据中，此方法用于扩展逻辑
  }

  /** 清空题目与作答容器（退出考试 / 回列表时由编排层调用） */
  function resetSheet() {
    questions.value = []
    currentIndex.value = 0
    answers.value = {}
    multiAnswers.value = {}
    textAnswers.value = {}
    fillAnswers.value = {}
  }

  return {
    questions,
    questionsLoading,
    currentIndex,
    answers,
    multiAnswers,
    textAnswers,
    fillAnswers,
    currentQuestion,
    loadQuestions,
    goToQuestion,
    prevQuestion,
    nextQuestion,
    isQuestionAnswered,
    getQuestionTypeName,
    getQuestionTypeTag,
    countFillBlanks,
    parseOptions,
    collectAnswers,
    parseSavedAnswers,
    restoreAnswers,
    resetSheet,
  }
}
