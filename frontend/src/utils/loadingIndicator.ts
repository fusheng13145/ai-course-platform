/**
 * 全局加载指示器（转圈）
 *
 * 取代原先的两套进度条：路由跳转用的 NProgress 顶部条，与 HTTP 请求用的
 * `#global-loading-bar` 宽度动画。现在路由与请求共用同一个计数，只有真正在等时
 * 才会出现一个转圈指示器，避免每次点击都有一条横条掠过。
 *
 * 两条显示规则（都是为了「不闪」）：
 * - 延迟 80ms 出现：80ms 内完成的跳转/请求完全不显示；
 * - 出现后至少停留 300ms：100ms 级的加载不会一闪而过。
 *
 * DOM 由 index.html 提供（id=global-loading-indicator），本模块不创建节点，
 * 与旧的 loadingBar 一样只负责状态与显隐，HTTP 层不直接操作 DOM。
 */

const INDICATOR_ID = 'global-loading-indicator'
/** 出现前的延迟：在此窗口内完成的加载不显示，减少视觉噪声 */
const SHOW_DELAY_MS = 80
/** 出现后的最短停留，避免一闪而过 */
const MIN_VISIBLE_MS = 300
/** 安全兜底：hide 因异常丢失时强制收起，避免转圈常驻 */
const SAFETY_MS = 30000

let activeCount = 0
let visibleSince = 0
let showTimer: ReturnType<typeof setTimeout> | null = null
let hideTimer: ReturnType<typeof setTimeout> | null = null
let safetyTimer: ReturnType<typeof setTimeout> | null = null

function indicator(): HTMLElement | null {
  return document.getElementById(INDICATOR_ID)
}

function isVisible(): boolean {
  return indicator()?.classList.contains('is-visible') ?? false
}

function clearTimer(timer: ReturnType<typeof setTimeout> | null): null {
  if (timer) clearTimeout(timer)
  return null
}

function reveal(): void {
  const node = indicator()
  if (!node || node.classList.contains('is-visible')) return
  node.classList.add('is-visible')
  visibleSince = Date.now()
  safetyTimer = clearTimer(safetyTimer)
  safetyTimer = setTimeout(() => forceHide(), SAFETY_MS)
}

function conceal(): void {
  indicator()?.classList.remove('is-visible')
  safetyTimer = clearTimer(safetyTimer)
}

/** 计数归零并立即收起（兜底路径；进行中的 hideLoading 随后调用也只会是空操作） */
function forceHide(): void {
  activeCount = 0
  showTimer = clearTimer(showTimer)
  hideTimer = clearTimer(hideTimer)
  conceal()
}

/**
 * 开始一次加载：路由守卫与请求拦截器各自成对调用。
 * 重复调用只增加计数，不会重复显示。
 */
function showLoading(): void {
  activeCount++
  if (isVisible() || showTimer) return
  showTimer = setTimeout(() => {
    showTimer = null
    // 延迟窗口内已经结束（计数归零）就不再弹出
    if (activeCount > 0) reveal()
  }, SHOW_DELAY_MS)
}

/**
 * 结束一次加载。
 * @param force 清零计数并立即收起（异常路径使用，不要用于正常配对）
 */
function hideLoading(force = false): void {
  if (force) {
    forceHide()
    return
  }
  activeCount = Math.max(0, activeCount - 1)
  if (activeCount > 0) return

  // 尚未显示：取消待显示的定时器即可，一次都不闪
  showTimer = clearTimer(showTimer)
  if (!isVisible()) return

  // 已显示：补足最短停留时间再收起
  const remaining = Math.max(0, MIN_VISIBLE_MS - (Date.now() - visibleSince))
  hideTimer = clearTimer(hideTimer)
  hideTimer = setTimeout(() => {
    hideTimer = null
    if (activeCount === 0) conceal()
  }, remaining)
}

export { showLoading, hideLoading }
