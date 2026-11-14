import { describe, expect, it } from 'vitest'
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'
import { PAGE_DEFS, type Role } from '../nav'
import { pageDisposition } from '../notProvided'

/**
 * 示例层不得复活（batch 24 死示例层清算的守卫）。
 *
 * 此前的前端有一整套「示例读数」底座：`pageData.ts`（791 行）、`courseTabData.ts`、
 * `modules.ts`＋`demo.json`（72 KB）、两个示例面板，以及 `PageHead` 上的
 * 「示例数据／接口 + 示例」来源章。它的问题是：读数真假只能靠人眼分辨，
 * 演示与验收都会被问穿；而真实组件接管后，这些代码大部分已经不可达。
 *
 * 本守卫把「不可达」变成「不可再引入」：只要有人把示例块、示例面板或
 * 示例数据文件接回来，这条用例就红。
 */
const UI_DIR = join(__dirname, '..')

/** 全局页里用显式组件（不走 special 约定）的页面 */
const EXPLICIT_BODY_PAGES = ['messages', 'examGovernance', 'analytics']

/** 示例层留下的符号与文件 */
const FORBIDDEN_SYMBOLS: Array<{ pattern: RegExp; label: string }> = [
  { pattern: /\bdemoBlock\b/, label: 'demoBlock（全局页示例块入口）' },
  { pattern: /\bcourseTabBlock\b/, label: 'courseTabBlock（课程页签示例块入口）' },
  { pattern: /\bfindModule\b/, label: 'findModule（示例模块注册表）' },
  { pattern: /data\/pageData/, label: 'data/pageData 导入' },
  { pattern: /data\/courseTabData/, label: 'data/courseTabData 导入' },
  { pattern: /data\/modules/, label: 'data/modules 导入' },
  { pattern: /data\/demoSource/, label: 'data/demoSource 导入' },
  { pattern: /\bModuleTab\b/, label: '示例面板 ModuleTab' },
  { pattern: /\bExamTab\b/, label: '示例面板 ExamTab' },
  { pattern: /v-else-if="block"/, label: '示例块兜底分支' },
]

function walk(dir: string): string[] {
  const out: string[] = []
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) {
      out.push(...walk(full))
      continue
    }
    if (/\.(ts|vue|json)$/.test(name) && !name.endsWith('.test.ts')) out.push(full)
  }
  return out
}

/** 扫描示例层痕迹；返回命中的文件清单（阴性对照用同一函数） */
export function scanDemoLayer(files: Array<{ path: string; text: string }>): string[] {
  const hits: string[] = []
  for (const file of files) {
    for (const { pattern, label } of FORBIDDEN_SYMBOLS) {
      if (pattern.test(file.text)) hits.push(`${file.path}: ${label}`)
    }
  }
  return hits
}

describe('示例层不得复活', () => {
  it('源码里不再出现示例块、示例面板或示例数据底座的任何引用', () => {
    const files = walk(UI_DIR).map((path) => ({ path, text: readFileSync(path, 'utf8') }))
    const hits = scanDemoLayer(files)
    expect(hits, `示例层被重新引入：\n${hits.join('\n')}`).toEqual([])
  })

  it('阴性对照：把示例块接回来时，这条守卫必须判红', () => {
    const clean = [{ path: 'a.ts', text: 'const x = 1\n' }]
    expect(scanDemoLayer(clean)).toEqual([])
    expect(scanDemoLayer([{ path: 'b.ts', text: 'demoBlock(role, page)' }]).length).toBe(1)
    expect(scanDemoLayer([{ path: 'c.vue', text: '<template v-else-if="block">' }]).length).toBe(1)
  })

  it('示例数据底座文件已从磁盘移除', () => {
    for (const name of [
      'demo.json',
      'pageData.ts',
      'courseTabData.ts',
      'modules.ts',
      'demoSource.ts',
    ]) {
      expect(existsSync(join(UI_DIR, 'data', name)), `data/${name} 仍在`).toBe(false)
    }
  })

  it('页头不再有英文 kicker 与「接口数据」来源章（展示层清理批 A）', () => {
    const pageHead = readFileSync(join(UI_DIR, 'components', 'PageHead.vue'), 'utf8')
    // 只看模板段：注释里提到这些名字是允许的，渲染出来才算违规
    const template = pageHead.slice(0, pageHead.indexOf('<script'))
    expect(pageHead, 'PageHead 仍带「示例数据」来源章').not.toContain('示例数据')
    expect(pageHead, 'PageHead 仍带「接口 + 示例」来源章').not.toContain('接口 + 示例')
    expect(template, '页头仍在渲染来源章').not.toContain('接口数据')
    expect(template, '页头仍在渲染 kicker').not.toContain('page-kicker')
    expect(pageHead, 'PageHead 仍声明 kicker/source prop').not.toMatch(
      /kicker\s*[?:]|source\s*[?:]/,
    )
  })

  it('每个「已提供」的全局页都有正文组件，不靠兜底分支撑住', () => {
    // 只有走 GlobalPage（router.ts 里的 page(...) 调用）的页面才需要 special 或显式分支；
    // 首页、课程列表、题库、试卷库等用的是独立路由组件，不在此列。
    const router = readFileSync(join(UI_DIR, 'router.ts'), 'utf8')
    const globalPages = [...router.matchAll(/page\('([a-z]+)\/([a-zA-Z]+)'/g)].map((match) => ({
      role: match[1] as Role,
      page: match[2],
    }))
    expect(globalPages.length, '未从 router.ts 解析到任何 GlobalPage 页面').toBeGreaterThan(5)

    const offenders: string[] = []
    for (const { role, page } of globalPages) {
      if (pageDisposition(role, page) !== 'provided') continue
      const def = PAGE_DEFS[role]?.[page]
      if (!def) continue
      if (!def.special && !EXPLICIT_BODY_PAGES.includes(page)) offenders.push(`${role}/${page}`)
    }
    expect(offenders, `这些页面没有正文组件，会落到兜底空态：${offenders.join('、')}`).toEqual([])
  })
})
