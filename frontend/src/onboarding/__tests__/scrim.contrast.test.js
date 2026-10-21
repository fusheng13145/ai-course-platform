import { describe, it, expect } from 'vitest'
import fs from 'node:fs'

// 静态字符串的 new URL(..., import.meta.url) 会被 Vite 当资源引用改写成非 file 方案，
// 故与 anchors.bindings.test.js 同法：路径经函数参数传入，保持 file URL。
const read = (p) => fs.readFileSync(new URL(p, import.meta.url), 'utf8')
const css = read('../../styles/theme.css')
const rgb = (s) => {
  const m = /rgba?\(([^)]+)\)/.exec(s)
  return m ? m[1].split(',').map((x) => Number(x.trim())) : null
}
const hex = (s) => {
  const m = /^#([0-9a-f]{6})$/i.exec(s.trim())
  return m ? [0, 2, 4].map((i) => parseInt(m[1].slice(i, i + 2), 16)) : null
}
const varOf = (block, name) => {
  const m = new RegExp(`--${name}:\\s*([^;]+);`).exec(block)
  return m ? m[1] : null
}

describe('暗色 scrim 可见性', () => {
  it('暗色 scrim 压在本体底色上，最大单通道差 ≥ 24/255', () => {
    const darkAt = css.search(/\[data-theme=['"]dark['"]\]/)
    const dark = darkAt >= 0 ? css.slice(darkAt) : ''
    expect(dark).not.toBe('')
    const scrimOf = () => {
      const raw = varOf(dark, 'onboarding-scrim')
      expect(raw, '暗段没读到 --onboarding-scrim').not.toBeNull()
      return rgb(raw)
    }
    const deltaOver = (body) => {
      const scrim = scrimOf()
      const a = scrim[3] ?? 1
      const over = scrim.slice(0, 3).map((c, i) => Math.round(c * a + body[i] * (1 - a)))
      return Math.max(...over.map((c, i) => Math.abs(c - body[i])))
    }
    // 洞的边界永远落在目标元素自己的底色上（surface 卡片或暗色 sidebar 渐变的最深端），
    // 不是页面基底 --bg；拿 --bg 当探针会把"洞外压暗不可见"误判成 scrim 不够黑。
    for (const name of ['surface', 'sidebar-bg-end']) {
      const raw = varOf(dark, name) ?? varOf(css, name)
      const body = raw ? rgb(raw) ?? hex(raw) : null
      expect(body, `底色变量解析失败：--${name}=${raw}`).not.toBeNull()
      const delta = deltaOver(body)
      expect(delta, `--${name} 上合成后只差 ${delta}/255，阈值 24`).toBeGreaterThanOrEqual(24)
    }
  })
})
