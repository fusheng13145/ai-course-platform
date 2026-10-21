/**
 * 锚点注册表：引导唯一允许用来找元素的地方。
 *
 * 为什么不用 CSS 类名当锚：`.sidebar`／`.topbar` 这类布局类名会被视觉升级批次改，
 * 而改红了没人发现——审计实测旧单测喂的是假 `.sidebar`，真类名改掉零告警。
 * 这里把"锚点键 → 元素"关进一张表：类名漂移只可能红在一处，且有集合相等断言守着。
 */
const NAV_PREFIX = 'nav:'

// jsdom 没有 CSS 全局（typeof window.CSS === 'undefined'），不能用 CSS.escape。
// 属性值已由双引号包裹，只需转义反斜杠与双引号。
const esc = (v) => String(v).replace(/["\\]/g, '\\$&')

/** 裸键 → 选择器。新增裸键必须同时被某一步用到，否则 anchors.test.js 红。 */
const BARE = {
  'side-user': '[data-onboard="side-user"]',
  'join-course': '[data-onboard="join-course"]',
}

export const ALL_ANCHOR_KEYS = Object.keys(BARE)

export function isRegisteredAnchor(key) {
  if (typeof key !== 'string') return false
  return key.startsWith(NAV_PREFIX) || Object.prototype.hasOwnProperty.call(BARE, key)
}

export function resolveAnchor(key, root = document) {
  if (!isRegisteredAnchor(key)) {
    throw new Error(`未注册锚点：${String(key)}（加进 BARE 或改 steps，不要静默返回 null）`)
  }
  const selector = key.startsWith(NAV_PREFIX) ? `[data-onboard="${esc(key)}"]` : BARE[key]
  return root.querySelector(selector)
}
