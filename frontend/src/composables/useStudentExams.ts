import { ref, computed, onMounted, onUnmounted } from 'vue'
import { formatTime } from '@/utils/datetime'
import { ElMessage, ElMessageBox } from 'element-plus'
import logger from '@/utils/logger'
import examApi from '@/api/exam'

/**
 * 学生考试业务逻辑
 * <p>承载学生考试页三视图（列表/答题/结果）的全部状态与操作：
 * 考试列表（搜索/状态筛选/已完成标记）、进入考试（启动会话 + 倒计时 + 自动保存）、
 * 答题交互（单选/多选/判断/简答 + 题目导航 + 作答收集）、提交与成绩结果。</p>
 */
export function useStudentExams() {
  /* ---- 视图模式：list / exam / result ---- */
  const viewMode = ref('list')

  /* ---- 列表页状态 ---- */
  const searchKeyword = ref('')
  const statusFilter = ref('')
  const listLoading = ref(false)
  const examList = ref([])

  /* ---- 答题状态 ---- */
  const currentExamPaper = ref(null)
  const sessionId = ref(null)
  const questions = ref([])
  const questionsLoading = ref(false)
  const currentIndex = ref(0)
  const answers = ref({} as Record<number, string | number | boolean>) // 单选/判断答案
  const multiAnswers = ref({} as Record<number, number[]>) // 多选答案
  const textAnswers = ref({} as Record<number, string>) // 简答答案
  const defaultScore = 10

  /* ---- 倒计时 ---- */
  let countdownTimer = null
  const totalSeconds = ref(0)
  const remainingSeconds = ref(0)

  /* ---- 成绩结果 ---- */
  const examResult = ref({} as Record<string, unknown>)
  const autoSaveTimer = ref(null)

  /* ---- 计算属性 ---- */

  /** 过滤后的考试列表（关键字 + 状态） */
  const filteredExams = computed(() => {
    let list = examList.value
    if (searchKeyword.value) {
      list = list.filter((e) => e.title?.includes(searchKeyword.value))
    }
    if (statusFilter.value) {
      list = list.filter((e) => {
        if (statusFilter.value === 'completed') return e.completed
        if (statusFilter.value === 'available') return !e.completed && e.status !== 'EXPIRED'
        if (statusFilter.value === 'expired') return e.status === 'EXPIRED'
        return true
      })
    }
    return list
  })

  /** 考试总数 */
  const totalExams = computed(() => examList.value.length)
  /** 可参加：未完成且未过期 */
  const availableExams = computed(
    () => examList.value.filter((e) => !e.completed && e.status !== 'EXPIRED').length,
  )
  /** 已完成 */
  const completedExams = computed(() => examList.value.filter((e) => e.completed).length)

  /** 当前题目 */
  const currentQuestion = computed(() => questions.value[currentIndex.value] || null)

  /** 剩余分钟数 */
  const remainingMinutes = computed(() => Math.floor(remainingSeconds.value / 60))

  /** 倒计时显示文本 */
  const countdownDisplay = computed(() => {
    const m = Math.floor(remainingSeconds.value / 60)
    const s = remainingSeconds.value % 60
    return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
  })

  /** 结果提示信息（P1-21：后端 result 返回 ExamSession，学生得分字段为 totalScore 而非 score） */
  const resultMessage = computed(() => {
    const score = examResult.value.totalScore
    if (score == null) return '成绩正在批阅中，请稍后查看...'
    if (score >= 90) return '表现优秀！继续保持！'
    if (score >= 75) return '良好水平，再接再厉！'
    if (score >= 60) return '及格了，还有提升空间哦~'
    return '未达到及格线，建议复习后再次尝试。'
  })

  /* ---- 方法：列表页 ---- */

  async function loadExamList() {
    listLoading.value = true
    try {
      const res = await examApi.getExamPaperList()
      const rawData = res.data?.records || res.data?.list || res.data || res
      examList.value = Array.isArray(rawData) ? rawData : []

      // 标记已完成状态（检查是否有提交记录）
      try {
        const sessionRes = await examApi.getMyExamSessions()
        const sessions = sessionRes.data || sessionRes || []
        const submittedIds = new Set(
          (Array.isArray(sessions) ? sessions : [])
            .filter((s) => s.status === 'SUBMITTED' || s.status === 'GRADED')
            .map((s) => s.examPaperId),
        )
        examList.value.forEach((e) => {
          if (submittedIds.has(e.id)) e.completed = true
        })
      } catch {
        /* 忽略 */
      }
    } catch (e) {
      logger.error('加载考试列表失败:', e)
    } finally {
      listLoading.value = false
    }
  }

  function getExamStatusType(exam) {
    if (exam.completed) return 'info'
    if (exam.status === 'EXPIRED') return 'info'
    return 'warning'
  }

  function getExamStatusLabel(exam) {
    if (exam.completed) return '已完成'
    if (exam.status === 'EXPIRED') return '已过期'
    return '进行中'
  }

  /* ---- 方法：进入考试 ---- */

  async function enterExam(exam) {
    if (exam.completed) {
      ElMessage.info('该考试已完成')
      return
    }

    try {
      ElMessageBox.confirm(`确定开始「${exam.title}」吗？\n进入后将启动倒计时。`, '确认开始考试', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info',
      })
        .then(async () => {
          // 调用 startExam API
          const res = await examApi.startExam(exam.id)
          const sid = res.data?.id || (res as unknown as { id?: string | number })?.id
          sessionId.value = sid

          // 加载题目
          await loadQuestions(exam.id)

          // 设置当前试卷信息
          currentExamPaper.value = exam

          // 初始化倒计时
          const duration = exam.duration || exam.timeLimit || 60
          totalSeconds.value = duration * 60
          remainingSeconds.value = totalSeconds.value

          // 切换到答题视图
          viewMode.value = 'exam'

          // 启动倒计时
          startCountdown()

          // 启动自动保存（每30秒）
          startAutoSave()

          ElMessage.success('考试已开始，祝你好运！')
        })
        .catch((e) => {
          // P1-22：开始考试失败不再静默吞掉，给出明确提示
          logger.error('开始考试失败:', e)
          ElMessage.error(e?.response?.data?.message || '开始考试失败，请稍后重试')
        })
    } catch (e) {
      logger.error('开始考试失败:', e)
      ElMessage.error('无法开始考试，请稍后重试')
    }
  }

  /* ---- 方法：加载题目 ---- */

  async function loadQuestions(paperId) {
    questionsLoading.value = true
    try {
      const res = await examApi.getExamQuestions(paperId)
      const data = res.data || res || []
      questions.value = Array.isArray(data) ? data : []
      // 初始化答案对象
      answers.value = {}
      multiAnswers.value = {}
      textAnswers.value = {}
    } catch (e) {
      logger.error('加载题目失败:', e)
      ElMessage.error('加载题目失败')
    } finally {
      questionsLoading.value = false
    }
  }

  /* ---- 方法：答题导航 ---- */

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
      return !!textAnswers.value[index]
    }
    return answers.value[index] !== undefined && answers.value[index] !== null
  }

  /* ---- 方法：题目类型处理 ---- */

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

  /* ---- 方法：倒计时 ---- */

  function startCountdown() {
    if (countdownTimer) clearInterval(countdownTimer)
    countdownTimer = setInterval(() => {
      if (remainingSeconds.value > 0) {
        remainingSeconds.value--
        if (remainingSeconds.value <= 300 && remainingSeconds.value % 60 === 0) {
          ElMessage.warning(`剩余时间：${Math.ceil(remainingSeconds.value / 60)} 分钟`)
        }
      } else {
        // 时间到，自动提交（P1-22：同时清理 autoSave 定时器，
        // 否则考试已提交后自动保存仍每 30s 触发，向后端发送无效请求并反复报错）
        clearInterval(countdownTimer)
        countdownTimer = null
        stopAutoSave()
        ElMessage.warning('考试时间已到，将自动提交试卷')
        submitExam()
      }
    }, 1000)
  }

  /* ---- 方法：自动保存 ---- */

  function startAutoSave() {
    if (autoSaveTimer.value) clearInterval(autoSaveTimer.value)
    autoSaveTimer.value = setInterval(() => {
      autoSaveAnswers()
    }, 30000) // 每30秒
  }

  function stopAutoSave() {
    if (autoSaveTimer.value) {
      clearInterval(autoSaveTimer.value)
      autoSaveTimer.value = null
    }
  }

  async function autoSaveAnswers() {
    if (!sessionId.value) return
    try {
      const answerData = collectAnswers()
      // 使用专用的自动保存 API
      await examApi.autoSaveAnswers(sessionId.value, { answers: answerData })
    } catch (e) {
      // autoSave 接口不可用时回退到 submit（兼容模式）
      logger.warn('自动保存失败，尝试回退:', e)
      try {
        await examApi.submitExam(sessionId.value, { answers: collectAnswers(), autoSave: true })
      } catch (e2) {
        logger.warn('回退保存也失败:', e2)
      }
    }
  }

  function collectAnswers() {
    const result = []
    questions.value.forEach((q, idx) => {
      let answer
      if (q.type === 'MULTIPLE_CHOICE') {
        answer = multiAnswers.value[idx] || []
      } else if (q.type === 'SHORT_ANSWER') {
        answer = textAnswers.value[idx] || ''
      } else {
        answer = answers.value[idx]
      }
      result.push({
        questionId: q.id,
        questionType: q.type,
        answer: answer,
      })
    })
    return result
  }

  function saveCurrentAnswer() {
    // 当前答案已在响应式数据中，此方法用于扩展逻辑
  }

  /* ---- 方法：提交试卷 ---- */

  async function confirmSubmit() {
    const unansweredCount = questions.value.filter((_, idx) => !isQuestionAnswered(idx)).length

    let msg = '确定要提交试卷吗？'
    if (unansweredCount > 0) {
      msg += `\n您还有 ${unansweredCount} 道题目未作答。`
    }

    try {
      await ElMessageBox.confirm(msg, '确认提交', {
        confirmButtonText: '确认提交',
        cancelButtonText: '继续答题',
        type: 'warning',
      })

      // 清理定时器
      cleanupTimers()
      await submitExam()
    } catch {
      // 用户取消
    }
  }

  async function submitExam() {
    try {
      const answerData = collectAnswers()
      const res = await examApi.submitExam(sessionId.value, { answers: answerData })
      const result = res.data || res || {}

      // 尝试获取即时成绩
      try {
        const resultRes = await examApi.getExamResult(sessionId.value)
        examResult.value = resultRes.data || resultRes || result
      } catch {
        examResult.value = { totalScore: null, ...result }
      }

      viewMode.value = 'result'
    } catch (e) {
      logger.error('提交试卷失败:', e)
      ElMessage.error('提交失败，请重试')
    }
  }

  /* ---- 方法：退出考试 ---- */

  async function confirmExitExam() {
    try {
      await ElMessageBox.confirm('确定要退出考试吗？\n已作答的进度将被保留。', '退出确认', {
        confirmButtonText: '确定退出',
        cancelButtonText: '继续答题',
        type: 'warning',
      })

      // 先自动保存一次
      await autoSaveAnswers()
      cleanupTimers()
      viewMode.value = 'list'
    } catch {
      // 取消
    }
  }

  function backToList() {
    viewMode.value = 'list'
    resetExamState()
  }

  async function viewDetailResult() {
    if (!sessionId.value) return
    try {
      const res = await examApi.getScoreReport(sessionId.value)
      const report = res.data || res || {}
      // P1-21：后端成绩报告字段为学生得分 totalScore（percentage 为百分制），原读 report.score 恒为 '--'
      ElMessage.info(
        `详细报告：得分 ${report.totalScore ?? '--'} / 百分制 ${report.percentage ?? '--'}`,
      )
    } catch (e) {
      logger.error('获取成绩报告失败:', e)
      ElMessage.info('成绩详情加载失败')
    }
  }

  function resetExamState() {
    sessionId.value = null
    currentExamPaper.value = null
    questions.value = []
    currentIndex.value = 0
    answers.value = {}
    multiAnswers.value = {}
    textAnswers.value = {}
    examResult.value = {}
  }

  function cleanupTimers() {
    if (countdownTimer) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
    if (autoSaveTimer.value) {
      clearInterval(autoSaveTimer.value)
      autoSaveTimer.value = null
    }
  }

  function getResultClass() {
    const score = examResult.value.totalScore
    if (score >= 90) return 'excellent'
    if (score >= 75) return 'good'
    if (score >= 60) return 'pass'
    return 'fail'
  }

  /* ---- 生命周期 ---- */

  onMounted(() => {
    loadExamList()
  })

  onUnmounted(() => {
    cleanupTimers()
  })

  return {
    viewMode,
    searchKeyword,
    statusFilter,
    listLoading,
    examList,
    currentExamPaper,
    sessionId,
    questions,
    questionsLoading,
    currentIndex,
    answers,
    multiAnswers,
    textAnswers,
    defaultScore,
    remainingMinutes,
    countdownDisplay,
    examResult,
    filteredExams,
    totalExams,
    availableExams,
    completedExams,
    currentQuestion,
    resultMessage,
    loadExamList,
    getExamStatusType,
    getExamStatusLabel,
    enterExam,
    loadQuestions,
    goToQuestion,
    prevQuestion,
    nextQuestion,
    isQuestionAnswered,
    getQuestionTypeName,
    getQuestionTypeTag,
    parseOptions,
    confirmSubmit,
    confirmExitExam,
    backToList,
    viewDetailResult,
    getResultClass,
    formatTime,
  }
}
