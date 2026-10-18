import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  route: { params: { id: '7' }, query: { taskId: '31' } },
  getAllTasks: vi.fn(),
  getTaskById: vi.fn(),
  getTaskSubmissions: vi.fn(),
  gradeSubmission: vi.fn(),
  aiGrade: vi.fn(),
  returnSubmission: vi.fn(),
  message: Object.assign(vi.fn(), {
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  }),
}))

vi.mock('vue-router', () => ({ useRoute: () => mocks.route }))
vi.mock('@/api/task', () => ({
  default: {
    getAllTasks: mocks.getAllTasks,
    getTaskById: mocks.getTaskById,
  },
}))
vi.mock('@/api/submission', () => ({
  default: {
    getTaskSubmissions: mocks.getTaskSubmissions,
    gradeSubmission: mocks.gradeSubmission,
    aiGrade: mocks.aiGrade,
    returnSubmission: mocks.returnSubmission,
  },
}))
vi.mock('element-plus', () => ({ ElMessage: mocks.message }))

import CourseSubmissions from '../CourseSubmissions.vue'

function mountPage() {
  return mount(CourseSubmissions, {
    global: {
      stubs: {
        PageHeader: true,
        Modal: { template: '<div><slot /><slot name="footer" /></div>' },
        // 提示条内容以 `:title` 传入；`true` 桩会把 title 变成属性、无法被 text() 读到，
        // 故按 course-detail.test.ts 的既有写法用渲染桩把 title 落成文本。
        'el-alert': {
          props: ['title'],
          template: '<div class="el-alert">{{ title }}<slot /></div>',
        },
        'el-pagination': true,
      },
    },
  })
}

describe('CourseSubmissions task targeting and grading boundaries', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.route.params = { id: '7' }
    mocks.route.query = { taskId: '31' }
    mocks.getAllTasks.mockResolvedValue([
      { id: 1, courseId: 7, title: '第一页任务', maxScore: 100 },
    ])
    mocks.getTaskById.mockResolvedValue({
      code: 200,
      data: { id: 31, courseId: 7, title: '待批阅任务', maxScore: 100 },
    })
    mocks.getTaskSubmissions.mockResolvedValue({
      code: 200,
      data: {
        records: [
          {
            id: 501,
            studentName: '林同学',
            status: 'SUBMITTED',
            fileUrls: '/uploads/answer.pdf,/uploads/source.zip',
            maxScore: 100,
          },
        ],
        total: 1,
      },
    })
  })

  it('opens the exact pending task even when it is outside the first task page', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(mocks.getAllTasks).toHaveBeenCalledWith({ courseId: 7 })
    expect(mocks.getTaskById).toHaveBeenCalledWith(31)
    expect(mocks.getTaskSubmissions).toHaveBeenCalledWith(31, { page: 1, size: 20 })
    expect(wrapper.text()).toContain('待批阅任务')
    expect(wrapper.text()).toContain('附件 2 个')

    wrapper.unmount()
  })

  it('does not submit a blank score and exposes the parsed attachment links for review', async () => {
    const wrapper = mountPage()
    await flushPromises()
    await wrapper.find('button.op-link').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('a.attachment-link')).toHaveLength(2)
    expect((wrapper.findAll('input[type="number"]')[0].element as HTMLInputElement).value).toBe('')
    const submitButton = wrapper
      .findAll('button')
      .find((button) => button.text().includes('提交批改'))
    await submitButton?.trigger('click')

    expect(mocks.message.warning).toHaveBeenCalledWith('请填写分数')
    expect(mocks.gradeSubmission).not.toHaveBeenCalled()

    await wrapper.find('input[type="number"]').setValue('101')
    await submitButton?.trigger('click')
    expect(mocks.message.warning).toHaveBeenCalledWith('分数需在 0 至 100 之间')
    expect(mocks.gradeSubmission).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('does not silently switch to another task when the requested task belongs to a different course', async () => {
    mocks.getTaskById.mockResolvedValue({
      code: 200,
      data: { id: 31, courseId: 8, title: '其他课程任务', maxScore: 100 },
    })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('不属于当前课程')
    expect(mocks.getTaskSubmissions).not.toHaveBeenCalled()

    wrapper.unmount()
  })
})
