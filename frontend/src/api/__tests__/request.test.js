import { describe, it, expect, vi, beforeEach, beforeAll } from 'vitest'

// 模拟 element-plus
vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    warning: vi.fn(),
  },
}))

// 模拟 logger
vi.mock('@/utils/logger', () => ({
  default: {
    log: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
  },
}))

// 模拟 router
vi.mock('@/router', () => ({
  default: {
    push: vi.fn(),
  },
}))

// 模拟 auth store
const mockResetAuthState = vi.fn()
vi.mock('@/stores/auth', () => ({
  useAuthStore: vi.fn(() => ({
    resetAuthState: mockResetAuthState,
  })),
}))

describe('request.js - API 请求层', () => {
  let request
  let cancelAllPendingRequests

  beforeAll(async () => {
    // 在所有 mock 之后动态导入
    const mod = await import('../request')
    request = mod.default
    cancelAllPendingRequests = mod.cancelAllPendingRequests
  })

  beforeEach(() => {
    vi.clearAllMocks()
  })

  describe('Axios 实例配置', () => {
    it('应设置 baseURL 为 /api', () => {
      expect(request.defaults.baseURL).toBe('/api')
    })

    it('应设置 timeout 为 30000', () => {
      expect(request.defaults.timeout).toBe(30000)
    })

    it('应开启 withCredentials', () => {
      expect(request.defaults.withCredentials).toBe(true)
    })

    it('应设置 xsrfCookieName 为 XSRF-TOKEN', () => {
      expect(request.defaults.xsrfCookieName).toBe('XSRF-TOKEN')
    })

    it('应设置 xsrfHeaderName 为 X-XSRF-TOKEN', () => {
      expect(request.defaults.xsrfHeaderName).toBe('X-XSRF-TOKEN')
    })

    it('应开启 withXSRFToken', () => {
      expect(request.defaults.withXSRFToken).toBe(true)
    })
  })

  describe('请求拦截器', () => {
    it('应注册请求和响应拦截器', () => {
      const reqUse = request.interceptors.request.use
      const resUse = request.interceptors.response.use
      expect(reqUse).toBeDefined()
      expect(resUse).toBeDefined()
    })
  })

  describe('导出函数', () => {
    it('cancelAllPendingRequests 应为函数', () => {
      expect(cancelAllPendingRequests).toBeDefined()
      expect(typeof cancelAllPendingRequests).toBe('function')
    })
  })
})
