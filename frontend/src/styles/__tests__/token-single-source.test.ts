import { describe, it, expect } from 'vitest'
import { readdirSync, statSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { createRequire } from 'node:module'

/**
 * 单源守卫：卡面描边、圆角阶梯、时长档与已清算别名各钉一条机械断言。
 * 计数口径不在这儿重写——和报告脚本共用同一组判定函数，避免第二套数法。
 */
type AuditPattern = { key: string; label: string; re: RegExp }
type StyleAudit = {
  PATTERNS: AuditPattern[]
  countLiteralDecls: (line: string, re: RegExp) => number
  literalSegments: (text: string, re: RegExp) => string[]
  stripComments: (text: string) => string
}
// .cjs 脚本没有类型声明，按导出的形状双段收窄
const req = createRequire(join(process.cwd(), 'virtual.cjs'))
const audit = req(
  join(process.cwd(), 'scripts', 'count-hardcoded-style.cjs'),
) as unknown as StyleAudit

const reOf = (key: string): RegExp => audit.PATTERNS.find((p) => p.key === key)!.re
const SRC = join(process.cwd(), 'src')

function styleBearing(dir: string, out: { file: string; text: string }[] = []) {
  for (const entry of readdirSync(dir)) {
    if (entry === '__tests__') continue
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) styleBearing(full, out)
    else if (/\.(vue|css|scss)$/.test(entry))
      out.push({ file: full, text: audit.stripComments(readFileSync(full, 'utf8')) })
  }
  return out
}

/** 卡面亮边那一枚色值（含空格差异的两种写法） */
const CARD_RGB = /rgba\(\s*227,\s*230,\s*236\s*,/

/** 圆角只许剩几何圆（等宽高元素的 50%）；5/7/9/18px 已在 P5-05b 归进五阶，棘轮只许向紧 */
const ALLOWED_OFF_SCALE = ['50%']

describe('样式令牌单源', () => {
  it('计数仪器本身判得开字面量与 var()（阴性对照先行）', () => {
    const radius = reOf('radius')
    expect(
      audit.countLiteralDecls('  border-radius: 10px;', radius),
      '假阴性：字面量没被抓到',
    ).toBe(1)
    expect(audit.countLiteralDecls('  border-radius: var(--r-ctl);', radius)).toBe(0)
    expect(audit.countLiteralDecls('  color: var(--ink, #1f2733);', reOf('hex6'))).toBe(0)
    // 注释里的色值不是硬编码：不剥注释的话，越写文档读数越红
    expect(
      audit.literalSegments('/* 改前是 rgba(227, 230, 236, 0.7) */\nborder-radius: 10px;', radius),
      '注释没被剥掉，读数会把解释当债务',
    ).toEqual(['border-radius: 10px'])
  })

  it('卡面亮色描边全站只允许 theme.css 里一枚定义', () => {
    const files = styleBearing(SRC)
    expect(files.length, '扫描面为空，断言会假绿').toBeGreaterThan(200)
    const hits = files
      .map((f) => ({
        file: f.file.replace(/\\/g, '/'),
        lines: f.text.split(/\r?\n/).filter((l) => CARD_RGB.test(l)),
      }))
      .filter((f) => f.lines.length > 0)
    expect(
      hits.map((h) => h.lines.length).reduce((a, b) => a + b, 0),
      '卡面亮边又被人抄回字面量',
    ).toBe(1)
    expect(
      hits[0].file.endsWith('src/styles/theme.css'),
      `卡面亮边不在令牌源：${hits[0].file}`,
    ).toBe(true)
    expect(hits[0].lines[0]).toMatch(/--card-line\s*:/)
  })

  it('design-system.css 的圆角字面量只能是已登记的离阶值', () => {
    const css = readFileSync(join(SRC, 'styles', 'design-system.css'), 'utf8')
    const values = audit
      .literalSegments(css, reOf('radius'))
      .map((seg) => seg.replace(/^border-radius\s*:/, '').trim())
    // 扫描窗口里确实有圆角声明，否则本条会因"读不到"而恒真
    expect(values.length).toBeGreaterThan(0)
    const offenders = values.filter((v) => !ALLOWED_OFF_SCALE.includes(v))
    expect(offenders, `六/八/十/十二/十六/九九九阶又写成字面量：${offenders.join(' , ')}`).toEqual(
      [],
    )
  })

  it('零消费的 --ac-* 别名已清算，不许再被写回来', () => {
    const retired = [
      '--ac-border-color',
      '--ac-shadow-colored',
      '--ac-sidebar-active-bg',
      '--ac-sidebar-active-text',
      '--ac-sidebar-bg',
      '--ac-sidebar-text',
      '--ac-sidebar-text-hover',
      '--ac-text-placeholder',
      '--ac-transition-slow',
    ]
    const files = styleBearing(SRC)
    for (const name of retired) {
      const hits = files.filter((f) => f.text.includes(name)).map((f) => f.file)
      expect(hits, `${name} 又出现了`).toEqual([])
    }
  })
})
