import { describe, it, expect, beforeAll, beforeEach, vi } from 'vitest'

/**
 * useQuizTask 单元测试
 * <p>锁定随堂测验的作答口径：提交值必须是**选项字母**（A/B/C…），
 * 因为判分端 StudentTaskComponent#submitQuiz 逐题比较
 * normalize(correct_answers) == normalize(作答)，而题库 correct_answers 按字母存。
 * DR-25：此处曾送数组下标（0/1/2…），客观题即时判分恒答错。</p>
 */

const mocks = vi.hoisted(() => ({
  randomSelect: vi.fn(),
  submitQuiz: vi.fn(),
  message: { info: vi.fn(), success: vi.fn(), warning: vi.fn(), error: vi.fn() },
}))

vi.mock('@/api/question', () => ({
  default: { randomSelect: mocks.randomSelect },
}))

vi.mock('@/api/student', () => ({
  default: { submitQuiz: mocks.submitQuiz },
}))

vi.mock('element-plus', () => ({ ElMessage: mocks.message }))

let useQuizTask: typeof import('@/composables/useQuizTask').useQuizTask

beforeAll(async () => {
  useQuizTask = (await import('@/composables/useQuizTask')).useQuizTask
}, 60000)

// 用例间隔离：mocks 为 hoisted 单例，跨用例累计调用记录会让 not.toHaveBeenCalled 误判
beforeEach(() => vi.clearAllMocks())

/** 三道客观题：单选/多选/判断，选项均为 4 项，便于按下标→字母映射断言 */
const QUIZ_QUESTIONS = [
  { id: 21, type: 'SINGLE_CHOICE', content: '单选', options: '["一","二","三","四"]' },
  { id: 22, type: 'MULTIPLE_CHOICE', content: '多选', options: '["一","二","三","四"]' },
  { id: 23, type: 'TRUE_FALSE', content: '判断', options: null },
]

function mount() {
  return useQuizTask({
    courseId: 7,
    getTask: () => ({ id: 555 }),
    onTaskCompleted: async () => {},
  })
}

describe('useQuizTask - 作答提交口径', () => {
  it('单选/多选/判断分别送字母、字母串与 TRUE/FALSE', async () => {
    mocks.randomSelect.mockResolvedValue({ code: 200, data: QUIZ_QUESTIONS })
    mocks.submitQuiz.mockResolvedValue({
      code: 200,
      data: { total: 3, correct: 3, score: 100, details: [] },
    })

    const state = mount()
    await state.loadQuiz()

    // 学生点选：单选第 1 项、多选第 1+3 项、判断「正确」
    state.quizAnswers[21] = 'A'
    state.quizAnswers[22] = ['C', 'A']
    state.quizAnswers[23] = true
    await state.submitQuizNow()

    expect(mocks.submitQuiz).toHaveBeenCalledWith(7, 555, {
      21: 'A',
      22: 'A,C', // 多选按字母排序后逗号连接，与 normalize 的切分/排序口径对齐
      23: 'TRUE',
    })
  })

  it('存在未作答题目时拒绝提交，不发出请求', async () => {
    mocks.randomSelect.mockResolvedValue({ code: 200, data: QUIZ_QUESTIONS })

    const state = mount()
    await state.loadQuiz()
    state.quizAnswers[21] = 'B'

    await state.submitQuizNow()

    expect(mocks.submitQuiz).not.toHaveBeenCalled()
    expect(mocks.message.warning).toHaveBeenCalledWith('还有 2 道题未作答')
  })

  it('letter() 覆盖 6 项以上选项（A-F 后按 ASCII 扩展），避免下标回潮', () => {
    const state = mount()
    expect(['A', 'B', 'C', 'D', 'E', 'F', 'G', 'H'].map((_, i) => state.letter(i))).toEqual([
      'A',
      'B',
      'C',
      'D',
      'E',
      'F',
      'G',
      'H',
    ])
  })
})
