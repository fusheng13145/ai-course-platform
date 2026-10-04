import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as systemApi from '@/api/system'

/**
 * 系统配置 / 运维 API 契约测试（2026-10-04）
 * <p>本模块此前 0% 覆盖。`createBackup` 与 `getBackupList` 打同一个 URL
 * `/admin/backup`（POST 触发、GET 列表），方法写反即 405；
 * `updateSystemConfig` 是 **POST 带 {key,value,...options}**（不是 PUT /configs/{key}），
 * 与 `admin.updateConfig` 是不同端点，历史重构中曾被误合并。</p>
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
  ['getSystemConfig', 'get', '/admin/system/configs', ['ai'], [{ params: { group: 'ai' } }]],
  ['getSystemLogs', 'get', '/admin/system/logs', [{ page: 1 }], [{ params: { page: 1 } }]],
  ['getSystemHealth', 'get', '/admin/system/health', [], []],
  ['createBackup', 'post', '/admin/backup', [], []],
  ['getBackupList', 'get', '/admin/backup', [], []],
]

describe('system API 契约（方法 / URL / 参数）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.get.mockResolvedValue({ code: 200, data: [] })
    mocks.post.mockResolvedValue({ code: 200, data: {} })
  })

  for (const [name, method, url, callArgs, requestArgs] of CONTRACT) {
    it(`${name} 走 ${method.toUpperCase()} ${url}`, async () => {
      const fn = (systemApi as unknown as Record<string, Function>)[name]
      expect(typeof fn).toBe('function')
      await fn(...callArgs)
      const spy = spyOf[method]
      expect(spy).toHaveBeenCalledTimes(1)
      expect(spy).toHaveBeenCalledWith(url, ...requestArgs)
    })
  }

  it('updateSystemConfig 以 POST 上报 {key,value} 并合并额外选项（不是 PUT /configs/{key}）', async () => {
    await systemApi.updateSystemConfig('ai.model', 'gpt-x', { group: 'ai', description: '模型' })
    expect(mocks.post).toHaveBeenCalledWith('/admin/system/configs', {
      key: 'ai.model',
      value: 'gpt-x',
      group: 'ai',
      description: '模型',
    })
  })

  it('updateSystemConfig 省略 options 时只带 key/value', async () => {
    await systemApi.updateSystemConfig('site.name', 'AI 课程平台')
    expect(mocks.post).toHaveBeenCalledWith('/admin/system/configs', {
      key: 'site.name',
      value: 'AI 课程平台',
    })
  })
})

describe('system 模块导出完整性', () => {
  it('默认导出覆盖全部具名接口', () => {
    const named = Object.keys(systemApi).filter((k) => k !== 'default')
    const defaultKeys = Object.keys(systemApi.default as object)
    expect(named.filter((k) => !defaultKeys.includes(k))).toEqual([])
  })
})
