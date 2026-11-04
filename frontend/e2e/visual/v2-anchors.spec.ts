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
  // 让首屏的图表/骨架过渡落定
  await page.waitForTimeout(900)
  // 先归零动画 delay，再截图（顺序不能反，见 alignAnimationDelays 的注释）
  await alignAnimationDelays(page)
  await expect(page).toHaveScreenshot(`${snapName(slug, theme)}.png`, {
    ...SNAPSHOT_THRESHOLDS,
    animations: 'disabled',
    fullPage: false,
  })
}

/**
 * 让截图前的渲染状态**唯一**——消除三处会逐轮摇摆的变量。
 *
 * 【关于第三处：行高钉成整数】
 * 新皮肤里 KPI 卡的小字号文本没有设 line-height：标签 10.5px、注脚 9.5px，
 * 默认行高＝字号×正常系数（约 1.2）＝12.6／11.4 这类小数。字体度量在小数行高上的
 * 舍入会浮动 ±1px ⇒ 卡片高度在 100.234 与 101.234 之间摇摆 ⇒ 卡片下边框的行号
 * 在 341／342 之间跳。实测差异包围盒正好是「卡片下沿那一行」，颜色差只有 4~7/255。
 * 这里把行高钉成整数（13px／12px）：钉的是**度量**不是**外观**——与默认值的视觉差
 * 不到 1px，而行高从此是确定的。不在样式表里改，是因为那些是并行成员的在途改动。
 *
 * 背景：同一份源码、同一份数据，视觉基线会在绿/红之间反复摇摆。
 * 2026-11-04 实测过程（三次根因排查，每一步都有读数支撑）：
 *  ① 差异稳定落在 KPI 卡那一行（包围盒 y 242..342＝卡片区），颜色差只有 4~7/255；
 *     而同一时刻用裸 page.screenshot() 连拍两张是 **0 差异** ⇒ 页面渲染本身是稳的，
 *     抖动出在「截图前的最后一步」。
 *  ② 逐像素比对确认：基线的卡片下边框线在 y=341、实拍在 y=342，
 *     而竖边位置不变 ⇒ 卡片**高度差了 1px**，不是位移。
 *  ③ 高度差来自 `.stat-label`(10.5px)／`.stat-note`(9.5px) 没有设 line-height：
 *     默认行高＝字号×正常系数（约 1.2）＝12.6／11.4 这类小数，
 *     字体度量在小数行高上的舍入会浮动 ±1px ⇒ 卡片高度摇摆 ⇒ 边框行号跳。
 *
 * 三处处理，各自对应一个变量：
 *  - 动画 delay 归零：`.stat` 的入场动画 `viewIn` 带 .04/.08/.12s 逐卡递增的 delay，
 *    Playwright 的 `animations: 'disabled'` 是**快进**到终态，各卡推进进度不一致，
 *    「transform 是否已归零」逐轮摇摆 ⇒ 归零 delay 让它们同步。
 *  - 动画终态显式写出（opacity 1 / translateY(0)）：与 `viewIn` 的 to 帧一致，
 *    既保住终态的合成层，又消除了「快进了多少」。
 *    ⚠️ 不能用 `animation: none`——那会连合成层一起去掉，文字抗锯齿方式随之改变，
 *    与真实用户看到的画面反而不同（实测差异 2800+ px，集中在字形轮廓上）。
 *  - 小字号文本行高钉成整数：见下方注释。
 *
 * 为什么不动样式表：那些是并行成员的在途改动，不该由基线侧去动；
 * 这里只在截图前钉住**度量**，不改变外观（10.5px 文字配 13px 行高、
 * 9.5px 文字配 12px 行高，与默认值的视觉差不到 1px）。
 */
async function alignAnimationDelays(page: Page): Promise<void> {
  await page.addStyleTag({
    content: `*, *::before, *::after {
      animation-delay: 0s !important;
      transition-delay: 0s !important;
    }
    body[data-app="ui"] .animate-hero,
    body[data-app="ui"] .stat,
    body[data-app="ui"] .module,
    body[data-app="ui"] .card {
      opacity: 1 !important;
      transform: translateY(0) !important;
    }
    body[data-app="ui"] .stat-label { line-height: 13px !important; }
    body[data-app="ui"] .stat-note { line-height: 12px !important; }`,
  })
  // 样式注入后要等一帧，让布局与栅格化按新样式重算。
  // ⚠️ 这里不能用 requestAnimationFrame 等——freezeClock 把页面时钟冻住了，
  // rAF 不再触发，等待会一直挂到超时（实测 60s 超时）。真实时间等待即可。
  await page.waitForTimeout(120)
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
