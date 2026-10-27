import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  getAllTasks: vi.fn(),
  getAllStudents: vi.fn(),
  getAllTaskSubmissions: vi.fn(),
  getTeacherStudents: vi.fn(),
}))

vi.mock('@/api/task', () => ({ default: { getAllTasks: mocks.getAllTasks } }))
vi.mock('@/api/course', () => ({ default: { getAllStudents: mocks.getAllStudents } }))
vi.mock('@/api/submission', () => ({
  default: { getAllTaskSubmissions: mocks.getAllTaskSubmissions },
}))
vi.mock('@/api/user', () => ({ default: { getTeacherStudents: mocks.getTeacherStudents } }))
vi.mock('@/utils/chartTheme', () => ({
  getChartTheme: () => ({ axis: '#ddd', subText: '#777' }),
}))
vi.mock('@/utils/logger', () => ({ default: { warn: vi.fn(), error: vi.fn() } }))

import { useCourseCompletionTab } from '@/composables/useCourseCompletionTab'

function mountTab() {
  let state!: ReturnType<typeof useCourseCompletionTab>
  const wrapper = mount(
    defineComponent({
      setup() {
        state = useCourseCompletionTab(ref(7))
        return () => h('div')
      },
    }),
  )
  return { state, wrapper }
}

describe('useCourseCompletionTab pagination and attempt aggregation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getAllTasks.mockResolvedValue([
      { id: 1, title: '第一次作业', taskType: 'assignment' },
      { id: 2, title: '课前测验', taskType: 'quiz' },
    ])
    mocks.getAllStudents.mockResolvedValue([
      { studentId: 11, isActive: true, approvalStatus: 'APPROVED' },
      { studentId: 12, isActive: true, approvalStatus: 'APPROVED' },
      { studentId: 13, isActive: true, approvalStatus: 'PENDING' },
    ])
    mocks.getTeacherStudents.mockResolvedValue({
      data: [
        { id: 11, fullName: '林一' },
        { id: 12, username: 'stu12' },
      ],
    })
    mocks.getAllTaskSubmissions.mockResolvedValue([
      { id: 101, studentId: 11, attemptNumber: 1, status: 'GRADED', score: '80' },
      { id: 102, studentId: 11, attemptNumber: 2, status: 'RESUBMITTED', score: null },
      { id: 103, studentId: 12, attemptNumber: 1, status: 'GRADED', score: '90' },
      { id: 104, studentId: 13, attemptNumber: 1, status: 'GRADED', score: '100' },
    ])
  })

  it('counts assignment tasks only and uses one latest submission per student', async () => {
    const { state, wrapper } = mountTab()
    await flushPromises()

    expect(mocks.getAllTasks).toHaveBeenCalledWith({ courseId: 7 })
    expect(mocks.getAllStudents).toHaveBeenCalledWith(7)
    expect(mocks.getAllTaskSubmissions).toHaveBeenCalledTimes(1)
    expect(mocks.getAllTaskSubmissions).toHaveBeenCalledWith(1)
    expect(state.overview.value).toMatchObject({
      totalAssignments: 1,
      avgSubmissionRate: 100,
      avgScore: 90,
      totalSubmissions: 2,
    })
    expect(state.assignmentStats.value[0]).toMatchObject({
      submittedCount: 2,
      totalStudents: 2,
      gradedCount: 1,
      avgScore: 90,
    })
    expect(state.studentStats.value.find((student) => student.studentId === 11)).toMatchObject({
      submittedCount: 1,
      avgScore: '-',
      studentName: '林一',
    })
    expect(state.loadNotice.value).toBe('')

    wrapper.unmount()
  })

  it('does not present a fabricated zero submission rate when the student roster is unavailable', async () => {
    mocks.getAllStudents.mockRejectedValue(new Error('offline'))
    mocks.getTeacherStudents.mockResolvedValue({ data: [] })
    const { state, wrapper } = mountTab()
    await flushPromises()

    expect(state.overview.value.avgSubmissionRate).toBeNull()
    expect(state.assignmentStats.value[0].submissionRate).toBeNull()
    expect(state.loadNotice.value).toContain('学生名单暂不可用')
    wrapper.unmount()
  })
})
