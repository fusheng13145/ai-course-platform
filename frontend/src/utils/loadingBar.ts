/**
 * 全局 Loading 进度条
 *
 * 从 request.ts 抽离的 UI 关注点：HTTP 传输层不再直接操作 DOM，
 * 仅通过本模块暴露的 showLoading / hideLoading 控制进度条。
 * 进度条 DOM 由入口模板提供（id=global-loading-bar）。
 */

let requestCount = 0
let loadingTimer: ReturnType<typeof setTimeout> | null = null

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
  if (loadingTimer) clearTimeout(loadingTimer)
  loadingTimer = setTimeout(() => hideLoading(true), 30000)
}

function hideLoading(force = false) {
  if (!force) {
    requestCount = Math.max(0, requestCount - 1)
    if (requestCount > 0) return
  } else {
    requestCount = 0
  }
  if (loadingTimer) clearTimeout(loadingTimer)
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

export { showLoading, hideLoading }
