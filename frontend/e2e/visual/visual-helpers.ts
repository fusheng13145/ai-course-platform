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
 * 全仓唯一截图阈值来源。**这两个数是测量结果，不是偏好**：
 * Task 4b 第 2 轮在 22 张真基线上逐级下探定出 `(0 / 0.0005)`；Task 4c Step 4 在**日期漂移被载荷钉死抵住之后**
 * 把它**收紧**到 `(0 / 0.0001)`（免检额 130 px）——三次独立全量 anchors 全绿，见下面"收紧的过程"。
 * 再动它们必须重新测量并重跑全量：**只许收紧**（连同 `helpers.spec.ts` 的钉桩单测一起改），
 * 放宽 = 直接退回"零 diff 不可解释"；未经验证的收紧 = 把噪声当成改动。
 *
 * 【唯一来源的机械约束（不是散文）】`anchors.spec.ts` 与 `sensitivity.spec.ts` 都从这里取值、不写字面量；
 * `tools/measure-diff.cjs` 的默认口径也是**从这里现取**（`deliveryThresholds()` 解析这一行，不复制数字）。
 * 机械版本 = `helpers.spec.ts` 的"量程常量钉死"与"阈值只有一个来源，消费侧不写字面量"两条单测。
 *
 * 【这两个数在 pixelmatch 里到底是什么】file:line 一律按**当前安装的** playwright-core 逐行核过：
 *   比较器**只把 threshold 交给 pixelmatch**：`coreBundle.js:7896-7898` ⇒ `includeAA` 取默认 false、alpha 取默认 0.1；
 *   逐像素闸 `|colorDelta| > 35215·t²`：`:7005`，YIQ 亮度项 `0.5053·y²`：`:7106`
 *     ⇒ 中性/明度类改动要求 **Δ > 264.0 × t**（0.2→52.8、0.05→13.2、0.02→5.28、0.01→2.64，均为 /255）。
 *       这条对**纯色度类**改动不精确成立；本套件的注入全是明度/中性类，够用。
 *   **t=0 仍不等于逐字节比**：`antialiased()` 在 `:7012` 被调用、函数体 `:7030-7060`，它把 AA 像素整类跳过；
 *       实测跳过率（同一对图，字节差 → 计数）：radius 浅色 5 卡页 1,983 → 1,394（**吃掉 30%**）、
 *       inset 暗色 5 卡页 3,610 → 107（**吃掉 97%**）、border 浅色 5 卡页 7,196 → 158（**吃掉 98%**）。
 *   免检额 = w·h·ratio，失败判据是 `count > 免检额`：`coreBundle.js:7903` 起。
 *   ⚠️ **两道闸不得混为一谈**（spec §6.12）；⚠️ **措辞受数值约束**（spec §6.16）：
 *   只有 **md5 相等**才许写"逐字节相同/一字不差"，其余一律写"计数 N（字节差 M）"。
 *
 * 【Task 4c Step 3/4 重测 · 交付口径 t=0 / r=0.0001（免检额 130 px）· 2026-10-19 本机】
 * 判据口径：期望图 = 该锚点**未注入**的已提交基线的逐字节副本；被测图 = 注入后的现场渲染
 *   （与回归**同一组**截图参数、同一个 mask、同一份可比性前置 `assertComparableRender`）。
 * 极性：**红 = 检出了**（成功的测量结果）；**绿 = 被免检额吸收 = 假绿**；SKIPPED = 选择器零命中（注入是空操作，不是"检不出"）。
 * 尺与跑测器同源的机械版本 = `sensitivity.spec.ts` 的"两条路径同判"与"跑测器计数 == 比较器计数"两条断言
 *   （不是注释里说同源就算同源）；每条读数同时写进 `tools/evidence/probe-results.json`（git-ignored，每次跑重生成）。
 * 锚点：5 卡页 = `admin-dashboard`、1 卡页 = `admin-courses-table`；`login` 没有 `.l1-card` ⇒ `.l1-card` 系档位在它上面恒 SKIPPED。
 * ⚠️ 计数只由 threshold 决定、与 ratio 无关 ⇒ 下表计数在 0.0005 与 0.0001 两档交付配置下是同一批实测；
 *    "在 r=0.0001 下"这一列只是把计数与 130 比。
 *
 *   档（注入）                    light 5卡/1卡                     dark 5卡/1卡                    r=0.0001 判定
 *   noise（不注入，地板）          0 / 0                             0 / 0                            地板=0
 *                                 **与基线 md5 相等、字节差 0**（login 明暗亦然，共 6 格皆是）⇒ 这里才许写"逐字节相同"
 *   gross  body brightness(.97)   1,234,282 / 1,243,769             1,234,504 / 1,247,775            检出 ≈9,500×
 *                                 Δmax 8/255（login 1,245,012 / 1,232,535）
 *   shadow  α .05/.06→.12/.13      55,903 / 21,059                   61,655 / 20,416                  检出 162×~475×
 *   （≈1.3 倍计划幅度）             Δmax 20 / 12；dark Δmax 9 / 7      字节差 65,670 / 23,785
 *   shadow-plan α .06→.10          62,700 / 21,603                   20,690 / 7,164                   检出 55×~484×
 *   （**正好 Phase 1 计划幅度**；    Δmax 7 / 6                       Δmax **2 / 1**；字节差 27,747 / 7,230
 *     暗色取同绝对增量 .5→.54）
 *   radius  16px→14px              1,394 / 198                       2,198 / 373                      **四格全检出** 1.5×~17×
 *                                 Δmax 19 / 18；字节差 1,983 / 280    Δmax 143 / 140；字节差 3,065 / 464
 *                                 （0.0005 下 1 卡页 198<648 曾漏检 ⇒ 收到 0.0001 的**直接收益**）
 *   inset   inset 0 1px 0 α.25     **0 / 0**                          **107 / 24**                     **恒漏检**
 *                                 浅色：与基线 **md5 相等、字节差 0** ⇒ 效应本身为 0（白卡上叠白高光），任何 ratio 都看不见
 *                                 暗色：Δmax 58/255、字节差 3,610 / 1,182，但分类器只计 107 / 24（AA 吃掉 97%）
 *   surface --surface→--surface-2  387,024 / 161,743                 398,286 / 172,049                检出 1,248×~3,072×
 *                                 Δmax 8 / 8                         Δmax 10 / 10
 *   border  --line→--line-strong   158 / **26**                       7,095 / 2,374                    三格检出（1.2×/55×/18×）
 *                                 Δmax 41；字节差 7,196 / 2,392        Δmax 98 / 92；字节差 7,249 / 2,402  **浅色 1 卡页仍漏检**（26 px）
 *
 *   重生成（每条 ~15s；需后端 8080 与 5175；工作目录 `frontend/`）：
 *     PW_LADDER=<档> PW_PORT=5175 npx playwright test -c playwright.visual.ts sensitivity              # 浅色
 *     PW_LADDER=<档> PW_PROBE_THEME=dark PW_PORT=5175 npx playwright test -c playwright.visual.ts sensitivity   # 暗色
 *   免检额与逐张读数的独立核对（不参与采集的只读比较器）：
 *     node e2e/visual/tools/measure-diff.cjs <基线.png> <取证图.png> --bands
 *     node e2e/visual/tools/measure-diff.cjs --manifest e2e/visual/__snapshots__/anchors.spec.ts --json
 *
 * 【收紧的过程（Step 4，每档都是整表 anchors 真跑）】
 *   r=0.0005（Task 4b 交付档）  → `34 passed / 2 failed`（红 = error-404 明暗，缺陷 D-1）
 *   r=0.0002（免检额 259 px）    → `34 passed / 2 failed`，1 次
 *   r=0.0001（免检额 130 px）    → `34 passed / 2 failed` × **3 次独立全量** ⇒ **采纳为交付配置**
 *   r=0（免检额 0）              → **未采纳**：唯一真抖动 `student-courses-modal` 的历史读数是暗色 46 px
 *                                  （Task 4b §R2.3；**本会话未重测它**），130 对它只有约 2.8 倍余量，
 *                                  0 会把这种 1~46 px 的抖动直接变成随机红灯。
 *   之所以现在能往下收：**4 张日期锚点的漂移已被载荷钉死抵住**（见下面的漂移块），
 *   而不是因为把抖动放宽了。
 *
 * 【Task 4b 的旧读数（引用时必须带它自己的配置——它们大多**不是**交付口径）】
 *   在**原生产配置 (0.2 / 0.002)** 下四档**全部 PASSED**：连"整页 3% 明度差"的哨兵档都不报警
 *     ——失明的主因是**逐像素闸**（t=0.2 ⇒ 要求 Δ > 52.8/255，而 gross 只有 8/255，一个像素都不计数），
 *     不是免检额；免检额是另一道闸，两者不得混为一谈（spec §6.12）。
 *   逐级下探矩阵（t=0.2/0.1/0.05/0.02/0，r=0）里被引用最多的几个数，**都是那个扫描口径**：
 *     gross t=0.02 起 1,015,094；shadow(×2 幅度) t=0.05 的 4,496（5 卡页）与 1 卡页要等到 t=0.02 的 4,704；
 *     radius t=0 的 1,394 / 198。
 *   ⚠️ 旧注释里"一次阴影增强恰好就是几千个弱差像素的形状"教的是**错的机理**：那句是 t=0.05 的 5 卡页读数 4,496，
 *     在交付口径 t=0 下同一注入是 **55,903 / 21,059**（浅）、在旧配置 t=0.2 下是 **0 个像素**。
 *   ⚠️ 旧注释把 gross 1,234,282 / shadow 55,903·21,059 / radius 1,394 / 漂移 321·295·110·111 / 抖动 13
 *     说成"与 Playwright 一字不差"。本次复核：**gross/shadow/radius 三组在交付口径下逐字复现**
 *     （1,234,282 / 1,243,769 / 55,903 / 21,059 / 1,394 / 198 全部由本会话的已入库命令重生成，见上表）；
 *     而 **漂移的 321/295/110/111 与抖动的 13 出自被否决的中间配置 `{0.02, 0}`**，不能与前者并列同级引用；
 *     "一字不差"这个措辞本身也已被 spec §6.16 禁用（只有 md5 相等才许写）。
 *   ⚠️ radius 的字节差 **1,983** 在已提交报告的表格里查无原始读数（spec §6.12 已记），
 *     本会话由 `measure-diff.cjs` 现场重算得 **1,983**（浅 5 卡页）——现在它有出处了。
 *
 * 【这组数现在保证什么、不保证什么（交付口径 t=0 / r=0.0001 = 免检额 130 px）】
 *   保证（每项都是**两主题 × 两版式**实测，见上表）：
 *     整页明度类 ≈9,500×；卡片阴影类**含 Phase 1 的精确幅度** `--sh-l1 .06→.10`（浅色 55×~484×、暗色 55×~160×）；
 *     局部表面色阶 `--surface→--surface-2`（1,248×~3,072×，两主题）；暗色描边加深（18×~55×）。
 *   不保证：
 *     ① **内高光 inset**：浅色**效应本身为 0**（白卡叠白高光，与基线 md5 相等）—— ratio 收到多少都看不见；
 *        暗色有效应但薄到 24~107 px（Δmax 58/255 而 AA 吃掉 97%）⇒ **仍低于 130**。
 *        ⇒ Phase 1 对 inner highlight **两主题都不得报"零 diff"**（spec §6.15 ④），判据改非像素的：
 *           逐卡 `getComputedStyle().boxShadow` 含 `inset 0 1px 0` + 对该 1px 行做定向裁剪比对。
 *     ② **1px 描边在单卡页**（26 px）与**任何 < 130 px 的形状**。
 *     ③ 圆角到 0.0001 只是"刚过线"：单卡页 198 px = 1.5× 余量，且它的上界是**几何定的**
 *        （16→14px 只重绘四角弧，4·(π/4)(16²−14²) = 189 px，实测 198 已核实）
 *        ⇒ Phase 2 的准入门不是继续压 ratio，而是**给圆角配非像素判据**（逐卡 `borderRadius` 计算值等式 + 角部定向裁剪）。
 *   任何**放宽**这两个数的改动必须重跑 sensitivity 全矩阵并把读数写进台账（`docs/visual-upgrade/LEDGER.md` 已入库）；
 *   **收紧**同样要跑：每下调一格都要整量 anchors 重跑并确认 22 张仍绿（Step 4 就是三次独立全量才采纳的）。
 *
 * 【门禁现在每天还到期吗？——日期漂移块，Task 4c Step 1】
 *   旧的"每天零点集体漂一次"这件事**已由载荷钉死接管**（`installPayloadPin` + `__fixtures__/*.json`，
 *   `route.fulfill` 供数 ⇒ 服务端 `LocalDate.now()` 到不了被比对的那份载荷）。
 *   实测（服务器日 = 基线次日 = 2026-10-19，逐数带口径；下面每个数各自的 t/r 是 Step 1f 要求的，不是可选的）：
 *   ① **钉住那组**：4 张日期锚点与基线 **md5 相等、字节差 0、计数 0**
 *      （**t=0 / r=0 口径**——这条走 `assertZeroDiff`，比较器被显式传 `--threshold=0 --ratio=0`，
 *       见 anchors.spec.ts 的 `measureAgainstBaseline`；md5 与字节差本来就不看 ratio，计数在 t=0 下也不看，
 *       所以这组数与交付 ratio 是 0.0005 还是 0.0001 **无关**）。
 *   ② **关掉钉死（`PW_VISUAL_NOPIN=1`）那组**：**计数 321 / 字节差 618**（student 明暗同值）与
 *      **计数 112 / 字节差 222**（analytics 明暗同值），差异带 y 537..544 / y 696..703。
 *      ⚠️ **这两组数采于 `t=0 / r=0.0005` 那一轮**（Step 1 做的时候交付档还是 0.0005 ⇒ 免检额 648 px；
 *      出处：spec §6.12 末"漂移那几轮跑在 r=0.0005"、task-4c-report §5.2 表 + §5.3 第 2 条）。
 *      计数只由 threshold 决定，所以 321/112 在今天的 0.0001 下**是同一个数**；
 *      但**"它被判绿还是判红"是 ratio 的性质**：321 在那一轮（648）绿、在这一档（129.6）红；
 *      112 在两档都绿（112 < 129.6 < 648）。⇒ 把 321 或 112 直接除以 129.6 就是误读，
 *      上面阶梯表那条"计数与 ratio 无关，只有与 130 比的那一列才是 0.0001 口径"说的正是这件事。
 *   ⇒ 抵住的是真的，不是巧合。
 *   重生成：`PW_LADDER=noise PW_PROBE_SLUGS=student-dashboard,admin-governance-analytics PW_VISUAL_NOPIN=1
 *            PW_PORT=5175 npx playwright test -c playwright.visual.ts sensitivity`（去掉 NOPIN 即钉住那组）
 *   ⚠️ **`PW_VISUAL_NOPIN` 只被上面那条探针命令认**：把它拼到 `anchors` 上是**无效且有害**的，
 *      `anchors.spec.ts` 现在在导入期直接抛（A3）。原因：探针这条读的是"不注入也非零"的**计数**，
 *      而 anchors 只有一条 r=0.0001（免检额 129.6 px，**当前交付配置**）的像素判据 + 挂在 `if (pinState)` 里的
 *      `assertZeroDiff`——NOPIN 下钉桩没装 ⇒ 判据整个消失，112 px 那格（analytics 的真实漂移，
 *      **t=0 / r=0.0005 那一轮的计数**）会**报绿**。
 *      "静默接受一个它不实现的开关"因此不是小毛病，它给的是反向对照的反面结论。
 *   ⚠️ **换来的新盲区要一起记账**：被钉的那 2 个端点从此**退出像素治理**——产品若改了这两个端点的数据结构，
 *      本套件不会红，要红得先重录 fixture（重录会让画面与已提交基线不符，那才是真红）。
 *      所以"钉住"不是一劳永逸：`assertPinCoverage` 钉住"页面必须真消费被钉端点"，`assertZeroDiff` 钉住
 *      "钉了以后必须仍与基线逐字节相同"，两者共同把"悄悄退回真后端"变成红。**门禁到期这件事没有消失，
 *      只是从"每天必然红一次"换成了"若 fixture 与产品脱节就红"。**
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

/* ============================================================================ *
 * 载荷钉死（payload pin）——Task 4c Step 1 的"首选路线"，不是遮罩
 * ============================================================================ *
 * 【为什么不用遮罩】评审给的算术决定了这一点，不能靠直觉：
 *   22 张基线实测全部 1440×900 = 1,296,000 px（这件事**从今天起由断言管**：
 *   `helpers.spec.ts` 的"每张已提交基线都是 1440×900，且免检额算式与注释一致"逐张读 IHDR，
 *   并把 `1,296,000 × SNAPSHOT_THRESHOLDS.maxDiffPixelRatio` 与注释里引用的免检额对齐——
 *   在这之前它只是"真"，没有任何东西钉着它）。
 *   Task 4b 当时交付档 **r=0.0005** ⇒ 免检额 648 px，
 *   而 student-dashboard **一天的**日期漂移就是**计数 321 / 字节差 618（t=0 / r=0.0005 口径）**
 *   （对 648 的余量 5%，不是原先以为的 2 倍）。
 *   Task 4c 复测：两个主题各 **计数 321 / 字节差 618**（student-dashboard）与
 *   **计数 112 / 字节差 222**（admin-governance-analytics），**同样是 t=0 / r=0.0005 那一轮的数**
 *   （计数只由 threshold 决定，所以两档交付配置下数值不变；变的是"这一格判绿还是判红"），
 *   差异带 y 537..544 / y 696..703，与 4b 的读数同带同量级。重生成（**必须带 NOPIN**——
 *   装了钉死这一轮的漂移就是 0，那不是"漂移没发生"而是"漂移被抵住"，两件事别混）：
 *   `cd frontend && PW_LADDER=noise PW_PROBE_THEME=light PW_VISUAL_NOPIN=1 \
 *      PW_PROBE_SLUGS=student-dashboard,admin-governance-analytics PW_PORT=5175 \
 *      npx playwright test -c playwright.visual.ts sensitivity`
 *   （把 NOPIN 换成 `PW_PIN_SHIFT_DAYS=1|2|3` 即是"服务端第 +k 天"的模拟，用于跨天外推；
 *    k=1 与真实 +1 天**不等价**（**计数 334 / 字节差 630（student）**、**计数 198 / 字节差 338（analytics）**，
 *    **t=0 口径**：探针那三轮按比较器 t=0/r=0 读，RED-5 那条 `anchors` 跑则是在**收紧后的 r=0.0001** 下由
 *    跑测器自报同样的 334/198 ⇒ 计数与 ratio 无关，两处的数本来就是同一批），
 *    所以模拟只用来证"漂移始终不归零"，不用来证幅度。
 *    ⚠️ **别把这里的 198 与阶梯表里 `radius` 在 1 卡页的 198 混成同一个数**——同值不同物
 *    （一个是模拟 k=1 的 analytics 漂移计数，一个是 16→14px 圆角的检出计数）；
 *    同理 618 在这里是 student 漂移的**字节差**，而 `docs/visual-upgrade/evidence/phase0-measurements.md`
 *    §6 逐张表 #6 里的 618 是**采样色数**。引用这类数必须同时给**档名 + 锚点 + t/r**。）
 *   遮掉标签带 = 那一带**永久**退出比较，
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
  /**
   * 这份载荷被归一到哪一天 = **基线日**（`BASELINE_DAY`）。
   * 回放时由 `loadPinManifest()` 核两件事：它必须等于 BASELINE_DAY，
   * 且 `backshiftDays` 必须真等于 `serverDateAtRecord − clockDate`（A4 的 ②③）。
   */
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
  /** 本轮回放的日期偏移（天）；>0 = 取证用的"服务端第 +k 天"模拟，非交付口径 */
  shiftDays: number
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

/**
 * A4 的 ②③，抽成纯函数好让 `helpers.spec.ts` 能直接把它弄红（`loadPinManifest` 要先读盘才走得到这里，
 * 拿真实文件做反向测试会污染入库的 `__fixtures__`）。
 * 只查 manifest **自己声明**的那三个日期字段，不碰 requests。
 */
export function assertManifestDates(
  m: Pick<PinManifest, 'serverDateAtRecord' | 'clockDate' | 'backshiftDays'>,
  where: string,
): void {
  // ② 这份载荷归一到哪一天，是 manifest **自己写的**，不是由 CLOCK_EPOCH 反推的。
  //    不等 = 这份 fixture 复现的是另一天的画面，钉住它去比基线永远不会 md5 相等。
  if (m.clockDate !== BASELINE_DAY) {
    throw new Error(
      `[载荷钉死] ${where} 的 clockDate=${JSON.stringify(m.clockDate)} 与 BASELINE_DAY=${BASELINE_DAY} 不符。\n` +
        `  含义：这份 fixture 把日期归一到了**另一天**，而 22 张基线拍于 ${BASELINE_DAY} ⇒ 日期锚点会永久红。\n` +
        `  处置只有两种，且都必须显式：① 重录（PW_RECORD_PAYLOADS=1 那条，落盘端按 BASELINE_DAY 回拨）；\n` +
        `  ② 有意换基线日 ⇒ 同时改 BASELINE_DAY 与 CLOCK_EPOCH，并**重拍全部 22 张基线**\n` +
        `     （走 global-setup 那道 --update-snapshots 显式承认闸门，同步 helpers.spec.ts 的 sha256 清单）。`,
    )
  }
  // ③ manifest 三个日期字段必须自洽：backshiftDays 真等于 serverDateAtRecord − clockDate。
  //    手改过 fixture（比如只挪 clockDate 想让 md5 对上）在这里就红，而不是变成一张"看不出来"的图。
  const implied = Math.round(
    (Date.parse(`${m.serverDateAtRecord}T00:00:00Z`) - Date.parse(`${m.clockDate}T00:00:00Z`)) / 86400000,
  )
  if (!Number.isInteger(m.backshiftDays) || m.backshiftDays !== implied) {
    throw new Error(
      `[载荷钉死] ${where} 的 backshiftDays=${m.backshiftDays} 与其自身日期不符（` +
        `serverDateAtRecord=${m.serverDateAtRecord} − clockDate=${m.clockDate} ⇒ 应为 ${implied}）` +
        `——这份 fixture 被手改过或落盘时算错，重录它。`,
    )
  }
}

/**
 * 读一份 fixture，并把**这份 fixture 自己声明的基线日**核对掉（A4 的 ②③）。
 * 原先这里只查形状（slug 对、requests 非空），日期字段一个字都不查
 * ⇒ "钉住后与基线 md5 相等"这条判据的前提（基线做于 `CLOCK_EPOCH` 那天）从来没人验过，
 * 前提破了表现为"永久红而且重录也救不回来"，归因方向会被带去查产品改动。
 */
export function loadPinManifest(slug: string): PinManifest {
  const file = fixturePathFor(slug)
  let raw: PinManifest
  try {
    raw = JSON.parse(readFileSync(file, 'utf8')) as PinManifest
  } catch (e) {
    throw new Error(
      `[载荷钉死] 读不到 ${file}：${(e as Error).message}\n` +
        `重录命令：cd frontend && PW_PORT=5175 PW_RECORD_PAYLOADS=1 npx playwright test -c playwright.visual.ts anchors -g "${slug}"\n` +
        `  （录制模式**先落 fixture 再**决定像素断言——Task 4c-fix-A A4 修的正是这条次序；\n` +
        `   那一轮会打 [pin-record] 与 [record] 两行，然后**必须**再跑一次不带 PW_RECORD_PAYLOADS 的同一条命令，\n` +
        `   由 assertZeroDiff 判"新 fixture 能不能复现基线"。别拿录制那一轮的红/绿当结论。）`,
    )
  }
  if (!raw || raw.slug !== slug || !raw.requests || Object.keys(raw.requests).length === 0) {
    throw new Error(`[载荷钉死] ${file} 形状不对（slug 不符或 requests 为空）——宁可红，不拿空 fixture 当钉住`)
  }
  assertManifestDates(raw, file)
  return raw
}

/**
 * **模拟服务端日期的偏移**（Task 4c Step 1 的"≥3 天"那一半的取证开关）。
 *
 * 【它为什么必须存在】brief 要求"必须证漂移在 **≥3 天**跨度上仍 ≤ X，只量今天这一天的差不算闭"。
 * 而一次会话里真实日历只能推进一天，改系统日期会把后端 JWT 的过期判定 / Redis TTL / 登录限流窗口
 * 一起弄坏（且控制者明令不得自行重启后端）⇒ 这条路按字面**不可执行**，如实报为发现（见报告 §5）。
 * 【它证的是什么】fixture 里的日期已被 `backshiftDays` 回拨到**基线那天**，所以
 * `PW_PIN_SHIFT_DAYS=k` 回放的就是"服务端在基线日 +k 天会返回的那份载荷"：
 *  - k=0（默认）= 交付口径，钉住后与基线的差必须为 0；
 *  - k=1 应当**复现今天真实的 +1 天漂移**（今天的服务器日就是基线次日）——这是校准，
 *    拿它和 `PW_VISUAL_NOPIN=1`（真走后端、真漂移）在同一次里的读数对账；
 *    两者一致才说明"只平移日期"这个模拟是忠实的（本批两个 fixture 的序列值全 0，
 *    所以"同窗口重新取数"不会改变几何；这条限制在 k=1 的校准里被实测检验，不是靠说）；
 *  - k=2、k=3 外推出"换到第 3 天漂移仍在、且不会自己回到 0"，而 k=0 恒为 0。
 * 【口径】设了它这一轮就**不是交付读数**：证据里记 `pinShiftDays`，日志明写"模拟"。
 */
export function pinShiftDays(): number {
  const raw = process.env.PW_PIN_SHIFT_DAYS
  if (raw === undefined || raw === '') return 0
  const k = Number(raw)
  if (!Number.isInteger(k) || k < 0 || k > 30) {
    throw new Error(`[载荷钉死] PW_PIN_SHIFT_DAYS=${raw} 需为 0..30 的整数（k=天偏移，0=交付口径）`)
  }
  return k
}

/**
 * 装上钉死：**只**拦 AnchorSpec.pinPaths 声明的 GET；其余请求 fallback 走真后端。
 * 返回的 state 交给 assertPinCoverage 验"声明的端点真的都被请求过"——
 * 钉了却没人调用 = fixture 过期或路径写错，那种"绿"和没钉一样，必须红。
 */
export async function installPayloadPin(page: Page, anchor: AnchorSpec): Promise<PinState> {
  const paths = anchor.pinPaths ?? []
  const manifest = loadPinManifest(anchor.slug)
  const shift = pinShiftDays()
  const state: PinState = { served: [], unpinnedTodayBearing: [], shiftDays: shift }
  const serverToday = localDay(new Date())

  await page.route((url) => paths.includes(`${url.pathname}${url.search}`), async (route) => {
    const req = route.request()
    if (req.method() !== 'GET') return route.fallback()
    const key = `${new URL(req.url()).pathname}${new URL(req.url()).search}`
    const hit = manifest.requests[key]
    if (!hit) return route.fallback()
    state.served.push(key)
    await route.fulfill({
      status: hit.status,
      contentType: hit.contentType,
      body: shift === 0 ? hit.body : shiftIsoDates(hit.body, shift),
    })
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
      // A4：回拨目标是**基线日**（具名常量、入库），不是"CLOCK_EPOCH 反推出来的那一天"。
      // 两者当前由本文件导入期那条断言强制相等；写成 BASELINE_DAY 是为了让这份 manifest
      // **自己声明**它归一到哪天，回放侧 loadPinManifest 会核这一条——硬推的话这件事从不出现在数据里。
      clockDate: BASELINE_DAY,
      // 整天差用日历日算，不用毫秒差 round：跨月/跨夏令时都不会差一天
      backshiftDays: Math.round((Date.parse(`${serverDate}T00:00:00Z`) - Date.parse(`${BASELINE_DAY}T00:00:00Z`)) / 86400000),
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
     */
    pinPaths: ['/api/analytics/users/growth?days=30'],
  },
  { slug: 'admin-profile', title: '个人中心', role: 'ADMIN', path: '/admin/profile' },
  { slug: 'error-404', title: '404', role: 'ANON', path: '/__no-such-route__' },
]
