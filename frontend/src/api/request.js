import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useAuthStore } from '@/stores/auth'

const service = axios.create({
  baseURL: '/api',
  timeout: 30000
})

/* ============================================
 * 请求重试配置
 * ============================================ */
const MAX_RETRY = 1 // 最大重试次数
const RETRY_STATUS_CODES = [502, 503, 504] // 需要重试的状态码
const retryCache = new Map() // 记录已重试的请求，避免无限循环

/* AbortController 管理器：页面切换时取消未完成请求 */
const pendingRequests = new Map()

/* 根据请求配置生成唯一标识 */
function getRequestKey(config) {
  const { method, url, params, data } = config || {}
  return `${method}:${url}:${JSON.stringify(params)}:${JSON.stringify(data)}`
}

// 取消所有未完成请求（路由切换时调用）
export function cancelAllPendingRequests() {
  pendingRequests.forEach((cancel, key) => {
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
    504: '网关超时，请稍后重试'
  }
  return messages[status] || `请求失败 (${status})`
}

/* ============================================
 * 请求拦截器
 * ============================================ */
service.interceptors.request.use(
  config => {
    /* 添加请求到取消列表 */
    addPendingRequest(config)

    /* 注入认证 token */
    const token = localStorage.getItem('token') || sessionStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }

    return config
  },
  error => {
    removePendingRequest(error.config)
    return Promise.reject(error)
  }
)

/* ============================================
 * 响应拦截器 - 增强版错误处理
 * ============================================ */
service.interceptors.response.use(
  response => {
    /* 移除已完成请求 */
    removePendingRequest(response.config)
    
    const data = response.data

    /* 业务状态码判断 */
    if (data.code !== undefined && data.code !== 200 && data.code !== 0) {
      ElMessage.error(data.message || '请求失败')
      return Promise.reject(new Error(data.message || '请求失败'))
    }

    return data
  },

  async error => {
    const config = error.config

    /* 移除已完成/失败请求 */
    removePendingRequest(config)

    /* 被主动取消的请求不提示 */
    if (axios.isCancel(error)) {
      console.log('[axios]', error.message)
      return Promise.reject(error)
    }

    const status = error.response?.status
    const serverMsg = error.response?.data?.message

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

      ElMessage.error(networkMsg)

      /* 网络抖动自动重试一次 */
      if (config && !retryCache.get(generateReqKey(config))) {
        retryCache.set(generateReqKey(config), true)
        try {
          const res = await service(config)
          retryCache.delete(generateReqKey(config))
          return res
        } catch (retryErr) {
          retryCache.delete(generateReqKey(config))
          return Promise.reject(retryErr)
        }
      }

      return Promise.reject(error)
    }

    /* ---- HTTP 状态码分类处理 ---- */
    switch (status) {
      case 400:
        ElMessage.error(serverMsg || '请求参数有误，请检查输入')
        break

      case 401:
        /* 未授权：清除 token 并跳转登录页 */
        const authStore = useAuthStore()
        localStorage.removeItem('token'); localStorage.removeItem('user')
        sessionStorage.removeItem('token'); sessionStorage.removeItem('user')
        authStore.token = ''; authStore.user = null
        ElMessage.error('登录已过期，请重新登录')
        router.push('/login')
        break

      case 403:
        ElMessage.error(serverMsg || '抱歉，您没有权限执行此操作')
        break

      case 404:
        ElMessage.error(serverMsg || '请求的资源不存在')
        break

      case 422: {
        /* 校验失败：尝试提取字段级错误信息 */
        const detail = error.response?.data?.detail
        if (Array.isArray(detail) && detail.length > 0) {
          const fieldErrors = detail.map(d => d?.msg || d?.message).filter(Boolean).join('; ')
          ElMessage.error(fieldErrors || '数据校验失败')
        } else {
          ElMessage.error(serverMsg || '数据校验失败，请检查输入')
        }
        break
      }

      case 429:
        ElMessage.error(serverMsg || '操作过于频繁，请稍后再试')
        break

      case 500:
        ElMessage.error(serverMsg || '服务器内部错误，请稍后重试')
        break

      case 502:
      case 503:
      case 504: {
        /* 服务端临时故障：自动重试一次 */
        if (config && !retryCache.get(generateReqKey(config))) {
          retryCache.set(generateReqKey(config), true)
          ElMessage.warning('服务暂时不可用，正在重试...')
          try {
            const res = await service(config)
            retryCache.delete(generateReqKey(config))
            return res
          } catch (retryErr) {
            retryCache.delete(generateReqKey(config))
            ElMessage.error(getStatusMessage(status))
            return Promise.reject(retryErr)
          }
        }
        ElMessage.error(getStatusMessage(status))
        break
      }

      default:
        ElMessage.error(serverMsg || getStatusMessage(status) || '请求失败')
    }

    return Promise.reject(error)
  }
)

export default service
