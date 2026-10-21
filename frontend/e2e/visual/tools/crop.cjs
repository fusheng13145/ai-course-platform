#!/usr/bin/env node
/**
 * 只读裁图（Task 0 归因用；修复轮 1 的 I-3② 把它从 gitignore 目录挪进入库面）。
 * 从一张 PNG 里裁出一块写成新 PNG——用来把"某条窄文本带到底画了什么数字"变成肉眼可核的图，
 * 而不是靠像素计数猜。
 * 解码器取 **Playwright 比对时真正用的那一份**（playwright-core/lib/utilsBundle.js 的 PNG），
 * 与已入库比较器 `e2e/visual/tools/measure-diff.cjs` 的 loadPlaywrightPng() 同源，不另造尺。
 * 用法（工作目录 `frontend/`）：
 *   `node e2e/visual/tools/crop.cjs <in.png> <out.png> <x> <y> <w> <h>`
 * Task 0 报告步骤 3b 那两条读数就是这么来的（未读角标 30/39 对 42）。
 */
const fs = require('node:fs')
const path = require('node:path')

function loadPng() {
  for (let dir = __dirname; ; dir = path.dirname(dir)) {
    const cand = path.join(dir, 'node_modules', 'playwright-core', 'lib', 'utilsBundle.js')
    if (fs.existsSync(cand)) {
      const ub = require(cand)
      if (typeof ub?.PNG?.sync?.read !== 'function') throw new Error(`${cand} 里没有 PNG.sync.read`)
      return ub.PNG
    }
    const parent = path.dirname(dir)
    if (parent === dir) break
  }
  throw new Error('找不到 playwright-core/lib/utilsBundle.js')
}

const PNG = loadPng()
const [inFile, outFile, xs, ys, ws, hs] = process.argv.slice(2)
const [x, y, w, h] = [xs, ys, ws, hs].map(Number)

const img = PNG.sync.read(fs.readFileSync(inFile))
const out = new PNG({ width: w, height: h })
for (let row = 0; row < h; row++) {
  for (let col = 0; col < w; col++) {
    const src = ((y + row) * img.width + (x + col)) << 2
    const dst = (row * w + col) << 2
    out.data[dst] = img.data[src]
    out.data[dst + 1] = img.data[src + 1]
    out.data[dst + 2] = img.data[src + 2]
    out.data[dst + 3] = img.data[src + 3]
  }
}
fs.writeFileSync(outFile, PNG.sync.write(out))
console.log(`${inFile} ${img.width}x${img.height} -> 裁 ${w}x${h} @(${x},${y}) -> ${outFile}`)
