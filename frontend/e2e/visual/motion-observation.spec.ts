import { expect, test } from '@playwright/test'
import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { setTheme, storagePathFor } from './visual-helpers'

/* ============================================================================
 * Task 14 · 非基线动效抽检组
 * ----------------------------------------------------------------------------
 * 视觉基线吃 config（playwright.visual.ts）的 `use.contextOptions.reducedMotion = 'reduce'`；
 * 但**基线像素确定性的来源不是这条媒体查询**，而是截图级的终态强制：anchors.spec.ts 里每次
 * `toHaveScreenshot` 都带 `animations` 选项（取值 'disabled'），它在截图瞬间把 `getAnimations()`
 * 里每个有限动画 `finish()` 落到终态（机制写死在 sensitivity.spec.ts 的注释里）。
 * ⇒ 旧版头部那句"视觉基线全程 reducedMotion:'reduce' ＋ animations:'disabled'（playwright.visual.ts 的
 * use.contextOptions）"是失真的，按修复轮 1 的裁定 R-T14-6 更正：`animations` **不在** config 里
 * （`grep -n animations playwright.visual.ts` 零命中），它是截图调用上的选项，与媒体查询无关。
 * 代价不变：22 张基线永远看不到动效。本组用**不进基线**
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

// 证据目录：`frontend/e2e/visual/tools/evidence/motion/`（`.gitignore` 已排除 `e2e/visual/tools/evidence/` 整目录）。
// Playwright 从 frontend/ 起跑（config 的 testDir/webServer 都相对它），process.cwd() 就是 frontend，
// 所以取证落在 cwd 之下而不是仓库根的文档目录——跑测不再需要事后 `git checkout --` 还原 docs/。
const MOTION_DIR = join(process.cwd(), 'e2e', 'visual', 'tools', 'evidence', 'motion')
mkdirSync(MOTION_DIR, { recursive: true })

test.describe('动效观察（非基线）', () => {
  // R-T14-1：本文件用的是**顶层 `reducedMotion: 'no-preference'`（写法 (a)）**。它对 config 的
  // `contextOptions.reducedMotion = 'reduce'` 的覆盖**有入库面可重放**——就是下面每个用例截图前那条
  // `matchMedia` 断言（读到 false 即覆盖成立，红了就是覆盖失效）。
  // 另一条写法 (b) `test.use({ contextOptions: { reducedMotion: 'no-preference' } })` 的 false 读数
  // 只存在于首轮那份**未入 git 的报告**里 ⇒ 与 LEDGER `P0a-MOTION-01` 同口径，只算"报告内观测、
  // 不可重放"；本文件因此**不宣称"两种写法都真覆盖"**，只宣称写法 (a) 这一条可重放。
  // 取 (a) 的理由：test.d.ts 注明 colorScheme/reducedMotion 这类可在用例间不同而不必重建 context。
  test.use({ storageState: storagePathFor('ADMIN'), reducedMotion: 'no-preference' })

  const shots: Array<{
    name: string
    path: string
    overlay?: 'modal'
    fullPage?: boolean
  }> = [
    { name: 'admin-dashboard', path: '/admin/dashboard', fullPage: true },
    { name: 'admin-courses', path: '/admin/courses' },
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

      // W1（RR-3）正读数：载体只认 /admin/dashboard（与降级对照用例同一页、同一选择器）。
      // 降级侧那条读的是 animationName === 'none'（负读数）；若有人把 design-system.css 的
      // `.animate-in { animation: rise-in … }` 基础规则**连同** reduce 块一起删掉，负读数读到的
      // 还是默认值 none、那条对照照样绿——**负读数本身不构成守卫**。这一条正读数就是它的成对另一半。
      if (shot.name === 'admin-dashboard') {
        const anim = await page.evaluate(
          () => getComputedStyle(document.querySelector('.animate-in')!).animationName,
        )
        expect(
          anim,
          'no-preference 下 .animate-in 不是 rise-in ⇒ 基础动效规则被删了，reduce 侧那条负读数当场失去意义',
        ).toBe('rise-in')
      }

      // P4-MIG-05 后 legacy 侧载体已不存在（el-dialog 全仓 0 命中，CourseManagement 的
      // <el-dialog> 已迁 Modal）——EP dialog-fade 入场动效随载体退役，dialog-open 观察用例
      // 一并退役（2026-10-27，P4-MIG-07）。自持侧载体（下方 modal 分支）继续守 Modal 侧动效面。
      if (shot.overlay === 'modal') {
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

        // R-T14-2 双拍（R-T14-7 收窄到元素级：page.screenshot({ clip }) 裁到 .modal 那块矩形）。
        // 不用 locator.screenshot()，理由和**限定**都见 dialog 分支：它等的是几何稳定，只对本仓这种
        // 带 transform 的入场致命；纯 opacity 的入场它并不等——别把这条读成"永远别用 locator.screenshot"。
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
        // 本实验现在由两条读数配成完整归因：像素面 c≠d（这把尺子对这块矩形的变化确实敏感）
        // ＋ 节点面 getAnimations>0（这个变化确实挂在 .modal 自己身上，见下一条 W2 的局限声明）。
        await page.addStyleTag({ content: '.modal{animation:rise-in 0.42s both}' })

        // W2（RR-2）节点级归因：c≠d 这条只把差异归因到"同一个 clip 矩形"，归因不到"是 .modal 这个节点在动"。
        // 这个洞是真实的：注入前面板不透明（design-system.css 的 .modal 背景是 var(--surface)），
        // rise-in 以 opacity:0 起步会把面板擦透明、露出遮罩与背景 ⇒ c≠d 的差值在物理上也可能来自背景。
        // 这条读数把那段"应该早就收敛了"的时序推断换成入库断言：读 .modal **自身**挂着的动画对象数量。
        // 【本工单 W2 二选一，实跑取的是 getAnimations 这一支，未用 opacity 中段读数】它的局限要说清：
        //  - 证的是"这个节点确实带一个由本次注入产生的 CSSAnimation"，**不**证读数那一刻仍在 active interval：
        //    rise-in 写的是 fill both，动画跑完后对象仍以 filling 状态留在 getAnimations() 里，所以
        //    这条**不能单独**证明"那两帧差在这个节点"——中段像素那一半仍由下面的 c≠d 负责，两条合起来才是归因。
        //  - 也挡不住"节点在动但矩形像素没变"（例如位移被父级裁剪），那一半同样由 c≠d 负责。
        //  - 反向极性和它配对：若这条读 0 而 c≠d 成立 ⇒ 差异不是 .modal 贡献的，本条当场红、归因作废。
        // subtree: false ⇒ 只数节点自身的动画，子节点（表单控件等）不会替它凑数。
        // 本机 Chromium（playwright 1.63.0 所用版本）实跑读数 = **1**，即这条首选方案可用，
        // 工单 W2 的备选（getComputedStyle 的 opacity 中段读数）**未启用**，本文件里也没有它。
        const inflight = await page.evaluate(
          () => document.querySelector('.modal')?.getAnimations({ subtree: false }).length ?? 0,
        )
        expect(
          inflight,
          '注入后 .modal 自身没有正在运行的动画 ⇒ 那两帧的差异不是这个节点贡献的，对偶实验没有归因',
        ).toBeGreaterThan(0)

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
        // admin-dashboard 这一格在截图前还多一条 W1 的 animationName 正读数（与降级侧那条负读数成对）。
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
  // 闭环现在是三条，缺一条就退回"负读数不构成守卫"（修复轮 2 的 W1）：
  //   ① 观察组的 matchMedia===false ⇒ "覆盖能被翻回 no-preference"；
  //   ② 观察组 admin-dashboard 那格的 animationName==='rise-in' ⇒ 正读数，证基础动效规则还在；
  //   ③ 本 describe 的 media===true ＋ animationName==='none' ⇒ 不翻时确实吃 config 的 reduce，
  //      且 reduce 真把 CSS 入场动效关掉了。
  // 没有 ② 的话，把 .animate-in 基础规则和 reduce 块**一起删掉**这种退化，本 describe 仍读 none 仍绿。
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
