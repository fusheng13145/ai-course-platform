import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'

const mocks = vi.hoisted(() => ({
  getStudentCourseProgress: vi.fn(),
  getCourseReport: vi.fn(),
  getPendingGrading: vi.fn(),
  message: Object.assign(vi.fn(), {
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  }),
}))

vi.mock('@/api/analytics', () => ({
  default: { getStudentCourseProgress: mocks.getStudentCourseProgress },
}))
vi.mock('@/api/grade', () => ({ default: { getCourseReport: mocks.getCourseReport } }))
vi.mock('@/api/task', () => ({ default: { getPendingGrading: mocks.getPendingGrading } }))
vi.mock('element-plus', () => ({ ElMessage: mocks.message }))
vi.mock('@/utils/chartTheme', () => ({
  getChartTheme: () => ({ axis: '#ddd', subText: '#777' }),
  getMonoSeries: () => ['#123456'],
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

import { useCourseGradesTab } from '@/composables/useCourseGradesTab'

function withSetup(courseId = 7) {
  const courseIdRef = ref(courseId)
  let state!: ReturnType<typeof useCourseGradesTab>
  const wrapper = mount(
    defineComponent({
      setup() {
        state = useCourseGradesTab(courseIdRef)
        return () => h('div')
      },
    }),
  )
  return { state, courseIdRef, wrapper }
}

describe('useCourseGradesTab', () => {
  beforeEach(() => {
    vi.clearAllMocks()
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
      data: [
        { id: 3, courseId: 7, title: '第一章作业' },
        { id: 4, courseId: 8, title: '其他课程作业' },
      ],
    })
  })

  it('merges course progress with canonical final grades and filters pending tasks by course', async () => {
    const { state, wrapper } = withSetup()
    await flushPromises()

    expect(mocks.getStudentCourseProgress).toHaveBeenCalledWith(7)
    expect(mocks.getCourseReport).toHaveBeenCalledWith(7)
    expect(state.gradeList.value).toHaveLength(1)
    expect(state.gradeList.value[0]).toMatchObject({
      studentNo: 'S0012',
      homeworkScore: 82,
      examScore: 76,
      totalScore: 64.5,
    })
    expect(state.overview.value.avgScore).toBe(64.5)
    expect(state.pendingTasks.value).toEqual([
      { id: 3, taskName: '第一章作业', pendingCount: null },
    ])
    expect(state.loadNotice.value).toBe('')

    wrapper.unmount()
  })

  it('clears rows when the course id becomes invalid', async () => {
    const { state, courseIdRef, wrapper } = withSetup()
    await flushPromises()
    courseIdRef.value = 0
    await state.loadData()

    expect(state.gradeList.value).toEqual([])
    expect(state.pendingTasks.value).toEqual([])
    expect(state.overview.value.avgScore).toBeNull()
    expect(state.loadNotice.value).toContain('课程信息无效')

    wrapper.unmount()
  })

  it('does not present a failed pending-task request as zero tasks', async () => {
    mocks.getPendingGrading.mockRejectedValue(new Error('network error'))
    const { state, wrapper } = withSetup()
    await flushPromises()

    expect(state.pendingTasks.value).toEqual([])
    expect(state.pendingUnavailable.value).toBe(true)
    expect(state.overview.value.pendingCount).toBeNull()
    expect(state.loadNotice.value).toContain('待批阅任务暂不可用')

    wrapper.unmount()
  })
})
