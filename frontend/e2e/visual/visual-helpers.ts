import type { Locator, Page } from '@playwright/test'

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
 * 全仓唯一截图阈值来源。默认值是**待测量**，不是结论：
 * Task 4b 用三档注入证明它能不能看见 Phase 1/2 形状的改动，
 * 测出来不合格就在此处收紧，并逐张重跑 anchors 确认没有把噪声当成改动。
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
 * 【Task 4b 实测·离线自检台，待真基线复验】后端不在期间用同一把尺量了合成页（真实 token 与真实卡片几何，
 * 1440×1070，噪声地板对照 = 不注入再截一次，t=0/r=0 下 0 个像素差，即静态渲染是逐字节可复现的）：
 *   gross(body brightness 0.97)：最大单通道色差 82/255，t=0.2 只有 220 个像素报警 < 免检额 3082 → **被吸收**
 *   shadow(--sh-l1 alpha 翻倍) ：最大单通道色差 20/255 → t=0.2 与 t=0.1 都是 **0 个像素**，t=0.05 起 11282 个
 *   radius(16px→14px)          ：最大单通道色差 19/255 → t=0.2/t=0.1 同样 0 个像素；
 *                                即便 t=0 也只有 2559 个 < 免检额 3082 → **连阈值降到 0 都检不出**
 * 结论：这一组阈值看不见 Phase 1/2/3 要做的任何一档改动，连"哨兵档"gross 都看不见。
 * 但把上面的数改成产品结论，必须先做两件本任务做不了的事：
 *   ①sensitivity.spec.ts 在 22 张真基线上跑同一张 ladder（真页面的卡片数量/面积与合成页不同）；
 *   ②每下调一格都重跑 anchors，确认 22 张仍零 diff（真页面的噪声地板从没在 t=0 下量过）。
 * 这两件做完之前**不改**这两个数——放宽是错的，未经验证的收紧同样是错的。
 */
export const SNAPSHOT_THRESHOLDS = { threshold: 0.2, maxDiffPixelRatio: 0.002 }

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
  { slug: 'student-dashboard', title: '学生工作台', role: 'STUDENT', path: '/student/dashboard' },
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
  { slug: 'admin-governance-analytics', title: '治理分析（图表代表）', role: 'ADMIN', path: '/admin/analytics' },
  { slug: 'admin-profile', title: '个人中心', role: 'ADMIN', path: '/admin/profile' },
  { slug: 'error-404', title: '404', role: 'ANON', path: '/__no-such-route__' },
]
