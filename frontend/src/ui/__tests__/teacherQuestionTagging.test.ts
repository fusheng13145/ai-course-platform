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
  useUi: () => ({ toast, toasts: { value: [] }, dismissToast: vi.fn() }),
}))
vi.mock('@/utils/logger', () => ({ default: { error: vi.fn(), warn: vi.fn(), info: vi.fn() } }))

import TeacherBank from '../views/teacher/TeacherBankView.vue'

const KP_LIST = [
  { id: 11, name: 'TCP与传输层' },
  { id: 12, name: '三次握手' },
]
const modal = () => document.body.querySelector('.modal-panel') as HTMLElement | null
const modalButton = (label: string) =>
  Array.from(modal()?.querySelectorAll('button') || []).find((node) =>
    (node.textContent || '').includes(label),
  ) as HTMLButtonElement | undefined
const modalInputs = (selector: string) =>
  Array.from(modal()!.querySelectorAll(selector)) as HTMLInputElement[]

async function setTextarea(placeholder: string, value: string) {
  const el = modal()!.querySelector(`textarea[placeholder="${placeholder}"]`) as HTMLTextAreaElement
  el.value = value
  el.dispatchEvent(new Event('input'))
  await flushPromises()
}

async function setOptions(values: string[]) {
  const inputs = modalInputs('.opt-row input')
  values.forEach((value, index) => {
    inputs[index].value = value
    inputs[index].dispatchEvent(new Event('input'))
  })
  await flushPromises()
}

async function pickType(value: string) {
  const el = modal()!.querySelectorAll('select')[0] as HTMLSelectElement
  el.value = value
  el.dispatchEvent(new Event('change'))
  await flushPromises()
}

const openCreate = async (wrapper: ReturnType<typeof mount>) => {
  await wrapper
    .findAll('button')
    .find((n) => n.text().includes('新建题目'))!
    .trigger('click')
  await flushPromises()
}

beforeEach(() => {
  vi.clearAllMocks()
  courseApi.getAllMyCourses.mockResolvedValue([{ id: 1, name: 'Python程序设计' }])
  questionApi.getAllQuestions.mockResolvedValue([])
  questionApi.createQuestion.mockResolvedValue({ data: {} })
  questionApi.updateQuestion.mockResolvedValue({ data: {} })
  knowledgeApi.getKnowledgePoints.mockResolvedValue({ data: KP_LIST })
})

afterEach(() => {
  document.body.replaceChildren()
})

describe('教师题库：知识点标签（批次 8）', () => {
  it('新建题目时按课程加载知识点选项，勾选后按 JSON 数组文本提交', async () => {
    const wrapper = mount(TeacherBank, { props: { courseId: 1 } })
    await flushPromises()
    await openCreate(wrapper)

    expect(knowledgeApi.getKnowledgePoints).toHaveBeenCalledWith(1)
    const chips = modalInputs('.kp-chip input')
    expect(chips).toHaveLength(2)

    chips[0].click()
    await flushPromises()
    await setTextarea('请输入题干内容', 'TCP 工作在哪一层？')
    await setOptions(['网络层', '传输层'])
    await setTextarea('请输入参考答案', 'A')
    modalButton('保存')!.click()
    await flushPromises()

    expect(questionApi.createQuestion).toHaveBeenCalledTimes(1)
    const payload = questionApi.createQuestion.mock.calls[0][0]
    expect(payload.knowledgePoints).toBe('["TCP与传输层"]')
    expect(payload.correctAnswers).toBe('"A"')
  })

  it('编辑回填：剥掉答案 JSON 引号，解析既有选项与知识点', async () => {
    questionApi.getAllQuestions.mockResolvedValue([
      {
        id: 701,
        content: '已存 JSON 答案的题',
        type: 'SINGLE_CHOICE',
        difficulty: 'MEDIUM',
        courseId: 1,
        correctAnswers: '"B"',
        options: '["甲","乙","丙"]',
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
    expect(modalInputs('.opt-row input').map((el) => el.value)).toEqual(['甲', '乙', '丙'])
    expect(modalInputs('.kp-chip input:checked').map((el) => el.value)).toEqual(['三次握手'])
  })

  it('清空勾选时按空 JSON 数组提交（[] 表示无标签），不再拦截', async () => {
    questionApi.getAllQuestions.mockResolvedValue([
      {
        id: 601,
        content: '待清标签的题',
        type: 'SINGLE_CHOICE',
        difficulty: 'MEDIUM',
        courseId: 1,
        correctAnswers: '"A"',
        options: '["甲","乙"]',
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
    modalInputs('.kp-chip input:checked')[0].click()
    await flushPromises()
    modalButton('保存')!.click()
    await flushPromises()
    expect(questionApi.updateQuestion).toHaveBeenCalledTimes(1)
    expect(questionApi.updateQuestion.mock.calls[0][1].knowledgePoints).toBe('[]')
  })

  it('课程没有知识点时给出可执行提示，而不是空白选择区', async () => {
    knowledgeApi.getKnowledgePoints.mockResolvedValue({ data: [] })
    const wrapper = mount(TeacherBank, { props: { courseId: 1 } })
    await flushPromises()
    await openCreate(wrapper)
    expect(modal()!.textContent).toContain('该课程还没有知识点')
  })
})

describe('教师题库：选择题选项输入（批次 9）', () => {
  it('单选题显示选项编辑，保存时把非空选项写成 JSON 数组', async () => {
    const wrapper = mount(TeacherBank, { props: { courseId: 1 } })
    await flushPromises()
    await openCreate(wrapper)
    expect(modal()!.querySelectorAll('.opt-row').length).toBeGreaterThanOrEqual(2)
    await setTextarea('请输入题干内容', 'TCP 工作在哪一层？')
    await setOptions(['网络层', '传输层', '会话层', ''])
    await setTextarea('请输入参考答案', 'b')
    modalButton('保存')!.click()
    await flushPromises()
    const payload = questionApi.createQuestion.mock.calls[0][0]
    expect(payload.options).toBe('["网络层","传输层","会话层"]')
    expect(payload.correctAnswers).toBe('"b"')
  })

  it('多选题答案按 JSON 数组编码，越界字母会被拦截', async () => {
    const wrapper = mount(TeacherBank, { props: { courseId: 1 } })
    await flushPromises()
    await openCreate(wrapper)
    await pickType('MULTIPLE_CHOICE')
    await setTextarea('请输入题干内容', '以下哪些属于传输层协议？')
    await setOptions(['TCP', 'UDP', 'IP', 'ICMP'])
    await setTextarea('请输入参考答案', 'A，B')
    modalButton('保存')!.click()
    await flushPromises()
    expect(questionApi.createQuestion.mock.calls[0][0].correctAnswers).toBe('["A","B"]')

    questionApi.createQuestion.mockClear()
    await openCreate(wrapper)
    await pickType('MULTIPLE_CHOICE')
    await setTextarea('请输入题干内容', '越界答案')
    await setOptions(['甲', '乙'])
    await setTextarea('请输入参考答案', 'D')
    modalButton('保存')!.click()
    await flushPromises()
    expect(questionApi.createQuestion).not.toHaveBeenCalled()
    expect(modal()!.textContent).toContain('超出选项范围')
  })

  it('少于两个非空选项时拦截保存', async () => {
    const wrapper = mount(TeacherBank, { props: { courseId: 1 } })
    await flushPromises()
    await openCreate(wrapper)
    await setTextarea('请输入题干内容', '只有一项选项')
    await setOptions(['甲', '   '])
    await setTextarea('请输入参考答案', 'A')
    modalButton('保存')!.click()
    await flushPromises()
    expect(questionApi.createQuestion).not.toHaveBeenCalled()
    expect(modal()!.textContent).toContain('至少需要 2 个非空选项')
  })

  it('判断题不显示选项编辑，自动写入「正确／错误」并把中文答案归一为 true/false', async () => {
    const wrapper = mount(TeacherBank, { props: { courseId: 1 } })
    await flushPromises()
    await openCreate(wrapper)
    await pickType('TRUE_FALSE')
    expect(modal()!.querySelectorAll('.opt-row').length).toBe(0)
    expect(modal()!.textContent).toContain('判断题固定为「正确／错误」')
    await setTextarea('请输入题干内容', 'TCP 是面向连接的协议。')
    await setTextarea('请输入参考答案', '正确')
    modalButton('保存')!.click()
    await flushPromises()
    const payload = questionApi.createQuestion.mock.calls[0][0]
    expect(payload.options).toBe('["正确","错误"]')
    expect(payload.correctAnswers).toBe('"true"')
  })

  it('增删选项：可添加第 5 项、且最少保留 2 项', async () => {
    const wrapper = mount(TeacherBank, { props: { courseId: 1 } })
    await flushPromises()
    await openCreate(wrapper)
    const addBtn = Array.from(modal()!.querySelectorAll('button')).find((b) =>
      (b.textContent || '').includes('添加选项'),
    ) as HTMLButtonElement
    addBtn.click()
    await flushPromises()
    expect(modal()!.querySelectorAll('.opt-row').length).toBe(5)
    for (let i = 0; i < 3; i += 1) {
      ;(modalInputs('.opt-row .text-btn')[0] as HTMLButtonElement as unknown as HTMLElement).click()
      await flushPromises()
    }
    expect(modal()!.querySelectorAll('.opt-row').length).toBe(2)
  })
})
