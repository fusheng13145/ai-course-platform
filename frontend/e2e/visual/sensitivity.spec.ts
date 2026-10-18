import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import { copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import {
  ANCHORS,
  freezeClock,
  maskOf,
  setTheme,
  settlePage,
  SNAPSHOT_PAGE_TIME_MS,
  SNAPSHOT_THRESHOLDS,
  snapName,
  storagePathFor,
} from './visual-helpers'

/**
 * 这不是回归测试，是**量程测量**：故意注入一个已知幅度的视觉改动，看基线是否报警。
 * 报警 = 该幅度上这层防护有效；不报警 = 这个幅度往后 Phase 1-6 做了也看不见。
 * 所以"注入后测试变红"是**成功的测量结果**，不是失败——不要为了让它绿而去放宽阈值。
 * 注入一律用 addStyleTag，不落源文件：改源文件会连带影响 token 的所有消费方，
 * 量出来的就不是"基线能否看见改动"，而是"改动有多大"，是另一个问题。
 *
 * ── 判读口径（四种结局，含义互不相同，别混）────────────────────────────────
 *   FAILED  + "N pixels (ratio R of all image pixels) are different" = **检测到了**（这是好消息）
 *   FAILED  + "Expected an image WxH, received ..."                  = 页面高度与基线不符，
 *           内容自 Task 4 以来漂了，本轮量程读数作废（先修可比性，不是先修阈值）
 *   PASSED                                                          = **假绿**：改动被逐像素吸收，
 *           这正是 Task 4 图谱基线事故（0.33% 像素差判为相同）的复现，是本任务要抓的东西
 *   SKIPPED                                                         = 该档选择器在这个锚点零命中，
 *           注入是空操作，测不出任何结论（见下方"空操作守卫"）
 *
 * ── 比对口径：为什么期望图是"从 anchors 基线复制过来的"──────────────────────
 * 计划原稿跑 `--update-snapshots` 生成参考、再跑一次比对，两次都是**注入后**的同一张图，
 * 必然绿——那是"探针 vs 探针"，量的是同一张图自己跟自己相等，与量程无关（ worthless 的 green）。
 * 本 spec 改成：每次比较前，把该锚点**已提交的未注入基线**（`__snapshots__/anchors.spec.ts/`）
 * 逐字节复制成 `__snapshots__/sensitivity.spec.ts/<slug>-<theme>-<档>-probe.png`，
 * 然后让 toHaveScreenshot 拿"注入后的现场渲染"去比这张"未注入的基线"。
 * 于是比较对象 = 真改动 vs 真基线，用的阈值 = SNAPSHOT_THRESHOLDS（与回归同一组数），
 * 结局直接就是"这层防护对这个形状的改动报不报警"。
 * 复制而不是"把参考指到 anchors 目录"：那样一旦手滑带上 `--update-snapshots`，
 * 22 张已提交基线会被注入图原地覆盖——测量顺手毁掉被测对象，绝不能留这个口子。
 * 落盘的 `*-probe.png`（期望图副本）与 `*-probe-*.png`（含 PW_PROBE_CAPTURE 存的现场图）都是测量产物，
 * 已在 .gitignore 里；Playwright 自己在判红时写的 `-actual/-diff` 落在 test-results/ 且每次跑测开头会被清空，
 * 所以那两个目录不是证据来源，证据要用 PW_PROBE_CAPTURE 存下来的图。
 *
 * ── 与 anchors 的口径对齐（少一条就不可比）─────────────────────────────────
 * 主题 light、fullPage、animations:'disabled'、caret:'hide'、scale:'css'、同 mask 调用、
 * 同 freezeClock→goto→settlePage 顺序，并逐张复验截图时刻 = SNAPSHOT_PAGE_TIME_MS。
 * 时刻不落在同一个常量上，两张图差的就可能是"多跑了几帧动画"而不是注入的改动，
 * 读数（尤其 diff 像素数）会整块失真，红绿都不再可信。
 */
const PROBE_ANCHORS = ['login', 'admin-dashboard', 'admin-courses-table'] as const
const PROBE_THEME = 'light' as const

/**
 * 三档，由粗到薄。档 2/3 才是本次测量的目的。
 * CSS 与计划原稿逐字相同；`watch`/`prop` 是本任务补的**空操作守卫**用字段：
 * 注入必须真的落到计算样式上，否则"检不出"会被误读成"基线是瞎的"。
 */
const LADDER: Record<string, { css: string; watch: string; prop: string }> = {
  gross: { css: 'body{filter:brightness(0.97)}', watch: 'body', prop: 'filter' },
  // 档 2：单卡阴影增强 —— 正是 Phase 1 的 --sh-l1 alpha 0.06→0.10
  //（现行值是 theme.css:52 的 `0 1px 2px rgba(24,32,48,.05), 0 4px 14px rgba(24,32,48,.06)`，
  //  注入把它翻倍，形状与幅度都对得上 Phase 1）
  shadow: {
    css: '.l1-card{box-shadow:0 1px 2px rgba(24,32,48,0.12),0 4px 14px rgba(24,32,48,0.13)!important}',
    watch: '.l1-card',
    prop: 'boxShadow',
  },
  // 档 3：圆角差 2px —— 正是 Phase 2 半径阶梯里较薄的一档（--r-l1 现为 16px，theme.css:46）
  radius: { css: '.l1-card{border-radius:14px!important}', watch: '.l1-card', prop: 'borderTopLeftRadius' },
  /**
   * 第四档不是注入，是**噪声地板对照**：什么都不改，同一条路径再截一次去比同一张基线。
   * 没有它，"红"分不出是改动还是渲染抖动，"绿"也分不出是吸收了改动还是本来就零差——
   * 量程的下限正是这条线：地板有多高，阈值就只能收到比它高多少。
   * Step 3 收紧阈值时每一档都要先看这条：noise 若在某档阈值下变红，说明基线本身混进了噪声，
   * 那要回头查 settlePage/freezeClock，**不是**把阈值放宽回去。
   */
  noise: { css: '', watch: 'body', prop: 'filter' },
}
const LAD = process.env.PW_LADDER ?? 'gross'
const STEP = LADDER[LAD]
if (!STEP) throw new Error(`PW_LADDER=${LAD} 不在 ladder 里：${Object.keys(LADDER).join('/')}`)

/**
 * 测量旋钮：只影响**本探针**用的阈值，用来扫出"多薄的改动才开始报警"。
 * 产品基线（anchors）永远读 SNAPSHOT_THRESHOLDS，这里不改它、也不写第二份字面量。
 * PW_PROBE_RATIO=0 是用来**把两个旋钮分开看**的：ratio=0 时"任何一个被 pixelmatch 认作不同的像素"
 * 就报警，于是读数只反映 threshold；跑完全程再按 Step 3 决定 ratio 要不要动
 *（threshold 降到 0 仍检不出才准动它）。
 */
const THRESHOLDS = {
  threshold: process.env.PW_PROBE_THRESHOLD === undefined
    ? SNAPSHOT_THRESHOLDS.threshold
    : Number(process.env.PW_PROBE_THRESHOLD),
  maxDiffPixelRatio: process.env.PW_PROBE_RATIO === undefined
    ? SNAPSHOT_THRESHOLDS.maxDiffPixelRatio
    : Number(process.env.PW_PROBE_RATIO),
}

/** 读 PNG 头（IHDR）拿宽高，用来把 maxDiffPixelRatio 折算成"免检像素数" */
function pngSize(file: string): { width: number; height: number } {
  const buf = readFileSync(file)
  if (buf.readUInt32BE(0) !== 0x89504e47) throw new Error(`${file} 不是 PNG`)
  return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) }
}

async function computedSignatures(page: Page) {
  return page.evaluate(
    ({ watch, prop }: { watch: string; prop: string }) => {
      const els = Array.from(document.querySelectorAll(watch))
      // 只取前 50 个：足够判定"注入生效"，又不必把整页计算样式序列化回来
      const values = els.slice(0, 50).map((el) => {
        const style = getComputedStyle(el) as unknown as Record<string, string>
        return style[prop] ?? ''
      })
      return { matched: els.length, values }
    },
    { watch: STEP.watch, prop: STEP.prop },
  )
}

for (const slug of PROBE_ANCHORS) {
  const anchor = ANCHORS.find((a) => a.slug === slug)
  if (!anchor) throw new Error(`探针锚点 ${slug} 不在 ANCHORS——先核对再跑`)
  // 图谱类锚点要 pinRandom / 画布守卫这套额外流程，本探针先不背这个复杂度，越界就明确报错
  if (anchor.pinRandom || anchor.requirePaintedGraph) {
    throw new Error(`探针锚点 ${slug} 需要 pinRandom/画布守卫，本 spec 未实现，勿直接加进来`)
  }

  test.describe(`量程探针 · ${slug}`, () => {
    // 【计划原稿在此处写反了，照抄会把夹具清空】原稿是
    // `await page.context().storageState({ path: storagePathFor(anchor.role) })`，
    // 而这个 API 的语义是**把当前上下文保存到该路径**（见 anchors.spec.ts 同款注释、Task 3 报告）：
    // 刚造好的空 context 会把 Task 3 落盘的 admin.json 原地覆盖成 `{"cookies":[],"origins":[]}`，
    // 于是探针截到的是登出页——比的是"登录页 vs 工作台基线"，读数毫无意义，
    // 还顺手把后面所有跑测的会话弄没（后端登录有 10 次/60 秒限流，补不回来）。
    // 正确口径与 anchors 一致：建 context 时用 test.use({ storageState }) 载入。
    if (anchor.role !== 'ANON') test.use({ storageState: storagePathFor(anchor.role) })

    test(`量程探针 ${slug} · 档 ${LAD}`, async ({ page }, testInfo) => {
      // 计划原稿这一行是 `test.skip(anchor.role === 'ANON' && slug === 'error-404', 'D-1 已拒收 error 锚点')`，
      // 在本 spec 里**永远为假**：PROBE_ANCHORS 是写死的三元素字面量元组，不含 error-404，
      // TS2367 直接把它判成编译期错误（typecheck:gate 抓到 402→403）。删掉而不是留着"以后也许用得上"——
      // 真要扩锚点时，那条拒收规则应当由锚点表自己带（D-1 的修复会一并处理），不该散在探针里。
      if (typeof anchor.path !== 'string') {
        throw new Error(`探针锚点 ${slug} 的 path 需运行时解析，本 spec 未实现`)
      }

      await setTheme(page, PROBE_THEME)
      await freezeClock(page)
      await page.goto(anchor.path)
      await settlePage(page)

      // ↓ 与 anchors.runAnchor 同口径的可比性前置（不是重复劳动，缺一条整轮读数作废）
      expect(await page.evaluate(() => Date.now()), '截图时刻未落在常量上').toBe(SNAPSHOT_PAGE_TIME_MS)
      await expect(page.locator('html')).toHaveAttribute('data-theme', PROBE_THEME)
      await expect(page.locator('#app')).not.toBeEmpty()

      // ↓ 空操作守卫：注入前后各读一次计算样式（noise 档不注入，只读一次做记录）
      const before = await computedSignatures(page)
      if (STEP.css) await page.addStyleTag({ content: STEP.css })
      const after = await computedSignatures(page)
      if (STEP.css) {
        // 零命中 = 注入是空操作，"检不出来"跟基线量程没关系。判 SKIPPED，不判 GREEN。
        // 静态核对过：登录页视图 src/views/auth/Login.vue 的模板里没有 .l1-card
        //（它用的是自己 scoped 的 .auth-card / .brand-mark），
        // 而 /admin/dashboard（AdChartPanels/AdQuickEntries）与 /admin/courses（CourseManagement:53）都有。
        // 所以 shadow/radius 两档在 login 锚点上预期是 SKIPPED——这是计划把三档 CSS 写成
        // .l1-card 作用域、却把 login 也选进探针锚点造成的，不是缺陷复发，
        // 也别为了"让它跑起来"去改锚点名单或改 ladder 的 CSS（那就不再是 Phase 1/2 的形状了）。
        if (after.matched === 0) {
          console.log(`[probe-skip] ${slug} 档 ${LAD}：${STEP.watch} 零命中（matched=${after.matched}）`)
          test.skip(true, `档 ${LAD} 的选择器 ${STEP.watch} 在 ${slug} 零命中——注入是空操作，无量程结论`)
          return
        }
        // 命中了却没变 = 注入被更高优先级挡掉（或 !important 写错），测量无效，必须炸出来
        if (JSON.stringify(before.values) === JSON.stringify(after.values)) {
          throw new Error(
            `注入命中 ${after.matched} 个 ${STEP.watch} 但 ${STEP.prop} 未变化——探针本身失效，` +
              `读数会是假的"检不出"。检查 CSS 优先级/选择器：${STEP.css}`,
          )
        }
      }

      const arg = `${snapName(slug, PROBE_THEME)}-${LAD}-probe.png`
      const expected = testInfo.snapshotPath(arg, { kind: 'screenshot' })
      const baseline = join(dirname(expected), '..', 'anchors.spec.ts', `${snapName(slug, PROBE_THEME)}.png`)
      if (!existsSync(baseline)) {
        throw new Error(`未找到该锚点的已提交基线：${baseline}——没有"未注入的原图"就没法量量程`)
      }
      mkdirSync(dirname(expected), { recursive: true })
      // 每次都从已提交基线重新复制：期望图永远是"未注入的原图"，绝不可能是上一轮的注入产物
      copyFileSync(baseline, expected)

      const { width, height } = pngSize(expected)
      const allowancePx = width * height * THRESHOLDS.maxDiffPixelRatio
      console.log(
        `[probe] ${slug} 档 ${LAD} · 期望图=未注入基线 ${width}x${height} · ` +
          `threshold=${THRESHOLDS.threshold} maxDiffPixelRatio=${THRESHOLDS.maxDiffPixelRatio} ` +
          `免检额=${allowancePx.toFixed(0)}px · 命中${STEP.watch}=${after.matched}个`,
      )

      // PW_PROBE_CAPTURE=1：把"注入后的现场渲染"另存一份（文件名落在 *-probe-captured.png，已 gitignore）。
      // 为什么自己存：比对失败时 Playwright 虽然也会写 -actual/-diff，但那两样落在 test-results/ 里的
      // 属于例产物，且判绿（被吸收）时**什么都不写**——而那正是本任务最需要的读数。
      // 有了自己存的这张，才能离线量出"到底差多少像素、单通道色差多大"，
      // 而不是只报"红/绿"（Phase 1-6 要写的 diff % 就是这个数）。
      if (process.env.PW_PROBE_CAPTURE) {
        writeFileSync(
          join(dirname(expected), `${snapName(slug, PROBE_THEME)}-${LAD}-probe-captured.png`),
          await page.screenshot({ fullPage: true, animations: 'disabled', caret: 'hide', scale: 'css' }),
        )
      }

      // 与 anchors 用同一组阈值，测的才是同一层防护
      await expect(page).toHaveScreenshot(arg, {
        fullPage: true,
        animations: 'disabled',
        caret: 'hide',
        scale: 'css',
        mask: maskOf(page, anchor.maskSelectors),
        ...THRESHOLDS,
      })
    })
  })
}
