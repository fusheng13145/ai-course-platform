import { expect, test } from '@playwright/test'
import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
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
 *  - 本组**不调 freezeClock、也不调 settlePage**：两者都会把页面时间推走或停住，
 *    与"看动效正在跑"这个目的正好相反。实测还更硬：freezeClock 停住页面时钟后，
 *    /admin/courses 表格的 el-loading 遮罩卡在 `el-loading-fade-leave-active` 不消失、
 *    一直 intercepts pointer events，"查看"按钮永远点不动（dialog-open 首跑就是这么红的）。
 *  - 本组要求 reducedMotion 被显式覆盖回 no-preference —— 这正是附录 R-T14-1 要自证的事。
 * ========================================================================== */

// 证据目录：仓库根 docs/visual-upgrade/evidence/P0a/motion/。
// Playwright 从 frontend/ 起跑（config 的 testDir/webServer 都相对它），process.cwd() 是 frontend，
// 所以仓库根 docs/ 是 `../docs`。用 join(process.cwd(), '..', ...) 而不是硬编码字符串，
// 免得下一次 config 挪 cwd 时悄悄写到别处。
const MOTION_DIR = join(process.cwd(), '..', 'docs', 'visual-upgrade', 'evidence', 'P0a', 'motion')
mkdirSync(MOTION_DIR, { recursive: true })

test.describe('动效观察（非基线）', () => {
  // R-T14-1 实测：顶层 `reducedMotion` 与 `contextOptions.reducedMotion` 两种写法都真覆盖
  // config 的 `contextOptions.reducedMotion='reduce'`（各跑一次，matchMedia 均报 false）。
  // 取顶层写法，理由：test.d.ts 注明 colorScheme/reducedMotion 这类可在用例间不同而不必重建 context。
  test.use({ storageState: storagePathFor('ADMIN'), reducedMotion: 'no-preference' })

  const shots: Array<{ name: string; path: string; overlay?: 'dialog' | 'modal'; fullPage?: boolean }> = [
    { name: 'admin-dashboard', path: '/admin/dashboard', fullPage: true },
    { name: 'admin-courses', path: '/admin/courses' },
    { name: 'dialog-open', path: '/admin/courses', overlay: 'dialog' },
    { name: 'modal-open', path: '/admin/users', overlay: 'modal' },
  ]

  for (const shot of shots) {
    test(shot.name, async ({ page }) => {
      await setTheme(page, 'light')
      await page.goto(shot.path)
      await page.waitForLoadState('networkidle')

      // R-T14-1 降级自证：截图之前，页面里读一次 media query。
      // `prefers-reduced-motion: reduce` 读的是 context 层的 reducedMotion，与页面时钟、与 CSS 都无关，
      // 所以它就是"config 的降级到底有没有被 test.use 覆盖掉"的直接读数。为 true ⇒ 覆盖失效、四张图全是静态图。
      const reduce = await page.evaluate(() => matchMedia('(prefers-reduced-motion: reduce)').matches)
      expect(
        reduce,
        `${shot.name}：matchMedia 仍报 reduce ⇒ config 的 contextOptions.reducedMotion='reduce' 未被覆盖，本组会全截成静态图`,
      ).toBe(false)

      if (shot.overlay === 'dialog') {
        // R-T14-3 legacy 侧载体：/admin/courses 表格首行"查看"→ CourseManagement.vue 的 <el-dialog append-to-body>
        await page.getByRole('button', { name: '查看' }).first().click()
        await page.locator('.el-dialog').first().waitFor({ state: 'visible', timeout: 10000 })

        // R-T14-2 双拍：紧贴触发拍 a、200ms 后拍 b（EP el-dialog 的 dialog-fade 是 .3s，200ms 正落中段）。
        const a = await page.screenshot()
        await page.waitForTimeout(200)
        const b = await page.screenshot()
        writeFileSync(join(MOTION_DIR, `${shot.name}.png`), a)
        writeFileSync(join(MOTION_DIR, `${shot.name}-mid.png`), b)
        // 判据极性：**a !== b 才算"这一页此刻确实有动效在跑"**。
        // 若 a === b：先查触发有没有真打开（上面 waitFor visible 已证），再调小 200→80–120ms 重试；
        // 调完仍相同就是真发现（该弹层无 CSS 入场动效），照实写 LEDGER，不许删断言换"人工看图确认"。
        expect(
          Buffer.compare(a, b),
          `${shot.name}：连拍两字节完全相同 ⇒ 这一页此刻没有动效在跑，观察组测了个空。dialog-open 是 legacy el-dialog，EP dialog-fade 应给它入场；红了先查触发与时长，再判是不是真发现。`,
        ).not.toBe(0)
      } else if (shot.overlay === 'modal') {
        // R-T14-3 自持侧载体：/admin/users 页头"新增用户"→ UmCreateDialog（模板根即 <Modal wide>，Teleport 到 body）
        await page.getByRole('button', { name: '新增用户' }).first().click()
        await page.locator('.overlay .modal').first().waitFor({ state: 'visible', timeout: 10000 })

        // R-T14-2 双拍。**先按 not.toBe(0) 极性跑一次拿到实测读数**：ui/Modal 的 `.overlay`/`.modal`
        // 静态读 CSS 无 animation/transition（`design-system.css` 的 .overlay/.modal 块 + Modal.vue scoped 块，
        // rise-in 只挂 .animate-in 页面区块、不挂弹层），预期两字节相同 ⇒ 是真发现。
        // 若真相同，按 R-T14-2 处置：调小时长重试；调完仍相同则把极性改成 toBe(0)（不是删断言），
        // 反过来把"该弹层无 CSS 入场动效"钉成一条会响的入库断言——将来给 .modal 加过渡它就红、逼重录。
        const a = await page.screenshot()
        await page.waitForTimeout(100)
        const b = await page.screenshot()
        writeFileSync(join(MOTION_DIR, `${shot.name}.png`), a)
        writeFileSync(join(MOTION_DIR, `${shot.name}-mid.png`), b)
        // 实测读数（本会话）：200ms 与 100ms 两种间隔下 a 与 b 均**逐字节相同**（Buffer.compare 返回 0）。
        // 触发确实开了弹层（上面 .overlay .modal 的 waitFor visible 已过），所以不是"没打开"；
        // 调小间隔仍相同 ⇒ 是真发现：ui/Modal 原语**没有 CSS 入场动效**（design-system.css 的 .overlay/.modal
        // 与 Modal.vue scoped 块都没有 animation/transition，rise-in 只挂 .animate-in 页面区块）。
        // R-T14-2 不许删这条断言换成"人工看图确认"——这里**保留机检**，只是把极性改成实测到的 toBe(0)：
        // 它现在是"该原语无入场动效"这条结论的入库面，谁哪天给 .modal 加过渡，这条立刻转红、逼重录观察组。
        expect(
          Buffer.compare(a, b),
          `${shot.name}：ui/Modal 无 CSS 入场动效 ⇒ 连拍两字节应逐字节相同（本会话 200ms/100ms 两间隔实测均为 0）。` +
            `这条若转红，说明 .modal/.overlay 新增了 animation/transition —— 那是动效面变化，须重跑本组并按新形状改判据。`,
        ).toBe(0)
      } else {
        // 页级观察（admin-dashboard/admin-courses）：截图前已经跑过 matchMedia 自证，
        // 这里只落一张静态画面，不做双拍（双拍归 R-T14-2 的两个 overlay 用例）。
        writeFileSync(join(MOTION_DIR, `${shot.name}.png`), await page.screenshot({ fullPage: shot.fullPage }))
      }
    })
  }
})
