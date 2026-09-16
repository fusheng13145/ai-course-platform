import { ref, computed, onMounted, onUnmounted } from 'vue'
import { formatTime } from '@/utils/datetime'
import { ElMessage, ElMessageBox } from 'element-plus'
import logger from '@/utils/logger'
import examApi from '@/api/exam'

/** 服务端会话状态：进行中（对应后端 ExamSessionStatus.STARTED） */
const SESSION_STATUS_STARTED = 'STARTED'

/**
 * 后端 ExamSession 快照。
 * <p>仅声明本 composable 实际用到的字段；其余字段一律不声明，避免 unknown 数据以 any 形态扩散。</p>
 */
interface ExamSessionSnapshot {
  id?: number | string
  status?: string
  answers?: unknown
}

/**
 * 统一解包后端响应体。
 * <p>request 拦截器返回的已经是 body 本体（{ code, message, data }），业务数据挂在 data 上；
 * 个别接口历史上直接返回裸数据，故做一次兜底，取不到 data 时退化为整个响应体。</p>
 */
function unwrapPayload<T>(res: unknown): T | null {
  const data = (res as { data?: unknown } | null | undefined)?.data
  const payload = data !== undefined && data !== null ? data : res
  return (payload ?? null) as T | null
}

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
  // 显式标注联合类型：默认 ref(null) 会推导为 Ref<null>，恢复/新建两条路径都要写入会话ID
  const sessionId = ref<number | string | null>(null)
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

  /**
   * 查询该试卷上「进行中」的会话，用于中途退出后续答。
   * @returns 处于 STARTED 的会话快照；无进行中会话或接口异常时返回 null（交由调用方走新建流程）
   */
  async function tryResumeSession(examPaperId): Promise<ExamSessionSnapshot | null> {
    try {
      const res = await examApi.getMyExamSession(examPaperId)
      const session = unwrapPayload<ExamSessionSnapshot>(res)
      if (!session || typeof session !== 'object') return null
      if (session.status !== SESSION_STATUS_STARTED) return null
      if (session.id === undefined || session.id === null) return null
      return session
    } catch (e) {
      // 接口不可用/未授权时不阻断主流程，降级为「新建会话」
      logger.warn('查询进行中的考试会话失败，降级为新建会话:', e)
      return null
    }
  }

  /**
   * 读取服务端剩余时间（秒）。
   * @returns 服务端剩余秒数；接口不可用或返回非数值时返回 null，由调用方回退到试卷时长
   */
  async function fetchRemainingSeconds(sid): Promise<number | null> {
    try {
      const res = await examApi.getRemainingTime(sid)
      const seconds = unwrapPayload<unknown>(res)
      if (typeof seconds !== 'number' || !Number.isFinite(seconds)) return null
      return Math.max(0, Math.floor(seconds))
    } catch (e) {
      logger.warn('获取考试剩余时间失败，回退为试卷时长:', e)
      return null
    }
  }

  /**
   * 进入考试（新建会话，或恢复未完成会话）。
   *
   * <p><b>修复 #18：学生中途退出考试后无法再进入。</b>
   * 退出时前端只清了定时器，服务端会话仍是 STARTED，再次 startExam 会被
   * 「您有正在进行的考试」拒绝，而前端又没有「继续考试」入口 → 该场考试彻底进不去，
   * 只能等超时自动收卷。因此进入时先查最新会话：存在 STARTED 会话则复用
   * （恢复题目 + 已保存作答 + 服务端剩余时间），否则才走创建流程。</p>
   *
   * <p><b>注意：刻意不改成「退出即提交」</b>——那会直接剥夺学生继续作答的权利，比现状更糟。</p>
   */
  async function enterExam(exam) {
    if (exam.completed) {
      ElMessage.info('该考试已完成')
      return
    }

    try {
      await ElMessageBox.confirm(`确定开始「${exam.title}」吗？\n进入后将启动倒计时。`, '确认开始考试', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'info',
      })
    } catch {
      return // 用户取消，静默返回
    }

    try {
      const resumed = await tryResumeSession(exam.id)
      const duration = exam.duration || exam.timeLimit || 60

      if (resumed && resumed.id !== undefined && resumed.id !== null) {
        // ---- 恢复流程：复用既有会话，不新建 ----
        sessionId.value = resumed.id
        await loadQuestions(exam.id)
        currentExamPaper.value = exam
        restoreAnswers(parseSavedAnswers(resumed.answers))

        totalSeconds.value = duration * 60
        // 已考时长以服务端为准：避免每次退出重进都重置为满时长（等价无限续时）
        const remaining = await fetchRemainingSeconds(resumed.id)
        remainingSeconds.value = remaining ?? totalSeconds.value

        viewMode.value = 'exam'
        startCountdown()
        startAutoSave()
        ElMessage.success('已恢复上次未完成的作答，请继续答题')
        return
      }

      // ---- 首次进入：创建会话 ----
      const res = await examApi.startExam(exam.id)
      const started = unwrapPayload<ExamSessionSnapshot>(res)
      const sid = started?.id
      if (sid === undefined || sid === null) {
        throw new Error('创建考试会话失败：响应缺少会话ID')
      }
      sessionId.value = sid

      await loadQuestions(exam.id)
      currentExamPaper.value = exam

      totalSeconds.value = duration * 60
      remainingSeconds.value = totalSeconds.value

      viewMode.value = 'exam'
      startCountdown()
      startAutoSave()
      ElMessage.success('考试已开始，祝你好运！')
    } catch (e) {
      // P1-22：开始考试失败不再静默吞掉，给出明确提示
      logger.error('开始考试失败:', e)
      ElMessage.error(e?.response?.data?.message || '开始考试失败，请稍后重试')
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
        multiAnswers.value[idx] = Array.isArray(value) ? (value as number[]) : []
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
      // 关键：清空本地会话态（含 sessionId / 题目 / 作答）。
      // 服务端会话刻意保持 STARTED（不提交、不结束），下次进入由 enterExam 走恢复流程续答；
      // 若此处不重置，本地残留的 sessionId 会与「重新创建的会话」错位。
      resetExamState()
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
