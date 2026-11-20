import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const api = vi.hoisted(() => ({
  archiveExamPaper: vi.fn(),
  copyExam: vi.fn(),
  createExamPaper: vi.fn(),
  deleteExamPaper: vi.fn(),
  getAllExamPapers: vi.fn(),
  getExamQuestions: vi.fn(),
  getExamStatistics: vi.fn(),
  publishExam: vi.fn(),
  updateExamPaper: vi.fn(),
}))

vi.mock('@/api/exam', () => api)

const courseApi = vi.hoisted(() => ({ getAllMyCourses: vi.fn() }))
vi.mock('@/api/course', () => courseApi)

const toast = vi.hoisted(() => vi.fn())
vi.mock('../composables/ui', () => ({
  useUi: () => ({ toast }),
}))

import TeacherExamsView from '../views/teacher/TeacherExamsView.vue'

const stubs = {
  PageHead: { template: '<div><slot /><slot name="actions" /></div>' },
  Stat: { template: '<div />' },
  Icon: { template: '<i />' },
  PaperStats: { template: '<div />' },
  Modal: {
    props: ['open'],
    template: '<div v-if="open" class="modal-stub"><slot /><slot name="footer" /></div>',
  },
}

const PUBLISHED = {
  id: 8,
  title: '已发布卷',
  status: 'PUBLISHED',
  courseId: 1,
  duration: 30,
  totalScore: 100,
  questionCount: 2,
}
const DRAFT = {
  id: 9,
  title: '草稿卷',
  status: 'DRAFT',
  courseId: 1,
  duration: 30,
  totalScore: 100,
  questionCount: 2,
}

beforeEach(() => {
  vi.clearAllMocks()
  courseApi.getAllMyCourses.mockResolvedValue([{ id: 1, name: '课程一' }])
  api.getAllExamPapers.mockResolvedValue([PUBLISHED, DRAFT])
  api.archiveExamPaper.mockResolvedValue({ code: 200, message: 'ok', data: null })
})

describe('教师试卷库归档入口', () => {
  it('课程内考试页把在线监考作为子页签，默认试卷，切换后挂载监考', async () => {
    const wrapper = mount(TeacherExamsView, {
      props: { courseId: 1 },
      global: {
        stubs: {
          ...stubs,
          ProctorTab: { template: '<div class="proctor-stub" />' },
        },
      },
    })
    await flushPromises()

    expect(wrapper.findAll('[role="tab"]').map((tab) => tab.text())).toEqual(['试卷', '在线监考'])
    expect(wrapper.find('.proctor-stub').exists()).toBe(false)

    await wrapper.findAll('[role="tab"]')[1].trigger('click')
    expect(wrapper.find('.proctor-stub').exists()).toBe(true)
    wrapper.unmount()
  })

  it('仅已发布试卷显示归档，确认后调用归档端点并刷新', async () => {
    const wrapper = mount(TeacherExamsView, { global: { stubs } })
    await flushPromises()

    const archiveButtons = wrapper.findAll('button').filter((button) => button.text() === '归档')
    expect(archiveButtons).toHaveLength(1)

    await archiveButtons[0].trigger('click')
    const modal = wrapper.find('.modal-stub')
    expect(modal.exists()).toBe(true)
    expect(modal.text()).toContain('历史作答、成绩与监考记录全部保留')
    await modal.findAll('button').at(-1)!.trigger('click')
    await flushPromises()

    expect(api.archiveExamPaper).toHaveBeenCalledWith(8)
    expect(toast).toHaveBeenCalledWith('试卷已归档', 'ok')
    expect(api.getAllExamPapers).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })
})
