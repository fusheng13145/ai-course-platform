/**
 * 全局加载指示（浏览器标签图标动画）
 *
 * 取代原先的两套进度条：路由跳转用的 NProgress 顶部条，与 HTTP 请求用的
 * `#global-loading-bar` 宽度动画。现在不再往页面里塞任何浮层，只把浏览器标签页
 * 的 favicon 换成转圈动画，加载结束后原样还原——视线不会被页面中央的浮层打断。
 *
 * 两条显示规则（都是为了「不闪」）：
 * - 延迟 80ms 出现：80ms 内完成的跳转/请求完全不动图标；
 * - 出现后至少停留 300ms：100ms 级的加载不会一闪而过。
 *
 * favicon 用离屏 canvas 逐帧生成（Chrome/Edge/Firefox 均支持 data URL 换图标）。
 * 若运行环境拿不到 2D 上下文（例如 jsdom / 无 canvas 支持），退化为一张静态 SVG
 * 图标，保证「图标确实变了」这一行为仍然成立，且不抛错。
 */

const FAVICON_ID = 'app-favicon'
/** 图标画布尺寸：64px 足够 16/32px 显示清晰 */
const ICON_SIZE = 64
/** 一圈 12 帧、每帧 90ms ≈ 1.08s 转一圈，比进度条安静得多 */
const FRAME_COUNT = 12
const FRAME_INTERVAL_MS = 90
/** 出现前的延迟：在此窗口内完成的加载不动图标，减少视觉噪声 */
const SHOW_DELAY_MS = 80
/** 出现后的最短停留，避免一闪而过 */
const MIN_VISIBLE_MS = 300
/** 安全兜底：hide 因异常丢失时强制还原图标，避免转圈常驻 */
const SAFETY_MS = 30000

/** 无 canvas 时的静态兜底图标（品牌紫圆环 + 缺口），保证图标仍会切换 */
const FALLBACK_ICON = `data:image/svg+xml,${encodeURIComponent(
  '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">' +
    '<circle cx="16" cy="16" r="11" fill="none" stroke="rgba(126,20,255,.22)" stroke-width="3"/>' +
    '<path d="M16 5a11 11 0 0 1 11 11" fill="none" stroke="#7e14ff" stroke-width="3" stroke-linecap="round"/>' +
    '</svg>',
)}`

let activeCount = 0
let spinning = false
let visibleSince = 0
let showTimer: ReturnType<typeof setTimeout> | null = null
let hideTimer: ReturnType<typeof setTimeout> | null = null
let safetyTimer: ReturnType<typeof setTimeout> | null = null
let frameTimer: ReturnType<typeof setInterval> | null = null
let frameIndex = 0
let originalHref: string | null = null
let canvas: HTMLCanvasElement | null = null

function faviconLink(): HTMLLinkElement | null {
  const byId = document.getElementById(FAVICON_ID)
  if (byId instanceof HTMLLinkElement) return byId
  const fallback = document.querySelector('link[rel~="icon"]')
  return fallback instanceof HTMLLinkElement ? fallback : null
}

/** 取一次 2D 上下文；拿不到就永久退化为静态兜底，不每帧重试 */
function canvasContext(): CanvasRenderingContext2D | null {
  if (canvas) return canvas.getContext('2d')
  try {
    const node = document.createElement('canvas')
    node.width = ICON_SIZE
    node.height = ICON_SIZE
    const ctx = node.getContext('2d')
    if (!ctx) return null
    canvas = node
    return ctx
  } catch {
    return null
  }
}

/** 画一帧：浅色底环 + 品牌紫缺口弧，按 index 旋转 */
function drawFrame(index: number): string | null {
  const ctx = canvasContext()
  if (!ctx) return null
  try {
    const center = ICON_SIZE / 2
    const radius = ICON_SIZE * 0.33
    const lineWidth = ICON_SIZE * 0.11
    ctx.clearRect(0, 0, ICON_SIZE, ICON_SIZE)
    ctx.save()
    ctx.translate(center, center)
    ctx.lineWidth = lineWidth
    ctx.strokeStyle = 'rgba(126, 20, 255, 0.22)'
    ctx.beginPath()
    ctx.arc(0, 0, radius, 0, Math.PI * 2)
    ctx.stroke()

    ctx.strokeStyle = '#7e14ff'
    const start = (index / FRAME_COUNT) * Math.PI * 2 - Math.PI / 2
    ctx.beginPath()
    ctx.arc(0, 0, radius, start, start + Math.PI * 0.62)
    ctx.stroke()
    ctx.restore()
    return canvas!.toDataURL('image/png')
  } catch {
    return null
  }
}

function applyFrame(link: HTMLLinkElement): void {
  const url = drawFrame(frameIndex)
  link.setAttribute('href', url ?? FALLBACK_ICON)
}

function startSpin(): void {
  const link = faviconLink()
  if (!link) return
  originalHref = link.getAttribute('href')
  frameIndex = 0
  applyFrame(link)
  frameTimer = setInterval(() => {
    // 后台标签页不逐帧重绘，省掉无意义的 toDataURL
    if (document.hidden) return
    frameIndex = (frameIndex + 1) % FRAME_COUNT
    applyFrame(link)
  }, FRAME_INTERVAL_MS)
}

function stopSpin(): void {
  if (frameTimer) {
    clearInterval(frameTimer)
    frameTimer = null
  }
  const link = faviconLink()
  if (link && originalHref !== null) link.setAttribute('href', originalHref)
  originalHref = null
}

function clearTimer(timer: ReturnType<typeof setTimeout> | null): null {
  if (timer) clearTimeout(timer)
  return null
}

function reveal(): void {
  if (spinning) return
  spinning = true
  visibleSince = Date.now()
  safetyTimer = clearTimer(safetyTimer)
  safetyTimer = setTimeout(() => forceHide(), SAFETY_MS)
  startSpin()
}

function conceal(): void {
  spinning = false
  safetyTimer = clearTimer(safetyTimer)
  stopSpin()
}

/** 计数归零并立即还原图标（兜底路径；进行中的 hideLoading 随后调用也只会是空操作） */
function forceHide(): void {
  activeCount = 0
  showTimer = clearTimer(showTimer)
  hideTimer = clearTimer(hideTimer)
  conceal()
}

/**
 * 开始一次加载：路由守卫与请求拦截器各自成对调用。
 * 重复调用只增加计数，不会重复启动动画。
 */
function showLoading(): void {
  activeCount++
  if (spinning || showTimer) return
  showTimer = setTimeout(() => {
    showTimer = null
    // 延迟窗口内已经结束（计数归零）就不再动图标
    if (activeCount > 0) reveal()
  }, SHOW_DELAY_MS)
}

/**
 * 结束一次加载。
 * @param force 清零计数并立即还原图标（异常路径使用，不要用于正常配对）
 */
function hideLoading(force = false): void {
  if (force) {
    forceHide()
    return
  }
  activeCount = Math.max(0, activeCount - 1)
  if (activeCount > 0) return

  // 尚未开始转：取消待启动的定时器即可，图标全程不动
  showTimer = clearTimer(showTimer)
  if (!spinning) return

  // 已开始转：补足最短停留时间再还原
  const remaining = Math.max(0, MIN_VISIBLE_MS - (Date.now() - visibleSince))
  hideTimer = clearTimer(hideTimer)
  hideTimer = setTimeout(() => {
    hideTimer = null
    if (activeCount === 0) conceal()
  }, remaining)
}

export { showLoading, hideLoading }
