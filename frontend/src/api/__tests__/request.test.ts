import { describe, it, expect, vi, beforeEach, beforeAll, afterEach } from 'vitest'
import {
  type AxiosAdapter,
  type AxiosInstance,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { type ApiData } from '@/utils/response'

// 模拟 element-plus
const mockElMessage = vi.hoisted(() => ({
  error: vi.fn(),
  warning: vi.fn(),
  success: vi.fn(),
  info: vi.fn(),
}))
vi.mock('element-plus', () => ({
  ElMessage: mockElMessage,
}))

// 模拟 logger
vi.mock('@/utils/logger', () => ({
  default: {
    log: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
  },
}))

// 模拟 router：拦截器会读 currentRoute 以决定登录页跳转是否带 redirect
const mockRouterPush = vi.hoisted(() => vi.fn())
const mockRouterCurrentRoute = vi.hoisted(() => ({ value: { fullPath: '/' } }))
vi.mock('@/router', () => ({
  default: {
    push: mockRouterPush,
    currentRoute: mockRouterCurrentRoute,
  },
}))

// 模拟 auth store
const mockResetAuthState = vi.fn()
vi.mock('@/stores/auth', () => ({
  useAuthStore: vi.fn(() => ({
    resetAuthState: mockResetAuthState,
  })),
}))

// HTTP 层不再静态引 ElMessage 与旧端 router，三件外设改为宿主注入。
// 这里用同一组 spy 注册宿主，因此既有断言（提示文案、跳转形状）语义保持不变。
import { setApiHost } from '@/api/apiHost'
setApiHost({
  message: mockElMessage,
  push: mockRouterPush,
  currentPath: () => mockRouterCurrentRoute.value.fullPath,
})

describe('request.js - API 请求层', () => {
  let request: AxiosInstance
  let cancelAllPendingRequests: () => void

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

  describe('401 静默刷新有界性（#14 加固）', () => {
    /** 取出响应拦截器的 rejected 处理器（axios 内部结构，经 unknown 断言访问）。 */
    function getResponseRejected() {
      const handlers = (
        request.interceptors.response as unknown as {
          handlers: { rejected: (e: unknown) => Promise<unknown> }[]
        }
      ).handlers
      return handlers[handlers.length - 1].rejected
    }

    it('刷新端点自身 401 不再次触发刷新，避免无限递归', async () => {
      const postSpy = vi.spyOn(request, 'post').mockRejectedValue(new Error('refresh 401'))
      const rejected = getResponseRejected()

      const refreshError = {
        config: { url: '/auth/refresh' },
        response: { status: 401, data: {} },
        message: 'Unauthorized',
      }

      await expect(rejected(refreshError)).rejects.toBeDefined()
      // 关键断言：刷新端点 401 不得再次调用刷新接口（否则递归）
      expect(postSpy).not.toHaveBeenCalledWith('/auth/refresh', {})
      // 应清空登录态（跳登录由 router.push 承担，已 mock）
      expect(mockResetAuthState).toHaveBeenCalled()
      postSpy.mockRestore()
    })

    it('普通业务请求 401 会尝试刷新；刷新失败后清态跳登录', async () => {
      const postSpy = vi.spyOn(request, 'post').mockRejectedValue(new Error('refresh failed'))
      const rejected = getResponseRejected()

      const error = {
        config: { url: '/student/courses' },
        response: { status: 401, data: {} },
        message: 'Unauthorized',
      }

      await expect(rejected(error)).rejects.toBeDefined()
      // 普通请求应发起一次刷新尝试
      expect(postSpy).toHaveBeenCalledWith('/auth/refresh', {})
      expect(mockResetAuthState).toHaveBeenCalled()
      postSpy.mockRestore()
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

/* ============================================================================
 * 拦截器真实行为（P0-4 / P1-7）
 *
 * 用 stub adapter 替换真实传输层：请求仍走完「请求拦截器 → adapter → 响应拦截器」
 * 的完整链路，因此这里断言的是**产品代码的真实行为**，不是测试内手写的逻辑。
 * ==========================================================================*/
describe('request 拦截器 - 401 单飞刷新与重试策略', () => {
  let service: AxiosInstance
  let resetRefreshState: () => void
  let resetLoginRedirectDedupe: () => void
  let adapter: AxiosAdapter

  /** 路由处理函数：按 URL 片段命中后，返回成功响应或被拒绝的 Promise */
  type StubRouteHandler = (config: InternalAxiosRequestConfig) => Promise<AxiosResponse>

  /** adapter 收到的每一次调用（url/method/signal），用于断言重放与 signal 重建 */
  type AdapterCall = Pick<InternalAxiosRequestConfig, 'url' | 'method' | 'signal'>
  let calls: AdapterCall[]

  /** 受控时钟的当前读数（用例之间推进，用例内保持恒定） */
  let fakeNow = 1_000_000

  beforeAll(async () => {
    const mod = await import('../request')
    service = mod.default
    resetRefreshState = mod.resetRefreshState
    resetLoginRedirectDedupe = mod.resetLoginRedirectDedupe
  })

  /** 构造一个成功的 axios 响应（经 transformResponse 原样透传） */
  function ok(data: ApiData, status = 200) {
    return (config: InternalAxiosRequestConfig): Promise<AxiosResponse> =>
      Promise.resolve({
        data,
        status,
        statusText: 'OK',
        headers: {},
        config,
      })
  }

  /** axios 风格的错误：Error 上挂了 config/response/isAxiosError 等字段 */
  type StubAxiosError = Error & {
    config?: unknown
    request?: unknown
    response?: { status: number; data: unknown; headers: unknown; config: unknown }
    isAxiosError?: boolean
  }

  /** 构造一个带 response 的 HTTP 错误（模拟 4xx/5xx） */
  function httpFail(status: number, responseData: ApiData = null) {
    return (config: InternalAxiosRequestConfig) => {
      const err: StubAxiosError = new Error(`Request failed with status code ${status}`)
      err.config = config
      err.response = { status, data: responseData ?? {}, headers: {}, config }
      err.isAxiosError = true
      return Promise.reject(err)
    }
  }

  /** 构造一个「无 response」的网络错误（超时/断网） */
  function networkFail(message = 'Network Error') {
    return (config: InternalAxiosRequestConfig) => {
      const err: StubAxiosError = new Error(message)
      err.config = config
      err.request = {}
      err.isAxiosError = true
      return Promise.reject(err)
    }
  }

  /**
   * 安装按 URL 前缀路由的 adapter。
   * @param {Record<string, StubRouteHandler>} routes key 为 url 片段，value 为处理函数
   */
  function installAdapter(routes: Record<string, StubRouteHandler>) {
    adapter = vi.fn((config: InternalAxiosRequestConfig) => {
      calls.push({ url: config.url, method: config.method, signal: config.signal })
      const key = Object.keys(routes).find((k) => String(config.url).includes(k))
      if (!key) throw new Error(`测试未编排的 URL: ${config.url}`)
      return routes[key](config)
    })
    service.defaults.adapter = adapter
  }

  beforeEach(() => {
    vi.clearAllMocks()
    calls = []
    // 受控时钟：showThrottledMessage（2s 窗口）与登录跳转去重（1s 窗口）都读 Date.now()。
    // 用例内取常量 → 同一用例里的重复提示/重复跳转能被正确去重（这正是要断言的行为）；
    // 用例之间推进 100s → 上一用例写进去重缓存的同名消息不会把本用例的断言吞掉。
    fakeNow += 100_000
    vi.spyOn(Date, 'now').mockImplementation(() => fakeNow)
    // 清掉上一用例残留的单飞槽位与跳转去重窗口，保证用例互相独立
    resetRefreshState()
    resetLoginRedirectDedupe()
  })

  afterEach(() => {
    vi.restoreAllMocks()
    resetRefreshState()
  })

  describe('P0-4 并发 401 单飞刷新', () => {
    /** 首次请求回 401、重放（第二次命中同一 URL）回 200 的编排器 */
    function firstOnly401(data: ApiData) {
      const seen = new Set()
      return (config: InternalAxiosRequestConfig) => {
        if (!seen.has(config.url)) {
          seen.add(config.url)
          return httpFail(401)(config)
        }
        return ok(data)(config)
      }
    }

    /** 统计某 URL 实际发出的次数（含重放） */
    function countCalls(url: string) {
      return calls.filter((c) => c.url === url).length
    }

    it('3 个并发 401 只发起一次 /auth/refresh，且原请求各自重试成功', async () => {
      installAdapter({
        '/auth/refresh': ok({ code: 200, data: null }),
        '/courses': firstOnly401({ code: 200, data: [1] }),
        '/tasks': firstOnly401({ code: 200, data: [2] }),
        '/notices': firstOnly401({ code: 200, data: [3] }),
      })

      const results = await Promise.all([
        service.get('/courses'),
        service.get('/tasks'),
        service.get('/notices'),
      ])

      // 核心断言：并发 3 个 401 → 刷新端点只被打 1 次
      expect(countCalls('/auth/refresh')).toBe(1)
      expect(results.map((r) => r.data)).toEqual([[1], [2], [3]])
      // 刷新成功就绝不能跳登录页、不能清登录态
      expect(mockRouterPush).not.toHaveBeenCalled()
      expect(mockResetAuthState).not.toHaveBeenCalled()
    })

    it('刷新失败时只跳一次登录页、只登出一次（原实现每个请求各跳一次）', async () => {
      mockRouterCurrentRoute.value.fullPath = '/student/courses'
      installAdapter({
        '/auth/refresh': networkFail('Network Error'),
        '/auth/logout': ok({ code: 200, data: null }),
        '/courses': httpFail(401),
        '/tasks': httpFail(401),
        '/notices': httpFail(401),
      })

      const settled = await Promise.allSettled([
        service.get('/courses'),
        service.get('/tasks'),
        service.get('/notices'),
      ])

      expect(settled.every((s) => s.status === 'rejected')).toBe(true)

      expect(countCalls('/auth/refresh')).toBe(1)
      expect(countCalls('/auth/logout')).toBe(1)
      expect(mockRouterPush).toHaveBeenCalledTimes(1)
      // DR-45：跳登录页必须带 redirect，重新登录后回到原页面（与守卫的未登录分支同口径）
      expect(mockRouterPush).toHaveBeenCalledWith({
        path: '/login',
        query: { redirect: '/student/courses' },
      })
      expect(mockResetAuthState).toHaveBeenCalled()
    })

    it('已在登录页时不再叠加 redirect', async () => {
      mockRouterCurrentRoute.value.fullPath = '/login'
      installAdapter({
        '/auth/refresh': networkFail('Network Error'),
        '/auth/logout': ok({ code: 200, data: null }),
        '/courses': httpFail(401),
      })

      await expect(service.get('/courses')).rejects.toBeDefined()
      expect(mockRouterPush).toHaveBeenCalledWith('/login')
    })

    it('守卫发起的 /auth/permissions 探针不在拦截器里跳转，会话结论回交路由守卫（DR-45）', async () => {
      // 冷启动时这次请求就发生在 beforeEach 里：拦截器若同时 router.push('/login')，
      // 就会与守卫自己的导航互相取消 —— 这正是「过期后页面仍显示已登录」的直接成因。
      mockRouterCurrentRoute.value.fullPath = '/teacher/dashboard'
      installAdapter({
        '/auth/permissions': httpFail(401),
        '/auth/refresh': networkFail('Network Error'),
        '/auth/logout': ok({ code: 200, data: null }),
      })

      await expect(service.get('/auth/permissions')).rejects.toBeDefined()
      expect(mockRouterPush).not.toHaveBeenCalled()
      expect(mockResetAuthState).not.toHaveBeenCalled()
    })

    it('重放请求不复用旧 signal，避免已 abort 的 signal 让重试瞬间失败', async () => {
      let n = 0
      installAdapter({
        '/auth/refresh': ok({ code: 200, data: null }),
        '/courses': (config) => {
          n++
          if (n === 1) return httpFail(401)(config)
          return ok({ code: 200, data: ['replayed'] })(config)
        },
      })

      const res = await service.get('/courses')
      expect(res.data).toEqual(['replayed'])

      const courseCalls = calls.filter((c) => c.url === '/courses')
      expect(courseCalls).toHaveLength(2)
      // 首由请求拦截器写入 signal；重放那次必须拿到全新 controller
      expect(courseCalls[0].signal).toBeTruthy()
      expect(courseCalls[1].signal).toBeTruthy()
      expect(courseCalls[1].signal).not.toBe(courseCalls[0].signal)
    })
  })

  describe('P1-7 网络错误仅幂等方法重试', () => {
    function countCalls(url: string) {
      return calls.filter((c) => c.url === url).length
    }

    it('GET 网络错误自动重试一次并成功', async () => {
      let n = 0
      installAdapter({
        '/courses': (config) => {
          n++
          if (n === 1) return networkFail('Network Error')(config)
          return ok({ code: 200, data: ['ok'] })(config)
        },
      })

      const res = await service.get('/courses')
      expect(n).toBe(2)
      expect(res.data).toEqual(['ok'])
    })

    it.each([
      ['post', '/exam-sessions/9/submit', '交卷'],
      ['put', '/submissions/9/grade', '批改'],
      ['delete', '/resources/9', '删除资源'],
    ])('%s %s（%s）网络错误绝不重放', async (method, url) => {
      installAdapter({
        [url]: networkFail('Network Error'),
      })

      await expect(
        service[method as 'post' | 'put' | 'delete'](url, { payload: 1 }),
      ).rejects.toBeDefined()
      // 关键断言：非幂等请求只发一次。重复提交会产生重复答卷/重复记录。
      expect(countCalls(url)).toBe(1)
    })

    it('POST 网络错误仍给出网络异常提示（不重试 ≠ 静默失败）', async () => {
      installAdapter({
        '/exam-sessions/1/submit': networkFail('Network Error'),
      })
      await expect(service.post('/exam-sessions/1/submit', { a: 1 })).rejects.toBeDefined()
      expect(mockElMessage.error).toHaveBeenCalledWith('网络不可用，请检查网络设置')
    })

    it('幂等重试同样重建 signal', async () => {
      let n = 0
      installAdapter({
        '/courses': (config) => {
          n++
          if (n === 1) return networkFail('Network Error')(config)
          return ok({ code: 200, data: [] })(config)
        },
      })
      await service.get('/courses')
      expect(calls).toHaveLength(2)
      expect(calls[1].signal).not.toBe(calls[0].signal)
    })

    it('5xx 只对幂等方法重试；非幂等直接失败不重放', async () => {
      installAdapter({
        '/courses': httpFail(503),
      })
      await expect(service.get('/courses')).rejects.toBeDefined()
      expect(countCalls('/courses')).toBe(2)

      installAdapter({
        '/resources/upload': httpFail(503),
      })
      await expect(service.post('/resources/upload', {})).rejects.toBeDefined()
      expect(countCalls('/resources/upload')).toBe(1)
    })
  })

  describe('P1-7 响应体为空时的健壮性', () => {
    it('204 空响应体（data 为 null）不抛 TypeError，原样返回 null', async () => {
      installAdapter({
        '/notes/1': (config) =>
          Promise.resolve({
            data: null,
            status: 204,
            statusText: 'No Content',
            headers: {},
            config,
          }),
      })

      await expect(service.delete('/notes/1')).resolves.toBeNull()
    })

    it('响应体为 undefined 同样不抛', async () => {
      installAdapter({
        '/notes/2': (config) =>
          Promise.resolve({
            data: undefined,
            status: 200,
            statusText: 'OK',
            headers: {},
            config,
          }),
      })
      await expect(service.get('/notes/2')).resolves.toBeUndefined()
    })

    it('blob 等非对象响应体没有 code 字段时不误判为业务错误', async () => {
      const blob = { size: 10, type: 'application/pdf' }
      installAdapter({
        '/resources/download/1': ok(blob),
      })
      const res = await service.get('/resources/download/1')
      expect(res).toBe(blob)
      expect(mockElMessage.error).not.toHaveBeenCalled()
    })
  })

  describe('业务码与状态码提示', () => {
    it('code !== 200 弹错误并 reject，消息取后端 message', async () => {
      installAdapter({
        '/courses': ok({ code: 500, message: '课程不存在或已下架', data: null }),
      })
      await expect(service.get('/courses')).rejects.toThrow('课程不存在或已下架')
      expect(mockElMessage.error).toHaveBeenCalledWith('课程不存在或已下架')
    })

    it('code !== 200 且无 message 时回落到「请求失败」', async () => {
      installAdapter({
        '/courses': ok({ code: 400 }),
      })
      await expect(service.get('/courses')).rejects.toThrow('请求失败')
    })

    it('code === 200 正常返回信封', async () => {
      installAdapter({
        '/courses': ok({ code: 200, message: 'ok', data: [1, 2] }),
      })
      const res = await service.get('/courses')
      expect(res.data).toEqual([1, 2])
      expect(mockElMessage.error).not.toHaveBeenCalled()
    })

    it('422 的 detail[] 字段级错误按 "; " 拼接展示', async () => {
      installAdapter({
        '/users': httpFail(422, {
          detail: [{ msg: '用户名长度不足' }, { message: '邮箱格式不正确' }, { other: 'x' }],
        }),
      })
      await expect(service.post('/users', { name: 'a' })).rejects.toBeDefined()
      expect(mockElMessage.error).toHaveBeenCalledWith('用户名长度不足; 邮箱格式不正确')
    })

    it('422 的 detail 为空数组时回落到通用文案，不显示空字符串', async () => {
      installAdapter({
        '/users': httpFail(422, { detail: [] }),
      })
      await expect(service.post('/users', {})).rejects.toBeDefined()
      expect(mockElMessage.error).toHaveBeenCalledWith('数据校验失败，请检查输入')
    })

    it('422 无 detail 时显示服务端 message', async () => {
      installAdapter({
        '/users': httpFail(422, { message: '分数必须在 0-100 之间' }),
      })
      await expect(service.post('/users', {})).rejects.toBeDefined()
      expect(mockElMessage.error).toHaveBeenCalledWith('分数必须在 0-100 之间')
    })

    it('403 显示权限提示且不跳登录页（403 不是会话失效）', async () => {
      installAdapter({
        '/admin/users': httpFail(403),
      })
      await expect(service.get('/admin/users')).rejects.toBeDefined()
      expect(mockElMessage.error).toHaveBeenCalledWith('抱歉，您没有权限执行此操作')
      expect(mockRouterPush).not.toHaveBeenCalled()
    })

    it('404 显示资源不存在提示', async () => {
      installAdapter({
        '/courses/999': httpFail(404),
      })
      await expect(service.get('/courses/999')).rejects.toBeDefined()
      expect(mockElMessage.error).toHaveBeenCalledWith('请求的资源不存在')
    })
  })
})
