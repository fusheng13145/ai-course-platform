import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as proctorApi from '@/api/proctor'

/**
 * 监考防作弊 API 契约测试（2026-10-04）
 * <p>本模块此前 0% 覆盖。`/proctor/exam/{id}/student/{sid}/session` 与
 * `/proctor/session/{id}/events` 属两套语义（按考试+学生取会话 vs 按会话取事件），
 * 命名相近、极易串用；`updateThresholds` 为 PUT（管理员），
 * 一旦被改成 POST 后端将 405 且界面只提示"保存失败"。</p>
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

/** 契约表：[导出名, HTTP 方法, 期望 URL, 调用实参, 期望传给 request 的完整实参] */
const CONTRACT: Array<[string, 'get' | 'post' | 'put' | 'delete', string, unknown[], unknown[]]> = [
  ['getSessionEvents', 'get', '/proctor/session/12/events', [12], []],
  ['getStudentSession', 'get', '/proctor/exam/3/student/8/session', [3, 8], []],
  ['getExamRisk', 'get', '/proctor/exam/3/risk', [3], []],
  ['getThresholds', 'get', '/proctor/thresholds', [], []],
  [
    'updateThresholds',
    'put',
    '/proctor/thresholds',
    [{ high: 6, medium: 3, weightLow: 1, weightMedium: 2, weightHigh: 3 }],
    [{ high: 6, medium: 3, weightLow: 1, weightMedium: 2, weightHigh: 3 }],
  ],
  ['getEvent', 'get', '/proctor/events/21', [21], []],
  [
    'reviewEvent',
    'post',
    '/proctor/events/21/review',
    [21, { status: 'EXEMPTED' }],
    [{ status: 'EXEMPTED' }],
  ],
]

describe('proctor API 契约（方法 / URL / 参数）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.get.mockResolvedValue({ code: 200, data: [] })
    mocks.post.mockResolvedValue({ code: 200, data: {} })
    mocks.put.mockResolvedValue({ code: 200, data: {} })
  })

  for (const [name, method, url, callArgs, requestArgs] of CONTRACT) {
    it(`${name} 走 ${method.toUpperCase()} ${url}`, async () => {
      const fn = (proctorApi as unknown as Record<string, Function>)[name]
      expect(typeof fn).toBe('function')
      await fn(...callArgs)
      const spy = spyOf[method]
      expect(spy).toHaveBeenCalledTimes(1)
      expect(spy).toHaveBeenCalledWith(url, ...requestArgs)
    })
  }
})

describe('proctor 模块导出完整性', () => {
  it('默认导出覆盖全部具名接口', () => {
    const named = Object.keys(proctorApi).filter((k) => k !== 'default')
    const defaultKeys = Object.keys(proctorApi.default as object)
    expect(named.filter((k) => !defaultKeys.includes(k))).toEqual([])
  })
})
