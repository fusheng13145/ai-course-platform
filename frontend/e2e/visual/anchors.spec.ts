import { expect, test } from '@playwright/test'
import type { Page, TestInfo } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import {
  ANCHORS,
  assertComparableRender,
  assertGenericGuards,
  assertAnchorArrival,
  assertPinCoverage,
  attachPayloadRecorder,
  DATA_ARRIVAL_CHECKS,
  DATA_PATHS,
  freezeClock,
  installPayloadPin,
  maskOf,
  pinRandom,
  settlePage,
  setTheme,
  SHELL_KEEP_ALIVE_PATHS,
  snapName,
  SNAPSHOT_THRESHOLDS,
  storagePathFor,
  type AnchorSpec,
  type PinState,
  type ThemeName,
} from './visual-helpers'

/** 课程列表页自身的 URL——__FROM_COURSE_LIST__ 先到这里取 id，再进壳页 */
const COURSE_LIST_PATH = '/student/courses'
/** 列表页的数据源：/student/courses 的 .tree-row 就是它 courses[0..] 的渲染结果 */
const COURSE_LIST_API = '/api/student/dashboard'

/** 离线取证图与比较器读数的落点（.gitignore 里 e2e/visual/tools/evidence/ 已排除） */
const EVIDENCE_DIR = join(process.cwd(), 'e2e', 'visual', 'tools', 'evidence')
const MEASURER = join(process.cwd(), 'e2e', 'visual', 'tools', 'measure-diff.cjs')

interface MeasureReading {
  width: number
  height: number
  byteDiffPx: number
  rgbByteDiffPx: number
  pixelmatchCount: number
  allowancePx: number
  failed: boolean
  maxChannelDelta: number
  md5: { expected: string; actual: string; equal: boolean }
  bbox: { minX: number; minY: number; maxX: number; maxY: number } | null
  threshold: number
  maxDiffPixelRatio: number
}

/**
 * 调**已入库**的只读比较器取数（Task 4c Step 1g：注释与台账里每个像素数都要能一条命令重生成）。
 * 不在这里重写判据——重写出来的"看起来一样"的尺必然与跑测器漂开。
 */
function measureAgainstBaseline(expected: string, actual: string): MeasureReading {
  // 显式传 t=0 / r=0，与下面 assertZeroDiff 的注释口径一致。
  // 不传的话比较器默认读**交付配置**（当前 r=0.0001 ⇒ 免检额 130 px；任何 r>0 都会吸收掉 count ≤ 免检额 的漂移），
  // 打印出来的就是交付口径的数，而这条用例的注释声明的是"归零验证用零免检额"——注释与代码各说一套
  // 正是 Step 1f/1g 要清的那类问题。（真正的判据本来就不看 `failed`：下面要求 md5 相等且 byteDiffPx===0，
  // 比任何一档 t/r 都硬。）
  const out = execFileSync(
    process.execPath,
    [MEASURER, expected, actual, '--json', '--threshold=0', '--ratio=0'],
    { encoding: 'utf8' },
  )
  return JSON.parse(out) as MeasureReading
}

function describeReading(r: MeasureReading): string {
  return (
    `计数 ${r.pixelmatchCount} · 字节差 ${r.byteDiffPx} · Δmax ${r.maxChannelDelta}/255 · ` +
    `免检额 ${r.allowancePx.toFixed(2)}（t=${r.threshold} r=${r.maxDiffPixelRatio} 口径） · ` +
    `md5 ${r.md5.equal ? '相同' : '不同'}` +
    (r.bbox ? ` · 包围盒 x ${r.bbox.minX}..${r.bbox.maxX} y ${r.bbox.minY}..${r.bbox.maxY}` : '')
  )
}

/**
 * 图谱画布"有墨"的下限（非透明像素数）。判据只到"空白画布 vs 画了东西"这一层，
 * 不做像素比对（那是 toHaveScreenshot 的活）。
 * 参照实测：4001 这门课的画布非透明像素 = **6051**，全部来自 10 条曲线边 + 8 组文字标签
 * ——节点圆点本身没画（产品缺陷，见 task-4-report 的 D-9），所以这里不能写成"节点面积"口径。
 * 取 300 是把它当"完全没画"的哨兵（差 20 倍），不是把 300 当成质量门槛。
 * ⚠️ 这个 6051 是上一轮单次观测，**不可重生成**（当时没有入库的比较器）；
 *    现在它能重生成：跑一次 anchors 后看本用例打印的 painted 数，或
 *    `npx playwright test -c playwright.visual.ts anchors -g "student-knowledge-graph"` 的 [graph] 行。
 */
const GRAPH_MIN_PAINTED_PX = 300

/**
 * 图谱锚点的"真画出内容"守卫（评审 Important 1 的第二半）。
 *
 * 为什么四条通用断言拦不住上一轮那张空态图：占位图非空壳、没有 .error-boundary、
 * data-theme 也写对了、URL 也不在 /403|/503——它本来就是一张"长得很正常"的页面，
 * 只是核心那块没画。所以守卫必须看图本身，三道判据从三个层面各钉一枚，缺一不够：
 * ① 组件级：`.graph-chart canvas` 存在。SkgGraphPanel.vue 的 v-if 条件是
 *    `graphOption.series[0].data.length > 0`，画布在 == 节点数据非空；
 *    走空态时渲染的是同文件的 EmptyState，没有 canvas。
 * ② 页面级：面板角标"N 个知识点"（绑 filteredNodeCount）> 0。
 *    ①会随组件模板改动静默失效，②是独立第二条腿；顺带把"关系 API 失败后
 *    走基础 API 回退"（useStudentKnowledgeGraph.ts）也算进去了——回退成功
 *    同样有节点，回退也空则两道都过不去。
 * ③ 绘制级：canvas 上真的有非透明像素。①②只证明"数据到了 DOM"，不证明 ECharts
 *    画出来了（尺寸塌陷、初始化异常都能让画布存在却全透明）。
 * 断言都在截图**之前**跑，所以 `--update-snapshots` 也替空态图生不出基线。
 */
async function assertGraphPainted(page: Page): Promise<void> {
  const canvas = page.locator('.graph-panel .graph-chart canvas').first()
  await expect(canvas, '图谱锚点截到的是空态：画布没渲染出来').toBeAttached({ timeout: 20000 })

  const badge = await page.locator('.graph-panel .node-count').first().innerText()
  const nodeCount = Number(badge.match(/\d+/)?.[0] ?? 'NaN')
  expect(nodeCount, `图谱角标 "${badge}" —— 节点数为 0 的占位图不得当基线`).toBeGreaterThan(0)

  const painted = await page.evaluate(() => {
    const list = Array.from(document.querySelectorAll('.graph-chart canvas'))
    return list.reduce((total, el) => {
      const canvas = el as HTMLCanvasElement
      const ctx = canvas.getContext('2d')
      if (!ctx) return total
      const { data } = ctx.getImageData(0, 0, canvas.width, canvas.height)
      let n = 0
      // 逐个像素看 alpha：ECharts 未绘制的区域是透明的
      for (let i = 3; i < data.length; i += 4) if (data[i] > 0) n++
      return total + n
    }, 0)
  })
  console.log(`[graph] painted=${painted}px（门槛 ${GRAPH_MIN_PAINTED_PX}）——重生成：本行每次跑都打`)
  expect(painted, '图谱画布存在但一个像素都没画上去').toBeGreaterThanOrEqual(GRAPH_MIN_PAINTED_PX)
}

/**
 * 学生 /student/courses/:id/learning-center 的 :id 必须来自当前种子数据的真实列表首项，
 * 且排序口径固定——否则"取第一条"在不同环境下取到不同课程，图就不该相等。
 *
 * 两处与计划原稿不同，都是本机实测出来的（原稿照跑必然 15s 超时）：
 * ① 原稿没导航就取选择器。runAnchor 调本函数时页面还停在 about:blank，
 *    `page.locator(...).waitFor` 永远等不到，报的是 TimeoutError 而不是"种子没课"，
 *    归因方向直接被带偏。这里先 goto 列表页。
 * ② 原稿的选择器 `a[href*="/student/courses/"], [data-course-id]` 在本页零命中：
 *    Courses.vue 的课程卡是 `div.tree-row` + `@click="selectCourse(c)"`，
 *    既没有 href 也没有 data 属性，"进入学习"同样是按钮不是链接。
 *    为了不改产品源码去迁就测试，id 改从**这一页真正渲染所用的那个响应**里取：
 *    .tree-row 与响应数组同源同序，所以 `courses[0].courseId` ≡ 列表首卡。
 *    DOM 侧仍必须真有一张可见卡片，
 *    缺这一条断言时空种子库会解析出 id、截出一张空态图当基线——正是注释里点名的事故。
 *
 * 注意：本函数**不得**调 settlePage。settlePage 一次要把页面时间推进 9400ms
 * （5000 预热 + 1200 轮询预算 + 3200 帧数沉降，见 visual-helpers 的 SNAPSHOT_PAGE_TIME_MS 推导），
 * 若这里也调一次，__FROM_COURSE_LIST__ 锚点的截图时刻就比其余 23 张多一整段，
 * 而"课程列表要几轮才稳定"又会把多推进的那段轮询变成随机量——等于专门给这一页装了个随机源。
 * 列表页的 DOM 只需已渲染，用 locator 等待即可。
 * 这条纪律现在有机器兜底：assertComparableRender 的时刻常量断言会把"多 settle 一次"直接判红。
 */
async function resolvePath(page: Page, anchor: AnchorSpec): Promise<string> {
  // AnchorSpec.path 的联合类型里函数形态是给调用方自带异步口径用的，这里一并消费，
  // 否则下面 `return anchor.path` 会把 (() => Promise<string>) 当字符串交给 page.goto
  if (typeof anchor.path === 'function') return await anchor.path()
  if (anchor.path !== '__FROM_COURSE_LIST__') return anchor.path

  // 监听挂上再导航，否则会漏掉这一次导航触发的首个响应
  const listed = page.waitForResponse(
    (res) => new URL(res.url()).pathname === COURSE_LIST_API && res.status() === 200,
    { timeout: 15000 },
  )
  await page.goto(COURSE_LIST_PATH)
  const body = (await (await listed).json()) as { data?: { courses?: { courseId?: number }[] } }
  // 首卡真的在屏幕上才算"列表可用"；数据对了但页面空着，同样不许当基线
  await page.locator('.tree-row').first().waitFor({ state: 'visible', timeout: 15000 })
  const id = body?.data?.courses?.[0]?.courseId
  if (!id) throw new Error('无法从 /student/courses 解析课程 id——种子数据或选择器需复核')
  return `/student/courses/${id}/learning-center`
}

/**
 * 会话必须在建 context 时载入，不能在测试体内调 page.context().storageState({path})——
 * 那个 API 的语义是**把当前会话保存到该路径**，在刚造好的空 context 上调用，
 * 会把 Task 3 写好的夹具原地覆盖成空会话。这是本任务最容易踩的坑，
 * 且现象是"基线全都截成登录页"而非报错，所以用一条显式断言兜住（见 assertGenericGuards）。
 */
async function runAnchor(page: Page, anchor: AnchorSpec, theme: ThemeName, testInfo: TestInfo): Promise<void> {
  await setTheme(page, theme)
  // 随机源必须在导航前钉：ECharts 力导向用 Math.random() 播节点初值，
  // 而冻结时钟挡不住它（只 fake 时间类 API）。只有声明了的锚点开，理由见 pinRandom 注释。
  if (anchor.pinRandom) await pinRandom(page)
  // 解析放在 freezeClock 之前：__FROM_COURSE_LIST__ 会多走一次列表导航，
  // 时钟若已 install，这次导航就把"冻结后又一次 runFor 机会"混进了截图时刻的账，
  // 24 张就只有 23 张落在同一个页面时间上。解析完再装时钟，页面时间口径全表一致。
  const target = await resolvePath(page, anchor)

  // 载荷钉死要装在**导航前**，且只有声明了 pinPaths 的锚点才装（理由见 visual-helpers 的 pin 注释块）。
  // PW_RECORD_PAYLOADS=1 时反过来录写真后端响应——录制模式不产量程结论，只产 fixture。
  const recording = process.env.PW_RECORD_PAYLOADS === '1'
  let flushRecord: (() => Promise<void>) | null = null
  let pinState: PinState | null = null
  if ((anchor.pinPaths?.length ?? 0) > 0) {
    if (recording) flushRecord = attachPayloadRecorder(page, anchor)
    else pinState = await installPayloadPin(page, anchor)
  }

  await freezeClock(page)
  await page.goto(target)
  await settlePage(page)

  // 可比性前置 + 该锚点自己的"数据确已到位"断言：整组都在 visual-helpers 里，
  // anchors 与 sensitivity 共用同一份（上一版探针自称同口径、实际少三条，见 assertComparableRender）。
  //
  // 【已知缺陷 · error-404 两主题恒红，勿用 --update-snapshots 吸收】
  // /403 /404 /503 三个错误页拿不到 data-theme：全仓只有 useTheme 的 applyTheme 会写这个属性
  // （useTheme.ts:15），而 applyTheme 只在调用过 useTheme() 的组件里跑（ThemeSwitcher / Profile），
  // 错误页既无 AppShell 也无 ThemeSwitcher → <html> 上永远没有 data-theme，
  // theme.css 的 [data-theme='dark'] 整块不生效，暗色用户看到的仍是 :root 的浅色 token。
  // 后果：这两张基线的明与暗必然逐像素相同，正是本断言要拦的事，所以按 Task 4 Step 3 的口径
  // 记为真实缺陷（缺陷 D-1，另开修页任务），不入库。修好后这 2 张自动补生成，无需改本文件。
  await assertComparableRender(page, anchor, theme)
  // 图谱代表必须真的"有图"——上一轮它截的是占位空态，四条通用断言全绿（见 assertGraphPainted）
  if (anchor.requirePaintedGraph) await assertGraphPainted(page)

  // `.png` 在这里补，不在 snapName 里补：Playwright 1.63 的 toHaveScreenshot 先对**传入的 arg**
  // 取 extname 决定 mimeType（matchers/expect.js:12437 的 getMimeTypeForPath(basename(expectedPath))），
  // 无扩展名直接抛 `Screenshot name "login-light" must have a '.png' or '.webp' extension`
  // （抛点在 expect.js:12595-12597），压根走不到比对。模板里的 {ext} 不是白给的，
  // 它就是这个 arg 的扩展名（workerProcessEntry.js:1244-1274 解析路径段，关键行 :1266
  // `basename(subPath, ext)`），arg 带 .png 落盘名仍是 login-light.png，不会变成 .png.png。
  // 保持 snapName 无扩展名是 helper 已发布并被 helpers.spec.ts 钉住的契约，改动只发生在消费侧。
  const arg = `${snapName(anchor.slug, theme)}.png`
  await expect(page).toHaveScreenshot(arg, {
    fullPage: true,
    animations: 'disabled',
    caret: 'hide',
    scale: 'css',
    mask: maskOf(page, anchor.maskSelectors),
    // 阈值不在这里写字面量：Task 4b 的灵敏度测量测的就是这一组数，
    // 两处各写一份就一定会漂，届时"量出来的防护"和"实际用的防护"不是同一个东西。
    ...SNAPSHOT_THRESHOLDS,
  })

  if (pinState) {
    assertPinCoverage(anchor, pinState)
    // 日期锚点的"归零"判据（brief Step 1 的判定式：钉载荷后计数必须归 0，不是"变小"）。
    // toHaveScreenshot 的"绿"根本不证明零漂移：交付档 r=0.0001 只有 130 px 免检额，但**判据是分类器计数**，
    //   字节差 618 / 计数 321 那种漂移在 0.0005 下被整体吸收、在 0.0001 下也只按 321 这个数变红，
    //   而"618 个像素真的变了"这件事从来不在 ratio 的账里。
    // 这里用同一个 mask、同一组截图参数另截一张，交给已入库的比较器按 t=0/r=0 读死数，并**要求 md5 相等**。
    await assertZeroDiff(page, anchor, theme, testInfo)
    console.log(`[pin] ${anchor.slug}/${theme} 钉掉 ${pinState.served.length} 次请求：${JSON.stringify(pinState.served)}`)
    if (pinState.unpinnedTodayBearing.length > 0) {
      console.log(`[pin-audit] ${anchor.slug} 仍在消费带"今天"日期的未钉端点 ${pinState.unpinnedTodayBearing.length} 个：${JSON.stringify([...new Set(pinState.unpinnedTodayBearing)])}`)
    }
  }
  if (flushRecord) await flushRecord()
}

/**
 * 钉死之后才算得出的那条：**与已提交基线 md5 相等且字节差 0**（spec §6.16 的措辞规矩：md5 相等才许说"逐字节相同"）。
 * 读数口径 t=0 / r=0——交付那一档的 ratio（当前 0.0001 ⇒ 130 px）会吸收 `count ≤ 免检额` 的一切，
 * 用它验"归零"等于没验。只对本套件里声明了 pinPaths 的锚点跑（现在 = 4 张日期图）。
 */
async function assertZeroDiff(page: Page, anchor: AnchorSpec, theme: ThemeName, testInfo: TestInfo): Promise<void> {
  const arg = `${snapName(anchor.slug, theme)}.png`
  const baseline = testInfo.snapshotPath(arg, { kind: 'screenshot' })
  mkdirSync(EVIDENCE_DIR, { recursive: true })
  const capture = join(EVIDENCE_DIR, arg)
  // 与 toHaveScreenshot 完全同一组参数（含 mask）——Task 4c Step 1e：两条路径必须共用 mask，
  // 否则离线自检台与跑测器的读数会静默分叉，而喂给台账的是前者。
  writeFileSync(capture, await page.screenshot({
    fullPage: true,
    animations: 'disabled',
    caret: 'hide',
    scale: 'css',
    mask: maskOf(page, anchor.maskSelectors),
  }))
  const r = measureAgainstBaseline(baseline, capture)
  if (r.md5.equal && r.byteDiffPx === 0) return
  throw new Error(
    `[零漂移] ${anchor.slug}/${theme} 钉了载荷仍与基线不符：${describeReading(r)}\n` +
      `要么 fixture 不是基线那一天的画面（重录：PW_PORT=5175 PW_RECORD_PAYLOADS=1 npx playwright test -c playwright.visual.ts anchors -g "${anchor.slug}"），` +
      `要么这一带真的有别的改动。禁止用 --update-snapshots 把它吸收掉。`,
  )
}

for (const theme of ['light', 'dark'] as ThemeName[]) {
  for (const role of ['ANON', 'STUDENT', 'TEACHER', 'ADMIN'] as const) {
    const subset = ANCHORS.filter((a) => a.role === role)
    if (subset.length === 0) continue
    test.describe(`视觉基线 · ${theme} · ${role}`, () => {
      if (role !== 'ANON') test.use({ storageState: storagePathFor(role) })
      for (const anchor of subset) {
        test(anchor.slug, async ({ page }, testInfo) => runAnchor(page, anchor, theme, testInfo))
      }
    })
  }
}

/* ============================================================================ *
 * Step 2：把"空态能过守卫"这个洞堵掉的**可证伪性证据**
 * ============================================================================ *
 * 【一次真实事故，不是假想】Task 4b 记录：后端瞬时失败把 teacher-dashboard 渲染成空态，
 * 而 .error-boundary 计数 0、#app 非空、URL 未被改写、data-theme 也对——四条通用守卫全过。
 * 【本组用例断言的是什么】**每个锚点的专属"数据已到位"断言在空响应下必须红**——
 * 一条你从没让它红过的断言不是守卫。通用守卫这次绿还是红**另外报出来**：
 * 只有"通用守卫绿 + 专属断言红"这个组合才等于复现了那次事故，
 * 而该组合由单独一条用例逐字钉住（teacher-dashboard，就是事故那一页），不靠矩阵去暗示。
 * 【极性】这里 expect(arrivalThrew).toBe(true) 的绿 = "守卫确实可红"，与量程探针那种
 * "FAILED=检出了"的反极性不是一回事——那条要报的是测量结论，本条要报的是断言的有效性。
 * 【为什么不装 payload pin】本组用例要的就是"真后端给空响应"，钉死会把空态一起钉没了。
 * 【截图】一律不做 toHaveScreenshot：本组只验断言，不产基线，也就没有"顺手吸收缺陷"的口子。
 */

/** 需要保住的会话/外壳端点：把它们也打空会得到 /login 重定向，红得无法归因（那就不是本用例要证的） */
async function stubApiToEmpty(page: Page, slug: string): Promise<void> {
  const prefixes = DATA_PATHS[slug]
  if (!prefixes) throw new Error(`锚点 ${slug} 没有 DATA_PATHS 条目——空态可证伪矩阵无法收窄，先补名单`)
  await page.route('**/api/**', async (route) => {
    const u = new URL(route.request().url())
    if (route.request().method() !== 'GET') return route.fallback()
    if (SHELL_KEEP_ALIVE_PATHS.some((k) => u.pathname.startsWith(k))) return route.fallback()
    const mine = prefixes.includes('ALL') || prefixes.some((p) => u.pathname.startsWith(p))
    if (!mine) return route.fallback()
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      // 空响应而不是 5xx：5xx 会被错误边界接住（那条守卫本来就能拦），
      // 而"接口 200、data 空"才是那次事故的样子——四条通用守卫对它全盲。
      body: JSON.stringify({ code: 200, message: 'ok', data: null }),
    })
  })
}

/**
 * 接口打空后**画面确实变空**的那些锚点（逐个实测出来的：桩装上后 #app 里的数据元素确实没了）。
 * admin-system-form 不在这里面——见下面矩阵 B 的说明，它这一页渲染的是组件内默认值，
 * 把 /api/system-configs 打空后 DOM 一字不变（实测 app 节点数 1909→1909、filled 26→26），
 * 所以它的专属断言只能用"拿错页跑"来证伪。
 */
const EMPTY_API_FALSIFIABLE = [
  'admin-dashboard',
  'student-dashboard',
  'teacher-dashboard',
  'admin-courses-table',
  'student-courses-modal',
  'student-learning-center',
  'student-knowledge-graph',
  'admin-governance-analytics',
  'admin-profile',
]

for (const role of ['STUDENT', 'TEACHER', 'ADMIN'] as const) {
  const subset = ANCHORS.filter((a) => a.role === role && EMPTY_API_FALSIFIABLE.includes(a.slug))
  test.describe(`空态守卫可证伪 · ${role}`, () => {
    test.use({ storageState: storagePathFor(role) })
    for (const anchor of subset) {
      test(`${anchor.slug} 的空态必须被它自己的数据到位断言拦下`, async ({ page }) => {
        // 先走真实列表把 __FROM_COURSE_LIST__ 解析出来，再装空桩（桩装上后就没有课程可解析了）
        const target = await resolvePath(page, anchor)
        await setTheme(page, 'light')
        await stubApiToEmpty(page, anchor.slug)
        await freezeClock(page)
        await page.goto(target)
        await settlePage(page)

        // ① 专属断言必须红——这一条是本用例的硬要求：一条从没红过的断言不是守卫
        let threw = ''
        try {
          await assertAnchorArrival(page, anchor.slug)
        } catch (e) {
          threw = (e as Error).message
        }
        expect(threw, `${anchor.slug}：接口全空却过了"数据已到位"断言——这条守卫是空的（spec §6.14）`).not.toBe('')

        // ② 通用守卫这次的地位：**报**出来，不当断言（只有 teacher-dashboard 那一条当断言，见下）。
        //    某些页把整页数据打空会连带让 #app 也空掉/主题丢失，那时通用守卫也红，
        //    本用例仍然证明了专属断言可红，但"只有专属断言拦得住"这个洞在该页不成立——
        //    这句必须写清楚，不然矩阵里 9 个绿会读成"9 次都复现了那次事故"。
        let genericGreen = true
        let genericErr = ''
        try {
          await assertGenericGuards(page, anchor, 'light')
        } catch (e) {
          genericGreen = false
          genericErr = (e as Error).message.split('\n')[0]
        }
        console.log(
          `[falsify] ${anchor.slug} 专属断言=红 ✓ · 通用守卫=${genericGreen ? '绿（= 这次复现了"只有专属断言拦得住"）' : `也红：${genericErr}`}`,
        )
      })
    }
  })
}

/**
 * 那次事故的**逐字复现**（spec §6.14 点名的就是这一页）：
 * 后端瞬时失败让 teacher-dashboard 渲染成空态，而 .error-boundary 计数 0、#app 非空、
 * URL 未被改写、data-theme 也对——四条通用守卫全过。
 * 这一条把那个组合钉成断言：通用守卫**必须绿** + 专属断言**必须红**。
 * 任何一侧退让（比如哪天 #app 非空那条也能拦住空态了），这条就红，提示台账更新。
 *
 * 【为什么必须包在带 storageState 的 describe 里（Task 4c 收尾时修的一次红）】
 * `/teacher/dashboard` 的路由守卫要求真实会话：没有会话时页面被踢到
 * `/login?redirect=/teacher/dashboard`，于是这条用例红在**会话**上而不是红在它要证的组合上——
 * `assertGenericGuards` 的 `/login` 那条（visual-helpers.ts 的 assertGenericGuards）判得完全正确，
 * 该修的是用例自己的 context 而不是把守卫放宽。
 * 上一版把它写在**顶层** `test()`，而顶层 test 不在主题×角色的矩阵 describe 之内，
 * 拿不到 `test.use({ storageState })`。注意 `test.use()` 在 `test()` 体内是非法的（Playwright 只在
 * describe/file 级收集它），所以只能包一层 describe。
 * 【这条用例的三个"不许"】不许 weaken 通用守卫来让它绿；不许 stub 会话（那会把"真实页面 + 真实会话"
 * 这个前提弄没）；桩只打在 **API** 上（`stubApiToEmpty`），因为本用例要证的正是
 * "接口 200 而 data 为空"这种通用守卫全盲的画面。
 */
test.describe('事故复现 · TEACHER 会话', () => {
  test.use({ storageState: storagePathFor('TEACHER') })
  test('teacher-dashboard：通用守卫全绿而专属断言红', async ({ page }) => {
    await setTheme(page, 'light')
    await stubApiToEmpty(page, 'teacher-dashboard')
    await freezeClock(page)
    await page.goto('/teacher/dashboard')
    await settlePage(page)
    await assertGenericGuards(page, ANCHORS.find((a) => a.slug === 'teacher-dashboard')!, 'light')
    let threw = ''
    try {
      await assertAnchorArrival(page, 'teacher-dashboard')
    } catch (e) {
      threw = (e as Error).message
    }
    expect(threw, '四条通用守卫都过的空态，专属断言也必须拦得住——这条红了就是那次事故重演').not.toBe('')
    console.log(`[falsify] teacher-dashboard 复现成功：通用守卫绿 + 专属断言红（${threw.split('\n')[0]}）`)
  })
})

/**
 * 矩阵 B：这一页的 DOM 不随接口空响应变化（表单渲染的是组件内默认值），
 * 所以它的专属断言用"拿错页跑"证伪——在登录页上跑系统设置的断言必须红。
 * 红的是"这一页根本没有 35 个 .el-form-item / 没有已回填的输入框"，
 * 与 admin-system-form 的断言口径一致，仍然证明了它不是"任何页面都绿"的假守卫。
 * 【同样必须带会话】本用例第一半要在**真的到达 /admin/system** 之后才能说"接口打空对它无效"：
 * 没有 ADMIN 会话时它落在 /login，那句 `[falsify] …=红` 红的是会话不是接口，
 * 等于把本用例的主张变成没测过。第二半的"拿错页跑"仍用**新建的匿名 context**，不受影响。
 */
test.describe('空态守卫可证伪 · ADMIN（系统设置）', () => {
  test.use({ storageState: storagePathFor('ADMIN') })
  test('admin-system-form 的断言用"拿错页跑"证伪（接口打空对它无效）', async ({ page }) => {
    const anchor = ANCHORS.find((a) => a.slug === 'admin-system-form')!
    await setTheme(page, 'light')
    // 先确认"接口打空对它无效"这句是实测而不是说法
    const target = typeof anchor.path === 'string' ? anchor.path : '/admin/system'
    await stubApiToEmpty(page, 'admin-system-form')
    await freezeClock(page)
    await page.goto(target)
    await settlePage(page)
    let onSelf = ''
    try {
      await assertAnchorArrival(page, 'admin-system-form')
    } catch (e) {
      onSelf = (e as Error).message
    }
    // 这一半是本用例的主张，必须断言而不是打印：落到 /login 的话 onSelf 也非空，
    // 所以先由 assertGenericGuards 确认真到了这一页，再让 onSelf 绿。
    await assertGenericGuards(page, anchor, 'light')
    expect(onSelf, '接口打空后 admin-system-form 的断言变红了——"本页画面不随接口变"这句已失效，需重测').toBe('')
    console.log(`[falsify] admin-system-form 接口打空后自己的断言=绿（= 本页画面不随接口变，见本用例说明）`)

    const anon = await page.context().browser()!.newContext({ baseURL: process.env.PW_BASE_URL ?? 'http://localhost:5175' })
    const p2 = await anon.newPage()
    await p2.goto('/login')
    await p2.waitForLoadState('networkidle')
    let threw = ''
    try {
      await assertAnchorArrival(p2, 'admin-system-form')
    } catch (e) {
      threw = (e as Error).message
    }
    await anon.close()
    expect(threw, '在登录页上跑系统设置的断言却绿了——那条断言不认页面').not.toBe('')
    console.log(`[falsify] admin-system-form 拿错页跑=红 ✓（${threw.split('\n')[0]}）`)
  })
})

/**
 * ANON 两个锚点（login / error-404）没有数据接口，桩打不出空态，
 * 它们的专属断言钉的是**页面身份**。身份类断言的可证伪方式是"拿错页跑"：
 * 在 /login 上跑 404 的断言、在 404 上跑 login 的断言，两边都必须红——
 * 红了才说明这两条不是 `#app 非空` 那种"任何页面都绿"的假守卫。
 */
test('ANON 锚点的数据到位断言是页面专属的（拿错页跑必须红）', async ({ page }) => {
  await setTheme(page, 'light')
  await page.goto('/login')
  await page.waitForLoadState('networkidle')
  let onLogin = ''
  try {
    await assertAnchorArrival(page, 'error-404')
  } catch (e) {
    onLogin = (e as Error).message
  }
  expect(onLogin, '在 /login 上跑 404 的断言却绿了——那条断言根本没看页面身份').not.toBe('')

  await page.goto('/__no-such-route__')
  await page.waitForLoadState('networkidle')
  let on404 = ''
  try {
    await assertAnchorArrival(page, 'login')
  } catch (e) {
    on404 = (e as Error).message
  }
  expect(on404, '在 404 页上跑登录表单的断言却绿了——同上').not.toBe('')
  // 顺带钉住"每条锚点都有专属断言"这条覆盖率：漏一条就是给新锚点留空档
  const missing = ANCHORS.filter((a) => !DATA_ARRIVAL_CHECKS[a.slug]).map((a) => a.slug)
  expect(missing, '有锚点没有专属数据到位断言').toEqual([])
})
