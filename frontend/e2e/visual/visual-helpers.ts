import type { Locator, Page } from '@playwright/test'

export const THEME_KEY = 'fz-theme'
export type ThemeName = 'light' | 'dark'

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
 * 冻结时间。仪表盘的"3 分钟前"、CountUp 的缓动、ECharts 入场动画都挂在时钟上，
 * 不冻结则同一份代码每天截出不同的图，基线失去意义。
 * 固定到本计划撰写日的上午九点整——刻意避开零点/整日边界，边界值最易暴露格式化差异。
 */
export async function freezeClock(page: Page): Promise<void> {
  await page.clock.install({ time: new Date('2026-10-18T09:00:00') })
}

/**
 * 稳定态收敛。顺序有意义：先让网络与定时器跑完，再读字体，最后校文本不再变。
 * `clock.runFor` 会把 rAF/定时器一次性推过 ECharts(~1000ms) 与 CountUp 的动画时长，
 * 这是比 waitForTimeout 更可靠的做法——慢机器上固定毫秒数会截到半截动画。
 */
export async function settlePage(page: Page): Promise<void> {
  await page.clock.runFor(5000)
  await page.waitForLoadState('networkidle')
  await page.evaluate(() => document.fonts.ready.then(() => undefined))
  await waitForStableText(page)
}

/**
 * 关键文本稳定判定：连续两轮读数相同才放行。
 * 这是对"动画真的结束了"的直接证据，不依赖对动画时长的猜测。
 */
export async function waitForStableText(page: Page, timeout = 15000): Promise<void> {
  let prev = ''
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) {
    const now = await page.evaluate(() =>
      Array.from(document.querySelectorAll('.s-value, .pg-title, .stat-value'))
        .map((el) => el.textContent?.trim() ?? '')
        .join('|'),
    )
    if (now && now === prev) return
    prev = now
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

export function snapName(theme: ThemeName): string {
  return `{platform}-${theme}.png`
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

export async function maskOf(page: Page, selectors: string[] = []): Promise<Locator[]> {
  return selectors.map((s) => page.locator(s))
}
