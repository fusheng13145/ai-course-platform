import { expect, test } from '@playwright/test'
import type { Page, TestInfo } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import {
  ANCHORS,
  apiStubTakesPathname,
  assertComparableRender,
  assertGenericGuards,
  assertAnchorArrival,
  assertPinCoverage,
  assertPinnedPayloadContent,
  attachPayloadRecorder,
  DATA_ARRIVAL_CHECKS,
  DATA_PATHS,
  evidenceTagSuffix,
  freezeClock,
  installPayloadPin,
  loadPinManifest,
  maskOf,
  pinRandom,
  readNopinEnv,
  settlePage,
  setTheme,
  snapName,
  SNAPSHOT_THRESHOLDS,
  storagePathFor,
  type AnchorSpec,
  type PinState,
  type ThemeName,
} from './visual-helpers'

/** 课程列表页自身的 URL——__FROM_COURSE_LIST__ 先到这里取 id，再进壳页 */
const COURSE_LIST_PATH = '/student/courses'

/* -------------------------------------------------------------------------- *
 * A3：`PW_VISUAL_NOPIN` **只有探针认**，anchors 认它就等于给自己发一张假绿
 * -------------------------------------------------------------------------- *
 * 【原先的行为】这个开关只在 `sensitivity.spec.ts`（`const nopin = process.env.PW_VISUAL_NOPIN === '1'`）
 * 被读；本套件从头到尾没读过它，于是照着台账/报告里的 NOPIN 命令把它拼到 anchors 上时，
 * Playwright **一声不响地接受**这个环境变量、照常装钉桩，跑出来的还是那 4 张"与基线 md5 相同"的图。
 * 【为什么"静默接受"比"报错"严重得多】拿 NOPIN 去跑 anchors 的人，本意是做**反向对照**
 * （"关掉钉死，看漂移是不是真的还在"）。装模作样地忽略它，他就得到
 * "连真后端都不漂"——**恰好是他本来想做的那件事的反面结论**，而且四条 md5 证据看起来完全支持它。
 * 【为什么不改成"让 anchors 也认它"】那更危险，不是更安全：
 *   `installPayloadPin` 不装 ⇒ `pinState` 为 null ⇒ `assertZeroDiff` 整条被跳过（它就挂在 `if (pinState)` 里），
 *   于是这套件唯一那条"钉住后必须仍与基线逐字节相同"的硬判据消失；剩下的只有 `toHaveScreenshot`
 *   那一档 r=0.0001（免检额 130 px）。实测（A2 那轮 NOPIN 对照）：
 *     student-dashboard 计数 321 → 红；
 *     admin-governance-analytics 计数 112 → **绿**（112 < 130，而字节差是实打实的 222）。
 *   也就是说"认它"的结果是**一半随机红、一半假绿**，正是本仓要避免的那一类读数。
 * 【现在的处置】在导入期就抛：一条测试都不会跑，也不可能被读成"跑过了"。
 *   消息里给出真正该跑的那条命令（探针侧，工作目录 `frontend/`，需要后端 8080 + dev server 5175）。
 * 【取法在 4c-fix-C 第 11 项统一】"读到什么算开、什么算关"不再是本文件自己的一套：
 *   `readNopinEnv()`（`visual-helpers.ts`，与 `pinShiftDays()` 同款"env 解析归 helper"的先例）定义唯一口径——
 *   未设置 / 空串 / `0` ⇒ 关；`1` ⇒ 开；**其余值当场抛**（fail-fast）。
 *   旧版这里曾写"0 与空串放行、其余非空都拒"，语义上与新口径一致（拒 = 抛），差别只在那道抛由
 *   共享函数发出、探针侧从此也走同一道门——`PW_VISUAL_NOPIN=true` 在探针侧被静默忽略的那种分裂结束了。
 */
const NOPIN_ENV = 'PW_VISUAL_NOPIN'
if (readNopinEnv()) {
  throw new Error(
    `[口径] anchors.spec.ts 拒绝带 ${NOPIN_ENV}=1 运行：这个开关**只有量程探针认**，本套件从来只走钉住那条路。\n` +
      `  照它跑 anchors 不会给你反向对照——要么钉桩照装而你以为关掉了，要么 assertZeroDiff 整个消失。\n` +
      `  反向对照（真走后端、量漂移本身）跑这条：\n` +
      `    cd frontend && PW_LADDER=noise PW_PROBE_THEME=light PW_VISUAL_NOPIN=1 \\\n` +
      `      PW_PROBE_SLUGS=student-dashboard,admin-governance-analytics PW_PORT=5175 \\\n` +
      `      npx playwright test -c playwright.visual.ts sensitivity\n` +
      `  读数落 e2e/visual/tools/evidence/probe-results.json 与 *-probe-captured@nopin.png（A2 之后两轮不再互相覆盖）。\n` +
      `  要模拟"服务端第 +k 天"改把 PW_PIN_SHIFT_DAYS=k 加到**上面这条**；把它加到 anchors 上是另一件事\n` +
      `  （那是在检验"钉歪一点整表就红"，见 Task 4c 报告的 RED-5，那条**是**认这个开关的）。`,
  )
}

/** 列表页的数据源：/student/courses 的 .tree-row 就是它 courses[0..] 的渲染结果 */
const COURSE_LIST_API = '/api/student/dashboard'

/**
 * 离线取证图与比较器读数的落点（.gitignore 里 `e2e/visual/tools/evidence/` 整目录已排除，
 * 所以文件名加什么标签都不会变成"未追踪但也没被忽略"的东西）。
 * 这里每张图的名字都带**数据路径标签**（`@nopin` / `@shift+k`，交付口径不带）——
 * 见 visual-helpers.ts 的 `evidenceTagSuffix()` 与下面 assertZeroDiff 的注释（A2）。
 */
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
  console.log(
    `[graph] painted=${painted}px（门槛 ${GRAPH_MIN_PAINTED_PX}）——重生成：本行每次跑都打`,
  )
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
async function runAnchor(
  page: Page,
  anchor: AnchorSpec,
  theme: ThemeName,
  testInfo: TestInfo,
): Promise<void> {
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
  // （`src/composables/useTheme.ts` 里 `applyTheme()` 那行 `document.documentElement.setAttribute('data-theme', …)`，
  //  定位：`grep -n "setAttribute('data-theme'" src/composables/useTheme.ts`），
  // 而 applyTheme 只在调用过 useTheme() 的组件里跑（ThemeSwitcher / Profile），
  // 错误页既无 AppShell 也无 ThemeSwitcher → <html> 上永远没有 data-theme，
  // theme.css 的 [data-theme='dark'] 整块不生效，暗色用户看到的仍是 :root 的浅色 token。
  // 后果：这两张基线的明与暗必然逐像素相同，正是本断言要拦的事，所以按 Task 4 Step 3 的口径
  // 记为真实缺陷（缺陷 D-1，另开修页任务），不入库。修好后这 2 张自动补生成，无需改本文件。
  await assertComparableRender(page, anchor, theme)
  // 图谱代表必须真的"有图"——上一轮它截的是占位空态，四条通用断言全绿（见 assertGraphPainted）
  if (anchor.requirePaintedGraph) await assertGraphPainted(page)

  // ───────── A4：录制模式必须**先落 fixture**，而且不再跑那条像素断言 ─────────
  // 【原先为什么这条路走不通】`flushRecord()` 写在 `toHaveScreenshot` **之后**。
  // 录制模式不装钉桩（`recording` 分支只 `attachPayloadRecorder`），所以这一页渲染的是**真后端今天的数据**；
  // 而 `toHaveScreenshot` 用的是交付那一档 r=0.0001（1440×900 ⇒ 免检额 129.6 ≈ 130 px），
  // `student-dashboard` 一天的漂移正好是 **321 px** ⇒ 像素断言先炸，函数直接抛出，
  // **`flushRecord()` 永远执行不到、fixture 永远写不出来**（实测见 Task 4c-fix-A 报告 A4：
  // 两次 `321 pixels (ratio 0.01 of all image pixels) are different.`、`__fixtures__/student-dashboard.json`
  // 一个字节没动）。而 `admin-governance-analytics` 的漂移只有 112 px < 130 ⇒ 它**静默写成**。
  // 合起来就是：两条错误消息把下一个代理支去跑的这条重录命令，**对那个真正需要重录的锚点是走不通的**，
  // 对不需要重录的那个反而"成功"。
  // 【为什么仍然先跑 assertComparableRender】录制端有条硬规矩："别把空态录成 fixture"
  // （`attachPayloadRecorder` 的注释，`visual-helpers.ts`），它靠的就是**落盘之前**先过一遍
  // `assertAnchorArrival`——而 `assertComparableRender` 里就含这一条（见该函数体）。
  // 所以顺序必须是：守卫（含 arrival）→ flush → 像素断言（录制模式下跳过），不能简单提前到 goto 之前。
  // 【4c-fix-C 第 10 项：日志与跳过都必须过"flushRecord 真的执行过"这一道门】
  // 原先 A4 把 flush 提前之后，跳过与"已写成"日志仍然只按 `recording` 判——**全部 12 个锚点**都进
  // 那个分支，而其中 10 个非钉桩锚点 flushRecord 为 null（`__fixtures__/` 一个字节都没写），
  // 于是全量录制跑会打出十个"fixture 已写成"，并**顺手跳过 22 张里非钉桩那些的像素比对**——
  // 只能靠"文档里的录制命令总带 `-g`"侥幸避开。从本轮起：`已写成` 三个字只出现在真的写完盘
  // 的那条分支里；录制模式下非钉桩锚点走正常像素比对（那是真读数：这一页画的是真后端今天的画面，
  // 漂不漂都照实报，不再被静默豁免）。
  if (flushRecord) {
    await flushRecord()
    console.log(
      `[record] ${anchor.slug}/${theme}：fixture 已写成（flushRecord 真的执行完了落盘；这一句现在只对钉桩锚点成立）\n` +
        `         ⇒ 本轮**不**做像素比对（录制模式量的是载荷不是画面）。\n` +
        `         下一步用钉住那轮验它能不能复现基线：PW_PORT=5175 npx playwright test -c playwright.visual.ts anchors -g "${anchor.slug}"`,
    )
    return
  }

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
    // R-CF1（4c-fix-C 第 9 项）：存在性断言 ≠ 内容断言。被钉的载荷自己必须非平凡
    // （data 内序列长度 > 0 且至少一个数值字段非 0）；全 0 且未登记的直接红，
    // 已登记的（今天这两个锚点，账号确实没分析数据）放行但**每次打印结论降级**——
    // "被钉区域不得作为数据到位的证据"从台账措辞走进了运行时日志。见 visual-helpers 的
    // assertPinnedPayloadContent / PIN_PAYLOAD_TRIVIAL_EXCUSED 与 LEDGER `P0a-GUARD-02`。
    assertPinnedPayloadContent(anchor, loadPinManifest(anchor.slug))
    // 日期锚点的"归零"判据（brief Step 1 的判定式：钉载荷后计数必须归 0，不是"变小"）。
    // toHaveScreenshot 的"绿"根本不证明零漂移：交付档 r=0.0001 只有 130 px 免检额，但**判据是分类器计数**，
    //   字节差 618 / 计数 321 那种漂移在 0.0005 下被整体吸收、在 0.0001 下也只按 321 这个数变红，
    //   而"618 个像素真的变了"这件事从来不在 ratio 的账里。
    // 这里用同一个 mask、同一组截图参数另截一张，交给已入库的比较器按 t=0/r=0 读死数，并**要求 md5 相等**。
    await assertZeroDiff(page, anchor, theme, testInfo, pinState)
    console.log(
      `[pin] ${anchor.slug}/${theme} 钉掉 ${pinState.served.length} 次请求：${JSON.stringify(pinState.served)}`,
    )
    if (pinState.unpinnedTodayBearing.length > 0) {
      console.log(
        `[pin-audit] ${anchor.slug} 仍在消费带"今天"日期的未钉端点 ${pinState.unpinnedTodayBearing.length} 个：${JSON.stringify([...new Set(pinState.unpinnedTodayBearing)])}`,
      )
    }
  }
  // A4：`flushRecord()` 已经挪到像素断言**之前**（录制模式在那儿就 return 了），这里不再出现。
}

/**
 * 钉死之后才算得出的那条：**与已提交基线 md5 相等且字节差 0**（spec §6.16 的措辞规矩：md5 相等才许说"逐字节相同"）。
 * 读数口径 t=0 / r=0——交付那一档的 ratio（当前 0.0001 ⇒ 130 px）会吸收 `count ≤ 免检额` 的一切，
 * 用它验"归零"等于没验。只对本套件里声明了 pinPaths 的锚点跑（现在 = 4 张日期图）。
 *
 * 【取证文件名带口径标签（A2）】原先这张图叫 `evidence/<slug>-<theme>.png`，
 * **与已提交基线逐字同名**、又不带任何标签，于是两件事叠在一起：
 *   ① 读 `--table` 的人在同一个目录里看到 `student-dashboard-light.png 计数 0 md5 相同`，
 *      分不清那是"基线自比"还是"钉住后重截的那张"；
 *   ② `PW_PIN_SHIFT_DAYS=1 … anchors` 那一轮（RED-5 用来证"钉歪一点就整表红"的那次跑）
 *      会把这张钉住读数的图**原地覆盖成漂移图**，下一个读 `--table` 的人拿到的是被换过的证据。
 * 现在改名 `-zerodiff-captured` 并接上 `evidenceTagSuffix()`（与探针的 JSON key / PNG 名同一个函数）。
 * 交付口径 = 空标签 ⇒ 默认那轮仍是 `<slug>-<theme>-zerodiff-captured.png`，只有 `PW_PIN_SHIFT_DAYS>0` 时才出现 `@shift+k`。
 * t/r **不进这里的标签**：这张图永远按 `--threshold=0 --ratio=0` 读（见 measureAgainstBaseline，
 * 那是这条判据本身的定义而不是一个会变动的口径），标上 `@r=0` 只会让每一张都带标签、把"标签=有例外"弄失效。
 */
async function assertZeroDiff(
  page: Page,
  anchor: AnchorSpec,
  theme: ThemeName,
  testInfo: TestInfo,
  pinState: PinState,
): Promise<void> {
  const arg = `${snapName(anchor.slug, theme)}.png`
  const baseline = testInfo.snapshotPath(arg, { kind: 'screenshot' })
  mkdirSync(EVIDENCE_DIR, { recursive: true })
  const scaleTag = evidenceTagSuffix({ shift: pinState.shiftDays })
  const capture = join(
    EVIDENCE_DIR,
    `${snapName(anchor.slug, theme)}-zerodiff-captured${scaleTag}.png`,
  )
  // 与 toHaveScreenshot 完全同一组参数（含 mask）——Task 4c Step 1e：两条路径必须共用 mask，
  // 否则离线自检台与跑测器的读数会静默分叉，而喂给台账的是前者。
  writeFileSync(
    capture,
    await page.screenshot({
      fullPage: true,
      animations: 'disabled',
      caret: 'hide',
      scale: 'css',
      mask: maskOf(page, anchor.maskSelectors),
    }),
  )
  const r = measureAgainstBaseline(baseline, capture)
  if (r.md5.equal && r.byteDiffPx === 0) return
  throw new Error(
    `[零漂移] ${anchor.slug}/${theme} 钉了载荷仍与基线不符：${describeReading(r)}\n` +
      `两条可能，按这个顺序排（A4 把第一条的次序弄对了——录制模式现在**先落 fixture 再**跳像素断言，\n` +
      `  所以这条命令真的能写出 fixture；原先它会在 toHaveScreenshot 上先炸掉，flushRecord 永远跑不到）：\n` +
      `  ① fixture 与基线那一天脱节 ⇒ 重录：cd frontend && PW_PORT=5175 PW_RECORD_PAYLOADS=1 \\\n` +
      `       npx playwright test -c playwright.visual.ts anchors -g "${anchor.slug}"\n` +
      `     那一轮**不比图**（录制的是载荷），看到 [pin-record] 写过盘之后，必须再跑一次**不带** PW_RECORD_PAYLOADS 的同一条命令，\n` +
      `     由本条判据说"新 fixture 能不能复现基线"。重录会改动入库的 __fixtures__/${anchor.slug}.json，那是有意的改动、要一起提交。\n` +
      `  ② 基线日这件事本身变了 ⇒ 查 visual-helpers.ts 的 BASELINE_DAY 与这份 fixture 的 clockDate\n` +
      `     （loadPinManifest 已经在比这两个值，红在那里说明是这一条；本条红在像素上说明 ① 或真有产品改动）。\n` +
      `  都不是的话才是这一带真的有别的改动。禁止用 --update-snapshots 把它吸收掉。`,
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
 * 【B5 的两条前提**拦不住白页与 JS 崩**（4c-fix-C 的 C2 步第 13 项改口）】B5 那轮加的两条是
 *   `桩命中数 > 0` 与 `location.pathname === 该锚点路由`，而当时把标题写成"加了这两条就成立"。
 *   **这两条都不是渲染成功的证据**：一页**没挂载完就停住**时，`pathname` 不会变（路由早就解析完了），
 *   桩也照样命中过 ≥1 个请求（请求是导航初期发的）——两条前提都满足，格子照样绿。
 *   白页与 JS 崩恰恰就是这个形状。所以那句标题原先吹过了，现在的口径是：
 * 【矩阵现在覆盖 **7 格，全是"事故形状"**（外加 teacher 专例与矩阵 B）】"事故形状"＝四条通用守卫**全绿**
 *   而该页专属的"数据已到位"断言**红**（Task 4b 那次 teacher-dashboard 空态就是这个组合，逐字复现由
 *   下面那条 teacher 专例钉住，不靠矩阵暗示）。
 * 【R-C2-4 改判：原先归到本矩阵的 `student-learning-center`／`admin-profile` 两格已移出，见下】第 13 项当时把
 *   这两格留在矩阵里、并把标题写成"9 格里 7 格属事故形状、另外两格仍证伪了自己的专属断言只是触发条件更强"，
 *   题面还明写"把这两格从矩阵里删掉是错的"。**那个前提被 C2b 的 S2 实测否证了**：把桩的接管面锚回 pathname
 *   前缀后主题回来了（壳层正常挂上、`pageerror=0`），可这两格在真实接口被清空时各自的到位断言**根本不红**
 *   （`[falsify] … 专属断言=绿 ✗`，桩命中的已是 `/api/courses` 这类真接口）——那两格不是"触发条件比事故更强"，
 *   而是**空转格**：它们既没测到空载荷、也没测到专属断言。§11.4 早规定"不许留成每次跑都有已知红的常态"，
 *   也**不许悄悄从可证伪名单删掉**（那等于缩小矩阵、此后无人再测这件事）。⇒ 按 R-C2-4 把这两格从
 *   `EMPTY_API_FALSIFIABLE` 移到下面那张**只减不增的缺口表** `NOT_FALSIFIABLE_BY_ARRIVAL_ASSERTION`，
 *   并各生成一条**反向守卫**（钉"当前缺陷在场"：打空流程里专属断言不红＝通过），两个方向都有牙。
 * 【本项的交付物：每格再加两条前提】`page.on('pageerror')` 计数为 **0**，以及**壳层真的渲染了**
 *   （`.app`／`.topbar` 在场，即 `AppShell.vue` 的根与 `AppTopbar.vue` 的 header 都挂上了）。
 *   这两条排的正是 pathname 排不掉的那两种形状：未捕获错误与挂载中途停住。判据本体见
 *   `renderPreconditionFindings`（纯函数），它的两个"能红"反例各入库成一条用例（见本节末尾），
 *   用 `page.setContent` 与 `page.evaluate`/`addInitScript` 造，**不改 `src/` 一个字**。
 * 【4c-fix-C 第 9 项补的边界（R-CF1，读矩阵结论时必须带着）】本矩阵证的是守卫抓得住
 *   `data:null` 这一形态；它**抓不住**"结构完整而数值全 0"——那种载荷渲染出的 DOM 存在性一条不缺。
 *   内容层面的那道口在钉桩侧：`assertPinnedPayloadContent`（visual-helpers 的 R-CF1 注释块，
 *   LEDGER `P0a-GUARD-02`）。别把"矩阵 9 格全绿"读成"到位断言是内容断言"。
 * 【极性】这里 expect(arrivalThrew).toBe(true) 的绿 = "守卫确实可红"，与量程探针那种
 * "FAILED=检出了"的反极性不是一回事——那条要报的是测量结论，本条要报的是断言的有效性。
 * 【为什么不装 payload pin】本组用例要的就是"真后端给空响应"，钉死会把空态一起钉没了。
 * 【截图】一律不做 toHaveScreenshot：本组只验断言，不产基线，也就没有"顺手吸收缺陷"的口子。
 */

/** 需要保住的会话/外壳端点：把它们也打空会得到 /login 重定向，红得无法归因（那就不是本用例要证的）
 *
 * 【B5：返回值是这条用例的**前提**，不是装饰】原先它返回 `void`，于是第 2 步那 9 格
 * 只有"arrival 抛了没抛"这一条断言，而**没有任何断言说"桩真的命中过、页面真的还在这条路由上"**。
 * 两端都会被误信：
 *   ① 会话被踢 / 白页 / JS 崩 ⇒ `assertAnchorArrival` **照样抛** ⇒ `threw` 非空 ⇒ **格子绿**，
 *      读起来是"这条专属断言真会因空响应而红"，实际测的是"这一页没登录"。这是主洞。
 *   ② 桩没命中（slug 打错、端点改名、这一页不发 GET）⇒ 页面是**正常有数据**的画面 ⇒
 *      `threw` 为空 ⇒ **格子红**，读起来是"这条守卫是空的（spec §6.14）"，实际是"实验没做成"。方向完全相反。
 * 现在 ① 由"pathname 仍是该锚点的路由"拦住、② 由"命中请求数 > 0"拦住，两条都在 arrival 断言**之前**跑，
 * 所以前提不成立时报的是前提，不再让矩阵去猜。通用守卫状态仍**只报不判**（那是另一件事，见下面矩阵的 ②）。
 * 【`served` 到底是什么清单（S1 的语义澄清，R-C2-1）】它记的是"**被本桩接管的路径**"，
 *   不是"这一页发过的全部请求"。改前那半句差距很大：接管面按"URL 里含 /api/ 这一段"命中，
 *   于是 dev server 的源码模块请求也进了清单，`served.length > 0` 这条前提（矩阵 ⓪，第 13 项的 B5）
 *   能被**一个模块请求**满足——看着满足、实际那一页一个后端数据接口都没发出去，属"假命中"。
 *   现口径：通配串已锚到 pathname 前缀（判据本体 `apiStubTakesPathname`，在 visual-helpers.ts），
 *   进得来这份清单的按构造都是 `/api/` 开头的**数据请求** ⇒ ⓪ 重新等于"这一页的接口被打空了"。
 *   清单里若再出现非 `/api/` 开头的一条，就是接管面又漂了，由 helpers.spec.ts 那条
 *   「匹配面锚定在 pathname 前缀」的守卫当场红（它喂候选 URL 清单，模块后缀那两类必须被排除）。
 * 【`dataShape` 这个参数是第 14 项 ② 要的】题面要给同两格把桩从 `data: null` 换成 `data: []`，
 *   看 `data-theme` 回不回得来——回得来才立得住"壳层／页面组件不容忍 null 载荷"这个名字。
 *   缺省 `'null'` 保持上面矩阵那 9 格的现有形状不变（那是第 13 项的交付面），只有取证用例显式传 `'emptyArray'`。
 *   **加了这个参数不等于本仓支持任意空值形态**：桩体只有这两种，第三种要另写。
 */
async function stubApiToEmpty(
  page: Page,
  slug: string,
  dataShape: 'null' | 'emptyArray' = 'null',
): Promise<{ served: string[] }> {
  const prefixes = DATA_PATHS[slug]
  if (!prefixes)
    throw new Error(`锚点 ${slug} 没有 DATA_PATHS 条目——空态可证伪矩阵无法收窄，先补名单`)
  const served: string[] = []
  // 【S1／R-C2-1：接管面锚到 pathname 前缀】第一个参数换成锚定正则——它要求 `/api/` 紧跟在 host 之后，
  // 所以 dev server 的源码模块请求（`/src/api/auth.ts`）与预打包产物（`/node_modules/.vite/deps/api.js`）
  // 根本进不来这个回调，也就再不会被 fulfill 成一段 JSON。
  // 回调里再走 `apiStubTakesPathname`（外壳保活与 DATA_PATHS 前缀收窄都在它里面，判据只有一份）。
  await page.route(/^https?:\/\/[^/]+\/api\//, async (route) => {
    const req = route.request()
    if (req.method() !== 'GET') return route.fallback()
    const u = new URL(req.url())
    if (!apiStubTakesPathname(u.pathname, prefixes)) return route.fallback()
    served.push(u.pathname)
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      // 空响应而不是 5xx：5xx 会被错误边界接住（那条守卫本来就能拦），
      // 而"接口 200、data 空"才是那次事故的样子——四条通用守卫对它全盲。
      // 第 14 项 ② 要的就是把"空"换成 `[]` 这一态，所以这里由 `dataShape` 决定，不再写死。
      body: JSON.stringify({
        code: 200,
        message: 'ok',
        data: dataShape === 'emptyArray' ? [] : null,
      }),
    })
  })
  return { served }
}

/**
 * 接口打空后**画面确实变空**、且该页专属"数据已到位"断言**真会因此变红**的那 7 个锚点（逐个实测出来的：
 * 桩把这一页的 `/api/` GET 打空后，`assertAnchorArrival` 抛）。admin-system-form 不在这里面——见下面矩阵 B 的
 * 说明，它这一页渲染的是组件内默认值，把 /api/system-configs 打空后 DOM 一字不变（实测 app 节点数 1909→1909、
 * filled 26→26），所以它的专属断言只能用"拿错页跑"来证伪。
 * 【R-C2-4：`student-learning-center`／`admin-profile` 两格已从本名单移出（不是删除）】它们在真实接口被清空时
 *   到位断言根本不红（空转），已移到下面的 `NOT_FALSIFIABLE_BY_ARRIVAL_ASSERTION` 并各有一条反向守卫钉住缺陷在场。
 *   移出本名单是为了消掉两条天天红的矩阵格（§11.4"不许留已知红常态"），**覆盖面没缩**——那两格由缺口表继续测。
 * 【这里 7 个名字一个都不许随便删】删一条＝这一格不再被"接口打空、专属断言必须红"测到；要移出必须先证明它
 *   变成了空转（专属断言不红），并按 R-C2-4 移进缺口表、同批配反向守卫，不能只从这张表删掉了事。
 */
const EMPTY_API_FALSIFIABLE = [
  'admin-dashboard',
  'student-dashboard',
  'teacher-dashboard',
  'admin-courses-table',
  'student-courses-modal',
  'student-knowledge-graph',
  'admin-governance-analytics',
]

/**
 * **只减不增的空转缺口表（R-C2-4）**：这一页在桩把它的 `/api/` GET 打空后，自身的"数据已到位"断言**不红**——
 * 即 §6.14 要的"专属断言必须能因空响应而红"这条准入门在这两个锚点上**一直没有证据**。
 * 参照先例是 `coverage-lib.ts` 的 `KNOWN_UNWIRED_EL_DRAWER_FILES`（只减不增、双向都红的缺口表）与
 * `motion-observation.spec.ts` 里把"没有动效"钉成 `toBe(0)` 的反向守卫：这里把"到位断言抓不到空载荷"
 * 这个坏样子钉成一条**当前能通过**的断言，它一红就说明缺陷被修好了 ⇒ 把锚点搬回可证伪名单、同批删本表条目。
 * 【值写的是**当前实测依据**（不是散文，改任何数字都要同步下面两条用例与 LEDGER）】
 * 【措辞纪律（R-C2-5）】下面每条只说"**桩给出的 `data:null` 形态下**"的行为——真后端"没有数据"的实测形态是
 *   **信封里根本没有 `data` 键**（台账 `P0a-GUARD-07` 的 S3），桩发的 `data:null` 与 `data:[]` 都不是那两页
 *   实测过的形状；所以这里绝不写"后端返回空时／页面在空数据下的行为"。
 */
const NOT_FALSIFIABLE_BY_ARRIVAL_ASSERTION: Record<string, string> = {
  'student-learning-center':
    'C2b S2 实测（桩给 data:null 形态下）：[falsify] 专属断言=绿 ✗ · 桩命中真接口 /api/student/courses、/api/student/courses/1/learning-center、/api/courses/1 · 通用守卫绿（壳层正常挂上、pageerror=0）· 到位断言查 .shell-tab 不小于 1 与非空标题，二者都由壳层与路由名供给 ⇒ 空转',
  'admin-profile':
    'C2b S2 实测（桩给 data:null 形态下）：[falsify] 专属断言=绿 ✗ · 桩命中真接口 /api/courses · 通用守卫绿（壳层正常挂上、pageerror=0）· 到位断言查 filled 输入框不小于 1 与 hero 统计非空非零，回填来自未被接管的外壳类端点（/api/auth/ 在保活名单里）⇒ 空转',
}

/**
 * 第 13 项两条新前提的**读数**（也是第 14 项 ① 的取证读数——工单明写"这条与第 13 项的两条前提是
 * 同一套读数，别做两遍"，所以这里一次取齐、两处共用）：
 *  `theme`＝`data-theme` 的**实际值**（空串就照实记空串，不解释）；
 *  `appCount`／`topbarCount`＝壳层两块（`AppShell.vue` 根 `.app`、`AppTopbar.vue` 的 `header.topbar`）在场数；
 *  `appChildCount`＝`#app > *` 的**直接子节点数**（"页面到底挂上去东西没有"的读数，白页时它是 0）。
 */
interface RenderProbe {
  theme: string
  appCount: number
  appChildCount: number
  topbarCount: number
}

async function probeRenderShape(page: Page): Promise<RenderProbe> {
  return await page.evaluate(() => ({
    theme: document.documentElement.getAttribute('data-theme') ?? '',
    appCount: document.querySelectorAll('.app').length,
    appChildCount: document.querySelectorAll('#app > *').length,
    topbarCount: document.querySelectorAll('.topbar').length,
  }))
}

/**
 * 第 13 项两条新前提的**判据本体**（纯函数 ⇒ "它会不会红"不必靠一次真事故来证明，
 * 下面两条反例用例直接喂合成页）。返回违规清单，**空＝两条前提都成立**。
 * 【为什么这两条排在 arrival 断言之前】它们和 B5 那两条同一个地位：前提不成立时红话必须说"实验没做成"，
 * 而不是让矩阵去猜"这条专属断言真会因空响应而红"。
 * 【为什么不复用通用守卫的 `#app 非空` 那一条】那条查的是 `#app` 的 **textContent** 非空——
 * 一个"挂载中途停住但已经把壳子的文字画出来"的页面它照样绿；而本条查的是**壳层自己的两个节点在不在场**，
 * 加上 `pageerror` 计数（未捕获错误的直接读数），这两样才是 pathname 拦不住的那两种形状。
 */
function renderPreconditionFindings(probe: RenderProbe, pageErrors: readonly string[]): string[] {
  const out: string[] = []
  if (pageErrors.length > 0) {
    out.push(
      `pageerror 计数 ${pageErrors.length} 不为 0——这一格跑在"JS 崩"的画面上，` +
        `而崩在哪与空响应无关：${pageErrors.join(' ｜ ')}`,
    )
  }
  if (probe.appCount === 0) out.push('.app 不在场（AppShell 的根没挂上＝挂载中途停住或白页）')
  if (probe.topbarCount === 0)
    out.push('.topbar 不在场（AppTopbar 的 header 没挂上＝壳层只有一半）')
  return out
}

/**
 * 第 13 项两条新前提**实测不成立**那一族的登记表（`DATA_PATHS` 里 `'ALL'` 锚点用）。
 * 【本轮状态：表已**清空**，判据本体一字未动】这张表当初是为 `student-learning-center`／`admin-profile`
 *   两格建的——当时它们的前提（壳层挂没挂、有没有未捕获错误）实测不成立，所以登记成"结论降级、不算事故形状"。
 *   C2b 的 S1 把桩的接管面锚回 pathname 前缀后重跑这两格：**前提现在成立了**（壳层正常挂上、`pageerror=0`），
 *   而它自己那条双向判据的红话要求"前提成立 ⇒ 触发条件变了 ⇒ 把注册条目删掉并同步 LEDGER 与 spec §11.4"。
 *   ⇒ 按那句红话的要求**删掉这两条登记**（不是放宽判据让它绿——判据与 `registered` 分支原样留着，
 *   将来任何一格前提塌了或变成成立，它照样在矩阵里当场响）。
 * 【改前那五个读数（`data-theme=""`、`#app>*=0`、`.app=0`、`.topbar` 不在场、`pageerror=0`）已转录到
 *   LEDGER `P0a-GUARD-07`——这里不重复抄，指针即来源】这两格改后的新形态（到位断言在桩给 `data:null`
 *   形态下根本不红＝空转）由上面的 `NOT_FALSIFIABLE_BY_ARRIVAL_ASSERTION` 缺口表与它的反向守卫继续测，
 *   覆盖面没有因为清空本表而缩小。
 * 【为什么判据本体与下面矩阵里的 `registered` 分支都留着】清空表不等于拆表——双向判据是这族"登记会退化成
 *   旧注释"事故的防线（本仓写过三次教训），留着它对空表是惰性的、对未来新格是有牙的。
 */
const PREMISE_NOT_MET_STRONGER_THAN_ACCIDENT: Record<string, string> = {}

/**
 * R-C2-4 缺口表的**结构一致性**判据（纯静态、不起浏览器）——把"只减不增、双向都有牙"钉成断言，
 * 参照 `KNOWN_UNWIRED_EL_DRAWER_FILES` 的 `novel`／`stale` 形状：
 *  · 表里的每个锚点必须是真锚点、有 `DATA_PATHS`、带会话（不是 ANON），且**不在** `EMPTY_API_FALSIFIABLE` 里；
 *  · 反过来任何一格若被搬回可证伪名单而**表里条目没删** ⇒ 两表相交非空 ⇒ 当场红；
 *  · 两表并起来必须正好是第 13 项那 9 格（谁从任一张表悄悄删掉一条 ⇒ 红），把"缺口表只减不增"落到可失败的门上。
 * 【为什么单独一条而不是塞进反向守卫】反向守卫那两条是浏览器用例（要真打空流程），跑得起但慢、且只在各自锚点
 *   上判"这一格空转还在不在"；这条是纯集合关系断言，专抓"两张表被分别编辑到自相矛盾"这种结构性漂移。
 */
test('空态缺口表与可证伪矩阵互斥且并集仍是那 9 格（R-C2-4 只减不增、双向有牙）', () => {
  const gapSlugs = Object.keys(NOT_FALSIFIABLE_BY_ARRIVAL_ASSERTION)
  // ① 缺口表条目必须是真锚点、有打桩面、带会话、且已移出可证伪矩阵
  for (const slug of gapSlugs) {
    const anchor = ANCHORS.find((a) => a.slug === slug)
    expect(
      anchor,
      `${slug} 登记在缺口表里却不是 ANCHORS 中的锚点（拼错名或锚点被删，先修表）`,
    ).toBeTruthy()
    expect(
      DATA_PATHS[slug],
      `${slug} 缺口表条目没有 DATA_PATHS ⇒ 反向守卫根本打不出空态，这条登记是空的`,
    ).toBeTruthy()
    expect(
      anchor!.role,
      `${slug} 是 ANON 锚点——缺口表的反向守卫按 role 取会话夹具，ANON 进不来`,
    ).not.toBe('ANON')
    expect(
      EMPTY_API_FALSIFIABLE,
      `${slug} 同时在缺口表与 EMPTY_API_FALSIFIABLE 里——要么搬回可证伪名单时就删掉本表条目，` +
        '要么它确实还空转就不该留在矩阵里天天红。两表不许相交（R-C2-4）',
    ).not.toContain(slug)
  }
  // ② 矩阵里的任何一格都不许出现在缺口表（反向：若某锚点被搬回可证伪名单而表里没删 ⇒ 这里红）
  const overlap = EMPTY_API_FALSIFIABLE.filter((s) => gapSlugs.includes(s))
  expect(
    overlap,
    `两表相交于 ${JSON.stringify(overlap)}——按 R-C2-4，一格从缺口表搬回可证伪名单时必须**同批**删掉缺口表条目，` +
      '留着的条目会变成"永远绿在一条早已不成立的登记上"的通行证',
  ).toEqual([])
  // ③ 两表并集必须正好等于第 13 项那 9 格（谁从任一张表悄悄删掉一条 ⇒ 覆盖面缩小，红）
  const union = [...EMPTY_API_FALSIFIABLE, ...gapSlugs].sort()
  expect(
    union,
    '可证伪矩阵 ＋ 空转缺口表 的并集不再是第 13 项那 9 格——矩阵只许"移格到缺口表"，不许把格删出覆盖面；' +
      '这一格若真的不再需要空态守卫（锚点删除），要连 DATA_ARRIVAL_CHECKS 与本判据一起重定',
  ).toEqual(
    [
      'admin-courses-table',
      'admin-dashboard',
      'admin-governance-analytics',
      'admin-profile',
      'student-courses-modal',
      'student-dashboard',
      'student-knowledge-graph',
      'student-learning-center',
      'teacher-dashboard',
    ].sort(),
  )
})

for (const role of ['STUDENT', 'TEACHER', 'ADMIN'] as const) {
  const subset = ANCHORS.filter((a) => a.role === role && EMPTY_API_FALSIFIABLE.includes(a.slug))
  test.describe(`空态守卫可证伪 · ${role}`, () => {
    test.use({ storageState: storagePathFor(role) })
    for (const anchor of subset) {
      test(`${anchor.slug} 的空态必须被它自己的数据到位断言拦下`, async ({ page }) => {
        // 未捕获错误的监听必须**在第一次导航之前**挂上：`resolvePath` 对 __FROM_COURSE_LIST__
        // 会先走一次列表页导航，"挂载中途崩"完全可能就发生在那一段（挂在 goto 之后就漏计了）。
        const pageErrors: string[] = []
        page.on('pageerror', (err) =>
          pageErrors.push(String(err && err.message ? err.message : err)),
        )
        // 先走真实列表把 __FROM_COURSE_LIST__ 解析出来，再装空桩（桩装上后就没有课程可解析了）
        const target = await resolvePath(page, anchor)
        const wantPathname = new URL(target, 'http://localhost').pathname
        await setTheme(page, 'light')
        const stub = await stubApiToEmpty(page, anchor.slug)
        await freezeClock(page)
        await page.goto(target)
        await settlePage(page)

        // ── 先把**全部读数**取齐并打印，再做断言 ──────────────────────────────────────────
        // 原先这里"前提不成立就直接抛"，于是那两格永远打印不出通用守卫的完整理由列表
        // ——而第 14 项 ① 要的正是那份完整列表。所以顺序改成：测量 → 打印 → 断言。
        const pathNow = await page.evaluate(() => location.pathname)
        const probe = await probeRenderShape(page)
        const preconditions = renderPreconditionFindings(probe, pageErrors)
        let threw = ''
        try {
          await assertAnchorArrival(page, anchor.slug)
        } catch (e) {
          threw = (e as Error).message
        }
        // 【C2 步第 14 项 ①：打的是**完整**理由列表】原先 `(e.message.split('\n'))[0]` 按第一行截断，
        // 而 `assertGenericGuards` 内部**先查 data-theme、后查 #app 非空** ⇒ 截断后那份读数
        // 分不开"挂载中途崩"与"渲染成空态"。现在逐条打，并把第 13 项那套读数（pageerror 数／
        // `#app > *` 数／`.topbar` 在场）打在同一处——工单明写这两处是同一套读数，只做一遍。
        let genericReasons: string[] = []
        try {
          await assertGenericGuards(page, anchor, 'light')
        } catch (e) {
          genericReasons = (e as Error).message
            .split('\n')
            .map((l) => l.trim())
            .filter((l) => l !== '')
        }
        // ⚠️ 这几行 `[falsify]`／`[取证 14①]` 是**台账与 spec §6.14 措辞范围的数据来源**，
        //    评审无法从 stdout 之外复算，所以每轮收尾要把 9 格逐锚抄进 LEDGER。
        console.log(
          `[falsify] ${anchor.slug} 专属断言=${threw ? '红 ✓' : '绿 ✗'} · 桩命中 ${stub.served.length} 请求（端点 ${JSON.stringify([...new Set(stub.served)])}）· ` +
            `path=${pathNow} · 通用守卫=${genericReasons.length === 0 ? '绿（= 事故形状）' : `也红，完整理由 ${genericReasons.length} 条（逐条见下行）`}\n` +
            `         [取证 14①] data-theme="${probe.theme}" · #app>*=${probe.appChildCount} · .app=${probe.appCount} · ` +
            `.topbar=${probe.topbarCount > 0 ? '在场' : '不在场'} · pageerror=${pageErrors.length}` +
            (pageErrors.length > 0 ? `（${pageErrors.join(' ｜ ')}）` : '') +
            ` · 第13项两条前提=${preconditions.length === 0 ? '成立' : `不成立 ${preconditions.length} 条`}`,
        )
        for (const [i, r] of genericReasons.entries()) {
          console.log(`         [取证 14①·理由 ${i + 1}] ${r}`)
        }
        for (const [i, r] of preconditions.entries()) {
          console.log(`         [第13项·前提不成立 ${i + 1}] ${r}`)
        }

        // ── 断言部分 ──────────────────────────────────────────────────────────────────
        // ⓪ B5 那两条前提：桩真的命中过、页面还在这条路由上。
        //    【别把它们读成"渲染成功了"】pathname 与桩命中都**拦不住白页与 JS 崩**（第 13 项）。
        expect(
          stub.served.length,
          `${anchor.slug}：桩命中 0 个请求 ⇒ 这一页根本没被打空，本用例什么都没测（名单见 DATA_PATHS）`,
        ).toBeGreaterThan(0)
        expect(
          pathNow,
          `${anchor.slug}：打空后落在 ${pathNow} 而不是该锚点的路由 ${wantPathname} ⇒ 红/绿都归因不到空态`,
        ).toBe(wantPathname)

        // ⓪b 第 13 项补的**两条新前提**（本项的交付物）：`pageerror` 计数为 0、壳层真的渲染了（`.app`／`.topbar` 在场）。
        //    判据本体是纯函数 `renderPreconditionFindings`，它"能红"由本节末尾那两条反例用例入库证明
        //    （合成页 + `page.evaluate`／`addInitScript`，`src/` 一字未动）。这里按注册表**双向**判：
        const registered = PREMISE_NOT_MET_STRONGER_THAN_ACCIDENT[anchor.slug] !== undefined
        if (registered) {
          expect(
            preconditions.length,
            `${anchor.slug}：它登记在"两条前提实测不成立"那一族（${PREMISE_NOT_MET_STRONGER_THAN_ACCIDENT[anchor.slug]}），` +
              '但现在前提成立了 ⇒ 触发条件变了，这一格升回"事故形状"：把注册条目删掉并同步 LEDGER 与 spec §11.4 的读数（别只改这张表）',
          ).toBeGreaterThan(0)
          // 登记串必须逐字写着**本轮**这四个读数——读数漂了而登记没改，这条红（登记不是散文）
          expect(
            PREMISE_NOT_MET_STRONGER_THAN_ACCIDENT[anchor.slug],
            `${anchor.slug} 的登记读数与本轮实测不符：登记里必须原样写着 data-theme／#app>*／.app／.topbar／pageerror 这五个当前值` +
              `（本轮实测：data-theme="${probe.theme}" · #app>*=${probe.appChildCount} · .app=${probe.appCount} · ` +
              `.topbar=${probe.topbarCount} · pageerror=${pageErrors.length}）`,
          ).toContain(
            `data-theme="${probe.theme}" · #app>*=${probe.appChildCount} · .app=${probe.appCount} · .topbar=${probe.topbarCount} · pageerror=${pageErrors.length}`,
          )
        } else {
          expect(
            preconditions,
            `${anchor.slug}：第 13 项那两条前提不成立（命中 ${preconditions.length} 条）⇒ 这一格不再是"渲染正常而数据空"的画面，` +
              `事故形状（通用守卫绿＋专属断言红）在这页不成立。读数：data-theme="${probe.theme}" · #app>*=${probe.appChildCount} · ` +
              `.app=${probe.appCount} · .topbar=${probe.topbarCount} · pageerror=${pageErrors.length}\n  ${preconditions.join('\n  ')}\n` +
              '  两种处置（都不许把这格从矩阵删掉）：① 页面本来就该渲染出壳层 ⇒ 这是产品侧的事，按第 14 项取证走；' +
              '② 确实测量到的是"触发条件比事故更强"⇒ 把它登记进 PREMISE_NOT_MET_STRONGER_THAN_ACCIDENT 并同批改 LEDGER 的 7/9 计数',
          ).toEqual([])
        }

        // ① 专属断言必须红——这一条是矩阵的硬要求（现 7 格全是事故形状：通用守卫绿＋专属断言红）。
        //    一条从没红过的断言不是守卫。R-C2-4 后那两格空转锚点已移出本矩阵、改由下面的反向守卫钉
        //    （那里断言的是"专属断言不红＝缺陷在场"，极性相反，别在这里把它们算回来）。
        expect(
          threw,
          `${anchor.slug}：接口全空却过了"数据已到位"断言——这条守卫是空的（spec §6.14）`,
        ).not.toBe('')
      })
    }
  })
}

/**
 * 空态覆盖面的**计数本身必须是断言**（第 13 项立、R-C2-4 改口径）。
 * 【口径为什么重写，不是只把 9 改成 7】第 13 项那句"7／9 属事故形状"的**分母 9**当时含两格
 * （`student-learning-center`／`admin-profile`）——它们"仍在证伪自己的专属断言、只是触发条件比事故更强"。
 * C2b 实测否证了这个说法：这两格在真实接口被清空时到位断言**根本不红**，属**空转格**，不是"更强的事故形状"。
 * ⇒ R-C2-4 把它们从可证伪矩阵移出、放进只减不增的缺口表 `NOT_FALSIFIABLE_BY_ARRIVAL_ASSERTION`。
 * 于是口径变成 **7 格可证伪（事故形状）＋ 2 格空转（缺口表）＝ 覆盖面仍是那 9 个锚点**——
 * 分母从"证伪了自己的 9 格"改写成"数据到位断言被打空流程试过的 9 格"，两半各有独立计数、都不许漂。
 * 缺口表条目与矩阵互斥、并集＝那 9 格的结构约束由上面那条「互斥且并集」用例守；这条只管**数**。
 */
test('空态覆盖面的计数是断言不是散文（R-C2-4：7 可证伪 ＋ 2 空转 ＝ 覆盖面仍是那 9 格）', () => {
  // 可证伪矩阵＝7 格事故形状，每格仍必须是有会话、能打桩的真锚点
  expect(
    EMPTY_API_FALSIFIABLE.length,
    `可证伪矩阵格数不再是 7（现 ${EMPTY_API_FALSIFIABLE.length}）——R-C2-4 后矩阵只收"打空后专属断言会红"的格，` +
      '空转那两格在缺口表里，别把它们算回来也别从矩阵删掉别的东西',
  ).toBe(7)
  for (const slug of EMPTY_API_FALSIFIABLE) {
    expect(
      DATA_PATHS[slug],
      `${slug} 在可证伪矩阵里却没有 DATA_PATHS 条目 ⇒ 桩打不出空态`,
    ).toBeTruthy()
    const a = ANCHORS.find((x) => x.slug === slug)!
    expect(a.role, `${slug} 是 ANON 锚点——矩阵按 role 取会话，ANON 进不来`).not.toBe('ANON')
  }
  // 空转缺口表＝2 格（实测依据写在表里，条目本身的有效性由「互斥且并集」用例断言）
  expect(
    Object.keys(NOT_FALSIFIABLE_BY_ARRIVAL_ASSERTION).length,
    '空转缺口表不再是 2 格 ⇒ 某格到位断言被修好后，要把它**搬回 EMPTY_API_FALSIFIABLE** 并同批删本表条目，' +
      '不能只删不搬、也不能悄悄长大（R-C2-4 只减不增）',
  ).toBe(2)
  // 第 13 项那张前提不成立登记表已清空（改前读数转录到 LEDGER P0a-GUARD-07；判据本体留着，见上方注释）
  expect(
    Object.keys(PREMISE_NOT_MET_STRONGER_THAN_ACCIDENT).length,
    'PREMISE_NOT_MET_STRONGER_THAN_ACCIDENT 又长出条目了——R-C2-4 已按它自己红话的要求把这两格删登记，' +
      '这两格现在的形态由缺口表 + 反向守卫描述，别回到旧的"触发条件比事故更强"那一格',
  ).toBe(0)
  // teacher 专例与矩阵 B 的锚点不许混进矩阵那 7 格（它们各自另有判据）
  expect(
    EMPTY_API_FALSIFIABLE.filter((s) => s === 'teacher-dashboard').length,
    'teacher 专例被并计数了',
  ).toBe(1)
  expect(
    EMPTY_API_FALSIFIABLE.includes('admin-system-form'),
    '矩阵 B（接口打空对 admin-system-form 无效）不许进可证伪矩阵——它用的是"拿错页跑"那种证伪',
  ).toBe(false)
})

/* ----------------------------------------------------------------------------
 * R-C2-4 的**反向守卫**：把这两格"当前坏在空转"这件事钉成一条**当前能绿**的断言。
 * 【形状照 motion-observation 把"没有动效"钉成 toBe(0)】那条钉的是"今天确实没有入场动效"，
 *   它一红就说明有人加了动效 ⇒ 改判据；这里钉的是"今天这两格的到位断言对桩给 data:null 形态全盲"，
 *   它一红就说明这两页的到位断言已经真的依赖数据了 ⇒ 把锚点搬回 EMPTY_API_FALSIFIABLE、同批删缺口表条目、
 *   并同步 LEDGER P0a-GUARD-06／07／08 与 spec §11.4（§11 归控制器，本文件只到提示为止）。
 * 【为什么不是"让它红"而是"让它绿但钉住缺陷在场"】§11.4 早规定不许留成每次跑都有已知红的常态——
 *   那会把真回归淹死在噪声里；而直接从可证伪名单删掉等于悄悄拆掉 §6.14 的覆盖面。反向守卫两个方向都有牙：
 *   缺陷还在 ⇒ 这条绿（不打扰别人），缺陷被修好 ⇒ 这条红（提醒搬回矩阵）。
 * 【两条有效性前提照样跑】桩命中 > 0、pathname 仍是该锚点路由——否则"专属断言不红"绿在"实验没做成"上，
 *   与改前那个"绿在假命中"的坑同族。壳层挂没挂与 pageerror 也一并打出来当取证读数。
 * ---------------------------------------------------------------------------- */
for (const slug of Object.keys(NOT_FALSIFIABLE_BY_ARRIVAL_ASSERTION)) {
  const gapAnchor = ANCHORS.find((a) => a.slug === slug)!
  const gapRole = gapAnchor.role === 'ANON' ? 'STUDENT' : gapAnchor.role
  test.describe(`空态空转缺口 · ${gapAnchor.role}`, () => {
    test.use({ storageState: storagePathFor(gapRole) })
    test(`${slug}：桩给 data:null 形态下到位断言不红（缺陷在场，R-C2-4 反向守卫）`, async ({
      page,
    }) => {
      const pageErrors: string[] = []
      page.on('pageerror', (err) => pageErrors.push(String(err && err.message ? err.message : err)))
      const target = await resolvePath(page, gapAnchor)
      const wantPathname = new URL(target, 'http://localhost').pathname
      await setTheme(page, 'light')
      const stub = await stubApiToEmpty(page, slug)
      await freezeClock(page)
      await page.goto(target)
      await settlePage(page)

      const pathNow = await page.evaluate(() => location.pathname)
      const probe = await probeRenderShape(page)
      const preconditions = renderPreconditionFindings(probe, pageErrors)
      let threw = ''
      try {
        await assertAnchorArrival(page, slug)
      } catch (e) {
        threw = (e as Error).message
      }

      // 有效性两前提（不是对产品的主张，只是保证"这次打空真做成了"）
      expect(
        stub.served.length,
        `${slug}：桩命中 0 个请求 ⇒ 这一页根本没被打空，反向守卫绿在"实验没做成"上（缺口表登记见 NOT_FALSIFIABLE_BY_ARRIVAL_ASSERTION）`,
      ).toBeGreaterThan(0)
      expect(
        pathNow,
        `${slug}：打空后落在 ${pathNow} 而不是该锚点路由 ${wantPathname} ⇒ 绿/红都归因不到空态`,
      ).toBe(wantPathname)
      // 壳层必须真挂上、无未捕获错误——否则这条就绿在"应用又被打碎"上（改前那格正是这个假命中）
      expect(
        preconditions,
        `${slug}：本轮壳层/错误前提不成立（${preconditions.join('\n  ')}）⇒ 这不是"页面正常渲染而到位断言抓不到空载荷"，` +
          `是 harness 又把应用打碎了，与缺口表登记的形态不符，先查接管面（helpers 那条 pathname 守卫）`,
      ).toEqual([])

      console.log(
        `[缺口·空转] ${slug} 桩给 data:null 形态下：专属断言=${threw ? '红' : '绿（= 缺陷在场）'} · ` +
          `桩命中 ${stub.served.length} 请求（端点 ${JSON.stringify([...new Set(stub.served)])}）· ` +
          `data-theme="${probe.theme}" · #app>*=${probe.appChildCount} · .app=${probe.appCount} · pageerror=${pageErrors.length}`,
      )
      // 反向断言：这两页的到位断言在桩给 data:null 形态下**不红**＝当前缺陷在场（今天绿）。
      expect(
        threw,
        `${slug} 转红了 ⇒ 这一页的"数据已到位"断言现在真的依赖数据了——缺陷已修。按 R-C2-4 的转组动作处置：` +
          `把 ${slug} 从 NOT_FALSIFIABLE_BY_ARRIVAL_ASSERTION 搬回 EMPTY_API_FALSIFIABLE、同批删本表条目，` +
          '并同步 LEDGER P0a-GUARD-06／07／08 与 spec §11.4（§11 归控制器）。本轮实测依据：' +
          NOT_FALSIFIABLE_BY_ARRIVAL_ASSERTION[slug],
      ).toBe('')
    })
  })
}

/**
 * 第 13 项交付物的**入库反例（第一条：`pageerror` 计数为 0）**。
 * 【工单给的做法】用 `page.addInitScript`／`page.evaluate` 抛一个未捕获错误，**不改 `src/`**。
 *   这里用 `page.evaluate` 里的 `setTimeout(throw …)`——**异步**抛出才会走 `pageerror`，
 *   同步 throw 只会 reject `evaluate` 的 Promise，那条前提是抓不到它的（本棒实测过这个差别，
 *   写反了就会得到"这条永不成立"的假绿，所以底本与反例都断言）。
 * 【为什么反例必须入库而不是写在报告里】本轮最重的一条教训就是"反例只存在于未入 git 的过程记录"
 *   ⇒ 交付面得是跟着仓库跑、每次跑测都重做一次断言的内容。
 */
test('第 13 项前提一有牙：合成页里抛一个未捕获错误就判红（不改 src/）', async ({ page }) => {
  const pageErrors: string[] = []
  page.on('pageerror', (err) => pageErrors.push(String(err && err.message ? err.message : err)))
  await page.setContent(
    '<div id="app"><div class="app"><header class="topbar">合成顶栏</header><main>合成内容</main></div></div>',
  )
  // 底本：壳层两块都在、没有未捕获错误 ⇒ 判据必须给空清单，否则下面的"红"什么也没证明
  const pathBefore = await page.evaluate(() => location.pathname)
  expect(
    renderPreconditionFindings(await probeRenderShape(page), pageErrors),
    '合成壳层底本就被判违规——判据把"在场"那一侧写反了，本条读数以它为零基',
  ).toEqual([])
  expect(pageErrors, '底本不该有未捕获错误').toEqual([])

  // 反例：页面上下文里异步抛一个没人接的错误（等价于"挂载中途 JS 崩"）
  await page.evaluate(() => {
    setTimeout(() => {
      throw new Error('合成：挂载中途抛的未捕获错误')
    }, 0)
  })
  await page.waitForTimeout(150)
  expect(
    pageErrors.length,
    'pageerror 监听没抓到异步抛出的错误 ⇒ 这条前提在任何真页上都永不成立（它是假绿，不是守卫）。' +
      '本棒实测：同步 throw 只会 reject evaluate 的 Promise，必须走 setTimeout',
  ).toBe(1)
  const bad = renderPreconditionFindings(await probeRenderShape(page), pageErrors)
  expect(bad, '未捕获错误没被前提一抓住——第 13 项那条"能红"是假的').toHaveLength(1)
  expect(bad[0], '红话要带计数与错误本身，别让下一个人回整页里猜').toContain(
    'pageerror 计数 1 不为 0',
  )
  expect(bad[0]).toContain('挂载中途抛的未捕获错误')
  // 顺带证那句标题改口的**机理**：JS 崩的时候 pathname 根本没变——所以旧的那两条前提拦不住它。
  // （比的是**同一次会话里崩前崩后**那两个读数，不是某个魔法字符串：`setContent` 之后
  //   `location.pathname` 在不同 Chromium 上可能是 `blank`（about:blank）也可能是 `/`，
  //   写死任何一个都会变成"绿在一个环境常量上"的那种假断言——本棒第一版就写死成 `/` 而红在这里。）
  expect(
    await page.evaluate(() => location.pathname),
    '崩了之后 pathname 竟然变了——本用例主张的"pathname 看不见 JS 崩"这条实物不成立，措辞要重定',
  ).toBe(pathBefore)
})

/**
 * 第 13 项交付物的**入库反例（第二条：壳层真的渲染了＝`.app`／`.topbar` 在场）**。
 * 【两个形状都验】① 只摘掉 `.topbar`＝"壳层只挂了一半"；② 再摘掉整个 `.app`＝**白页**
 *   （`#app > *` 落到 0）。②正是题面点名的那个洞：这种页面上 `location.pathname` 不变、
 *   桩也命中过 ≥1 个请求，两条旧前提都满足而格子照样绿——本条把"照样绿"变成"当场红"。
 * 【同样不改 `src/`】移除动作走 `page.evaluate`，只作用在一次性合成 DOM 上。
 */
test('第 13 项前提二有牙：摘掉 .topbar 或整个 .app（白页）都判红，而 pathname 全程不变（不改 src/）', async ({
  page,
}) => {
  await page.setContent(
    '<div id="app"><div class="app"><header class="topbar">合成顶栏</header><main>合成内容</main></div></div>',
  )
  const pathBefore = await page.evaluate(() => location.pathname)
  expect(
    renderPreconditionFindings(await probeRenderShape(page), []),
    '合成壳层底本就被判违规——本条的两次摘除都以它为零基',
  ).toEqual([])

  // ① 只摘顶栏 ⇒ 只剩一条违规
  await page.evaluate(() => document.querySelector('.topbar')?.remove())
  const noTopbar = renderPreconditionFindings(await probeRenderShape(page), [])
  expect(noTopbar, '壳层只剩一半却没被抓住——前提二没有牙').toHaveLength(1)
  expect(noTopbar[0]).toContain('.topbar 不在场')

  // ② 再摘掉整个 `.app` ⇒ 白页形状：两条都违，且 `#app > *` 读数落到 0
  await page.evaluate(() => document.querySelector('.app')?.remove())
  const blankProbe = await probeRenderShape(page)
  const blank = renderPreconditionFindings(blankProbe, [])
  expect(blank, '白页形状（壳层全没）却没被抓住——第 13 项要堵的那个洞原样留着').toHaveLength(2)
  expect(
    blank.map((s) => (s.startsWith('.app') ? 'app' : 'topbar')).sort(),
    '两条违规的身份不对（红话要指得出是哪一块不在场）',
  ).toEqual(['app', 'topbar'])
  expect(
    blankProbe.appChildCount,
    '#app 的直接子节点数没随摘除归零——probeRenderShape 的选择器写错了，取证读数 14① 也一起作废',
  ).toBe(0)
  // **这就是那句标题改口的实物**：白页上 pathname 一点没变（旧的两条前提都满足），
  // 而第 13 项这两条新前提把它判红。
  expect(
    await page.evaluate(() => location.pathname),
    '白页形状下 pathname 竟然变了——本用例主张的"pathname 拦不住白页"这条实物不成立，措辞要重定',
  ).toBe(pathBefore)
})

/**
 * 第 14 项 ②（C2 步）：**同两格把桩从 `data: null` 换成 `data: []`，只取证、不定性。**
 * 【题面给的判据怎么读】"若 `data-theme` 回来了，缺陷的名字才立得住（壳层／页面组件不容忍 null 载荷）；
 *   若仍为空，说明触发条件不是 `data:null` ⇒ 照实写，别挑一个能自洽的解释交差。"
 *   本条因此**只打印读数**，对产品侧不下结论：断言的部分只有"这次取证真的做成了"那两条
 *   （桩命中 > 0、pathname 仍是该锚点的路由）——它们不成立时读数没有意义，而不是"结果不合我意就红"。
 * 【红线（工单原话）】在定性之前，任何注释与台账行都**不许**把它写成"打桩假象"或"已确认缺陷"。
 *   `docs/superpowers/specs/…-design.md` §11.4 那条"未定性、谁先跑完谁定性"归控制器，本棒没动它。
 * 【为什么不复用矩阵那条】矩阵已经把 `data: null` 那一态的完整读数打出来了（第 13 项的两条前提与
 *   `[取证 14①]` 那一行是同一套数，工单明写"别做两遍"）；本条只补 `data: []` 这一态，
 *   两态并排起来就是 ② 要的对照。
 */
for (const slug of Object.keys(NOT_FALSIFIABLE_BY_ARRIVAL_ASSERTION)) {
  const probeAnchor = ANCHORS.find((a) => a.slug === slug)!
  // 缺口表里的锚点**必须带会话**（ANON 没有 storageState 可取）。这一条由「互斥且并集」那条用例断言，
  // 这里只做类型收窄——不写 `as`，因为那会把"万一有人登记了一个 ANON 锚点"这件事糊掉。
  const probeRole = probeAnchor.role === 'ANON' ? 'STUDENT' : probeAnchor.role
  test.describe(`第 14 项② 取证 · ${probeAnchor.role}`, () => {
    test.use({ storageState: storagePathFor(probeRole) })
    test(`${slug}：桩换成 data: [] 后的读数（只报观察，不据此定性）`, async ({ page }) => {
      const pageErrors: string[] = []
      page.on('pageerror', (err) => pageErrors.push(String(err && err.message ? err.message : err)))
      const target = await resolvePath(page, probeAnchor)
      const wantPathname = new URL(target, 'http://localhost').pathname
      await setTheme(page, 'light')
      const stub = await stubApiToEmpty(page, slug, 'emptyArray')
      await freezeClock(page)
      await page.goto(target)
      await settlePage(page)

      const pathNow = await page.evaluate(() => location.pathname)
      const probe = await probeRenderShape(page)
      let genericReasons: string[] = []
      try {
        await assertGenericGuards(page, probeAnchor, 'light')
      } catch (e) {
        genericReasons = (e as Error).message
          .split('\n')
          .map((l) => l.trim())
          .filter((l) => l !== '')
      }
      // 取证有效性——这两条不成立时下面那行读数没有意义（它们不是对产品的主张）
      expect(stub.served.length, `${slug}：桩命中 0 个请求 ⇒ 这次对照根本没做成`).toBeGreaterThan(0)
      expect(pathNow, `${slug}：落在 ${pathNow} 而不是 ${wantPathname} ⇒ 对照的页不是同一页`).toBe(
        wantPathname,
      )
      console.log(
        `[取证 14②] ${slug} 桩体 data=[] ⇒ data-theme="${probe.theme}"（回到 light 了吗：` +
          `${probe.theme === 'light' ? '回来了' : '没回来'}）· #app>*=${probe.appChildCount} · .app=${probe.appCount} · ` +
          `.topbar=${probe.topbarCount > 0 ? '在场' : '不在场'} · pageerror=${pageErrors.length}` +
          (pageErrors.length > 0 ? `（${pageErrors.join(' ｜ ')}）` : '') +
          ` · 桩命中 ${stub.served.length} 请求（端点 ${JSON.stringify([...new Set(stub.served)])}）· 通用守卫理由 ${genericReasons.length} 条`,
      )
      for (const [i, r] of genericReasons.entries()) {
        console.log(`         [取证 14②·理由 ${i + 1}] ${r}`)
      }
      console.log(
        `         [取证 14②·对照] 同格 data:null 那一态的读数见上一条矩阵用例的 [取证 14①] 行；` +
          `两态是否同形只由这两行 stdout 判定，本棒**不写结论**（定性归控制器，spec §11.4 未定性那条）。`,
      )
    })
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
    await assertGenericGuards(
      page,
      ANCHORS.find((a) => a.slug === 'teacher-dashboard')!,
      'light',
    )
    let threw = ''
    try {
      await assertAnchorArrival(page, 'teacher-dashboard')
    } catch (e) {
      threw = (e as Error).message
    }
    expect(
      threw,
      '四条通用守卫都过的空态，专属断言也必须拦得住——这条红了就是那次事故重演',
    ).not.toBe('')
    console.log(
      `[falsify] teacher-dashboard 复现成功：通用守卫绿 + 专属断言红（${threw.split('\n')[0]}）`,
    )
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
    expect(
      onSelf,
      '接口打空后 admin-system-form 的断言变红了——"本页画面不随接口变"这句已失效，需重测',
    ).toBe('')
    console.log(
      `[falsify] admin-system-form 接口打空后自己的断言=绿（= 本页画面不随接口变，见本用例说明）`,
    )

    const anon = await page
      .context()
      .browser()!
      .newContext({ baseURL: process.env.PW_BASE_URL ?? 'http://localhost:5175' })
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
