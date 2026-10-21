/**
 * 纯几何，不碰 DOM。改前 F1／F9 的根在这里：旧实现把气泡一律放"目标下方 + 12px"，
 * 翻转条件写的是 `top > 240`，而满高侧栏 top=0 ⇒ 条件永不成立 ⇒ 气泡被推到 912（视口外）。
 */
export const PLACEMENT_CANDIDATES = ['below', 'above', 'right', 'left', 'bar-bottom']

const fits = (box, cardSize, vp, margin) =>
  box.top >= margin &&
  box.left >= margin &&
  box.top + cardSize.height <= vp.height - margin &&
  box.left + cardSize.width <= vp.width - margin

export function computeCardPlacement({ targetRect: t, cardSize: c, viewport: v, gap = 12, margin = 8 }) {
  const centerX = t.left + t.width / 2 - c.width / 2
  const midY = t.top + Math.min(t.height, v.height) / 2 - c.height / 2
  const candidates = {
    below: { top: t.top + t.height + gap, left: centerX },
    above: { top: t.top - gap - c.height, left: centerX },
    right: { top: midY, left: t.left + t.width + gap },
    left: { top: midY, left: t.left - gap - c.width },
    'bar-bottom': { top: v.height - margin - c.height, left: v.width / 2 - c.width / 2 },
  }
  for (const name of PLACEMENT_CANDIDATES) {
    const box = candidates[name]
    const clamped = { ...box, left: Math.min(Math.max(box.left, margin), v.width - margin - c.width) }
    if (fits(clamped, c, v, margin)) return { top: clamped.top, left: clamped.left, placement: name }
  }
  return null
}

/** 洞外压暗＝四块矩形（上/下/左/右）。洞本身没有元素 ⇒ 点击天然落到真实控件上。
 *  四边界都夹到视口内：目标完全在视口外时，未夹的洞顶会落到 894（视口 800）⇒ 压暗块自己跑出屏外。 */
export function computeScrimRects(t, v, pad = 6) {
  const clamp = (x, lo, hi) => Math.min(hi, Math.max(lo, x))
  const hole = {
    top: clamp(t.top - pad, 0, v.height),
    left: clamp(t.left - pad, 0, v.width),
    right: clamp(t.left + t.width + pad, 0, v.width),
    bottom: clamp(t.top + t.height + pad, 0, v.height),
  }
  const mid = Math.max(0, hole.bottom - hole.top)
  return [
    { top: 0, left: 0, width: v.width, height: hole.top },
    { top: hole.bottom, left: 0, width: v.width, height: Math.max(0, v.height - hole.bottom) },
    { top: hole.top, left: 0, width: hole.left, height: mid },
    { top: hole.top, left: hole.right, width: Math.max(0, v.width - hole.right), height: mid },
  ]
}
