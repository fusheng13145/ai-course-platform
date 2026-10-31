import { afterEach, describe, expect, it, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const questionApi = vi.hoisted(() => ({
  getAllQuestions: vi.fn(),
  createQuestion: vi.fn(),
  updateQuestion: vi.fn(),
  deleteQuestion: vi.fn(),
  copyQuestion: vi.fn(),
  generateByAI: vi.fn(),
  reviewQuestion: vi.fn(),
}))
const courseApi = vi.hoisted(() => ({ getAllMyCourses: vi.fn() }))
const knowledgeApi = vi.hoisted(() => ({ getKnowledgePoints: vi.fn() }))
const toast = vi.hoisted(() => vi.fn())

vi.mock('@/api/question', () => questionApi)
vi.mock('@/api/course', () => courseApi)
vi.mock('@/api/knowledge', () => ({ default: knowledgeApi }))
vi.mock('@/utils/questionTransfer', () => ({
  createQuestionTransfer: () => ({
    handleExport: vi.fn(),
    handleImport: vi.fn(),
    busy: { value: false },
  }),
}))
vi.mock('@/composables/useQuestionBank', () => ({
  resolveReviewStatus: () => 'NONE',
}))
vi.mock('../composables/ui', () => ({
  useV2Ui: () => ({ toast, toasts: { value: [] }, dismissToast: vi.fn() }),
}))
vi.mock('@/utils/logger', () => ({ default: { error: vi.fn(), warn: vi.fn(), info: vi.fn() } }))

import TeacherBank from '../views/teacher/V2TeacherBankView.vue'

const KP_LIST = [
  { id: 11, name: 'TCP与传输层' },
  { id: 12, name: '三次握手' },
]
const modal = () => document.body.querySelector('.v2-modal-panel') as HTMLElement | null
const modalButtons = () => Array.from(modal()?.querySelectorAll('button') || [])
const modalButton = (label: string) =>
  modalButtons().find((node) => (node.textContent || '').includes(label)) as
    HTMLButtonElement | undefined

async function setTextarea(placeholder: string, value: string) {
  const el = modal()!.querySelector(`textarea[placeholder="${placeholder}"]`) as HTMLTextAreaElement
  el.value = value
  el.dispatchEvent(new Event('input'))
  await flushPromises()
}

beforeEach(() => {
  vi.clearAllMocks()
  courseApi.getAllMyCourses.mockResolvedValue([{ id: 1, name: 'Python程序设计' }])
  questionApi.getAllQuestions.mockResolvedValue([])
  questionApi.createQuestion.mockResolvedValue({ data: {} })
  knowledgeApi.getKnowledgePoints.mockResolvedValue({ data: KP_LIST })
})

afterEach(() => {
  document.body.replaceChildren()
})

describe('教师题库：题目知识点标签（批次 8）', () => {
  it('新建题目时按课程加载知识点选项，勾选后按 JSON 数组文本提交', async () => {
    const wrapper = mount(TeacherBank, { props: { courseId: 1 } })
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((n) => n.text().includes('新建题目'))!
      .trigger('click')
    await flushPromises()

    expect(knowledgeApi.getKnowledgePoints).toHaveBeenCalledWith(1)
    const chips = modal()!.querySelectorAll('.kp-chip input')
    expect(chips).toHaveLength(2)

    ;(chips[0] as HTMLInputElement).click()
    await flushPromises()
    await setTextarea('请输入题干内容', 'TCP 工作在哪一层？')
    await setTextarea('请输入参考答案', 'A')
    modalButton('保存')!.click()
    await flushPromises()

    expect(questionApi.createQuestion).toHaveBeenCalledTimes(1)
    const payload = questionApi.createQuestion.mock.calls[0][0]
    expect(payload.knowledgePoints).toBe('["TCP与传输层"]')
    // correct_answers 是 JSON 列：单答案必须是 JSON 字符串
    expect(payload.correctAnswers).toBe('"A"')
  })

  it('编辑既有题目时预填已选知识点（兼容 JSON 与逗号分隔两种历史形态）', async () => {
    questionApi.getAllQuestions.mockResolvedValue([
      {
        id: 501,
        content: '旧题（JSON 形态）',
        type: 'SINGLE_CHOICE',
        difficulty: 'MEDIUM',
        courseId: 1,
        correctAnswers: 'A',
        knowledgePoints: '["三次握手"]',
      },
      {
        id: 502,
        content: '旧题（逗号形态）',
        type: 'SINGLE_CHOICE',
        difficulty: 'MEDIUM',
        courseId: 1,
        correctAnswers: 'B',
        knowledgePoints: 'TCP与传输层，三次握手',
      },
    ])
    const wrapper = mount(TeacherBank, { props: { courseId: 1 } })
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((n) => n.text() === '编辑')!
      .trigger('click')
    await flushPromises()
    const checked = Array.from(
      modal()!.querySelectorAll('.kp-chip input:checked'),
    ) as HTMLInputElement[]
    expect(checked.map((el) => el.value)).toEqual(['三次握手'])

    modal()!.querySelector('button')?.click()
    await flushPromises()
  })

  it('清空勾选后保存会显式回写空字符串（否则后端保留旧标签）', async () => {
    questionApi.getAllQuestions.mockResolvedValue([
      {
        id: 601,
        content: '待清标签的题',
        type: 'SINGLE_CHOICE',
        difficulty: 'MEDIUM',
        courseId: 1,
        correctAnswers: 'A',
        knowledgePoints: '["三次握手"]',
      },
    ])
    const wrapper = mount(TeacherBank, { props: { courseId: 1 } })
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((n) => n.text() === '编辑')!
      .trigger('click')
    await flushPromises()
    const checked = modal()!.querySelector('.kp-chip input:checked') as HTMLInputElement
    checked.click()
    await flushPromises()
    modalButton('保存')!.click()
    await flushPromises()
    expect(questionApi.updateQuestion).toHaveBeenCalledWith(
      601,
      expect.objectContaining({ knowledgePoints: '' }),
    )
  })

  it('多选题答案按 JSON 数组编码（与后端 JSON 列对齐）', async () => {
    const wrapper = mount(TeacherBank, { props: { courseId: 1 } })
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((n) => n.text().includes('新建题目'))!
      .trigger('click')
    await flushPromises()
    const selects = modal()!.querySelectorAll('select')
    // 第一个 select 是题型
    ;(selects[0] as HTMLSelectElement).value = 'MULTIPLE_CHOICE'
    selects[0].dispatchEvent(new Event('change'))
    await flushPromises()
    await setTextarea('请输入题干内容', '以下哪些属于传输层协议？')
    await setTextarea('请输入参考答案', 'A，C')
    modalButton('保存')!.click()
    await flushPromises()
    const payload = questionApi.createQuestion.mock.calls[0][0]
    expect(payload.correctAnswers).toBe('["A","C"]')
  })

  it('编辑回填时剥掉 JSON 引号，展示为纯文本', async () => {
    questionApi.getAllQuestions.mockResolvedValue([
      {
        id: 701,
        content: '已存 JSON 答案的题',
        type: 'SINGLE_CHOICE',
        difficulty: 'MEDIUM',
        courseId: 1,
        correctAnswers: '"B"',
        knowledgePoints: '["三次握手"]',
      },
    ])
    const wrapper = mount(TeacherBank, { props: { courseId: 1 } })
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((n) => n.text() === '编辑')!
      .trigger('click')
    await flushPromises()
    const answerBox = modal()!.querySelector(
      'textarea[placeholder="请输入参考答案"]',
    ) as HTMLTextAreaElement
    expect(answerBox.value).toBe('B')
  })

  it('课程没有知识点时给出可执行提示，而不是空白选择区', async () => {
    knowledgeApi.getKnowledgePoints.mockResolvedValue({ data: [] })
    const wrapper = mount(TeacherBank, { props: { courseId: 1 } })
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((n) => n.text().includes('新建题目'))!
      .trigger('click')
    await flushPromises()
    expect(modal()!.textContent).toContain('该课程还没有知识点')
  })
})
