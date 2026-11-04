import { test, expect, type Page } from '@playwright/test'
import AxeBuilder from '@axe-core/playwright'
import { loginAs } from './test-helpers'

/**
 * 无障碍门禁（docs/前端待办项.md §4 · P-01）
 *
 * 尺子：@axe-core/playwright 在真实登录态、明/暗两档下扫描 7 个面。
 * 判据：只有 serious / critical 失败才算红灯；moderate / minor 与 incomplete
 * （渐变、背景图等 axe 无法判定的）只打印计数，不阻断。
 *
 * 登录沿用 e2e/test-helpers.ts 的 loginAs（种子账号 + 勾「记住我」）。
 * 登录接口有 IP 限流（ratelimit.login 默认 10 次/60 秒），本套件共 3 次角色登录；
 * 命中 429 时等 15s 重试一次，仍失败就让 loginAs 的原始错误暴露，不 mock。
 */

type Theme = 'light' | 'dark'
type Role = 'STUDENT' | 'TEACHER' | 'ADMIN'

interface PageTarget {
  path: string
  ready: string
}

const LOGIN_RETRY_WAIT_MS = 15000
const SETTLE_MS = 800

const ROLE_PAGES: Array<{ role: Role; pages: PageTarget[] }> = [
  {
    role: 'STUDENT',
    pages: [
      { path: '/student/home', ready: '.kpi-grid' },
      { path: '/student/courses/4001/overview', ready: '.course-head' },
    ],
  },
  {
    role: 'TEACHER',
    pages: [
      { path: '/teacher/inbox', ready: '.inbox-body' },
      { path: '/teacher/exams', ready: '.module .kpi-grid' },
    ],
  },
  {
    role: 'ADMIN',
    pages: [
      { path: '/admin/users', ready: '.users-body' },
      { path: '/admin/uploads', ready: '.module .kpi-grid' },
    ],
  },
]

test.describe.configure({ mode: 'parallel' })
test.setTimeout(180000)

async function loginWithRetry(page: Page, role: Role) {
  try {
    await loginAs(page, role)
  } catch (error) {
    if (!/HTTP 429|429/.test(String(error))) throw error
    console.warn(`[a11y] ${role} 登录命中 429，等待 15s 重试一次`)
    await page.waitForTimeout(LOGIN_RETRY_WAIT_MS)
    await loginAs(page, role)
  }
}

async function useTheme(page: Page, theme: Theme) {
  await page.addInitScript((next) => {
    localStorage.setItem('theme', next)
  }, theme)
}

async function waitForTheme(page: Page, theme: Theme) {
  await page.waitForFunction(
    (expected) => document.documentElement.dataset.theme === expected,
    theme,
    { timeout: 10000 },
  )
}

/**
 * 单页单主题扫描。页面就绪等待主选择器 + 800ms 落定，与 a11y-probe 同口径。
 */
async function scan(page: Page, target: PageTarget, theme: Theme) {
  await page.goto(target.path, { waitUntil: 'domcontentloaded' })
  await page.locator(target.ready).first().waitFor({ state: 'visible', timeout: 20000 })
  await waitForTheme(page, theme)
  await page.waitForTimeout(SETTLE_MS)

  const results = await new AxeBuilder({ page }).analyze()
  const blocking = results.violations.filter(
    (violation) => violation.impact === 'serious' || violation.impact === 'critical',
  )
  const label = `${target.path} [${theme}]`
  const incompleteIds = Array.from(new Set(results.incomplete.map((item) => item.id))).join(',')

  console.log(
    `[a11y] ${label}: ${blocking.length} serious/critical, ` +
      `${results.violations.length} violations total, ` +
      `${results.incomplete.length} incomplete${incompleteIds ? ` (${incompleteIds})` : ''}`,
  )
  for (const violation of blocking) {
    const nodes = violation.nodes.map((node) => node.target.join(' ')).slice(0, 5)
    console.log(
      `[a11y]   ${violation.id} (${violation.impact}, ${violation.nodes.length} nodes): ` +
        nodes.join(' | '),
    )
  }

  expect(
    blocking.map((violation) => `${violation.id}(${violation.impact}):${violation.nodes.length}`),
    `${label} 存在 serious/critical 无障碍违规`,
  ).toEqual([])
}

async function scanPages(page: Page, pages: PageTarget[], theme: Theme) {
  for (const target of pages) await scan(page, target, theme)
}

test('a11y: 登录页（未登录，明/暗）', async ({ page }) => {
  await useTheme(page, 'light')
  await scan(page, { path: '/login', ready: '.av-form' }, 'light')

  await useTheme(page, 'dark')
  await scan(page, { path: '/login', ready: '.av-form' }, 'dark')
})

for (const { role, pages } of ROLE_PAGES) {
  test(`a11y: ${role} 页面（明/暗）`, async ({ page }) => {
    await loginWithRetry(page, role)
    await scanPages(page, pages, 'light')

    await useTheme(page, 'dark')
    await scanPages(page, pages, 'dark')
  })
}
