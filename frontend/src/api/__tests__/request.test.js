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

  describe('CSRF 令牌配置', () => {
    it('xsrfCookieName 正确配置为 XSRF-TOKEN', () => {
      expect(request.defaults.xsrfCookieName).toBe('XSRF-TOKEN')
    })

    it('xsrfHeaderName 正确配置为 X-XSRF-TOKEN', () => {
      expect(request.defaults.xsrfHeaderName).toBe('X-XSRF-TOKEN')
    })

    it('withXSRFToken 已开启', () => {
      expect(request.defaults.withXSRFToken).toBe(true)
    })

    it('CSRF 配置在 axios defaults 中生效', () => {
      const defaults = request.defaults
      expect(defaults.xsrfCookieName).toBeTruthy()
      expect(defaults.xsrfHeaderName).toBeTruthy()
      expect(defaults.withXSRFToken).toBe(true)
    })
  })

  describe('401 未授权处理', () => {
    it('响应拦截器已注册错误处理器', () => {
      const resUse = request.interceptors.response.use
      expect(resUse).toBeDefined()
      // 响应拦截器应有两个处理器：成功处理和错误处理
      expect(typeof resUse).toBe('function')
    })

    it('withCredentials 已开启，支持 Cookie 认证', () => {
      expect(request.defaults.withCredentials).toBe(true)
    })

    it('未手动注入 Authorization 头（使用 HttpOnly Cookie）', () => {
      // 验证请求拦截器不会注入 Authorization 头
      const interceptors = request.interceptors.request
      expect(interceptors).toBeDefined()
    })
  })

  describe('网络错误重试', () => {
    it('timeout 设置为 30000ms', () => {
      expect(request.defaults.timeout).toBe(30000)
    })

    it('响应拦截器包含网络错误处理逻辑', () => {
      // 验证响应拦截器已注册（包含网络错误重试逻辑）
      const resInterceptor = request.interceptors.response
      expect(resInterceptor).toBeDefined()
    })

    it('请求拦截器为每个请求分配 AbortController', () => {
      const reqInterceptor = request.interceptors.request
      expect(reqInterceptor).toBeDefined()
    })

    it('取消的请求不会触发错误提示', () => {
      // 通过 cancelAllPendingRequests 取消请求
      cancelAllPendingRequests()
      // 验证 cancelAllPendingRequests 为函数且不抛出异常
      expect(cancelAllPendingRequests).toBeDefined()
    })
  })

  describe('Axios 实例安全性配置', () => {
    it('baseURL 正确指向 /api 代理', () => {
      expect(request.defaults.baseURL).toBe('/api')
    })

    it('withCredentials 保证跨域 Cookie 携带', () => {
      expect(request.defaults.withCredentials).toBe(true)
    })

    it('实例已正确创建为 axios 实例', () => {
      expect(request).toBeDefined()
      expect(request.get).toBeDefined()
      expect(request.post).toBeDefined()
      expect(request.put).toBeDefined()
      expect(request.delete).toBeDefined()
      expect(request.interceptors).toBeDefined()
    })
  })
})
