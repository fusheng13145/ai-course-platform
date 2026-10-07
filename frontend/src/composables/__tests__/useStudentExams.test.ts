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
})
