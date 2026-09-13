/**
 * TypeScript 类型检查棘轮门禁（typecheck ratchet gate）。
 *
 * 背景：项目历史上前端从未接入类型检查——`npm run build` 走 Vite（剥离类型、不做校验），
 * 导致 `.ts` 与 `.vue` 的类型错误长期不可见。首次接入 vue-tsc 时存量错误为数百条，
 * 属渐进式迁移存量，无法一次性清零。因此门禁采用「棘轮」策略：
 *
 *   - 错误数 **不得超过** 基线（超限即失败，阻断回归）
 *   - 错误数 **低于** 基线时提示下调基线（基线只许下降，不许上调）
 *
 * 基线记录于 scripts/typecheck-baseline.json。修复存量后请同步下调基线值，
 * 使门禁持续收紧；禁止为放行新增错误而上调基线。
 */
const { execFileSync } = require('child_process')
const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..')
const BASELINE_FILE = path.join(__dirname, 'typecheck-baseline.json')
const VUE_TSC = path.join(ROOT, 'node_modules', 'vue-tsc', 'bin', 'vue-tsc.js')

function readBaseline() {
  if (!fs.existsSync(BASELINE_FILE)) {
    console.error(`[typecheck] 缺少基线文件：${path.relative(ROOT, BASELINE_FILE)}`)
    process.exit(1)
  }
  return JSON.parse(fs.readFileSync(BASELINE_FILE, 'utf8'))
}

function runVueTsc() {
  if (!fs.existsSync(VUE_TSC)) {
    console.error('[typecheck] 未找到 vue-tsc，请先执行 npm ci / npm install')
    process.exit(1)
  }
  try {
    return { output: execFileSync(process.execPath, [VUE_TSC, '--noEmit'], { cwd: ROOT, encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }), failed: false }
  } catch (e) {
    return { output: `${e.stdout || ''}${e.stderr || ''}`, failed: true }
  }
}

function countErrors(output) {
  const lines = output.split(/\r?\n/)
  const errors = lines.filter((l) => /error TS\d+/.test(l))
  const files = new Set()
  for (const l of errors) {
    const m = l.match(/^([^(]+)\(\d+,\d+\)/)
    if (m) files.add(m[1])
  }
  return { errors, fileCount: files.size }
}

function main() {
  const baseline = readBaseline()
  const { output } = runVueTsc()
  const { errors, fileCount } = countErrors(output)

  console.log(`[typecheck] 当前错误 ${errors.length} 条 / ${fileCount} 个文件`)
  console.log(`[typecheck] 基线错误 ${baseline.errors} 条（${baseline.note || '基线'}）`)

  if (errors.length > baseline.errors) {
    console.error('')
    console.error(`[typecheck] 失败：类型错误较基线增加 ${errors.length - baseline.errors} 条。`)
    console.error('[typecheck] 新增错误明细（最多 40 条）：')
    for (const l of errors.slice(0, 40)) console.error('  ' + l)
    console.error('')
    console.error('[typecheck] 请修复新增错误；基线上调需在评审中说明理由。')
    process.exit(1)
  }

  if (errors.length < baseline.errors) {
    console.log('')
    console.log(`[typecheck] 通过，且较基线减少 ${baseline.errors - errors.length} 条。`)
    console.log(`[typecheck] 请将 scripts/typecheck-baseline.json 的 errors 下调为 ${errors.length} 以收紧门禁。`)
  } else {
    console.log('[typecheck] 通过（与基线持平）。')
  }
}

main()
