const FOCUSABLE = [
  'a[href]',
  'button:not([disabled])',
  'textarea:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

let scrollLocks = 0
let savedBodyOverflow = ''
let savedBodyPaddingRight = ''

/**
 * 视口滚动锁按引用计数：多个弹层叠加时，只有最后一个关闭才恢复原值，
 * 否则先关的那个会把背景解锁，后面的弹层又能滚到页面内容。
 */
export function lockViewportScroll(): void {
  const body = document.body
  if (scrollLocks === 0) {
    savedBodyOverflow = body.style.overflow
    savedBodyPaddingRight = body.style.paddingRight
  }
  const gutter = window.innerWidth - document.documentElement.clientWidth
  // 每次都重申 hidden：只要还有一个弹层开着，背景就必须不可滚
  body.style.overflow = 'hidden'
  if (gutter > 0) body.style.paddingRight = `${gutter}px`
  scrollLocks += 1
}

export function unlockViewportScroll(): void {
  if (scrollLocks <= 0) return
  scrollLocks -= 1
  if (scrollLocks === 0) {
    document.body.style.overflow = savedBodyOverflow
    document.body.style.paddingRight = savedBodyPaddingRight
  }
}

export function focusableWithin(container: HTMLElement | null): HTMLElement[] {
  if (!container) return []
  return Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE)).filter(
    (node) => !node.hasAttribute('hidden') && node.getAttribute('aria-hidden') !== 'true',
  )
}

/**
 * 焦点陷阱：Tab / Shift+Tab 只在弹层内循环，并把初始焦点交给首个可聚焦元素。
 * 返回的 restore 用于关闭时把焦点还给打开前的元素。
 */
export function trapFocus(panel: HTMLElement | null): () => void {
  const previous = document.activeElement as HTMLElement | null
  const first = focusableWithin(panel)[0]
  ;(first ?? panel)?.focus()

  function onKeydown(event: KeyboardEvent) {
    if (event.key !== 'Tab' || !panel) return
    const items = focusableWithin(panel)
    if (!items.length) {
      event.preventDefault()
      panel.focus()
      return
    }
    const active = document.activeElement as HTMLElement | null
    const index = active ? items.indexOf(active) : -1
    const next = event.shiftKey
      ? items[(index - 1 + items.length) % items.length]
      : items[(index + 1) % items.length]
    // 焦点已在陷阱内时交给浏览器原生顺序，避免与控件自身按键处理打架
    const inside = active ? panel.contains(active) : false
    if (!inside || index === -1) {
      event.preventDefault()
      next.focus()
    } else if ((event.shiftKey && index === 0) || (!event.shiftKey && index === items.length - 1)) {
      event.preventDefault()
      next.focus()
    }
  }

  document.addEventListener('keydown', onKeydown, true)
  return () => {
    document.removeEventListener('keydown', onKeydown, true)
    previous?.focus?.()
  }
}

let liveRegion: HTMLElement | null = null

/**
 * 全局 aria-live 播报区：读屏需要一处常驻区域，弹层卸载后文本仍在才算播报成功。
 */
export function announce(message: string): void {
  if (!liveRegion || !liveRegion.isConnected) {
    liveRegion = document.createElement('div')
    liveRegion.setAttribute('aria-live', 'polite')
    liveRegion.setAttribute('role', 'status')
    liveRegion.className = 'v2-sr-live'
    Object.assign(liveRegion.style, {
      position: 'absolute',
      width: '1px',
      height: '1px',
      padding: '0',
      margin: '-1px',
      overflow: 'hidden',
      clip: 'rect(0 0 0 0)',
      whiteSpace: 'nowrap',
      border: '0',
    })
  }
  if (!liveRegion.isConnected) document.body.appendChild(liveRegion)
  liveRegion.textContent = ''
  liveRegion.textContent = message
}
