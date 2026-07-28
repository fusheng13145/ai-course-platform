/**
 * 一次性代码迁移脚本：将 src 下的裸 console.log/info/debug/warn/error 调用
 * 替换为统一的 logger.*，并按文件类型注入 `import logger from '@/utils/logger'`。
 *
 * 规则：
 * - 仅替换「方法调用」形态：console.<m>( → logger.<m>(（避免误伤字符串/注释里非调用文本）。
 * - 跳过 src/utils/logger.js 自身。
 * - 已存在 logger 导入的文件不重复注入。
 * - .vue 在首个 <script...> 开标签后注入；.js/.ts 在文件顶部注入。
 *
 * 用法：node scripts/migrate-console-to-logger.mjs
 */
import { readFileSync, writeFileSync } from 'node:fs'
import { readdirSync, statSync } from 'node:fs'
import { join, extname, resolve } from 'node:path'

const SRC = resolve(process.cwd(), 'src')
const LOGGER_ABS = resolve(SRC, 'utils', 'logger.js')
const CALL_RE = /\bconsole\.(log|info|debug|warn|error)\s*\(/g
const IMPORT_LINE = "import logger from '@/utils/logger'"

function walk(dir, acc = []) {
  for (const name of readdirSync(dir)) {
    const p = join(dir, name)
    const st = statSync(p)
    if (st.isDirectory()) {
      if (name === 'node_modules' || name === 'dist') continue
      walk(p, acc)
    } else {
      const ext = extname(p)
      if (['.js', '.ts', '.vue'].includes(ext)) acc.push(p)
    }
  }
  return acc
}

function hasLoggerImport(src) {
  return /import\s+logger\s+from\s+['"]@\/utils\/logger['"]/.test(src)
}

function injectImportJs(src) {
  return IMPORT_LINE + '\n' + src
}

function injectImportVue(src) {
  // 在首个 <script ...> 开标签之后插入 import
  const m = src.match(/<script\b[^>]*>/)
  if (!m) return src // 无 script 块（理论上不会有 console），保持原样
  const idx = m.index + m[0].length
  return src.slice(0, idx) + '\n' + IMPORT_LINE + src.slice(idx)
}

let changedFiles = 0
let totalReplacements = 0
const files = walk(SRC)

for (const file of files) {
  if (resolve(file) === LOGGER_ABS) continue
  const original = readFileSync(file, 'utf8')
  CALL_RE.lastIndex = 0
  const matches = original.match(CALL_RE)
  if (!matches || matches.length === 0) continue

  let updated = original.replace(CALL_RE, (full, method) => `logger.${method}(`)

  if (!hasLoggerImport(updated)) {
    updated = extname(file) === '.vue' ? injectImportVue(updated) : injectImportJs(updated)
  }

  if (updated !== original) {
    writeFileSync(file, updated, 'utf8')
    changedFiles++
    totalReplacements += matches.length
    console.log(`[migrated] ${file.replace(SRC, 'src')} (${matches.length})`)
  }
}

console.log(`\nDone. Files changed: ${changedFiles}, replacements: ${totalReplacements}`)
