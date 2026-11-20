import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const CSS = readFileSync(join(__dirname, '../styles/coursera.css'), 'utf8')
const DESIGN_LAYER = CSS.split('/* 冻结区：')[0]
const AUTH_SHELL = readFileSync(join(__dirname, '../views/auth/AuthShell.vue'), 'utf8')
const AUTH_ASIDE = readFileSync(join(__dirname, '../views/auth/AuthAside.vue'), 'utf8')

const ALLOWED_HEX = new Set([
  '#f7f8fa',
  '#ffffff',
  '#1d2129',
  '#5e6978',
  '#667085',
  '#d9dee4',
  '#1a6ad8',
  '#155fc6',
  '#5b45c4',
  '#16784f',
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
      '--ink-3: #5e6978',
      '--line: #d9dee4',
      '--accent: #1a6ad8',
      '--violet: #5b45c4',
      '--ok: #16784f',
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

  it('认证页用表单 + 平台结构双栏填满容器，并在窄屏收回单列', () => {
    expect(AUTH_SHELL).toContain("import AuthAside from './AuthAside.vue'")
    expect(AUTH_SHELL).toContain('<AuthAside />')
    expect(AUTH_ASIDE).toContain('NumberBadge')
    expect(AUTH_ASIDE.match(/title: '/g)).toHaveLength(4)
    expect(AUTH_ASIDE.match(/name: '/g)).toHaveLength(3)
    expect(CSS).toContain('grid-template-columns: minmax(0, 520px) minmax(0, 1fr);')
    expect(CSS).toContain('grid-template-columns: minmax(0, 640px) minmax(0, 1fr);')
    expect(CSS).toMatch(
      /@media \(max-width: 980px\)[\s\S]*max-width: 560px;[\s\S]*max-width: 620px;/,
    )
    expect(CSS).not.toContain('av-context')
  })

  it('认证页布局覆盖带 .auth 作用域，不看 CSS 注入顺序', () => {
    // 反例背景（2026-11-20）：从 /admin/courses 退出登录时，coursera.css 先于 auth.css 注入，
    // 同权重的 .av-card 被 auth.css 的 420px/纵向 stretch 反压，登录卡片拉成 1600px 高。
    for (const selector of [
      '.auth .av-main {',
      '.auth .av-card,',
      '.auth .av-card-wide {',
      '.auth .av-panel {',
      '.auth .av-form {',
      '.auth .av-submit {',
    ]) {
      expect(CSS, `${selector} 缺少 .auth 作用域`).toContain(selector)
    }
    expect(CSS, '仍存在未加 .auth 的 .av-main 规则').not.toMatch(/\n\.av-main\s*\{/)
    expect(CSS, '仍存在未加 .auth 的 .av-card 规则').not.toMatch(/\n\.av-card\s*\{/)
    expect(CSS, '仍存在未加 .auth 的 .av-panel 规则').not.toMatch(/\n\.av-panel\s*\{/)
    // 阴性对照：上面的正则确实能抓住旧形态，而不是恒假。
    expect(/\n\.av-main\s*\{/.test('\n.av-main {')).toBe(true)
    expect(/\n\.av-card\s*\{/.test('\n.av-card {')).toBe(true)
  })
})
