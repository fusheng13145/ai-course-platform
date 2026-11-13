import { describe, expect, it } from 'vitest'
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

/**
 * e2e 登录口令的单点来源守卫（2026-11-06 验收批）。
 *
 * 背景：口令原先以字面量散在 `loginAs()` 与 `core.spec.ts` 两处，库里口令一旦不等于
 * 123456（例如曾以 DEMO_ACCOUNTS_ENABLED=false 在本机库启动过，admin 被单向改密），
 * 整轮只表现为 admin 用例随机红，读不出根因。现在唯一来源是 `DEMO_PASS`，本守卫钉住
 * 「不许再出现第二份字面量」。
 */

/** 命中形态：往密码控件里填一个字符串字面量（而不是变量）。 */
const HARDCODED_PASSWORD = /input\[name=["']?password["']?\]?\s*['"]?\s*,\s*(['"][^'"]+['"])/

function scan(dir: string): string[] {
  const offenders: string[] = []
  const files = readdirSync(dir, { withFileTypes: true })
    .filter((e) => e.isFile() && e.name.endsWith('.ts'))
    .map((e) => join(dir, e.name))
  for (const p of files) {
    readFileSync(p, 'utf-8')
      .split('\n')
      .forEach((line, i) => {
        if (HARDCODED_PASSWORD.test(line)) offenders.push(`${p}:${i + 1}`)
      })
  }
  return offenders
}

describe('e2e 登录口令单点来源', () => {
  it('检测器能抓住人造违规（阴性对照，防「扫的是空气」的恒真假绿）', () => {
    expect(HARDCODED_PASSWORD.test(`await page.fill('input[name=password]', '123456')`)).toBe(true)
    expect(HARDCODED_PASSWORD.test(`await page.fill('input[name=password]', PASS)`)).toBe(false)
  })

  it('e2e/ 下没有任何一处把口令写成字面量', () => {
    expect(scan('e2e')).toEqual([])
  })

  it('test-helpers.ts 导出的 DEMO_PASS 仍只有一处定义', () => {
    const src = readFileSync(join('e2e', 'test-helpers.ts'), 'utf-8')
    const defs = src.split('\n').filter((line) => /^export const DEMO_PASS\s*=/.test(line))
    expect(defs).toHaveLength(1)
    expect(src).toMatch(/process\.env\.PW_PASS/)
  })
})
