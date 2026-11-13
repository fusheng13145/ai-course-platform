/**
 * 全仓 API 方法引用一致性检查：
 * 扫描 src 下所有 `import ... from '@/api/xxx'` 的使用，比对被引用的方法是否存在于该 api 模块导出。
 *
 * 覆盖口径（2026-10-04 扩充；2026-10-13 补类型导出与 type 具名导入）：
 * - 目录：src 全量 .ts/.vue（仅排除 api 定义本身与测试文件）。
 *   原实现只扫 composables + views，components/stores/layouts/utils 里的 API 调用完全不在雷达内。
 * - 导入形式：default 导入、具名导入（`import { foo }` / `import { foo as bar }` /
 *   `import { type Foo }`）、命名空间导入。
 *   原实现只识别 default 导入，具名导入的错引一个都抓不到。
 * - 导出形式：export const/let/var、export function、export interface/type/enum/class。
 *   类型导出（interface/type）虽无运行时值，但作为具名导入是合法用法，故一并登记，
 *   避免把 `import { type X } from '@/api/xxx'` 误报为「无此导出」。
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..', 'src')

function walk(dir, exts, out = []) {
  for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
    if (e.name === '__tests__' || e.name === 'node_modules') continue
    const p = path.join(dir, e.name)
    if (e.isDirectory()) walk(p, exts, out)
    else if (exts.some((x) => e.name.endsWith(x))) out.push(p)
  }
  return out
}

/** 从对象字面量正文里取「行首键名/简写属性」，作为该对象的成员名集合 */
function objectKeys(block) {
  const keys = new Set()
  // 只认行首标识符后紧跟 : ( , } —— 避免把 `request.get(` 这类值内部的调用误当成成员
  const STOP = new Set([
    'if',
    'for',
    'while',
    'switch',
    'return',
    'const',
    'let',
    'var',
    'function',
    'else',
    'try',
    'catch',
    'throw',
    'await',
  ])
  for (const m of block.matchAll(/^\s*(\w+)\s*[:,(}]/gm)) {
    if (!STOP.has(m[1])) keys.add(m[1])
  }
  return keys
}

/**
 * 提取 api 模块对外可见的名字集合：
 * 具名导出（`export const/function/{...}`）+ 默认导出对象的成员（模块普遍以 default 对象对外提供方法表）。
 */
function apiExports(file) {
  const src = fs.readFileSync(file, 'utf8')
  const names = new Set()

  // export const NAME [: 注解] = ... （对象字面量则连同其成员一起登记）
  for (const m of src.matchAll(/^export\s+(?:const|let|var)\s+(\w+)\s*(?::[^=]+)?=\s*\{/gm)) {
    names.add(m[1])
    const block = src.slice(m.index)
    const body = block.match(/=\s*\{([\s\S]*?)\n\}/)
    if (body) for (const k of objectKeys(body[1])) names.add(k)
  }
  // export const NAME = 非对象值（箭头函数等）
  for (const m of src.matchAll(/^export\s+(?:const|let|var)\s+(\w+)\s*(?::[^=\n]+)?=\s*(?!\{)/gm))
    names.add(m[1])
  // export function foo(...) / export async function foo(...)
  for (const m of src.matchAll(/^export\s+(?:async\s+)?function\s+(\w+)/gm)) names.add(m[1])
  // export interface Foo / export type Foo = ... / export enum Foo / export (abstract) class Foo
  for (const m of src.matchAll(
    /^export\s+(?:declare\s+)?(?:interface|type|enum|(?:abstract\s+)?class)\s+(\w+)/gm,
  ))
    names.add(m[1])
  // export { a, b as c }
  for (const m of src.matchAll(/^export\s*\{([^}]*)\}/gm)) {
    for (const part of m[1].split(',')) {
      const seg = part.trim()
      if (!seg || seg === 'default') continue
      const asMatch = seg.match(/\bas\s+(\w+)$/)
      names.add(asMatch ? asMatch[1] : seg)
    }
  }
  // export default { ... }（贪婪到最后一个顶层闭合，避免嵌套对象提前截断成员列表）
  const dm = src.match(/^export\s+default\s*\{([\s\S]*?)\n\}/m)
  if (dm) for (const k of objectKeys(dm[1])) names.add(k)
  return names
}

// 收集 api 模块（TS 迁移后 api/ 为 .ts，保留 .js 兼容以防混合期漏扫）
const apiDir = path.join(ROOT, 'api')
const apiModules = {}
const apiModuleExt = {}
for (const f of fs.readdirSync(apiDir)) {
  const ext = ['.ts', '.js'].find((x) => f.endsWith(x))
  if (!ext) continue
  const name = f.slice(0, -ext.length)
  apiModules[name] = apiExports(path.join(apiDir, f))
  apiModuleExt[name] = ext
}

// 扫描 src 下全部 .ts/.js/.vue（api/ 目录本身是导出定义方，排除）
const targets = walk(ROOT, ['.ts', '.js', '.vue']).filter((f) => {
  const rel = path.relative(ROOT, f).split(path.sep).join('/')
  return !rel.startsWith('api/')
})
const issues = []
let scannedImports = 0

/** 解析 import 子句：default 别名、命名空间别名、具名导入（含 as 重命名） */
function parseImportClause(clause) {
  const out = { defaultAlias: null, namespaceAlias: null, named: [] }
  let rest = clause
  const ns = rest.match(/\*\s+as\s+(\w+)/)
  if (ns) {
    out.namespaceAlias = ns[1]
    rest = rest.replace(ns[0], '')
  }
  const braces = rest.match(/\{([^}]*)\}/)
  if (braces) {
    for (const part of braces[1].split(',')) {
      // 剥离 TS 具名导入的 `type` 修饰（`import { type Foo }` / `import { type Foo as Bar }`）
      const seg = part.trim().replace(/^type\s+/, '')
      if (!seg) continue
      const asMatch = seg.match(/^(\w+)\s+as\s+(\w+)$/)
      // 具名导入：局部名用于匹配调用，原始导出名用于比对 api 模块导出
      out.named.push(
        asMatch ? { exported: asMatch[1], local: asMatch[2] } : { exported: seg, local: seg },
      )
    }
    rest = rest.replace(braces[0], '')
  }
  const def = rest.replace(/,/g, ' ').trim().split(/\s+/).filter(Boolean)[0]
  if (def && /^\w+$/.test(def)) out.defaultAlias = def
  return out
}

for (const file of targets) {
  const src = fs.readFileSync(file, 'utf8')
  for (const m of src.matchAll(/import\s+([^;'"]*?)\s+from\s+['"]@\/api\/([\w-]+)['"]/g)) {
    const module = m[2]
    scannedImports++
    const modExports = apiModules[module]
    if (!modExports) {
      issues.push(`${file}: import 的 api 模块 '@/api/${module}' 不存在`)
      continue
    }
    const { defaultAlias, namespaceAlias, named } = parseImportClause(m[1])

    // 具名导入：导入的导出名必须真实存在，否则运行时拿到 undefined
    for (const { exported, local } of named) {
      if (exported === 'default') continue
      if (!modExports.has(exported)) {
        issues.push(
          `${file}: import { ${exported}${local !== exported ? ' as ' + local : ''} } from '@/api/${module}' ` +
            `—— 该模块无此导出 [实际导出: ${Array.from(modExports).join(', ')}]`,
        )
      }
    }

    // default / 命名空间别名：逐个方法比对
    for (const alias of [defaultAlias, namespaceAlias]) {
      if (!alias || alias === 'request') continue // request 是 axios 实例（get/post/... 均存在）
      for (const call of src.matchAll(new RegExp(alias + '\\.(\\w+)(\\?)?\\.?\\s*\\(', 'g'))) {
        const method = call[1]
        if (method === 'default' || modExports.has(method)) continue
        const suffix =
          call[2] === '?' ? '（可选链调用：运行时不报错，但这段逻辑永远静默不执行）' : ''
        issues.push(
          `${file}: ${alias}.${method}${suffix} —— api/${module}${apiModuleExt[module]} 无此导出 ` +
            `[实际导出: ${Array.from(modExports).join(', ')}]`,
        )
      }
    }
  }
}

if (issues.length) {
  console.log(`发现 ${issues.length} 处 API 方法引用不一致：`)
  issues.forEach((i) => console.log('  ' + i))
  process.exit(1)
}
// 口径自守：扫描面塌陷（目录结构调整、正则失配）时必须报警，而不是"0 问题"绿灯。
// 地板＝100 文件（2026-10-30 由项目主授权重定，推导：旧前端整树删除后实测面为 122 个源文件，
// 地板取 100 以容自然波动又远低于真实面，仍能抓住"扫描器哑掉"）。旧地板 300 是双树并存期
// （旧端 views 290 个文件在树）的口径，随 15fb858 作废，保留为史料。导入地板 50 未动（今日实测 87）。
if (targets.length < 100 || scannedImports < 50) {
  console.error(
    `[lint:api-refs] 异常：仅扫到 ${targets.length} 个源文件 / ${scannedImports} 条 @/api 导入，` +
      '低于既有口径（≥100 文件 / ≥50 导入），扫描器可能已失效，拒绝放行',
  )
  process.exit(1)
}
console.log(
  `✅ 全部 API 方法引用一致（${targets.length} 个源文件 / ${scannedImports} 条 @/api 导入，含 default、具名与命名空间导入）`,
)
