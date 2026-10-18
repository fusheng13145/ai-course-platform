/**
 * 全局读屏播报原语（D2-7）
 * ============================
 * SPA 的路由切换、异步加载完成对读屏软件用户是「无声」的——视觉用户看到
 * 页面变了，读屏用户却停留在旧焦点、旧内容。本原语提供一个全局
 * `aria-live="polite"` 区域，把关键状态变化（页面进入、加载完成等）
 * 写进去，由读屏软件自动朗读。
 *
 * 设计取舍：
 * - **惰性创建 + 单例**：首次 announce 时才建 DOM（`#a11y-announcer`），
 *   避免为可能用不到的功能常驻节点；
 * - **内联视觉隐藏样式**：不依赖全局 CSS 类（clip + 1px 尺寸是业界
 *   sr-only 标准手法），换肤/样式重构不影响播报可用性；
 * - **先清后写**：连续相同内容（如重复进入同一标题页面）必须先置空再
 *   写入，否则 DOM 文本无变化、读屏不触发播报；
 * - **polite**：排队朗读，不打断用户当前操作（alert 级才用 assertive）。
 */

const REGION_ID = 'a11y-announcer'

let region: HTMLElement | null = null

function ensureRegion(): HTMLElement {
  if (region && document.body.contains(region)) return region
  const el = document.createElement('div')
  el.id = REGION_ID
  el.setAttribute('aria-live', 'polite')
  el.setAttribute('aria-atomic', 'true')
  el.setAttribute('role', 'status')
  // 视觉隐藏（sr-only 标准手法）：不占布局、不显示，仅对读屏可用
  el.style.position = 'absolute'
  el.style.width = '1px'
  el.style.height = '1px'
  el.style.margin = '-1px'
  el.style.padding = '0'
  el.style.overflow = 'hidden'
  el.style.clip = 'rect(0 0 0 0)'
  el.style.clipPath = 'inset(50%)'
  el.style.whiteSpace = 'nowrap'
  el.style.border = '0'
  document.body.appendChild(el)
  region = el
  return el
}

/**
 * 向全局 live 区域写入一条播报（读屏软件自动朗读）。
 *
 * @param message 播报文案（空串忽略）
 */
export function announce(message: string): void {
  if (!message) return
  const el = ensureRegion()
  // 先清后写：保证相同文案的重复播报也能触发读屏
  el.textContent = ''
  el.textContent = message
}

/** 测试辅助：重置单例（避免跨用例残留 DOM） */
export function resetAnnouncerForTest(): void {
  if (region) {
    region.remove()
    region = null
  }
}
