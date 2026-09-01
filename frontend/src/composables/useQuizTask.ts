import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import studentApi from '@/api/student'
import questionApi from '@/api/question'

/** 客观题类型（可参与随堂测验即时判分的题型） */
const OBJ_TYPES = ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE', 'SINGLE', 'MULTIPLE', 'JUDGE']

/**
 * 测验任务点交互组合式函数
 * <p>封装随堂测验任务点：随机抽题（默认 8 题、仅客观题）、作答态管理（单选/多选/判断）、
 * 提交前全量作答校验、提交后逐题反馈解析。由任务点抽屉组件（TaskDrawer）消费。</p>
 *
 * @param {Object} options
 * @param {string|number} options.courseId 课程 ID（抽题按课程范围）
 * @param {() => (Object|null)} options.getTask 获取当前活动任务点（须含 id）
 * @param {() => Promise<void>} options.onTaskCompleted 提交成功后回调（刷新学习中心进度）
 * @returns {{ quizQuestions: import('vue').Ref, quizAnswers: import('vue').Reactive,
 *   quizSubmitting: import('vue').Ref, quizResult: import('vue').Ref,
 *   loadQuiz: Function, submitQuizNow: Function, feedbackOf: Function, resetQuiz: Function,
 *   parseOptions: Function, letter: Function, isSingle: Function, isMultiple: Function,
 *   isJudge: Function, qTypeLabel: Function, qTypeClass: Function }}
 */
export function useQuizTask({ courseId, getTask, onTaskCompleted }) {
  const quizQuestions = ref([])
  const quizAnswers = reactive({} as any)
  const quizSubmitting = ref(false)
  const quizResult = ref(null)

  /** 随机抽题（仅保留客观题），并预解析选项为数组 */
  async function loadQuiz() {
    quizQuestions.value = []
    for (const k in quizAnswers) delete quizAnswers[k]
    quizResult.value = null
    try {
      const res = await questionApi.randomSelect({ courseId, count: 8 })
      const list = unwrap(res) || []
      quizQuestions.value = (Array.isArray(list) ? list : [])
        .filter((q) => OBJ_TYPES.includes(q.type))
        .map((q) => ({ ...q, _options: parseOptions(q.options) }))
    } catch (e) {
      logger.warn('加载测验题目失败:', e)
    }
  }

  /** 按题型归一化作答结果（多选逗号分隔、判断转 TRUE/FALSE） */
  function buildAnswers() {
    const ans = {}
    for (const q of quizQuestions.value) {
      const a = quizAnswers[q.id]
      if (q.type === 'MULTIPLE_CHOICE' || q.type === 'MULTIPLE') {
        const arr = Array.isArray(a)
          ? a.map(Number).sort((x, y) => x - y).map(String)
          : []
        ans[q.id] = arr.join(',')
      } else if (q.type === 'TRUE_FALSE' || q.type === 'JUDGE') {
        ans[q.id] = a === true ? 'TRUE' : a === false ? 'FALSE' : ''
      } else {
        ans[q.id] = a == null ? '' : String(a)
      }
    }
    return ans
  }

  /** 提交测验：先校验全量作答，再提交并回填逐题反馈 */
  async function submitQuizNow() {
    const ans = buildAnswers()
    const unanswered = quizQuestions.value.filter((q) => !String(ans[q.id]).length)
    if (unanswered.length) {
      ElMessage.warning(`还有 ${unanswered.length} 道题未作答`)
      return
    }
    quizSubmitting.value = true
    try {
      const res = await studentApi.submitQuiz(courseId, getTask()?.id, ans)
      quizResult.value = unwrap(res) || {}
      const task = getTask()
      if (task) task.completed = true
      await onTaskCompleted()
    } catch (e) {
      ElMessage.error('提交失败')
    } finally {
      quizSubmitting.value = false
    }
  }

  /** 按题目 ID 取该题反馈（无记录时降级为未答） */
  function feedbackOf(qid) {
    const d = (quizResult.value?.details || []).find((x) => String(x.questionId) === String(qid))
    return d || { correct: false, correctAnswer: '', explanation: '' }
  }

  /** 抽屉关闭时清理测验状态 */
  function resetQuiz() {
    quizQuestions.value = []
    for (const k in quizAnswers) delete quizAnswers[k]
    quizResult.value = null
  }

  /* ---- 选项与题型工具 ---- */

  /** 选项解析：JSON 数组/对象优先，失败回退文本分割（换行/分号/逗号） */
  function parseOptions(str) {
    if (!str) return []
    if (Array.isArray(str)) return str
    try {
      const p = JSON.parse(str)
      if (Array.isArray(p)) return p
      if (p && typeof p === 'object') return Object.values(p)
    } catch {
      if (import.meta.env.DEV) {
        logger.warn('选项解析失败，已回退为文本分割')
      }
    }
    return String(str)
      .split(/[\n;；]+/)
      .map((s) => s.trim())
      .filter(Boolean)
  }

  /** 选项序号 → 字母（A-F 后按 ASCII 扩展） */
  function letter(i) {
    return ['A', 'B', 'C', 'D', 'E', 'F'][i] || String.fromCharCode(65 + i)
  }

  function isSingle(t) {
    return t === 'SINGLE_CHOICE' || t === 'SINGLE'
  }

  function isMultiple(t) {
    return t === 'MULTIPLE_CHOICE' || t === 'MULTIPLE'
  }

  function isJudge(t) {
    return t === 'TRUE_FALSE' || t === 'JUDGE'
  }

  function qTypeLabel(t) {
    if (isSingle(t)) return '单选'
    if (isMultiple(t)) return '多选'
    if (isJudge(t)) return '判断'
    return t || '题'
  }

  function qTypeClass(t) {
    if (isSingle(t)) return 't-single'
    if (isMultiple(t)) return 't-multiple'
    if (isJudge(t)) return 't-judge'
    return ''
  }

  return {
    quizQuestions,
    quizAnswers,
    quizSubmitting,
    quizResult,
    loadQuiz,
    submitQuizNow,
    feedbackOf,
    resetQuiz,
    parseOptions,
    letter,
    isSingle,
    isMultiple,
    isJudge,
    qTypeLabel,
    qTypeClass,
  }
}
