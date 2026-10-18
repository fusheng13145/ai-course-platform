import type { Locator, Page } from '@playwright/test'

export const THEME_KEY = 'fz-theme'
export type ThemeName = 'light' | 'dark'

/**
 * 冻结点与预热推进量。为什么是这两个数，见下面 freezeClock / settlePage 的注释；
 * 两处共用同一个常量，页面时间在截图时刻才是可推算的。
 */
const CLOCK_EPOCH = new Date('2026-10-18T09:00:00')
const CLOCK_WARMUP_MS = 5000

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
 * runFor 不会 resume，所以跑完仍是停住状态：settlePage 返回后页面时间 ≥ 09:00:10.000，
 * 且只由文本收敛所需的轮数决定，二次跑同样落在这个时刻。
 */
export async function settlePage(page: Page): Promise<void> {
  await page.waitForLoadState('networkidle')
  await page.clock.runFor(CLOCK_WARMUP_MS)
  await page.evaluate(() => document.fonts.ready.then(() => undefined))
  await waitForStableText(page)
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
 */
export async function waitForStableText(page: Page, timeout = 15000): Promise<void> {
  let prev: string | null = null
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) {
    const now = await page.evaluate(() =>
      Array.from(document.querySelectorAll('.s-value, .pg-title'))
        .map((el) => el.textContent?.trim() ?? '')
        .join('|'),
    )
    if (now === '') return // 页面无统计类文本 → 天然稳定
    if (now === prev) return
    prev = now
    await page.clock.runFor(120)
    await page.waitForTimeout(120)
  }
  throw new Error(`文本未收敛到稳定态（可能仍在动画中）·最后读数: ${prev}`)
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
  { slug: 'student-knowledge-graph', title: '知识图谱（图谱代表）', role: 'STUDENT', path: '/student/knowledge-graph' },
  { slug: 'admin-governance-analytics', title: '治理分析（图表代表）', role: 'ADMIN', path: '/admin/analytics' },
  { slug: 'admin-profile', title: '个人中心', role: 'ADMIN', path: '/admin/profile' },
  { slug: 'error-404', title: '404', role: 'ANON', path: '/__no-such-route__' },
]
