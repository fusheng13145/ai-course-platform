/* eslint-disable no-console */
/**
 * 前端监控模块（统一错误采集与上报出口）。
 *
 * 职责：
 * 1. 统一采集 Vue 全局错误（组件渲染异常 / 异步错误），由 main.js 的 errorHandler 接入；
 * 2. 通过可选配置把错误结构化上报到后端监控端点（生产排障证据链），未配置时静默降级为控制台；
 * 3. 为未来接入第三方监控（如 Sentry）预留同一插拔面——只需在本文件增加
 *    VITE_SENTRY_DSN 分支，即可全站生效，main.js 无需再改。
 *
 * 配置（Vite 环境变量，构建时注入，见根目录 .env.example / docker-compose.yml build args）：
 * - VITE_MONITOR_ENDPOINT：后端错误上报端点（相对或绝对 URL）。
 *   例如：VITE_MONITOR_ENDPOINT=/api/monitor/frontend-error
 *   为空（默认）时不启用远端上报，仅控制台输出，零副作用。
 *
 * 上报通道设计：
 * - 优先 navigator.sendBeacon（页面卸载场景也不丢包，同源无 CORS 开销）；
 * - 不支持时降级 fetch + keepalive；
 * - 上报本身失败一律静默吞掉，绝不把前端故障二次放大为新的异常。
 */
import logger from '@/utils/logger'

// import.meta.env.DEV 由 Vite 注入；在非 Vite 环境（如单测）下安全回退为 false
const isDev = Boolean(import.meta && import.meta.env && import.meta.env.DEV)

// 上报端点（相对路径同源部署即同源请求；跨域部署需后端 CORS 放行）
const endpoint = import.meta?.env?.VITE_MONITOR_ENDPOINT || ''

let initialized = false

/**
 * 初始化监控模块（幂等）。建议在应用启动早期（main.js）调用一次。
 */
export function initMonitoring() {
  if (initialized) return
  initialized = true
  if (endpoint && isDev) {
    logger.info(`[monitor] 前端错误远端上报已启用: ${endpoint}（生产构建生效）`)
  }
}

/**
 * 采集并上报一次前端运行时错误。
 * @param {Error} err 异常对象
 * @param {string} [info] Vue errorHandler 提供的错误类型信息（如 render / async event handler）
 * @param {string} [component] 出错组件名（取自 instance.$options.name）
 */
export function captureFrontendError(err, info, component) {
  // 控制台输出（保留现状：开发调试与本地排障）
  logger.error('[Global Error]', err)
  logger.error('[Error Info]', info || '')
  logger.error('[Component]', component || 'Unknown')

  if (!endpoint) return

  const payload = {
    message: err instanceof Error ? err.message : String(err),
    stack: err instanceof Error ? err.stack : '',
    info: info || '',
    component: component || 'Unknown',
    url: typeof window !== 'undefined' ? window.location.href : '',
    userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : '',
    timestamp: new Date().toISOString(),
  }

  try {
    const body = JSON.stringify(payload)
    if (typeof navigator !== 'undefined' && typeof navigator.sendBeacon === 'function') {
      navigator.sendBeacon(endpoint, new Blob([body], { type: 'application/json' }))
    } else if (typeof fetch === 'function') {
      fetch(endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body,
        keepalive: true,
      }).catch(() => {
        /* 上报失败静默 */
      })
    }
  } catch {
    /* 上报失败静默，不影响主流程 */
  }
}

export default { initMonitoring, captureFrontendError }
