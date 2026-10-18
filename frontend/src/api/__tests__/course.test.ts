import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as courseApi from '@/api/course'

const mocks = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('@/api/request', () => ({
  default: { get: mocks.get },
}))

describe('course API', () => {
  beforeEach(() => vi.clearAllMocks())

  it('getAllMyCourses follows pagination instead of silently truncating at the first page', async () => {
    mocks.get
      .mockResolvedValueOnce({
        code: 200,
        data: { list: [{ id: 1, name: '数据结构' }], total: 101 },
      })
      .mockResolvedValueOnce({
        code: 200,
        data: { list: [{ id: 2, name: '算法设计' }], total: 101 },
      })

    await expect(courseApi.getAllMyCourses()).resolves.toEqual([
      { id: 1, name: '数据结构' },
      { id: 2, name: '算法设计' },
    ])
    expect(mocks.get).toHaveBeenNthCalledWith(1, '/courses/my', { params: { page: 1, size: 100 } })
    expect(mocks.get).toHaveBeenNthCalledWith(2, '/courses/my', { params: { page: 2, size: 100 } })
  })

  it('propagates a later-page failure rather than reporting a partial course list as complete', async () => {
    mocks.get
      .mockResolvedValueOnce({
        code: 200,
        data: { list: [{ id: 1, name: '数据结构' }], total: 101 },
      })
      .mockRejectedValueOnce(new Error('page unavailable'))

    await expect(courseApi.getAllMyCourses()).rejects.toThrow('page unavailable')
  })

  it('getAllCourses preserves filters while reading every matching course page', async () => {
    mocks.get
      .mockResolvedValueOnce({
        code: 200,
        data: { list: [{ id: 9, name: '平台课程 A' }], total: 101 },
      })
      .mockResolvedValueOnce({
        code: 200,
        data: { list: [{ id: 10, name: '平台课程 B' }], total: 101 },
      })

    await expect(courseApi.getAllCourses({ status: 'ACTIVE' })).resolves.toHaveLength(2)
    expect(mocks.get).toHaveBeenNthCalledWith(1, '/courses', {
      params: { status: 'ACTIVE', page: 1, size: 100 },
    })
    expect(mocks.get).toHaveBeenNthCalledWith(2, '/courses', {
      params: { status: 'ACTIVE', page: 2, size: 100 },
    })
  })

  it('getAllCourses accepts an empty list alias when records contains the current page', async () => {
    mocks.get.mockResolvedValueOnce({
      code: 200,
      data: { list: [], records: [{ id: 2, name: '编译原理' }], total: 1 },
    })

    await expect(courseApi.getAllCourses()).resolves.toEqual([{ id: 2, name: '编译原理' }])
  })

  it('getAllStudents retrieves every enrollment page for accurate course-level statistics', async () => {
    mocks.get
      .mockResolvedValueOnce({
        code: 200,
        data: { list: [{ studentId: 1 }], total: 101 },
      })
      .mockResolvedValueOnce({
        code: 200,
        data: { list: [{ studentId: 2 }], total: 101 },
      })

    await expect(courseApi.getAllStudents(7)).resolves.toEqual([{ studentId: 1 }, { studentId: 2 }])
    expect(mocks.get).toHaveBeenNthCalledWith(1, '/courses/7/students', {
      params: { page: 1, size: 100 },
    })
    expect(mocks.get).toHaveBeenNthCalledWith(2, '/courses/7/students', {
      params: { page: 2, size: 100 },
    })
  })

  it('includes the all-course helper in the default API export', () => {
    expect(courseApi.default.getAllMyCourses).toBe(courseApi.getAllMyCourses)
    expect(courseApi.default.getAllCourses).toBe(courseApi.getAllCourses)
  })
})
