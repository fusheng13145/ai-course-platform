import { describe, it, expect, beforeAll, beforeEach, afterEach, vi } from 'vitest'

/**
 * useStudentExams 单元测试
 * <p>聚焦 #18「学生中途退出考试后无法再进入」的修复：进入考试时应先查最新会话，
 * 存在 STARTED 会话则复用（恢复题目 + 已保存作答 + 服务端剩余时间），否则才新建。</p>
 */

const mocks = vi.hoisted(() => ({
  startExam: vi.fn(),
  getMyExamSession: vi.fn(),
  getExamQuestions: vi.fn(),
  getRemainingTime: vi.fn(),
  autoSaveAnswers: vi.fn(),
  submitExam: vi.fn(),
  getExamResult: vi.fn(),
  getExamPaperList: vi.fn(),
  getAllExamPapers: vi.fn(),
  getAllExamPapersByCourse: vi.fn(),
  getMyExamSessions: vi.fn(),
  message: { info: vi.fn(), success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  confirm: vi.fn(),
}))

vi.mock('@/api/exam', () => ({
  default: {
    startExam: mocks.startExam,
    getMyExamSession: mocks.getMyExamSession,
    getExamQuestions: mocks.getExamQuestions,
    getRemainingTime: mocks.getRemainingTime,
    autoSaveAnswers: mocks.autoSaveAnswers,
    submitExam: mocks.submitExam,
    getExamResult: mocks.getExamResult,
    getExamPaperList: mocks.getExamPaperList,
    getAllExamPapers: mocks.getAllExamPapers,
    getAllExamPapersByCourse: mocks.getAllExamPapersByCourse,
    getMyExamSessions: mocks.getMyExamSessions,
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.message,
  ElMessageBox: { confirm: mocks.confirm },
}))

// 组合式函数在组件外直接调用时 Vue 会对 onMounted/onUnmounted 告警，
// 生命周期不是本用例的测试目标，桩掉以保持输出干净（ref/computed 等仍为真实实现）
vi.mock('vue', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue')>()
  return { ...actual, onMounted: vi.fn(), onUnmounted: vi.fn() }
})

type UseStudentExamsFn = typeof import('@/composables/useStudentExams').useStudentExams

let useStudentExams: UseStudentExamsFn

// 冷启动陷阱：首次解析 @/api/request 依赖链在本机可能超过 5s，故放宽 beforeAll 超时
beforeAll(async () => {
  const mod = await import('@/composables/useStudentExams')
  useStudentExams = mod.useStudentExams
}, 60000)

/**
 * 三道题：单选 / 多选 / 简答，ID 故意与下标不同，用于验证「按题目ID匹配」而非「按下标匹配」。
 *
 * 契约边界说明（2026-09-21）：本文件 mock 的是 `@/api/exam`，即**已经过 API 层归一化**的
 * 输出——扁平结构、`id` 为真实题目 id。归一化本身（后端包装结构 `{id, orderIndex, points,
 * question:{…}}` → 扁平）由 `src/api/__tests__/exam.test.ts` 覆盖，其中用的是从运行实例
 * 真实抓取的响应形态。两者合起来才构成完整契约链，改动归一化请同步该文件。
 */
const QUESTIONS = [
  { id: 11, type: 'SINGLE_CHOICE', content: '单选题' },
  { id: 12, type: 'MULTIPLE_CHOICE', content: '多选题' },
  { id: 13, type: 'SHORT_ANSWER', content: '简答题' },
]

const EXAM = { id: 5, title: '期中考试', duration: 60 }

beforeEach(() => {
  vi.clearAllMocks()
  // 倒计时/自动保存会注册 setInterval，用假定时器避免测试进程残留定时器
  vi.useFakeTimers()
  mocks.confirm.mockResolvedValue(undefined)
  mocks.getExamQuestions.mockResolvedValue({ code: 200, data: QUESTIONS })
  mocks.autoSaveAnswers.mockResolvedValue({ code: 200, data: null })
  mocks.submitExam.mockResolvedValue({ code: 200, data: { id: 77, status: 'SUBMITTED' } })
  mocks.getExamResult.mockResolvedValue({ code: 200, data: { totalScore: 88 } })
})

afterEach(() => {
  vi.clearAllTimers()
  vi.useRealTimers()
})

describe('useStudentExams - 中途退出后重新进入（#18）', () => {
  it('存在 STARTED 会话时：复用该会话，不调用 startExam', async () => {
    mocks.getMyExamSession.mockResolvedValue({
      code: 200,
      data: { id: 77, status: 'STARTED', answers: JSON.stringify({ 11: 'B' }) },
    })
    mocks.getRemainingTime.mockResolvedValue({ code: 200, data: 1234 })

    const state = useStudentExams()
    await state.enterExam(EXAM)

    expect(mocks.getMyExamSession).toHaveBeenCalledWith(5)
    expect(mocks.startExam).not.toHaveBeenCalled()
    expect(state.sessionId.value).toBe(77)
    expect(state.viewMode.value).toBe('exam')
  })

  it('恢复时应回填已保存作答（按题目ID匹配，非按下标）', async () => {
    mocks.getMyExamSession.mockResolvedValue({
      code: 200,
      data: {
        id: 77,
        status: 'STARTED',
        answers: JSON.stringify({ 11: 'B', 12: ['A', 'C'], 13: '简答题答案' }),
      },
    })
    mocks.getRemainingTime.mockResolvedValue({ code: 200, data: 1234 })

    const state = useStudentExams()
    await state.enterExam(EXAM)

    expect(state.answers.value[0]).toBe('B')
    expect(state.multiAnswers.value[1]).toEqual(['A', 'C'])
    expect(state.textAnswers.value[2]).toBe('简答题答案')
  })

  it('恢复时应沿用服务端剩余时间，而不是重置为试卷满时长', async () => {
    mocks.getMyExamSession.mockResolvedValue({
      code: 200,
      data: { id: 77, status: 'STARTED', answers: '{}' },
    })
    mocks.getRemainingTime.mockResolvedValue({ code: 200, data: 1234 })

    const state = useStudentExams()
    await state.enterExam(EXAM)

    // remainingSeconds/totalSeconds 为内部状态，经 remainingMinutes / countdownDisplay 暴露
    expect(state.remainingMinutes.value).toBe(20)
    expect(state.countdownDisplay.value).toBe('20:34')
    expect(mocks.message.success).toHaveBeenCalledWith('已恢复上次未完成的作答，请继续答题')
  })

  it('兼容历史数组形态的已保存作答', async () => {
    mocks.getMyExamSession.mockResolvedValue({
      code: 200,
      data: {
        id: 88,
        status: 'STARTED',
        answers: JSON.stringify([{ questionId: 11, answer: 'D' }]),
      },
    })
    mocks.getRemainingTime.mockResolvedValue({ code: 200, data: 900 })

    const state = useStudentExams()
    await state.enterExam(EXAM)

    expect(state.answers.value[0]).toBe('D')
  })

  it('已保存作答为非法 JSON 时不应阻断进入考试', async () => {
    mocks.getMyExamSession.mockResolvedValue({
      code: 200,
      data: { id: 77, status: 'STARTED', answers: '{not-json' },
    })
    mocks.getRemainingTime.mockResolvedValue({ code: 200, data: 1000 })

    const state = useStudentExams()
    await state.enterExam(EXAM)

    expect(state.viewMode.value).toBe('exam')
    expect(state.answers.value[0]).toBeUndefined()
  })

  it('无进行中会话时：走新建流程并使用试卷满时长', async () => {
    mocks.getMyExamSession.mockResolvedValue({
      code: 200,
      data: { id: 9, status: 'SUBMITTED' },
    })
    mocks.startExam.mockResolvedValue({ code: 200, data: { id: 100, status: 'STARTED' } })

    const state = useStudentExams()
    await state.enterExam(EXAM)

    expect(mocks.startExam).toHaveBeenCalledWith(5)
    expect(state.sessionId.value).toBe(100)
    expect(state.remainingMinutes.value).toBe(60)
    expect(state.countdownDisplay.value).toBe('60:00')
    expect(mocks.message.success).toHaveBeenCalledWith('考试已开始，祝你好运！')
  })

  it('查询最新会话失败时：降级为新建会话，不阻断学生进入考试', async () => {
    mocks.getMyExamSession.mockRejectedValue(new Error('500'))
    mocks.startExam.mockResolvedValue({ code: 200, data: { id: 200, status: 'STARTED' } })

    const state = useStudentExams()
    await state.enterExam(EXAM)

    expect(mocks.startExam).toHaveBeenCalledWith(5)
    expect(state.sessionId.value).toBe(200)
  })

  it('用户取消开始确认时不应发起任何考试接口调用', async () => {
    mocks.confirm.mockRejectedValue(new Error('cancel'))

    const state = useStudentExams()
    await state.enterExam(EXAM)

    expect(mocks.getMyExamSession).not.toHaveBeenCalled()
    expect(mocks.startExam).not.toHaveBeenCalled()
    expect(state.viewMode.value).toBe('list')
  })
})

describe('useStudentExams - 退出与提交', () => {
  it('退出考试应保存进度并清空本地会话态，但不得提交试卷', async () => {
    mocks.getMyExamSession.mockResolvedValue({ code: 200, data: null })
    mocks.startExam.mockResolvedValue({ code: 200, data: { id: 300, status: 'STARTED' } })

    const state = useStudentExams()
    await state.enterExam(EXAM)
    state.answers.value[0] = 'B'
    await state.confirmExitExam()

    expect(mocks.autoSaveAnswers).toHaveBeenCalledWith(300, {
      answers: { 11: 'B', 12: [], 13: '' },
    })
    expect(mocks.submitExam).not.toHaveBeenCalled()
    expect(state.sessionId.value).toBeNull()
    expect(state.viewMode.value).toBe('list')
  })

  it('提交试卷应按「题目ID → 答案」对象提交（后端 ExamSubmitDTO.answers 为 Map）', async () => {
    mocks.getMyExamSession.mockResolvedValue({ code: 200, data: null })
    mocks.startExam.mockResolvedValue({ code: 200, data: { id: 400, status: 'STARTED' } })

    const state = useStudentExams()
    await state.enterExam(EXAM)
    state.answers.value[0] = 'C'
    // 多选按**选项字母**上报，与题库 correct_answers 口径一致（DR-25：曾用数组下标，自动阅卷恒判错）
    state.multiAnswers.value[1] = ['A', 'C']
    state.textAnswers.value[2] = '作答内容'
    // submitExam 为内部方法，经 confirmSubmit（确认框已 mock 为直接确认）触发
    await state.confirmSubmit()

    expect(mocks.submitExam).toHaveBeenCalledWith(400, {
      answers: { 11: 'C', 12: ['A', 'C'], 13: '作答内容' },
    })
  })

  it('题目加载失败或试卷为空时不进入空白答题页', async () => {
    mocks.getMyExamSession.mockResolvedValue({ code: 200, data: null })
    mocks.startExam.mockResolvedValue({ code: 200, data: { id: 401, status: 'STARTED' } })
    mocks.getExamQuestions.mockResolvedValue({ code: 200, data: [] })

    const state = useStudentExams()
    await state.enterExam(EXAM)

    expect(state.viewMode.value).toBe('list')
    expect(state.questions.value).toEqual([])
    expect(mocks.message.error).toHaveBeenCalledWith('该试卷暂无可用题目，请联系老师确认后重试')
  })

  it('交卷失败时保留答题页和自动保存，成功重试后才清理定时器', async () => {
    mocks.getMyExamSession.mockResolvedValue({ code: 200, data: null })
    mocks.startExam.mockResolvedValue({ code: 200, data: { id: 402, status: 'STARTED' } })
    mocks.submitExam.mockRejectedValueOnce(new Error('offline'))

    const state = useStudentExams()
    await state.enterExam(EXAM)
    state.answers.value[0] = 'B'
    await state.confirmSubmit()

    expect(state.viewMode.value).toBe('exam')
    expect(state.submitting.value).toBe(false)
    expect(vi.getTimerCount()).toBe(2)
    expect(mocks.message.error).toHaveBeenCalledWith('提交失败，作答仍保留在本页；请检查网络后重试')

    await state.confirmSubmit()

    expect(state.viewMode.value).toBe('result')
    expect(vi.getTimerCount()).toBe(0)
  })

  it('交卷进行中忽略重复提交并锁定答题交互状态', async () => {
    mocks.getMyExamSession.mockResolvedValue({ code: 200, data: null })
    mocks.startExam.mockResolvedValue({ code: 200, data: { id: 403, status: 'STARTED' } })
    let resolveSubmit: (value: unknown) => void = () => {}
    mocks.submitExam.mockImplementation(() => new Promise((resolve) => (resolveSubmit = resolve)))

    const state = useStudentExams()
    await state.enterExam(EXAM)
    const firstSubmit = state.confirmSubmit()
    await Promise.resolve()
    const confirmCalls = mocks.confirm.mock.calls.length
    await state.confirmSubmit()

    expect(state.submitting.value).toBe(true)
    expect(mocks.confirm).toHaveBeenCalledTimes(confirmCalls)
    resolveSubmit({ code: 200, data: { id: 403, status: 'SUBMITTED' } })
    await firstSubmit
    expect(state.viewMode.value).toBe('result')
  })

  it('退出前自动保存失败时保留本地答卷，不清空考试会话', async () => {
    mocks.getMyExamSession.mockResolvedValue({ code: 200, data: null })
    mocks.startExam.mockResolvedValue({ code: 200, data: { id: 404, status: 'STARTED' } })
    mocks.autoSaveAnswers.mockRejectedValue(new Error('offline'))

    const state = useStudentExams()
    await state.enterExam(EXAM)
    state.answers.value[0] = 'A'
    await state.confirmExitExam()

    expect(state.viewMode.value).toBe('exam')
    expect(state.sessionId.value).toBe(404)
    expect(state.answers.value[0]).toBe('A')
    expect(vi.getTimerCount()).toBe(2)
    expect(mocks.submitExam).not.toHaveBeenCalled()
  })
})

describe('useStudentExams - 定时器边界（倒计时与自动保存）', () => {
  /** 进入一场指定时长的考试，返回 state（倒计时与自动保存共注册两个 interval） */
  async function enterExamOf(duration: number) {
    mocks.getMyExamSession.mockResolvedValue({ code: 200, data: null })
    mocks.startExam.mockResolvedValue({ code: 200, data: { id: 500, status: 'STARTED' } })

    const state = useStudentExams()
    await state.enterExam({ id: 6, title: '随堂小测', duration })
    return state
  }

  it('倒计时归零自动交卷；不出现「剩余时间：0 分钟」的误导提示', async () => {
    const state = await enterExamOf(1)
    state.answers.value[0] = 'A'
    mocks.message.warning.mockClear()

    // 1 分钟 = 60 个 tick；归零后的下一个 tick 触发自动交卷
    await vi.advanceTimersByTimeAsync(61 * 1000)

    expect(mocks.message.warning).not.toHaveBeenCalledWith('剩余时间：0 分钟')
    expect(mocks.message.warning).toHaveBeenCalledWith('考试时间已到，将自动提交试卷')
    expect(mocks.submitExam).toHaveBeenCalledWith(500, { answers: { 11: 'A', 12: [], 13: '' } })
    expect(state.viewMode.value).toBe('result')
    expect(vi.getTimerCount()).toBe(0)
  })

  it('每 30 秒自动保存一次，成功后保持静默（不打扰答题）', async () => {
    const state = await enterExamOf(10)
    mocks.message.success.mockClear()

    await vi.advanceTimersByTimeAsync(30 * 1000)

    expect(mocks.autoSaveAnswers).toHaveBeenCalledTimes(1)
    expect(mocks.message.success).not.toHaveBeenCalled()
    expect(state.viewMode.value).toBe('exam')
  })

  it('自动保存连续失败只提示一次；网络恢复后明确提示已重新同步', async () => {
    const state = await enterExamOf(10)
    mocks.message.warning.mockClear()
    mocks.message.success.mockClear()
    mocks.autoSaveAnswers
      .mockRejectedValueOnce(new Error('offline'))
      .mockRejectedValueOnce(new Error('offline'))

    await vi.advanceTimersByTimeAsync(30 * 1000)
    expect(mocks.message.warning).toHaveBeenCalledTimes(1)
    expect(mocks.message.warning).toHaveBeenCalledWith(
      '网络异常，本次自动保存未成功；请勿关闭页面，作答已暂存本地并会持续重试',
    )

    // 第二次仍失败：不重复打扰
    await vi.advanceTimersByTimeAsync(30 * 1000)
    expect(mocks.message.warning).toHaveBeenCalledTimes(1)

    // 第三次恢复：显式告知已重新同步
    await vi.advanceTimersByTimeAsync(30 * 1000)
    expect(mocks.message.success).toHaveBeenCalledWith('网络恢复，作答已重新同步到服务端')
    expect(state.viewMode.value).toBe('exam')
  })

  it('倒计时归零自动交卷失败时周期性重试，网络恢复后成功提交并进入结果页', async () => {
    const state = await enterExamOf(1)
    state.answers.value[0] = 'A'
    // 归零触发的首次交卷与 10s 后首次重试均失败，第二次重试（再 10s）成功
    mocks.submitExam
      .mockRejectedValueOnce(new Error('offline'))
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce({ code: 200, data: { id: 500, status: 'SUBMITTED' } })

    // 归零触发首次自动交卷（失败）→ 安排 10s 后重试；倒计时已清，自动保存仍在跑
    await vi.advanceTimersByTimeAsync(61 * 1000)
    expect(mocks.submitExam).toHaveBeenCalledTimes(1)
    expect(state.viewMode.value).toBe('exam')
    expect(vi.getTimerCount()).toBe(2) // 自动保存 1 + 交卷重试 1

    // 第一次重试（10s 后）仍失败
    await vi.advanceTimersByTimeAsync(10 * 1000)
    expect(mocks.submitExam).toHaveBeenCalledTimes(2)
    expect(state.viewMode.value).toBe('exam')

    // 第二次重试（再过 10s）成功 → 切结果页并清理全部定时器
    await vi.advanceTimersByTimeAsync(10 * 1000)
    expect(mocks.submitExam).toHaveBeenCalledTimes(3)
    expect(state.viewMode.value).toBe('result')
    expect(vi.getTimerCount()).toBe(0)
  })

  it('倒计时归零自动交卷失败：保留答题页、submitting 复位、自动保存持续重试', async () => {
    const state = await enterExamOf(1)
    state.answers.value[0] = 'A'
    mocks.submitExam.mockRejectedValue(new Error('offline'))
    mocks.message.error.mockClear()

    await vi.advanceTimersByTimeAsync(61 * 1000)

    expect(state.viewMode.value).toBe('exam')
    expect(state.submitting.value).toBe(false)
    expect(mocks.message.error).toHaveBeenCalledWith('提交失败，作答仍保留在本页；请检查网络后重试')
    // 交卷失败后自动保存定时器仍在运行（持续同步作答到服务端），且已安排交卷重试
    expect(vi.getTimerCount()).toBe(2)
  })
})

describe('useStudentExams - 列表加载失败可见', () => {
  it('考试列表请求失败时必须给出持久提示，且不得表现为"暂无考试"', async () => {
    mocks.getAllExamPapers.mockRejectedValue(new Error('500'))
    const state = useStudentExams()

    await state.loadExamList()

    expect(state.loadError.value).toBe('考试列表加载失败，请检查网络后重试')
    expect(state.examList.value).toEqual([])
    expect(state.listLoading.value).toBe(false)
  })

  it('阴性对照：加载成功时不得出现失败提示', async () => {
    mocks.getAllExamPapers.mockResolvedValue([{ id: 1, title: '期中考试' }])
    mocks.getMyExamSessions.mockResolvedValue({ code: 200, data: [] })
    const state = useStudentExams()

    await state.loadExamList()

    expect(state.loadError.value).toBe('')
    expect(state.examList.value).toHaveLength(1)
  })
})
