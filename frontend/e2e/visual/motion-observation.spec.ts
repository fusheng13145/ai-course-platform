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

  const shots: Array<{
    name: string
    path: string
    overlay?: 'dialog' | 'modal'
    fullPage?: boolean
  }> = [
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
      const reduce = await page.evaluate(
        () => matchMedia('(prefers-reduced-motion: reduce)').matches,
      )
      expect(
        reduce,
        `${shot.name}：matchMedia 仍报 reduce ⇒ config 的 contextOptions.reducedMotion='reduce' 未被覆盖，本组会全截成静态图`,
      ).toBe(false)

      if (shot.overlay === 'dialog') {
        // R-T14-3 legacy 侧载体：/admin/courses 表格首行"查看"→ CourseManagement.vue 的 <el-dialog append-to-body>
        await page.getByRole('button', { name: '查看' }).first().click()
        const panel = page.locator('.el-dialog').first()
        await panel.waitFor({ state: 'visible', timeout: 10000 })

        // R-T14-2 双拍（R-T14-7 收窄到元素级）：紧贴触发拍 a、200ms 后拍 b
        //（EP el-dialog 的 dialog-fade 是 .3s，200ms 正落中段）。
        // 为什么不是 locator.screenshot()：本会话实测 `locator.screenshot()` 会做元素稳定等待、
        // 把这一层弹层裁到**动画终态**才落笔（dialog-fade 收尾后 opacity 已恒为 1），
        // 于是连拍两字节相同、dialog-open 当场假红——它和 animations:'disabled' 是同一族"强制落终态"，
        // 与"看动效正在跑"这个目的正好相反。page.screenshot({ clip }) 用节点 boundingBox 裁到
        // 那块矩形、**不等稳定**，才既收窄了字节面（避开同页 v-loading 遮罩的 opacity 过渡假绿源）
        // 又留住中段像素。
        const box = await panel.boundingBox()
        expect(
          box,
          `${shot.name}：弹层已 visible 却拿不到 boundingBox ⇒ 元素级裁剪无从下手`,
        ).not.toBeNull()
        const clip = { x: box!.x, y: box!.y, width: box!.width, height: box!.height }
        const a = await page.screenshot({ clip })
        await page.waitForTimeout(200)
        const b = await page.screenshot({ clip })
        writeFileSync(join(MOTION_DIR, `${shot.name}.png`), a)
        writeFileSync(join(MOTION_DIR, `${shot.name}-mid.png`), b)
        // 判据极性：**a !== b 才算"这块弹层此刻确实有动效在跑"**。
        // ui/Modal 与 ui/Drawer 的原语面板没有 CSS 入场动效（design-system.css 的 .overlay/.modal
        // 块无 animation/transition，Drawer.vue 样式块只有按钮级 transition）；el-dialog 侧会动。
        // 若 a === b：先查触发有没有真打开（上面 waitFor visible 已证），再调小 200→80–120ms 重试；
        // 调完仍相同就是真发现（该弹层无 CSS 入场动效），照实写 LEDGER，不许删断言换"人工看图确认"。
        expect(
          Buffer.compare(a, b),
          `${shot.name}：连拍两字节完全相同 ⇒ 这块弹层此刻没有动效在跑，观察组测了个空。dialog-open 是 legacy el-dialog，EP dialog-fade 应给它入场；红了先查触发与时长，再判是不是真发现。`,
        ).not.toBe(0)
      } else if (shot.overlay === 'modal') {
        // R-T14-3 自持侧载体：/admin/users 页头"新增用户"→ UmCreateDialog（模板根即 <Modal wide>，Teleport 到 body）
        await page.getByRole('button', { name: '新增用户' }).first().click()
        const panel = page.locator('.modal').first()
        await panel.waitFor({ state: 'visible', timeout: 10000 })
        const box = await panel.boundingBox()
        expect(
          box,
          `${shot.name}：面板已 visible 却拿不到 boundingBox ⇒ 元素级裁剪无从下手`,
        ).not.toBeNull()
        const clip = { x: box!.x, y: box!.y, width: box!.width, height: box!.height }

        // R-T14-2 双拍（R-T14-7 收窄到元素级：page.screenshot({ clip }) 裁到 .modal 那块矩形，
        // 不用 locator.screenshot()——理由见 dialog 分支，它会等稳定把画面裁到终态、对动效不敏感）。
        // 结论措辞对齐证据面：ui/Modal 的 .overlay/.modal 原语面板**没有 CSS 入场动效**
        //（design-system.css 的 .overlay/.modal 块 + Modal.vue scoped 块都无 animation/transition，
        // rise-in 只挂 .animate-in 页面区块、不挂弹层）⇒ 元素级两字节相同是真发现。
        const a = await page.screenshot({ clip })
        await page.waitForTimeout(100)
        const b = await page.screenshot({ clip })
        writeFileSync(join(MOTION_DIR, `${shot.name}.png`), a)
        writeFileSync(join(MOTION_DIR, `${shot.name}-mid.png`), b)
        expect(
          Buffer.compare(a, b),
          `${shot.name}：ui/Modal 无 CSS 入场动效 ⇒ 元素级连拍两字节应逐字节相同。` +
            `这条若转红，说明 .modal 新增了 animation/transition —— 那是动效面变化，须重跑本组并按新形状改判据。`,
        ).toBe(0)

        // R-T14-7 对偶实验：证明上面那条 toBe(0) 真会红，而不是"这把尺子本来就量不出动效"。
        // 用例内 page.addStyleTag 注入一个入场过渡（**绝不改 frontend/src/，注入只活在用例里、随页面销毁**），
        // 再对同一 clip 矩形双拍——rise-in 的 opacity/translateY 正在跑，两字节必须不同。
        // 没有这条，"谁日后给 .modal 加动效这条就立刻转红"只是推断。
        await page.addStyleTag({ content: '.modal{animation:rise-in 0.42s both}' })
        const c = await page.screenshot({ clip })
        await page.waitForTimeout(100)
        const d = await page.screenshot({ clip })
        expect(
          Buffer.compare(c, d),
          `${shot.name}：注入 rise-in 后元素级两字节仍相同 ⇒ 这把尺子对动效不敏感，上一条 toBe(0) 的"加动效就转红"是假的。` +
            `红了才说明本判据确实能区分"有动效/无动效"。`,
        ).not.toBe(0)
      } else {
        // 页级观察（admin-dashboard/admin-courses）：截图前已经跑过 matchMedia 自证，
        // 这里只落一张静态画面，不做双拍（双拍归 R-T14-2 的两个 overlay 用例）。
        writeFileSync(
          join(MOTION_DIR, `${shot.name}.png`),
          await page.screenshot({ fullPage: shot.fullPage }),
        )
      }
    })
  }
})

test.describe('降级对照（故意不做 test.use 覆盖，读 config 的 reduce 真值）', () => {
  // R-T14-6（本轮核心）：题面 Step 3 原本拿"anchors admin-dashboard 明暗零 diff"**间接**推
  // "CSS 动效在 reduce 下停了"，那条推理已作废——`animations:'disabled'` **不在** config
  // （playwright.visual.ts 全文只有 use.contextOptions.reducedMotion），它是 anchors.spec.ts
  // 里每次 toHaveScreenshot 的**截图级**选项；本仓 sensitivity.spec.ts 的注释已把机制写死：
  // 它在截图瞬间把 root.getAnimations() 里每个有限动画都 animation.finish() 强制落到终态。
  // ⇒ 基线像素稳定由截图级终态强制保证，与媒体查询无关，"没停二次跑就会飘"不成立。
  // 所以这里换成**直接读数**：**不写** test.use({ reducedMotion })，让它吃 config 的
  // contextOptions.reducedMotion='reduce'，进去读两样东西——媒体查询命中，且 .animate-in 的
  // animationName 归零。这两条一起把 spec §6.7「动效降级被证明」从推断升级为实测。
  // 与上一个 describe 的 matchMedia===false 那条合起来是完整闭环：那条证"能被翻回 no-preference"，
  // 这条证"不翻时确实吃 config 的 reduce 且 reduce 真把 CSS 入场动效关掉了"。
  // 载体 .animate-in 选 /admin/dashboard（AdChartPanels/AdQuickEntries/AdStatCards 都用 animate-in；
  // design-system.css 的 .animate-in 是 `animation: rise-in 0.42s var(--ease-out) both`，
  // 其 @media (prefers-reduced-motion: reduce) 块把 .animate-in/.fade-in 打成 animation:none）。
  test.use({ storageState: storagePathFor('ADMIN') })

  test('reduce 未被覆盖：matchMedia 命中 true 且 .animate-in 的 animationName 为 none', async ({
    page,
  }) => {
    await setTheme(page, 'light')
    await page.goto('/admin/dashboard')
    await page.waitForLoadState('networkidle')

    const q = await page.evaluate(() => {
      const el = document.querySelector('.animate-in')
      return {
        hasAnchor: el !== null,
        media: matchMedia('(prefers-reduced-motion: reduce)').matches,
        anim: el ? getComputedStyle(el).animationName : '(no .animate-in on page)',
      }
    })

    expect(q.hasAnchor, '/admin/dashboard 上没有 .animate-in ⇒ 选错载体，这条对照测了个空').toBe(
      true,
    )
    expect(
      q.media,
      '不写 test.use 覆盖时 matchMedia 读到 false ⇒ config 的 contextOptions.reducedMotion=reduce 根本没落到 context，全仓基线的"降级"前提是假的',
    ).toBe(true)
    expect(
      q.anim,
      'reduce 下 .animate-in 的 animationName 不是 none ⇒ 媒体查询没把 CSS 入场动效关掉（design-system.css 的 reduce 块应把它打成 animation:none）',
    ).toBe('none')
  })
})
