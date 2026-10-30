import { describe, expect, it } from 'vitest'
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

/**
 * XSS 面守卫（P-12 处置清单 F-1／F-2／F-4）。
 *
 * 这份守卫走的是本仓既有的套路（见同目录 noElementPlus.test.ts）：
 * **扫描面非空前置 ＋ 全量判定 ＋ 阴性对照（植入违例必须被抓红）**，
 * 因为这三条判据都是"某件事不许再发生"，没有机械断言就只是口头承诺。
 *
 * 钉住的四件事：
 * 1. `v-html` 位点清单守恒，且每个位点必须走 `utils/sanitize` 唯一入口、不得自带 DOMPurify；
 * 2. 宽 profile `USE_PROFILES` 全仓归零（那是处置前 ReaderTab 的漏洞本体）；
 * 3. `decodeAiDisplayText`（文本解码器，非净化器）的载体文件里不得出现 `v-html`；
 * 4. `href="javascript:…"` 归零（F-4），失效符号 `decodeHtml`／`renderMarkdown` 不得复活（F-2）。
 */
const srcRoot = join(__dirname, '../..')

function sourceFiles(dir: string): string[] {
  const out: string[] = []
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      if (entry === '__tests__' || entry === 'node_modules') continue
      out.push(...sourceFiles(full))
    } else if (/\.(vue|ts)$/.test(entry) && !entry.endsWith('.d.ts')) {
      out.push(full)
    }
  }
  return out
}

/**
 * 注释里的 v-html／DOMPurify 是常态（每个位点上方都挂着 eslint-disable 说明），
 * 不剥干净判据就会被说明文字误触——HTML 注释、块注释、行注释三类都剥。
 */
const stripComments = (source: string) =>
  source
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '')

/** 真位点＝模板里出现了 v-html 绑定，不是注释里提到这个词 */
const hasVHtml = (source: string) => /v-html\s*=/.test(stripComments(source))

const rel = (file: string) => relative(srcRoot, file).replace(/\\/g, '/')

/** 判据 1：一个 v-html 位点是否走唯一入口 */
function inspectVHtmlSite(raw: string): string[] {
  const source = stripComments(raw)
  if (!hasVHtml(source)) return []
  const out: string[] = []
  if (/from\s+'dompurify'/i.test(source))
    out.push('位点自带 DOMPurify 配置（白名单必须只在 utils/sanitize 定义）')
  if (!/from\s+'@\/utils\/sanitize'/.test(source)) out.push('位点未引用 utils/sanitize 唯一入口')
  return out
}

/** 判据 3：AI 文本解码器与 v-html 不得同处一个文件 */
function inspectDecoderFile(raw: string): string[] {
  const source = stripComments(raw)
  if (!/decodeAiDisplayText/.test(source)) return []
  return hasVHtml(source) ? ['decodeAiDisplayText 的返回值落进了 v-html 位点'] : []
}

/** 判据 4：javascript: 伪协议写在 href 上 */
const javascriptHref = (raw: string) => /href\s*=\s*"?\s*javascript:/i.test(stripComments(raw))

/** 处置前的三个位点；新增 v-html 位点必须先在这里登记并说明净化档位，否则本条判红 */
const KNOWN_V_HTML_SITES = [
  'v2/components/V2Table.vue',
  'v2/views/course/tabs/ReaderTab.vue',
  'v2/views/legal/V2LegalView.vue',
]

/** decodeAiDisplayText 的消费面（当前只有课程内 AI 那一路）；扩消费方要连同判据一起改 */
const KNOWN_DECODER_FILES = ['v2/views/course/tabs/AiCenterTab.vue']

/** 定义文件本身不算消费方（它是 `export function`，不是"把返回值接进渲染"的那一方） */
const DECLARATION_FILES = ['utils/response.ts']

const files = sourceFiles(srcRoot)

describe('xss 面守卫 · 阴性对照', () => {
  it('检测器必须抓住植入违例，同时放过合规写法', () => {
    // 植入：自带 DOMPurify 且没有唯一入口
    expect(
      inspectVHtmlSite(
        '<template><div v-html="x"></div></template>\nimport DOMPurify from \'dompurify\'',
      ),
    ).toEqual([
      '位点自带 DOMPurify 配置（白名单必须只在 utils/sanitize 定义）',
      '位点未引用 utils/sanitize 唯一入口',
    ])
    // 植入：只缺唯一入口
    expect(
      inspectVHtmlSite(
        '<template><div v-html="x"></div></template>\nimport { sanitizeHtml } from \'@/utils/sanitize\'',
      ),
    ).toEqual([])
    // 植入：解码器接进 v-html
    expect(
      inspectDecoderFile(
        '<template><div v-html="answer"></div></template>\nimport { decodeAiDisplayText } from \'@/utils/response\'',
      ),
    ).toEqual(['decodeAiDisplayText 的返回值落进了 v-html 位点'])
    // 植入：javascript: 伪协议
    expect(javascriptHref('<template><a href="javascript:void(0)">x</a></template>')).toBe(true)
    expect(javascriptHref('<template><button type="button">x</button></template>')).toBe(false)
    // 反向阴性对照：只在注释里提到 v-html 的文件不许被算成位点
    expect(
      hasVHtml(
        '<!-- eslint-disable-next-line vue/no-v-html -- 这里没有位点 -->\n<div>{{ x }}</div>',
      ),
    ).toBe(false)
    expect(inspectVHtmlSite('<template><div>{{ x }}</div></template>')).toEqual([])
  })
})

describe('xss 面守卫 · 真实树', () => {
  it('扫描面非空（否则下面所有判据恒真）', () => {
    // 旧端下线后 src 下非测试的 .vue/.ts 实测 163 个；下界留 150 作为"扫描面还在"的证据，
    // 真把源码目录清空了这里会红，而不是让全表恒真。
    expect(files.length).toBeGreaterThanOrEqual(150)
  })

  it('v-html 位点清单与处置时逐字相同', () => {
    const sites = files
      .filter((file) => hasVHtml(readFileSync(file, 'utf8')))
      .map(rel)
      .sort()
    expect(sites).toEqual([...KNOWN_V_HTML_SITES].sort())
  })

  it('三处位点全部只经 utils/sanitize 取白名单，没有第二份 DOMPurify 配置', () => {
    const offenders = files
      .map((file) => ({ file: rel(file), found: inspectVHtmlSite(readFileSync(file, 'utf8')) }))
      .filter((item) => item.found.length > 0)
    expect(offenders, `这些 v-html 位点绕开了唯一入口：${JSON.stringify(offenders)}`).toEqual([])
  })

  it('宽 profile USE_PROFILES 全仓归零（位点不许再用「整个 html 档」）', () => {
    // 剥注释：sanitize.ts 的档位说明里正引用着那句旧配置作为反面教材
    const hits = files
      .map((file) => rel(file))
      .filter((path) =>
        stripComments(readFileSync(join(srcRoot, path), 'utf8')).includes('USE_PROFILES'),
      )
    expect(hits, `这些文件仍用宽 profile：${JSON.stringify(hits)}`).toEqual([])
  })

  it('dompurify 的直接 import 只允许 utils/sanitize 一处', () => {
    const hits = files
      .map((file) => rel(file))
      .filter((path) =>
        /from\s+'dompurify'/i.test(stripComments(readFileSync(join(srcRoot, path), 'utf8'))),
      )
    expect(hits, `DOMPurify 被多处直接调用＝白名单又开始分叉：${JSON.stringify(hits)}`).toEqual([
      'utils/sanitize.ts',
    ])
  })

  it('decodeAiDisplayText 的载体文件里没有 v-html，且消费面未扩散', () => {
    const consumers = files
      .filter((file) => /decodeAiDisplayText/.test(stripComments(readFileSync(file, 'utf8'))))
      .map(rel)
      .filter((path) => !DECLARATION_FILES.includes(path))
      .sort()
    expect(consumers, '新增解码器消费方要先证明它不进 v-html，再登记进清单').toEqual(
      [...KNOWN_DECODER_FILES].sort(),
    )
    const offenders = consumers
      .map((path) => ({
        file: path,
        found: inspectDecoderFile(readFileSync(join(srcRoot, path), 'utf8')),
      }))
      .filter((item) => item.found.length > 0)
    expect(offenders, `AI 文本流向 v-html：${JSON.stringify(offenders)}`).toEqual([])
  })

  it('失效符号已清算：decodeHtml／renderMarkdown 全仓零命中，javascript: 不留在 href 上', () => {
    const stale = files
      .map((file) => ({ file: rel(file), raw: readFileSync(file, 'utf8') }))
      .filter((item) => /\bdecodeHtml\b|\brenderMarkdown\b/.test(item.raw))
      .map((item) => item.file)
    expect(stale, `旧名字会误导下一个读代码的人：${JSON.stringify(stale)}`).toEqual([])
    const jsHref = files.filter((file) => javascriptHref(readFileSync(file, 'utf8'))).map(rel)
    expect(jsHref, `href 上仍有 javascript: 伪协议：${JSON.stringify(jsHref)}`).toEqual([])
  })

  it('sanitize.ts 不是死配置：三档白名单各有消费方', () => {
    const consumers = ['sanitizeHtml', 'sanitizeTableCellHtml', 'sanitizeLegalHtml'].map((name) => {
      const users = files
        .map((file) => ({ path: rel(file), raw: readFileSync(file, 'utf8') }))
        .filter(
          (item) =>
            item.path !== 'utils/sanitize.ts' &&
            new RegExp(`\\b${name}\\b`).test(stripComments(item.raw)),
        )
        .map((item) => item.path)
      return { name, users }
    })
    for (const item of consumers) {
      expect(item.users.length, `${item.name} 没有消费方＝治理配置又是死的`).toBeGreaterThanOrEqual(
        1,
      )
    }
  })
})
