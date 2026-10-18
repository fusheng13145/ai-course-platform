import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  getAllMyCourses: vi.fn(),
  getStudentCourseProgress: vi.fn(),
  getCourseReport: vi.fn(),
  getPendingGrading: vi.fn(),
  push: vi.fn(),
  message: Object.assign(vi.fn(), {
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  }),
}))

vi.mock('@/api/course', () => ({ default: { getAllMyCourses: mocks.getAllMyCourses } }))
vi.mock('@/api/analytics', () => ({
  default: { getStudentCourseProgress: mocks.getStudentCourseProgress },
}))
vi.mock('@/api/grade', () => ({ default: { getCourseReport: mocks.getCourseReport } }))
vi.mock('@/api/task', () => ({ default: { getPendingGrading: mocks.getPendingGrading } }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: mocks.push }) }))
vi.mock('element-plus', () => ({ ElMessage: mocks.message }))
vi.mock('@/utils/chartTheme', () => ({
  getChartTheme: () => ({ axis: '#ddd', split: '#eee', text: '#777' }),
  monoScore: () => '#123456',
}))
vi.mock('xlsx', () => ({
  utils: {
    book_new: vi.fn(() => ({})),
    book_append_sheet: vi.fn(),
    json_to_sheet: vi.fn(() => ({})),
  },
  writeFile: vi.fn(),
}))

import { useTeacherGrades } from '@/composables/useTeacherGrades'

function withSetup() {
  let state!: ReturnType<typeof useTeacherGrades>
  const wrapper = mount(
    defineComponent({
      setup() {
        state = useTeacherGrades()
        return () => h('div')
      },
    }),
  )
  return { state, wrapper }
}

describe('useTeacherGrades canonical data flow', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getAllMyCourses.mockResolvedValue([{ id: 7, name: '数据结构' }])
    mocks.getStudentCourseProgress.mockResolvedValue({
      code: 200,
      data: [
        {
          studentId: 12,
          studentNo: 'S0012',
          studentName: '林同学',
          avgAssignmentScore: 82,
          avgExamScore: 76,
        },
      ],
    })
    mocks.getCourseReport.mockResolvedValue({
      code: 200,
      data: [
        {
          studentId: 12,
          studentName: '林同学',
          finalScore: '64.50',
          components: [
            {
              key: 'homework',
              name: '平时作业',
              weight: 40,
              score: 82,
              contribution: 32.8,
              graded: true,
            },
          ],
          warnings: [],
        },
      ],
    })
    mocks.getPendingGrading.mockResolvedValue({
      code: 200,
      data: [{ id: 31, courseId: 7, title: '第一章作业' }],
    })
  })

  it('uses course student progress and configured final grades, not the signed-in teacher records', async () => {
    const { state, wrapper } = withSetup()
    await flushPromises()

    expect(mocks.getAllMyCourses).toHaveBeenCalledOnce()
    expect(mocks.getStudentCourseProgress).toHaveBeenCalledWith(7)
    expect(mocks.getCourseReport).toHaveBeenCalledWith(7)
    expect(state.gradeList.value).toHaveLength(1)
    expect(state.gradeList.value[0]).toMatchObject({
      id: '7:12',
      studentNo: 'S0012',
      studentName: '林同学',
      homeworkScore: 82,
      examScore: 76,
      totalScore: 64.5,
      details: [{ taskName: '平时作业', weight: 40, contribution: 32.8, status: 'graded' }],
    })
    expect(state.overview.value.avgScore).toBe(64.5)
    expect(state.hasDistribution.value).toBe(true)

    await state.goToGrading(state.pendingTasks.value[0])
    expect(mocks.push).toHaveBeenCalledWith({
      path: '/teacher/courses/7/submissions',
      query: { taskId: '31' },
    })

    wrapper.unmount()
  })

  it('refresh retries a course-list failure instead of leaving the page permanently empty', async () => {
    mocks.getAllMyCourses.mockRejectedValueOnce(new Error('offline'))
    const { state, wrapper } = withSetup()
    await flushPromises()

    expect(state.loadNotice.value).toContain('课程列表暂不可用')
    mocks.getAllMyCourses.mockResolvedValue([{ id: 7, name: '数据结构' }])
    await state.refresh()

    expect(state.courseList.value).toHaveLength(1)
    expect(state.gradeList.value[0].totalScore).toBe(64.5)
    expect(state.loadNotice.value).toBe('')
    wrapper.unmount()
  })

  it('does not show failed pending-task data as a zero count', async () => {
    mocks.getPendingGrading.mockRejectedValue(new Error('offline'))
    const { state, wrapper } = withSetup()
    await flushPromises()

    expect(state.pendingTasks.value).toEqual([])
    expect(state.pendingUnavailable.value).toBe(true)
    expect(state.overview.value.pendingCount).toBeNull()
    expect(state.loadNotice.value).toContain('待批阅任务暂不可用')
    wrapper.unmount()
  })
})
