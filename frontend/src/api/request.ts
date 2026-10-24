import logger from '@/utils/logger'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'
import { showLoading, hideLoading } from '@/utils/loadingBar'
import { getRequestKey, generateReqKey } from '@/api/requestKey'
import {
  getStatusMessage,
  resolveNetworkErrorMessage,
  isIdempotentMethod,
  extractFieldErrorSummary,
} from '@/api/requestError'

// 全局 Loading 进度条已抽离至 @/utils/loadingBar（HTTP 层不直接操作 DOM）

// ==================== 消息去重（防连续弹窗堆叠） ====================
const messageCache = new Map()
const MESSAGE_THROTTLE_MS = 2000

/**
 * 节流消息提示：相同内容在 2s 内只弹一次
 */
function showThrottledMessage(type, message) {
  const key = `${type}:${message}`
  const lastTime = messageCache.get(key)
  const now = Date.now()
  if (lastTime && now - lastTime < MESSAGE_THROTTLE_MS) return
  messageCache.set(key, now)
  // 定期清理过期缓存
  if (messageCache.size > 50) {
    const cutoff = now - MESSAGE_THROTTLE_MS * 2
    for (const [k, t] of messageCache) {
      if (t < cutoff) messageCache.delete(k)
    }
  }
  ElMessage[type](message)
}

/**
 * 创建并配置 Axios 实例
 *
 * 核心配置：
 * - baseURL: `/api` —— 所有请求统一代理到后端 API 根路径，由 Vite dev server 或 Nginx 反向代理转发
 * - timeout: 30000ms —— 单次请求超时阈值，超时触发网络错误重试机制（响应拦截器中自动重试一次）
 * - withCredentials: true —— 开启跨域 Cookie 携带，后端 JWT 令牌以 HttpOnly Cookie 下发，
 *   浏览器随请求自动携带，前端 JS 不可读令牌内容（防 XSS 窃取）
 * - CSRF 双提交：后端 DoubleSubmitCsrfFilter 下发可读的 XSRF-TOKEN Cookie，
 *   axios 自动读取并在写请求（POST/PUT/DELETE/PATCH）上回填 X-XSRF-TOKEN 请求头
 *
 * 拦截器行为：
 * - 请求拦截：将每个未完成请求注册到 AbortController 管理器，页面切换时统一取消；
 *   不手动注入 Authorization 头（令牌存于 HttpOnly Cookie，由 withCredentials 自动携带）
 * - 响应拦截（成功）：移出待取消列表，根据业务状态码 code !== 200 提示错误
 * - 响应拦截（失败）：按状态码分类处理（401 静默刷新 → 403/404/422/429→500/502/503/504）；
 *   网络错误（无 response）自动重试一次；502/503/504 也自动重试一次
 *
 * @type {import('axios').AxiosInstance}
 */
const service = axios.create({
  baseURL: '/api',
  timeout: 30000,
  // 认证令牌已改为 HttpOnly Cookie 下发，浏览器随请求自动携带，必须开启 withCredentials
  // 才能把 Cookie 发送到跨域（同源/同站）的后端接口；前端 JS 无法读取该 Cookie。
  withCredentials: true,
  // CSRF 双提交：后端 DoubleSubmitCsrfFilter 会下发可读的 XSRF-TOKEN Cookie，
  // axios 自动读取并在写请求上回填 X-XSRF-TOKEN 请求头。显式声明避免不同 axios 版本的默认差异。
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  withXSRFToken: true,
})

/* ============================================
 * 会话探针静默期（/503 连接失败页轮询期间）
 * ============================================ */
/**
 * 探针静默开关：views/error/503.vue 轮询 /auth/permissions 期间置 true。
 *
 * 背景（2026-10-16 启动崩溃修复）：后端未就绪时探针每 3 秒重试一次，若不禁用
 * 提示，用户每 3 秒就被弹一次「网络异常」/「服务器内部错误」，既掩盖本页自己
 * 展示的连接状态，也掩盖其他真正需要关注的错误。静默只吞 toast 与自动重试，
 * 不改写任何状态码分支的控制流（401 刷新、结论回传等照常）。
 */
let probeSilence = false

/** 进入/退出探针静默期（由 /503 页在挂载/卸载时调用） */
export function setProbeSilence(value: boolean) {
  probeSilence = value
}

/** 供测试查询当前静默状态 */
export function isProbeSilent() {
  return probeSilence
}

/* ============================================
 * 请求重试配置
 * ============================================ */
// 记录已重试的请求，避免无限循环（实际重试判定由 generateReqKey + retryCache 完成）
const retryCache = new Map()

/* AbortController 管理器：页面切换时取消未完成请求 */
const pendingRequests = new Map()

// getRequestKey 已抽离至 @/api/requestKey

// 取消所有未完成请求（路由切换时调用）
export function cancelAllPendingRequests() {
  pendingRequests.forEach((cancel) => {
    cancel('页面切换，取消未完成请求')
  })
  pendingRequests.clear()
}

/* 添加请求到待取消列表 */
function addPendingRequest(config) {
  const key = getRequestKey(config)
  const controller = new AbortController()
  config.signal = controller.signal
  pendingRequests.set(key, controller.abort.bind(controller))
}

/* 移除已完成请求 */
function removePendingRequest(config) {
  const key = getRequestKey(config)
  pendingRequests.delete(key)
}

// generateReqKey 已抽离至 @/api/requestKey

/* 判断是否为网络错误（无响应） */
function isNetworkError(error) {
  return !error.response && !!error.message
}

/* 幂等性判定与状态码/网络错误文案已抽离至 @/api/requestError（FE-07） */

/* ============================================
 * 401 单飞刷新（P0-4）
 * ============================================ */
/**
 * 并发 401 时共享同一次刷新请求。
 *
 * 缺陷修复（P0-4）：原实现每个 401 请求各自 `post('/auth/refresh')`、各自
 * `post('/auth/logout')` + `resetAuthState()` + `router.push('/login')`。
 * 一个页面并发 5 个接口同时 401，就会打 5 次刷新、跳 5 次登录页，
 * 且刷新 Cookie 轮换存在竞态（后一次刷新使前一次的会话失效）。
 * 现在用模块级 `refreshPromise` 做 single-flight：所有并发 401 复用同一 Promise，
 * 排队重试的请求因此都能拿到刷新后的新会话 Cookie。
 */
let refreshPromise = null
let logoutPromise = null

function refreshSessionOnce() {
  if (!refreshPromise) {
    // finally 保留原始 settle 结果，且无论成功失败都释放单飞槽位，
    // 使「下一次会话过期」仍能重新发起刷新。
    refreshPromise = service.post('/auth/refresh', {}).finally(() => {
      refreshPromise = null
    })
  }
  return refreshPromise
}

/**
 * 刷新失败后的兜底登出同样单飞：并发 401 只会打一次 /auth/logout。
 */
function logoutSessionOnce() {
  if (!logoutPromise) {
    logoutPromise = service.post('/auth/logout').finally(() => {
      logoutPromise = null
    })
  }
  return logoutPromise
}

/** 供测试与调用方查询当前是否有刷新在途 */
export function isRefreshing() {
  return refreshPromise !== null
}

/** 手动使在途刷新/登出失效（登出后不应复用旧刷新结果） */
export function resetRefreshState() {
  refreshPromise = null
  logoutPromise = null
}

/**
 * 登录态失效处理的去重窗口（毫秒）。
 * 并发 401 只允许一次 `router.push('/login')`，避免路由抖动与重复导航告警。
 */
const LOGIN_REDIRECT_DEDUPE_MS = 1000
let lastLoginRedirectAt = 0

/** 供测试重置跳转去重窗口 */
export function resetLoginRedirectDedupe() {
  lastLoginRedirectAt = 0
}

/**
 * 会话失效统一收尾：清前端登录态 + 提示 + **至多一次**跳转登录页。
 *
 * 跳转带上 `redirect`，重新登录后回到原页面 —— 与路由守卫的未登录分支同口径。
 * 例外：守卫冷启动时发起的 `/auth/permissions` 探针**不在此跳转**（见下方调用处），
 * 由守卫用 `next({path:'/login', query:{redirect}})` 作为唯一出口，避免两条导航互相取消。
 */
function handleAuthExpired() {
  const authStore = useAuthStore()
  authStore.resetAuthState()
  showThrottledMessage('error', '登录已过期，请重新登录')
  const now = Date.now()
  if (now - lastLoginRedirectAt < LOGIN_REDIRECT_DEDUPE_MS) return
  lastLoginRedirectAt = now
  const from = router.currentRoute?.value?.fullPath
  if (from && from !== '/login' && from !== '/register') {
    router.push({ path: '/login', query: { redirect: from } })
    return
  }
  router.push('/login')
}

/**
 * 重试前重建 signal（P1-7 第二处缺陷）。
 *
 * 请求拦截器会把 AbortController 的 signal 写进 config（addPendingRequest）。
 * 直接复用该 config 重试时，若原 signal 已被 `cancelAllPendingRequests()` 触发中止，
 * 重试请求会在发出的瞬间被同一已 aborted 的 signal 取消 → 重试必然失败。
 * 这里克隆 config 并剥离旧 signal，交给请求拦截器重新分配全新控制器。
 */
function cloneConfigForRetry(config) {
  const cloned = { ...config }
  delete cloned.signal
  return cloned
}

/* 状态码 → 文案映射已抽离至 @/api/requestError 的 getStatusMessage（FE-07） */

/* ============================================
 * 请求拦截器
 * ============================================ */
service.interceptors.request.use(
  (config) => {
    /* 添加请求到取消列表 */
    addPendingRequest(config)
    /* 全局 loading 进度条 */
    showLoading()
    /* 认证令牌存于 HttpOnly Cookie，浏览器经 withCredentials 自动携带，
       无需（也不应在）前端手动注入 Authorization 头，避免令牌暴露给 JS 上下文。 */

    return config
  },
  (error) => {
    removePendingRequest(error.config)
    hideLoading()
    return Promise.reject(error)
  },
)

/* ============================================
 * 响应拦截器 - 增强版错误处理
 * ============================================ */
service.interceptors.response.use(
  (response) => {
    /* 移除已完成请求 */
    removePendingRequest(response.config)
    /* 隐藏 loading */
    hideLoading()

    const data = response.data

    /* 空响应体（204 No Content / 无 body 的成功响应）：
       缺陷修复（P1-7 第三处）——原实现直接读 `data.code`，body 为 null 时抛
       `TypeError: Cannot read properties of null`，把一个成功响应变成运行时错误。 */
    if (data === null || data === undefined) {
      return data
    }

    /* 业务状态码判断 */
    if (data.code !== undefined && data.code !== 200) {
      showThrottledMessage('error', data.message || '请求失败')
      /* 把业务码挂在 Error 上：调用方需要区分"上游 AI 不可用(503，要报病因)"与
         其它失败（网络层文案不该原样丢给用户）。仅加属性，不改既有 reject 语义。 */
      const bizError = new Error(data.message || '请求失败') as Error & { code?: number }
      bizError.code = data.code
      return Promise.reject(bizError)
    }

    return data
  },

  async (error) => {
    const config = error.config

    /* 移除已完成/失败请求 */
    removePendingRequest(config)

    /* 被主动取消的请求不提示 */
    if (axios.isCancel(error)) {
      if (import.meta.env.DEV) {
        logger.log('[axios] 请求被取消:', error.message)
      }
      return Promise.reject(error)
    }

    const status = error.response?.status
    const serverMsg = error.response?.data?.message

    /* 隐藏 loading */
    hideLoading()

    /* ---- 网络错误处理（无响应）---- */
    if (isNetworkError(error)) {
      const networkMsg = resolveNetworkErrorMessage(error.message)

      // 探针静默期不弹toast（/503 页自带状态展示与重试 UI）
      if (!probeSilence) {
        showThrottledMessage('error', networkMsg)
      }

      /* 网络抖动自动重试一次：仅限幂等方法（P1-7），非幂等请求绝不重放 */
      if (
        config &&
        !probeSilence &&
        isIdempotentMethod(config.method) &&
        !retryCache.get(generateReqKey(config))
      ) {
        retryCache.set(generateReqKey(config), true)
        try {
          const res = await service(cloneConfigForRetry(config))
          retryCache.delete(generateReqKey(config))
          return res
        } catch (retryErr) {
          logger.error('[request] catch:', retryErr)
          retryCache.delete(generateReqKey(config))
          return Promise.reject(retryErr)
        }
      }

      return Promise.reject(error)
    }

    /* ---- HTTP 状态码分类处理 ---- */
    switch (status) {
      case 400:
        showThrottledMessage('error', serverMsg || '请求参数有误，请检查输入')
        break

      case 401: {
        /* 未授权：尝试用 HttpOnly 刷新 Cookie 静默刷新访问令牌（前端不可读该 Cookie）。
           刷新成功后后端会写入新的访问 Cookie，直接重试原请求即可；无需在前端暂存令牌。 */
        // 加固（#14）：刷新/登出端点自身的 401 **不得**再次触发刷新，否则一旦后端把
        // 「刷新失败」由 400 改为 401，`service.post('/auth/refresh')` 将重新进入本分支，
        // 形成无限递归。此处以 URL 为界，使重试链路**无条件有界**，不再依赖
        // 「刷新失败恰好返回 400（而非 401）」这一隐式且脆弱的前提。
        const url = typeof config?.url === 'string' ? config.url : ''
        const isAuthFlowCall = url.includes('/auth/refresh') || url.includes('/auth/logout')
        // 加固（P0-4）：刷新走 single-flight，并发 401 只发一次 /auth/refresh；
        // 收尾统一交给 handleAuthExpired()，登录页至多跳转一次。
        if (config && !config._retry && !isAuthFlowCall) {
          config._retry = true
          try {
            // 刷新 Cookie 随请求自动携带（withCredentials），请求体为空
            await refreshSessionOnce()
            return await service(cloneConfigForRetry(config))
          } catch (refreshErr) {
            logger.error('[request] catch:', refreshErr)
            // 刷新失败：尽力清除后端会话 Cookie，避免残留脏会话。
            // 登出同样走单飞，避免并发打出 N 个 /auth/logout。
            try {
              await logoutSessionOnce()
            } catch (logoutErr) {
              logger.error('[request] catch:', logoutErr)
              // 登出接口失败不影响主流程（令牌已失效），仅开发环境记录
              if (import.meta.env.DEV) {
                logger.warn('登出失败:', logoutErr)
              }
            }
          }
        }
        // 刷新失败 / 已重试过 / 刷新端点自身 401：清空前端登录态并跳转登录页。
        // 例外（DR-45）：/auth/permissions 是路由守卫在冷启动时发起的「会话探针」，
        // 它的失败结果会由 fetchPermissions 回传给守卫，跳转由守卫的 next() 作为唯一出口；
        // 这里再 push 一次就会与进行中的守卫导航互相取消。
        const isSessionProbe = url.includes('/auth/permissions')
        if (!isSessionProbe) {
          handleAuthExpired()
        }
        break
      }

      case 403:
        showThrottledMessage('error', serverMsg || '抱歉，您没有权限执行此操作')
        break

      case 404:
        showThrottledMessage('error', serverMsg || '请求的资源不存在')
        break

      case 422: {
        /* 校验失败：尝试提取字段级错误信息 */
        const detail = error.response?.data?.detail
        if (Array.isArray(detail) && detail.length > 0) {
          const fieldErrors = extractFieldErrorSummary(detail)
          showThrottledMessage('error', fieldErrors || '数据校验失败')
        } else {
          showThrottledMessage('error', serverMsg || '数据校验失败，请检查输入')
        }
        break
      }

      case 429:
        showThrottledMessage('error', serverMsg || '操作过于频繁，请稍后再试')
        break

      case 500:
        // 探针静默期不提示（/503 页轮询中；且 dev 下 Vite 代理对 ECONNREFUSED
        // 正是返回 500，此时「服务器内部错误」的文案会误导成后端代码故障）
        if (!probeSilence) {
          showThrottledMessage('error', serverMsg || '服务器内部错误，请稍后重试')
        }
        break

      case 502:
      case 503:
      case 504: {
        /* 服务端临时故障：仅幂等方法自动重试一次（P1-7），重试前重建 signal */
        if (
          config &&
          !probeSilence &&
          isIdempotentMethod(config.method) &&
          !retryCache.get(generateReqKey(config))
        ) {
          retryCache.set(generateReqKey(config), true)
          showThrottledMessage('warning', '服务暂时不可用，正在重试...')
          try {
            const res = await service(cloneConfigForRetry(config))
            retryCache.delete(generateReqKey(config))
            return res
          } catch (retryErr) {
            logger.error('[request] catch:', retryErr)
            retryCache.delete(generateReqKey(config))
            showThrottledMessage('error', getStatusMessage(status))
            return Promise.reject(retryErr)
          }
        }
        if (!probeSilence) {
          showThrottledMessage('error', getStatusMessage(status))
        }
        break
      }

      default:
        showThrottledMessage('error', serverMsg || getStatusMessage(status) || '请求失败')
    }

    return Promise.reject(error)
  },
)

export default service
