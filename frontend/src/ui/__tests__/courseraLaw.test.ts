import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const CSS = readFileSync(join(__dirname, '../styles/coursera.css'), 'utf8')
const DESIGN_LAYER = CSS.split('/* 冻结区：')[0]

const ALLOWED_HEX = new Set([
  '#f7f8fa',
  '#ffffff',
  '#1d2129',
  '#667085',
  '#d9dee4',
  '#2b7de9',
  '#7c5cff',
  '#219653',
  '#b54708',
  '#eef1f4',
])

export function unauthorizedHex(css: string): string[] {
  const values = css.match(/#[0-9a-fA-F]{6}\b/g) ?? []
  return [...new Set(values.filter((value) => !ALLOWED_HEX.has(value.toLowerCase())))]
}

describe('Coursera 视觉规范', () => {
  it('设计层只使用批准的色板，并保留阴性对照', () => {
    expect(DESIGN_LAYER.length, '未读到 Coursera 设计层').toBeGreaterThan(1000)
    expect(unauthorizedHex(DESIGN_LAYER)).toEqual([])
    expect(unauthorizedHex('.x { color: #123456; }')).toEqual(['#123456'])
  })

  it('核心令牌精确落在指定色值、圆角与字族', () => {
    for (const token of [
      '--bg: #f7f8fa',
      '--surface: #ffffff',
      '--ink: #1d2129',
      '--ink-3: #667085',
      '--line: #d9dee4',
      '--accent: #2b7de9',
      '--violet: #7c5cff',
      '--ok: #219653',
      '--warn: #b54708',
      '--r-ctl: 4px',
      '--r-card: 6px',
      "--font-body: 'Inter'",
    ]) {
      expect(CSS).toContain(token)
    }
  })

  it('非课程卡片阴影使用指定读数，装饰无渐变与模糊', () => {
    expect(CSS).toContain('0 1px 2px rgba(17, 24, 39, 0.04)')
    expect(CSS).toContain('0 1px 3px rgba(17, 24, 39, 0.06)')
    expect(CSS).toContain('0 4px 12px rgba(43, 125, 233, 0.08)')
    expect(DESIGN_LAYER).not.toMatch(/(?:linear|radial)-gradient/)
    expect(DESIGN_LAYER).not.toMatch(/backdrop-filter:(?!\s*none[;\s])/)
  })

  it('课程卡片冻结令牌与原色系保持隔离', () => {
    const frozen = CSS.slice(
      CSS.indexOf('/* 冻结区：'),
      CSS.indexOf("body[data-app='ui'] .page-title"),
    ).replace(/\s+/g, ' ')
    expect(frozen).toContain("body[data-style='record'][data-theme='light'] .cf {")
    expect(frozen).toContain('--accent: #4f46e5')
    expect(frozen).toContain('--r-card: 8px')
    expect(frozen).toContain('--tone-1: #dbeafe')
    expect(frozen).toContain("--font-body: 'Geist'")
  })

  it('高密度布局收敛卡片内边距，并让工作台左右列纵向互补', () => {
    expect(CSS).toContain("body[data-app='ui'] .card {\n  padding: 16px;\n}")
    expect(CSS).toContain("body[data-app='ui'] .stat-grid {\n  margin-bottom: 16px;\n}")
    expect(CSS).toContain("body[data-app='ui'] .course-head-meta")
    expect(CSS).toMatch(/\.card-courses\s*\{\s*grid-column: 1 \/ span 7;\s*grid-row: 1 \/ span 2;/)
    expect(CSS).toMatch(/\.card-chart\s*\{\s*grid-column: 8 \/ -1;\s*grid-row: 2;/)
  })
})
