import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const studentApi = vi.hoisted(() => ({ getAssignmentDetail: vi.fn() }))
vi.mock('@/api/student', () => ({ default: studentApi }))

const submissionApi = vi.hoisted(() => ({
  submitTask: vi.fn(),
  getAllMySubmissions: vi.fn(),
  saveDraft: vi.fn(),
}))
vi.mock('@/api/submission', () => ({ default: submissionApi }))

const commonApi = vi.hoisted(() => ({ uploadFile: vi.fn() }))
vi.mock('@/api/common', () => ({ default: commonApi }))

const toast = vi.hoisted(() => vi.fn())
vi.mock('../composables/ui', () => ({ useUi: () => ({ toast }) }))

const push = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { taskId: '9001' } }),
  useRouter: () => ({ push }),
}))

import AssignmentView from '../views/task/AssignmentView.vue'

function assignment(overrides: Record<string, unknown> = {}) {
  return {
    id: 9001,
    title: '实验一报告',
    description: '提交实验报告',
    requirements: '不少于 500 字',
    dueDate: '2099-01-01T00:00:00',
    maxScore: 100,
    isPublished: true,
    courseId: 4007,
    ...overrides,
  }
}

function detailResponse(submission: Record<string, unknown> | null, overrides = {}) {
  return { data: { assignment: assignment(overrides), submission } }
}

beforeEach(() => {
  vi.clearAllMocks()
  submissionApi.submitTask.mockResolvedValue({ data: {} })
  submissionApi.getAllMySubmissions.mockResolvedValue([])
  submissionApi.saveDraft.mockResolvedValue({ data: { id: 1, status: 'DRAFT' } })
  commonApi.uploadFile.mockResolvedValue({ data: { url: '/uploads/a.pdf' } })
})

describe('沉浸式作业页', () => {
  it('加载详情：展示要求、得分与教师评语，并预填上次作答', async () => {
    studentApi.getAssignmentDetail.mockResolvedValue(
      detailResponse({
        id: 5600,
        status: 'GRADED',
        score: 88,
        maxScore: 100,
        feedback: '报告完整',
        content: '第一次作答',
        attemptNumber: 1,
        submittedAt: '2026-11-20T10:00:00',
      }),
    )

    const wrapper = mount(AssignmentView)
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('实验一报告')
    expect(text).toContain('提交实验报告')
    expect(text).toContain('88')
    expect(text).toContain('报告完整')
    expect(text).toContain('已提交 1 次，可继续重新提交')
    const textarea = wrapper.find('textarea').element as HTMLTextAreaElement
    expect(textarea.value).toBe('第一次作答')
    expect(wrapper.findAll('button').some((b) => b.text().includes('重新提交作业'))).toBe(true)
    wrapper.unmount()
  })

  it('重新提交走真实接口：POST /submissions 的 { content, fileUrls }', async () => {
    studentApi.getAssignmentDetail.mockResolvedValue(detailResponse(null))

    const wrapper = mount(AssignmentView)
    await flushPromises()

    await wrapper.find('textarea').setValue('新的作答')
    const submitBtn = wrapper.findAll('button').find((b) => b.text().includes('提交作业'))
    expect(submitBtn).toBeTruthy()
    await submitBtn?.trigger('click')
    await flushPromises()

    expect(submissionApi.submitTask).toHaveBeenCalledWith(9001, {
      content: '新的作答',
      fileUrls: [],
    })
    expect(toast).toHaveBeenCalledWith('提交成功，等待教师批改', 'ok')
    wrapper.unmount()
  })

  it('达到 maxSubmissions 上限后不渲染表单并说明原因', async () => {
    studentApi.getAssignmentDetail.mockResolvedValue(
      detailResponse(
        { id: 5601, status: 'SUBMITTED', attemptNumber: 1, content: '已交' },
        { maxSubmissions: 1 },
      ),
    )

    const wrapper = mount(AssignmentView)
    await flushPromises()

    expect(wrapper.text()).toContain('已达到最大提交次数（1 次）')
    expect(wrapper.find('textarea').exists()).toBe(false)
    wrapper.unmount()
  })

  it('提交失败保留填写内容并提示重试', async () => {
    studentApi.getAssignmentDetail.mockResolvedValue(detailResponse(null))
    submissionApi.submitTask.mockRejectedValueOnce(new Error('502'))

    const wrapper = mount(AssignmentView)
    await flushPromises()

    await wrapper.find('textarea').setValue('别丢这段')
    await wrapper
      .findAll('button')
      .find((b) => b.text().includes('提交作业'))
      ?.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('提交失败')
    const textarea = wrapper.find('textarea').element as HTMLTextAreaElement
    expect(textarea.value).toBe('别丢这段')
    wrapper.unmount()
  })

  it('有草稿时回填内容并提示，但不算已提交', async () => {
    studentApi.getAssignmentDetail.mockResolvedValue(detailResponse(null))
    submissionApi.getAllMySubmissions.mockResolvedValue([
      { id: 1, taskId: 9001, status: 'DRAFT', content: '草稿内容' },
    ])

    const wrapper = mount(AssignmentView)
    await flushPromises()

    const textarea = wrapper.find('textarea').element as HTMLTextAreaElement
    expect(textarea.value).toBe('草稿内容')
    expect(wrapper.text()).toContain('已有草稿')
    // 草稿不是正式提交：按钮仍为「提交作业」，也不显示「已提交 N 次」
    expect(wrapper.findAll('button').some((b) => b.text().includes('提交作业'))).toBe(true)
    expect(wrapper.text()).not.toContain('已提交 1 次')
    wrapper.unmount()
  })

  it('保存草稿走 /submissions/draft 并提示已保存', async () => {
    studentApi.getAssignmentDetail.mockResolvedValue(detailResponse(null))

    const wrapper = mount(AssignmentView)
    await flushPromises()

    await wrapper.find('textarea').setValue('写到一半')
    const draftBtn = wrapper.findAll('button').find((b) => b.text().includes('保存草稿'))
    expect(draftBtn).toBeTruthy()
    await draftBtn?.trigger('click')
    await flushPromises()

    expect(submissionApi.saveDraft).toHaveBeenCalledWith(9001, {
      content: '写到一半',
      fileUrls: [],
    })
    expect(toast).toHaveBeenCalledWith('草稿已保存', 'ok')
    wrapper.unmount()
  })
})
