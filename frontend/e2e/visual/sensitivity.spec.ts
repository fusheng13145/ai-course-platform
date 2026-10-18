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
/**
 * 探针锚点。默认三个是计划口径。
 * `PW_PROBE_SLUGS=a,b` 可临时换锚点——本轮用它把 `noise` 档打到"收紧阈值后变红的锚点"上，
 * 用来分辨两种长得一样的红：**渲染噪声**（同一路径两次截到的图互不相同）与
 * **确定性漂移**（两次截到的图彼此逐字节相同，但都不等于已提交基线 = 产品真的变了）。
 * 判据只有靠双跑：单次读数分不出这两者，而它们的处置方向完全相反。
 */
const PROBE_ANCHORS: string[] =
  process.env.PW_PROBE_SLUGS?.split(',').map((s) => s.trim()).filter(Boolean)
  ?? ['login', 'admin-dashboard', 'admin-courses-table']
const PROBE_THEME = 'light' as const

/**
 * 三档，由粗到薄。档 2/3 才是本次测量的目的。
 * CSS 与计划原稿逐字相同；`watch`/`prop` 是本任务补的**空操作守卫**用字段：
 * 注入必须真的落到计算样式上，否则"检不出"会被误读成"基线是瞎的"。
 *
 * `settled` = 注入后该属性的**终态**里必须出现的片段（大小写/顺序按 Chromium 的计算样式序列化写）。
 * 为什么要有这一条而只比对"变了没"不够：守卫只验"变了"时，"注入生效但只生效一半"
 * （被别处的 `!important` 部分覆盖、transition 只推进了一半）会被当成合格继续跑，
 * 量出来的就不再是"这一档改动"而是"别的改动"。片段全部命中才算注入落到位。
 * 片段而不是整串相等：box-shadow 的序列化顺序属于实现细节，写死整串会在下次浏览器升级时假炸。
 */
const LADDER: Record<string, { css: string; watch: string; prop: string; settled: string[] }> = {
  gross: { css: 'body{filter:brightness(0.97)}', watch: 'body', prop: 'filter', settled: ['brightness(0.97)'] },
  // 档 2：单卡阴影增强 —— 正是 Phase 1 的 --sh-l1 alpha 0.06→0.10
  //（现行值是 theme.css:52 的 `0 1px 2px rgba(24,32,48,.05), 0 4px 14px rgba(24,32,48,.06)`，
  //  注入把它翻倍，形状与幅度都对得上 Phase 1）
  shadow: {
    css: '.l1-card{box-shadow:0 1px 2px rgba(24,32,48,0.12),0 4px 14px rgba(24,32,48,0.13)!important}',
    watch: '.l1-card',
    prop: 'boxShadow',
    settled: ['rgba(24, 32, 48, 0.12)', 'rgba(24, 32, 48, 0.13)'],
  },
  // 档 3：圆角差 2px —— 正是 Phase 2 半径阶梯里较薄的一档（--r-l1 现为 16px，theme.css:46）
  radius: { css: '.l1-card{border-radius:14px!important}', watch: '.l1-card', prop: 'borderTopLeftRadius', settled: ['14px'] },
  /**
   * 第四档不是注入，是**噪声地板对照**：什么都不改，同一条路径再截一次去比同一张基线。
   * 没有它，"红"分不出是改动还是渲染抖动，"绿"也分不出是吸收了改动还是本来就零差——
   * 量程的下限正是这条线：地板有多高，阈值就只能收到比它高多少。
   * Step 3 收紧阈值时每一档都要先看这条：noise 若在某档阈值下变红，说明基线本身混进了噪声，
   * 那要回头查 settlePage/freezeClock，**不是**把阈值放宽回去。
   */
  noise: { css: '', watch: 'body', prop: 'filter', settled: [] },
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

/**
 * 空操作守卫用的计算样式读数。
 *
 * 【真基线上量出来的一个坑，非离线自检台所能发现】`.l1-card` 带
 * `transition: box-shadow 0.25s var(--ease-out)`（design-system.css:63）。
 * 本页时钟是被 freezeClock 停住的，注入后过渡**开始了但推进不了**，
 * 于是 getComputedStyle('.l1-card').boxShadow 在注入后仍读回旧值——
 * 第一版守卫正是这样误报"注入被优先级挡掉"（shadow 档两个锚点都炸），
 * 而 radius 档（border-radius 不过渡）却正常。
 *
 * 读数口径要跟截图口径一致才有意义：toHaveScreenshot 用的是 `animations:'disabled'`，
 * 它在截图瞬间把过渡/动画**强制落到终态**（实现见 playwright-core screenshotter：
 * `root.getAnimations()` 里每个有限动画都 `animation.finish()`，并挂 transitionrun/animationstart
 * 监听把截图过程中新起的过渡也收尾）。所以这里在读之前临时压一枚
 * `*{transition:none!important;animation:none!important}`，读到的是"这一帧之后的目标值"，
 * 读完立刻移除——截图仍由 animations:'disabled' 决定，两者从此读同一个值。
 *
 * 【这枚临时 style 标签为什么不会污染被截的图（机制，不是宣称）】
 * add → querySelectorAll/getComputedStyle → remove 全在**同一个同步 JS 任务**里，中间没有任何 await，
 * 而 Blink 的绘制只可能发生在两个任务之间，所以这枚标签**从未进入过任何一帧已提交的画面**；
 * 它被移除时也没有属性值发生变化（只是 transition-property 恢复），因此不会再排一趟新过渡。
 * 这条性质可以单独取证：`PW_PROBE_SKIP_GUARD=1` 时不压这枚标签（同时跳过"未变化"抛错，
 * 因为那时 shadow 档必然读到旧值——那正是本轮踩过的坑），其余流程一字不动，
 * 两次 PW_PROBE_CAPTURE 的产物逐字节对比即是"守卫对被测画面的影响"。
 * 该开关只用于取证，跑出来的读数不作量程结论（守卫没验，注入是否落地未知）。
 */
const TRANSITION_KILL = !process.env.PW_PROBE_SKIP_GUARD

async function computedSignatures(page: Page) {
  return page.evaluate(
    ({ watch, prop, kill }: { watch: string; prop: string; kill: boolean }) => {
      let matched = 0
      let values: string[] = []
      const kill1 = document.createElement('style')
      kill1.id = 'probe-guard-transitions-off'
      kill1.textContent = '*,*::before,*::after{transition:none!important;animation:none!important}'
      if (kill) document.head.appendChild(kill1)
      try {
        const els = Array.from(document.querySelectorAll(watch))
        matched = els.length
        // 只取前 50 个：足够判定"注入生效"，又不必把整页计算样式序列化回来
        values = els.slice(0, 50).map((el) => {
          const style = getComputedStyle(el) as unknown as Record<string, string>
          return style[prop] ?? ''
        })
      } finally {
        kill1.remove()
      }
      return { matched, values }
    },
    { watch: STEP.watch, prop: STEP.prop, kill: TRANSITION_KILL },
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
      //   读数一律取**终态**（见 computedSignatures 的过渡说明）——否则 shadow 档会把
      //   "过渡刚开始"读成"注入被优先级挡掉"，误判成本任务的结论方向。
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
        if (!TRANSITION_KILL) {
          console.log(`[probe-guard-off] ${slug} 档 ${LAD}：守卫已按 PW_PROBE_SKIP_GUARD 关掉，` +
            `只取证不参与判读（before=${before.values[0] ?? ''} / after=${after.values[0] ?? ''}）`)
        } else {
          // 命中了却没变 = 注入被更高优先级挡掉（或 !important 写错），测量无效，必须炸出来
          if (JSON.stringify(before.values) === JSON.stringify(after.values)) {
            throw new Error(
              `注入命中 ${after.matched} 个 ${STEP.watch} 但 ${STEP.prop} 未变化——探针本身失效，` +
                `读数会是假的"检不出"。检查 CSS 优先级/选择器：${STEP.css}`,
            )
          }
          // 变了还不够：终态里必须带上注入的那几个片段，否则量的不是这一档改动
          const notSettled = after.values.filter(
            (v) => !STEP.settled.every((frag) => v.includes(frag)),
          )
          if (notSettled.length > 0) {
            throw new Error(
              `${after.matched} 个 ${STEP.watch} 里有 ${notSettled.length} 个的 ${STEP.prop} 终态不含 ` +
                `${JSON.stringify(STEP.settled)}（实测首个不符：${notSettled[0]}）` +
                `——注入没有完整落到终态，本轮量程读数作废`,
            )
          }
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
          `免检额=${allowancePx.toFixed(0)}px · 命中${STEP.watch}=${after.matched}个 · ` +
          `${STEP.prop}: ${before.values[0] ?? '(无)'} → ${after.values[0] ?? '(无)'}`,
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
