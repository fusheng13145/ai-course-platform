import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as adminApi from '@/api/admin'

/**
 * 管理员端 API 契约测试（2026-10-04）
 * <p>本模块此前 0% 覆盖，且是权限面最大的一组接口（用户增删改、密码重置、
 * 批量导入、系统配置、备份）。这里除锁定方法/URL/参数外，还锁定
 * `exportReport` / `createBackup` 的 **re-export 链路**：
 * admin 模块不重复定义而是复用 analytics/system，
 * 一旦有人在此重新定义同名函数，就会打出不存在的 `/admin/...` 路径。</p>
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

const USER_BODY = { username: 'stu01', role: 'STUDENT' }

/** 契约表：[导出名, HTTP 方法, 期望 URL, 调用实参, 期望传给 request 的完整实参] */
const CONTRACT: Array<[string, 'get' | 'post' | 'put' | 'delete', string, unknown[], unknown[]]> = [
  [
    'getUsers',
    'get',
    '/admin/users',
    [{ page: 1, keyword: '张' }],
    [{ params: { page: 1, keyword: '张' } }],
  ],
  ['getUserDetail', 'get', '/admin/users/8', [8], []],
  ['createUser', 'post', '/admin/users', [USER_BODY], [USER_BODY]],
  ['updateUser', 'put', '/admin/users/8', [8, USER_BODY], [USER_BODY]],
  ['deleteUser', 'delete', '/admin/users/8', [8], []],
  [
    'resetUserPassword',
    'put',
    '/admin/users/8/reset-password',
    [8, { password: 'Abc@1234' }],
    [{ password: 'Abc@1234' }],
  ],
  ['importUsers', 'post', '/admin/users/import', [{ rows: [] }], [{ rows: [] }]],
  ['importUsersExcel', 'post', '/admin/users/import-excel', [{ file: 'x' }], [{ file: 'x' }]],
  ['getConfigs', 'get', '/admin/configs', [{ group: 'ai' }], [{ params: { group: 'ai' } }]],
  [
    'updateConfig',
    'put',
    '/admin/configs/site.name',
    ['site.name', { value: 'AI 课程平台' }],
    [{ value: 'AI 课程平台' }],
  ],
  ['getLogs', 'get', '/admin/logs', [], []],
  ['getHealth', 'get', '/admin/health', [], []],
  ['getOverview', 'get', '/admin/statistics/overview', [], []],
  [
    'getUserGrowth',
    'get',
    '/admin/statistics/user-growth',
    [{ days: 7 }],
    [{ params: { days: 7 } }],
  ],
  ['getCourseStats', 'get', '/admin/statistics/courses', [], []],
  ['getAIUsage', 'get', '/admin/statistics/ai-usage', [], []],
  ['getAIMonitor', 'get', '/admin/ai-monitor', [], []],
  ['getAdminAiCurrent', 'get', '/admin/ai/current', [], []],
  ['switchAiProvider', 'post', '/admin/ai/provider', ['qwen'], [{ provider: 'qwen' }]],
  ['testAiProvider', 'get', '/admin/ai/test', [], []],
]

describe('admin API 契约（方法 / URL / 参数）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.get.mockResolvedValue({ code: 200, data: [] })
    mocks.post.mockResolvedValue({ code: 200, data: {} })
    mocks.put.mockResolvedValue({ code: 200, data: {} })
    mocks.del.mockResolvedValue({ code: 200, data: {} })
  })

  for (const [name, method, url, callArgs, requestArgs] of CONTRACT) {
    it(`${name} 走 ${method.toUpperCase()} ${url}`, async () => {
      const fn = (adminApi as unknown as Record<string, Function>)[name]
      expect(typeof fn).toBe('function')
      await fn(...callArgs)
      const spy = spyOf[method]
      expect(spy).toHaveBeenCalledTimes(1)
      expect(spy).toHaveBeenCalledWith(url, ...requestArgs)
    })
  }
})

describe('admin re-export 链路（不得在本模块重复定义）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.get.mockResolvedValue({ code: 200, data: [] })
    mocks.post.mockResolvedValue({ code: 200, data: {} })
  })

  it('exportReport 复用 analytics 模块：GET /admin/statistics/export（注意与 teacher.exportReport 不是同一端点）', async () => {
    await adminApi.exportReport()
    expect(mocks.get).toHaveBeenCalledWith('/admin/statistics/export')
  })

  it('createBackup 复用 system 模块：POST /admin/backup', async () => {
    await adminApi.createBackup()
    expect(mocks.post).toHaveBeenCalledWith('/admin/backup')
  })

  it('默认导出覆盖全部具名接口（含 re-export）', () => {
    const named = Object.keys(adminApi).filter((k) => k !== 'default')
    const defaultKeys = Object.keys(adminApi.default as object)
    expect(named.filter((k) => !defaultKeys.includes(k))).toEqual([])
  })
})
