/**
 * 产物体积预算门禁（bundle size gate）。
 *
 * 背景：`vite.config.ts` 的 `manualChunks` 一度引用 exceljs / pdfjs-dist / jspdf / marked
 * 等**并不在 package.json 里**的依赖，属死配置（分支永不命中）；清理后真实分块才第一次可信。
 * 但「分块正确」不等于「体积不失控」——ECharts 与 Element Plus 是天然大户，
 * 若无人守线，一次「顺手多引一个组件库」就会静默把首屏推高几百 KB。
 *
 * 本门禁只做一件事：把当前实测基线写成**上限**，构建产物超限即失败。
 * 口径与既有 `check-types.cjs` 一致：超限 exit 1 阻断；有余量只提示，不阻断。
 *
 * ⚠️ 与 `check-types.cjs` 的棘轮不同，此处**没有自动下调机制**——上限需人工在评审后收紧，
 * 因为「体积涨了但没超线」是允许的（属正常功能增长），自动收紧会把噪声当成基线。
 *
 * 用法：
 *   npm run build && npm run size:gate     # 构建后校验
 *   node scripts/check-bundle-size.cjs     # 等价直接调用
 *
 * ⚠️ 依赖 `dist/assets`（构建产物，已 gitignore）：**未先 build 时脚本会明确报错而非静默通过**，
 * 避免"没产物所以没超限"的假绿。
 */
const fs = require('fs')
const path = require('path')

const ROOT = path.resolve(__dirname, '..')
const ASSETS = path.join(ROOT, 'dist', 'assets')

/**
 * 体积上限（未压缩 KB）。
 * 基线来源：2026-10-13 本机 `npm run build` 后 `dist/assets` 实测（`size:gate` 首跑读数）。
 * 留约 10% 余量以容忍正常功能增长；**上调须在评审中说明理由**。
 */
const BUDGETS = {
  // 可视化与 UI 库：天然大户，单独设线以便一眼看出是哪一类在涨
  echarts: 900, // 实测 805.06
  'element-plus': 830, // 实测 739.31（另有 .css 分块，见下方 CSS 预算）
  xlsx: 380, // 实测 323.28
  // 其余功能块合计（含 vue-vendor / axios / dompurify / html2canvas / 各路由懒加载块）
  othersTotal: 1300, // 实测 1145.55（129 个 JS 块中除上面三块之外的合计）
  // 全部 JS 合计
  jsTotal: 3400, // 实测 3013.20
}

/** 单块跳过的上限校验名单（体积预算已由 jsTotal 兜住，无需逐块） */
const CSS_BUDGET = { 'element-plus': 420 } // 实测 382

function kb(bytes) {
  return Math.round((bytes / 1024) * 100) / 100
}

function readAssets() {
  if (!fs.existsSync(ASSETS)) {
    console.error(`[bundle] 未找到构建产物目录：${path.relative(ROOT, ASSETS)}`)
    console.error('[bundle] 请先执行 `npm run build`，否则无法判定体积（拒绝静默通过）。')
    process.exit(1)
  }
  return fs.readdirSync(ASSETS).map((name) => {
    const stat = fs.statSync(path.join(ASSETS, name))
    return { name, size: kb(stat.size) }
  })
}

function main() {
  const files = readAssets()
  if (files.length === 0) {
    console.error('[bundle] 产物目录为空 —— 拒绝放行（疑似构建被中断）。')
    process.exit(1)
  }

  const js = files.filter((f) => f.name.endsWith('.js'))
  const css = files.filter((f) => f.name.endsWith('.css'))

  /** 按「文件名前缀」归并同名分块（Vite 产出 `<name>-<hash>.js`） */
  const chunkKb = (prefix) => {
    const hit = js.find((f) => f.name.startsWith(`${prefix}-`))
    return hit ? hit.size : 0
  }

  const failures = []
  const notes = []

  const echarts = chunkKb('echarts')
  const elementPlus = chunkKb('element-plus')
  const xlsx = chunkKb('xlsx')
  // 各分块体积已在 readAssets() 内换算为 KB，故此处求和**不得再次换算**
  const jsTotal = Math.round(js.reduce((s, f) => s + f.size, 0) * 100) / 100
  const othersTotal = Math.round((jsTotal - echarts - elementPlus - xlsx) * 100) / 100

  for (const [label, actual, limit] of [
    ['echarts', echarts, BUDGETS.echarts],
    ['element-plus', elementPlus, BUDGETS['element-plus']],
    ['xlsx', xlsx, BUDGETS.xlsx],
    ['其余功能块合计', othersTotal, BUDGETS.othersTotal],
    ['JS 合计', jsTotal, BUDGETS.jsTotal],
  ]) {
    if (actual > limit) failures.push(`${label}: ${actual} KB > 上限 ${limit} KB`)
    else notes.push(`${label}: ${actual} KB / 上限 ${limit} KB`)
  }

  const elementPlusCss = css.find((f) => f.name.startsWith('element-plus-'))
  if (elementPlusCss) {
    if (elementPlusCss.size > CSS_BUDGET['element-plus'])
      failures.push(
        `element-plus CSS: ${elementPlusCss.size} KB > 上限 ${CSS_BUDGET['element-plus']} KB`,
      )
    else
      notes.push(
        `element-plus CSS: ${elementPlusCss.size} KB / 上限 ${CSS_BUDGET['element-plus']} KB`,
      )
  }

  console.log(`[bundle] 产物 ${files.length} 个（JS ${js.length} / CSS ${css.length}）`)
  for (const n of notes) console.log(`  · ${n}`)

  if (failures.length) {
    console.error('')
    console.error(`[bundle] 失败：${failures.length} 项超出体积上限。`)
    for (const f of failures) console.error(`  ✗ ${f}`)
    console.error('[bundle] 请先排查新增依赖/组件导入，或在评审后上调上限并说明理由。')
    process.exit(1)
  }

  console.log('[bundle] 通过：全部体积指标在预算内。')
}

main()
