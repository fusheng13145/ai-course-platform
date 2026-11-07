import { ref, computed, onMounted, onUnmounted } from 'vue'
import { isAxiosError } from 'axios'
import { formatTime } from '@/utils/datetime'
import { overlayHost } from '@/bootstrap/overlayHost'
import logger from '@/utils/logger'
import examApi from '@/api/exam'
import { confirmDanger, type DangerConfirmOptions } from '@/utils/dangerConfirm'
import { useStudentExamList } from '@/composables/useStudentExamList'
import { useStudentExamSheet } from '@/composables/useStudentExamSheet'
import { useStudentExamTimers } from '@/composables/useStudentExamTimers'

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
 * 学生考试业务逻辑（编排层，FE-06 拆分后的公共入口）
 * <p>承载学生考试页三视图（列表/答题/结果）的**视图切换与会话编排**；
 * 列表域见 useStudentExamList、作答域见 useStudentExamSheet、
 * 倒计时/自动保存定时器域见 useStudentExamTimers。</p>
 * <p>对外导出面与拆分前逐名一致（Exams.vue 消费，勿改名）。</p>
 */
/**
 * 考试流的交互宿主。默认全部沿用浮层宿主（旧端为 Element Plus，行为逐字不变），
 * 侧注入自有 toast 与确认弹层，使同一套编排不必再长第二份。
 */
export interface ExamUiHost {
  notify?: {
    info: (message: string) => void
    success: (message: string) => void
    warning: (message: string) => void
    error: (message: string) => void
  }
  confirmStart?: (message: string, title: string) => Promise<boolean>
  dangerConfirm?: (options: DangerConfirmOptions) => Promise<boolean>
}

async function defaultConfirmStart(message: string, title: string): Promise<boolean> {
  try {
    return await overlayHost().confirm(message, title, {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info',
    })
  } catch {
    return false
  }
}

export function useStudentExams(host: ExamUiHost = {}) {
  const notify = host.notify ?? overlayHost().message
  const confirmStart = host.confirmStart ?? defaultConfirmStart
  const dangerConfirm = host.dangerConfirm ?? confirmDanger

  /* ---- 视图模式：list / exam / result ---- */
  const viewMode = ref('list')

  /* ---- 答题会话状态 ---- */
  // 试卷行沿用列表行的未建模口径（title/duration/timeLimit 等字段访问在视图层）
  const currentExamPaper = ref<Record<string, unknown> | null>(null)
  // 显式标注联合类型：默认 ref(null) 会推导为 Ref<null>，恢复/新建两条路径都要写入会话ID
  const sessionId = ref<number | string | null>(null)
  const defaultScore = 10

  /* ---- 成绩结果 ---- */
  const examResult = ref({} as Record<string, unknown>)
  const submitting = ref(false)

  /**
   * 自动保存故障是否已提示过（每个考试页实例一份）。
   * <p>只用于抑制「每 30s 一条」的重复弹窗；保存成功后复位，故障复发时仍会再提示。</p>
   */
  let autoSaveFailureNotified = false

  /* ---- 子域 ---- */
  const list = useStudentExamList()
  const sheet = useStudentExamSheet()

  /* ---- 计算属性 ---- */

  /** 结果提示信息（P1-21：后端 result 返回 ExamSession，学生得分字段为 totalScore 而非 score） */
  const resultMessage = computed(() => {
    const raw = examResult.value.totalScore
    const score = raw == null ? null : Number(raw)
    if (score == null) return '成绩正在批阅中，请稍后查看...'
    if (score >= 90) return '表现优秀！继续保持！'
    if (score >= 75) return '良好水平，再接再厉！'
    if (score >= 60) return '及格了，还有提升空间哦~'
    return '未达到及格线，建议复习后再次尝试。'
  })

  /* ---- 方法：进入考试（会话编排） ---- */

  /**
   * 查询该试卷上「进行中」的会话，用于中途退出后续答。
   * @returns 处于 STARTED 的会话快照；无进行中会话或接口异常时返回 null（交由调用方走新建流程）
   */
  async function tryResumeSession(
    examPaperId: string | number,
  ): Promise<ExamSessionSnapshot | null> {
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
  async function fetchRemainingSeconds(sid: string | number): Promise<number | null> {
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
  async function enterExam(exam: Record<string, unknown>) {
    if (exam.completed) {
      notify.info('该考试已完成')
      return
    }

    const started = await confirmStart(
      `确定开始「${exam.title}」吗？\n进入后将启动倒计时。`,
      '确认开始考试',
    )
    if (!started) return // 用户取消，静默返回

    try {
      const paperId = exam.id as string | number
      const resumed = await tryResumeSession(paperId)
      const duration = Number(exam.duration ?? exam.timeLimit ?? 60)

      if (resumed && resumed.id !== undefined && resumed.id !== null) {
        // ---- 恢复流程：复用既有会话，不新建 ----
        sessionId.value = resumed.id
        await sheet.loadQuestions(paperId)
        currentExamPaper.value = exam
        sheet.restoreAnswers(sheet.parseSavedAnswers(resumed.answers))

        timers.totalSeconds.value = duration * 60
        // 已考时长以服务端为准：避免每次退出重进都重置为满时长（等价无限续时）
        const remaining = await fetchRemainingSeconds(resumed.id)
        timers.remainingSeconds.value = remaining ?? timers.totalSeconds.value

        viewMode.value = 'exam'
        timers.startCountdown()
        timers.startAutoSave()
        notify.success('已恢复上次未完成的作答，请继续答题')
        return
      }

      // ---- 首次进入：创建会话 ----
      const res = await examApi.startExam(paperId)
      const started = unwrapPayload<ExamSessionSnapshot>(res)
      const sid = started?.id
      if (sid === undefined || sid === null) {
        throw new Error('创建考试会话失败：响应缺少会话ID')
      }
      sessionId.value = sid

      await sheet.loadQuestions(paperId)
      currentExamPaper.value = exam

      timers.totalSeconds.value = duration * 60
      timers.remainingSeconds.value = timers.totalSeconds.value

      viewMode.value = 'exam'
      timers.startCountdown()
      timers.startAutoSave()
      notify.success('考试已开始，祝你好运！')
    } catch (e) {
      // P1-22：开始考试失败不再静默吞掉，给出明确提示
      logger.error('开始考试失败:', e)
      notify.error(
        (isAxiosError(e) ? e.response?.data?.message : undefined) ||
          (e instanceof Error ? e.message : undefined) ||
          '开始考试失败，请稍后重试',
      )
    }
  }

  /* ---- 方法：自动保存 ---- */

  async function autoSaveAnswers(): Promise<boolean> {
    if (!sessionId.value || submitting.value) return false
    try {
      const answerData = sheet.collectAnswers()
      // 使用专用的自动保存 API
      await examApi.autoSaveAnswers(sessionId.value, { answers: answerData })
      if (!autoSaveFailureNotified) return true
      // 上一次失败、本次成功：复位提示标记，下一轮故障仍能重新提示（不吞掉后续独立故障）
      autoSaveFailureNotified = false
      notify.success('网络恢复，作答已重新同步到服务端')
      return true
    } catch (e) {
      // 自动保存失败（断网 / 5xx）时**绝不**回退到 submit。
      // submit 是终止性操作：会话置 SUBMITTED、立即判卷并锁定重入，
      // 把一次瞬时保存失败换成一次「不可撤销的自动交卷」，学生整场考试直接报废。
      // 正确姿态是让定时器继续跑：作答仍在内存里，下一次 30s tick 自然重试，
      // 退出考试时 confirmExitExam 也会再存一次；同时把故障显式告知用户（原实现只写日志，
      // 页面一切「正常」，学生以为进度已保存才敢关标签页 → 真丢卷）。
      logger.warn('自动保存失败，作答保留在本地，将在下一次定时器重试:', e)
      if (!autoSaveFailureNotified) {
        autoSaveFailureNotified = true
        notify.warning('网络异常，本次自动保存未成功；请勿关闭页面，作答已暂存本地并会持续重试')
      }
      return false
    }
  }

  /* ---- 方法：提交试卷 ---- */

  async function confirmSubmit() {
    if (submitting.value) return
    const unansweredCount = sheet.questions.value.filter(
      (_, idx) => !sheet.isQuestionAnswered(idx),
    ).length

    // D2-2：交卷为不可逆操作，确认语明确影响范围与后果
    const ok = await dangerConfirm({
      verb: '提交',
      target: '试卷',
      impact:
        unansweredCount > 0
          ? `您还有 ${unansweredCount} 道题目未作答，未作答题目将按 0 分计入成绩`
          : '提交后将无法继续修改答案',
      irreversible: true,
      recovery: '请确认作答无误后再提交',
      confirmText: '确认提交',
      cancelText: '继续答题',
    })
    if (!ok) return

    await submitExam()
  }

  /**
   * 提交试卷（内部实现，供手动与超时自动交卷共用）。
   * @returns true=服务端已接受（已切到结果页并清理定时器）；false=未提交（会话失效或被并发/失败拦截）
   */
  async function submitExam(): Promise<boolean> {
    if (submitting.value) return false
    if (sessionId.value === null) {
      notify.error('考试会话已失效，请返回考试列表后重新进入')
      return false
    }

    submitting.value = true
    try {
      const answerData = sheet.collectAnswers()
      const res = await examApi.submitExam(sessionId.value, { answers: answerData })
      // 交卷已由服务端接受：此后不再发送自动保存请求，避免终态会话收到无效写入。
      cleanupTimers()
      const result = res.data || res || {}

      // 尝试获取即时成绩
      try {
        const resultRes = await examApi.getExamResult(sessionId.value)
        examResult.value = resultRes.data || resultRes || result
      } catch {
        examResult.value = { totalScore: null, ...result }
      }

      viewMode.value = 'result'
      return true
    } catch (e) {
      logger.error('提交试卷失败:', e)
      notify.error('提交失败，作答仍保留在本页；请检查网络后重试')
      return false
    } finally {
      submitting.value = false
    }
  }

  /**
   * 超时自动交卷（倒计时归零时触发）。
   * <p>与手动交卷不同，超时交卷必须由前端"兜底到底"：此时学生已无权继续操作，
   * 若因瞬时网络故障交卷失败就放任不管，考试会卡在「已到时却未交卷」的悬空态，
   * 学生不手动重试则永远交不出去。故失败时：
   * （1）先尽力把最新作答同步到服务端（避免服务端缺失最新一笔作答）；
   * （2）以 10s 退避节奏周期性重试交卷，直到网络恢复成功提交；
   * （3）成功提交后由 submitExam 内部切到结果页并清理全部定时器（含本重试定时器）。
   * 若在重试窗口内学生手动交卷成功，submitExam 会 cleanupTimers 清掉本重试定时器，不会重复交卷。</p>
   */
  function submitExamOnTimeout() {
    submitExam().then((ok) => {
      if (ok) {
        timeoutSubmitRetryTimer = null
        return
      }
      // 交卷失败：尽力同步作答，再安排下一次重试
      autoSaveAnswers().catch(() => {})
      if (timeoutSubmitRetryTimer) clearTimeout(timeoutSubmitRetryTimer)
      timeoutSubmitRetryTimer = setTimeout(submitExamOnTimeout, 10000)
    })
  }

  /* ---- 方法：退出考试 ---- */

  async function confirmExitExam() {
    if (submitting.value) return
    // D2-2：退出考试确认语明确后果（进度保留方式、重新进入条件、超时仍会 auto-submit）
    const ok = await dangerConfirm({
      verb: '退出',
      target: '考试',
      impact: '已作答的进度将保留，退出后需在考试有效期内重新进入才能继续作答',
      irreversible: false,
      note: '倒计时结束后系统仍会自动提交试卷',
      confirmText: '确定退出',
      cancelText: '继续答题',
    })
    if (!ok) return

    try {
      // 先自动保存一次。若同步失败，保留答题状态与定时器，不让“进度已保留”的提示失真。
      const saved = await autoSaveAnswers()
      if (!saved) {
        notify.warning('作答尚未成功同步，已保留在本页；请恢复网络后重试退出')
        return
      }
      cleanupTimers()
      // 关键：清空本地会话态（含 sessionId / 题目 / 作答）。
      // 服务端会话刻意保持 STARTED（不提交、不结束），下次进入由 enterExam 走恢复流程续答；
      // 若此处不重置，本地残留的 sessionId 会与「重新创建的会话」错位。
      resetExamState()
      viewMode.value = 'list'
    } catch {
      // 自动保存异常：保留答题页，与“进度已保留”提示保持一致
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
      notify.info(
        `详细报告：得分 ${report.totalScore ?? '--'} / 百分制 ${report.percentage ?? '--'}`,
      )
    } catch (e) {
      logger.error('获取成绩报告失败:', e)
      notify.info('成绩详情加载失败')
    }
  }

  function resetExamState() {
    sessionId.value = null
    currentExamPaper.value = null
    sheet.resetSheet()
    examResult.value = {}
  }

  function getResultClass() {
    const raw = examResult.value.totalScore
    const score = raw == null ? 0 : Number(raw)
    if (score >= 90) return 'excellent'
    if (score >= 75) return 'good'
    if (score >= 60) return 'pass'
    return 'fail'
  }

  /* ---- 定时器域（在交卷/保存动作声明之后装配，回调经 handlers 注入） ---- */

  /** 超时自动交卷的重试定时器句柄（编排层私有，随 cleanupTimers 一并清理）。 */
  let timeoutSubmitRetryTimer: ReturnType<typeof setTimeout> | null = null
  const timers = useStudentExamTimers({
    onTimeout: submitExamOnTimeout,
    onAutoSave: autoSaveAnswers,
    notify,
  })

  function cleanupTimers() {
    // 复用定时器域的单一清理实现，再补清编排层私有的超时交卷重试定时器
    // （组件卸载 / 成功交卷 / 手动退出均须清掉，避免残留重试）。
    timers.cleanupTimers()
    if (timeoutSubmitRetryTimer) {
      clearTimeout(timeoutSubmitRetryTimer)
      timeoutSubmitRetryTimer = null
    }
  }

  /* ---- 生命周期 ---- */

  onMounted(() => {
    list.loadExamList()
  })

  onUnmounted(() => {
    cleanupTimers()
  })

  return {
    viewMode,
    searchKeyword: list.searchKeyword,
    statusFilter: list.statusFilter,
    courseIdFilter: list.courseIdFilter,
    listLoading: list.listLoading,
    loadError: list.loadError,
    examList: list.examList,
    currentExamPaper,
    sessionId,
    submitting,
    questions: sheet.questions,
    questionsLoading: sheet.questionsLoading,
    currentIndex: sheet.currentIndex,
    answers: sheet.answers,
    multiAnswers: sheet.multiAnswers,
    textAnswers: sheet.textAnswers,
    defaultScore,
    remainingMinutes: timers.remainingMinutes,
    countdownDisplay: timers.countdownDisplay,
    examResult,
    filteredExams: list.filteredExams,
    totalExams: list.totalExams,
    availableExams: list.availableExams,
    completedExams: list.completedExams,
    currentQuestion: sheet.currentQuestion,
    resultMessage,
    loadExamList: list.loadExamList,
    getExamStatusType: list.getExamStatusType,
    getExamStatusLabel: list.getExamStatusLabel,
    examJoinReason: list.examJoinReason,
    isExamJoinable: list.isExamJoinable,
    enterExam,
    loadQuestions: sheet.loadQuestions,
    goToQuestion: sheet.goToQuestion,
    prevQuestion: sheet.prevQuestion,
    nextQuestion: sheet.nextQuestion,
    isQuestionAnswered: sheet.isQuestionAnswered,
    getQuestionTypeName: sheet.getQuestionTypeName,
    getQuestionTypeTag: sheet.getQuestionTypeTag,
    parseOptions: sheet.parseOptions,
    confirmSubmit,
    confirmExitExam,
    backToList,
    viewDetailResult,
    getResultClass,
    formatTime,
  }
}
