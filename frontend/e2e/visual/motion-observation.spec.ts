import { expect, test } from '@playwright/test'
import { setTheme, storagePathFor } from './visual-helpers'

/* ============================================================================
 * Task 14 · 非基线动效抽检组
 * ----------------------------------------------------------------------------
 * 视觉基线全程 reducedMotion:'reduce' + animations:'disabled'（playwright.visual.ts 的
 * use.contextOptions），像素确定性够了，代价是 22 张基线永远看不到动效。本组用**不进基线**
 * 的观察截图补上"动效本身被看过"这一半（spec §6.7），并把"CSS 动效在降级下确实停了"证到能写台账。
 *
 * 与 anchors.spec.ts 的关键区别：
 *  - 本组**绝不用 toHaveScreenshot**（动效开着时像素不确定，做成基线天天红）；只 page.screenshot 落盘。
 *  - 本组**不调 settlePage**：settlePage 一次把页面时间推进约 9400ms，会把入场动画跑到终态、
 *    与"观察动效"这个目的正好相反。
 *  - 本组要求 reducedMotion 被显式覆盖回 no-preference —— 这正是附录 R-T14-1 要自证的事。
 * ========================================================================== */

// 触发路径与最终选择器（A 步先证"可达 + 覆盖成立"，B 步再落图与双拍）：
//  - admin-dashboard / admin-courses：页级观察（工作台版式代表 + 表格版式代表）。
//  - dialog-open：**legacy `el-dialog`** —— /admin/courses 表格首行"查看"按钮开详情弹窗（EP dialog-fade，会动）。
//  - modal-open：**自持 `ui/Modal`** —— /admin/users "新增用户"按钮开 UmCreateDialog（模板根即 <Modal wide>）。
//    R-T14-3：四张图里 Modal 与 el-dialog 两类入场动效都要有一张；legacy 侧只取 el-dialog（"任选其一"），
//    故 brief 原来的 drawer-open 位被 modal-open 取代（自持侧要的是 Modal，不是抽屉）。
test.describe('动效观察（非基线）', () => {
  // R-T14-1 实测：顶层 `reducedMotion` 与 `contextOptions.reducedMotion` 两种写法都真覆盖
  // config 的 `contextOptions.reducedMotion='reduce'`（各跑一次，matchMedia 均报 false）。
  // 取顶层写法，理由：test.d.ts 注明 colorScheme/reducedMotion 这类可在用例间不同而不必重建 context。
  test.use({ storageState: storagePathFor('ADMIN'), reducedMotion: 'no-preference' })

  const shots: Array<{ name: string; path: string; overlay?: 'dialog' | 'modal' }> = [
    { name: 'admin-dashboard', path: '/admin/dashboard' },
    { name: 'admin-courses', path: '/admin/courses' },
    { name: 'dialog-open', path: '/admin/courses', overlay: 'dialog' },
    { name: 'modal-open', path: '/admin/users', overlay: 'modal' },
  ]

  for (const shot of shots) {
    test(shot.name, async ({ page }) => {
      await setTheme(page, 'light')
      // 本组**不调 freezeClock、也不调 settlePage**：两者都会把页面时间推走或停住，
      // 与"看动效正在跑"这个目的正好相反。实测还更硬：freezeClock 停住页面时钟后，
      // /admin/courses 表格的 el-loading 遮罩卡在 `el-loading-fade-leave-active` 不消失、
      // 一直 intercepts pointer events，"查看"按钮永远点不动（dialog-open 首跑就是这么红的）。
      await page.goto(shot.path)
      await page.waitForLoadState('networkidle')

      // R-T14-1 降级自证：截图之前，页面里读一次 media query。
      const reduce = await page.evaluate(() => matchMedia('(prefers-reduced-motion: reduce)').matches)
      expect(reduce, `${shot.name}：matchMedia 仍报 reduce ⇒ config 的 contextOptions.reducedMotion='reduce' 未被覆盖，本组会全截成静态图`).toBe(false)

      // R-T14-3 触发器可达证明：选择器命中不到就地换可证明载体，不用更宽泛选择器蒙。
      if (shot.overlay === 'dialog') {
        await page.getByRole('button', { name: '查看' }).first().click()
        await page.locator('.el-dialog').first().waitFor({ state: 'visible', timeout: 10000 })
      } else if (shot.overlay === 'modal') {
        await page.getByRole('button', { name: '新增用户' }).first().click()
        await page.locator('.overlay .modal').first().waitFor({ state: 'visible', timeout: 10000 })
      }
    })
  }
})
