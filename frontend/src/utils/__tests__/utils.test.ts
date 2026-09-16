import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest'

// 注意：permission.js 的 getDefaultRouteByRole / canAccessRoute 测试
// 已由 permission.test.js 独立完整覆盖，此处不再重复。

// ==================== response.js 测试 ====================
describe('response.js', () => {
  it('unwrap - 提取 data 字段', async () => {
    const { unwrap } = await import('@/utils/response')
    const result = unwrap({ data: { id: 1 }, code: 200 })
    expect(result).toEqual({ id: 1 })
  })

  it('unwrap - data 为 null 时返回 null（不返回原对象）', async () => {
    const { unwrap } = await import('@/utils/response')
    const result = unwrap({ data: null, code: 500, message: '错误' })
    expect(result).toBeNull()
  })

  it('unwrap - null/undefined 原样返回', async () => {
    const { unwrap } = await import('@/utils/response')
    expect(unwrap(null)).toBeNull()
    expect(unwrap(undefined)).toBeUndefined()
  })

  it('unwrap - 无 data 字段时返回原对象', async () => {
    const { unwrap } = await import('@/utils/response')
    const result = unwrap({ code: 200 })
    expect(result).toEqual({ code: 200 })
  })
})

// ==================== request.js (Axios 配置) 测试 ====================
describe('request.js - Axios 配置', () => {
  // 预热：@/api/request 的**首次**动态导入需现场转换 element-plus / router / stores 整张依赖图，
  // 本机实测冷启动单次耗时 > 5s（超过 vitest 默认 testTimeout），会使本文件首个用例非确定性超时
  // （全量运行实测：默认 5s 超时失败、`--testTimeout=60000` 全绿；模块缓存后其余用例均为毫秒级）。
  // 故将这次一次性冷导入放入 beforeAll 并给足超时，使断言不再承担转换成本。
  beforeAll(async () => {
    await import('@/api/request')
  }, 60000)

  it('baseURL 为 /api', async () => {
    const { default: instance } = await import('@/api/request')
    expect(instance.defaults.baseURL).toBe('/api')
  })

  it('withCredentials 为 true', async () => {
    const { default: instance } = await import('@/api/request')
    expect(instance.defaults.withCredentials).toBe(true)
  })

  it('xsrfCookieName 和 xsrfHeaderName 正确配置', async () => {
    const { default: instance } = await import('@/api/request')
    expect(instance.defaults.xsrfCookieName).toBe('XSRF-TOKEN')
    expect(instance.defaults.xsrfHeaderName).toBe('X-XSRF-TOKEN')
  })

  it('timeout 为 30000ms', async () => {
    const { default: instance } = await import('@/api/request')
    expect(instance.defaults.timeout).toBe(30000)
  })

  it('cancelAllPendingRequests 可安全调用', async () => {
    const { cancelAllPendingRequests } = await import('@/api/request')
    expect(() => cancelAllPendingRequests()).not.toThrow()
  })
})

// ==================== logger.js 测试 ====================
describe('logger.js', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('log/warn/error 方法存在且可调用', async () => {
    const logger = (await import('@/utils/logger')).default
    expect(() => logger.log('test')).not.toThrow()
    expect(() => logger.warn('test')).not.toThrow()
    expect(() => logger.error('test')).not.toThrow()
  })
})
