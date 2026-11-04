import { test, expect, type Locator, type Page } from '@playwright/test'
import { freezeClock, SNAPSHOT_THRESHOLDS, snapName, storagePathFor } from './visual-helpers'

/**
 * 像素锚点（L-03）。
 *
 * 与旧端 anchors.spec.ts 分开的理由：旧套件的 12 个锚点全部指向旧路由，摘除旧前端时一起失效；
 * 这里只钉当前壳层的五个面，快照落在本文件自己的 __snapshots__ 目录，互不覆盖。
 * 时钟用同一把 freezeClock 冻结（相对时间文案才不会天天漂）；明暗两档各一张，共 10 张。
 * 数据来自本机真栈（course 4001 等种子）；种子变了要按重录闸门重新录制，不是"容忍红"。
 */

type Theme = 'light' | 'dark'
const THEMES: Theme[] = ['light', 'dark']

async function setTheme(page: Page, theme: Theme) {
  // 明暗键与 Shell/main.ts 同源（localStorage['theme']），旧套件的 fz-theme 对本壳层无效
  await page.addInitScript((value) => localStorage.setItem('theme', value), theme)
}

async function shoot(page: Page, slug: string, theme: Theme, url: string, ready: Locator) {
  await freezeClock(page)
  await setTheme(page, theme)
  await page.goto(url, { waitUntil: 'domcontentloaded' })
  await expect(ready).toBeVisible()
  // 让首屏的图表/骨架过渡落定；动画本身在截图时被禁用
  await page.waitForTimeout(900)
  await expect(page).toHaveScreenshot(`${snapName(slug, theme)}.png`, {
    ...SNAPSHOT_THRESHOLDS,
    animations: 'disabled',
    fullPage: false,
  })
}

test.describe('视觉基线 · 未登录', () => {
  for (const theme of THEMES) {
    test(`登录页 · ${theme}`, async ({ page }) => {
      await shoot(page, 'v2-login', theme, '/login', page.locator('.av-form'))
    })
  }
})

test.describe('视觉基线 · 学生', () => {
  test.use({ storageState: storagePathFor('STUDENT') })
  for (const theme of THEMES) {
    test(`学生工作台 · ${theme}`, async ({ page }) => {
      await shoot(page, 'v2-student-home', theme, '/student/home', page.locator('.kpi-grid'))
    })
    test(`学生课程空间 · ${theme}`, async ({ page }) => {
      await shoot(
        page,
        'v2-student-course-overview',
        theme,
        '/student/courses/4001/overview',
        page.locator('.course-head'),
      )
    })
  }
})

test.describe('视觉基线 · 教师', () => {
  test.use({ storageState: storagePathFor('TEACHER') })
  for (const theme of THEMES) {
    test(`教师收件箱 · ${theme}`, async ({ page }) => {
      await shoot(page, 'v2-teacher-inbox', theme, '/teacher/inbox', page.locator('.inbox-body'))
    })
  }
})

test.describe('视觉基线 · 管理员', () => {
  test.use({ storageState: storagePathFor('ADMIN') })
  for (const theme of THEMES) {
    test(`管理端用户页 · ${theme}`, async ({ page }) => {
      await shoot(page, 'v2-admin-users', theme, '/admin/users', page.locator('.users-body'))
    })
  }
})
