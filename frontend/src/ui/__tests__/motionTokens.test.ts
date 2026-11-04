import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

/**
 * P6-06 / P6-07 源码级守卫（先例：specialRegistry.test.ts）。
 *
 * 钉住三件事：
 * ① 三枚令牌定义在**活的** record 皮肤层（body[data-style="record"]），不是文件顶部随旧端留下的死块；
 * ② 每枚令牌至少有一个 var() 消费点——「token 生效」必须给出消费点；
 * ③ prefers-reduced-motion 降级块存在，且覆盖 的动画/过渡与新增位移。
 * 只读源码，不跑浏览器：像素面由 anchors 的视觉套件负责。
 */
const CSS = readFileSync(join(__dirname, '../styles/record.css'), 'utf8')
const TOKENS = ['--ease-spring', '--t-xslow', '--glow-brand'] as const

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

const skinAt = CSS.indexOf('body[data-style="record"] {')
const skinBlock = balancedBlock(skinAt)

describe('运动令牌与降级守卫', () => {
  it('三枚令牌定义在 data-style="record" 皮肤块内，且全文件只有这一处定义', () => {
    expect(skinAt, '找不到活的 record 皮肤块').toBeGreaterThan(-1)
    for (const token of TOKENS) {
      expect(skinBlock, `${token} 未定义在 record 皮肤块`).toContain(`${token}:`)
      // 死块（文件顶部旧令牌层）不得承载这三枚；重复定义也会让这里的计数大于 1
      const definitions = CSS.match(new RegExp(`${token}\\s*:`, 'g')) ?? []
      expect(definitions.length, `${token} 定义处数`).toBe(1)
    }
  })

  it('三枚令牌各至少有一个 var() 消费点', () => {
    for (const token of TOKENS) {
      const uses = CSS.match(new RegExp(`var\\(${token}\\b`, 'g')) ?? []
      expect(uses.length, `${token} 没有消费点`).toBeGreaterThan(0)
    }
  })

  it('文件顶部的旧令牌层没有被写入新令牌', () => {
    const topTokenBlock = balancedBlock(CSS.indexOf('body[data-app="ui"] {'))
    for (const token of TOKENS) {
      expect(topTokenBlock, `${token} 误写入文件顶部死块`).not.toContain(token)
    }
  })

  it('存在 prefers-reduced-motion 降级块，覆盖 的动画、过渡与位移', () => {
    const at = CSS.indexOf('@media (prefers-reduced-motion: reduce)')
    expect(at, '缺少 prefers-reduced-motion 降级块').toBeGreaterThan(-1)
    const block = balancedBlock(at)
    expect(block).toContain('body[data-app="ui"]')
    // 现有降级用的是 .001ms（比要求的 .01ms 更紧）；两者都算「瞬时」
    expect(block).toMatch(/animation-duration:\s*\.0+1ms\s*!important/)
    expect(block).toMatch(/transition-duration:\s*\.0+1ms\s*!important/)
    expect(block).toMatch(/transform:\s*none\s*!important/)
  })
})
