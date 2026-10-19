import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import {
  ANCHORS,
  assertComparableRender,
  freezeClock,
  installPayloadPin,
  maskOf,
  setTheme,
  settlePage,
  SNAPSHOT_THRESHOLDS,
  snapName,
  storagePathFor,
  type AnchorSpec,
  type ThemeName,
} from './visual-helpers'

/**
 * 这不是回归测试，是**量程测量**：故意注入一个已知幅度的视觉改动，看基线是否报警。
 * 报警 = 该幅度上这层防护有效；不报警 = 这个幅度往后 Phase 1-6 做了也看不见。
 * 所以"注入后测试变红"是**成功的测量结果**，不是失败——不要为了让它绿而去放宽阈值。
 * 注入一律用 addStyleTag，不落源文件：改源文件会连带影响 token 的所有消费方，
 * 量出来的就不是"基线能否看见改动"，而是"改动有多大"，是另一个问题。
 *
 * ── 判读口径（四种结局，含义互不相同，别混）────────────────────────────────
 *   红 + `[量程结论] … 检出`      = **检测到了**（好消息；消息里自带计数/字节差/Δmax）
 *   红 + `[探针无效]`             = 测量本身不成立（注入没落地 / 两条路径判据分叉 / 尺寸不符）——
 *                                  这种红**必须修**，它不是量程结论，别往里加阈值
 *   绿 + `[量程结论] … 零效应`    = 注入**不产生任何像素差**（计数 0 且字节差 0、与基线 md5 相等）：
 *                                  收 ratio 完全无用，要改的是注入本身/换证据形式。浅色 `inset` 档就是这一类
 *   绿 + `[量程结论] … 全被抗锯齿分类器吸收` = 计数 0 而字节差 > 0：t=0 下 AA 判别整类跳过，**免检额没参与**，
 *                                  收 ratio 同样无用（这一类的存在本身就是 spec §6.16 说"绿≠一致"的实测形态）
 *   绿 + `[量程结论] … 漏检`      = **假绿**：改动被免检额吸收，这正是 Task 4 图谱基线事故的复现（处置 = 收 ratio）
 *   绿 + `[量程结论] … 地板`      = noise 档（不注入）读数，量的是渲染抖动本身
 *   SKIPPED                       = 该档选择器在这个锚点零命中，注入是空操作，无结论
 *
 * 【Task 4c Step 1g 改了什么，为什么】原来每条 case 只有一个 matcher，而且极性是设计成反的
 * （FAILED = 检出了）⇒ **本套件的绿不代表任何结论**，保护丢了只会让它更绿；免检额只 console.log，
 * diff 计数测试根本没读，"最大单通道色差"没有一行已提交代码在算。
 * 现在每条 case 都真的**读两张 PNG**（已提交基线的逐字节副本 vs 现场取证图），
 * 用**已入库的只读比较器** `tools/measure-diff.cjs` 取数，并断言三件事：
 *   ① `toHaveScreenshot` 的失败/通过 == 比较器算出的 failed（两条路径同判 = Step 1e 的一致性断言）；
 *   ② 失败时报错消息里的 pixelmatch 计数 == 比较器算出的计数（**尺与跑测器同源**，不是"看起来一样"）；
 *   ③ 读数和档名/阈值/ratio/主题一起落 `tools/evidence/probe-results.json`，
 *      LEDGER 引用的每个像素数都由那一条命令重生成。
 *
 * ── 比对口径：为什么期望图是"从 anchors 基线复制过来的"──────────────────────
 * 计划原稿跑 `--update-snapshots` 生成参考、再跑一次比对，两次都是**注入后**的同一张图，
 * 必然绿——那是"探针 vs 探针"，量的是同一张图自己跟自己相等，与量程无关（worthless 的 green）。
 * 本 spec 改成：每次比较前，把该锚点**已提交的未注入基线**（`__snapshots__/anchors.spec.ts/`）
 * 逐字节复制成 `__snapshots__/sensitivity.spec.ts/<slug>-<theme>-<档>-probe.png`，
 * 然后让 toHaveScreenshot 拿"注入后的现场渲染"去比这张"未注入的基线"。
 * 于是比较对象 = 真改动 vs 真基线，用的阈值 = SNAPSHOT_THRESHOLDS（与回归同一组数），
 * 结局直接就是"这层防护对这个形状的改动报不报警"。
 * 复制而不是"把参考指到 anchors 目录"：那样一旦手滑带上 `--update-snapshots`，
 * 22 张已提交基线会被注入图原地覆盖——测量顺手毁掉被测对象，绝不能留这个口子。
 *
 * ── 与 anchors 的口径对齐（少一条就不可比）─────────────────────────────────
 * 主题、fullPage、animations:'disabled'、caret:'hide'、scale:'css'、**同一个 mask**、
 * 同 freezeClock→goto→settlePage 顺序、同一组可比性前置（assertComparableRender，
 * 含时刻常量、data-theme、#app 非空、.error-boundary、/login 重定向、/403|/503、
 * 以及该锚点自己的"数据确已到位"断言）。
 * Step 1g 的"口径共享"就是这一条：上一版探针自称与 anchors.runAnchor 同口径，实际少三条，
 * 于是探针完全可能在"未登录 / 错误页 / 403"上量出一个长得像有效读数的数。
 * 现在少一条就两边一起红，探针不可能在坏画面上出结论。
 */

/**
 * 探针锚点。默认三个是计划口径（都是**无日期漂移**的静态页）。
 * `PW_PROBE_SLUGS=a,b` 可临时换锚点——本轮用它把 `noise` 档打到日期锚点上，
 * 用来分辨两种长得一样的红：**渲染噪声**（同一路径两次截到的图互不相同）与
 * **确定性漂移**（两次截到的图彼此逐字节相同，但都不等于已提交基线 = 产品真的变了）。
 * 判据只有靠双跑：单次读数分不出这两者，而它们的处置方向完全相反。
 */
const PROBE_ANCHORS: string[] =
  process.env.PW_PROBE_SLUGS?.split(',').map((s) => s.trim()).filter(Boolean)
  ?? ['login', 'admin-dashboard', 'admin-courses-table']

/**
 * 探针主题。Step 3 的暗色量程就靠它：**spec §6.13 明令不得用浅色结论代证暗色**，
 * 所以暗色必须能整轮重跑，而不是在注释里断言"暗色也一样"。
 */
const PROBE_THEME: ThemeName = process.env.PW_PROBE_THEME === 'dark' ? 'dark' : 'light'

/**
 * ladder 档。`watch`/`prop`/`settled` 是**空操作守卫**用的：
 * 注入必须真的落到计算样式的终态上，否则"检不出"会被误读成"基线是瞎的"。
 * `settledByTheme` 存在时按主题取片段——注入用的是 token 本身（`var(--surface-2)` 等），
 * 计算值因此随主题变，写死一份会在另一主题下假炸。
 *
 * 档位与 Phase 1/2 的对应关系（每条都标了出处，别把探针幅度当成计划幅度）：
 *   gross       —— 哨兵档：整页 brightness(0.97)，"连这都看不见就全线作废"
 *   shadow      —— **2 倍于计划幅度**（alpha .05/.06 → .12/.13），历史读数都挂在它上
 *   shadow-plan —— 正好是计划幅度：theme.css:52 的 --sh-l1 环境层 0.06 → 0.10（spec §4 Phase 1"接触阴影 1px 高 alpha + 环境阴影 alpha 0.06 → 0.10"）
 *   radius      —— Phase 2 的薄档：--r-l1 16px → 14px（theme.css:46）
 *   inset       —— Phase 1 的内高光：现仓内两处 inset 高光的实际值（design-system.css:41 alpha .25、:282 alpha .18）取 .25
 *   surface     —— Phase 1 的暗色主机制：--surface → --surface-2（浅色 #fff→#f7f8fa，暗色 #171d26→#1c2330）
 *   border      —— 暗色"描边优先"：--line → --line-strong
 *   noise       —— 不注入，同一条路径再截一次去比同一张基线 = **噪声地板**
 * 为什么 inset/surface/border 三档必须存在：Task 4b 只用"t=0 什么都能报"论证过 shadow/gross，
 * 而那个论证只在 pixelmatch 的抗锯齿分类器不吃掉高光边缘时成立——radius 恰恰演示了会被吃。
 * 这三档没量出来之前，Phase 1 不得对"内高光/局部色阶/描边"报零 diff（spec §6.15 ④）。
 */
interface LadderStep {
  /** 明暗各一套时必须按主题取，否则暗色那一轮注入的是**浅色 token 的字面值**，量的就不是暗色的形状了 */
  css: Partial<Record<ThemeName, string>> & { light?: string; dark?: string }
  watch: string
  prop: string
  settled: string[]
  settledByTheme?: Partial<Record<ThemeName, string[]>>
  /** 这一档在测什么形状（进证据文件与失败消息，免得日后只看档名猜） */
  shape: string
}

const cssByTheme = (light: string, dark: string): Partial<Record<ThemeName, string>> => ({ light, dark })
const cssBoth = (both: string): Partial<Record<ThemeName, string>> => ({ light: both, dark: both })

const LADDER: Record<string, LadderStep> = {
  gross: {
    css: cssBoth('body{filter:brightness(0.97)}'),
    watch: 'body',
    prop: 'filter',
    settled: ['brightness(0.97)'],
    shape: '整页明度阶（哨兵）',
  },
  shadow: {
    // 浅色=theme.css 的 --sh-l1 (.05/.06) ×2；暗色=暗色那套 --sh-l1 (.4/.5) 的**同比例**放大。
    // 不能用浅色的 rgba(24,32,48,…) 字面量去打暗色页：那测的是"换成浅色的阴影"，不是"暗色阴影变深"，
    // 而 spec §6.13 禁止用浅色的形状代替暗色下结论。
    css: cssByTheme(
      '.l1-card{box-shadow:0 1px 2px rgba(24,32,48,0.12),0 4px 14px rgba(24,32,48,0.13)!important}',
      '.l1-card{box-shadow:0 1px 2px rgba(0,0,0,0.8),0 4px 14px rgba(0,0,0,1)!important}',
    ),
    watch: '.l1-card',
    prop: 'boxShadow',
    settled: [],
    settledByTheme: {
      light: ['rgba(24, 32, 48, 0.12)', 'rgba(24, 32, 48, 0.13)'],
      // ⚠️ alpha=1 的阴影层被 Chromium **序列化回 `rgb(0, 0, 0)`**（不是 `rgba(0, 0, 0, 1)`）。
      // 本次跑暗色这一档时终态核对就是按后者写的，于是空操作守卫判 `[探针无效]`：
      // 实测计算值 `rgba(0, 0, 0, 0.8) 0px 1px 2px 0px, rgb(0, 0, 0) 0px 4px 14px 0px`。
      // 修的是这条**期望串**（不是把守卫放宽）——守卫本身这次是正确的：它拒绝把没核对过的注入当量程结论。
      dark: ['rgba(0, 0, 0, 0.8)', 'rgb(0, 0, 0)'],
    },
    shape: '卡片环境阴影 ×2（比计划幅度大约 1.3 倍；历史读数都挂在这条，浅色口径）',
  },
  'shadow-plan': {
    // 浅色：theme.css 现值 0 1px 2px rgba(24,32,48,.05), 0 4px 14px rgba(24,32,48,.06)
    //       → 环境层 .06→.10（spec §4 Phase 1"接触阴影 1px 高 alpha + 环境阴影 alpha 0.06 → 0.10"），接触层不动 ⇒ 单变量。
    // 暗色：同一**绝对增量** +0.04 加在环境层上（.5→.54），接触层 .4 同样不动。
    css: cssByTheme(
      '.l1-card{box-shadow:0 1px 2px rgba(24,32,48,0.05),0 4px 14px rgba(24,32,48,0.1)!important}',
      '.l1-card{box-shadow:0 1px 2px rgba(0,0,0,0.4),0 4px 14px rgba(0,0,0,0.54)!important}',
    ),
    watch: '.l1-card',
    prop: 'boxShadow',
    settled: [],
    settledByTheme: {
      light: ['rgba(24, 32, 48, 0.05)', 'rgba(24, 32, 48, 0.1)'],
      dark: ['rgba(0, 0, 0, 0.4)', 'rgba(0, 0, 0, 0.54)'],
    },
    shape: '卡片环境阴影 = Phase 1 计划幅度（浅色 .06→.10；暗色同绝对增量 .5→.54）',
  },
  radius: {
    css: cssBoth('.l1-card{border-radius:14px!important}'),
    watch: '.l1-card',
    prop: 'borderTopLeftRadius',
    settled: ['14px'],
    shape: '圆角 16→14px（Phase 2 薄档，四角弧几何量级）',
  },
  inset: {
    // 保留外层阴影不动，只在末尾追加一条 1px 顶部内高光 ⇒ 内高光是唯一变量。
    // 高光值取自本仓既有两处 inset（design-system.css:41 的 .25、:282 的 .18）。
    // 外层阴影按主题各写一份，否则暗色下这条注入会把暗色阴影换成浅色值（那就不是单变量了）。
    css: cssByTheme(
      '.l1-card{box-shadow:0 1px 2px rgba(24,32,48,0.05),0 4px 14px rgba(24,32,48,0.06),inset 0 1px 0 rgba(255,255,255,0.25)!important}',
      '.l1-card{box-shadow:0 1px 2px rgba(0,0,0,0.4),0 4px 14px rgba(0,0,0,0.5),inset 0 1px 0 rgba(255,255,255,0.25)!important}',
    ),
    watch: '.l1-card',
    prop: 'boxShadow',
    settled: ['rgba(255, 255, 255, 0.25)', 'inset'],
    shape: '1px 顶部内高光（Phase 1 的"抬起感"，长在 AA 边缘上）',
  },
  surface: {
    css: cssBoth('.l1-card{background:var(--surface-2)!important}'),
    watch: '.l1-card',
    prop: 'backgroundColor',
    settled: [],
    settledByTheme: {
      light: ['rgb(247, 248, 250)'], // #f7f8fa
      dark: ['rgb(28, 35, 48)'], // #1c2330
    },
    shape: '局部表面色阶 L1→L2（暗色 Phase 1 的主机制=抬亮）',
  },
  border: {
    css: cssBoth('.l1-card{border-color:var(--line-strong)!important}'),
    watch: '.l1-card',
    prop: 'borderTopColor',
    settled: [],
    settledByTheme: {
      light: ['rgb(194, 201, 212)'], // #c2c9d4
      dark: ['rgb(75, 85, 101)'], // #4b5565
    },
    shape: '1px 描边加深 --line→--line-strong（暗色"描边优先"）',
  },
  /**
   * 第四档不是注入，是**噪声地板对照**：什么都不改，同一条路径再截一次去比同一张基线。
   * 没有它，"红"分不出是改动还是渲染抖动，"绿"也分不出是吸收了改动还是本来就零差——
   * 量程的下限正是这条线：地板有多高，阈值就只能收到比它高多少。
   * Step 3 收紧阈值时每一档都要先看这条：noise 若在某档阈值下变红，说明基线本身混进了噪声，
   * 那要回头查 settlePage/freezeClock，**不是**把阈值放宽回去。
   */
  noise: { css: {}, watch: 'body', prop: 'filter', settled: [], shape: '噪声地板（不注入）' },
}

/**
 * 默认档 = noise（Task 4c Step 1c）。
 * 原来是 `?? 'gross'`：任何**不带过滤**的 visual 跑都天然带 3 条设计好的失败，
 * 而计划里两处给的命令恰恰是不带过滤的 `--update-snapshots`——
 * 后果是①把后来的代理训练成"红了也别管"，②同一条命令重写全部 22 张基线而毫无机械阻止。
 * noise 档不注入、默认绿，"红"从今往后只表示"量出了东西"或"探针坏了"。
 */
const LAD = process.env.PW_LADDER ?? 'noise'
const STEP = LADDER[LAD]
if (!STEP) throw new Error(`PW_LADDER=${LAD} 不在 ladder 里：${Object.keys(LADDER).join('/')}`)
const SETTLING = STEP.settledByTheme?.[PROBE_THEME] ?? STEP.settled
/** 本主题要注入的那一条 CSS；noise 档（或该档在此主题没写）= 空串 = 不注入 */
const INJECT = STEP.css[PROBE_THEME] ?? ''

/**
 * env 旋钮。**只有这几个数被设置时，本轮读数就不是交付口径**——
 * Step 1g 要求"一旦 env 旋钮被设置，就拒绝把这次读数当作交付口径"，落在这里：
 * ① 比交付配置**更宽松**的旋钮直接 fail fast（量程只许往下探，不许往上放；
 *    上一轮 `{0.02,0}` 那个被否决的中间配置就是靠"临时放宽"混进注释的，
 *    同一条注释里因此出现过两个免检额）；
 * ② 允许收紧方向的扫描，但证据文件里 `deliveryScale:false`，
 *    且日志明写"非交付口径"——喂台账前必须换回默认（无旋钮）重跑。
 */
const knobThreshold = process.env.PW_PROBE_THRESHOLD === undefined ? undefined : Number(process.env.PW_PROBE_THRESHOLD)
const knobRatio = process.env.PW_PROBE_RATIO === undefined ? undefined : Number(process.env.PW_PROBE_RATIO)
const THRESHOLDS = {
  threshold: knobThreshold ?? SNAPSHOT_THRESHOLDS.threshold,
  maxDiffPixelRatio: knobRatio ?? SNAPSHOT_THRESHOLDS.maxDiffPixelRatio,
}
/**
 * "交付量程"只由两个阈值旋钮决定——换锚点或换主题**不**改变尺，
 * 暗色那轮因此仍然可以按交付口径引用（spec §6.13 要的正是暗色的交付读数）。
 * 一旦 t/r 被设过，这轮的读数就不是交付口径：证据里写 deliveryScale:false，
 * 且日志明写——喂台账前必须换回默认（无旋钮）重跑。
 */
const IS_DELIVERY_SCALE = knobThreshold === undefined && knobRatio === undefined
if (knobThreshold !== undefined && !(knobThreshold >= 0 && knobThreshold <= SNAPSHOT_THRESHOLDS.threshold)) {
  throw new Error(
    `[探针无效] PW_PROBE_THRESHOLD=${knobThreshold} 比交付配置 threshold=${SNAPSHOT_THRESHOLDS.threshold} 宽松。` +
      '量程只许往更灵敏扫；放宽过的读数会伪装成交付口径（Task 4b 的 {0.02,0} 事故）。',
  )
}
if (knobRatio !== undefined && !(knobRatio >= 0 && knobRatio <= SNAPSHOT_THRESHOLDS.maxDiffPixelRatio)) {
  throw new Error(`[探针无效] PW_PROBE_RATIO=${knobRatio} 比交付配置 maxDiffPixelRatio=${SNAPSHOT_THRESHOLDS.maxDiffPixelRatio} 宽松——同上，拒绝`)
}

/** 读 PNG 头（IHDR）拿宽高，用来把 maxDiffPixelRatio 折算成"免检像素数" */
function pngSize(file: string): { width: number; height: number } {
  const buf = readFileSync(file)
  if (buf.readUInt32BE(0) !== 0x89504e47) throw new Error(`${file} 不是 PNG`)
  return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) }
}

/* -------------------------------------------------- 只读比较器（已入库那把尺） */

const EVIDENCE_DIR = join(process.cwd(), 'e2e', 'visual', 'tools', 'evidence')
const MEASURER = join(process.cwd(), 'e2e', 'visual', 'tools', 'measure-diff.cjs')
const RESULTS = join(EVIDENCE_DIR, 'probe-results.json')

interface MeasureReading {
  width: number
  height: number
  byteDiffPx: number
  rgbByteDiffPx: number
  pixelmatchCount: number
  allowancePx: number
  failed: boolean
  maxChannelDelta: number
  maxChannelDeltaAt: { x: number; y: number } | null
  md5: { expected: string; actual: string; equal: boolean }
  bbox: { minX: number; minY: number; maxX: number; maxY: number } | null
  bands?: { from: number; to: number; rows: number; px: number }[]
  threshold: number
  maxDiffPixelRatio: number
}

function measureWith(expected: string, actual: string, t: number, r: number): MeasureReading {
  // 显式把本轮口径传给比较器：它默认读交付配置，而扫描档时探针用的可能是收紧后的 t/r。
  // 不传就会拿"交付口径的计数"去和"扫描口径的跑测器计数"比，①②两条一致性断言必然假炸。
  const out = execFileSync(process.execPath, [MEASURER, expected, actual, '--json', `--threshold=${t}`, `--ratio=${r}`], {
    encoding: 'utf8',
  })
  return JSON.parse(out) as MeasureReading
}

function readingText(r: MeasureReading): string {
  return (
    `计数 ${r.pixelmatchCount} / 免检额 ${r.allowancePx.toFixed(2)} · 字节差 ${r.byteDiffPx} · ` +
    `Δmax ${r.maxChannelDelta}/255${r.maxChannelDeltaAt ? ` @${r.maxChannelDeltaAt.x},${r.maxChannelDeltaAt.y}` : ''} · ` +
    `md5 ${r.md5.equal ? '相同' : '不同'} · t=${r.threshold} r=${r.maxDiffPixelRatio}` +
    (r.bbox ? ` · 包围盒 x ${r.bbox.minX}..${r.bbox.maxX} y ${r.bbox.minY}..${r.bbox.maxY}` : '')
  )
}

/**
 * 证据写盘的 key。
 * ⚠️ 原来 key 只有 `slug/theme/ladder`，于是**同一格的漂移读数会把钉住读数原地覆盖掉**：
 * `PW_VISUAL_NOPIN=1` 与 `PW_PIN_SHIFT_DAYS=k` 那几轮量的明明是另一件事（漂移本身），
 * 却写进交付口径那一格的 key 里——最后跑的那轮赢，前一轮的读数在盘上消失，只剩控制台日志。
 * 现在把"这一轮到底走的哪条数据路径"编进 key：
 *   `slug/theme/ladder`（钉住 = 交付口径）
 *   `slug/theme/ladder@nopin`（真走后端 = 漂移本身）
 *   `slug/theme/ladder@shift+k`（模拟服务端第 +k 天）
 * 顺带把扫描档的 t/r 也编进去，免得收紧扫描覆盖交付口径那格。
 */
function evidenceKey(slug: string, theme: ThemeName, ladder: string, opts: { nopin: boolean; shift: number; t: number; r: number }): string {
  const tags: string[] = []
  if (opts.nopin) tags.push('nopin')
  if (opts.shift > 0) tags.push(`shift+${opts.shift}`)
  if (Number.isFinite(opts.t) && opts.t !== SNAPSHOT_THRESHOLDS.threshold) tags.push(`t=${opts.t}`)
  if (Number.isFinite(opts.r) && opts.r !== SNAPSHOT_THRESHOLDS.maxDiffPixelRatio) tags.push(`r=${opts.r}`)
  return `${slug}/${theme}/${ladder}${tags.length > 0 ? `@${tags.join(',')}` : ''}`
}

function recordEvidence(entry: Record<string, unknown>): void {
  mkdirSync(EVIDENCE_DIR, { recursive: true })
  let all: Record<string, unknown> = {}
  try {
    all = JSON.parse(readFileSync(RESULTS, 'utf8')) as Record<string, unknown>
  } catch {
    all = {}
  }
  const key = evidenceKey(
    String(entry.slug),
    String(entry.theme) as ThemeName,
    String(entry.ladder),
    {
      nopin: entry.nopin === true,
      shift: Number(entry.pinShiftDays ?? 0),
      t: Number(entry.threshold),
      r: Number(entry.maxDiffPixelRatio),
    },
  )
  all[key] = { ...((all[key] as Record<string, unknown> | undefined) ?? {}), ...entry, at: new Date().toISOString(), key }
  writeFileSync(RESULTS, `${JSON.stringify(all, null, 2)}\n`, 'utf8')
}

/**
 * 空操作守卫用的计算样式读数。
 *
 * 【真基线上量出来的一个坑，非离线自检台所能发现】`.l1-card` 带
 * `transition: box-shadow 0.25s var(--ease-out)`（design-system.css:63）。
 * 本页时钟是被 freezeClock 停住的，注入后过渡**开始了但推进不了**，
 * 于是 getComputedStyle('.l1-card').boxShadow 在注入后仍读回旧值——
 * 第一版守卫正是这样误报"注入被优先级挡掉"（shadow 档两个锚点都炸），
 * 而 radius 档（border-radius 不过渡）却正常。
 *
 * 读数口径要跟截图口径一致才有意义：toHaveScreenshot 用的是 `animations:'disabled'`，
 * 它在截图瞬间把过渡/动画**强制落到终态**（实现见 playwright-core screenshotter：
 * `root.getAnimations()` 里每个有限动画都 `animation.finish()`，并挂 transitionrun/animationstart
 * 监听把截图过程中新起的过渡也收尾）。所以这里在读之前临时压一枚
 * `*{transition:none!important;animation:none!important}`，读到的是"这一帧之后的目标值"，
 * 读完立刻移除——截图仍由 animations:'disabled' 决定，两者从此读同一个值。
 *
 * 【这枚临时 style 标签为什么不会污染被截的图（机制，不是宣称）】
 * add → querySelectorAll/getComputedStyle → remove 全在**同一个同步 JS 任务**里，中间没有任何 await，
 * 而 Blink 的绘制只可能发生在两个任务之间，所以这枚标签**从未进入过任何一帧已提交的画面**；
 * 它被移除时也没有属性值发生变化（只是 transition-property 恢复），因此不会再排一趟新过渡。
 * 这条性质可以单独取证：`PW_PROBE_SKIP_GUARD=1` 时不压这枚标签（同时跳过"未变化"抛错，
 * 因为那时 shadow 档必然读到旧值——那正是本轮踩过的坑），其余流程一字不动，
 * 两次 PW_PROBE_CAPTURE 的产物逐字节对比即是"守卫对被测画面的影响"。
 * 该开关只用于取证，跑出来的读数不作量程结论（守卫没验，注入是否落地未知）。
 *
 * 【Step 1g 更正的一条错话】上一版注释写"noise 档不注入，只读一次"，代码却对**每一档**
 * 都调两次 computedSignatures——于是"噪声地板 0 px"其实是在**锚点套件不走的那条 DOM 变异路径**
 * （装卸 style 标签 ×2）上量的。现在 `INJECT === ''`（noise 档）时一次都不调，地板读数与 anchors 同路径。
 */
const TRANSITION_KILL = !process.env.PW_PROBE_SKIP_GUARD

async function computedSignatures(page: Page, watch: string, prop: string) {
  return page.evaluate(
    ({ w, p, kill }: { w: string; p: string; kill: boolean }) => {
      let matched = 0
      let values: string[] = []
      const kill1 = document.createElement('style')
      kill1.id = 'probe-guard-transitions-off'
      kill1.textContent = '*,*::before,*::after{transition:none!important;animation:none!important}'
      if (kill) document.head.appendChild(kill1)
      try {
        const els = Array.from(document.querySelectorAll(w))
        matched = els.length
        // 只取前 50 个：足够判定"注入生效"，又不必把整页计算样式序列化回来
        values = els.slice(0, 50).map((el) => {
          const style = getComputedStyle(el) as unknown as Record<string, string>
          return style[p] ?? ''
        })
      } finally {
        kill1.remove()
      }
      return { matched, values }
    },
    { w: watch, p: prop, kill: TRANSITION_KILL },
  )
}

/** 从 toHaveScreenshot 的报错里取出跑测器自己算的像素计数（`N pixels (ratio R …) are different.`） */
function parsePlaywrightCount(message: string | null): number | null {
  if (!message) return null
  const m = message.match(/(\d+) pixels \(ratio [\d.]+ of all image pixels\) are different\./)
  return m ? Number(m[1]) : null
}

for (const slug of PROBE_ANCHORS) {
  const anchor = ANCHORS.find((a) => a.slug === slug)
  if (!anchor) throw new Error(`探针锚点 ${slug} 不在 ANCHORS——先核对再跑`)
  // 图谱类锚点要 pinRandom / 画布守卫这套额外流程，本探针先不背这个复杂度，越界就明确报错
  if (anchor.pinRandom || anchor.requirePaintedGraph) {
    throw new Error(`探针锚点 ${slug} 需要 pinRandom/画布守卫，本 spec 未实现，勿直接加进来`)
  }

  test.describe(`量程探针 · ${slug} · ${PROBE_THEME}`, () => {
    // 【计划原稿在此处写反了，照抄会把夹具清空】原稿是
    // `await page.context().storageState({ path: storagePathFor(anchor.role) })`，
    // 而这个 API 的语义是**把当前上下文保存到该路径**（见 anchors.spec.ts 同款注释、Task 3 报告）：
    // 刚造好的空 context 会把 Task 3 落盘的 admin.json 原地覆盖成 `{"cookies":[],"origins":[]}`，
    // 于是探针截到的是登出页——比的是"登录页 vs 工作台基线"，读数毫无意义，
    // 还顺手把后面所有跑测的会话弄没（后端登录有 10 次/60 秒限流，补不回来）。
    // 正确口径与 anchors 一致：建 context 时用 test.use({ storageState }) 载入。
    if (anchor.role !== 'ANON') test.use({ storageState: storagePathFor(anchor.role) })

    test(`量程探针 ${slug} · 档 ${LAD} · ${PROBE_THEME}`, async ({ page }, testInfo) => {
      if (typeof anchor.path !== 'string') {
        throw new Error(`探针锚点 ${slug} 的 path 需运行时解析，本 spec 未实现`)
      }

      await setTheme(page, PROBE_THEME)
      // 与 anchors 一样钉载荷（同一口径才有可比性）：日期锚点的读数若来自"服务端今天的窗口"，
      // 那漂移会混进量程里。PW_VISUAL_NOPIN=1 是**取证用的反面对照**：
      // 用它重跑 noise 档就能重新量出漂移本身（注释里那些 618/321 就是这么重生的），
      // 但那一轮的证据文件里 pinned=false，不得当交付口径引用。
      const nopin = process.env.PW_VISUAL_NOPIN === '1'
      let pinShift = 0
      if (anchor.pinPaths?.length && !nopin) pinShift = (await installPayloadPin(page, anchor)).shiftDays

      await freezeClock(page)
      await page.goto(anchor.path)
      await settlePage(page)

      // ↓ 与 anchors.runAnchor **同一份**可比性前置（少一条整轮读数作废，见文件头）
      await assertComparableRender(page, anchor, PROBE_THEME)

      // ↓ 空操作守卫：注入前后各读一次计算样式。noise 档不注入 ⇒ 一次都不读（见上面那条更正）
      let before = { matched: 0, values: [] as string[] }
      let after = { matched: 0, values: [] as string[] }
      if (INJECT) {
        before = await computedSignatures(page, STEP.watch, STEP.prop)
        await page.addStyleTag({ content: INJECT })
        after = await computedSignatures(page, STEP.watch, STEP.prop)
        // 零命中 = 注入是空操作，"检不出来"跟基线量程没关系。判 SKIPPED，不判 GREEN。
        // 静态核对过：登录页视图 src/views/auth/Login.vue 的模板里没有 .l1-card
        //（它用的是自己 scoped 的 .auth-card / .brand-mark），
        // 而 /admin/dashboard（AdChartPanels/AdQuickEntries）与 /admin/courses 都有。
        // 所以 .l1-card 系档位（shadow/radius/inset/surface/border）在 login 锚点上预期是 SKIPPED——
        // 这是计划把三档 CSS 写成 .l1-card 作用域、却把 login 也选进探针锚点造成的，
        // 不是缺陷复发，也别为了"让它跑起来"去改锚点名单或改 ladder 的 CSS
        //（那就不再是 Phase 1/2 的形状了）。
        if (after.matched === 0) {
          console.log(`[probe-skip] ${slug} 档 ${LAD}：${STEP.watch} 零命中（matched=${after.matched}）`)
          recordEvidence({ slug, theme: PROBE_THEME, ladder: LAD, skipped: true, nopin, pinShiftDays: pinShift, threshold: THRESHOLDS.threshold, maxDiffPixelRatio: THRESHOLDS.maxDiffPixelRatio, reason: '选择器零命中，注入是空操作' })
          test.skip(true, `档 ${LAD} 的选择器 ${STEP.watch} 在 ${slug} 零命中——注入是空操作，无量程结论`)
          return
        }
        if (!TRANSITION_KILL) {
          console.log(`[probe-guard-off] ${slug} 档 ${LAD}：守卫已按 PW_PROBE_SKIP_GUARD 关掉，` +
            `只取证不参与判读（before=${before.values[0] ?? ''} / after=${after.values[0] ?? ''}）`)
        } else {
          // 命中了却没变 = 注入被更高优先级挡掉（或 !important 写错），测量无效，必须炸出来
          if (JSON.stringify(before.values) === JSON.stringify(after.values)) {
            throw new Error(
              `[探针无效] 注入命中 ${after.matched} 个 ${STEP.watch} 但 ${STEP.prop} 未变化——探针本身失效，` +
                `读数会是假的"检不出"。检查 CSS 优先级/选择器：${INJECT}`,
            )
          }
          // 变了还不够：终态里必须带上注入的那几个片段，否则量的不是这一档改动
          const notSettled = after.values.filter((v) => !SETTLING.every((frag) => v.includes(frag)))
          if (notSettled.length > 0) {
            throw new Error(
              `[探针无效] ${after.matched} 个 ${STEP.watch} 里有 ${notSettled.length} 个的 ${STEP.prop} 终态不含 ` +
                `${JSON.stringify(SETTLING)}（实测首个不符：${notSettled[0]}）` +
                `——注入没有完整落到终态，本轮量程读数作废`,
            )
          }
        }
      }

      const arg = `${snapName(slug, PROBE_THEME)}-${LAD}-probe.png`
      const expected = testInfo.snapshotPath(arg, { kind: 'screenshot' })
      const baseline = join(dirname(expected), '..', 'anchors.spec.ts', `${snapName(slug, PROBE_THEME)}.png`)
      if (!existsSync(baseline)) {
        throw new Error(`[探针无效] 未找到该锚点的已提交基线：${baseline}——没有"未注入的原图"就没法量量程`)
      }
      mkdirSync(dirname(expected), { recursive: true })
      // 每次都从已提交基线重新复制：期望图永远是"未注入的原图"，绝不可能是上一轮的注入产物
      copyFileSync(baseline, expected)

      const { width, height } = pngSize(expected)
      const allowancePx = width * height * THRESHOLDS.maxDiffPixelRatio

      // ↓ 取证图：**与 toHaveScreenshot 完全同一组参数，含同一个 mask**（Step 1e）。
      //   原来这里不带 mask 而比较带 mask，今天无害（12 个锚点没一个声明 maskSelectors），
      //   但 Step 1 若采纳"遮标签带"，离线自检台与跑测器的读数就会静默分叉，
      //   而喂给 LEDGER 的是前者。现在两条路径共用 mask，并由下面的 ①②断言把分叉判红。
      mkdirSync(EVIDENCE_DIR, { recursive: true })
      const capture = join(EVIDENCE_DIR, `${snapName(slug, PROBE_THEME)}-${LAD}-probe-captured.png`)
      writeFileSync(
        capture,
        await page.screenshot({
          fullPage: true,
          animations: 'disabled',
          caret: 'hide',
          scale: 'css',
          mask: maskOf(page, anchor.maskSelectors),
        }),
      )
      if (process.env.PW_PROBE_CAPTURE) copyFileSync(capture, join(dirname(expected), `${snapName(slug, PROBE_THEME)}-${LAD}-probe-captured.png`))
      const reading = measureWith(expected, capture, THRESHOLDS.threshold, THRESHOLDS.maxDiffPixelRatio)

      console.log(
        `[probe] ${slug}/${PROBE_THEME} 档 ${LAD}（${STEP.shape}）· 期望图=未注入基线 ${width}x${height} · ` +
          `threshold=${THRESHOLDS.threshold} maxDiffPixelRatio=${THRESHOLDS.maxDiffPixelRatio} · ` +
          `口径=${IS_DELIVERY_SCALE ? '交付量程' : '非交付量程（env 已改 t/r，禁止当交付读数引用）'} · ` +
          `钉载荷=${anchor.pinPaths?.length ? (nopin ? '已按 PW_VISUAL_NOPIN 关掉（= 真走后端，量的是漂移本身）' : pinShift > 0 ? `开，但按 PW_PIN_SHIFT_DAYS 模拟服务端第 +${pinShift} 天（**非交付读数**）` : '开') : '该锚点未声明'} · ` +
          `命中${STEP.watch}=${after.matched || '—'}个 · ${STEP.prop}: ${before.values[0] ?? '(未读)'} → ${after.values[0] ?? '(未读)'}`,
      )
      console.log(`[probe-reading] ${readingText(reading)}`)
      recordEvidence({
        slug,
        theme: PROBE_THEME,
        ladder: LAD,
        shape: STEP.shape,
        css: INJECT,
        threshold: THRESHOLDS.threshold,
        maxDiffPixelRatio: THRESHOLDS.maxDiffPixelRatio,
        deliveryScale: IS_DELIVERY_SCALE,
        pinned: !!anchor.pinPaths?.length && !nopin,
        nopin,
        pinShiftDays: pinShift,
        guardOff: !TRANSITION_KILL,
        imageWidth: width,
        imageHeight: height,
        allowancePx,
        pixelmatchCount: reading.pixelmatchCount,
        byteDiffPx: reading.byteDiffPx,
        maxChannelDelta: reading.maxChannelDelta,
        md5Equal: reading.md5.equal,
        bbox: reading.bbox,
        injectedMatched: after.matched,
      })

      // ↓ 最后一步才做跑测器自己的比较：判绿/判红都必须在读数已经落盘之后
      let pwError: string | null = null
      try {
        await expect(page).toHaveScreenshot(arg, {
          fullPage: true,
          animations: 'disabled',
          caret: 'hide',
          scale: 'css',
          mask: maskOf(page, anchor.maskSelectors),
          ...THRESHOLDS,
        })
      } catch (e) {
        pwError = (e as Error).message
      }
      const pwCount = parsePlaywrightCount(pwError)
      const pwFailed = pwError !== null
      // ① 两条路径同判（同一对图、同一组参数、同一个 mask ⇒ 结论必须一致）
      if (pwFailed !== reading.failed) {
        throw new Error(
          `[探针无效] 两条路径判定分叉：toHaveScreenshot ${pwFailed ? '红' : '绿'} vs 比较器 ${reading.failed ? '红' : '绿'}。\n` +
            `比较器读数：${readingText(reading)}\n跑测器报错：${(pwError ?? '').split('\n')[0]}\n` +
            `先修尺/参数一致性，再谈量程——分叉时任何一个数都不能进台账。`,
        )
      }
      // ② 失败时：跑测器报出的计数 == 比较器算出的计数（同源的那把尺，不是"看起来一样"）
      if (pwCount !== null && pwCount !== reading.pixelmatchCount) {
        throw new Error(
          `[探针无效] 尺不同源：跑测器计数 ${pwCount} ≠ 比较器计数 ${reading.pixelmatchCount}（${readingText(reading)}）。\n` +
            `比较器从 playwright-core 的 coreBundle.js 取 pixelmatch，这里对不上就是取错了或被比图不一致。`,
        )
      }
      // ③ 结论。判绿的三种情形**处置方向不同**，混写成一句"漏检（被免检额吸收）"就是归因错误：
      //   · 计数 0 且字节差 0        → **零效应**：注入不改变画面一个像素（浅色 inset：白卡叠白高光，
      //                               实测与基线 md5 相等）。收 ratio 完全无用。
      //   · 计数 0 但字节差 > 0      → **全被抗锯齿分类器吸收**：t=0 下 `antialiased()`（coreBundle.js:7012/7030-7060）
      //                               把这些像素整类跳过，**免检额根本没参与**——收 ratio 同样无用，要收的是 threshold
      //                               已经到底了，所以只能换证据形式（定向裁剪 / 计算样式）。
      //   · 0 < 计数 ≤ 免检额        → **被免检额吸收**：这才是收 ratio 能救的那一类。
      const zeroEffect = !pwFailed && reading.pixelmatchCount === 0 && reading.byteDiffPx === 0 && LAD !== 'noise'
      const aaAbsorbed = !pwFailed && reading.pixelmatchCount === 0 && reading.byteDiffPx > 0 && LAD !== 'noise'
      const verdict =
        LAD === 'noise'
          ? pwFailed
            ? '地板非零（抖动或确定性漂移，须用 PW_VISUAL_NOPIN/PW_PIN_SHIFT_DAYS 归因）'
            : '地板'
          : pwFailed
            ? '检出'
            : zeroEffect
              ? '零效应（注入不产生任何像素差，收 ratio 无用）'
              : aaAbsorbed
                ? `全被抗锯齿分类器吸收（字节差 ${reading.byteDiffPx}、计数 0；免检额未参与，收 ratio 无用）`
                : '漏检（被免检额吸收 = 假绿）'
      console.log(`[量程结论] ${slug}/${PROBE_THEME} 档 ${LAD} · ${STEP.shape} · ${verdict} · ${readingText(reading)}`)
      recordEvidence({ slug, theme: PROBE_THEME, ladder: LAD, detected: pwFailed, verdict, playwrightCount: pwCount, nopin, pinShiftDays: pinShift, threshold: THRESHOLDS.threshold, maxDiffPixelRatio: THRESHOLDS.maxDiffPixelRatio })
      if (pwError) {
        throw new Error(
          LAD === 'noise'
            ? `[地板非零] ${slug}/${PROBE_THEME} 不注入却与基线不符——这不是"检出"，是**地板本身不干净**：` +
              `要么这一页有抖动，要么有确定性漂移（日期锚点请用 PW_VISUAL_NOPIN=1 对照归因）。\n  ${readingText(reading)}\n  先归因再谈阈值：**不要**用放宽阈值把这一条压下去，也不要 --update-snapshots 吸收。\n` +
              `  重生成：cd frontend && PW_LADDER=noise PW_PROBE_SLUGS=${slug} PW_PROBE_THEME=${PROBE_THEME} npx playwright test -c playwright.visual.ts sensitivity`
            : `[量程结论] ${slug}/${PROBE_THEME} 档 ${LAD}（${STEP.shape}）**检出**——这是成功的测量，不是回归失败。\n` +
              `  ${readingText(reading)}\n  口径=${IS_DELIVERY_SCALE ? '交付量程' : '非交付量程（env 已改 t/r）'} · 免检额 ${allowancePx.toFixed(2)} px\n` +
              `  跑测器原文：${pwError.split('\n')[0]}\n` +
              `  重生成：cd frontend && PW_LADDER=${LAD} PW_PROBE_SLUGS=${slug} PW_PROBE_THEME=${PROBE_THEME} npx playwright test -c playwright.visual.ts sensitivity`,
        )
      }
    })
  })
}
