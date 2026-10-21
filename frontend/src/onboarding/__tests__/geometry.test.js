import { describe, it, expect } from 'vitest'
import { PLACEMENT_CANDIDATES, computeCardPlacement, computeScrimRects } from '../geometry'

const VP = (width, height) => ({ width, height })
const CARD = { width: 320, height: 245 }
const visible = (box, vp, margin = 8) =>
  box.top >= margin &&
  box.left >= margin &&
  box.top + CARD.height <= vp.height - margin &&
  box.left + CARD.width <= vp.width - margin

describe('computeCardPlacement', () => {
  it('候选位序至少 4 个（变异检测器：把候选删光必红）', () => {
    expect(PLACEMENT_CANDIDATES.length).toBeGreaterThanOrEqual(4)
  })

  // 正读数：矮目标且下方有空间 ⇒ below
  it('目标够矮、下方有空间时 placement 为 below', () => {
    const got = computeCardPlacement({
      targetRect: { top: 0, left: 600, width: 900, height: 56 },
      cardSize: CARD,
      viewport: VP(1440, 900),
    })
    expect(got.placement).toBe('below')
    expect(got.top).toBeGreaterThan(56)
  })

  // 改前缺陷 F1 的形状：满高侧栏 ⇒ 旧算法把卡片推到 912（视口外）
  it.each([900, 1080, 1440])('视口高 %i 时满高侧栏的卡片仍整卡可见', (h) => {
    const vp = VP(1440, h)
    const got = computeCardPlacement({
      targetRect: { top: 0, left: 0, width: 256, height: h },
      cardSize: CARD,
      viewport: vp,
    })
    expect(got, '无可用候选位（改前就是这一档丢的）').not.toBeNull()
    expect(visible(got, vp), JSON.stringify(got)).toBe(true)
    expect(['right', 'bar-bottom']).toContain(got.placement)
  })

  it('375×667 窄屏仍整卡可见（改前 F9：bottom 923 > 667）', () => {
    const vp = VP(375, 667)
    const got = computeCardPlacement({
      targetRect: { top: 0, left: 0, width: 256, height: 667 },
      cardSize: CARD,
      viewport: vp,
    })
    expect(got).not.toBeNull()
    expect(visible(got, vp), JSON.stringify(got)).toBe(true)
  })

  it('卡片放不下时返回 null（调用方降级为居中且不阻塞点击）', () => {
    expect(
      computeCardPlacement({
        targetRect: { top: 0, left: 0, width: 60, height: 60 },
        cardSize: { width: 900, height: 400 },
        viewport: VP(320, 480),
      }),
    ).toBeNull()
  })
})

describe('computeScrimRects', () => {
  it('目标在中间 ⇒ 四块，面积和＝视口减洞', () => {
    const vp = VP(1000, 800)
    const rects = computeScrimRects({ top: 300, left: 300, width: 200, height: 100 }, vp, 6)
    expect(rects.length).toBe(4)
    const area = rects.reduce((s, r) => s + r.width * r.height, 0)
    expect(area).toBe(1000 * 800 - (200 + 12) * (100 + 12))
  })

  it('目标贴满视口 ⇒ 不留任何有面积的压暗块', () => {
    const rects = computeScrimRects({ top: 0, left: 0, width: 1000, height: 800 }, VP(1000, 800), 6)
    expect(rects.every((r) => r.width <= 0 || r.height <= 0)).toBe(true)
  })

  it('目标完全在视口外 ⇒ 四块仍都在视口内，面积和＝视口', () => {
    const vp = VP(1000, 800)
    for (const t of [
      { top: 900, left: 300, width: 200, height: 100 },
      { top: -200, left: 300, width: 200, height: 100 },
    ]) {
      const rects = computeScrimRects(t, vp, 6)
      for (const r of rects) {
        expect(r.top).toBeGreaterThanOrEqual(0)
        expect(r.left).toBeGreaterThanOrEqual(0)
        expect(r.top + r.height).toBeLessThanOrEqual(vp.height)
        expect(r.left + r.width).toBeLessThanOrEqual(vp.width)
      }
      const area = rects.reduce((s, r) => s + r.width * r.height, 0)
      expect(area).toBe(vp.width * vp.height)
    }
  })
})
