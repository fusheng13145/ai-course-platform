import { beforeEach, describe, expect, it, vi } from 'vitest'

const api = vi.hoisted(() => ({
  getMyCourseSummaries: vi.fn(),
  getCourseDetail: vi.fn(),
  getStudentDashboard: vi.fn(),
  getAllCourses: vi.fn(),
  getCourseById: vi.fn(),
  getOverview: vi.fn(),
  getDashboardStats: vi.fn(),
  getNotificationList: vi.fn(),
}))

vi.mock('@/api/teacher', () => ({
  default: {
    getMyCourseSummaries: api.getMyCourseSummaries,
    getCourseDetail: api.getCourseDetail,
  },
}))
vi.mock('@/api/student', () => ({ default: { getStudentDashboard: api.getStudentDashboard } }))
vi.mock('@/api/course', () => ({
  default: { getAllCourses: api.getAllCourses, getCourseById: api.getCourseById },
}))
vi.mock('@/api/admin', () => ({ default: { getOverview: api.getOverview } }))
vi.mock('@/api/analytics', () => ({ default: { getDashboardStats: api.getDashboardStats } }))
vi.mock('@/api/notification', () => ({ default: { getNotificationList: api.getNotificationList } }))

import {
  clearCourses,
  getCourses,
  loadAdminOverview,
  loadCourse,
  loadNotifications,
} from '../data/api'

const envelope = <T>(data: T) => ({ code: 200, message: 'ok', data })

beforeEach(() => {
  vi.clearAllMocks()
  clearCourses()
})

describe('课程卡片归一', () => {
  it('教师端走 /teacher/courses/summaries，字段别名按 courseId→id、avgProgress→progress 归一', async () => {
    api.getMyCourseSummaries.mockResolvedValue(
      envelope([
        {
          courseId: 4007,
          name: '计算机网络',
          code: 'CS-4007',
          teacherName: '张教授',
          semester: '2026 秋',
          avgProgress: 68,
          studentCount: 60,
          pendingGrading: 5,
          status: 'APPROVED',
        },
      ]),
    )

    const [card] = await getCourses('teacher')
    expect(card).toMatchObject({
      id: 4007,
      name: '计算机网络',
      teacher: '张教授',
      progress: 68,
      students: 60,
      pending: 5,
      short: 'CS40',
    })
    expect(card!.tone).toBeGreaterThanOrEqual(1)
    expect(card!.tone).toBeLessThanOrEqual(5)
  })

  it('同一 id 的 tone 与 short 是确定的（卡片配色不能每次刷新都变）', async () => {
    api.getAllCourses.mockResolvedValue(envelope({ records: [{ id: 4007, name: '计算机网络' }] }))
    const first = await getCourses('admin', true)
    const second = await getCourses('admin', true)
    expect(second[0]).toEqual(first[0])
  })

  it('学生端只认 payload.courses：后端回裸数组时归一结果为空（不是把裸数组当课程列表）', async () => {
    api.getStudentDashboard.mockResolvedValue(envelope([4007, 4060]))
    expect(await getCourses('student', true)).toEqual([])

    api.getStudentDashboard.mockResolvedValue(
      envelope({ courses: [{ courseId: 4007, name: '计算机网络', completionRate: 42 }] }),
    )
    const [card] = await getCourses('student', true)
    expect(card!.progress).toBe(42)
  })

  it('非数字读数一律归零，名称缺失回落到「课程 #id」而不是空白', async () => {
    api.getAllCourses.mockResolvedValue(
      envelope({ records: [{ courseId: 'x', avgProgress: null, studentCount: 'NaN' }] }),
    )
    const [card] = await getCourses('admin', true)
    expect(card).toMatchObject({ id: 0, name: '课程 #0', progress: 0, students: 0 })
  })

  it('课程编号无可提取的 ASCII 时，短名回落名称前两字', async () => {
    api.getAllCourses.mockResolvedValue(
      envelope({ records: [{ id: 1, name: '数据结构与算法', code: '—' }] }),
    )
    const [card] = await getCourses('admin', true)
    expect(card!.short).toBe('数据')
  })

  it('失败与真空返回同一个值：页面必须自管失败态，不得把 [] 渲染成「暂无数据」（M-15 的依据）', async () => {
    api.getAllCourses.mockRejectedValue(new Error('500'))
    const failed = await getCourses('admin', true)

    clearCourses()
    api.getAllCourses.mockResolvedValue(envelope({ records: [] }))
    const empty = await getCourses('admin', true)

    expect(failed).toEqual([])
    expect(empty).toEqual([])
  })

  it('同一角色的列表被缓存，force 才重新取数', async () => {
    api.getMyCourseSummaries.mockResolvedValue(envelope([{ id: 1, name: 'A' }]))
    await getCourses('teacher')
    await getCourses('teacher')
    expect(api.getMyCourseSummaries).toHaveBeenCalledTimes(1)

    await getCourses('teacher', true)
    expect(api.getMyCourseSummaries).toHaveBeenCalledTimes(2)
  })
})

describe('课程详情与通知归一', () => {
  it('教师端详情走 teacherApi，其它角色走 courseApi；空对象判为读不到', async () => {
    api.getCourseDetail.mockResolvedValue(envelope({ id: 4007, name: '计算机网络', credits: '4' }))
    expect(await loadCourse('teacher', 4007)).toMatchObject({ id: 4007, credits: 4 })
    expect(api.getCourseById).not.toHaveBeenCalled()

    api.getCourseById.mockResolvedValue(envelope({}))
    expect(await loadCourse('admin', 4060)).toBeNull()

    api.getCourseById.mockRejectedValue(new Error('404'))
    expect(await loadCourse('admin', 999999)).toBeNull()
  })

  it('管理员首页两路读数各自归因：一路失败不得把另一路也清成空（与 4c 批的 allSettled 判据同源）', async () => {
    api.getOverview.mockResolvedValue(envelope({ courses: 8 }))
    api.getDashboardStats.mockRejectedValue(new Error('500'))

    const { overview, dashboard } = await loadAdminOverview()
    expect(overview, '总览路成功时不该被统计路拖空').toEqual({ courses: 8 })
    expect(dashboard).toEqual({})
  })

  it('三种已读字段写法都认，缺标题回落「通知」，缺 id 用行号兜住 v-for 键', async () => {
    api.getNotificationList.mockResolvedValue(
      envelope({
        records: [
          { id: 1, title: 'A', isRead: false },
          { title: 'B', read: false },
          { title: 'C', unread: true },
          { title: 'D', isRead: true },
          { content: 'E' },
        ],
      }),
    )

    const rows = await loadNotifications()
    expect(rows.map((row) => row.unread)).toEqual([true, true, true, false, false])
    expect(rows[4]).toMatchObject({ id: 'demo-4', title: '通知', content: 'E' })
    expect(api.getNotificationList).toHaveBeenCalledWith({ page: 1, size: 20 })
  })
})
