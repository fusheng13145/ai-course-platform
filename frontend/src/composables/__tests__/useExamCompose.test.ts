import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import {
  useExamCompose,
  questionContentOf,
  type AutoComposePayload,
  type BankQuestion,
} from '@/composables/useExamCompose'

const mocks = vi.hoisted(() => ({
  getQuestionList: vi.fn(),
  autoComposeExamPaper: vi.fn(),
  getExamQuestions: vi.fn(),
  message: Object.assign(vi.fn(), {
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  }),
}))

vi.mock('@/api/question', () => ({
  default: {
    getQuestionList: mocks.getQuestionList,
  },
}))
vi.mock('@/api/exam', () => ({
  default: {
    autoComposeExamPaper: mocks.autoComposeExamPaper,
    getExamQuestions: mocks.getExamQuestions,
  },
}))
vi.mock('element-plus', () => ({ ElMessage: mocks.message }))

import { ElMessage } from 'element-plus'

const message = ElMessage as unknown as typeof mocks.message

function makeQuestion(overrides: Partial<BankQuestion>): BankQuestion {
  return { id: 1, type: 'SINGLE_CHOICE', content: '题干', score: 2, ...overrides }
}

function setup() {
  const courseId = ref<number | null>(7)
  const formId = ref<number | null>(null)
  const totalScore = ref(100)
  const compose = useExamCompose({
    courseId,
    form: { id: formId, totalScore },
  })
  return { courseId, formId, totalScore, compose }
}

describe('useExamCompose - 题库加载', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loadBank 按 courseId+分页请求；无筛选时不带 type；列表/总数走 unwrap/pickList/readTotal', async () => {
    mocks.getQuestionList.mockResolvedValue({
      code: 200,
      data: { records: [makeQuestion({ id: 1 }), makeQuestion({ id: 2 })], total: 12 },
    })
    const { compose } = setup()
    await compose.loadBank()

    expect(mocks.getQuestionList).toHaveBeenCalledWith({
      courseId: 7,
      page: 1,
      size: 10,
    })
    expect(compose.bankQuestions.value.map((q) => q.id)).toEqual([1, 2])
    expect(compose.bankTotal.value).toBe(12)
  })

  it('题型筛选后请求携带 type', async () => {
    mocks.getQuestionList.mockResolvedValue({ code: 200, data: { records: [], total: 0 } })
    const { compose } = setup()
    compose.bankType.value = 'SINGLE_CHOICE'
    await compose.loadBank()

    expect(mocks.getQuestionList).toHaveBeenCalledWith({
      courseId: 7,
      page: 1,
      size: 10,
      type: 'SINGLE_CHOICE',
    })
  })

  it('加载失败：清空列表、错误提示（失败不得伪装成「暂无题目」的静默空态）', async () => {
    mocks.getQuestionList.mockRejectedValue(new Error('500'))
    const { compose } = setup()
    compose.bankQuestions.value = [makeQuestion({ id: 9 })]
    await compose.loadBank()

    expect(compose.bankQuestions.value).toEqual([])
    expect(compose.bankTotal.value).toBe(0)
    expect(message.error).toHaveBeenCalledWith('题库加载失败，请检查网络后重试')
  })

  it('courseId 为 null 时不发起请求', async () => {
    const { courseId, compose } = setup()
    courseId.value = null
    await compose.loadBank()
    expect(mocks.getQuestionList).not.toHaveBeenCalled()
  })
})

describe('useExamCompose - 勾选与合计', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('勾选默认取题目 score；再点取消；selectedTotalScore 实时合计', () => {
    const { compose } = setup()
    const q = makeQuestion({ id: 1, score: 3 })
    compose.toggleQuestion(q)
    expect(compose.isSelected(1)).toBe(true)
    expect(compose.selectedQuestions.value[0]).toEqual({
      questionId: 1,
      type: 'SINGLE_CHOICE',
      content: '题干',
      score: 3,
    })

    const q2 = makeQuestion({ id: 2, score: 2.5 })
    compose.toggleQuestion(q2)
    expect(compose.selectedTotalScore.value).toBe(5.5)

    compose.toggleQuestion(q2)
    expect(compose.isSelected(2)).toBe(false)
    expect(compose.selectedTotalScore.value).toBe(3)
  })

  it('题目无 score 时分值回落默认 1 分', () => {
    const { compose } = setup()
    compose.toggleQuestion(makeQuestion({ id: 3, score: null }))
    expect(compose.selectedQuestions.value[0].score).toBe(1)
  })

  it('updatePoints 修改已选分值；非法值与未选题不动', () => {
    const { compose } = setup()
    compose.toggleQuestion(makeQuestion({ id: 1, score: 2 }))
    compose.updatePoints(1, 5)
    expect(compose.selectedQuestions.value[0].score).toBe(5)

    compose.updatePoints(1, 0)
    expect(compose.selectedQuestions.value[0].score).toBe(5)

    compose.updatePoints(99, 5)
    expect(compose.selectedQuestions.value).toHaveLength(1)
  })

  it('questionContentOf：content 为主，stem/title 兜底，空返回空串', () => {
    expect(questionContentOf(makeQuestion({ content: '主题干', stem: '旧题干' }))).toBe('主题干')
    expect(questionContentOf(makeQuestion({ content: null, stem: '旧题干' }))).toBe('旧题干')
    expect(questionContentOf(makeQuestion({ content: null, stem: null, title: '标题' }))).toBe(
      '标题',
    )
    expect(questionContentOf(makeQuestion({ content: '', stem: '', title: '' }))).toBe('')
    expect(questionContentOf(null)).toBe('')
  })
})

describe('useExamCompose - 编辑回显', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('restoreFromPaper：getExamQuestions 归一化扁平题目 → selectedQuestions', async () => {
    // getExamQuestions 在 API 层已把 { id, orderIndex, points, question } 包装结构
    // 归一化为扁平题目对象（score = 关联行 points），mock 直接给契约形状。
    mocks.getExamQuestions.mockResolvedValue({
      code: 200,
      data: [
        { id: 11, orderIndex: 1, score: 5, type: 'SINGLE_CHOICE', content: '甲' },
        { id: 12, orderIndex: 2, score: 10, type: 'ESSAY', content: '乙' },
      ],
    })
    const { compose } = setup()
    await compose.restoreFromPaper(88)

    expect(mocks.getExamQuestions).toHaveBeenCalledWith(88)
    expect(compose.selectedQuestions.value).toEqual([
      { questionId: 11, type: 'SINGLE_CHOICE', content: '甲', score: 5 },
      { questionId: 12, type: 'ESSAY', content: '乙', score: 10 },
    ])
  })

  it('restoreFromPaper 失败时抛出（宿主提示「不改动已有题目」），不清空现状', async () => {
    mocks.getExamQuestions.mockRejectedValue(new Error('500'))
    const { compose } = setup()
    compose.toggleQuestion(makeQuestion({ id: 1 }))
    await expect(compose.restoreFromPaper(88)).rejects.toThrow('500')
    expect(compose.selectedQuestions.value).toHaveLength(1)
  })
})

describe('useExamCompose - 自动组卷', () => {
  const basePayload: AutoComposePayload = {
    courseId: 7,
    title: '期中卷',
    typeRules: { SINGLE: 5, ESSAY: 2 },
  }

  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('autoCompose：调 autoComposeExamPaper → form.id 切新卷 → 题目回显 → 返回新卷', async () => {
    const newPaper = { id: 88, title: '期中卷', status: 'DRAFT' }
    mocks.autoComposeExamPaper.mockResolvedValue({ code: 200, data: newPaper })
    mocks.getExamQuestions.mockResolvedValue({
      code: 200,
      data: [{ id: 11, points: 5, question: { id: 11, type: 'SINGLE_CHOICE', content: '甲' } }],
    })
    const { formId, compose } = setup()
    const paper = await compose.autoCompose({ ...basePayload })

    expect(mocks.autoComposeExamPaper).toHaveBeenCalledWith({ ...basePayload })
    expect(paper).toEqual(newPaper)
    expect(formId.value).toBe(88)
    expect(compose.selectedQuestions.value.map((q) => q.questionId)).toEqual([11])
  })

  it('autoCompose 失败返回 null 并提示，不写 form.id', async () => {
    mocks.autoComposeExamPaper.mockRejectedValue(new Error('500'))
    const { formId, compose } = setup()
    const paper = await compose.autoCompose({ ...basePayload })

    expect(paper).toBeNull()
    expect(formId.value).toBeNull()
    expect(message.error).toHaveBeenCalledWith('自动组卷失败，请稍后重试')
  })

  it('返回体缺 id：判为异常返回 null（不建幽灵编辑态）', async () => {
    mocks.autoComposeExamPaper.mockResolvedValue({ code: 200, data: { title: '无 id' } })
    const { compose } = setup()
    expect(await compose.autoCompose({ ...basePayload })).toBeNull()
  })

  it('题目回显失败不吞掉建卷结果：仍返回新卷并警示', async () => {
    mocks.autoComposeExamPaper.mockResolvedValue({ code: 200, data: { id: 88, title: '期中卷' } })
    mocks.getExamQuestions.mockRejectedValue(new Error('500'))
    const { formId, compose } = setup()
    const paper = await compose.autoCompose({ ...basePayload })

    expect(paper?.id).toBe(88)
    expect(formId.value).toBe(88)
    expect(message.warning).toHaveBeenCalledWith(
      '自动组卷完成，但题目清单回显失败，请重新打开该试卷查看',
    )
  })
})

describe('useExamCompose - reset', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('reset：清空题库/勾选/筛选/页码', async () => {
    mocks.getQuestionList.mockResolvedValue({
      code: 200,
      data: { records: [makeQuestion({})], total: 1 },
    })
    const { compose } = setup()
    await compose.loadBank()
    compose.toggleQuestion(makeQuestion({ id: 1 }))
    compose.bankType.value = 'JUDGE'
    compose.bankPage.value = 3

    compose.reset()

    expect(compose.bankQuestions.value).toEqual([])
    expect(compose.bankTotal.value).toBe(0)
    expect(compose.bankPage.value).toBe(1)
    expect(compose.bankType.value).toBe('')
    expect(compose.selectedQuestions.value).toEqual([])
  })
})
