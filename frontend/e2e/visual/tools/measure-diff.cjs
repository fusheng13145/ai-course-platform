#!/usr/bin/env node
/**
 * 只读比较器（Task 4c Step 1g）。
 *
 * 【它存在的唯一理由】交付代码里原本**没有任何一处**产出 LEDGER/spec 引用过的那些结论数：
 * 探针只 console.log 免检额、既不读 diff 计数也算不出最大单通道色差，而算过它们的一次性脚本
 * `frontend/probe-analysis.cjs` / `frontend/probe-harness/` 被当临时产物删除且从未入库
 * ⇒ spec §6.15 要求 Phase 1 每个单元报「pixelmatch 计数 + 最大单通道色差」当时是空头条款。
 * 本文件把那把尺重新入库：**注释与 LEDGER 里每一个像素数都必须能由这里的一条命令重生成。**
 *
 * 【它是"只读比较器"，不是采集器】不参与采集、不截图、不起浏览器、不写任何被比对的图。
 * 输入永远是两张已经在盘上的 PNG（已提交基线 / 探针落盘的取证图）。
 *
 * 【尺的出处】pixelmatch 不重写、不引依赖（Phase 0 零新依赖），而是**从当前安装的
 * playwright-core 里取那一份**——Playwright 的比对就是拿它做的
 * （coreBundle.js 里 `comparator === 'pixelmatch'` 那支只传 `{ threshold }`，
 * 于是 includeAA 取默认 false、alpha 取默认 0.1）。自己实现一份"看起来一样"的判据
 * 必然与跑测器漂开，届时注释里的数就不是门禁里的数。
 * 取法与自检：见 loadPixelmatch() 与 loadPlaywrightPng() 的注释。
 *
 * 【--selfcheck 到底在验什么（2026-10-19 改过——原文是一句做不到的承诺）】
 *   旧版本这里写着"拿一对已知图与 Playwright 自己报出的计数核对，不匹配即退出码 3"，
 *   而实现只打印就 return，**全文件没有任何一条路径能给出退出码 3**。
 *   那句承诺还有个更硬的前提错误：`toHaveScreenshot` 只比**同名**的 expected/actual，
 *   所以"Playwright 对 login-light vs login-dark 这一对报过的计数"从来不存在；
 *   盘上确实有 Playwright 自报数的记录（`tools/kat-falsification.cjs` 头的 1,394、
 *   `helpers.spec.ts` 的同一格），但那一对图**全是 git-ignored 的探针产物**，
 *   重建要先跑一次带浏览器的探针 ⇒ 入库产物里没有"可重建且带 Playwright 报数"的图对。
 *   照原承诺做 (a) 只能编一个"已知好数"当参照，那正是本轮要拆掉的假信心，所以不编。
 *
 *   现在改成有牙齿、且参照可真的执行的那一件事：**与 Playwright 比对时实际跑的那两个组件对账**。
 *   Playwright 的 `compareImages()` 只用到两样东西（coreBundle.js 逐字核实）：
 *     ① 解码 `PNG.sync.read(buffer)` —— `coreBundle.js:7873`，`PNG` 来自
 *        `require("./utilsBundle")`（`coreBundle.js:7963`）；
 *     ② 计数 `import_pixelmatch.default(expected.data, actual.data, …, { threshold: options.threshold ?? 0.2 })`
 *        —— `coreBundle.js:7896-7898`（只传 threshold ⇒ includeAA=false、alpha 默认 0.1）。
 *   `--selfcheck` 拿**同一对已提交基线**跑两条路径：本工具的 decodePng vs Playwright 那份 pngjs
 *   （逐字节比 RGBA），以及本工具的计数 vs "那份 pngjs 解码 + 那份 pixelmatch"的计数。
 *   **任一不符 ⇒ 退出码 3。** 参照不是某个历史数字，是跑测器用的解码器与分类器本身。
 *
 * 【三道守卫的分工——别把它们当同一件事】
 *   · `--selfcheck`（本文件）：离线、不要浏览器，只守"尺的输入端与 Playwright 同源"（PNG 解码那条路径）。
 *     helpers.spec.ts 的 filter0/filter3 KAT 抓的就是这一类的现行 bug（Average 过滤少一层括号
 *     ⇒ radius 计数从跑测器的 1,394 漂到 2,257）。
 *   · `sensitivity.spec.ts` 的 `[探针无效] 两条路径判定分叉` 与 `尺不同源`：**日常**那道门，
 *     每次探针都拿真跑测器真报出的计数与真判据对账，覆盖整条 pipeline（含 ratio/判据分支）。
 *   · `sensitivity.spec.ts` 的 `[量程结论] …（成功测量不是回归失败）`：极性与回归相反，FAILED 才是检出了。
 *   前一条不能替后一条：自检不需要浏览器，所以它证明不了 Playwright 的判据分支；
 *   探针那条要后端与 dev server，所以它不是"随手能跑的验尺命令"。
 *
 * 【口径纪律】所有读数都绑定一组 (threshold, maxDiffPixelRatio)。默认**读交付配置**
 * （从 visual-helpers.ts 的 SNAPSHOT_THRESHOLDS 现取，不复制字面量）。
 * 一旦用 --threshold/--ratio 指了别的口径，输出会带 `CONFIG: 非交付口径`，
 * 且 sensitivity.spec.ts 在 env 旋钮被设置时直接 fail fast，拒绝把那次读数当交付口径。
 *
 * 【措辞纪律（spec §6.16）】"byte-identical / 一字不差"只允许在 md5 相等时说；
 * 本工具因此同时给 md5、字节级差异像素数（byteDiffPx）与分类器计数（pixelmatchCount），
 * 三者谁也不能替谁说"相同"。
 *
 * 用法：
 *   node e2e/visual/tools/measure-diff.cjs <expected.png> <actual.png> [--threshold=t] [--ratio=r] [--json] [--bands]
 *   node e2e/visual/tools/measure-diff.cjs --table <capturesDir> <baselineDir> [--bands]
 *   node e2e/visual/tools/measure-diff.cjs --manifest <dir>
 *   node e2e/visual/tools/measure-diff.cjs --selfcheck
 *
 * 常用重生成命令（LEDGER/注释里引用读数时把这条一起写上）：
 *   cd frontend && node e2e/visual/tools/measure-diff.cjs \
 *     e2e/visual/__snapshots__/anchors.spec.ts/student-dashboard-light.png \
 *     e2e/visual/tools/evidence/student-dashboard-light-zerodiff-captured.png --bands
 *   （取证图的名字带**数据路径标签**：无标签=交付口径（钉住、交付 t/r），
 *     `@nopin`=关掉钉死真走后端=量的是漂移本身，`@shift+k`=模拟服务端第 +k 天。
 *     同一格的不同轮次从此不共用一个文件名——A2 修的就是这件事。）
 */
'use strict'

const crypto = require('node:crypto')
const fs = require('node:fs')
const path = require('node:path')
const zlib = require('node:zlib')

const HELPERS_TS = path.join(__dirname, '..', 'visual-helpers.ts')
const SNAP_DIR = path.join(__dirname, '..', '__snapshots__', 'anchors.spec.ts')

/* ---------------------------------------------------------------- 交付口径 */

/**
 * 从唯一来源现取交付阈值：visual-helpers.ts 的 `SNAPSHOT_THRESHOLDS`。
 * 这里不复制字面量——复制过的那一份就是 Task 4b 里"飞的过程中被别的代理改掉"的那个常量。
 * 解析失败/形状变了就抛，宁可这条命令红，也不要它静默用一组猜的阈值。
 */
function deliveryThresholds() {
  const src = fs.readFileSync(HELPERS_TS, 'utf8')
  const m = src.match(
    /export const SNAPSHOT_THRESHOLDS\s*=\s*\{\s*threshold:\s*([-\d.e]+)\s*,\s*maxDiffPixelRatio:\s*([-\d.e]+)\s*\}/,
  )
  if (!m) throw new Error(`未能从 ${HELPERS_TS} 解析 SNAPSHOT_THRESHOLDS——它的写法变了，本命令会给出假口径`)
  return { threshold: Number(m[1]), maxDiffPixelRatio: Number(m[2]) }
}

/* ------------------------------------------------------------ PNG 解码（零依赖） */

/**
 * 最小 PNG 解码：够本仓库的截图用（8-bit、非隔行、colorType 0/2/4/6），输出 RGBA Buffer。
 * 为什么不用 pngjs：Phase 0 明令零新依赖，node_modules 里也没有 pngjs（Playwright 把它打包进了 coreBundle）。
 * colorType 2（无 alpha）按 pngjs 的口径补 alpha=255，这样喂给 pixelmatch 的字节与跑测器一致。
 * 其余（隔行、16-bit、调色板）一律明确拒绝而不是猜——猜错的解码会产出一个看似有效的读数。
 */
function decodePng(file) {
  const buf = fs.readFileSync(file)
  if (buf.length < 8 || buf.readUInt32BE(0) !== 0x89504e47) throw new Error(`${file} 不是 PNG`)
  let off = 8
  let ihdr = null
  const idat = []
  while (off + 8 <= buf.length) {
    const len = buf.readUInt32BE(off)
    const type = buf.toString('ascii', off + 4, off + 8)
    const data = buf.subarray(off + 8, off + 8 + len)
    if (type === 'IHDR') {
      ihdr = {
        width: data.readUInt32BE(0),
        height: data.readUInt32BE(4),
        bitDepth: data[8],
        colorType: data[9],
        interlace: data[12],
      }
    } else if (type === 'IDAT') idat.push(Buffer.from(data))
    else if (type === 'IEND') break
    off += 12 + len
  }
  if (!ihdr) throw new Error(`${file} 缺 IHDR`)
  if (ihdr.interlace !== 0) throw new Error(`${file} 是隔行 PNG，本解码器不支持（Adam7 的读法与 pixelmatch 的邻域判据不同口径）`)
  if (ihdr.bitDepth !== 8) throw new Error(`${file} bitDepth=${ihdr.bitDepth}，只支持 8`)
  const channels = { 0: 1, 2: 3, 4: 2, 6: 4 }[ihdr.colorType]
  if (!channels) throw new Error(`${file} colorType=${ihdr.colorType}（调色板等不支持）`)
  const raw = zlib.inflateSync(Buffer.concat(idat))
  const { width, height } = ihdr
  const bpp = channels
  const stride = width * bpp
  const out = Buffer.alloc(width * height * 4)
  let prev = Buffer.alloc(stride)
  let p = 0
  for (let y = 0; y < height; y++) {
    const filter = raw[p++]
    const line = Buffer.from(raw.subarray(p, p + stride))
    p += stride
    switch (filter) {
      case 0:
        break
      case 1:
        for (let x = bpp; x < stride; x++) line[x] = (line[x] + line[x - bpp]) & 0xff
        break
      case 2:
        for (let x = 0; x < stride; x++) line[x] = (line[x] + prev[x]) & 0xff
        break
      case 3:
        // Average：Rec(x) = Filt(x) + floor((Rec(a) + Rec(b)) / 2)
        // ⚠️ 括号必须写明——`a + b >> 1` 在 JS 里是 `(a + b) >> 1` 之外的另一种结合（+ 比 >> 结合更紧，
        // 少写括号就会算成 `(Filt + (a + b)) >> 1`）。本机就是这样把这一支写错的，
        // 后果是**凡 Chromium 用了 Average 过滤的那些行全部解错**，
        // 与跑测器（pngjs）的计数从 1394 漂到 2257——由 sensitivity.spec.ts 的"尺不同源"断言抓到。
        // 回归测试：helpers.spec.ts 的"比较器与 pngjs 同尺"一条（同一张图的 filter0/filter3 两种编码必须解成同一样）。
        for (let x = 0; x < stride; x++) {
          const avg = ((x >= bpp ? line[x - bpp] : 0) + prev[x]) >> 1
          line[x] = (line[x] + avg) & 0xff
        }
        break
      case 4:
        for (let x = 0; x < stride; x++) {
          const a = x >= bpp ? line[x - bpp] : 0
          const b = prev[x]
          const c = x >= bpp ? prev[x - bpp] : 0
          const pp = a + b - c
          const pa = Math.abs(pp - a)
          const pb = Math.abs(pp - b)
          const pc = Math.abs(pp - c)
          line[x] = (line[x] + (pa <= pb && pa <= pc ? a : pb <= pc ? b : c)) & 0xff
        }
        break
      default:
        throw new Error(`${file} 第 ${y} 行的 filter type=${filter}，非法`)
    }
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 4
      const s = x * bpp
      if (channels === 4) {
        out[i] = line[s]
        out[i + 1] = line[s + 1]
        out[i + 2] = line[s + 2]
        out[i + 3] = line[s + 3]
      } else if (channels === 3) {
        out[i] = line[s]
        out[i + 1] = line[s + 1]
        out[i + 2] = line[s + 2]
        out[i + 3] = 255
      } else if (channels === 2) {
        out[i] = line[s]
        out[i + 1] = line[s]
        out[i + 2] = line[s]
        out[i + 3] = line[s + 1]
      } else {
        out[i] = line[s]
        out[i + 1] = line[s]
        out[i + 2] = line[s]
        out[i + 3] = 255
      }
    }
    prev = line
  }
  return { width, height, data: out }
}

/* ----------------------------------------------- 从 playwright-core 取那一份 pixelmatch */

/**
 * 定位当前安装的 playwright-core 的打包体。
 * 不用 require.resolve('playwright-core/lib/coreBundle.js')：该包的 `exports` 白名单不含这个子路径，
 * 直接 resolve 会抛 `Package subpath './lib/coreBundle.js' is not defined by "exports"`（本机实测）。
 * 这里只**读文件**、不 require 它，所以逐级向上找 node_modules 是正确且够用的做法。
 */
function findCoreBundle() {
  for (let dir = __dirname; ; dir = path.dirname(dir)) {
    const cand = path.join(dir, 'node_modules', 'playwright-core', 'lib', 'coreBundle.js')
    if (fs.existsSync(cand)) return cand
    const parent = path.dirname(dir)
    if (parent === dir) break
  }
  throw new Error('找不到 playwright-core/lib/coreBundle.js——尺的来源没了，读数一律不可引用')
}

/**
 * 取法：在 coreBundle.js 里定位 `var require_pixelmatch = __commonJS({ ... })` 整块
 * （esbuild 把顶层模块的收尾打在**第 0 列**的 `});`，块内没有第 0 列的右括号，
 * 所以"从标记起找第一个第 0 列的 });"是确定边界），连同 esbuild 的 `__commonJS` 垫片一起 new Function 求值。
 * 垫片按 bundle 第 16 行那份**逐字**抄（少一个逗号语义就变）。
 * 之所以不锁行号：node_modules 不归本项目改，但同一版本内行号也可能因打包变动；
 * 定位靠标记文本，找不到就抛——这条命令宁可红也不给假读数。
 */
function loadPixelmatch() {
  const bundle = findCoreBundle()
  const text = fs.readFileSync(bundle, 'utf8')
  const marker = 'var require_pixelmatch = __commonJS({'
  const start = text.indexOf(marker)
  if (start < 0) throw new Error(`${bundle} 里找不到 require_pixelmatch——Playwright 版本变了，尺的来源要重新核`)
  const end = text.indexOf('\n});', start)
  if (end < 0) throw new Error('require_pixelmatch 块的收尾没找到')
  const block = text.slice(start, end + 4)
  const shim =
    'var __getOwnPropNames = Object.getOwnPropertyNames;' +
    'var __commonJS = (cb, mod) => function __require() {' +
    ' return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports; };'
  // eslint-disable-next-line no-new-func
  return new Function(`${shim}\n${block}\nreturn require_pixelmatch();`)()
}

/**
 * 取 **Playwright 比对时真正用的那份 PNG 解码器**（pngjs 的 `PNG.sync.read`）。
 * 出处不是文档转述：coreBundle.js 的 `compareImages()` 里 `decode()` 对 image/png 就是
 * `PNG.sync.read(buffer)`（`:7873`），而那个 `PNG` 是 `({ PNG } = require("./utilsBundle"))`（`:7963`）。
 * 所以 utilsBundle 里的 PNG 与跑测器解出来的像素是同一份实现——这才是"参照"，
 * 而不是某个注释里抄来的历史数字。
 * 为什么可以 require 它而 coreBundle 不行：package 的 `exports` 白名单只约束**裸包名/子路径说明符**，
 * 按**绝对文件路径**require 不走 exports 解析（本机实测：`PNG? function`）。
 * 取不到就抛——自检宁可红，也不要拿一份猜的解码器当参照。
 */
function loadPlaywrightPng() {
  for (let dir = __dirname; ; dir = path.dirname(dir)) {
    const cand = path.join(dir, 'node_modules', 'playwright-core', 'lib', 'utilsBundle.js')
    if (fs.existsSync(cand)) {
      const ub = require(cand)
      if (!ub || typeof ub.PNG !== 'function' || typeof ub.PNG.sync?.read !== 'function') {
        throw new Error(`${cand} 里没有 PNG.sync.read——Playwright 版本变了，解码参照要重新核`)
      }
      return ub.PNG
    }
    const parent = path.dirname(dir)
    if (parent === dir) break
  }
  throw new Error('找不到 playwright-core/lib/utilsBundle.js——解码参照没了，--selfcheck 无从对账')
}

/** 用跑测器那份 pngjs 解一张图（与 coreBundle.js:7873 同一调用式）。 */
function decodeWithPlaywrightPng(file) {
  const PNG = loadPlaywrightPng()
  const img = PNG.sync.read(fs.readFileSync(file))
  return { width: img.width, height: img.height, data: img.data }
}

/* --------------------------------------------------------------------- 度量 */

/**
 * 一次比较产出的全部结论数。每个数都自带口径：
 * - byteDiffPx：RGBA 任一通道不等的像素数（**这就是"字节级"差异**，与 md5 是否相等是两件事）
 * - rgbByteDiffPx：只看 RGB 三通道（给"差异落在哪一带"这类几何归因用，与 alpha 无关）
 * - pixelmatchCount：跑测器同一把尺的计数（分类器判"不同"且非 AA）
 * - eatenByAA = byteDiffPx − pixelmatchCount 只在 threshold=0 时才叫"被抗锯齿分类器吃掉"，
 *   在 t>0 时这个差值混着"低于逐像素闸"的部分，所以输出里分开标，不加合。
 * - maxChannelDelta：单通道最大绝对差（spec §6.15 要的那个数）
 */
function measure(expectedFile, actualFile, opts) {
  const a = decodePng(expectedFile)
  const b = decodePng(actualFile)
  if (a.width !== b.width || a.height !== b.height) {
    throw new Error(
      `尺寸不符：expected ${a.width}x${a.height} vs actual ${b.width}x${b.height}。` +
        `Playwright 走的是补边（padImageToSize）后再比，本工具不模拟补边——先修可比性再来读数。`,
    )
  }
  const w = a.width
  const h = a.height
  const len = w * h
  const pixelmatch = loadPixelmatch()
  const out = Buffer.alloc(len * 4)
  const pixelmatchCount = pixelmatch(a.data, b.data, out, w, h, { threshold: opts.threshold })

  let byteDiffPx = 0
  let rgbByteDiffPx = 0
  let maxChannelDelta = 0
  let maxAt = null
  let sumDelta = 0
  const rowHits = new Int32Array(h)
  let minX = w
  let minY = h
  let maxX = -1
  let maxY = -1
  for (let y = 0; y < h; y++) {
    for (let x = 0; x < w; x++) {
      const i = (y * w + x) * 4
      const dr = Math.abs(a.data[i] - b.data[i])
      const dg = Math.abs(a.data[i + 1] - b.data[i + 1])
      const db = Math.abs(a.data[i + 2] - b.data[i + 2])
      const da = Math.abs(a.data[i + 3] - b.data[i + 3])
      const rgb = Math.max(dr, dg, db)
      if (rgb > 0) {
        rgbByteDiffPx++
        byteDiffPx += rgb > 0 || da > 0 ? 1 : 0
        rowHits[y]++
        if (x < minX) minX = x
        if (x > maxX) maxX = x
        if (y < minY) minY = y
        if (y > maxY) maxY = y
      } else if (da > 0) byteDiffPx++
      if (rgb > maxChannelDelta) {
        maxChannelDelta = rgb
        maxAt = { x, y, expected: [a.data[i], a.data[i + 1], a.data[i + 2]], actual: [b.data[i], b.data[i + 1], b.data[i + 2]] }
      }
      sumDelta += rgb
    }
  }
  const allowancePx = w * h * opts.maxDiffPixelRatio
  const md5A = crypto.createHash('md5').update(fs.readFileSync(expectedFile)).digest('hex')
  const md5B = crypto.createHash('md5').update(fs.readFileSync(actualFile)).digest('hex')
  // Playwright 的判据：count > maxDiffPixels（maxDiffPixels = ceil 前的 w*h*ratio），见 coreBundle 的 pixelsMismatchError
  const failed = pixelmatchCount > allowancePx
  const bands = []
  if (opts.bands) {
    let start = -1
    for (let y = 0; y < h; y++) {
      if (rowHits[y] > 0 && start < 0) start = y
      if (rowHits[y] === 0 && start >= 0) {
        bands.push({ from: start, to: y - 1, rows: y - start, px: rowHits.slice(start, y).reduce((s, v) => s + v, 0) })
        start = -1
      }
    }
    if (start >= 0) bands.push({ from: start, to: h - 1, rows: h - start, px: rowHits.slice(start, h).reduce((s, v) => s + v, 0) })
  }
  return {
    expected: expectedFile,
    actual: actualFile,
    width: w,
    height: h,
    imagePixels: len,
    md5: { expected: md5A, actual: md5B, equal: md5A === md5B },
    byteDiffPx,
    rgbByteDiffPx,
    pixelmatchCount,
    allowancePx,
    failed,
    maxChannelDelta,
    maxChannelDeltaAt: maxAt,
    meanDeltaOverRgbDiffPx: rgbByteDiffPx ? +(sumDelta / rgbByteDiffPx).toFixed(3) : 0,
    bbox: maxX < 0 ? null : { minX, minY, maxX, maxY, w: maxX - minX + 1, h: maxY - minY + 1 },
    threshold: opts.threshold,
    maxDiffPixelRatio: opts.maxDiffPixelRatio,
    bands,
  }
}

/* ------------------------------------------------------------------ CLI 装配 */

/**
 * `--selfcheck` 的实现（唯一会给出退出码 3 的地方；3 = 尺与跑测器不同源）。
 * 参照是 **Playwright 比对时真跑的那两个组件**：pngjs 的 `PNG.sync.read`（coreBundle.js:7873/7963）
 * 与 bundle 里那份 pixelmatch（:7896-7898）。不是某个抄进注释的历史数字——那种参照一旦那轮配置变了就作废。
 * 输入用**已提交的两张基线** ⇒ 不需要浏览器与后端，随时能跑（这是它相对探针 ①② 的唯一优势，
 * 也是它只能覆盖解码这一段的代价，见文件头"三道守卫的分工"）。
 * 两步对账：
 *   ① 逐字节：本工具 decodePng 的 RGBA == Playwright 那份 pngjs 的 RGBA（尺寸也得相等）；
 *   ② 计数：本工具 pixelmatch 计数 == 用 pngjs 的像素喂同一份 pixelmatch 得到的计数。
 */
function selfcheck(opts) {
  const pair = ['login-light.png', 'login-dark.png'].map((f) => path.join(SNAP_DIR, f))
  const problems = []
  const pixelmatch = loadPixelmatch()
  const refPixels = []

  for (const f of pair) {
    const ours = decodePng(f)
    const ref = decodeWithPlaywrightPng(f)
    refPixels.push(ref)
    if (ours.width !== ref.width || ours.height !== ref.height) {
      problems.push(`${path.basename(f)}：尺寸 ${ours.width}x${ours.height} ≠ pngjs 的 ${ref.width}x${ref.height}`)
      continue
    }
    let diffAt = -1
    let diffCount = 0
    for (let i = 0; i < ours.data.length; i++) {
      if (ours.data[i] !== ref.data[i]) {
        diffCount++
        if (diffAt < 0) diffAt = i
      }
    }
    if (diffAt >= 0) {
      problems.push(
        `${path.basename(f)}：本工具与 pngjs 解出 ${diffCount} 个字节不同（首个 @offset ${diffAt}，` +
          `本工具 ${ours.data[diffAt]} vs pngjs ${ref.data[diffAt]}）——unfilter 那一支有 bug`,
      )
    }
  }

  if (refPixels[0].width === refPixels[1].width && refPixels[0].height === refPixels[1].height) {
    const { width, height } = refPixels[0]
    const refCount = pixelmatch(
      refPixels[0].data,
      refPixels[1].data,
      Buffer.alloc(width * height * 4),
      width,
      height,
      { threshold: opts.threshold },
    )
    const mine = measure(pair[0], pair[1], opts)
    console.log(fmt({ ...mine, deliveryConfig: opts.deliveryConfig }))
    console.log(
      `对账 解码=PNG.sync.read(playwright-core/lib/utilsBundle.js) 计数=coreBundle 里的 require_pixelmatch · ` +
        `pngjs 路径计数 ${refCount} · 本工具计数 ${mine.pixelmatchCount}`,
    )
    if (refCount !== mine.pixelmatchCount) {
      problems.push(`计数不同源：pngjs 喂进去的像素得到 ${refCount}，本工具得到 ${mine.pixelmatchCount}`)
    }
  } else {
    problems.push('两张图尺寸不一致，无法按 Playwright 的路径复算计数（本工具本来也会在这里抛）')
  }

  if (problems.length > 0) {
    console.error('SELFCHECK: **尺与跑测器不同源** —— 本轮所有像素读数一律不可引用：\n  ' + problems.join('\n  '))
    process.exitCode = 3
    return
  }
  console.log(
    'SELFCHECK: 通过——解码与 Playwright 用的那份 pngjs 逐字节相同，计数与"那份 pngjs + bundle 那份 pixelmatch' +
      '（即跑测器 compareImages 的全部输入）"相等。免检额/ratio 那一段不在本命令覆盖内，由 sensitivity.spec.ts 的' +
      ' `[探针无效] 两条路径判定分叉` 与 `尺不同源` 在日常跑测里对账。',
  )
}

function parseArgv(argv) {
  const opts = { bands: false, json: false }
  const positional = []
  // 三个模式开关本身占一个"位置"，故不能当未知开关拒掉
  const MODES = ['--selfcheck', '--manifest', '--table']
  for (const a of argv) {
    if (a === '--bands') opts.bands = true
    else if (a === '--json') opts.json = true
    else if (a.startsWith('--threshold=')) opts.threshold = Number(a.slice(12))
    else if (a.startsWith('--ratio=')) opts.maxDiffPixelRatio = Number(a.slice(8))
    else if (MODES.includes(a) || !a.startsWith('--')) positional.push(a)
    else throw new Error(`未知开关 ${a}`)
  }
  if (opts.threshold === undefined || opts.maxDiffPixelRatio === undefined) {
    const d = deliveryThresholds()
    opts.threshold = d.threshold
    opts.maxDiffPixelRatio = d.maxDiffPixelRatio
    opts.delivery = true
  }
  return { opts, positional }
}

function fmt(r) {
  const lines = []
  const tag = r.deliveryConfig ? '交付口径' : '非交付口径（显式指定）'
  lines.push(`CONFIG t=${r.threshold} r=${r.maxDiffPixelRatio} · ${tag}`)
  lines.push(`尺寸 ${r.width}x${r.height} = ${r.imagePixels} px · 免检额 ${r.allowancePx.toFixed(2)} px`)
  lines.push(`md5 ${r.md5.expected} vs ${r.md5.actual} → ${r.md5.equal ? '相等（可写"一字不差"）' : '不等（禁止写"一字不差"，spec §6.16）'}`)
  lines.push(`byteDiffPx ${r.byteDiffPx} · rgbByteDiffPx ${r.rgbByteDiffPx} · pixelmatchCount ${r.pixelmatchCount}`)
  lines.push(`分类器吸收 ${r.byteDiffPx - r.pixelmatchCount} px（仅在 t=0 时才等于"被 AA 吃掉"）`)
  lines.push(`maxChannelDelta ${r.maxChannelDelta}/255 @ ${r.maxChannelDeltaAt ? `${r.maxChannelDeltaAt.x},${r.maxChannelDeltaAt.y} ${JSON.stringify(r.maxChannelDeltaAt.expected)}→${JSON.stringify(r.maxChannelDeltaAt.actual)}` : '—'}`)
  lines.push(`判定 ${r.failed ? `FAIL（${r.pixelmatchCount} > ${r.allowancePx.toFixed(2)}）` : `PASS（${r.pixelmatchCount} ≤ ${r.allowancePx.toFixed(2)}）`}`)
  if (r.bbox) lines.push(`差异包围盒 x ${r.bbox.minX}..${r.bbox.maxX} · y ${r.bbox.minY}..${r.bbox.maxY}（${r.bbox.w}x${r.bbox.h}）`)
  for (const b of r.bands) lines.push(`  带 y ${b.from}..${b.to}（${b.rows} 行）= ${b.px} px`)
  return lines.join('\n')
}

function main() {
  const argv = process.argv.slice(2)
  const { opts, positional } = parseArgv(argv)
  opts.deliveryConfig = opts.delivery === true

  if (positional[0] === '--manifest') {
    const dir = positional[1]
    const rows = fs
      .readdirSync(dir)
      .filter((f) => f.toLowerCase().endsWith('.png'))
      .sort()
      .map((f) => ({ file: f, sha256: crypto.createHash('sha256').update(fs.readFileSync(path.join(dir, f))).digest('hex') }))
    if (opts.json) return console.log(JSON.stringify(rows, null, 2))
    console.log(`# ${rows.length} 张基线的篡改痕迹（sha256）· ${dir}`)
    for (const r of rows) console.log(`${r.sha256}  ${r.file}`)
    console.log(
      `# 重生成这条清单：cd frontend && node e2e/visual/tools/measure-diff.cjs --manifest e2e/visual/__snapshots__/anchors.spec.ts --json`,
    )
    return
  }

  if (positional[0] === '--selfcheck') {
    selfcheck(opts)
    return
  }

  if (positional[0] === '--table') {
    const capturesDir = positional[1]
    const baselineDir = positional[2] || SNAP_DIR
    const files = fs.readdirSync(capturesDir).filter((f) => f.toLowerCase().endsWith('.png')).sort()
    const rows = []
    console.log(
      '# 每一行都是「这张取证图 vs 它自己那张已提交基线」。文件名尾的 `@…` 是**数据路径标签**（无 = 交付口径：' +
        '钉住载荷 + 交付 t/r）；`@nopin` = 关掉钉死真走后端，量的是**漂移本身**而不是残余噪声，' +
        '`@shift+k` = 模拟服务端第 +k 天。同一格的不同轮次各占一个文件名，**不再互相覆盖**（A2）。',
    )
    for (const f of files) {
      // 取证图叫 `<slug>-<theme>[-<档>-probe[-captured]|-zerodiff-captured][@标签].png`，
      // 基线叫 `<slug>-<theme>.png`：按前者截出 slug+theme 再回基线目录找同名图，
      // 找不到就跳过（不是错误——目录里混着探针产物）。标签段落在 `-captured` 之后，
      // 所以下面这条"到 .png 之前允许多个 -或. 起头的小段"的正则一并吃得住它。
      const m = f.match(/^(.+-(?:light|dark))(?:[.-].*)?\.png$/)
      if (!m) {
        console.log(`SKIP ${f}：文件名截不出 <slug>-<theme>，无从配对`)
        continue
      }
      const base = path.join(baselineDir, `${m[1]}.png`)
      if (!fs.existsSync(base)) {
        console.log(`SKIP ${f}：基线目录没有 ${path.basename(base)}，无从对比`)
        continue
      }
      try {
        rows.push(measure(base, path.join(capturesDir, f), opts))
      } catch (e) {
        console.log(`ERR  ${f}：${e.message}`)
      }
    }
    if (opts.json) return console.log(JSON.stringify(rows, null, 2))
    console.log(
      `CONFIG t=${opts.threshold} r=${opts.maxDiffPixelRatio} · ${opts.deliveryConfig ? '交付口径' : '非交付口径（显式指定）'} · 免检额按各图自身尺寸算`,
    )
    for (const r of rows) {
      console.log(
        `${path.basename(r.actual).padEnd(58)} 计数 ${String(r.pixelmatchCount).padStart(7)} · 字节差 ${String(r.byteDiffPx).padStart(7)}` +
          ` · Δmax ${String(r.maxChannelDelta).padStart(3)}/255 · 免检 ${r.allowancePx.toFixed(0)} · ${r.failed ? '红' : '绿'} · md5 ${r.md5.equal ? '相同' : '不同'}` +
          (r.bbox ? ` · 包围盒 y ${r.bbox.minY}..${r.bbox.maxY}` : ''),
      )
    }
    return
  }

  if (positional.length !== 2) {
    throw new Error(
      '用法：measure-diff.cjs <expected.png> <actual.png> [--bands] [--json] [--threshold=t] [--ratio=r]\n' +
        '     measure-diff.cjs --table <capturesDir> [<baselineDir>]   逐张对比取证图与基线\n' +
        '     measure-diff.cjs --manifest <dir>                        基线 sha256 清单（篡改痕迹）\n' +
        '     measure-diff.cjs --selfcheck                             尺与跑测器是否同源（不同源 → 退出码 3）',
    )
  }
  const r = measure(positional[0], positional[1], opts)
  console.log(opts.json ? JSON.stringify(r, null, 2) : fmt({ ...r, deliveryConfig: opts.deliveryConfig }))
  if (r.bands.length) console.log(`带数 ${r.bands.length}`)
}

// 只有被当命令跑时才执行 main()：被 require 进来（helpers.spec.ts 的解码回归）不该顺带跑一次 CLI
if (require.main === module) {
  try {
    main()
  } catch (e) {
    console.error(`measure-diff 失败：${e && e.message ? e.message : e}`)
    process.exitCode = e && e.exitCode ? e.exitCode : 2
  }
}

module.exports = { measure, decodePng, loadPixelmatch, deliveryThresholds }
