import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const api = vi.hoisted(() => ({
  getAllExamPapersByCourse: vi.fn(),
  getAllExamPapers: vi.fn(),
  getMyExamSessions: vi.fn(),
  startExam: vi.fn(),
  getExamQuestions: vi.fn(),
  getMyExamSession: vi.fn(),
  getRemainingTime: vi.fn(),
  submitExam: vi.fn(),
  autoSaveAnswers: vi.fn(),
  getExamResult: vi.fn(),
  getScoreReport: vi.fn(),
}))

vi.mock('@/api/exam', () => ({
  default: api,
}))

const toast = vi.hoisted(() => vi.fn())
vi.mock('../composables/ui', () => ({
  useV2Ui: () => ({ toast }),
}))

import StudentExamPanel from '../views/exam/StudentExamPanel.vue'

/** V2Modal 走 Teleport，测试里换成同 slot 结构的存根，确认按钮仍能被点到 */
const stubs = {
  V2Modal: {
    props: ['open', 'title'],
    template: '<div v-if="open" class="modal-stub"><slot /><slot name="footer" /></div>',
  },
}

function mountPanel(courseId: number | null = 4007) {
  return mount(StudentExamPanel, { props: { courseId }, global: { stubs } })
}

beforeEach(() => {
  vi.clearAllMocks()
  api.getMyExamSessions.mockResolvedValue({ data: [] })
  api.getMyExamSession.mockResolvedValue({ data: null })
  api.getRemainingTime.mockResolvedValue({ data: 1800 })
  api.autoSaveAnswers.mockResolvedValue({ data: {} })
})

describe('v2 学生考试面板', () => {
  it('列表加载失败与真正为空是两种可读状态', async () => {
    api.getAllExamPapersByCourse.mockRejectedValueOnce(new Error('500'))
    const failed = mountPanel()
    await flushPromises()
    expect(failed.find('.callout.danger').text()).toContain('加载失败')
    expect(failed.text()).not.toContain('本课程暂无可参加的考试')
    failed.unmount()

    api.getAllExamPapersByCourse.mockResolvedValueOnce({ data: [] })
    const empty = mountPanel()
    await flushPromises()
    expect(empty.find('.callout.danger').exists()).toBe(false)
    expect(empty.text()).toContain('本课程暂无可参加的考试')
    empty.unmount()
  })

  it('开考后按题目 ID 组装载荷：多选题是字母数组，交卷体是 Map 不是数组', async () => {
    api.getAllExamPapersByCourse.mockResolvedValue({
      data: [{ id: 7100, title: '期中测验', courseId: 4007, duration: 30, status: 'PUBLISHED' }],
    })
    api.startExam.mockResolvedValue({ data: { id: 999, status: 'STARTED' } })
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
    api.submitExam.mockResolvedValue({ data: { totalScore: 85 } })
    api.getExamResult.mockResolvedValue({ data: { totalScore: 85, percentage: 85 } })

    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.text()).toContain('期中测验')

    await wrapper.findAll('.row-actions button')[0].trigger('click') // 开始作答
    await flushPromises()
    expect(wrapper.find('.modal-stub').exists()).toBe(true) // 确认弹层，未确认前不开会话
    expect(api.startExam).not.toHaveBeenCalled()
    const buttons = wrapper.findAll('.modal-stub button')
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()
    expect(api.startExam).toHaveBeenCalledWith(7100)

    // 单选选 B，下一题多选勾 B、C
    await wrapper.findAll('.q-options input')[1].setValue(true)
    await wrapper.findAll('.qnav-btn')[1].trigger('click')
    await wrapper.findAll('.q-options input')[1].setValue(true)
    await wrapper.findAll('.q-options input')[2].setValue(true)
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
  })

  it('已有 STARTED 会话时恢复作答，不新建会话', async () => {
    api.getAllExamPapersByCourse.mockResolvedValue({
      data: [{ id: 7100, title: '期中测验', courseId: 4007, duration: 30 }],
    })
    api.getMyExamSession.mockResolvedValue({
      data: { id: 555, status: 'STARTED', answers: '{"7001":"C"}' },
    })
    api.getExamQuestions.mockResolvedValue({
      data: [{ id: 7001, type: 'SINGLE_CHOICE', content: '题干', options: '["A项","B项","C项"]' }],
    })

    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.findAll('.row-actions button')[0].trigger('click')
    await flushPromises()
    const buttons = wrapper.findAll('.modal-stub button')
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(api.startExam).not.toHaveBeenCalled()
    // 提示走 v2 的 toast 宿主（不是 Element Plus），恢复事实要可见
    expect(toast).toHaveBeenCalledWith('已恢复上次未完成的作答，请继续答题', 'ok')
    const checked = wrapper.findAll('.q-options input')[2].element as HTMLInputElement
    expect(checked.checked).toBe(true)
  })

  it('未识别题型不静默：给出明示且不渲染作答控件', async () => {
    api.getAllExamPapersByCourse.mockResolvedValue({
      data: [{ id: 7100, title: '测验', courseId: 4007, duration: 10 }],
    })
    api.startExam.mockResolvedValue({ data: { id: 1, status: 'STARTED' } })
    api.getExamQuestions.mockResolvedValue({
      data: [{ id: 8001, type: 'UNKNOWN_TYPE', content: '异常题', options: '[]' }],
    })

    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.findAll('.row-actions button')[0].trigger('click')
    await flushPromises()
    const buttons = wrapper.findAll('.modal-stub button')
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(wrapper.find('.callout.warn').text()).toContain('未识别的题型')
    expect(wrapper.findAll('.q-options input').length).toBe(0)
  })
})
