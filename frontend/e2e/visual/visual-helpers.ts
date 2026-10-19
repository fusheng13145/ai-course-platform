import type { Locator, Page } from '@playwright/test'
import { createHash } from 'node:crypto'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'

export const THEME_KEY = 'fz-theme'
export type ThemeName = 'light' | 'dark'

/**
 * 冻结点与推进量。为什么是这几个数，见下面 freezeClock / settlePage 的注释；
 * 三处共用同一组常量，页面时间在截图时刻才是可推算的。
 *
 * 预算制：文本轮询每轮只推进 TEXT_POLL_STEP_MS，最多占用 TEXT_POLL_BUDGET_MS，
 * settlePage 收尾把没用完的补满——总推进量因此与轮数无关。
 */
const CLOCK_EPOCH = new Date('2026-10-18T09:00:00')
const CLOCK_WARMUP_MS = 5000
const TEXT_POLL_STEP_MS = 120
const TEXT_POLL_BUDGET_MS = 1200
/**
 * 文本收敛之后**无条件**再推进这么多页面时间，为的是帧数敏感型绘制：
 * ECharts 力导向布局每 16ms 走一步（GraphView.js:209 `setTimeout(step, 16)`），
 * 每步 friction ×0.992、从 0.6 衰减到 <0.01 才算停（forceHelper.js:179-181），
 * 即 ln(0.01/0.6)/ln(0.992) ≈ 510 步 ≈ 8160ms。
 * 导航后总推进量 = WARMUP 5000 + 轮询预算 1200 + 本量 3200 = 9400ms ≈ 587 步 > 510，
 * 截图因此落在**收敛之后**：布局对"多跑/少跑几帧"不再敏感，只剩 Math.random 种子
 * 这一个依赖（由 pinRandom 钉住）。余量取 77 步，是为了 autoresize / 二次 setOption
 * 触发 `_startForceLayoutIteration` 重启时（GraphView.js:135）也还能收完。
 */
const CLOCK_FRAME_SETTLE_MS = 3200

/**
 * 截图时刻的页面时间（`Date.now()`）恒等于这个值：
 * epoch + pauseAt(5000) + runFor(5000) + 轮询预算(1200) + 帧数沉降(3200)
 * = 2026-10-18 09:00:14.400。与轮数、与机器快慢都无关。
 * 导出来是为了让 spec 逐张断言它真的成立，而不是只在注释里宣称。
 */
export const SNAPSHOT_PAGE_TIME_MS =
  CLOCK_EPOCH.getTime() +
  CLOCK_WARMUP_MS +
  CLOCK_WARMUP_MS +
  TEXT_POLL_BUDGET_MS +
  CLOCK_FRAME_SETTLE_MS

/**
 * 主题必须在导航前落到 localStorage：useTheme 在模块作用域读一次初值
 * （useTheme.ts:6），页面起来后才写入就来不及了。addInitScript 而非 evaluate——
 * Chromium 新版对 about:blank 的 localStorage 访问抛 SecurityError，
 * 且 addInitScript 对每次导航幂等（test-helpers.ts:196-199 实测结论）。
 */
export async function setTheme(page: Page, theme: ThemeName): Promise<void> {
  await page.addInitScript((t) => localStorage.setItem('fz-theme', t), theme)
}

/**
 * 冻结时间：把页面时钟钉到 2026-10-18 09:00:05.000 整并停住。
 * 仪表盘的"3 分钟前"、CountUp 的缓动、ECharts 入场动画都挂在时钟上，
 * 不冻结则同一份代码每天截出不同的图，基线失去意义。
 * 起点刻意选上午九点整——避开零点/整日边界，边界值最易暴露格式化差异。
 *
 * 为什么是 `install({ time })` + `pauseAt(...)` 两步（本机 Playwright 1.63 实测，三种写法只有这一种真的停）：
 * - 只 `install({ time })`：把起点挪到 time 但**仍按真实速率前进**——真实等 400ms，页面 Date.now() 前进 416ms。
 *   于是截图时刻 = 09:00:05 + 真实耗时，秒级文案照旧会漂，Task 4 的"二次跑零 diff"变成随机红灯。
 * - `install({ time, pauseAt })`：`pauseAt` **不是这个版本 install 的选项**（types.d.ts 里 install 只收 time），
 *   传进去被静默忽略，实测照样漂 416ms；且它过不了 vue-tsc 的多余属性检查。
 * - `install({ time })` → `runFor(5000)` → `pause()`：这个版本的 Clock 根本没有 `pause()`
 *   （只导出 install/fastForward/pauseAt/resume/runFor/setFixedTime/setSystemTime）。
 *   `pauseAt(时刻)` 正是"推进到该刻即停"的那一个：实测之后页面 Date.now() 差值恒为 0。
 * 关键性质：推进量由参数决定，不由机器快慢决定。
 *
 * 副作用要记着：时钟停住后页面自己的 setTimeout/setInterval/rAF 不再自走，只有 runFor 能推进它们
 * ——所以"把入场动画跑完"是 settlePage 的职责，不是 freezeClock 的。
 */
export async function freezeClock(page: Page): Promise<void> {
  await page.clock.install({ time: CLOCK_EPOCH })
  await page.clock.pauseAt(new Date(CLOCK_EPOCH.getTime() + CLOCK_WARMUP_MS))
}

/**
 * 稳定态收敛。顺序有意义：先等网络空闲，让迟到的数据把动画排上队，
 * 再一次性推进 CLOCK_WARMUP_MS（已越过 ECharts ~1000ms 与 CountUp 的时长），
 * 然后校字体，最后校文本不再变。这比裸 waitForTimeout 可靠——
 * 固定毫秒数在慢机器上会截到半截动画，而这里的推进量与机器快慢无关。
 *
 * runFor 必须在 networkidle 之后（前一次实现正好反了）：freezeClock 在导航前调用，
 * 那一刻页面还没有任何定时器，先推进只会挪时间戳、跑不出任何动画；
 * 页面加载后排队的入场动画要靠 networkidle 之后的这次 runFor 跑完。
 *
 * 【不变式——评审 Important 3 修正后的口径，旧文案是错的，别再引用】
 * 本函数返回后页面 `Date.now()` **恒等于 SNAPSHOT_PAGE_TIME_MS**（09:00:14.400），
 * 与 waitForStableText 轮了几轮无关；时钟仍是停的，所以截图前后都不再前进。
 *
 * 为什么原来做不到：轮询每轮都 runFor(120)，截图时刻实际是
 * 09:00:10 + 120ms×(轮数−1)；零匹配页（登录页/404/个人中心）在第 1 轮之前就 return，
 * 停在 09:00:10.000，而有 `.s-value`/`.pg-title` 的页至少走满一轮停在 09:00:10.120。
 * 两种分支在本批基线里都真实存在——"24 张落在同一瞬间"当时只是经验上碰巧没漂，
 * 不是结构上被钉住。现在改成预算制：轮询至多消耗 TEXT_POLL_BUDGET_MS 的页面时间，
 * 收尾把差额一次补满，再无条件推进 CLOCK_FRAME_SETTLE_MS（合计与轮数无关），
 * 于是总推进量 = 2×CLOCK_WARMUP_MS + TEXT_POLL_BUDGET_MS + CLOCK_FRAME_SETTLE_MS 恒成立。
 * runFor 只推进假时钟，所以补多少都与机器快慢无关。
 *
 * 这条不变式顺带钉住的是**帧数**：fake requestAnimationFrame 同属 runFor 驱动的定时器
 * （Playwright clock 的 install 清单里就有 requestAnimationFrame，types.d.ts:20468-20478），
 * ECharts 入场动画的收尾帧、力导向布局的步数都只由这个常量决定。
 * 图谱锚点没有它就没有可比基线——力导向每 16ms 一步、要 510 步才收敛
 * （算式与余量见 CLOCK_FRAME_SETTLE_MS 注释）。
 * 所以 anchors.spec.ts 每张都断言 `Date.now() === SNAPSHOT_PAGE_TIME_MS`：
 * 这条不变式每次跑都验一遍，不是写在注释里相信。
 */
export async function settlePage(page: Page): Promise<void> {
  await page.waitForLoadState('networkidle')
  await page.clock.runFor(CLOCK_WARMUP_MS)
  await page.evaluate(() => document.fonts.ready.then(() => undefined))
  const usedByPolling = await waitForStableText(page)
  // 补齐预算：轮数少 ↔ 补得多，总推进量不变。usedByPolling 不会超过预算，见 waitForStableText
  const rest = TEXT_POLL_BUDGET_MS - usedByPolling
  if (rest > 0) await page.clock.runFor(rest)
  await page.clock.runFor(CLOCK_FRAME_SETTLE_MS)
}

/**
 * 关键文本稳定判定：连续两轮读数相同才放行。这是对"动画真的结束了"的直接证据，
 * 不依赖对动画时长的猜测。
 * 零匹配同样算稳定——一个没有统计卡/页头标题的页面（登录页、404、个人中心就是）
 * 不可能"还在动画中"；若在此等满超时，抛出的诊断"可能仍在动画中"是假的，
 * 还会把 12 个锚点里的多数页面拖成随机红（.stat-value 更是全仓零命中的死选择器，已删）。
 *
 * 每轮先用 runFor 推进页面时钟：时钟被 freezeClock 停住后页面的 CountUp 不会自己跑，
 * 只等真实毫秒数会永远读到同一个初值。保留 waitForTimeout 是为"忘了先 freezeClock"的调用方
 * 兜底——那种情况下 runFor 是静默 no-op，真实等待才让判别力还在。
 *
 * 返回值 = 本函数消耗掉的页面毫秒数，settlePage 用它把总额补到 TEXT_POLL_BUDGET_MS，
 * 这样"截图时刻"与"fake rAF 跑过多少帧"都只由常量决定（见 settlePage 的不变式）。
 * 因此这里对推进量设上限：预算用完仍不收敛，就只剩真实毫秒在等，页面时间不再前进、
 * 文本也不会再变——15s 后抛"未收敛"。这是**故意的严格化**：
 * 需要 >10 轮（1200ms 页面时间）才停下的动画属于"没被钉住的动态源"，
 * 该红给您看，而不是靠多轮 runFor 把它悄悄跑完、顺手改变截图时刻。
 */
export async function waitForStableText(page: Page, timeout = 15000): Promise<number> {
  let prev: string | null = null
  let used = 0
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) {
    const now = await page.evaluate(() =>
      Array.from(document.querySelectorAll('.s-value, .pg-title'))
        .map((el) => el.textContent?.trim() ?? '')
        .join('|'),
    )
    if (now === '') return used // 页面无统计类文本 → 天然稳定
    if (now === prev) return used
    prev = now
    const step = Math.min(TEXT_POLL_STEP_MS, TEXT_POLL_BUDGET_MS - used)
    if (step > 0) {
      await page.clock.runFor(step)
      used += step
    }
    await page.waitForTimeout(TEXT_POLL_STEP_MS)
  }
  throw new Error(`文本未收敛到稳定态（可能仍在动画中）·最后读数: ${prev}`)
}

/**
 * 把 Math.random 钉成固定序列（mulberry32，种子写死在代码里）。
 *
 * 只有 `AnchorSpec.pinRandom` 的锚点才开，为什么不并进 freezeClock：
 * Playwright 的 clock 只 fake 时间类 API（types.d.ts:20468-20478 的 install 清单里
 * 没有 Math.random），所以冻结时钟挡不住随机源；但**全局**钉会把其它页面里
 * "真该暴露出来的随机"一并抹平成假稳定，而需要它的锚点是少数（现在就一个）。
 *
 * 那一个为什么必须有：ECharts 力导向布局用 Math.random() 播节点初值
 * （node_modules/echarts/lib/chart/graph/forceHelper.js:80），节点重叠时的斥力也用它
 * （:162）。种子不钉死，同一份代码每次截出一张不同的图，基线就是随机红灯；
 * 用遮罩把整块图谱遮掉是自欺——那等于这张基线根本没看图。
 * 钉了种子以后，布局 = f(固定初值, 固定步数)，步数由 settlePage 的预算恒定保证，
 * 于是画布逐像素可复现，二次跑零 diff 才有结构性依据。
 */
export async function pinRandom(page: Page): Promise<void> {
  await page.addInitScript(() => {
    let state = 0x20261018
    Math.random = () => {
      state = (state + 0x6d2b79f5) >>> 0
      let t = state
      t = Math.imul(t ^ (t >>> 15), t | 1)
      t ^= t + Math.imul(t ^ (t >>> 7), t | 61)
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296
    }
  })
}

export interface AnchorSpec {
  /** 用于快照文件名的稳定 slug，禁止含空格与斜杠 */
  slug: string
  title: string
  /** 'ANON' 表示无需会话（登录页 / 错误页） */
  role: 'ANON' | 'STUDENT' | 'TEACHER' | 'ADMIN'
  /** 相对 baseURL 的路径；'__FROM_COURSE_LIST__' 由 resolvePath 在运行时解析 */
  path: string | (() => Promise<string>)
  /** 合法随数据变化、必须遮掉的区域选择器（如头像图片） */
  maskSelectors?: string[]
  /**
   * 该页渲染力导向图，随机源必须钉住（见 pinRandom）。
   * 默认不开：全局钉会掩盖别处"本该暴露的随机"，所以按锚点显式声明。
   */
  pinRandom?: boolean
  /**
   * 该页的图表面板必须"真的画出内容"才允许入库（见 anchors.spec.ts 的 assertGraphPainted）。
   * 设立理由：上一轮图谱锚点截到的是空态占位图，而占位图同样"非空壳、无错误边界、断言全绿"，
   * 四条通用防护一条都拦不住——它得专门看图本身。
   */
  requirePaintedGraph?: boolean
  /**
   * **钉死响应载荷**的端点（pathname+search，GET）。非空即表示：这些端点的响应改由
   * `e2e/visual/__fixtures__/<slug>.json` 提供，不再走服务端，见 pinPayloadSection 注释块。
   * 只钉"日期窗口由服务端 LocalDate.now() 回推"的那几个端点，其余请求照旧走真后端——
   * 钉全屏就等于把"数据确已到位"这件事一起钉死了（那正是 Step 2 要堵的洞）。
   */
  pinPaths?: string[]
}

/**
 * 快照文件名。**不要**在这里放 `{platform}` 之类的占位符：Playwright 只在
 * `snapshotPathTemplate` 里展开占位符，作为 `toHaveScreenshot(name)` 传入的 arg 里
 * `{platform}` 会原样落进文件名。
 * 返回**不含扩展名**的名字——但注意扩展名不是模板免费给的：
 * 1.63 实测 `toHaveScreenshot(snapName(..))` 直接抛
 * `Screenshot name "x-light" must have a '.png' or '.webp' extension`
 * （matchers/expect.js:12597 先按 arg 的 extname 定 mimeType，无扩展名就进不到比对），
 * 而模板里的 `{ext}` 正是从 arg 的扩展名反推出来的（workerProcessEntry.js:1258
 * `basename(subPath, ext)`），arg 带 `.png` 时 `{arg}` 仍是去掉扩展名的部分，
 * 落盘名 `x-light.png`，不会出现 `.png.png`。
 * 结论：**消费方在调用处补 `.png`**（见 anchors.spec.ts），本函数保持无扩展名返回值——
 * 这是 helpers.spec.ts 已钉住的契约，Task 5+ 同样按 `snapName(..) + '.png'` 用。
 */
export function snapName(slug: string, theme: ThemeName): string {
  return `${slug}-${theme}`
}

/**
 * 全仓唯一截图阈值来源。这两个数**是测量结果**（Task 4b 第 2 轮，22 张真基线上跑出来的，见下方实测块）：
 * 原来那组 (0.2 / 0.002) 在真基线上对三档注入全部失明，连哨兵档都不报警，已按逐级下探收到 (0 / 0.0005)。
 * 再动它们必须重新测量：放宽 = 直接退回"零 diff 不可解释"；未经验证的收紧 = 把噪声当成改动。
 *
 * 【唯一来源的机械约束】anchors.spec.ts 与 sensitivity.spec.ts 都从这里取值，
 * 不在各自 spec 里写字面量——两处各写一份就一定会漂，
 * 而"量程测量"用的阈值和"基线回归"用的阈值一旦不是同一组，测出来的结论对回归无效。
 *
 * 【这两个数在 pixelmatch 里到底是什么】comparators 调 pixelmatch(expected, actual, diff, w, h, { threshold })
 * （playwright-core/lib/coreBundle.js:7896，只传 threshold，其余走 pixelmatch 默认 includeAA:false），
 * 判据是 `|colorDelta| > 35215 × threshold²`，其中 YIQ 亮度差 y 单独贡献 0.5053·y²：
 * - threshold 0.2 → 需要单通道差 δ > 52.8/255 ≈ **20.6% 的明度跳变**才算"不同像素"；
 * - threshold 0.05 → δ > 13.2（5.2%）；0.02 → δ > 5.3（2.1%）；0 → 任何不相等都算，
 *   但**仍会**被 pixelmatch 的抗锯齿判别（antialiased()）整类跳过——
 *   所以"降到 0"不等于"逐字节比"，圆角/描边这种本来就长在 AA 边缘上的改动可能一条都不计。
 * maxDiffPixelRatio 0.002 再乘一层宽松度：1440×H 的整页图按 H=1200 算是 3456 个像素的免检额，
 * 而一次阴影增强恰好就是"几千个弱差像素"的形状。
 * 这两条合起来就是 Task 4 图谱基线假绿（0.33% 像素差被判相同）的机制解释。
 *
 * 【Task 4b 第 2 轮·真基线实测（22 张已提交基线，1440×900=1,296,000 px）】
 * 判据口径：期望图 = 该锚点**未注入**的已提交基线逐字节副本；被测图 = 注入后的现场渲染。
 * FAILED=检测到了，PASSED=被逐像素吸收（假绿），SKIPPED=选择器零命中（注入是空操作，不是"检不出"）。
 * 尺与跑测器同源（pixelmatch 从 coreBundle.js 抠出，与 Playwright 自己报的数逐点核对过，
 * 全部一字不差：gross 1,234,282 / shadow 55,903·21,059 / radius 1,394 / 漂移 321·295·110·111 / 抖动 13）。
 *
 * 在**原生产配置 (0.2 / 0.002)** 下四档**全部 PASSED**：连"整页 3% 明度差"的哨兵档都不报警。
 * 逐级下探的响应曲线（像素数 = pixelmatch 判为不同的像素，免检额 r=0.002→2592 / 0.0005→648 / 0→0）：
 *   gross(body filter brightness 0.97)：最大单通道色差只有 **8/255**（白底 255×0.97=247），
 *     t=0.2/0.1/0.05 全是 **0 个像素**，t=0.02 起 1,015,094 个 → 哨兵档要求 threshold ≤ 0.02
 *   shadow(.l1-card alpha .05/.06→.12/.13)：最大色差 20/255；5 卡页 t=0.05 起报警（4496 个），
 *     1 卡页要等到 t=0.02（4704 个）→ 两页都报警要求 threshold ≤ 0.02
 *   radius(.l1-card 16px→14px)：最大色差 19/255；**threshold 降到 0 也只有 1394 / 198 个**，
 *     198 < 2592 → 杀它的是 maxDiffPixelRatio 而不是 threshold（第 1 轮自检台的预测在真基线上一字应验）。
 *     r=0.0005 只救得了 5 卡页（1394 > 648），1 卡页仍瞎（198 < 648）；要两页都报警只能 r=0。
 *
 * 【为什么最终是 (0, 0.0005) 而不是 (0, 0)】r=0 在**当前这 22 张基线**上跑不通，但不是因为渲染抖动：
 *   ① 三个探针锚点（login / admin-dashboard / admin-courses-table）的不注入复截**逐字节相同**（0 px，
 *      连 t=0/r=0 都是 0）→ 静态渲染的复现性是完美的，这条支持收紧。
 *   ② 但 4 张基线（student-dashboard 明/暗、admin-governance-analytics 明/暗）与"改动前的代码"的新截图
 *      差 110~321 个像素，且**两次独立跑测的产物 md5 完全一致** → 不是抖动，是**确定性的内容漂移**：
 *      漂移点全在图表的时间轴标签行（y 537..544 / y 696..703），标签整组往后挪了一天
 *      （基线 10-05..10-18 → 今日 10-06..10-19；分析页 2026-09-25 → 09-26）。
 *      根因：freezeClock 只钉得住浏览器时钟，**图轴日期是后端按真实日期算出来的**，
 *      所以这 4 张基线只在"截它的那一天"有效，每天零点集体漂一次。
 *   ③ student-courses-modal 另有 1~46 个像素的**真抖动**（同一条路径两次跑一次红一次绿，红在 y 21..34 顶栏）。
 * 于是 r 的下限由 ②（321 px）而不是由 ③ 决定：0.0005（免检额 648）是"22 张仍全绿"的最小档。
 * 把 ② 修掉（遮罩时间轴标签带 / 让后端日期也可钉 / 换成与日期无关的锚点）之后 r 才能到 0，
 * radius 那档才可能在单卡页也报警——这条记在 LEDGER 的准入前提里，不在这里偷偷放宽。
 *
 * 【这组数现在能保证什么、不能保证什么】（全部是真跑出来的，不是推断）
 *   保证：整页明度类改动（gross 形状，96% 像素报警）、卡片阴影类改动（shadow 形状，免检额的 32~86 倍）一定报警。
 *   不保证：圆角/描边类改动——只有当它铺到 ≥ 649 个像素时才发现（5 卡页 16→14 是 1394，1 卡页 198 就漏了）。
 *   不保证：任何长在图表时间轴标签带里的改动（648 个像素以内一律吸收）。
 * 任何放宽这两个数的改动，必须重跑 sensitivity 四档并把读数留在 LEDGER；
 * 反过来"收紧"也要：每下调一格都要重跑 anchors 确认 22 张仍相等，否则就是把噪声当成改动。
 */
export const SNAPSHOT_THRESHOLDS = { threshold: 0, maxDiffPixelRatio: 0.0005 }

/**
 * 会话夹具路径。定义在 helper 而非 global-setup：消费者是 anchors.spec.ts /
 * motion-observation.spec.ts，让它们去 import global-setup 会形成"配置导入设置"的反向依赖。
 * 路径相对 frontend/（Playwright 的 storageState 按 cwd 解析），全仓只用这一种写法，
 * 绝对/相对混用会得到 ENOENT 这种最难查的失败。
 */
export function storagePathFor(role: 'STUDENT' | 'TEACHER' | 'ADMIN'): string {
  return `e2e/visual/.auth/${role.toLowerCase()}.json`
}

/** 遮罩定位器。同步函数——它没有任何 await，标 async 只会让调用点多一个假 await。 */
export function maskOf(page: Page, selectors: string[] = []): Locator[] {
  return selectors.map((s) => page.locator(s))
}

/* ============================================================================ *
 * 载荷钉死（payload pin）——Task 4c Step 1 的"首选路线"，不是遮罩
 * ============================================================================ *
 * 【为什么不用遮罩】评审给的算术决定了这一点，不能靠直觉：
 *   1440×900 = 1,296,000 px，交付配置 r=0.0005 ⇒ 免检额 648 px，而 student-dashboard
 *   一天的日期漂移就是**字节差 618 / 分类器计数 321**（重生成：
 *   `cd frontend && PW_PORT=5175 PW_LADDER=noise PW_PROBE_SLUGS=student-dashboard PW_PROBE_CAPTURE=1 npx playwright test -c playwright.visual.ts sensitivity`
 *   然后 `node e2e/visual/tools/measure-diff.cjs --table e2e/visual/__snapshots__/sensitivity.spec.ts`）。
 *   618 对 648 = **余量 5%**，不是原先以为的 2 倍。遮掉标签带 = 那一带**永久**退出比较，
 *   而 Phase 1/2 的改动（卡片阴影、圆角）恰好长在图表面板那一带里——盲区与改动区重叠，
 *   遮罩等于把门禁的受益人换成空气。
 * 【更要紧的】同一个 `LocalDate.now()` 窗口**还会重新取数**（StudentVisualizationComponent
 *   的 buildDurationTrend 用 today.minusDays(13)、buildHeatmapData 用 today.minusDays(6) 去查库），
 *   所以换一天动的可能不只是轴标签文字，而是整条 series 的几何。遮标签带挡不住后者。
 * 【钉载荷做的是另一件事】把"服务端按今天算"换成"读一份入库的固定响应"，日期与数值**一起**冻住，
 *   被比对的区域一个像素都不遮。代价与边界：
 *   ① 只钉 AnchorSpec.pinPaths 声明的那几个端点（现在每个日期锚点各 1 个），
 *      其余请求照旧打真后端——否则"数据确已到位"会被一起钉成假命题（Step 2 的洞就是这么来的）；
 *   ② fixture 是从**真后端录下来的**，录的那一次必须先过 arrival 断言（录空态 = 基线空态，
 *      所以 record 模式在写出 fixture 前就跑一次 DATA_ARRIVAL_CHECKS）；
 *   ③ 钉死之后这几个端点**不再被像素门禁覆盖**：产品若改图表数据结构，本套件不会红，
 *      要红得先重录（重录会让图与已提交基线不符 ⇒ 那时才是真红）。这条盲区要写进 LEDGER。
 * 【回看一天的偏移怎么补】录制时把载荷里所有 ISO 日期整体回拨到冻结时钟那一天
 *   （CLOCK_EPOCH = 2026-10-18，见 freezeClock），于是 fixture 复现的是**基线当天**的画面。
 *   服务端今天的值与基线当天是否相同，不由注释说：由"钉了之后 4 张日期锚点与已提交基线
 *   逐字节相同（md5 相等）"这条硬断言说——它不成立就必须显式重生成基线，而不是悄悄放宽阈值。
 */

/** fixture 落点（入库；不是 .auth/ 那种含令牌的会话夹具） */
export const FIXTURE_DIR = 'e2e/visual/__fixtures__'

export interface PinnedResponse {
  status: number
  contentType: string
  body: string
}

export interface PinManifest {
  slug: string
  /** 录制时刻（Node 侧真实时间） */
  recordedAt: string
  /** 录制时服务端的本地日（= 后端 LocalDate.now() 的日） */
  serverDateAtRecord: string
  /** 冻结时钟那一日的 YYYY-MM-DD */
  clockDate: string
  /** serverDateAtRecord − clockDate 的天数；录制时用它把日期整体回拨到基线当天 */
  backshiftDays: number
  /** key = pathname + search */
  requests: Record<string, PinnedResponse>
}

export interface PinState {
  /** 实际被钉掉的请求 key（含重复） */
  served: string[]
  /** 审计信息：/api 下**未**声明钉死、但响应体里带服务端"今天"字符串的端点 */
  unpinnedTodayBearing: string[]
}

export function fixturePathFor(slug: string): string {
  return join(process.cwd(), FIXTURE_DIR, `${slug}.json`)
}

function isoDay(d: Date): string {
  return d.toISOString().slice(0, 10)
}

/**
 * **本地**日历日。服务端那侧是 `LocalDate.now()`（JVM 默认时区），
 * 用 toISOString 的 UTC 日会在"本地已跨天、UTC 还没跨天"的时段（本地 00:00–08:00，+08 区）
 * 少算一天——少算的那天正好把回拨量弄错，fixture 就复现不出基线画面。
 */
function localDay(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** 冻结时钟那一天的日历日：与 freezeClock 共用 CLOCK_EPOCH，别在这儿另写一个日期 */
export const CLOCK_DATE = localDay(new Date(CLOCK_EPOCH.getTime() + CLOCK_WARMUP_MS))

const ISO_DATE_RE = /20\d\d-(?:0[1-9]|1[0-2])-(?:[0-2]\d|3[01])/g

/**
 * 把载荷里所有 ISO 日期（含 `2026-10-19T08:00:00` 这种日期时间的前半段）整体回拨 `days` 天。
 * 只在**录制**时跑一次，产出的 fixture 从此不再变——服务时逐字回放到盘上那份。
 * 用文本替换而不是解析重序列化，是为了不让 JSON 的键序/数字格式改动混进载荷。
 */
export function shiftIsoDates(body: string, days: number): string {
  if (days === 0) return body
  return body.replace(ISO_DATE_RE, (m) => {
    const d = new Date(`${m}T00:00:00Z`)
    d.setUTCDate(d.getUTCDate() - days)
    return isoDay(d)
  })
}

export function loadPinManifest(slug: string): PinManifest {
  const file = fixturePathFor(slug)
  let raw: PinManifest
  try {
    raw = JSON.parse(readFileSync(file, 'utf8')) as PinManifest
  } catch (e) {
    throw new Error(
      `[载荷钉死] 读不到 ${file}：${(e as Error).message}\n` +
        `重录命令：cd frontend && PW_PORT=5175 PW_RECORD_PAYLOADS=1 npx playwright test -c playwright.visual.ts anchors -g "${slug}"`,
    )
  }
  if (!raw || raw.slug !== slug || !raw.requests || Object.keys(raw.requests).length === 0) {
    throw new Error(`[载荷钉死] ${file} 形状不对（slug 不符或 requests 为空）——宁可红，不拿空 fixture 当钉住`)
  }
  return raw
}

/**
 * 装上钉死：**只**拦 AnchorSpec.pinPaths 声明的 GET；其余请求 fallback 走真后端。
 * 返回的 state 交给 assertPinCoverage 验"声明的端点真的都被请求过"——
 * 钉了却没人调用 = fixture 过期或路径写错，那种"绿"和没钉一样，必须红。
 */
export async function installPayloadPin(page: Page, anchor: AnchorSpec): Promise<PinState> {
  const paths = anchor.pinPaths ?? []
  const manifest = loadPinManifest(anchor.slug)
  const state: PinState = { served: [], unpinnedTodayBearing: [] }
  const serverToday = localDay(new Date())

  await page.route((url) => paths.includes(`${url.pathname}${url.search}`), async (route) => {
    const req = route.request()
    if (req.method() !== 'GET') return route.fallback()
    const key = `${new URL(req.url()).pathname}${new URL(req.url()).search}`
    const hit = manifest.requests[key]
    if (!hit) return route.fallback()
    state.served.push(key)
    await route.fulfill({ status: hit.status, contentType: hit.contentType, body: hit.body })
  })

  // 审计（不是断言）：页面还在消费哪些"带今天日期"的 /api 响应。
  // 它们多数不落在画上（未读数、时间戳字段），所以只报数、不判红；判红由零 diff 那条负责。
  page.on('response', (res) => {
    const u = new URL(res.url())
    if (!u.pathname.startsWith('/api/') || paths.includes(`${u.pathname}${u.search}`)) return
    void res
      .text()
      .then((t) => {
        if (t.includes(serverToday)) state.unpinnedTodayBearing.push(`${u.pathname}${u.search}`)
      })
      .catch(() => undefined)
  })
  return state
}

export function assertPinCoverage(anchor: AnchorSpec, state: PinState): void {
  const declared = anchor.pinPaths ?? []
  const missing = declared.filter((p) => !state.served.includes(p))
  if (missing.length > 0) {
    throw new Error(
      `[载荷钉死] ${anchor.slug} 声明要钉 ${JSON.stringify(declared)}，但页面没请求它们：${JSON.stringify(missing)}\n` +
        `钉了却没用上 = fixture 与产品已脱节，这次的"零 diff"不是钉出来的。要么改 pinPaths，要么重录 fixture。`,
    )
  }
}

/**
 * 录制模式（PW_RECORD_PAYLOADS=1）：不装 route，改为收集真响应并按 CLOCK_DATE 回拨落盘。
 * 落盘前必须先过 arrival 断言——把空态录进 fixture 等于把"数据到位"钉成假命题。
 */
export function attachPayloadRecorder(page: Page, anchor: AnchorSpec): () => Promise<void> {
  const paths = anchor.pinPaths ?? []
  const captured = new Map<string, PinnedResponse>()
  const wanted = new Set(paths)
  // res.text() 是异步的：不把这些 promise 收着等完，flush 时可能还没落地，
  // 症状是"明明请求过的端点报没等到"（本机第一次录制就是这么炸的）。
  const pending = new Set<Promise<void>>()
  const seen: string[] = []
  page.on('response', (res) => {
    const u = new URL(res.url())
    const key = `${u.pathname}${u.search}`
    if (!u.pathname.startsWith('/api/') || res.request().method() !== 'GET') return
    seen.push(key)
    if (!wanted.has(key)) return
    const p = res
      .text()
      .then((body) => {
        captured.set(key, { status: res.status(), contentType: res.headers()['content-type'] ?? 'application/json', body })
      })
      .catch(() => undefined)
    pending.add(p)
    void p.finally(() => pending.delete(p))
  })
  return async () => {
    for (let i = 0; i < 5 && pending.size > 0; i++) await Promise.all([...pending])
    const serverNow = new Date()
    const serverDate = localDay(serverNow)
    const manifest: PinManifest = {
      slug: anchor.slug,
      recordedAt: serverNow.toISOString(),
      serverDateAtRecord: serverDate,
      clockDate: CLOCK_DATE,
      // 整天差用日历日算，不用毫秒差 round：跨月/跨夏令时都不会差一天
      backshiftDays: Math.round((Date.parse(`${serverDate}T00:00:00Z`) - Date.parse(`${CLOCK_DATE}T00:00:00Z`)) / 86400000),
      requests: {},
    }
    for (const [key, v] of captured) {
      if (!wanted.has(key)) continue
      manifest.requests[key] = { ...v, body: shiftIsoDates(v.body, manifest.backshiftDays) }
    }
    const absent = paths.filter((p) => !manifest.requests[p])
    if (absent.length > 0) {
      // 常见的错法是把 search 漏了：key 是 pathname+search，逐字比。
      // 把 pathname 对得上的真实 key 一起打出来，一眼能看出差在哪个 query。
      const near = absent.map((p) => {
        const base = p.split('?')[0]
        const hits = [...new Set(seen.filter((k) => k.split('?')[0] === base))]
        return `${base} → 实测 ${JSON.stringify(hits)}`
      })
      throw new Error(
        `[载荷录制] ${anchor.slug} 没等到这些端点的响应，拒绝写半成品 fixture：${JSON.stringify(absent)}\n` +
          `按 pathname 找相近的真实 key：${JSON.stringify(near)}\n` +
          `本次页面实际请求过的 /api GET：${JSON.stringify([...new Set(seen)])}\n` +
          `两种可能：pinPaths 里的 search 写错了（key 是 pathname+search，逐字比），或这一页已经不再取这份数（那要重选钉哪个端点）。`,
      )
    }
    mkdirSync(dirname(fixturePathFor(anchor.slug)), { recursive: true })
    writeFileSync(fixturePathFor(anchor.slug), `${JSON.stringify(manifest, null, 2)}\n`, 'utf8')
    console.log(`[pin-record] ${anchor.slug} → ${fixturePathFor(anchor.slug)}（回拨 ${manifest.backshiftDays} 天，端点 ${Object.keys(manifest.requests).length} 个）`)
  }
}

/* ============================================================================ *
 * 每个锚点自己的"数据确已到位"断言（spec §6.14）
 * ============================================================================ *
 * 【为什么通用守卫不够】Task 4b 记过一次真实事故：后端瞬时失败把 teacher-dashboard 渲染成
 * 空态，而 `.error-boundary` 计数 0、`#app` 非空、URL 没被改写、data-theme 也对——四条全过。
 * 空态是一张"长得很正常"的图，只有看**这页该有的东西在不在**才拦得住。
 * 【为什么它们必须一次读全表】所有判定都基于同一次 evaluate 的快照：
 * 断言之间不能再有等待/推进，否则"到位"的时点与截图的时点不是同一帧，读数会自相矛盾。
 * 【为什么必须是抛错而不是 expect】这套检查同时被 anchors 与 sensitivity 共用（见 assertComparableRender），
 * 探针那边要的是"立刻可归因的失败"，polling 版 expect 会把 20s 超时伪装成量程结论。
 */

interface ArrivalSnapshot {
  path: string
  l1Card: number
  statValues: string[]
  treeRows: number
  tableRows: number
  canvas: number
  formItems: number
  filledInputs: number
  heroStats: string[]
  shellTabs: number
  headings: string[]
}

const hasNonZeroDigit = (s: string): boolean => /[1-9]/.test(s)

async function readArrival(page: Page): Promise<ArrivalSnapshot> {
  return page.evaluate(() => {
    const q = (sel: string) => document.querySelectorAll(sel).length
    const txt = (sel: string) => Array.from(document.querySelectorAll(sel)).map((e) => (e.textContent ?? '').trim())
    const filled = Array.from(document.querySelectorAll('input, textarea')).filter(
      (el) => ((el as HTMLInputElement).value ?? '').trim() !== '',
    ).length
    return {
      path: location.pathname,
      l1Card: q('.l1-card'),
      statValues: txt('.s-value'),
      treeRows: q('.tree-row'),
      tableRows: q('.el-table__row'),
      canvas: q('canvas'),
      formItems: q('.el-form-item'),
      filledInputs: filled,
      heroStats: txt('.hero-stat__value'),
      shellTabs: q('.shell-tab'),
      headings: txt('h1, h2, h3'),
    }
  })
}

export interface ArrivalCheck {
  /** 这条断言在验什么——直接进失败消息，别让读红的人去猜选择器 */
  what: string
  run: (page: Page) => Promise<void>
}

/** 至少一个统计值带非零数字（全 0 / 全空 = 后端没给数，正是那次事故的样子） */
function requireSomeStat(s: ArrivalSnapshot, where: string): void {
  if (s.statValues.length === 0) throw new Error(`${where}：一个 .s-value 都没有——统计卡没渲染`)
  if (!s.statValues.some(hasNonZeroDigit)) {
    throw new Error(`${where}：.s-value 全部为 0/空/占位 ${JSON.stringify(s.statValues)}——数据没到位，不得当基线`)
  }
}

export const DATA_ARRIVAL_CHECKS: Record<string, ArrivalCheck> = {
  login: {
    what: '登录表单两个字段真渲染（本页无数据接口，钉的是页面身份不是数据）',
    run: async (page) => {
      const s = await readArrival(page)
      if (s.formItems < 2) throw new Error(`登录页只渲染出 ${s.formItems} 个 .el-form-item（要 ≥2）`)
    },
  },
  'admin-dashboard': {
    what: '管理工作台 ≥1 张 .l1-card 且统计值里有非零数',
    run: async (page) => {
      const s = await readArrival(page)
      if (s.l1Card < 1) throw new Error(`管理工作台 .l1-card = ${s.l1Card}（要 ≥1）`)
      requireSomeStat(s, '管理工作台')
    },
  },
  'student-dashboard': {
    what: '学生工作台 ≥1 张 .l1-card、统计值有非零数、且图表 canvas 真存在',
    run: async (page) => {
      const s = await readArrival(page)
      if (s.l1Card < 1) throw new Error(`学生工作台 .l1-card = ${s.l1Card}（要 ≥1）`)
      requireSomeStat(s, '学生工作台')
      if (s.canvas < 1) throw new Error('学生工作台的图表 canvas 没渲染——日期载荷钉死后画面不再来自后端，这一条是唯一还能看出"图没画"的判据')
    },
  },
  'teacher-dashboard': {
    what: '教师工作台 ≥1 张 .l1-card 且统计值里有非零数（**就是那次事故的那一页**）',
    run: async (page) => {
      const s = await readArrival(page)
      if (s.l1Card < 1) throw new Error(`教师工作台 .l1-card = ${s.l1Card}（要 ≥1）——空态页面正是这样：非空壳、无错误边界、断言全绿`)
      requireSomeStat(s, '教师工作台')
    },
  },
  'admin-courses-table': {
    what: '课程表格真有新行（.el-table__row ≥1）',
    run: async (page) => {
      const s = await readArrival(page)
      if (s.tableRows < 1) throw new Error(`课程表格行数 = ${s.tableRows}（要 ≥1）——空表基线会把"数据没了"判成"改版了"`)
      requireSomeStat(s, '课程管理')
    },
  },
  'admin-system-form': {
    // ⚠️ 这一页的画面**不随接口变**（实测：把 /api/system-configs 五个端点全打空，
    // #app 节点数 1909→1909、有值输入框 26→26 一字不变——表单渲染的是组件内默认值）。
    // 所以这条只能是**结构性**断言（表单确实挂载），它证伪的方式是"拿错页跑"（见 anchors.spec.ts 矩阵 B），
    // 不是"接口打空"。这条限制如实报上去，不假装它是数据到位断言。
    // 阈值取 ≥10 条目 / ≥1 开关 / ≥5 已填输入框：实测本页 35/8/26，而登录页只有 2/0/≤2，
    // 三个数都取在中段，既不会因为表单加减字段就假炸，也不会让别的页面混过去。
    what: '系统设置的表单确实挂载（≥10 个字段、≥1 个开关、≥5 个已回填输入框）',
    run: async (page) => {
      const s = await readArrival(page)
      if (s.formItems < 10 || s.filledInputs < 5) {
        throw new Error(`系统设置不像系统设置：.el-form-item=${s.formItems}（要 ≥10）、已回填输入框=${s.filledInputs}（要 ≥5）`)
      }
      const switches = await page.evaluate(() => document.querySelectorAll('.el-switch').length)
      if (switches < 1) throw new Error(`系统设置没有 .el-switch（实测 ${switches}）——设置面板没挂载`)
    },
  },
  'student-courses-modal': {
    what: '课程大厅真列出课程卡（.tree-row ≥1）',
    run: async (page) => {
      const s = await readArrival(page)
      if (s.treeRows < 1) throw new Error(`课程大厅 .tree-row = ${s.treeRows}（要 ≥1）`)
    },
  },
  'student-learning-center': {
    what: '学习中心外壳真挂上（.shell-tab ≥1 且有课程名标题）',
    run: async (page) => {
      const s = await readArrival(page)
      if (s.shellTabs < 1) throw new Error(`学习中心 .shell-tab = ${s.shellTabs}（要 ≥1）——壳页没挂载就是白屏/空态`)
      if (s.headings.every((h) => h === '')) throw new Error('学习中心没有任何非空标题：课程名没到位')
    },
  },
  'student-knowledge-graph': {
    what: '图谱页统计卡非零（真画出内容由 anchors.spec.ts 的 assertGraphPainted 另三道判据把关）',
    run: async (page) => {
      const s = await readArrival(page)
      requireSomeStat(s, '知识图谱')
    },
  },
  'admin-governance-analytics': {
    what: '分析页 ≥1 个 canvas 且统计值有非零数',
    run: async (page) => {
      const s = await readArrival(page)
      if (s.canvas < 1) throw new Error('分析页没有 canvas——图表整块没渲染')
      requireSomeStat(s, '治理分析')
    },
  },
  'admin-profile': {
    what: '个人中心真回填了资料（有值的 input/textarea ≥1 且 hero 统计非空）',
    run: async (page) => {
      const s = await readArrival(page)
      if (s.filledInputs < 1) throw new Error('个人中心的资料字段全空——用户数据没到位')
      if (!s.heroStats.some((v) => v !== '' && v !== '0')) throw new Error(`个人中心 hero 统计为空/零 ${JSON.stringify(s.heroStats)}`)
    },
  },
  'error-404': {
    what: '错误页真的是 404（钉的是页面身份：换成正则路由它就红）',
    run: async (page) => {
      const s = await readArrival(page)
      if (!s.headings.some((h) => h.includes('404'))) throw new Error(`404 页标题不是 404：${JSON.stringify(s.headings)}（path=${s.path}）`)
    },
  },
}

export async function assertAnchorArrival(page: Page, slug: string): Promise<void> {
  const check = DATA_ARRIVAL_CHECKS[slug]
  if (!check) throw new Error(`[数据到位] 锚点 ${slug} 没有专属断言——新锚点必须先加一条，通用守卫拦不住空态（spec §6.14）`)
  try {
    await check.run(page)
  } catch (e) {
    throw new Error(`[数据到位] ${slug} 未通过：${check.what}\n${(e as Error).message}`)
  }
}

/**
 * 每个锚点"这一页自己的数据端点"前缀，供 anchors.spec.ts 的空态可证伪矩阵打桩用。
 * 名单不是猜的：逐个锚点挂 response 监听跑一遍真实导航量出来的（GET /api/** 全集里
 * 去掉外壳类 /api/auth/permissions 与 /api/notifications/unread-count）。
 * 为什么要按锚点收窄而不是一把梭空掉整个 /api：
 * 实测发现把 /api/auth/permissions 之类也打空时，暗色/浅色锚点会连 `data-theme` 都拿不到，
 * 于是红的是**通用守卫**而不是本页的专属断言——那种红证明不了专属断言堵得住这个洞。
 * 'ALL' = 这一页没有可辨识的独立数据端点（学习中心走的是各模块端点），打全部非外壳 GET。
 */
export const DATA_PATHS: Record<string, string[]> = {
  'admin-dashboard': ['/api/admin/statistics', '/api/analytics/dashboard'],
  'student-dashboard': ['/api/student/analytics/visualization', '/api/student/courses', '/api/student/dashboard'],
  'teacher-dashboard': ['/api/courses/my', '/api/teacher/dashboard/v2'],
  'admin-courses-table': ['/api/courses'],
  'admin-system-form': ['/api/system-configs'],
  'student-courses-modal': ['/api/student/courses', '/api/student/dashboard'],
  'student-learning-center': ['ALL'],
  'student-knowledge-graph': ['/api/knowledge', '/api/student/courses'],
  'admin-governance-analytics': [
    '/api/analytics/users/growth',
    '/api/analytics/heatmap/platform',
    '/api/analytics/dashboard',
    '/api/analytics/courses/ranking',
  ],
  'admin-profile': ['ALL'],
}

/** 外壳与会话类端点：把它们也打空会得到 /login 重定向或主题丢失，红得无法归因 */
export const SHELL_KEEP_ALIVE_PATHS = ['/api/auth/', '/api/notifications/']

/* ============================================================================ *
 * 可比性前置：anchors 与 sensitivity 共用同一份（Task 4c Step 1g "口径共享"）
 * ============================================================================ *
 * 原来 anchors 有 5 条（时刻、data-theme、#app 非空、.error-boundary、/403|/503、URL 未跳 /login），
 * 而探针自称"同口径"实际只有 3 条——少的那 3 条正是"未登录/错误页/403"三种"看着像页面"的落点。
 * 探针在那三种画面上量出来的读数长得和真读数一模一样，却是彻底的假数（上一轮就差点这么喂台账）。
 * 现在两边都调这一个函数，缺一条两边一起红，不可能只有一边瞎。
 */
export async function assertGenericGuards(page: Page, anchor: AnchorSpec, theme: ThemeName): Promise<void> {
  const fail = (msg: string): never => {
    throw new Error(`[可比性] ${anchor.slug}/${theme}：${msg}`)
  }
  const now = await page.evaluate(() => Date.now())
  if (now !== SNAPSHOT_PAGE_TIME_MS) fail(`截图时刻 ${now} ≠ 常量 ${SNAPSHOT_PAGE_TIME_MS}（多推进/少推进了 ${now - SNAPSHOT_PAGE_TIME_MS}ms，帧数不可比）`)
  const attrs = await page.evaluate(() => ({
    theme: document.documentElement.getAttribute('data-theme') ?? '',
    appEmpty: (document.querySelector('#app')?.textContent ?? '').trim() === '',
    errorBoundaries: document.querySelectorAll('.error-boundary').length,
    path: location.pathname,
    href: location.href,
  }))
  if (attrs.theme !== theme) fail(`data-theme="${attrs.theme}" 与要求的 ${theme} 不符——主题没注入成功，两张图会是同一张图换了名字`)
  if (attrs.appEmpty) fail('#app 是空的（SPA 白屏）')
  if (attrs.errorBoundaries > 0) fail(`命中 ${attrs.errorBoundaries} 个 .error-boundary（报错边界页长得像正常页）`)
  if (anchor.role !== 'ANON') {
    if (attrs.href.includes('/login')) fail(`被守卫踢回 /login（会话没带上）：${attrs.href}`)
    if (/\/(403|503)$/.test(attrs.path)) fail(`落在 ${attrs.path}：后端不可达或角色不符也会渲染成"正常页"，四条通用守卫一条都不拦`)
  }
}

/** 通用守卫 + 该锚点自己的数据到位断言。anchors 与 sensitivity 都调这一个，不许各自抄一份。 */
export async function assertComparableRender(page: Page, anchor: AnchorSpec, theme: ThemeName): Promise<void> {
  await assertGenericGuards(page, anchor, theme)
  await assertAnchorArrival(page, anchor.slug)
}

/**
 * 12 个锚点是 spec §4 Phase 0a 选定的"每一类版式的代表"，不是随便 12 页：
 * 工作台 ×3 端（卡片+统计+图表混排）、表格页代表（54 个文件在用 el-table）、
 * 表单页代表、Modal 复用代表（12 个 ui/Modal 调用点之一）、图谱/图表代表各一、
 * 个人中心、错误页。LearningCenter 需 :id，故不在这里写死数字——种子库里
 * 学生的第 1 门课未必存在，硬编 /student/courses/1 会截出一张空态图当基线，
 * 之后所有真实改版都被判为"改善"。改为运行时取列表首条真实链接。
 *
 * 路径逐条核过 src/router/index.ts 的**路由表**（不是文件目录）：
 * admin 端的 children 是平铺的，`views/admin/governance/Analytics.vue` 挂在
 * router/index.ts:187 的 `path: 'analytics'` 上，真实 URL 是 `/admin/analytics`；
 * 写成 `/admin/governance/analytics` 会被 :562 的 `/:pathMatch(.*)*` 兜住，
 * 截出来是一张 404 页——而 404 页恰好也是"渲染正常、非空壳"，断言全绿。
 *
 * 图谱锚点带 `?courseId=`：默认的 `courseList[0]`（= /api/courses 列表首项 4060）
 * 在种子里 **0 个知识点**，不带参数截到的永远是占位图（上一轮缺陷 D-3）。
 * 查询参数有效是因为 useStudentKnowledgeGraph.ts:150 先读 `route.query.courseId`，
 * `:155` 的 courseList[0] 只是它的兜底。
 */
export const ANCHORS: readonly AnchorSpec[] = [
  { slug: 'login', title: '登录页', role: 'ANON', path: '/login' },
  { slug: 'admin-dashboard', title: '管理工作台', role: 'ADMIN', path: '/admin/dashboard' },
  {
    slug: 'student-dashboard',
    title: '学生工作台',
    role: 'STUDENT',
    path: '/student/dashboard',
    /**
     * 后端按 LocalDate.now() 回推 14 天窗口并重新取数：
     * StudentVisualizationComponent.java 的 getVisualizationData → buildDurationTrend(today.minusDays(13))
     * 与 buildHeatmapData(today.minusDays(6))，前端 SdVizSection.vue 把 `t.date.slice(5)` 直接铺进 xAxis.data。
     * page.clock 管得到浏览器、管不到服务端 ⇒ 每天零点这张基线集体漂一次（实测见 SNAPSHOT_THRESHOLDS 注释）。
     */
    pinPaths: ['/api/student/analytics/visualization'],
  },
  { slug: 'teacher-dashboard', title: '教师工作台', role: 'TEACHER', path: '/teacher/dashboard' },
  { slug: 'admin-courses-table', title: '课程管理（表格代表）', role: 'ADMIN', path: '/admin/courses' },
  { slug: 'admin-system-form', title: '系统设置（表单代表）', role: 'ADMIN', path: '/admin/system' },
  { slug: 'student-courses-modal', title: '学生课程（弹层代表）', role: 'STUDENT', path: '/student/courses' },
  { slug: 'student-learning-center', title: '学习中心（壳页代表）', role: 'STUDENT', path: '__FROM_COURSE_LIST__' },
  {
    slug: 'student-knowledge-graph',
    title: '知识图谱（图谱代表）',
    role: 'STUDENT',
    /**
     * 4001 = Python程序设计（A班）。选它不是照抄评审建议，是逐课实测出来的：
     * 拿学生会话打 GET /api/knowledge/relations/graph/{id}（就是 loadGraphData :170
     * 调的那个端点），38 门课里 4001 有 8 节点 10 边，而列表首项 4060 与 4047、
     * 以及 2/3/4/5/6 都是 0 节点 0 边；4007 更多（65 节点 120 边）。
     * 8 个节点在 520px 高的面板里画得开、10 条边与 8 组标签都看得清，
     * 所以取 4001（节点圆点本身不画是另一回事，见 task-4-report 的缺陷 D-9，与选哪门课无关）；
     * 随机种子由 pinRandom 钉、步数由 settlePage 的预算钉，
     * 入库前由 requirePaintedGraph 验画布真有墨。
     */
    path: '/student/knowledge-graph?courseId=4001',
    pinRandom: true,
    requirePaintedGraph: true,
  },
  {
    slug: 'admin-governance-analytics',
    title: '治理分析（图表代表）',
    role: 'ADMIN',
    path: '/admin/analytics',
    /**
     * AdminAnalyticsComponent.getUserGrowthTrend：endDate = LocalDate.now()、
     * startDate = endDate.minusDays(days - 1)，且同窗口还重新取数；
     * 消费端 useAnalytics.ts 把后端 d.date 直接铺进 xAxis.data ⇒ 与 student-dashboard 同因。
     */
    pinPaths: ['/api/analytics/users/growth?days=30'],
  },
  { slug: 'admin-profile', title: '个人中心', role: 'ADMIN', path: '/admin/profile' },
  { slug: 'error-404', title: '404', role: 'ANON', path: '/__no-such-route__' },
]
