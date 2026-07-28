import { describe, it, expect, vi, beforeEach } from 'vitest'

// ==================== permission.js 测试 ====================
describe('permission.js', () => {
  let hasPermission, hasAnyPermission, hasAllPermissions

  beforeEach(async () => {
    const mod = await import('@/utils/permission')
    hasPermission = mod.hasPermission
    hasAnyPermission = mod.hasAnyPermission
    hasAllPermissions = mod.hasAllPermissions
  })

  it('hasPermission - 有权限返回 true', () => {
    const permissions = ['user:read', 'course:write']
    expect(hasPermission(permissions, 'user:read')).toBe(true)
    expect(hasPermission(permissions, 'unknown:perm')).toBe(false)
  })

  it('hasAnyPermission - 任意匹配返回 true', () => {
    const permissions = ['user:read']
    expect(hasAnyPermission(permissions, ['user:read', 'user:write'])).toBe(true)
    expect(hasAnyPermission(permissions, ['admin:access'])).toBe(false)
  })

  it('hasAllPermissions - 全部匹配返回 true', () => {
    const permissions = ['user:read', 'course:write', 'exam:manage']
    expect(hasAllPermissions(permissions, ['user:read', 'course:write'])).toBe(true)
    expect(hasAllPermissions(permissions, ['user:read', 'unknown'])).toBe(false)
  })

  it('空权限列表返回 false', () => {
    expect(hasPermission([], 'any')).toBe(false)
    expect(hasAnyPermission([], ['any'])).toBe(false)
    expect(hasAllPermissions([], [])).toBe(true)
  })
})

// ==================== response.js 测试 ====================
describe('response.js', () => {
  it('unwrap - 提取 data 字段', async () => {
    const { unwrap } = await import('@/utils/response')
    const result = unwrap({ data: { id: 1 }, code: 200 })
    expect(result).toEqual({ id: 1 })
  })

  it('unwrap - code 非200返回原始值', async () => {
    const { unwrap } = await import('@/utils/response')
    const result = unwrap({ data: null, code: 500, message: '错误' })
    expect(result).toEqual({ data: null, code: 500, message: '错误' })
  })
})

// ==================== request.js (Axios 配置) 测试 ====================
describe('request.js - Axios 配置', () => {
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
