import logger from '@/utils/logger'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

// ==================== 全局 Loading 进度条 ====================
let requestCount = 0
let loadingTimer = null

function showLoading() {
  requestCount++
  const bar = document.getElementById('global-loading-bar')
  if (bar) {
    bar.style.display = 'block'
    bar.style.width = '0%'
    bar.style.transition = 'none'
    // 强制回流后启动动画
    void bar.offsetWidth
    bar.style.width = '85%'
    bar.style.transition = 'width 8s cubic-bezier(0.1, 0.05, 0, 1)'
  }
  // 安全兜底：30s 后自动隐藏
  clearTimeout(loadingTimer)
  loadingTimer = setTimeout(() => hideLoading(true), 30000)
}

function hideLoading(force = false) {
  if (!force) {
    requestCount = Math.max(0, requestCount - 1)
    if (requestCount > 0) return
  } else {
    requestCount = 0
  }
  clearTimeout(loadingTimer)
  const bar = document.getElementById('global-loading-bar')
  if (bar) {
    bar.style.width = '100%'
    bar.style.transition = 'width 0.3s ease-out'
    setTimeout(() => {
      bar.style.display = 'none'
      bar.style.width = '0%'
    }, 350)
  }
}

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
 * 请求重试配置
 * ============================================ */
// 记录已重试的请求，避免无限循环（实际重试判定由 generateReqKey + retryCache 完成）
const retryCache = new Map()

/* AbortController 管理器：页面切换时取消未完成请求 */
const pendingRequests = new Map()

/* 根据请求配置生成唯一标识 */
function getRequestKey(config) {
  const { method, url, params, data } = config || {}
  return `${method}:${url}:${JSON.stringify(params)}:${JSON.stringify(data)}`
}

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

/* 根据请求配置生成唯一标识（用于重试去重） */
function generateReqKey(config) {
  const { method, url, params, data } = config || {}
  const paramStr = params ? JSON.stringify(params) : ''
  const dataStr = data ? JSON.stringify(data) : ''
  return `${method}:${url}?${paramStr}|${dataStr}`
}

/* 判断是否为网络错误（无响应） */
function isNetworkError(error) {
  return !error.response && !!error.message
}

/* 根据状态码获取友好提示信息 */
function getStatusMessage(status) {
  const messages = {
    400: '请求参数错误，请检查输入',
    401: '登录已过期，请重新登录',
    403: '抱歉，您没有权限执行此操作',
    404: '请求的资源不存在',
    405: '请求方法不被允许',
    408: '请求超时，请稍后重试',
    409: '数据冲突，请刷新后重试',
    410: '资源已被删除',
    413: '上传文件过大',
    415: '不支持的媒体类型',
    422: '数据校验失败',
    423: '资源已被锁定',
    429: '操作过于频繁，请稍后再试',
    500: '服务器内部错误，请联系管理员',
    501: '服务器不支持此功能',
    502: '网关错误，服务暂时不可用',
    503: '服务维护中，请稍后访问',
    504: '网关超时，请稍后重试',
  }
  return messages[status] || `请求失败 (${status})`
}

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

    /* 业务状态码判断 */
    if (data.code !== undefined && data.code !== 200) {
      showThrottledMessage('error', data.message || '请求失败')
      return Promise.reject(new Error(data.message || '请求失败'))
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
        // eslint-disable-next-line no-console
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
      let networkMsg = '网络异常'

      if (error.message.includes('timeout') || error.message.includes('Timeout')) {
        networkMsg = '请求超时，请检查网络连接'
      } else if (error.message.includes('Network Error')) {
        networkMsg = '网络不可用，请检查网络设置'
      } else if (error.message.includes('ERR_INTERNET_DISCONNECTED')) {
        networkMsg = '网络已断开，请连接后重试'
      }

      showThrottledMessage('error', networkMsg)

      /* 网络抖动自动重试一次 */
      if (config && !retryCache.get(generateReqKey(config))) {
        retryCache.set(generateReqKey(config), true)
        try {
          const res = await service(config)
          retryCache.delete(generateReqKey(config))
          return res
        } catch (retryErr) {
          console.error('[request.js] catch:', retryErr)
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

      case 401:
        /* 未授权：尝试用 HttpOnly 刷新 Cookie 静默刷新访问令牌（前端不可读该 Cookie）。
           刷新成功后后端会写入新的访问 Cookie，直接重试原请求即可；无需在前端暂存令牌。 */
        const authStore = useAuthStore()
        if (config && !config._retry) {
          config._retry = true
          try {
            // 刷新 Cookie 随请求自动携带（withCredentials），请求体为空
            await service.post('/auth/refresh', {})
            return service(config)
          } catch (refreshErr) {
            console.error('[request.js] catch:', refreshErr)
            // 刷新失败：尽力清除后端会话 Cookie，避免残留脏会话
            try {
              await service.post('/auth/logout')
            } catch (logoutErr) {
              console.error('[request.js] catch:', logoutErr)
              // 登出接口失败不影响主流程（令牌已失效），仅开发环境记录
              if (import.meta.env.DEV) {
                // eslint-disable-next-line no-console
                logger.warn('登出失败:', logoutErr)
              }
            }
          }
        }
        // 刷新失败或已重试过：清空前端登录态并跳转登录页
        authStore.resetAuthState()
        showThrottledMessage('error', '登录已过期，请重新登录')
        router.push('/login')
        break

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
          const fieldErrors = detail
            .map((d) => d?.msg || d?.message)
            .filter(Boolean)
            .join('; ')
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
        showThrottledMessage('error', serverMsg || '服务器内部错误，请稍后重试')
        break

      case 502:
      case 503:
      case 504: {
        /* 服务端临时故障：自动重试一次 */
        if (config && !retryCache.get(generateReqKey(config))) {
          retryCache.set(generateReqKey(config), true)
          showThrottledMessage('warning', '服务暂时不可用，正在重试...')
          try {
            const res = await service(config)
            retryCache.delete(generateReqKey(config))
            return res
          } catch (retryErr) {
            console.error('[request.js] catch:', retryErr)
            retryCache.delete(generateReqKey(config))
            showThrottledMessage('error', getStatusMessage(status))
            return Promise.reject(retryErr)
          }
        }
        showThrottledMessage('error', getStatusMessage(status))
        break
      }

      default:
        showThrottledMessage('error', serverMsg || getStatusMessage(status) || '请求失败')
    }

    return Promise.reject(error)
  },
)

export default service
