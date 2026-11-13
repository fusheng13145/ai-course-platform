import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

/**
 * P5-04 机械落点（新载体版，2026-11-07 视觉 V1 批）：
 * .vue scoped 样式内 border-radius 字面量钉死为豁免清单。
 *
 * 背景：旧端 token-single-source.test.ts 随旧前端下线；新载体样式单层化后
 * `.vue` 字面量已收敛到 10→2（本批），本测试把"只许向紧"的棘轮钉住——
 * 新增字面量必须先落豁免账（docs/前端待办项.md 视觉 V1 批）再改清单。
 *
 * 口径与 scripts/count-hardcoded-style.cjs 一致：段内不含 `var(` 即视为字面量；
 * 注释不计；`inherit` 等关键字与细条（≤3px 装饰性顶角）属书面豁免而非漏网。
 */

const WHITELIST: Array<{ file: string; value: string; reason: string }> = [
  {
    file: 'src/ui/views/specials/AiUsageBody.vue',
    value: 'border-radius: 3px 3px 0 0',
    reason: '按日趋势柱顶角：2px 高细条装饰（P5-05b 第二批"细条"豁免类），无承载语义',
  },
  {
    file: 'src/ui/views/course/tabs/AnalyticsTab.vue',
    value: 'border-radius: inherit',
    reason: 'inherit 是级联关键字不是取值字面量；跟随 .bar-col 母级',
  },
]

function collectVueFiles(dir: string): string[] {
  const out: string[] = []
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    if (statSync(p).isDirectory()) out.push(...collectVueFiles(p))
    else if (name.endsWith('.vue')) out.push(p)
  }
  return out
}

function scopedStyleBlocks(source: string): string {
  const blocks: string[] = []
  const re = /<style[^>]*>([\s\S]*?)<\/style>/g
  let m: RegExpExecArray | null
  while ((m = re.exec(source)) !== null) blocks.push(m[1])
  return blocks.join('\n')
}

function stripComments(css: string): string {
  return css
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .split('\n')
    .map((line) => line.replace(/^\s*\/\/.*$/, ''))
    .join('\n')
}

describe('P5-04 棘轮：.vue scoped 样式不得新增 border-radius 字面量', () => {
  // __dirname = frontend/src/ui/__tests__ → 上溯三级 = frontend/（与豁免清单的 src/ 前缀路径一致）
  const srcDir = join(__dirname, '..', '..', '..')
  const vueFiles = collectVueFiles(srcDir)

  it('扫描面非空（防止目录挪动后测试空转）', () => {
    expect(vueFiles.length).toBeGreaterThan(50)
  })

  it('扫描根确实落在 frontend/（豁免路径以 src/ 开头，根错一层会整表误报）', () => {
    expect(() => statSync(join(srcDir, 'package.json'))).not.toThrow()
  })

  it('border-radius 字面量仅存在于豁免清单内', () => {
    const violations: string[] = []
    for (const file of vueFiles) {
      const rel = relative(srcDir, file).replace(/\\/g, '/')
      const css = stripComments(scopedStyleBlocks(readFileSync(file, 'utf-8')))
      for (const segment of css.split(';')) {
        if (!/border-(?:-?[a-z]+-)?radius\s*:/.test(segment)) continue
        if (segment.includes('var(')) continue
        const value = segment.replace(/^\s*/, '')
        const allowed = WHITELIST.some((w) => w.file === rel && segment.trim().startsWith(w.value))
        if (!allowed) violations.push(`${rel}: ${value}`)
      }
    }
    expect(
      violations,
      `新增字面量需先落豁免账再更新 WHITELIST：\n${violations.join('\n')}`,
    ).toEqual([])
  })

  it('豁免清单中的文件真实存在（防漂移）', () => {
    for (const w of WHITELIST) {
      expect(() => statSync(join(srcDir, w.file)), `${w.file} 不存在，请清理豁免项`).not.toThrow()
    }
  })

  it('悬空 --ac-* 旧设计系统令牌不得回流（2026-11-07 V1 批已清零）', () => {
    const offenders: string[] = []
    for (const file of vueFiles) {
      const rel = relative(srcDir, file).replace(/\\/g, '/')
      const css = scopedStyleBlocks(readFileSync(file, 'utf-8'))
      if (/--ac-(bg|border|radius)/.test(css)) offenders.push(rel)
    }
    expect(
      offenders,
      `--ac-bg/--ac-border/--ac-radius-* 已随旧令牌层删除，勿再引用：${offenders.join(',')}`,
    ).toEqual([])
  })
})
