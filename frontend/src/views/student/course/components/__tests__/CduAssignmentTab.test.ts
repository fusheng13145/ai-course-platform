import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  getAllTasks: vi.fn(),
  getAllMySubmissions: vi.fn(),
  submitTask: vi.fn(),
  uploadFile: vi.fn(),
  message: Object.assign(vi.fn(), {
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  }),
}))

vi.mock('@/api/task', () => ({ default: { getAllTasks: mocks.getAllTasks } }))
vi.mock('@/api/submission', () => ({
  default: { getAllMySubmissions: mocks.getAllMySubmissions, submitTask: mocks.submitTask },
}))
vi.mock('@/api/common', () => ({ default: { uploadFile: mocks.uploadFile } }))
vi.mock('element-plus', () => ({ ElMessage: mocks.message }))

import CduAssignmentTab from '../CduAssignmentTab.vue'

function mountTab() {
  return mount(CduAssignmentTab, {
    props: { courseId: '7' },
    global: {
      stubs: {
        Modal: { template: '<div class="modal-stub"><slot /><slot name="footer" /></div>' },
        'el-alert': { props: ['title'], template: '<div class="el-alert">{{ title }}</div>' },
      },
    },
  })
}

describe('CduAssignmentTab - 学生作业任务页签（S1/S2 最小面）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getAllTasks.mockResolvedValue([
      { id: 1, title: '作业一', isPublished: true, maxScore: 100, dueDate: '2026-11-01' },
      { id: 2, title: '退回的作业', isPublished: true, maxScore: 100 },
    ])
    mocks.getAllMySubmissions.mockResolvedValue([
      { id: 11, taskId: 1, status: 'GRADED', score: 88, maxScore: 100, feedback: '结构清晰' },
      { id: 12, taskId: 2, status: 'RETURNED', attemptNumber: 1, feedback: '缺少步骤' },
    ])
  })

  it('渲染任务卡片：状态标签、得分与教师评语可见（S2）', async () => {
    const wrapper = mountTab()
    await flushPromises()

    expect(wrapper.text()).toContain('作业一')
    expect(wrapper.text()).toContain('已批改')
    expect(wrapper.text()).toContain('88')
    expect(wrapper.text()).toContain('结构清晰')
    expect(wrapper.text()).toContain('缺少步骤')

    wrapper.unmount()
  })

  it('已批改的任务不显示提交按钮；被退回的任务显示「重新提交」并可打开弹层（S1）', async () => {
    const wrapper = mountTab()
    await flushPromises()

    const buttons = wrapper.findAll('button').map((b) => b.text())
    expect(buttons.filter((text) => text === '提交作业')).toHaveLength(0)
    expect(buttons.filter((text) => text === '重新提交')).toHaveLength(1)

    await wrapper.findAll('button').find((b) => b.text() === '重新提交')!.trigger('click')
    await flushPromises()
    expect(wrapper.find('.modal-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('作答内容')

    wrapper.unmount()
  })

  it('空内容提交被拦截，不发起请求', async () => {
    const wrapper = mountTab()
    await flushPromises()
    await wrapper.findAll('button').find((b) => b.text() === '重新提交')!.trigger('click')
    await flushPromises()

    const confirm = wrapper.findAll('button').find((b) => b.text() === '确认提交')!
    await confirm.trigger('click')
    await flushPromises()

    expect(mocks.message.warning).toHaveBeenCalledWith('请填写作答内容或上传附件')
    expect(mocks.submitTask).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('加载失败显示持久错误提示', async () => {
    mocks.getAllTasks.mockRejectedValue(new Error('boom'))
    const wrapper = mountTab()
    await flushPromises()

    expect(wrapper.find('.el-alert').text()).toContain('加载失败')

    wrapper.unmount()
  })
})
