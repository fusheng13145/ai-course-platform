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
 * 基线记录于 scripts/typecheck-baseline.json：
 *   - `errors` / `files`：总量
 *   - `byFile`：逐文件错误数，用于把「较基线新增」的定位精确到文件，
 *     而不是把 vue-tsc 输出的前 40 行（含大量存量错误）误报成新增错误。
 *
 * 用法：
 *   npm run typecheck:gate            校验（CI）
 *   node scripts/check-types.cjs --sync   存量下降后同步基线（只许下降，超限时拒绝）
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
    return {
      output: execFileSync(process.execPath, [VUE_TSC, '--noEmit'], {
        cwd: ROOT,
        encoding: 'utf8',
        stdio: ['ignore', 'pipe', 'pipe'],
        maxBuffer: 64 * 1024 * 1024,
      }),
      failed: false,
      status: 0,
    }
  } catch (e) {
    return { output: `${e.stdout || ''}${e.stderr || ''}`, failed: true, status: e.status ?? -1 }
  }
}

/** 解析 vue-tsc 输出：错误行 + 逐文件计数（路径统一为 POSIX 风格相对路径） */
function parseErrors(output) {
  const errors = output.split(/\r?\n/).filter((l) => /error TS\d+/.test(l))
  const byFile = new Map()
  for (const l of errors) {
    const m = l.match(/^([^(]+)\(\d+,\d+\)/)
    if (!m) continue
    const rel = m[1].replace(/\\/g, '/').replace(/^\.\/+/, '')
    byFile.set(rel, (byFile.get(rel) || 0) + 1)
  }
  return { errors, byFile }
}

function syncBaseline(errors, fileCount, byFile) {
  const baseline = readBaseline()
  if (errors > baseline.errors) {
    console.error(`[typecheck] 拒绝上调基线：当前 ${errors} 条 > 基线 ${baseline.errors} 条。`)
    console.error('[typecheck] 棘轮只许下降；请先修复新增错误。')
    process.exit(1)
  }
  const next = {
    ...baseline,
    errors,
    files: fileCount,
    byFile: Object.fromEntries(Array.from(byFile.entries()).sort((a, b) => b[1] - a[1])),
    note: `${baseline.note}（${new Date().toISOString().slice(0, 10)} 同步：${errors} 条）`,
  }
  fs.writeFileSync(BASELINE_FILE, JSON.stringify(next, null, 2) + '\n')
  console.log(
    `[typecheck] 基线已下调并写入：${baseline.errors} → ${errors} 条 / ${fileCount} 个文件`,
  )
}

function main() {
  const baseline = readBaseline()
  const { output, failed, status } = runVueTsc()
  const { errors, byFile } = parseErrors(output)
  const fileCount = byFile.size

  // 关键护栏：vue-tsc 非零退出却解析不到任何 error 行，说明是崩溃 / 配置错误 / 输出被截断。
  // 历史缺陷（2026-10-04 修复）：原实现忽略 failed 标志，把「数不出错误」当成「零错误」绿灯放行。
  if (failed && errors.length === 0) {
    console.error(`[typecheck] vue-tsc 异常退出（exit ${status}）且输出中无错误行 —— 拒绝放行`)
    console.error(output.slice(0, 4000))
    process.exit(1)
  }

  console.log(`[typecheck] 当前错误 ${errors.length} 条 / ${fileCount} 个文件`)
  console.log(`[typecheck] 基线错误 ${baseline.errors} 条（${baseline.note || '基线'}）`)

  // --sync：只把基线同步到当前（更优）水位，不做失败判定
  if (process.argv.includes('--sync')) {
    syncBaseline(errors.length, fileCount, byFile)
    return
  }

  if (errors.length > baseline.errors) {
    const base = baseline.byFile || {}
    const worsened = []
    for (const [file, count] of byFile) {
      const prev = base[file] || 0
      if (count > prev) worsened.push({ file, count, prev, delta: count - prev })
    }
    worsened.sort((a, b) => b.delta - a.delta)

    console.error('')
    console.error(`[typecheck] 失败：类型错误较基线增加 ${errors.length - baseline.errors} 条。`)
    if (!base || Object.keys(base).length === 0) {
      console.error('[typecheck] 基线缺少 byFile 明细，无法逐文件定位，输出前 40 条错误供参考：')
      for (const l of errors.slice(0, 40)) console.error('  ' + l)
    } else if (worsened.length === 0) {
      console.error(
        '[typecheck] 逐文件计数均未超过基线，但总量上升：基线 byFile 已过期，请重新 --sync。',
      )
    } else {
      console.error('[typecheck] 较基线变差的文件（真实新增错误）：')
      for (const w of worsened.slice(0, 20)) {
        console.error(`  ${w.file}: ${w.prev} → ${w.count}（+${w.delta}）`)
        for (const l of errors) {
          if (l.replace(/\\/g, '/').startsWith(w.file + '(')) console.error('      ' + l.trim())
        }
      }
    }
    console.error('')
    console.error('[typecheck] 请修复新增错误；基线上调需在评审中说明理由。')
    process.exit(1)
  }

  if (errors.length < baseline.errors) {
    console.log('')
    console.log(`[typecheck] 通过，且较基线减少 ${baseline.errors - errors.length} 条。`)
    console.log('[typecheck] 请执行 `node scripts/check-types.cjs --sync` 收紧基线。')
  } else {
    console.log('[typecheck] 通过（与基线持平）。')
  }
}

main()
