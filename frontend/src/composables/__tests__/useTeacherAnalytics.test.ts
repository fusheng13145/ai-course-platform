import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  getDashboardStats: vi.fn(),
  getStudentCourseProgress: vi.fn(),
  getTeacherDashboardV2: vi.fn(),
}))

vi.mock('@/api/analytics', () => ({
  default: {
    getDashboardStats: mocks.getDashboardStats,
    getStudentCourseProgress: mocks.getStudentCourseProgress,
  },
}))
vi.mock('@/api/teacher', () => ({
  default: { getTeacherDashboardV2: mocks.getTeacherDashboardV2 },
}))
vi.mock('@/utils/chartTheme', () => ({
  getChartTheme: () => ({
    axis: '#ddd',
    subText: '#777',
    tooltipBg: '#fff',
    tooltipBorder: '#ddd',
    tooltipText: '#222',
    split: '#eee',
    text: '#222',
    bg: '#fff',
  }),
  getMonoSeries: () => [
    '#111111',
    '#222222',
    '#333333',
    '#444444',
    '#555555',
    '#666666',
    '#777777',
  ],
  monoScore: () => '#222222',
}))

import { useTeacherAnalytics } from '@/composables/useTeacherAnalytics'

function withSetup() {
  let state!: ReturnType<typeof useTeacherAnalytics>
  const wrapper = mount(
    defineComponent({
      setup() {
        state = useTeacherAnalytics()
        return () => h('div')
      },
    }),
  )
  return { state, wrapper }
}

describe('useTeacherAnalytics', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getDashboardStats.mockResolvedValue({
      code: 200,
      data: {
        trendData: [
          { label: '2026-10-12', value: 2 },
          { date: '2026-10-13', count: 4 },
        ],
      },
    })
    mocks.getTeacherDashboardV2.mockResolvedValue({
      code: 200,
      data: {
        courseCount: 2,
        studentCount: 3,
        avgScore: 81.4,
        pendingGrading: 5,
        taskPointOverview: { submissionRate: 72.5 },
        courseCards: [
          {
            courseId: 11,
            name: '数据结构',
            studentCount: 2,
            avgProgress: 65,
            completionRate: 50,
            pendingGrading: 3,
          },
          {
            courseId: 12,
            name: '算法基础',
            studentCount: 1,
            avgProgress: 90,
            completionRate: 100,
            pendingGrading: 2,
          },
        ],
      },
    })
    mocks.getStudentCourseProgress.mockImplementation((courseId: number) =>
      Promise.resolve({
        code: 200,
        data:
          courseId === 11
            ? [
                {
                  studentId: 1,
                  studentName: '林同学',
                  completedTasks: 3,
                  avgAssignmentScore: 92,
                },
                {
                  studentId: 2,
                  studentName: '周同学',
                  completedTasks: 1,
                  avgAssignmentScore: 58,
                },
              ]
            : [
                {
                  studentId: 1,
                  studentName: '林同学',
                  completedTasks: 5,
                  avgAssignmentScore: 82,
                },
              ],
      }),
    )
  })

  it('uses backend submission and course progress metrics without synthetic values', async () => {
    const { state, wrapper } = withSetup()
    await flushPromises()

    expect(mocks.getDashboardStats).toHaveBeenCalledWith()
    expect(mocks.getStudentCourseProgress).toHaveBeenNthCalledWith(1, 11)
    expect(mocks.getStudentCourseProgress).toHaveBeenNthCalledWith(2, 12)
    expect(state.overview.value).toMatchObject({
      courseCount: 2,
      studentCount: 3,
      avgScore: 81.4,
      submissionRate: 72.5,
      pendingGrading: 5,
    })
    expect(state.trendOption.value.series[0].data).toEqual([2, 4])
    expect(state.distributionOption.value.series[0].data).toEqual([1, 1, 0, 0, 1])
    expect(state.radarOption.value.series[0].data).toEqual([
      { name: '数据结构', value: [65, 50] },
      { name: '算法基础', value: [90, 100] },
    ])
    expect(state.activityRankOption.value.series[0].data).toEqual([1, 8])
    expect(state.completionTable.value).toHaveLength(2)
    expect(state.loadNotice.value).toBe('')

    wrapper.unmount()
  })

  it('shows a partial-data warning when one course progress request fails', async () => {
    mocks.getStudentCourseProgress.mockRejectedValueOnce(new Error('offline'))
    const { state, wrapper } = withSetup()
    await flushPromises()

    expect(state.loadNotice.value).toContain('1 门课程的学情数据暂不可用')
    expect(state.completionTable.value).toHaveLength(2)

    wrapper.unmount()
  })
})
