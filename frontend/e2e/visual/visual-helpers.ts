import type { Locator, Page } from '@playwright/test'
import { createHash } from 'node:crypto'
import { assertAnchorArrival } from './data-arrival'

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
 * 主题必须在导航前落到 localStorage：useTheme 在**模块作用域**读一次初值
 * （`src/composables/useTheme.ts` 里那条 `const theme = ref(localStorage.getItem(THEME_KEY) || 'dark')`，
 *  定位：`grep -n "const theme = ref" src/composables/useTheme.ts`；"默认值是 dark"这件事由计划
 *  Global Constraints 明写，别按行号引它），页面起来后才写入就来不及了。
 * addInitScript 而非 evaluate——
 * Chromium 新版对 about:blank 的 localStorage 访问抛 SecurityError，
 * 且 addInitScript 对每次导航幂等（`e2e/test-helpers.ts` 的 `setLocalStorageAuth` 上方那段实测结论）。
 * ⚠️ 引用 `src/**` / `e2e/**` 一律按**声明名 / 选择器名 / 函数名**引，不写行号：Phase 1/2/4/5 会往
 *    这些文件里插行，行号在改动落地那一刻就指到别处（计划 Global Constraints 的"引用禁行号"条）。
 *    指向 `node_modules/**` 的行号**不在此列**——那不是我们改的文件，且它随安装的版本被锁死。
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
 * - `install({ time, pauseAt })`：`pauseAt` **不是这个版本 install 的选项**——
 *   `playwright-core/types/types.d.ts:20486-20492` 里 `install(options?: { time?: number|string|Date })`
 *   的参数对象只有 `time` 一个键；传进去被静默忽略，实测照样漂 416ms；且它过不了 vue-tsc 的多余属性检查。
 * - `install({ time })` → `runFor(5000)` → `pause()`：这个版本的 Clock 根本没有 `pause()`。
 *   证据（按**当前安装的这份** types.d.ts 逐行核过，旧注释写的 `:20466-20480` 撑不住这条——那是 `install()` 的 JSDoc）：
 *   `export interface Clock` = `types.d.ts:20450-20581`，区间内的方法只有
 *   `fastForward:20465`、`install:20486`、`pauseAt:20525`、`resume:20530`、`runFor:20545`、
 *   `setFixedTime:20564`、`setSystemTime:20580`——**确无 `pause()`**；
 *   文件里那个 `pause(): Promise<void>` 在 `:4050`，属 `Page` 接口，不是 Clock。
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
 * （被 fake 的清单在真正执行的那份代码里：`playwright-core/lib/coreBundle.js:12023` 内嵌的
 * `packages/injected/src/clock.ts` 源串——`setTimeout / setInterval / Date / performance /
 * requestAnimationFrame / cancelAnimationFrame`，types.d.ts 的 JSDoc 不列这些，故不引它），
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
 * Playwright 的 clock 只 fake 时间类 API（被 fake 的清单在真正执行的那份代码里：
 * `playwright-core/lib/coreBundle.js:12023` 内嵌的 `packages/injected/src/clock.ts` 源串——
 * `setTimeout / setInterval / Date / performance / requestAnimationFrame / cancelAnimationFrame`；
 * 该源串里**没有 Math.random**，仓库内也没有单独的 clock.ts 磁盘文件可引），所以冻结时钟挡不住随机源；
 * 但**全局**钉会把其它页面里
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
 * （mimeType 由 **arg 的扩展名**决定：`playwright/lib/matchers/expect.js:12437`
 * `getMimeTypeForPath(basename(this.expectedPath))`；无扩展名时在
 * `expect.js:12595-12597` 的 `screenshotType` 门卫就抛出，压根进不到比对），
 * 而模板里的 `{ext}` 正是从 arg 的扩展名反推出来的（`playwright/lib/worker/workerProcessEntry.js:1266`
 * `basename(subPath, ext)`，同一段路径解析在 `:1244-1267`——旧注释写的 `:1258` 是错的行号，
 * 本次按当前安装的这份逐字核过改定），arg 带 `.png` 时 `{arg}` 仍是去掉扩展名的部分，
 * 落盘名 `x-light.png`，不会出现 `.png.png`。
 * 结论：**消费方在调用处补 `.png`**（见 anchors.spec.ts），本函数保持无扩展名返回值——
 * 这是 helpers.spec.ts 已钉住的契约，Task 5+ 同样按 `snapName(..) + '.png'` 用。
 */
export function snapName(slug: string, theme: ThemeName): string {
  return `${slug}-${theme}`
}

/**
 * 全仓唯一截图阈值来源。**这两个数是测量结果，不是偏好**。
 * 量程账本（这两个数怎么量出来的、各档计数与字节差、免检额算术、保证什么不保证什么、收紧的过程、
 * 日期漂移那一块）已按 4c-fix-C 第 15 项整块搬进
 * `docs/visual-upgrade/evidence/snapshot-thresholds-ledger.md`——动这两个数之前先读那里，
 * 改完必须重跑 sensitivity 全矩阵并把读数写进 `docs/visual-upgrade/LEDGER.md`。
 * 【唯一来源的机械约束（不是散文）】`anchors.spec.ts` 与 `sensitivity.spec.ts` 都从这里取值、
 * 不写字面量；`tools/measure-diff.cjs` 的 `deliveryThresholds()` 从这一行现取；
 * 机械版本 = `helpers.spec.ts` 的「量程常量钉死」与「阈值只有一个来源」两条用例。
 */
export const SNAPSHOT_THRESHOLDS = { threshold: 0, maxDiffPixelRatio: 0.0001 }

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

/** 取证产物所属那一轮的数据路径（哪条路径取的数 + 哪一档尺）。 */
export interface EvidenceScale {
  /** 这一轮关掉了载荷钉死 = 真走后端，量的是漂移本身，不是交付口径 */
  nopin?: boolean
  /** PW_PIN_SHIFT_DAYS：模拟服务端第 +k 天 */
  shift?: number
  /** 这一轮实际用的 threshold */
  t?: number
  /** 这一轮实际用的 maxDiffPixelRatio */
  r?: number
}

/**
 * 取证产物（**PNG 文件名**与 `probe-results.json` 的 key）共用的那一段口径标签。
 *
 * 【为什么要有这一条】`890e3c0` 只给 JSON 的 key 打了标，PNG 的文件名没打，于是
 * "钉住的读数"与"`PW_VISUAL_NOPIN=1` 关掉钉死后的漂移读数"**共用同一个文件名**：
 * 后跑的那轮把前一轮的图原地覆盖掉。症状当时就在盘上——`--table` 同一格里并列出
 * `student-dashboard-light-noise-probe-captured.png 计数 321 … md5 不同`（NOPIN 轮的）与
 * `student-dashboard-light.png 计数 0 md5 相同`（钉住的），读的人以为量出了两种结论，
 * 其实量的是两件事的两张图，其中一张已经被删掉。JSON 那侧当时修了，PNG 这侧没修。
 * 【为什么放在 helper 而不是各 spec 各写一份】标签必须由**一个函数**生成：
 * JSON 的 key 与 PNG 的文件名一旦各写一份就会漂开，而"漂开"的表现正是上面那条——
 * 一份带标签、一份不带，覆盖又回来了。sensitivity 的 `evidenceKey()` 现在就是转发到这里。
 * 【交付口径 = 空串】钉住 + 交付 t/r 时不带任何标签，于是：
 *   ① 已入库的引用不受影响（`tools/kat-falsification.cjs` 就写死了
 *      `evidence/admin-dashboard-light-radius-probe-captured.png` 这个名字）；
 *   ② 文件名仍在 `e2e/visual/tools/evidence/` 目录规则之下（`frontend/.gitignore:60` 排的是**整个目录**），
 *      不会因为加了 `@tag` 而变成未追踪但也没被忽略的名字——探针的 `arg`（`*-probe.png`，落在
 *      `__snapshots__/` 里那条）则**不能**打标：`.gitignore:53-55` 的模式是结尾匹配，
 *      `...-probe@nopin.png` 会脱出 `*-probe.png` 规则、把测量产物变成待入库文件。
 *      标签只加在取证文件名上，不加在 Playwright 的期望图上。
 */
export function evidenceTags(o: EvidenceScale): string[] {
  const tags: string[] = []
  if (o.nopin) tags.push('nopin')
  if ((o.shift ?? 0) > 0) tags.push(`shift+${o.shift}`)
  if (Number.isFinite(o.t) && o.t !== SNAPSHOT_THRESHOLDS.threshold) tags.push(`t=${o.t}`)
  if (Number.isFinite(o.r) && o.r !== SNAPSHOT_THRESHOLDS.maxDiffPixelRatio) tags.push(`r=${o.r}`)
  return tags
}

/** 直接拼进文件名的那一段：`''`（交付口径）或 `@nopin` / `@shift+1` / `@t=0.01,r=0` 之类。 */
export function evidenceTagSuffix(o: EvidenceScale): string {
  const tags = evidenceTags(o)
  return tags.length > 0 ? `@${tags.join(',')}` : ''
}

/**
 * 写一格取证读数（`probe-results.json` 那本账）。纯函数，便于 helpers.spec.ts 直接断言它的语义。
 *
 * 【默认整格替换，不再 merge——这是 A2 的另一半】原先这里是 `{...旧格, ...新}`，
 * 于是**只在"这一格没测成"时才该存在的字段（`skipped:true` + `reason`）会活到后一条同格读数上**，
 * 反向也一样（`pixelmatchCount`/`verdict` 会活到一条 skipped 记录上）：
 * 读 JSON 的人要么把量到了的一格当成没量到，要么把没测成的一格当成有读数。
 * `890e3c0` 把 key 打上标签之后**跨口径**的覆盖没了，但**同一格跨轮次**的字段残留还在，
 * 因为 key 相同的那两笔写的本来就是同一次测量的不同阶段。默认替换把残留清掉。
 * 【唯一还该 merge 的地方 = 同一轮的第二笔】探针必须"先落读数、后落判定"
 * （判绿/判红都必须在读数已经落盘之后），那一笔只往本轮自己的读数上补三个字段，
 * 安全的前提是第一笔刚刚把整格替换过。
 */
export function applyEvidenceWrite(
  all: Record<string, unknown>,
  key: string,
  entry: Record<string, unknown>,
  mergeIntoThisRunCell = false,
): Record<string, unknown> {
  const prev = mergeIntoThisRunCell ? ((all[key] as Record<string, unknown> | undefined) ?? {}) : {}
  return { ...all, [key]: { ...prev, ...entry, at: new Date().toISOString(), key } }
}

/**
 * **本地**日历日。服务端那侧是 `LocalDate.now()`（JVM 默认时区），
 * 用 toISOString 的 UTC 日会在"本地已跨天、UTC 还没跨天"的时段（本地 00:00–08:00，+08 区）
 * 少算一天——少算的那天正好把回拨量弄错，fixture 就复现不出基线画面。
 */
export function localDay(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`
}

/** 冻结时钟那一天的日历日：与 freezeClock 共用 CLOCK_EPOCH，别在这儿另写一个日期 */
export const CLOCK_DATE = localDay(new Date(CLOCK_EPOCH.getTime() + CLOCK_WARMUP_MS))

/**
 * **基线日**（A4 的另一半：把这件事从"隐含假设"变成"被断言的事实"）。
 *
 * 【原先没人断言什么】录制端把 fixture 里所有 ISO 日期整体回拨到 `CLOCK_DATE`（`attachPayloadRecorder`
 * 的 `backshiftDays = serverDate − CLOCK_DATE`），回放时逐字供那份回拨过的载荷。
 * 于是"**钉住后必须与已提交基线 md5 相等**"这条判据成立的前提是：
 * **那 22 张基线本身做于 `CLOCK_EPOCH` 那一天（2026-10-18）**。这件事原先只写在注释里，
 * 没有任何断言：如果哪天有人把 `CLOCK_EPOCH` 往后挪一天（为了让相对时间文案"今天/昨天"继续对得上），
 * 基线拍的是一天、fixture 归一到另一天 ⇒ 4 张日期图**永久红**，而红得像"产品把图表改了"，
 * 重录 fixture 也救不回来（重录归一到新的 CLOCK_DATE，基线仍是旧的）——这是最难归因的一种红。
 * 【现在写死成一个具名常量，并三处断言它】
 *   ① 本文件导入期：`CLOCK_DATE === BASELINE_DAY`（改 CLOCK_EPOCH 必须同时决策基线日，否则整套件起不来）；
 *   ② `loadPinManifest()`：fixture 的 `clockDate` 必须等于它（fixture 归一到哪一天是**读 manifest**，不是硬推）；
 *   ③ `loadPinManifest()`：`backshiftDays` 必须真等于 `serverDateAtRecord − clockDate`（防手改 fixture）。
 * 行为证明见 Task 4c-fix-A 报告 A4（把 ① 弄红一次、把 ② 弄红一次）。
 * 【它管的只有"日期"】基线的**画面内容**由那 22 张 sha256 清单管（helpers.spec.ts），
 * 两边管的是不同的事，别把这条当"基线没被换过"的证据。
 */
export const BASELINE_DAY = '2026-10-18'

/** ① 导入期就钉住：冻结时钟的那一天必须就是基线拍的那一天 */
if (CLOCK_DATE !== BASELINE_DAY) {
  throw new Error(
    `[口径] CLOCK_DATE=${CLOCK_DATE} 而 BASELINE_DAY=${BASELINE_DAY} —— 两者必须同一天，否则日期锚点的\n` +
      `  "钉住后与基线 md5 相等"永远不可能成立（基线拍于 BASELINE_DAY 那天，fixture 却被归一到 CLOCK_DATE 那天）。\n` +
      `  两个合法处置：① 把 CLOCK_EPOCH 改回去；② 有意换基线日 ⇒ 把 BASELINE_DAY 一起改，并且**必须重拍全部 22 张基线**\n` +
      `  （那是 --update-snapshots 级的动作，要走 global-setup 那道显式承认闸门，还要同步 helpers.spec.ts 的 sha256 清单）。`,
  )
}

/* ============================================================================ *
 * 可比性前置：anchors 与 sensitivity 共用同一份（Task 4c Step 1g "口径共享"）
 * ============================================================================ *
 * 原来 anchors 有 5 条（时刻、data-theme、#app 非空、.error-boundary、/403|/503、URL 未跳 /login），
 * 而探针自称"同口径"实际只有 3 条——少的那 3 条正是"未登录/错误页/403"三种"看着像页面"的落点。
 * 探针在那三种画面上量出来的读数长得和真读数一模一样，却是彻底的假数（上一轮就差点这么喂台账）。
 * 现在两边都调这一个函数，缺一条两边一起红，不可能只有一边瞎。
 */
/**
 * 通用守卫的**逐条理由清单**（4c-fix-C 的 C2 步第 13／14 项把它从"第一条即抛"改成"全量收集"）。
 * 【原先缺什么】`assertGenericGuards` 命中第一条就抛，而它内部的次序是
 * **先查 `data-theme`、后查 `#app` 非空** ⇒ 调用方拿到的那句理由**分不开**
 * "挂载中途崩"与"渲染成空态"这两种形状（第 13 项点名的正是这一点，第 14 项 ① 要的是完整列表）。
 * 【现在】把所有判据都跑完、把失败理由**逐条**返回；空数组＝这一页四条通用判据全过。
 * 判据本身、判据次序、每条的措辞**一字未改**——改的只是"第一条就抛"这个出口形状。
 * 【为什么仍然只有一处判据】collect 与 assert 在同一份代码里，anchors 与 sensitivity 共用，
 * 不许任何一侧再抄一份"看起来一样"的尺（Task 4c Step 1g 那条口径没变）。
 */
export async function collectGenericGuardReasons(
  page: Page,
  anchor: AnchorSpec,
  theme: ThemeName,
): Promise<string[]> {
  const reasons: string[] = []
  const now = await page.evaluate(() => Date.now())
  if (now !== SNAPSHOT_PAGE_TIME_MS)
    reasons.push(
      `截图时刻 ${now} ≠ 常量 ${SNAPSHOT_PAGE_TIME_MS}（多推进/少推进了 ${now - SNAPSHOT_PAGE_TIME_MS}ms，帧数不可比）`,
    )
  const attrs = await page.evaluate(() => ({
    theme: document.documentElement.getAttribute('data-theme') ?? '',
    appEmpty: (document.querySelector('#app')?.textContent ?? '').trim() === '',
    appChildCount: document.querySelectorAll('#app > *').length,
    errorBoundaries: document.querySelectorAll('.error-boundary').length,
    path: location.pathname,
    href: location.href,
  }))
  if (attrs.theme !== theme)
    reasons.push(
      `data-theme="${attrs.theme}" 与要求的 ${theme} 不符——主题没注入成功，两张图会是同一张图换了名字`,
    )
  if (attrs.appEmpty)
    reasons.push(`#app 是空的（SPA 白屏，#app 直接子节点数 ${attrs.appChildCount}）`)
  if (attrs.errorBoundaries > 0)
    reasons.push(`命中 ${attrs.errorBoundaries} 个 .error-boundary（报错边界页长得像正常页）`)
  if (anchor.role !== 'ANON') {
    if (attrs.href.includes('/login'))
      reasons.push(`被守卫踢回 /login（会话没带上）：${attrs.href}`)
    if (/\/(403|503)$/.test(attrs.path))
      reasons.push(
        `落在 ${attrs.path}：后端不可达或角色不符也会渲染成"正常页"，四条通用守卫一条都不拦`,
      )
  }
  return reasons
}

export async function assertGenericGuards(
  page: Page,
  anchor: AnchorSpec,
  theme: ThemeName,
): Promise<void> {
  const reasons = await collectGenericGuardReasons(page, anchor, theme)
  if (reasons.length === 0) return
  // 整份列表一起抛：调用方要么只取第一行（旧口径的读数不变），要么逐条打印（第 14 项 ① 要的就是这个）。
  throw new Error(
    `[可比性] ${anchor.slug}/${theme}：通用守卫命中 ${reasons.length} 条理由（逐条列出，不再只报第一条）\n` +
      reasons.map((r, i) => `  ${i + 1}. ${r}`).join('\n'),
  )
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
 * `src/router/index.ts` 里 **admin 那条 children 的 `path: 'analytics'`** 上，真实 URL 是 `/admin/analytics`。
 * ⚠️ 这里**不写行号**，而且行号本来就不可用：`grep -n "path: 'analytics'" src/router/index.ts`
 * 本机有 4 处命中（教师 cockpit ×2、教师 tools ×1、admin governance ×1），按行号引必然引错一条，
 * Phase 4/5 改动路由表时全体位移。现查现用（输出直接指出哪一条是 admin 的）：
 *   `grep -n -A1 "path: 'analytics'" src/router/index.ts | grep -B1 governance`
 * 写成 `/admin/governance/analytics` 会被路由表**末尾那条 catch-all `path: '/:pathMatch(.*)*'`** 兜住，
 * 截出来是一张 404 页——而 404 页恰好也是"渲染正常、非空壳"，断言全绿。
 *
 * 图谱锚点带 `?courseId=`：默认的 `courseList[0]`（= /api/courses 列表首项 4060）
 * 在种子里 **0 个知识点**，不带参数截到的永远是占位图（上一轮缺陷 D-3）。
 * 查询参数有效是因为 `src/composables/useStudentKnowledgeGraph.ts` 的 **`loadCourseList()`**
 * 先读 `route.query.courseId || route.params.courseId || route.params.id`，
 * 同一个函数里 `courseList[0].id` 那条 `else if` 分支只是它的兜底。
 */
export const ANCHORS: readonly AnchorSpec[] = [
  /**
   * 【Task 0（2026-10-21）实测：这一格没有未钉端点，出路① 对它无从下手】
   * 只读脚本量过一次：打开 `/login` 发出的 GET `/api/` **共 0 条**（匿名页，业务请求一条不发；
   * 非 GET 也是 0）。⇒ `pinPaths` **保持不写**——硬钉一个不存在的端点会被 `assertPinCoverage`
   * 当场判红（"钉了却没用上"），而把差集凑成非空更是自欺（简报「不要为了凑数硬钉」那条）。
   * 所以 §11.4 记的「login 暗格红过一次 425 px」**不归因于跨套件数据耦合**，
   * 也不得被 (a) 的解除顺手算成已解决：它是暗色首帧/渲染时序那一族的事（另案）。
   * 复核方式（工作目录 `frontend/`）：
   *   `npx playwright test -c test-results/pin-gap/pin-gap.config.ts`（一次性脚本，gitignore 目录）
   */
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
     *
     * 【Task 0（2026-10-21，出路①）：下面这一条从来不够，红不在它身上】
     * 准入条件 (a)「跑过 9 个流程 e2e spec 之后紧接着跑视觉套件，基线确定性变红」的根因是
     * **本锚点只钉住了一个端点的载荷，而这一页还消费四条没钉的 GET**（差集由只读脚本当场量出来，
     * 命令与读数见 `.superpowers/sdd/2026-10-21-onboarding-redesign/task-0-report.md` 步骤 1；
     * 台账 `P0a-PINGAP-01`）：统计卡来自 `/api/student/dashboard`、未读角标来自
     * `/api/notifications/unread-count`、课程卡来自 `/api/student/courses?page=1&size=100`、
     * 权限来自 `/api/auth/permissions`。流程套件写真后端数据 ⇒ 这四条的响应变 ⇒ 画上跟着变 ⇒ 基线跟不上，
     * 而被钉的那一条从头到尾没参与这次红（改前包围盒 x 1159..1252 / y 18..234 起于顶栏，
     * 明 152 px／暗 148 px，免检额 129.6 px ⇒ 超线）。
     * ⇒ 现在把这一页**实测发出的每一条 GET** 都钉住，跨套件数据耦合不再进画面。
     * 【代价与边界，读这条结论时必须带着】
     *  ① 钉全屏之后，本锚点的"数据确已到位"里凡来自这些端点的那一半**不再是真后端的证据**
     *     （payload-pin.ts 顶部那条"钉全屏就等于把数据到位一起钉死"的限制，本格现在适用面变宽了）；
     *     `DATA_ARRIVAL_CHECKS` 的 `.l1-card`／`.s-value`／`canvas` 三条本来就是**存在性**断言，
     *     R-CF1 早已把"被钉区域不作数据到位证据"写进 LEDGER `P0a-GUARD-02`，此处只是把范围扩到整页。
     *  ② 只钉 GET：`installPayloadPin` 与 `attachPayloadRecorder` 都只处理 GET，本锚点实测**没有**非 GET 的
     *     `/api` 请求（`nonGetApi` 为空），所以差集里没有"钉不了的漏网项"。
     *  ③ 键必须逐字等于实测的 `pathname+search`（`?page=1&size=100` 不是可省的修饰，
     *     少了 search 就是"钉了却没命中"，由 `assertPinCoverage` 当场红）。
     */
    pinPaths: [
      '/api/student/analytics/visualization',
      '/api/student/dashboard',
      '/api/student/courses?page=1&size=100',
      '/api/notifications/unread-count',
      '/api/auth/permissions',
    ],
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
     * 拿学生会话打 GET /api/knowledge/relations/graph/{id}（就是 `loadGraphData()` 里
     * 那条 `knowledgeRelationApi.getGraphData(...)` 的 await），
     * 38 门课里 4001 有 8 节点 10 边，而列表首项 4060 与 4047、
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
     *
     * 【Task 0（2026-10-21，出路①）】与 student-dashboard 同一条修法：这一页实测发出 7 条 GET，
     * 原先只钉 1 条 ⇒ 其余 6 条（仪表汇总 / 课程排行 / 平台热力 / 课程列表 / 未读角标 / 权限）
     * 照旧走真后端，流程套件一改后端状态这张基线就跟不上（暗格间歇红）。
     * 现在把实测发出的每一条 GET 都钉住。边界与代价同 student-dashboard 那三条
     * （① 被钉区域不再作"数据到位"的证据、② 只钉 GET 且本页无非 GET 请求、③ search 逐字参与键）。
     * 清单与 `data-arrival.ts` 的 `DATA_PATHS['admin-governance-analytics']` 独立对得上。
     */
    pinPaths: [
      '/api/analytics/users/growth?days=30',
      '/api/analytics/dashboard',
      '/api/analytics/courses/ranking',
      '/api/analytics/heatmap/platform?days=30',
      '/api/courses?page=1&size=100',
      '/api/notifications/unread-count',
      '/api/auth/permissions',
    ],
  },
  { slug: 'admin-profile', title: '个人中心', role: 'ADMIN', path: '/admin/profile' },
  { slug: 'error-404', title: '404', role: 'ANON', path: '/__no-such-route__' },
]
