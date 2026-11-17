import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

/**
 * 视觉纪律守卫（2026-11-17，替代原 motionTokens 用例）。
 *
 * 设计取向从「有品牌的装饰」改为「静态即完整」：壳层与内容不做入场动画、不做 hover 位移、
 * 不做渐变条与辉光，标题与读数共用一套字族。这里用源码级断言钉住，避免这些效果在后续批次里
 * 一件件长回来；像素面仍由 anchors 视觉套件负责。
 */
const CSS = readFileSync(join(__dirname, '../styles/record.css'), 'utf8')

/** 从 from 起的第一个 `{` 做花括号配平，返回整段块（含外层花括号）。 */
function balancedBlock(from: number): string {
  const open = CSS.indexOf('{', from)
  expect(open, '锚点之后没有花括号').toBeGreaterThan(-1)
  let depth = 0
  for (let i = open; i < CSS.length; i += 1) {
    if (CSS[i] === '{') depth += 1
    if (CSS[i] === '}') {
      depth -= 1
      if (depth === 0) return CSS.slice(open, i + 1)
    }
  }
  throw new Error('花括号未闭合')
}

describe('视觉纪律：静态即完整', () => {
  it('壳层与内容没有入场动画（.module / .card / .stat / 进度条）', () => {
    for (const name of ['viewIn', 'growBar', 'growUp', 'pulse']) {
      expect(CSS, `${name} 仍在被引用`).not.toContain(`animation: ${name}`)
    }
    const statRule = CSS.slice(
      CSS.indexOf('body[data-app="ui"] .stat {'),
      CSS.indexOf('}', CSS.indexOf('body[data-app="ui"] .stat {')),
    )
    expect(statRule, '.stat 仍有动画').not.toContain('animation')
  })

  it('进度条是实色，不是渐变', () => {
    const barRule = CSS.slice(
      CSS.indexOf('body[data-app="ui"] .course-bar i {'),
      CSS.indexOf('}', CSS.indexOf('body[data-app="ui"] .course-bar i {')),
    )
    expect(barRule, '课程进度条又变成渐变').not.toContain('linear-gradient')
    expect(barRule).toContain('background: var(--accent)')
  })

  it('卡片没有 hover 辉光与位移动效', () => {
    expect(CSS, '品牌辉光令牌被重新引入').not.toContain('--glow-brand')
    expect(CSS).not.toMatch(/\.card\s*\{[^}]*transition/)
    expect(CSS).not.toMatch(/\.btn:hover[^}]*translateY/)
    expect(CSS).not.toMatch(/\.nav-item:hover[^}]*translateX/)
  })

  it('标题与读数共用一套字族（不再另起衬线）', () => {
    expect(CSS).toContain('--font-display: var(--font-body)')
    expect(CSS, 'record.css 里仍有衬线字族').not.toMatch(/Noto Serif|Songti|Georgia,\s*serif/)
  })

  it('存在 prefers-reduced-motion 降级块（过渡与位移可被瞬时化）', () => {
    const at = CSS.indexOf('@media (prefers-reduced-motion: reduce)')
    expect(at, '缺少 prefers-reduced-motion 降级块').toBeGreaterThan(-1)
    const block = balancedBlock(at)
    expect(block).toContain('body[data-app="ui"]')
    expect(block).toMatch(/animation-duration:\s*\.0+1ms\s*!important/)
    expect(block).toMatch(/transition-duration:\s*\.0+1ms\s*!important/)
    expect(block).toMatch(/transform:\s*none\s*!important/)
  })
})
