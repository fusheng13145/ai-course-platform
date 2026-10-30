import { ref, computed } from 'vue'
import logger from '@/utils/logger'
import examApi from '@/api/exam'

/**
 * 学生考试·作答域（useStudentExams 内部拆分，FE-06）。
 *
 * <p>承载题目装载、三容器作答状态（单选/判断 answers、多选 multiAnswers、
 * 简答 textAnswers）、题目导航、作答判定、按题目ID收集与回填（服务端
 * 落库形态归一）、题型展示映射。不持有会话/提交/定时器状态。</p>
 *
 * <p>仅由 useStudentExams 编排消费，不单独对外承诺导出面。</p>
 */
export function useStudentExamSheet() {
  const questions = ref([])
  const questionsLoading = ref(false)
  const currentIndex = ref(0)
  const answers = ref({} as Record<number, string | number | boolean>) // 单选/判断答案（单选为选项字母）
  const multiAnswers = ref({} as Record<number, string[]>) // 多选答案（选项字母数组）
  const textAnswers = ref({} as Record<number, string>) // 简答答案

  /** 当前题目 */
  const currentQuestion = computed(() => questions.value[currentIndex.value] || null)

  async function loadQuestions(paperId) {
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
    } catch (e) {
      logger.error('加载题目失败:', e)
      throw e
    } finally {
      questionsLoading.value = false
    }
  }

  /* ---- 题目导航 ---- */

  function goToQuestion(index) {
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

  function isQuestionAnswered(index) {
    const q = questions.value[index]
    if (!q) return false
    if (q.type === 'MULTIPLE_CHOICE') {
      return multiAnswers.value[index]?.length > 0
    }
    if (q.type === 'SHORT_ANSWER') {
      return !!textAnswers.value[index]?.trim()
    }
    const value = answers.value[index]
    if (value === undefined || value === null) return false
    // 自动保存会把未答题写成空串；空串不是作答，判断题的 false 才是
    if (typeof value === 'string') return value.trim().length > 0
    return true
  }

  /* ---- 题目类型展示 ---- */

  function getQuestionTypeName(type) {
    const map = {
      SINGLE_CHOICE: '单选题',
      MULTIPLE_CHOICE: '多选题',
      TRUE_FALSE: '判断题',
      SHORT_ANSWER: '简答题',
    }
    return map[type] || '未知题型'
  }

  function getQuestionTypeTag(type) {
    const map = {
      SINGLE_CHOICE: '',
      MULTIPLE_CHOICE: 'warning',
      TRUE_FALSE: 'info',
      SHORT_ANSWER: 'success',
    }
    return map[type] || ''
  }

  function parseOptions(options) {
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
      } else if (q.type === 'SHORT_ANSWER') {
        result[String(questionId)] = textAnswers.value[idx] || ''
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
      } else if (q.type === 'SHORT_ANSWER') {
        textAnswers.value[idx] = typeof value === 'string' ? value : String(value ?? '')
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
  }

  return {
    questions,
    questionsLoading,
    currentIndex,
    answers,
    multiAnswers,
    textAnswers,
    currentQuestion,
    loadQuestions,
    goToQuestion,
    prevQuestion,
    nextQuestion,
    isQuestionAnswered,
    getQuestionTypeName,
    getQuestionTypeTag,
    parseOptions,
    collectAnswers,
    parseSavedAnswers,
    restoreAnswers,
    resetSheet,
  }
}
