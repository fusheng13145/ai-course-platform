// 解码尺的**可证伪性取证**脚本（Task 4c Step 1g；只取证，不参与采集，也不是门禁）。
//
// 【它存在的理由】`helpers.spec.ts` 那条"同一张图的 filter0 与 filter3 必须解成同一份 RGBA"的 KAT
//   是注释里说"这条能抓 bug"的——而一条从没被弄红过的断言不是守卫。本脚本把注释点名的那个 bug
//   **原地注入一份坏尺副本**，看两件事：① KAT 对会不会红；② radius 那对图的计数会漂到哪。
// 【2026-10-19 本会话的实测结果（每条都可重跑）】
//   好尺：KAT 对 byteDiffPx = 0；radius = 计数 1,394 / 字节差 1,983（= 跑测器自己报的数）
//   坏尺（`line[x] + (a + b) >> 1`，即注释点名的少一层括号那一版）：
//         KAT 对 byteDiffPx = **696** ⇒ helpers 那条断言**会红**；
//         radius = 计数 **2,257** / 字节差 2,920 ⇒ 与注释里"漂到 2257"这个数字**逐字对上**
//         ⇒ 从此 `1,394 → 2,257` 不再是一句不可复现的历史转述，而是一条命令能重放的读数。
//   另一个变体（只把括号挪到 avg 外面：`(a + b) >> 1` 里 `+` 本就比 `>>` 结合更紧）
//         KAT 0 / radius 1,394 ⇒ **语义与好尺相同**，所以它**不是**那次 bug 的形状；
//         这条同时说明："少写一层括号"到底会不会改变结果取决于括号挪在哪，别凭感觉断言。
// 【用法】cd frontend && node e2e/visual/tools/kat-falsification.cjs
// 【前置】`e2e/visual/tools/evidence/kat-filter0.png` / `kat-filter3.png` 与
//   `e2e/visual/__snapshots__/sensitivity.spec.ts/admin-dashboard-light-radius-probe.png` +
//   `e2e/visual/tools/evidence/admin-dashboard-light-radius-probe-captured.png` 必须在盘上
//   （分别由 `helpers.spec.ts` 与一次 `PW_LADDER=radius` 的探针跑生成；两者都不入库，跑一次即回来）。
// 【纪律】坏尺只写在 `tools/evidence/broken-diff.cjs`（git-ignored），跑完即删；
//   **绝不允许**把坏尺的那支表达式抄回 `measure-diff.cjs`。
const fs = require('node:fs')
const path = require('node:path')
const { execFileSync } = require('node:child_process')

const SRC = path.join(__dirname, 'measure-diff.cjs')
const BROKEN = path.join(__dirname, 'evidence', 'broken-diff.cjs')
const src = fs.readFileSync(SRC, 'utf8')

const variants = {
  // 注释里点名的原始错法：`line[x] + (a + b) >> 1` —— + 比 >> 结合更紧，少一层括号就是另一种算法
  precedence: {
    from: `          const avg = ((x >= bpp ? line[x - bpp] : 0) + prev[x]) >> 1\n          line[x] = (line[x] + avg) & 0xff`,
    to: `          line[x] = (line[x] + (x >= bpp ? line[x - bpp] : 0) + prev[x]) >> 1 & 0xff`,
  },
  // 另一种"少写括号"的写法（把括号挪到 avg 外面）
  shifted: {
    from: `          const avg = ((x >= bpp ? line[x - bpp] : 0) + prev[x]) >> 1\n          line[x] = (line[x] + avg) & 0xff`,
    to: `          const avg = (x >= bpp ? line[x - bpp] : 0) + prev[x] >> 1\n          line[x] = (line[x] + avg) & 0xff`,
  },
}

const KATA = path.join(__dirname, 'evidence', 'kat-filter0.png')
const KATB = path.join(__dirname, 'evidence', 'kat-filter3.png')
const RAD_E = path.join(__dirname, '..', '__snapshots__', 'sensitivity.spec.ts', 'admin-dashboard-light-radius-probe.png')
const RAD_C = path.join(__dirname, 'evidence', 'admin-dashboard-light-radius-probe-captured.png')

function run(file, a, b) {
  const out = execFileSync(process.execPath, [file, a, b, '--json', '--threshold=0', '--ratio=0'], { encoding: 'utf8' })
  return JSON.parse(out)
}

function show(tag, r) {
  console.log(`${tag}: byteDiffPx=${r.byteDiffPx} rgbByteDiffPx=${r.rgbByteDiffPx} count=${r.pixelmatchCount}`)
}

console.log('=== 对照：好尺 ===')
show('KAT  filter0 vs filter3（同一张图两种编码，应 0）', run(SRC, KATA, KATB))
show('radius 基线 vs 取证（应 1394/1983）', run(SRC, RAD_E, RAD_C))

for (const [name, v] of Object.entries(variants)) {
  if (!src.includes(v.from)) {
    console.log(`\n=== 变体 ${name}: MARKER_NOT_FOUND（源码形状变了，本取证作废）===`)
    continue
  }
  fs.writeFileSync(BROKEN, src.replace(v.from, v.to))
  console.log(`\n=== 变体 ${name}（坏尺）===`)
  try {
    show('KAT  filter0 vs filter3', run(BROKEN, KATA, KATB))
    show('radius 基线 vs 取证', run(BROKEN, RAD_E, RAD_C))
  } catch (e) {
    console.log('坏尺报错：', String(e.message).split('\n')[0])
  }
}
fs.rmSync(BROKEN, { force: true })
console.log('\n(坏尺副本已删)')
