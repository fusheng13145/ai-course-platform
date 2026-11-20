import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join } from 'node:path'

/**
 * 界面文案纪律守卫（2026-11-17）。
 *
 * 设计法只有一句：**页面靠布局自解释，不靠解释性文字**。
 *  1. 一页 = 标题 + 动作 + 内容；没有 kicker、副标题、卡内提示段。
 *  2. 文案只承载动作标签、数据、以及改不了的事实（不可撤回/仅查询）。
 *  3. 空态一行，错误态一行 + 重试。
 *
 * 这里钉住的是「解释层」这一类实现：它们一被加回来，页面就会重新长出注释式段落，
 * 而这类段落只能靠人眼判断真假。数据字段（note/meta/warning）不在此列。
 */
const UI_DIR = join(__dirname, '..')

/** 退役的「解释层」类名：渲染出来即违规 */
const RETIRED_CLASSES = [
  'page-kicker',
  'page-sub',
  'card-kicker',
  'card-sub',
  'card-hint',
  'ai-caveat',
  'eyebrow',
  'lede',
  'av-story',
  'av-feat',
  'av-motto',
  'av-note',
  'av-context',
  'practice-subtitle',
  'toc-note',
  'kg-guide',
]

/** 曾写死在侧栏的机构 / 学期假数据：任何源码文件都不得再出现 */
const FORBIDDEN_LITERALS = ['校级治理', '教师空间 · 2026 秋', '学生空间 · 2026 秋']

function walk(dir: string, allow: RegExp): string[] {
  const out: string[] = []
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    if (statSync(full).isDirectory()) {
      out.push(...walk(full, allow))
      continue
    }
    if (allow.test(name) && !name.endsWith('.test.ts')) out.push(full)
  }
  return out
}

/** 只取模板段：script 里的注释与类型名不算「渲染」 */
function templateOf(vue: string): string {
  const cut = vue.indexOf('<script')
  return cut === -1 ? vue : vue.slice(0, cut)
}

/** 扫描解释层类名与假数据；阴性对照用同一函数 */
export function scanCopyLaw(files: Array<{ path: string; template: string }>): string[] {
  const hits: string[] = []
  for (const file of files) {
    for (const name of RETIRED_CLASSES) {
      const pattern = new RegExp(`class="[^"]*\\b${name}\\b`)
      if (pattern.test(file.template)) hits.push(`${file.path}: ${name}`)
    }
    for (const literal of FORBIDDEN_LITERALS) {
      if (file.template.includes(literal)) hits.push(`${file.path}: ${literal}`)
    }
  }
  return hits
}

describe('界面文案纪律：布局自解释，不加解释层', () => {
  it('模板里不再出现 kicker / 副标题 / 卡内提示段等解释层类名', () => {
    const files = walk(UI_DIR, /\.vue$/).map((path) => ({
      path,
      template: templateOf(readFileSync(path, 'utf8')),
    }))
    expect(files.length, '未扫到任何 .vue 模板').toBeGreaterThan(20)
    const hits = scanCopyLaw(files)
    expect(hits, `解释层类名被重新引入：\n${hits.join('\n')}`).toEqual([])
  })

  it('阴性对照：塞回一个副标题或假机构名时，这条守卫必须判红', () => {
    const clean = [{ path: 'a.vue', template: '<h1>课程</h1>\n' }]
    expect(scanCopyLaw(clean)).toEqual([])
    expect(scanCopyLaw([{ path: 'b.vue', template: '<p class="page-sub">说明</p>' }])).toHaveLength(
      1,
    )
    expect(scanCopyLaw([{ path: 'c.vue', template: '<b>校级治理</b>' }])).toHaveLength(1)
  })

  it('PageHead 只有 title 一个输入，没有副标题 / kicker 参数', () => {
    const pageHead = readFileSync(join(UI_DIR, 'components', 'PageHead.vue'), 'utf8')
    const props = pageHead.match(/defineProps<[\s\S]*?>\(\)/)
    expect(props, 'PageHead 没有定义 props').toBeTruthy()
    expect(props?.[0]).toContain('title: string')
    expect(props?.[0], 'PageHead 又长出了副标题参数').not.toMatch(/subtitle|kicker|desc/)
  })

  it('页面定义不再携带从未渲染的描述字段', () => {
    const nav = readFileSync(join(UI_DIR, 'nav.ts'), 'utf8')
    expect(nav, 'PageDef 又加回了 desc').not.toMatch(/\bdesc\b/)
  })
})
