import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as classroomApi from '@/api/classroom'

/**
 * 班级管理 API 契约测试（2026-10-04）
 * <p>本模块此前 0% 覆盖。`getMyClassrooms` 的 advisorId 走 query（`/classrooms/my`），
 * 其余学生增减走 path 段 `/classrooms/{id}/students/{studentId}`；
 * 两类拼法混在一起极易写错，故逐条锁定。</p>
 */

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
}))

vi.mock('@/api/request', () => ({
  default: {
    get: mocks.get,
    post: mocks.post,
    put: mocks.put,
    delete: mocks.del,
  },
}))

const spyOf = { get: mocks.get, post: mocks.post, put: mocks.put, delete: mocks.del }

const CLASS_BODY = { name: '计科2101', grade: '2021' }

/** 契约表：[导出名, HTTP 方法, 期望 URL, 调用实参, 期望传给 request 的完整实参] */
const CONTRACT: Array<[string, 'get' | 'post' | 'put' | 'delete', string, unknown[], unknown[]]> = [
  [
    'getClassroomList',
    'get',
    '/classrooms',
    [{ page: 1, size: 10 }],
    [{ params: { page: 1, size: 10 } }],
  ],
  ['getClassroomById', 'get', '/classrooms/11', [11], []],
  ['createClassroom', 'post', '/classrooms', [CLASS_BODY], [CLASS_BODY]],
  ['updateClassroom', 'put', '/classrooms/11', [11, CLASS_BODY], [CLASS_BODY]],
  ['deleteClassroom', 'delete', '/classrooms/11', [11], []],
  ['getDetail', 'get', '/classrooms/11/detail', [11], []],
  [
    'getStudents',
    'get',
    '/classrooms/11/students',
    [11, { keyword: '张' }],
    [{ params: { keyword: '张' } }],
  ],
  ['addStudent', 'post', '/classrooms/11/students/8', [11, 8], []],
  ['removeStudent', 'delete', '/classrooms/11/students/8', [11, 8], []],
  ['getStats', 'get', '/classrooms/11/stats', [11], []],
  ['getMyClassrooms', 'get', '/classrooms/my', [5], [{ params: { advisorId: 5 } }]],
  ['search', 'get', '/classrooms/search', [{ name: '计科' }], [{ params: { name: '计科' } }]],
]

describe('classroom API 契约（方法 / URL / 参数）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.get.mockResolvedValue({ code: 200, data: [] })
    mocks.post.mockResolvedValue({ code: 200, data: {} })
    mocks.put.mockResolvedValue({ code: 200, data: {} })
    mocks.del.mockResolvedValue({ code: 200, data: {} })
  })

  for (const [name, method, url, callArgs, requestArgs] of CONTRACT) {
    it(`${name} 走 ${method.toUpperCase()} ${url}`, async () => {
      const fn = (classroomApi as unknown as Record<string, Function>)[name]
      expect(typeof fn).toBe('function')
      await fn(...callArgs)
      const spy = spyOf[method]
      expect(spy).toHaveBeenCalledTimes(1)
      expect(spy).toHaveBeenCalledWith(url, ...requestArgs)
    })
  }

  it('getStudents 不传第二参数时默认空对象（不会向 axios 传 undefined params）', async () => {
    await classroomApi.getStudents(11)
    expect(mocks.get).toHaveBeenCalledWith('/classrooms/11/students', { params: {} })
  })
})

describe('classroom 模块导出完整性', () => {
  it('默认导出覆盖全部具名接口', () => {
    const named = Object.keys(classroomApi).filter((k) => k !== 'default')
    const defaultKeys = Object.keys(classroomApi.default as object)
    expect(named.filter((k) => !defaultKeys.includes(k))).toEqual([])
  })
})
