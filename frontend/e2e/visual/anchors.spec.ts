import { expect, test } from '@playwright/test'
import type { Page } from '@playwright/test'
import {
  ANCHORS,
  freezeClock,
  maskOf,
  settlePage,
  setTheme,
  snapName,
  storagePathFor,
  type AnchorSpec,
  type ThemeName,
} from './visual-helpers'

/** 课程列表页自身的 URL——__FROM_COURSE_LIST__ 先到这里取 id，再进壳页 */
const COURSE_LIST_PATH = '/student/courses'
/** 列表页的数据源：/student/courses 的 .tree-row 就是它 courses[0..] 的渲染结果 */
const COURSE_LIST_API = '/api/student/dashboard'

/**
 * 学生 /student/courses/:id/learning-center 的 :id 必须来自当前种子数据的真实列表首项，
 * 且排序口径固定——否则"取第一条"在不同环境下取到不同课程，图就不该相等。
 *
 * 两处与计划原稿不同，都是本机实测出来的（原稿照跑必然 15s 超时）：
 * ① 原稿没导航就取选择器。runAnchor 调本函数时页面还停在 about:blank，
 *    `page.locator(...).waitFor` 永远等不到，报的是 TimeoutError 而不是"种子没课"，
 *    归因方向直接被带偏。这里先 goto 列表页。
 * ② 原稿的选择器 `a[href*="/student/courses/"], [data-course-id]` 在本页零命中：
 *    Courses.vue:46-74 的课程卡是 `div.tree-row` + `@click="selectCourse(c)"`，
 *    既没有 href 也没有 data 属性，"进入学习" (:114) 同样是按钮不是链接。
 *    为了不改产品源码去迁就测试，id 改从**这一页真正渲染所用的那个响应**里取：
 *    .tree-row 与响应数组同源同序（:47 `v-for="c in courses"` 无 sort/filter），
 *    所以 `courses[0].courseId` ≡ 列表首卡。DOM 侧仍必须真有一张可见卡片，
 *    缺这一条断言时空种子库会解析出 id、截出一张空态图当基线——正是注释里点名的事故。
 *
 * 注意：本函数**不得**调 settlePage。settlePage 每次都会 runFor(5000) 推进页面时钟，
 * 若这里也调一次，__FROM_COURSE_LIST__ 锚点的截图时刻就变成 09:00:20+，与其他 23 张不同基，
 * 而重跑时"课程列表要几轮才稳定"又会让轮数漂移——等于专门给这一页装了个随机源。
 * 列表页的 DOM 只需已渲染，用 locator 等待即可。
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
 * 且现象是"基线全都截成登录页"而非报错，所以用一条显式断言兜住（见下方 expect URL）。
 */
async function runAnchor(page: Page, anchor: AnchorSpec, theme: ThemeName): Promise<void> {
  await setTheme(page, theme)
  // 解析放在 freezeClock 之前：__FROM_COURSE_LIST__ 会多走一次列表导航，
  // 时钟若已 install，这次导航就把"冻结后又一次 runFor 机会"混进了截图时刻的账，
  // 24 张就只有 23 张落在同一个页面时间上。解析完再装时钟，页面时间口径全表一致。
  const target = await resolvePath(page, anchor)
  await freezeClock(page)
  await page.goto(target)
  await settlePage(page)

  if (anchor.role !== 'ANON') {
    // 会话真在用的证据：没被守卫踢回 /login（router/index.ts:598 的 next('/login')）
    expect(page.url()).not.toContain('/login')
  }
  // 主题注入必须真的生效——否则 24 张里有 12 张是同一张图换了名字，
  // 基线会"通过"但从未验证过暗色（spec §6.6 明令禁止的现象）。
  //
  // 【已知缺陷 · error-404 两主题恒红，勿用 --update-snapshots 吸收】
  // /403 /404 /503 三个错误页拿不到 data-theme：全仓只有 useTheme 的 applyTheme 会写这个属性
  // （useTheme.ts:15），而 applyTheme 只在调用过 useTheme() 的组件里跑（ThemeSwitcher / Profile），
  // 错误页既无 AppShell 也无 ThemeSwitcher → <html> 上永远没有 data-theme，
  // theme.css:205 的 [data-theme='dark'] 整块不生效，暗色用户看到的仍是 :root 的浅色 token。
  // 后果：这两张基线的明与暗必然逐像素相同，正是本断言要拦的事，所以按 Task 4 Step 3 的口径
  // 记为真实缺陷（另开修页任务），不入库。修好后这 2 张自动补生成，无需改本文件。
  await expect(page.locator('html')).toHaveAttribute('data-theme', theme)
  // 空壳页不得当基线：SPA 白屏 / 报错边界都会截出一张干净的图。
  await expect(page.locator('#app')).not.toBeEmpty()
  // 根类实测自 src/components/AppErrorBoundary.vue:2 —— 是 `.error-boundary`，
  // 不是 `.app-error-boundary`，全仓也没有 data-test="error-state"。写错的选择数会让这层防护静默失效。
  await expect(page.locator('.error-boundary')).toHaveCount(0)
  // /503 与 /403 都是"渲染正常但内容不对"的落点：守卫在后端不可达时落 /503（router/index.ts:622），
  // 角色不符时落 /403（:632）。两张都能通过上面的非空断言，却把一次后端抖动固化成基线。
  if (anchor.role !== 'ANON') {
    expect(new URL(page.url()).pathname).not.toMatch(/\/(403|503)$/)
  }

  // `.png` 在这里补，不在 snapName 里补：Playwright 1.63 的 toHaveScreenshot 先对**传入的 arg**
  // 取 extname 决定 mimeType，无扩展名直接抛 `Screenshot name "login-light" must have a '.png'
  // or '.webp' extension`（matchers/expect.js:12597），压根走不到比对。模板里的 {ext} 不是白给的，
  // 它就是这个 arg 的扩展名（workerProcessEntry.js:1258 的 basename(subPath, ext)），
  // 所以 arg 带 .png 落盘名仍是 login-light.png，不会变成 .png.png。
  // 保持 snapName 无扩展名是 helper 已发布并被 helpers.spec.ts 钉住的契约，改动只发生在消费侧。
  await expect(page).toHaveScreenshot(`${snapName(anchor.slug, theme)}.png`, {
    fullPage: true,
    animations: 'disabled',
    caret: 'hide',
    scale: 'css',
    mask: maskOf(page, anchor.maskSelectors),
    maxDiffPixelRatio: 0.002,
  })
}

for (const theme of ['light', 'dark'] as ThemeName[]) {
  for (const role of ['ANON', 'STUDENT', 'TEACHER', 'ADMIN'] as const) {
    const subset = ANCHORS.filter((a) => a.role === role)
    if (subset.length === 0) continue
    test.describe(`视觉基线 · ${theme} · ${role}`, () => {
      if (role !== 'ANON') test.use({ storageState: storagePathFor(role) })
      for (const anchor of subset) {
        test(anchor.slug, async ({ page }) => runAnchor(page, anchor, theme))
      }
    })
  }
}
