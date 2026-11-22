import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/** 答题页在新标签打开，列表只负责 resolve + window.open */
const routerResolve = vi.hoisted(() => vi.fn((path: string) => ({ href: path })))
const openMock = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({
  useRouter: () => ({ resolve: routerResolve }),
}))

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

const studentApi = vi.hoisted(() => ({
  getAllMyCourses: vi.fn(),
}))
vi.mock('@/api/student', () => ({
  default: studentApi,
}))

const toast = vi.hoisted(() => vi.fn())
vi.mock('../composables/ui', () => ({
  useUi: () => ({ toast }),
}))

import StudentExamPanel from '../views/exam/StudentExamPanel.vue'

/** Modal 走 Teleport，测试里换成同 slot 结构的存根，确认按钮仍能被点到 */
const stubs = {
  Modal: {
    props: ['open', 'title'],
    template: '<div v-if="open" class="modal-stub"><slot /><slot name="footer" /></div>',
  },
}

function mountPanel(courseId: number | null = 4007) {
  return mount(StudentExamPanel, { props: { courseId }, global: { stubs } })
}

beforeEach(() => {
  vi.clearAllMocks()
  vi.stubGlobal('open', openMock)
  api.getMyExamSessions.mockResolvedValue({ data: [] })
  api.getMyExamSession.mockResolvedValue({ data: null })
  api.getRemainingTime.mockResolvedValue({ data: 1800 })
  api.autoSaveAnswers.mockResolvedValue({ data: {} })
})

describe('学生考试面板', () => {
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

  it('点击开始作答跳转到沉浸式答题页（作答逻辑在 /exam-room/:paperId）', async () => {
    api.getAllExamPapersByCourse.mockResolvedValue({
      data: [{ id: 7100, title: '期中测验', courseId: 4007, duration: 30, status: 'PUBLISHED' }],
    })

    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.text()).toContain('期中测验')

    await wrapper.findAll('.row-actions button')[0].trigger('click')
    await flushPromises()

    expect(openMock).toHaveBeenCalledWith('/exam-room/7100', '_blank', 'noopener,noreferrer')
    // 列表不再内嵌答题：这里既不建会话，也不弹确认层
    expect(api.startExam).not.toHaveBeenCalled()
    expect(wrapper.find('.modal-stub').exists()).toBe(false)
  })
})

describe('M-02b 全局考试列表「可参加」行内预禁用', () => {
  it('未选课 / 时间窗外 的考试行禁用并展示原因，已选且在窗内则可参加', async () => {
    api.getAllExamPapers.mockResolvedValue({
      data: [
        { id: 7101, title: '未选课程考', courseId: 4009, duration: 30, status: 'PUBLISHED' },
        {
          id: 7102,
          title: '未开始考',
          courseId: 4007,
          duration: 30,
          status: 'PUBLISHED',
          startTime: '2099-01-01T00:00:00',
        },
        {
          id: 7103,
          title: '已结束考',
          courseId: 4007,
          duration: 30,
          status: 'PUBLISHED',
          endTime: '2000-01-01T00:00:00',
        },
        {
          id: 7104,
          title: '可参加考',
          courseId: 4007,
          duration: 30,
          status: 'PUBLISHED',
          startTime: '2000-01-01T00:00:00',
          endTime: '2099-01-01T00:00:00',
        },
      ],
    })
    studentApi.getAllMyCourses.mockResolvedValue([{ id: 4007 }, { id: 4008 }])

    const wrapper = mount(StudentExamPanel, { props: { courseId: null }, global: { stubs } })
    await flushPromises()

    const rows = wrapper.findAll('.exam-row')
    expect(rows.length).toBe(4)

    expect(rows[0].find('.exam-status').text()).toBe('未选课')
    expect(rows[0].find('.row-actions button').attributes('disabled')).toBeDefined()

    expect(rows[1].find('.exam-status').text()).toBe('未开始')
    expect(rows[1].find('.row-actions button').attributes('disabled')).toBeDefined()

    expect(rows[2].find('.exam-status').text()).toBe('已结束')
    expect(rows[2].find('.row-actions button').attributes('disabled')).toBeDefined()

    expect(rows[3].find('.exam-status').text()).toBe('可参加')
    expect(rows[3].find('.row-actions button').attributes('disabled')).toBeUndefined()
  })

  it('可参加的行点击后跳转答题页（后端拒绝由答题页兜底提示）', async () => {
    api.getAllExamPapers.mockResolvedValue({
      data: [{ id: 7105, title: '兜底考', courseId: 4007, duration: 30, status: 'PUBLISHED' }],
    })
    studentApi.getAllMyCourses.mockResolvedValue([{ id: 4007 }])

    const wrapper = mount(StudentExamPanel, { props: { courseId: null }, global: { stubs } })
    await flushPromises()
    const btn = wrapper.find('.row-actions button')
    expect(btn.attributes('disabled')).toBeUndefined() // 已选且在窗内，按钮可点
    await btn.trigger('click')
    await flushPromises()

    expect(openMock).toHaveBeenCalledWith('/exam-room/7105', '_blank', 'noopener,noreferrer')
  })
})

describe('考试次数（max_attempts）语义', () => {
  it('已考过但次数未用尽时，状态为可重考且按钮跳转答题页', async () => {
    api.getAllExamPapersByCourse.mockResolvedValue({
      data: [
        {
          id: 7200,
          title: '可重考卷',
          courseId: 4007,
          duration: 30,
          status: 'PUBLISHED',
          maxAttempts: 2,
        },
      ],
    })
    api.getMyExamSessions.mockResolvedValue({ data: [{ examPaperId: 7200, status: 'GRADED' }] })
    api.getMyExamSession.mockResolvedValue({ data: null })
    api.startExam.mockResolvedValue({ data: { id: 321, status: 'STARTED' } })
    api.getExamQuestions.mockResolvedValue({
      data: [{ id: 7301, type: 'SINGLE_CHOICE', content: '题干', options: '["A","B"]' }],
    })

    const wrapper = mountPanel()
    await flushPromises()

    const row = wrapper.find('.exam-row')
    expect(row.find('.exam-status').text()).toBe('可重考')
    const btn = row.find('.row-actions button')
    expect(btn.text()).toBe('再次考试')
    expect(btn.attributes('disabled')).toBeUndefined()

    await btn.trigger('click')
    await flushPromises()
    expect(openMock).toHaveBeenCalledWith('/exam-room/7200', '_blank', 'noopener,noreferrer')
  })

  it('次数用尽后才显示已完成并禁用，避免无限重考', async () => {
    api.getAllExamPapersByCourse.mockResolvedValue({
      data: [
        {
          id: 7201,
          title: '已满卷',
          courseId: 4007,
          duration: 30,
          status: 'PUBLISHED',
          maxAttempts: 1,
        },
      ],
    })
    api.getMyExamSessions.mockResolvedValue({ data: [{ examPaperId: 7201, status: 'GRADED' }] })

    const wrapper = mountPanel()
    await flushPromises()

    const row = wrapper.find('.exam-row')
    expect(row.find('.exam-status').text()).toBe('已完成')
    const btn = row.find('.row-actions button')
    expect(btn.text()).toBe('已完成')
    expect(btn.attributes('disabled')).toBeDefined()
  })

  it('不限次数（maxAttempts=0）时，考过仍可重考且不会锁定', async () => {
    api.getAllExamPapersByCourse.mockResolvedValue({
      data: [
        {
          id: 7300,
          title: '不限次卷',
          courseId: 4007,
          duration: 30,
          status: 'PUBLISHED',
          maxAttempts: 0,
        },
      ],
    })
    api.getMyExamSessions.mockResolvedValue({ data: [{ examPaperId: 7300, status: 'GRADED' }] })

    const wrapper = mountPanel()
    await flushPromises()

    const row = wrapper.find('.exam-row')
    expect(row.find('.exam-status').text()).toBe('可重考')
    const btn = row.find('.row-actions button')
    expect(btn.text()).toBe('再次考试')
    expect(btn.attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })
})
