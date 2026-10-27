/**
 * 全站硬编码样式计数 + 令牌消费点计数：扫 src 下的 .vue 与 .css/.scss，输出各类字面量的
 * 命中行数与出现次数，并给出每个自定义属性的 var() 消费数与 src 外的提及数。
 *
 * 用法：
 *   node scripts/count-hardcoded-style.cjs            # 打印读数
 *   node scripts/count-hardcoded-style.cjs --write     # 同时把报告写入 scripts/style-hardcode-report.md
 * 口径与判据本体见 docs/前端待办项.md 的 P5-04 行；这里只负责可重生成，不判定通过与否。
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..')
const REPORT = path.join(ROOT, 'scripts', 'style-hardcode-report.md')

const PATTERNS = [
  { key: 'radius', label: 'border-radius:', re: /border-radius\s*:/g },
  // 角位单独设值（border-top-left-radius 之类）不落在上一条的匹配面里，单列一条，
  // 免得历史基线口径被改写后又有"看着是 0 其实漏了"的第二套数
  {
    key: 'radiusCorner',
    label: 'border-*-radius:（角位）',
    re: /border-(?:top|bottom)-(?:left|right)-radius\s*:|border-(?:left|right)-(?:top|bottom)-radius\s*:/g,
  },
  { key: 'shadow', label: 'box-shadow:', re: /box-shadow\s*:/g },
  { key: 'hex6', label: '6 位十六进制色', re: /#[0-9a-f]{6}\b/gi },
  { key: 'rgba', label: 'rgba(', re: /rgba\(/g },
  { key: 'hsl', label: 'hsl(', re: /hsla?\(/g },
  { key: 'gradient', label: 'gradient(', re: /(?:linear|radial|conic)-gradient\(/g },
]

const IGNORE_DIR = new Set(['node_modules', 'dist', 'test-results', '__snapshots__'])

function walk(dir, out) {
  for (const name of fs.readdirSync(dir)) {
    const p = path.join(dir, name)
    const st = fs.statSync(p)
    if (st.isDirectory()) {
      if (!IGNORE_DIR.has(name)) walk(p, out)
    } else if (/\.(vue|css|scss)$/.test(name)) {
      out.push(p)
    }
  }
  return out
}

function rel(p, base) {
  return path.relative(base || ROOT, p).replace(/\\/g, '/')
}

function isStyleSource(p, base) {
  return rel(p, base).startsWith('src/styles/')
}

/** `--root=<目录>` 用于对某一版本重跑同一口径（例如改前读数：先 `git archive` 导出再计数）。 */
function argValue(name) {
  const prefix = `--${name}=`
  const hit = process.argv.find((a) => a.startsWith(prefix))
  return hit ? hit.slice(prefix.length) : null
}

/**
 * 字面量判定：把一行按 `;` 切成声明段，段内不含 `var(` 才把它的命中算作字面量。
 * 模式本身要求冒号（`box-shadow:`），所以 transition 列表里的属性名不会被误计。
 * 这样 `border-radius: var(--r-l1)` 不计、`border-radius: 10px` 计，
 * 带兜底的 `color: var(--ink, #1f2733)` 也不计（令牌已在位，兜底不属失控硬编码）。
 */
function countLiteralDecls(line, re) {
  let total = 0
  for (const decl of line.split(';')) {
    if (decl.includes('var(')) continue
    const hits = decl.match(re)
    if (hits) total += hits.length
  }
  return total
}

/** 与 countLiteralDecls 同一判定，返回被判定为字面量的声明段，供守卫断言取值集合。 */
function literalSegments(text, re) {
  // 带 g 标志的正则有 lastIndex 状态，test 逐段复用时必须换一枚无状态的
  const probe = new RegExp(re.source, re.flags.replace('g', ''))
  const out = []
  for (const line of text.split(/\r?\n/)) {
    for (const decl of line.split(';')) {
      if (decl.includes('var(')) continue
      if (probe.test(decl)) out.push(decl.trim().replace(/\s+/g, ' '))
    }
  }
  return out
}

function measure(files, base) {
  const lines = {}
  const occurrences = {}
  const literals = {}
  for (const { key } of PATTERNS) {
    lines[key] = 0
    occurrences[key] = 0
    literals[key] = 0
  }
  let gradientFiles = 0
  const perFile = []
  for (const file of files) {
    const text = fs.readFileSync(file, 'utf8')
    const rows = text.split(/\r?\n/)
    let fileHasGradient = false
    let hardcodeTotal = 0
    for (const row of rows) {
      for (const p of PATTERNS) {
        const hits = row.match(p.re)
        if (!hits) continue
        lines[p.key] += 1
        occurrences[p.key] += hits.length
        const lit = countLiteralDecls(row, p.re)
        literals[p.key] += lit
        if (p.key === 'gradient') fileHasGradient = true
        else hardcodeTotal += lit
      }
    }
    if (fileHasGradient) gradientFiles += 1
    if (hardcodeTotal > 0) perFile.push({ file: rel(file, base), hardcodeTotal })
  }
  perFile.sort((a, b) => b.hardcodeTotal - a.hardcodeTotal || a.file.localeCompare(b.file))
  return { lines, occurrences, literals, gradientFiles, perFile }
}

function collectAll(scopeDirs) {
  const out = []
  for (const dir of scopeDirs) walkInto(dir, out, /\.(vue|css|scss|ts|js|cjs|mjs|html)$/)
  return out
}

function walkInto(dir, out, re) {
  if (!fs.existsSync(dir)) return out
  for (const name of fs.readdirSync(dir)) {
    if (IGNORE_DIR.has(name)) continue
    const p = path.join(dir, name)
    const st = fs.statSync(p)
    if (st.isDirectory()) walkInto(p, out, re)
    else if (re.test(name)) out.push(p)
  }
  return out
}

/** 令牌审计：定义取 src/styles/**，消费数取整个 frontend（src + e2e + 根配置）。 */
function tokenAudit(styleFiles, base) {
  const scanDirs = [path.join(base || ROOT, 'src'), path.join(base || ROOT, 'e2e')]
  const consumers = collectAll(scanDirs)
  const styleSet = new Set(styleFiles)
  const texts = consumers.map((f) => ({
    raw: fs.readFileSync(f, 'utf8'),
    inStyles: styleSet.has(f),
  }))
  const defs = new Map()
  for (const f of styleFiles) {
    const raw = fs.readFileSync(f, 'utf8')
    for (const m of raw.matchAll(/(--[a-z][a-z0-9-]*)\s*:/g)) {
      const name = m[1]
      defs.set(name, (defs.get(name) || 0) + 1)
    }
  }
  const rows = []
  for (const [name, defined] of [...defs.entries()].sort()) {
    const esc = name.replace(/-/g, '\\-')
    let used = 0
    let mentioned = 0
    for (const { raw, inStyles } of texts) {
      used += (raw.match(new RegExp(`var\\(\\s*${esc}\\s*[,)]`, 'g')) || []).length
      // 「其它提及」只统计样式目录之外（视图、脚本、e2e），用来抓"删了定义但引用还在"
      if (!inStyles) mentioned += (raw.match(new RegExp(esc, 'g')) || []).length
    }
    rows.push({ name, defined, used, mentioned })
  }
  return rows
}

function tokenSection(rows) {
  const allZero = rows.filter((r) => r.used === 0)
  // --el-* 由 Element Plus 内部消费（组件样式里直接读），本仓源码里没有 var() 属正常，不算零消费
  const zero = allZero.filter((r) => !r.name.startsWith('--el-'))
  const elZero = allZero.filter((r) => r.name.startsWith('--el-'))
  const ac = rows.filter((r) => r.name.startsWith('--ac-'))
  const lines = [
    '### 令牌消费点（定义面＝`src/styles/**`，消费面＝整个 `frontend`）',
    '',
    '| 汇总 | 数 |',
    '| --- | --- |',
    `| 定义到的自定义属性总数 | ${rows.length} |`,
    `| var() 消费为 0 的属性（除 \`--el-*\`） | ${zero.length} |`,
    `| 其中 \`--ac-*\` 族 | ${zero.filter((r) => r.name.startsWith('--ac-')).length} |`,
    `| \`--el-*\` 消费为 0（EP 内部读，不列） | ${elZero.length} |`,
    '',
    '**`--ac-*` 别名族逐枚消费数**（P5-05 判「别名族收敛」与 P6-06 判「token 有没有消费点」都引这张表）',
    '',
    '| 属性 | 定义处 | var() 消费 | 样式目录外提及 |',
    '| --- | --- | --- | --- |',
  ]
  for (const r of ac) lines.push(`| \`${r.name}\` | ${r.defined} | ${r.used} | ${r.mentioned} |`)
  lines.push(
    '',
    '**零消费清单**（「样式目录外提及」＝视图／脚本／e2e 里的引用，用来抓"删了定义但引用还在"）',
    '',
    '| 属性 | 定义处 | var() 消费 | 样式目录外提及 |',
    '| --- | --- | --- | --- |',
  )
  for (const r of zero) lines.push(`| \`${r.name}\` | ${r.defined} | ${r.used} | ${r.mentioned} |`)
  if (!zero.length) lines.push('| — | — | — | — |')
  return lines.join('\n')
}

function table(title, m) {
  const head = `### ${title}\n\n| 类别 | 命中行数 | 出现次数 | 其中字面量 |\n| --- | --- | --- | --- |\n`
  const rows = PATTERNS.map(
    (p) => `| ${p.label} | ${m.lines[p.key]} | ${m.occurrences[p.key]} | ${m.literals[p.key]} |`,
  ).join('\n')
  const grad = `\n| 含渐变的文件数 | ${m.gradientFiles} | — | — |\n`
  const top =
    '\n**Top 10（按 radius／角位 radius／shadow／hex6／rgba／hsl 六类字面量处数合计）**\n\n' +
    '| 文件 | 处数 |\n| --- | --- |\n' +
    m.perFile
      .slice(0, 10)
      .map((r) => `| \`${r.file}\` | ${r.hardcodeTotal} |`)
      .join('\n') +
    '\n'
  return head + rows + grad + top
}

function buildReport(base) {
  const src = path.join(base, 'src')
  if (!fs.existsSync(src)) {
    console.error(`[ERROR] 未找到扫描目录 ${src}`)
    process.exit(1)
  }
  const all = walk(src, [])
  const vue = all.filter((f) => f.endsWith('.vue'))
  const styles = all.filter((f) => !f.endsWith('.vue') && isStyleSource(f, base))
  // 扫描面自检：文件数低于下界说明路径或过滤条件错了，此时读数是空气
  if (vue.length < 200) {
    console.error(`[ERROR] .vue 扫描面只有 ${vue.length} 个文件（下界 200），计数不可信`)
    process.exit(1)
  }
  if (styles.length < 3) {
    console.error(`[ERROR] src/styles 扫描面只有 ${styles.length} 个文件（下界 3），计数不可信`)
    process.exit(1)
  }
  const mVue = measure(vue, base)
  const mStyles = measure(styles, base)
  const stamp = new Date().toISOString().slice(0, 16).replace('T', ' ')

  return [
    '# 前端硬编码样式计数报告',
    '',
    `> 由 \`node frontend/scripts/count-hardcoded-style.cjs --write\` 生成于 ${stamp} UTC。`,
    '> 判据本体见 `docs/前端待办项.md` P5-04；基线（2026-10-19 实测，`.vue` 内行数）：',
    '> `border-radius:` 313／6 位十六进制色 175／`rgba(` 110／`box-shadow:` 73／含渐变的文件 22。',
    '> 该基线当年没有留命令，口径按最宽的解释落地：**命中行数**＝该行至少一次命中（含 `var()` 写法），',
    '> **出现次数**＝逐次累计，**其中字面量**＝按 `;` 切段后段内不含 `var(` 的命中次数。',
    '> 三列同表给出，判「硬编码有没有降」看第三列；前两列只用于和历史读数对齐。',
    '> 「处数」＝radius／角位 radius／shadow／hex6／rgba／hsl 六类**字面量**次数合计，不含 gradient（渐变单列）。',
    '> 对某一历史版本重跑同口径：`git archive <commit> frontend | tar -x -C <tmp>` 后加 `--root=<tmp>/frontend`。',
    '',
    table('`.vue`（' + vue.length + ' 个文件）', mVue),
    '',
    table('`src/styles/**`（' + styles.length + ' 个文件）', mStyles),
    '',
    tokenSection(tokenAudit(styles, base)),
    '',
  ].join('\n')
}

function main() {
  const rootArg = argValue('root')
  const base = rootArg ? path.resolve(rootArg) : ROOT
  const usedArg = argValue('used')
  if (usedArg) {
    // 只给消费点读数：`--used=--ac-radius-md,--card-line` 逐名列出 var() 消费数
    const src = path.join(base, 'src')
    const rows = tokenAudit(walk(src, []).filter((f) => !f.endsWith('.vue') && isStyleSource(f, base)), base)
    for (const raw of usedArg.split(',')) {
      const name = `--${raw.trim().replace(/^-+/, '')}`
      const hit = rows.find((r) => r.name === name)
      console.log(`${name}: ${hit ? `var() 消费 ${hit.used}／定义 ${hit.defined} 处` : '未在 src/styles 里定义'}`)
    }
    return
  }
  const out = buildReport(base)
  console.log(out)
  if (!rootArg && process.argv.includes('--write')) {
    fs.writeFileSync(REPORT, out, 'utf8')
    console.log(`[OK] 报告写入 ${rel(REPORT)}`)
  }
}

module.exports = { PATTERNS, countLiteralDecls, literalSegments }

// 被守卫测试 require 时只取口径函数，不重跑计数
if (require.main === module) main()
