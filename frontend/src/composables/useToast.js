/* Toast composable for Vue - pure JS module */

let container = null
let toasts = []
let toastIdx = 0

const ICON_SVG = {
  ok: '<svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="#5fd191" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M20 6 9 17l-5-5"/></svg>',
  err: '<svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="#ff8f84" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0zM12 9v4M12 17h.01"/></svg>',
  info: '<svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="#7db3e8" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"><path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.94 1.94 0 0 0 3.4 0"/></svg>',
}

function ensureContainer() {
  if (container) return
  container = document.createElement('div')
  container.className = 'toast-stack'
  document.body.appendChild(container)
}

/** HTML 转义：toast 消息按纯文本渲染，防止服务端消息注入脚本 */
function escapeHtml(s) {
  return String(s ?? '').replace(
    /[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c],
  )
}

function addToast(msg, type, ms) {
  ensureContainer()
  const id = ++toastIdx
  const el = document.createElement('div')
  el.className = `toast ${type}`
  el.innerHTML = (ICON_SVG[type] || ICON_SVG.info) + escapeHtml(msg)
  container.appendChild(el)
  toasts.push({ id, el })
  setTimeout(() => {
    el.remove()
    toasts = toasts.filter(t => t.id !== id)
  }, ms || 2600)
}

export const toast = { show: addToast }

export function useToast() {
  return { show: addToast }
}
