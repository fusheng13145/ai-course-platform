import { beforeEach, describe, expect, it, vi } from 'vitest'
import { nextTick, ref } from 'vue'
import {
  canSubmitStatus,
  normalizeAttachmentUrls,
  useStudentAssignments,
} from '@/composables/useStudentAssignments'

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

vi.mock('@/api/task', () => ({
  default: { getAllTasks: mocks.getAllTasks },
}))
vi.mock('@/api/submission', () => ({
  default: { getAllMySubmissions: mocks.getAllMySubmissions, submitTask: mocks.submitTask },
}))
vi.mock('@/api/common', () => ({
  default: { uploadFile: mocks.uploadFile },
}))
vi.mock('element-plus', () => ({ ElMessage: mocks.message }))

import { ElMessage } from 'element-plus'

const message = ElMessage as unknown as typeof mocks.message

function setup() {
  const courseId = ref<string | number | null>(7)
  const assignments = useStudentAssignments({ courseId })
  return { courseId, assignments }
}

describe('useStudentAssignments - 聚合与派生状态', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getAllTasks.mockResolvedValue([
      { id: 1, title: '作业一', isPublished: true, maxScore: 100, dueDate: '2026-11-01' },
      { id: 2, title: '作业二（草稿）', isPublished: false, maxScore: 50 },
      { id: 3, title: '作业三', isPublished: true, maxScore: 100 },
      { id: 4, title: '作业四', isPublished: true, maxScore: 100 },
    ])
    mocks.getAllMySubmissions.mockResolvedValue([
      { id: 11, taskId: 1, status: 'GRADED', score: 88, feedback: '不错', attemptNumber: 1 },
      { id: 12, taskId: 3, status: 'SUBMITTED', attemptNumber: 1 },
      // 任务 4 有两次提交，最新一次（attempt 2）为准
      { id: 13, taskId: 4, status: 'RETURNED', attemptNumber: 1 },
      { id: 14, taskId: 4, status: 'RESUBMITTED', attemptNumber: 2 },
    ])
  })

  it('仅聚合已发布任务，未发布任务不出现', async () => {
    const { assignments } = setup()
    await assignments.load()
    expect(assignments.rows.value.map((row) => row.taskId)).toEqual([1, 3, 4])
  })

  it('派生状态：未提交/已批改/待批改/重新提交，标签随状态', async () => {
    const { assignments } = setup()
    await assignments.load()
    const byId = new Map(assignments.rows.value.map((row) => [row.taskId, row]))
    expect(byId.get(1)?.status).toBe('GRADED')
    expect(byId.get(1)?.statusLabel).toBe('已批改')
    expect(byId.get(3)?.status).toBe('SUBMITTED')
    expect(byId.get(4)?.status).toBe('RESUBMITTED')
    // 已提交/已批改/重交中的任务均不允许再次发起提交（退回后才可重交，见提交流 describe）
    expect(canSubmitStatus(byId.get(1)?.status ?? 'NOT_SUBMITTED')).toBe(false)
    expect(canSubmitStatus(byId.get(3)?.status ?? 'NOT_SUBMITTED')).toBe(false)
    expect(canSubmitStatus(byId.get(4)?.status ?? 'NOT_SUBMITTED')).toBe(false)
  })

  it('同一任务多次提交取最新一次 attempt', async () => {
    const { assignments } = setup()
    await assignments.load()
    const row4 = assignments.rows.value.find((row) => row.taskId === 4)
    expect(row4?.submission?.id).toBe(14)
  })

  it('成绩与评语透出到行数据上（S2）', async () => {
    const { assignments } = setup()
    await assignments.load()
    const row1 = assignments.rows.value.find((row) => row.taskId === 1)
    expect(row1?.submission?.score).toBe(88)
    expect(row1?.submission?.feedback).toBe('不错')
  })

  it('加载失败置空并给出持久提示，不抛异常', async () => {
    mocks.getAllTasks.mockRejectedValue(new Error('boom'))
    const { assignments } = setup()
    await assignments.load()
    expect(assignments.loadError.value).toContain('加载失败')
    expect(assignments.rows.value).toEqual([])
  })
})

describe('useStudentAssignments - 提交流', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getAllTasks.mockResolvedValue([
      { id: 9, title: '退回的作业', isPublished: true, maxScore: 100 },
    ])
    mocks.getAllMySubmissions.mockResolvedValue([
      // 退回且未填写作答（content 为空）：openSubmit 后表单内容为空，需拦截空提交
      { id: 21, taskId: 9, status: 'RETURNED', attemptNumber: 1, content: null },
    ])
  })

  it('内容与附件均为空时不提交', async () => {
    const { assignments } = setup()
    await assignments.load()
    assignments.openSubmit(assignments.rows.value[0])
    await expect(assignments.submitAssignment()).resolves.toBe(false)
    expect(message.warning).toHaveBeenCalledWith('请填写作答内容或上传附件')
    expect(mocks.submitTask).not.toHaveBeenCalled()
  })

  it('提交成功：payload 为 {content, fileUrls}，刷新列表并关闭弹层', async () => {
    mocks.submitTask.mockResolvedValue({ code: 200, data: { id: 99 } })
    mocks.getAllMySubmissions.mockResolvedValue([]) // 重载后任务变为未提交
    const { assignments } = setup()
    await assignments.load()
    assignments.openSubmit(assignments.rows.value[0])
    assignments.submitModal.form.content = '重做的作答'
    assignments.submitModal.form.fileUrls = ['/uploads/a.pdf']
    await expect(assignments.submitAssignment()).resolves.toBe(true)
    expect(mocks.submitTask).toHaveBeenCalledWith(9, {
      content: '重做的作答',
      fileUrls: ['/uploads/a.pdf'],
    })
    expect(assignments.submitModal.visible).toBe(false)
    // load() 会被再次调用刷新状态
    expect(mocks.getAllTasks.mock.calls.length).toBeGreaterThanOrEqual(2)
  })

  it('提交失败返回 false，弹层保留供重试', async () => {
    mocks.submitTask.mockRejectedValue(new Error('boom'))
    const { assignments } = setup()
    await assignments.load()
    assignments.openSubmit(assignments.rows.value[0])
    assignments.submitModal.form.content = '作答'
    await expect(assignments.submitAssignment()).resolves.toBe(false)
    expect(assignments.submitModal.visible).toBe(true)
  })

  it('附件上传成功后 url 进入表单，可移除', async () => {
    mocks.uploadFile.mockResolvedValue({
      code: 200,
      data: { url: '/uploads/x.pdf', fileName: 'x.pdf', size: '10' },
    })
    const { assignments } = setup()
    await assignments.load()
    assignments.openSubmit(assignments.rows.value[0])
    const file = new File(['x'], 'x.pdf', { type: 'application/pdf' })
    await assignments.uploadFiles([file as File])
    await nextTick()
    expect(assignments.submitModal.form.fileUrls).toEqual(['/uploads/x.pdf'])
    assignments.removeAttachment('/uploads/x.pdf')
    expect(assignments.submitModal.form.fileUrls).toEqual([])
  })
})

describe('normalizeAttachmentUrls - 附件字段兼容', () => {
  it('兼容数组 / JSON 数组字符串 / 逗号分隔字符串 / 空值', () => {
    expect(normalizeAttachmentUrls(['/a.pdf'])).toEqual(['/a.pdf'])
    expect(normalizeAttachmentUrls('["/a.pdf","/b.zip"]')).toEqual(['/a.pdf', '/b.zip'])
    expect(normalizeAttachmentUrls('/a.pdf, /b.zip')).toEqual(['/a.pdf', '/b.zip'])
    expect(normalizeAttachmentUrls('')).toEqual([])
    expect(normalizeAttachmentUrls(null)).toEqual([])
  })
})
