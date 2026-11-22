import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const api = vi.hoisted(() => ({
  getAllExamPapers: vi.fn(),
  getAllExamPapersByCourse: vi.fn(),
  getMyExamSessions: vi.fn(),
  getExamPaperById: vi.fn(),
  getMyExamSession: vi.fn(),
  startExam: vi.fn(),
  getExamQuestions: vi.fn(),
  getRemainingTime: vi.fn(),
  submitExam: vi.fn(),
  getExamResult: vi.fn(),
  getScoreReport: vi.fn(),
  autoSaveAnswers: vi.fn(),
}))
vi.mock('@/api/exam', () => ({ default: api }))

const toast = vi.hoisted(() => vi.fn())
vi.mock('../composables/ui', () => ({ useUi: () => ({ toast }) }))

const push = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { paperId: '7100' } }),
  useRouter: () => ({ push }),
}))

import ExamRoomView from '../views/exam/ExamRoomView.vue'

const stubs = {
  Modal: {
    props: ['open', 'title'],
    template: '<div v-if="open" class="modal-stub"><slot /><slot name="footer" /></div>',
  },
}

function mountRoom() {
  return mount(ExamRoomView, { global: { stubs } })
}

beforeEach(() => {
  vi.clearAllMocks()
  api.getExamPaperById.mockResolvedValue({
    data: { id: 7100, title: '期中测验', courseId: 4007, duration: 30, isPublished: true },
  })
  api.getMyExamSessions.mockResolvedValue({ data: [] })
  api.getAllExamPapers.mockResolvedValue({ data: [] })
  api.getMyExamSession.mockResolvedValue({ data: null })
  api.startExam.mockResolvedValue({ data: { id: 999, status: 'STARTED' } })
  api.getRemainingTime.mockResolvedValue({ data: 1800 })
  api.autoSaveAnswers.mockResolvedValue({ data: {} })
  api.submitExam.mockResolvedValue({ data: { totalScore: 85 } })
  api.getExamResult.mockResolvedValue({ data: { totalScore: 85, percentage: 85 } })
  api.getExamQuestions.mockResolvedValue({
    data: [
      {
        id: 7001,
        type: 'SINGLE_CHOICE',
        content: '网络层协议?',
        options: '["HTTP","IP","TCP","ARP"]',
        score: 5,
      },
      {
        id: 7002,
        type: 'MULTIPLE_CHOICE',
        content: '属于路由协议?',
        options: '["RIP","OSPF","BGP","SMTP"]',
      },
    ],
  })
})

describe('沉浸式答题页', () => {
  it('进入即开考并渲染题目与倒计时', async () => {
    const wrapper = mountRoom()
    await flushPromises()

    expect(api.startExam).toHaveBeenCalledWith(7100)
    expect(wrapper.text()).toContain('期中测验')
    expect(wrapper.text()).toContain('网络层协议')
    expect(wrapper.find('.timer').exists()).toBe(true)
    wrapper.unmount()
  })

  it('存在 STARTED 会话时恢复作答，不新建会话', async () => {
    api.getMyExamSession.mockResolvedValue({
      data: { id: 555, status: 'STARTED', answers: '{"7001":"C"}' },
    })

    const wrapper = mountRoom()
    await flushPromises()

    expect(api.startExam).not.toHaveBeenCalled()
    expect(toast).toHaveBeenCalledWith('已恢复上次未完成的作答，请继续答题', 'ok')
    const checked = wrapper.findAll('.q-options input')[2].element as HTMLInputElement
    expect(checked.checked).toBe(true)
    wrapper.unmount()
  })

  it('交卷载荷：多选题是字母数组，作答体不是数组', async () => {
    const wrapper = mountRoom()
    await flushPromises()

    await wrapper.findAll('.q-options input')[1].setValue(true) // 第 1 题选 B
    await wrapper.findAll('.qnav-btn')[1].trigger('click')
    await wrapper.findAll('.q-options input')[1].setValue(true) // 多选 B
    await wrapper.findAll('.q-options input')[2].setValue(true) // 多选 C
    expect(wrapper.text()).toContain('已作答 2 / 2')

    await wrapper.findAll('.sheet-foot button').at(-1)?.trigger('click') // 交卷
    await flushPromises()
    const confirm = wrapper.findAll('.modal-stub button')
    await confirm[confirm.length - 1].trigger('click')
    await flushPromises()

    expect(api.submitExam).toHaveBeenCalledTimes(1)
    const [sid, body] = api.submitExam.mock.calls[0]
    expect(sid).toBe(999)
    expect(body.answers).toEqual({ '7001': 'B', '7002': ['B', 'C'] })
    expect(Array.isArray(body.answers)).toBe(false)
    expect(wrapper.text()).toContain('85')
    wrapper.unmount()
  })

  it('填空题按题干占位符渲染多空输入，交卷按分隔符合并', async () => {
    api.getExamQuestions.mockResolvedValue({
      data: [
        {
          id: 7003,
          type: 'FILL_BLANK',
          content: 'TCP 建立连接需要 __ 次握手，断开需要 __ 次挥手',
          options: '[]',
          score: 10,
        },
      ],
    })

    const wrapper = mountRoom()
    await flushPromises()

    expect(wrapper.find('.q-head .chip').text()).toBe('填空题')
    const inputs = wrapper.findAll('.q-fill input')
    expect(inputs.length).toBe(2)

    await inputs[0].setValue('三')
    await inputs[1].setValue('四')
    expect(wrapper.text()).toContain('已作答 1 / 1')

    await wrapper.findAll('.sheet-foot button').at(-1)?.trigger('click')
    await flushPromises()
    const confirm = wrapper.findAll('.modal-stub button')
    await confirm[confirm.length - 1].trigger('click')
    await flushPromises()

    expect(api.submitExam.mock.calls[0][1].answers).toEqual({ '7003': '三；四' })
    wrapper.unmount()
  })

  it('论述题提供多行输入并计入已作答', async () => {
    api.getExamQuestions.mockResolvedValue({
      data: [
        {
          id: 7004,
          type: 'ESSAY',
          content: '简述 Python 中列表与元组的区别及各自适用场景',
          options: '[]',
          score: 10,
        },
      ],
    })

    const wrapper = mountRoom()
    await flushPromises()

    expect(wrapper.find('.q-head .chip').text()).toBe('论述题')
    const textarea = wrapper.find('textarea.q-text')
    expect(textarea.exists()).toBe(true)
    expect(textarea.attributes('rows')).toBe('8')

    await textarea.setValue('列表可变、元组不可变；元组可作字典键')
    expect(wrapper.text()).toContain('已作答 1 / 1')
    wrapper.unmount()
  })

  it('恢复作答时多空填空逐空回填', async () => {
    api.getMyExamSession.mockResolvedValue({
      data: { id: 556, status: 'STARTED', answers: '{"7003":"三；四"}' },
    })
    api.getExamQuestions.mockResolvedValue({
      data: [
        {
          id: 7003,
          type: 'FILL_BLANK',
          content: 'TCP 建立连接需要 __ 次握手，断开需要 __ 次挥手',
          options: '[]',
          score: 10,
        },
      ],
    })

    const wrapper = mountRoom()
    await flushPromises()

    const inputs = wrapper.findAll('.q-fill input')
    expect(inputs.length).toBe(2)
    expect((inputs[0].element as HTMLInputElement).value).toBe('三')
    expect((inputs[1].element as HTMLInputElement).value).toBe('四')
    wrapper.unmount()
  })

  it('未识别题型不静默：给出明示且不渲染作答控件', async () => {
    api.getExamQuestions.mockResolvedValue({
      data: [{ id: 8001, type: 'UNKNOWN_TYPE', content: '异常题', options: '[]' }],
    })

    const wrapper = mountRoom()
    await flushPromises()

    expect(wrapper.find('.callout.warn').text()).toContain('未识别的题型')
    expect(wrapper.findAll('.q-options input').length).toBe(0)
    wrapper.unmount()
  })

  it('开考被后端拒绝时展示错误而不是白屏', async () => {
    api.startExam.mockRejectedValueOnce(new Error('已达到最大尝试次数限制（1次）'))

    const wrapper = mountRoom()
    await flushPromises()

    expect(wrapper.find('.callout.danger').text()).toContain('未能进入考试')
    wrapper.unmount()
  })
})
