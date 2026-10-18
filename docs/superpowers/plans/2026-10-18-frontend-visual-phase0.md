# 前端视觉升级 Phase 0（视觉台账 harness + 弹层原语齐平）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建成分辨率/时间/主题三维钉死的视觉回归基线（12 锚点 × 明暗 = 24 张）与唯一台账 `LEDGER.md`，并把 `ui/Modal` / `ui/Drawer` 补强到与 `el-dialog` / `el-drawer` 能力齐平、把焦点覆盖率护栏改写为弹层实现无关——使 Phase 4 的 65 处结构收敛既不留痕缺口，也不造成 a11y 倒退。

**Architecture:** 视觉 harness 走**独立** Playwright 配置文件，不与既有 9 个流程 spec 共用 `projects`/`screenshot` 设置；确定性靠 `page.clock` 冻结时间 + 真后端固定种子数据 + 文本稳定态轮询达成，而非盲目 `waitForTimeout`。原语补强走 TDD：先把 12 个存量 `Modal` 调用点的行为钉成测试，再加能力，保证向后兼容是"测出来的"而不是"看起来没问题"。

**Tech Stack:** Vue 3.5 `<script setup>` · Element Plus 2.4.4 · Vitest 3.2.7（jsdom + `@vue/test-utils`）· **Playwright 1.63.0**（`toHaveScreenshot` / `page.clock` / `storageState`）· 无 SCSS 新增（新层用原生 CSS）

**Spec:** `docs/superpowers/specs/2026-10-18-frontend-visual-upgrade-design.md` — 本计划实现其 **§4 Phase 0a 与 Phase 0b**，并消解 §9 的第 2、3、4、5 项未验证条。执行时两份同读。

## Global Constraints

以下逐条来自 spec，每个任务的验收都隐含包含本节：

- **Phase 0 不引入任何新依赖。** Playwright、vitest、`@vue/test-utils`、jsdom 均已在 `package.json` 内（devDependencies）。`lucide-vue-next` / `motion-v` 属 Phase 6，本阶段不得安装。
- **不改任何业务页 `<template>` 的语义**（不增删改 `v-if` / `v-for` / 事件 / props / slot 名）。spec §6.1。
- **弹层焦点覆盖率必须保持 100%，且不得靠放宽断言达成。** spec §9.5 明令：直接删断言或调低阈值 = 无声拆掉 D2-7 的机械保障。
- **验证链每任务收尾跑齐**：`npm run lint` → **`npm run typecheck:gate`** → `npm run test` → `npm run build` → `npm run size:gate`。
  **注意：不是 `npm run typecheck`**——本仓前端从未做过全量类型治理，存量错误约 402 条，`vue-tsc` 裸跑必然"红"。
  门禁是**棘轮**：`scripts/check-types.cjs` + `scripts/typecheck-baseline.json`，错误数只许降不许升；
  收尾证据写"gate 通过 / 总数与基线相同、本任务新增 0"，不要写"typecheck 全绿"。
- **e2e 目录的正确性靠类型检查保证，不靠 lint**：`eslint.config.js` 只为 `src/**` 配了 TS parser，
  `npx eslint e2e/**` 会对**既有**的 `e2e/test-helpers.ts` 同样报 Parsing error（先前存在的状况，非本批引入）。
  Phase 0 **不扩 eslint 作用域**；`tsconfig.json` 已 include `e2e/**/*.ts`，新增视觉文件由 `typecheck:gate` 覆盖。
- **e2e / 视觉跑之前必须带 `PW_PORT=5175`**（`frontend/playwright.config.ts:3-6` 自陈 5173 常被别的项目占用、
  `reuseExistingServer` 会静默复用并测出假缺陷。**该文件在 `frontend/` 根，不在 `frontend/e2e/`**）。
  **并且 `PW_BASE_URL` 必须与它一致**（Task 3 实测：会话夹具的 localStorage 绑定到写入时的 origin，
  夹具的 baseURL 回退值写死 5175；若只改 `PW_PORT` 而不改 `PW_BASE_URL`，得到的是"半恢复的会话"——
  cookie 是端口无关的、能带上，但 `user`/`permissions`/引导标记这些按 origin 存的键取不回来，
  现象是页面渲染成未登录态而不是报错）。要么固定用 5175，要么两个变量一起设。
- **会话的真实载体是 httpOnly cookie，不是 localStorage**（Task 3 实测，`src/stores/auth.ts:34` 的口径是
  token 从不落客户端存储）。夹具里是 `ai_course_token` + `ai_course_refresh` 两个 cookie。
  因此**任何"往 localStorage 塞 token 就当作已登录"的做法在本项目必然失败**，
  `e2e/test-helpers.ts:33-38` 的说明与此一致。
- **两个 Playwright config 必须互不发现**：`frontend/playwright.config.ts` 的 `testDir: './e2e'` 会把
  `e2e/visual/` 一并扫进流程套件，故须给它加 `testIgnore: /[/\\]visual[/\\]/`（Task 1 负责，见其 Step 6）。
- **视觉基线须同时覆盖明暗两主题**，暗色不得只在浅色截图下被判通过。spec §6.6。
- **主题默认值是 `dark`**（`src/composables/useTheme.ts:6` `ref(localStorage.getItem(THEME_KEY) || 'dark')`，键 `fz-theme`）。任何"没设置主题就当作浅色"的假设都是错的。
- **vitest 固化 `fileParallelism: false`**（`vitest.config.ts` 注释：Windows + 中文路径下默认并发 worker 挂起）。不得为了跑得快而改动它。
- **覆盖率门禁棘轮制**：`vitest.config.ts` thresholds 为 lines 30 / functions 62 / branches 78 / statements 30，**只许上调、不得下调**。
- **台账唯一**：`docs/visual-upgrade/LEDGER.md` 一份表，不拆多份。单元截图只留失败项与代表项（spec §5）。
- **计划分份**：本计划只做 Phase 0。Phase 1–3 / 4 / 5–6 的计划待本阶段验收后另出（spec §4"计划分份"）。

---

## 前置条件（每次跑视觉任务前核对，缺一即停）

1. **后端在 8080 且已就绪。** 视觉 harness 复用真后端登录（`e2e/test-helpers.ts:159-188` 的 `loginAs` 打真实 `/api/auth/login`），因为 auth 加固后伪造 localStorage 会被守卫判 `/403` 或 401 → 登出（`test-helpers.ts:33-38` 的说明）。
   Run: `netstat -ano | grep ':8080 ' | grep LISTENING`
   Expected: 至少一行。无则用仓库根 `start-backend.bat` 启动，再复测。
2. **登录限流已放宽。** 24 张基线 = 24 次进入需会话的页面；`loginAs` 注释（`test-helpers.ts:169-181`）记明默认 `ratelimit.login` 10 次/60 秒/IP，超 429 且后端**不打日志**，现象是"随机红灯"。
   后端须以 `RATELIMIT_LOGIN_LIMIT=200` 启动。本计划用 `storageState` 把登录次数压到每角色 1 次（共 3 次），但限流仍须放宽以防重跑。
3. **Playwright 支持 `page.clock`。** 该 API 自 1.45 起提供。
   Run: `npx playwright --version`
   Expected: `Version 1.63.0`（若 < 1.45 则本计划不适用，停下报告）。
   **版本影响写法**：1.63 的 `Clock` 接口实测只有 `fastForward / install({time}) / pauseAt / resume /
   runFor / setFixedTime / setSystemTime` —— **没有 `pause()`**，且 `install` 不接受 `pauseAt` 选项。
   时钟相关代码须按此写，见 Task 1 Step 3。

---

### Task 1: 独立视觉配置与主题注入助手

现有 `playwright.config.ts` 是流程 e2e 的单一事实源（`screenshot: 'only-on-failure'`、`webServer` 起 dev server、单 `chromium` project）。视觉 spec 需要不同的截图策略、不同主题矩阵与冻结时钟，塞进同一 config 会让 9 个既有 spec 的行为一起变。故新建独立 config。

**Files:**
- Create: `frontend/playwright.visual.ts`
- Create: `frontend/e2e/visual/visual-helpers.ts`
- Test: `frontend/e2e/visual/smoke.spec.ts`
- Modify: `frontend/playwright.config.ts` — 加 `testIgnore`。理由：它的 `testDir: './e2e'` 会把新建的
  `e2e/visual/` 一并收进流程套件（实测 `--list` 从 35 tests/9 files 变 37/10），而 `npm run test:e2e`
  不带 `PW_PORT`、会打到 5173 上那个**别的项目**的服务；CI 里 `.github/workflows/ci.yml:756` 跑裸
  `npx playwright test` 同样会收。**"新建视觉 config" 与 "把 visual 从流程 config 里摘出去" 是同一件事的两半，
  只做前一半就是交付半成品**，故归入本任务。
- Modify: `frontend/tsconfig.json` — `include` 加 `"playwright.visual.ts"`，让新 config 自身也受类型检查。

**Interfaces:**
- Consumes: 无（本任务是地基）
- Produces:
  - `THEME_KEY: 'fz-theme'`、`type ThemeName = 'light' | 'dark'`
  - `setTheme(page: Page, theme: ThemeName): Promise<void>` — 导航前注入 localStorage，幂等
  - `freezeClock(page: Page): Promise<void>` — 装固定时钟
  - `settlePage(page: Page): Promise<void>` — 等网络空闲 + 字体就绪 + 关键文本稳定
  - `storagePathFor(role: 'STUDENT'|'TEACHER'|'ADMIN'): string` — 会话夹具路径（Task 3/4/14 都从这里导；
    **定义在本文件而非 `global-setup.ts`**：消费方是视觉 spec，放在被导入方才不会出现循环依赖，
    且 `tsconfig.json` 已 include `e2e/**/*.ts`，导入断了 `npm run typecheck` 会当场红）

- [ ] **Step 1: 写烟雾测试（先证明主题注入真的生效）**

Create `frontend/e2e/visual/smoke.spec.ts`:

```ts
import { expect, test } from '@playwright/test'
import { setTheme } from './visual-helpers'

// 目的只有一件：证明"主题是被钉住的"，不是碰巧渲染成某个样子。
// useTheme.ts:6 的默认值是 'dark'，所以"不设主题就当作浅色"的假设必然错；
// 若 setTheme 注入失败，light 分支会读到默认的 dark 而两遍同图，本用例即失效而不报错——
// 因此两条断言分别校验 data-theme 属性，而不是只比截图。
for (const theme of ['light', 'dark'] as const) {
  test(`锚点页在 ${theme} 主题下 data-theme 命中`, async ({ page }) => {
    await setTheme(page, theme)
    await page.goto('/login')
    await expect(page.locator('html')).toHaveAttribute('data-theme', theme)
  })
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `PW_PORT=5175 npx playwright test -c playwright.visual.ts -g "data-theme 命中"`
Expected: FAIL——`setTheme` / 配置文件不存在（`Cannot find module './visual-helpers'` 或 config 读不到）。

- [ ] **Step 3: 写助手（含稳定态判定，不用裸超时）**

Create `frontend/e2e/visual/visual-helpers.ts`:

```ts
import type { Locator, Page } from '@playwright/test'

export const THEME_KEY = 'fz-theme'
export type ThemeName = 'light' | 'dark'

/**
 * 主题必须在导航前落到 localStorage：useTheme 在模块作用域读一次初值
 * （useTheme.ts:6），页面起来后才写入就来不及了。addInitScript 而非 evaluate——
 * Chromium 新版对 about:blank 的 localStorage 访问抛 SecurityError，
 * 且 addInitScript 对每次导航幂等（test-helpers.ts:196-199 实测结论）。
 */
export async function setTheme(page: Page, theme: ThemeName): Promise<void> {
  await page.addInitScript((t) => localStorage.setItem('fz-theme', t), theme)
}

/**
 * 冻结时间。仪表盘的"3 分钟前"、CountUp 的缓动、ECharts 入场动画都挂在时钟上，
 * 不冻结则同一份代码每天截出不同的图，基线失去意义。
 * 固定到本计划撰写日的上午九点整——刻意避开零点/整日边界，边界值最易暴露格式化差异。
 *
 * 必须真的停下来：`clock.install({ time })` 只把起点挪到 time，时钟**仍按真实速率前进**
 * （实测 400ms 真实时间走 416ms 页面时间），到截图那一刻页面时间 = 09:00:0x + 真实耗时，
 * 秒级相对文案照旧会漂，Task 4 的"二次跑零 diff"就变成随机红灯。
 *
 * API 事实（本机 Playwright 1.63.0，实测自 `playwright-core/types/types.d.ts` 的 `Clock` 接口）：
 *   `install` 只接受 `{ time }`；`pauseAt` 是**独立方法**；**没有 `pause()`**。
 *   把 `pauseAt` 写进 install 的选项里会被静默忽略，且 `npm run typecheck:gate` 报 TS2353、
 *   把存量错误从 402 抬到 403 —— 即"看着生效、实际既没冻结又弄脏棘轮"。
 * 故正确写法是两步：install 定起点 → `pauseAt(起点 + 5000)` 让它停在该刻。
 * 注意 `pauseAt` 在导航之前调用时不会触发页面 timer，动画推进须交给 `settlePage`（见下）。
 */
export const CLOCK_T0 = new Date('2026-10-18T09:00:00')
export async function freezeClock(page: Page): Promise<void> {
  await page.clock.install({ time: CLOCK_T0 })
  await page.clock.pauseAt(new Date(CLOCK_T0.getTime() + 5000))
}

/**
 * 稳定态收敛。顺序有意义：先等网络空闲让迟到的数据把动画排上队，
 * 再一次性把时钟推进 5000ms（越过 ECharts ~1000ms 与 CountUp 的时长并停住），
 * 然后读字体、校文本不再变。这比 waitForTimeout 可靠——固定毫秒数在慢机器上会截到半截动画。
 *
 * 截图时刻的页面时间 = 09:00:05 + `settlePage` 的 5000ms + 稳定态轮询所推进的 `120ms × 轮数`，
 * 落在 09:00:10 附近；轮数由页面文本何时不再变化决定，故**不是**一个逐页相同的常量。
 * Task 4/14 若要断言绝对时间文案，只能断言到"小时:分钟"粒度，或在同一锚点上比"二次跑一致"，
 * 不得假设每页都是同一个毫秒值。
 */
export async function settlePage(page: Page): Promise<void> {
  await page.waitForLoadState('networkidle')
  await page.clock.runFor(5000)
  await page.evaluate(() => document.fonts.ready.then(() => undefined))
  await waitForStableText(page)
}

/**
 * 关键文本稳定判定：连续两轮读数相同才放行。
 * 零匹配也算稳定——一个没有任何统计卡的页面（登录页就是）不可能"还在动画中"，
 * 若在此等超时，抛出的诊断"可能仍在动画中"是假的，且会把多数锚点页拖成随机红。
 *
 * 每轮轮询先 `runFor(120)` 再读数：时钟已停在 pauseAt，页面定时器不会自己走，
 * 不推时钟的话"正在变化的数字"永远不变，第 3 条 helper 测试（数字跑到 7 才放行）无法成立。
 */
export async function waitForStableText(page: Page, timeout = 15000): Promise<void> {
  let prev: string | null = null
  const deadline = Date.now() + timeout
  while (Date.now() < deadline) {
    const now = await page.evaluate(() =>
      Array.from(document.querySelectorAll('.s-value, .pg-title'))
        .map((el) => el.textContent?.trim() ?? '')
        .join('|'),
    )
    if (now === '') return // 页面无统计类文本 → 天然稳定
    if (now === prev) return
    prev = now
    await page.clock.runFor(120)
  }
  throw new Error(`文本未收敛到稳定态（可能仍在动画中）·最后读数: ${prev}`)
}

export interface AnchorSpec {
  /** 用于快照文件名的稳定 slug，禁止含空格与斜杠 */
  slug: string
  title: string
  /** 'ANON' 表示无需会话（登录页 / 错误页） */
  role: 'ANON' | 'STUDENT' | 'TEACHER' | 'ADMIN'
  /** 相对 baseURL 的路径；'__FROM_COURSE_LIST__' 由 resolvePath 在运行时解析 */
  path: string | (() => Promise<string>)
  /** 合法随数据变化、必须遮掉的区域选择器（如头像图片） */
  maskSelectors?: string[]
}

/**
 * 快照文件名基。**不要**在这里放 `{platform}` 之类的占位符：Playwright 只在
 * `snapshotPathTemplate` 里展开占位符，作为 `toHaveScreenshot(name)` 传入的 arg 里
 * `{platform}` 会原样落进文件名。
 *
 * 返回**不含扩展名**的名字，但**扩展名要由调用方补 `.png`**——不是模板免费给的。
 * 机制实测（Playwright 1.63）：`toHaveScreenshot("x-light")` 直接抛
 * `Screenshot name "x-light" must have a '.png' or '.webp' extension`，
 * 因为 `matchers/expect.js:12597` 先按**传入 arg 的 extname** 定 mimeType，没扩展名根本进不到比对；
 * 而模板尾部的 `{ext}` 是**从 arg 的扩展名反推**出来的（`workerProcessEntry.js:1258` 的
 * `basename(subPath, ext)`），arg 带 `.png` 时 `{arg}` 仍是去掉扩展名的部分，
 * 落盘名 `x-light.png`，**不会**得到 `light.png.png`。
 * 所以：helper 保持无扩展名返回值，调用处写 `${snapName(slug, theme)}.png`。
 */
export function snapName(slug: string, theme: ThemeName): string {
  return `${slug}-${theme}`
}

/** 遮罩定位器。同步函数——它没有任何 await，标 async 只会让调用点多一个假 await。 */
export function maskOf(page: Page, selectors: string[] = []): Locator[] {
  return selectors.map((s) => page.locator(s))
}

/**
 * 会话夹具路径。定义在 helper 而非 global-setup：消费者是 anchors.spec.ts /
 * motion-observation.spec.ts，让它们去 import global-setup 会形成"配置导入设置"的反向依赖。
 * 路径相对 frontend/（Playwright 的 storageState 按 cwd 解析），全仓只用这一种写法，
 * 绝对/相对混用会得到 ENOENT 这种最难查的失败。
 */
export function storagePathFor(role: 'STUDENT' | 'TEACHER' | 'ADMIN'): string {
  return `e2e/visual/.auth/${role.toLowerCase()}.json`
}
```

- [ ] **Step 4: 写配置（含快照目录显式化）**

Create `frontend/playwright.visual.ts`:

```ts
import { defineConfig, devices } from '@playwright/test'

// 与 playwright.config.ts 分开的理由：那边 screenshot 是 'only-on-failure'
// （playwright.config.ts:25），成功即无图；视觉回归要的正是成功时的图。
// 两者共用一份 config 会让 9 个流程 spec 的行为连带改变。
const PORT = process.env.PW_PORT || '5175'
const BASE_URL = `http://localhost:${PORT}`

export default defineConfig({
  testDir: './e2e/visual',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env.CI,
  retries: 0,
  timeout: 60000,
  reporter: [['list']],
  expect: { timeout: 20000 },
  use: {
    baseURL: BASE_URL,
    trace: 'off',
    screenshot: 'off',
    video: 'off',
    contextOptions: { reducedMotion: 'reduce' },
  },
  projects: [
    {
      name: 'chromium-visual',
      use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 }, deviceScaleFactor: 1 },
    },
  ],
  // 快照落点显式指定，符合 spec §10 的 __snapshots__ 约定
  //（Playwright 默认落在 `<spec>-snapshots/`，与本仓库文档不一致，故改写模板）
  snapshotPathTemplate: '{testDir}/__snapshots__/{testFilePath}/{arg}{ext}',
  webServer: {
    command: `npm run dev -- --port ${PORT} --strictPort`,
    url: BASE_URL,
    reuseExistingServer: !process.env.CI,
    timeout: 120000,
  },
})
```

- [ ] **Step 5: 跑测试确认通过**

Run: `PW_PORT=5175 npx playwright test -c playwright.visual.ts -g "data-theme 命中"`
Expected: PASS，2 passed。若 light 用例失败并显示 `data-theme="dark"`，说明 `addInitScript` 未生效——回到 Step 3 检查是否在 `goto` 之前调用。

- [ ] **Step 5b: 把 helper 本身测了（不是只测 setTheme）**

`settlePage` / `freezeClock` 是 24 张基线的公共依赖，它们出错的方式是"每页都假红"或"每页都假绿"，
比 smoke 用例更值得一条自己的测试。Create `frontend/e2e/visual/helpers.spec.ts`:

```ts
import { expect, test } from '@playwright/test'
import { freezeClock, settlePage, waitForStableText } from './visual-helpers'

// 零统计卡的页面（登录页就是）必须直接判稳定，而不是空转到 15s 超时后
// 抛一句"可能仍在动画中"的假诊断。
test('settlePage 在无 .s-value/.pg-title 的页上不超时', async ({ page }) => {
  await freezeClock(page)
  await page.setContent('<div id="app"><h1>纯静态</h1></div>')
  await settlePage(page)
})

// 时钟必须真的停下来：install 后不冻结时，页面时间按真实速率前进（实测约 310ms/300ms），
// 秒级相对文案会漂，Task 4 的"二次跑零 diff"就变成随机红灯。
test('freezeClock 之后页面时间不再随真实耗时前进', async ({ page }) => {
  await freezeClock(page)
  await page.setContent('<div id="app"></div>')
  const t1 = await page.evaluate(() => Date.now())
  await page.waitForTimeout(400)
  const t2 = await page.evaluate(() => Date.now())
  expect(t2 - t1).toBe(0)
})

// 有统计卡的页面，文本变化期间不得提前放行。
test('waitForStableText 在文本仍在变时不立即返回', async ({ page }) => {
  await freezeClock(page)
  await page.setContent('<div id="app"><span class="s-value">0</span></div>')
  await page.evaluate(() => {
    const el = document.querySelector('.s-value')!
    let n = 0
    const id = setInterval(() => {
      el.textContent = String(++n)
      if (n > 6) clearInterval(id)
    }, 50)
  })
  await waitForStableText(page)
  await expect(page.locator('.s-value')).toHaveText('7')
})
```

Run: `PW_PORT=5175 npx playwright test -c playwright.visual.ts helpers`
Expected: 三条 PASS。**若第二条 FAIL，说明 `pauseAt` 没真的停住时钟**——此时**不要**去找 `pause()`
（本机 Playwright 1.63.0 的 `Clock` 接口里没有这个方法，实测自 `playwright-core/types/types.d.ts`），
也**不要**把断言改成"前进量 < 某阈值"或删除它。改法是核对 `install` 只收 `{ time }`、
`pauseAt` 必须作为独立 `await` 调用（写成 install 选项会被静默忽略并让 typecheck 报 TS2353）。
第三条若停在 `'0'` 而非 `'7'`，是 `waitForStableText` 没在每轮推时钟——见其注释。

- [ ] **Step 5c: 把 visual 从流程 config 里摘出去（本任务交付的另一半）**

`playwright.config.ts` 的 `testDir: './e2e'` 会把 `e2e/visual/` 一并收进流程套件——实测
`--list` 从 35 tests/9 files 变成 37/10。后果是具体的：`npm run test:e2e`（package.json 里不带 `PW_PORT`）
会在 5173 上跑视觉用例，而 5173 按该文件自己的注释常被**别的项目**占用；CI 的 `.github/workflows/ci.yml`
跑裸 `npx playwright test`，同样会收走。Modify `frontend/playwright.config.ts`：

```ts
export default defineConfig({
  testDir: './e2e',
  // 视觉回归有自己的 config（playwright.visual.ts）：那边 workers:1、快照进 __snapshots__、
  // 端口 5175。留在这里被扫到，流程套件就会在 5173 上跑基线并假红。
  testIgnore: /[/\\]visual[/\\]/,
```

Run: `npx playwright test --list`
Expected: 回到 **35 tests / 9 files**。数字没变回去就是 `testIgnore` 的正则没命中 Windows 反斜杠路径。

- [ ] **Step 6: 提交前先过类型检查（e2e 不在 lint 口径内，别把 lint 失败当通过）**

```bash
cd frontend
npm run typecheck:gate   # 棘轮门禁：本仓存量错误约 402 条，基线在 scripts/typecheck-baseline.json
```
Expected: gate 通过。若报 `playwright.visual.ts` 未被 include（include 列表里没有它），
把 `"playwright.visual.ts"` 加进 `tsconfig.json` 的 `include`（与既有 `"playwright.config.ts"` 同列），
而不是把 e2e 文件从类型检查里摘掉。

**不要用 `npx eslint e2e/visual/ playwright.visual.ts`**：`eslint.config.js` 只为 `src/**` 配了 TS parser，
该命令会报 Parsing error（既有的 `e2e/test-helpers.ts` 同样报错，属先前状况，不是本批引入）。
e2e 的正确性由上面的 `typecheck:gate` 保证；这条 lint 缺口按 deferred minor 记入账本，**不得记为通过**。

```bash
git add frontend/playwright.visual.ts frontend/e2e/visual/visual-helpers.ts frontend/e2e/visual/smoke.spec.ts frontend/e2e/visual/helpers.spec.ts frontend/playwright.config.ts frontend/tsconfig.json
git commit -m "test(visual): 独立视觉配置与主题/时钟/稳定态助手，并用 testIgnore 把基线挡在流程套件之外"
```

---

### Task 2: 三张 PNG 判据写进 `.gitattributes` 与 `.gitignore`

视觉基线是二进制，必须禁止换行/文本 diff 处理；同时 `playwright-report/`、`test-results/` 会随每次视觉跑生成临时目录。

**Files:**
- Modify: `frontend/.gitignore`
- Modify: `.gitattributes`

**Interfaces:**
- Consumes: Task 1 的 `snapshotPathTemplate` 产出的目录形状
- Produces: 无对外接口；后续任务的"基线是否真的入库"判据

- [ ] **Step 1: 先确认当前忽略状态与既有 gitattributes 约定**

Run: `cd frontend && git check-ignore -v e2e/visual/__snapshots__/x.png playwright-report/index.html 2>/dev/null; cat ../.gitattributes`
Expected: 看清 `frontend/.gitignore` 现有条目与根 `.gitattributes` 是否已声明 binary 规则。按实际内容决定追加什么，不凭记忆改。

- [ ] **Step 2: 追加忽略与 binary 规则**

在 `frontend/.gitignore` 末尾追加（已有同名条目则跳过，不重复写）：

```gitignore
# 视觉回归的中间产物（真实基线 PNG 在 e2e/visual/__snapshots__，须入库，不在此列）
test-results/
playwright-report/
e2e/visual/**/*-diff.png
e2e/visual/**/*-actual.png
```

在根 `.gitattributes` 追加（**用 `binary` 宏，不用 `merge=binary`**——Task 2 实测裁定，见下）：

```gitattributes
# 视觉基线：禁止换行/文本化 diff，否则跨平台重生成会把整图判为差异。
# 用 binary 宏而非 merge=binary：本仓既没有任何 merge driver 配置
# （git config --get-regexp '^merge\..*\.driver' 为空），而 merge=<名称> 只是"选一个驱动"，
# 未注册的驱动会让合并直接报错；更关键的是 binary 宏本身已含 -merge -diff -text，
# 再写 merge=binary 反而把宏设的 -merge 覆盖成一个不存在的驱动名。
frontend/e2e/visual/__snapshots__/**/*.png binary
```

判据实测依据（保留在案）：同样的 CRLF 字节，在 `__snapshots__` 下的 `.txt` 会被工作树规范化改写，
在同目录的 `.png` 上 `git hash-object` 结果与原始字节一致；临时注释掉这条规则后该保护消失、恢复后
哈希逐字节复原，证明这条规则**本身在承重**，不是被别的规则顺带覆盖。

- [ ] **Step 3: 验证规则命中**

Run: `mkdir -p frontend/e2e/visual/__snapshots__/smoke && touch frontend/e2e/visual/__snapshots__/smoke/a-dark.png frontend/e2e/visual/__snapshots__/smoke/a-light.png frontend/test-results/junk.txt && git check-attr text merge -- frontend/e2e/visual/__snapshots__/smoke/a-dark.png && git check-ignore frontend/test-results/junk.txt && rm -rf frontend/e2e/visual/__snapshots__/smoke frontend/test-results`
Expected: `a-dark.png` 报 `text: unset` / `merge: binary`；`git check-ignore` 回显 `frontend/test-results/junk.txt`。任一不符即规则写错，改正后重跑。

- [ ] **Step 4: 提交**

```bash
git add frontend/.gitignore .gitattributes
git commit -m "build(visual): 基线 PNG 标 binary、临时产物加 ignore，防跨平台整图假差异"
```

---

### Task 3: 会话夹具（每角色登录一次，storageState 复用）

`loginAs` 每次都要过真实登录与 IP 限流。24 次导航若各自登录，既慢又易撞 429（`test-helpers.ts:169-181` 记的就是这个坑）。用 globalSetup 登录 3 次、落 3 份 storageState。

**Files:**
- Create: `frontend/e2e/visual/global-setup.ts`
- Modify: `frontend/playwright.visual.ts`（加 `globalSetup`）
- Test: `frontend/e2e/visual/smoke.spec.ts`（追加会话有效性用例）

**Interfaces:**
- Consumes: `loginAs(page, role)` from `e2e/test-helpers.ts`；`storagePathFor` from Task 1 的 `visual-helpers.ts`（**只导入，不再定义一份**）
- Produces: `frontend/e2e/visual/.auth/{student,teacher,admin}.json` 三份会话夹具（不入 git）

- [ ] **Step 1: 写失败测试**

追加到 `frontend/e2e/visual/smoke.spec.ts`:

```ts
// 追加到 smoke.spec.ts 时**必须合并进文件顶部已有的 import**——
// 原样另起一段 `import { test as base, expect }` 会与既有 `import { expect, test }` 撞名，
// 报 `SyntaxError: Identifier 'expect' has already been declared`（Task 3 实测），
// 且 setTheme 同理重复。正确做法：把顶部改成
//   `import { test, expect, test as base } from '@playwright/test'`（或直接复用 test）
//   `import { freezeClock, setTheme, settlePage, storagePathFor } from './visual-helpers'`
// 然后只追加下面这段 describe。
import { test as base, expect } from '@playwright/test'
import { freezeClock, setTheme, settlePage, storagePathFor } from './visual-helpers'

// 会话必须真的可用：auth 加固后，未登录访问受限页会被守卫踢回
// `router/index.ts:598` 的 `next({ path: '/login', query: { redirect: to.fullPath } })`。
// **判据必须是 pathname 相等，不能是 toContain('/admin/dashboard')**——
// 被踢回时的 URL 是 `/login?redirect=/admin/dashboard`，`toContain` 照样命中，
// 空夹具也能通过（Task 3 用"清空夹具"作反向对照实测过：toContain 那行 PASS，只有 pathname 断言 FAIL）。
// 侧边栏 `nav[aria-label="主导航"]` 是 AppShell 才渲染的 landmark（D2-7 第五批），作第二道证明。
base.describe('会话夹具', () => {
  base.use({ storageState: storagePathFor('ADMIN') })
  base('admin 夹具可直达受限工作台', async ({ page }) => {
    await freezeClock(page)
    await setTheme(page, 'light')
    await page.goto('/admin/dashboard')
    await settlePage(page)
    expect(new URL(page.url()).pathname).toBe('/admin/dashboard')
    await expect(page.locator('nav[aria-label="主导航"]')).toBeVisible()
  })
})
```

- [ ] **Step 2: 跑测试确认失败**

Run: `PW_PORT=5175 npx playwright test -c playwright.visual.ts -g "admin 夹具"`
Expected: FAIL——`Error: EACCES/ENOENT ... e2e/visual/.auth/admin.json`（夹具文件还不存在）。

- [ ] **Step 3: 写 globalSetup**

Create `frontend/e2e/visual/global-setup.ts`:

```ts
import { chromium } from '@playwright/test'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'
// ts 扩展名导入：与既有 spec 的相对导入风格一致（e2e/*.spec.ts 均 import from './test-helpers'）
import { loginAs } from '../test-helpers'
// storagePathFor 由 Task 1 定义在 visual-helpers.ts，此处只导入不复定义——
// 两份定义会让 spec 与 setup 写到不同路径，夹具"存在但读不到"。
import { storagePathFor } from './visual-helpers'

const ROLES = ['STUDENT', 'TEACHER', 'ADMIN'] as const
// 不能用 `join(__dirname, '.auth')`：frontend/package.json 是 `"type": "module"`，
// Playwright 的 requireOrImport 走 `import()` 分支，ESM 下没有 `__dirname`，
// 直接 `ReferenceError: __dirname is not defined in ES module scope`（Task 3 实测）。
// 也不用 `import.meta.url`：spec 侧读夹具的路径是 cwd 相对的，两边会分叉成两个路径。
// 统一以 cwd 为基准——globalSetup 与 worker 都继承同一 cwd（Playwright fork 不设 cwd、
// runner 里也没有 process.chdir），所以这不是"碰巧可用"，是结构性一致。
const AUTH_DIR = join(process.cwd(), 'e2e', 'visual', '.auth')

/**
 * 登录三次而非 24 次。两个理由都是实测出来的：
 * ①loginAs 每次都要点登录、等 /api/auth/login 响应、再关首次引导层；
 * ②后端 ratelimit.login 默认 10 次/60 秒且 429 不打日志（test-helpers.ts:169-171），
 *   多登录 = 高概率撞限流 = 失败集合随跑测快慢漂移，读到的红灯不是产品缺陷。
 * 引导浮层是按账号写的（键含 :<userId>），所以这里必须真实点掉，
 * 落进 storageState 的是"已看过"状态，后续导航不再弹出。
 */
export default async function globalSetup() {
  mkdirSync(AUTH_DIR, { recursive: true })
  const browser = await chromium.launch()
  try {
    for (const role of ROLES) {
      const ctx = await browser.newContext({ baseURL: process.env.PW_BASE_URL || 'http://localhost:5175' })
      const page = await ctx.newPage()
      await loginAs(page, role)
      // 落盘路径必须与 storagePathFor 完全一致（相对 cwd），否则 spec 侧 ENOENT
      await ctx.storageState({ path: join(process.cwd(), storagePathFor(role)) })
      await ctx.close()
    }
  } finally {
    await browser.close()
  }
}
```

- [ ] **Step 4: 挂进 config**

Modify `frontend/playwright.visual.ts` — 在 `defineConfig({` 后第一行加入：

```ts
  globalSetup: './e2e/visual/global-setup.ts',
```

`storagePathFor` 已在 Task 1 定义于 `visual-helpers.ts`、返回相对 `frontend/` 的路径串；
`global-setup.ts` 落盘时须 `join(process.cwd(), storagePathFor(role))` 与之对齐。
跑测 cwd 必须是 `frontend/`（`webServer.command` 用的就是 `npm run dev`），否则相对路径两边都指错。

- [ ] **Step 5: 跑测试确认通过**

Run: `PW_PORT=5175 npx playwright test -c playwright.visual.ts -g "admin 夹具"`
Expected: PASS。若报 `/403`，先查 `loginAs` 是否因限流失败（按前置条件 2 放宽 `RATELIMIT_LOGIN_LIMIT`）；若报引导层拦截点击，说明 `dismissOnboarding` 未命中账号键，查 `OnboardingGuide.vue` 的键格式而非改测试。

- [ ] **Step 6: 确认夹具文件不入 git（先验，再决定要不要写）**

**不要**直接 `echo "frontend/e2e/visual/.auth/" >> frontend/.gitignore`——`frontend/.gitignore` 里的模式
是**相对 `frontend/` 解析**的，那样写指向的是 `frontend/frontend/e2e/...`，永远不会命中（Task 2 实测：
隔离仓库里 `git check-ignore` 对这种写法返回 exit=1）。正确做法是先验现状：

Run: `cd frontend && git check-ignore -v e2e/visual/.auth/admin.json`
Expected: 命中一条规则（Task 2 已把 `e2e/visual/.auth/` 加进 `frontend/.gitignore`）。
若未命中，补 `e2e/visual/.auth/`（**不带** `frontend/` 前缀），再复验命中。
然后 `git status --short` 确认工作区不出现 `.auth/*.json`——这些文件含会话 token，入 git 即泄漏。

- [ ] **Step 7: 提交**

```bash
git add frontend/e2e/visual/global-setup.ts frontend/playwright.visual.ts frontend/e2e/visual/smoke.spec.ts frontend/.gitignore
git commit -m "test(visual): storageState 三角色会话夹具，把 24 次导航的登录压到 3 次避 429 假红灯"
```

---

### Task 4: 12 锚点 × 明暗 = 24 张基线建立

**Files:**
- Create: `frontend/e2e/visual/anchors.spec.ts`
- Create: `frontend/e2e/visual/__snapshots__/anchors.spec.ts/**.png`（24 张，由 `--update-snapshots` 生成）
- Modify: `frontend/e2e/visual/visual-helpers.ts`（补 `ANCHORS` 常量）

**Interfaces:**
- Consumes: Task 1 的 `setTheme` / `freezeClock` / `settlePage` / `snapName` / `maskOf` / `AnchorSpec`；Task 3 的 `storagePathFor`
- Produces: `ANCHORS`（12 项，Task 5 与 Phase 1–6 全部视觉单元引用同一份）；24 张已人工核验的基线

- [ ] **Step 1: 定义 12 锚点**

追加到 `frontend/e2e/visual/visual-helpers.ts`（路径取自 `src/router/index.ts` 实测行号）：

```ts
/**
 * 12 个锚点是 spec §4 Phase 0a 选定的"每一类版式的代表"，不是随便 12 页：
 * 工作台 ×3 端（卡片+统计+图表混排）、表格页代表（54 个文件在用 el-table）、
 * 表单页代表、Modal 复用代表（12 个 ui/Modal 调用点之一）、图谱/图表代表各一、
 * 个人中心、错误页。LearningCenter 需 :id，故不在这里写死数字——种子库里
 * 学生的第 1 门课未必存在，硬编 /student/courses/1 会截出一张空态图当基线，
 * 之后所有真实改版都被判为"改善"。改为运行时取列表首条真实链接。
 */
export const ANCHORS: readonly AnchorSpec[] = [
  { slug: 'login', title: '登录页', role: 'ANON', path: '/login' },
  { slug: 'admin-dashboard', title: '管理工作台', role: 'ADMIN', path: '/admin/dashboard' },
  { slug: 'student-dashboard', title: '学生工作台', role: 'STUDENT', path: '/student/dashboard' },
  { slug: 'teacher-dashboard', title: '教师工作台', role: 'TEACHER', path: '/teacher/dashboard' },
  { slug: 'admin-courses-table', title: '课程管理（表格代表）', role: 'ADMIN', path: '/admin/courses' },
  { slug: 'admin-system-form', title: '系统设置（表单代表）', role: 'ADMIN', path: '/admin/system' },
  { slug: 'student-courses-modal', title: '学生课程（弹层代表）', role: 'STUDENT', path: '/student/courses' },
  { slug: 'student-learning-center', title: '学习中心（壳页代表）', role: 'STUDENT', path: '__FROM_COURSE_LIST__' },
  { slug: 'student-knowledge-graph', title: '知识图谱（图谱代表）', role: 'STUDENT', path: '/student/knowledge-graph' },
  { slug: 'admin-governance-analytics', title: '治理分析（图表代表）', role: 'ADMIN', path: '/admin/governance/analytics' },
  { slug: 'admin-profile', title: '个人中心', role: 'ADMIN', path: '/admin/profile' },
  { slug: 'error-404', title: '404', role: 'ANON', path: '/__no-such-route__' },
]
```

- [ ] **Step 2: 写规格（含运行时路径解析）**

Create `frontend/e2e/visual/anchors.spec.ts`:

```ts
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

/**
 * 学生 /student/courses/:id/learning-center 的 :id 必须来自当前种子数据的真实列表首项，
 * 且排序口径固定——否则"取第一条"在不同环境下取到不同课程，图就不该相等。
 * 取 DOM 里第一个 course 卡链接，解析出 id。
 */
async function resolvePath(page: Page, anchor: AnchorSpec): Promise<string> {
  // 注意：本函数**不得**调 settlePage。settlePage 每次都会 runFor(5000) 推进页面时钟，
  // 若这里也调一次，__FROM_COURSE_LIST__ 锚点的截图时刻就变成 09:00:20+，与其他 23 张不同基，
  // 而重跑时"课程列表要几轮才稳定"又会让轮数漂移——等于专门给这一页装了个随机源。
  // 列表页的 DOM 只需已渲染，用 locator 等待即可。
  if (anchor.path !== '__FROM_COURSE_LIST__') return anchor.path
  await page.locator('a[href*="/student/courses/"], [data-course-id]').first()
    .waitFor({ state: 'attached', timeout: 15000 })
  const href = await page
    .locator('a[href*="/student/courses/"], [data-course-id]')
    .first()
    .evaluate((el) =>
      'href' in el ? (el as HTMLAnchorElement).getAttribute('href') ?? '' : `/${(el as HTMLElement).dataset.courseId}`,
    )
  const id = href.match(/\/student\/courses\/(\d+)/)?.[1]
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
  await freezeClock(page)
  const target = await resolvePath(page, anchor)
  await page.goto(target)
  await settlePage(page)

  if (anchor.role !== 'ANON') {
    // 会话真在用的证据：没被守卫踢回 /login（router/index.ts:598 的 next('/login')）
    expect(page.url()).not.toContain('/login')
  }
  // 主题注入必须真的生效——否则 24 张里有 12 张是同一张图换了名字，
  // 基线会"通过"但从未验证过暗色（spec §6.6 明令禁止的现象）。
  await expect(page.locator('html')).toHaveAttribute('data-theme', theme)
  // 空壳页不得当基线：SPA 白屏 / 报错边界都会截出一张干净的图。
  await expect(page.locator('#app')).not.toBeEmpty()
  // 根类实测自 src/components/AppErrorBoundary.vue:2 —— 是 `.error-boundary`，
  // 不是 `.app-error-boundary`，全仓也没有 data-test="error-state"。写错的选择数会让这层防护静默失效。
  await expect(page.locator('.error-boundary')).toHaveCount(0)

  // 扩展名必须在 arg 上（见 snapName 注释的 1.63 实测机制），模板的 {ext} 是从它反推的。
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
```

- [ ] **Step 3: 首跑生成基线**

Run: `PW_PORT=5175 npx playwright test -c playwright.visual.ts --update-snapshots`
Expected: 24 passed，`frontend/e2e/visual/__snapshots__/anchors.spec.ts/` 下出现 24 个 PNG。
若某锚点 FAIL 且报"空壳页"或 404，先判是**页面真的坏**还是**选择器/路径错**——前者是真实缺陷，记入 LEDGER 并另开修页任务，**不许**用 `--update-snapshots` 把它吸收成基线。

- [ ] **Step 4: 二次跑确认自洽（关键，不能跳）**

Run: `PW_PORT=5175 npx playwright test -c playwright.visual.ts anchors --repeat-each=2`
Expected: 48 passed，**零 diff**。`--repeat-each=2` 是刻意加的：Task 1 的"先等网络空闲再推时钟"这个顺序
（评审 finding #8）没有自己的测试——它能被间接证明的唯一方式，是让同一页在同一进程里连跑两次还稳定。
若某锚点跑出 diff，说明某个动态源还没被钉住（常见：图片懒加载、WebSocket 通知计数、
`Math.random()` 生成的图谱布局）。逐个定位后补进 `freezeClock` / `maskSelectors`，**不得**靠调大
`maxDiffPixelRatio` 掩盖——那等于关掉这层防护。
截图时刻的页面时间 = **09:00:10.000**（`freezeClock` 停在 +5000，`settlePage` 再推 5000）；
若某页断言或显示绝对时间，按此值核对，不要按 09:00:00。

- [ ] **Step 5: 逐张人工核验并记录（24 张全看，不抽样）**

这一步是基线合法性的唯一来源。要看的不是"好不好看"，而是三件事：
1. 内容不是空态/骨架屏残留；
2. 明暗两套各自可读（暗色下没有一片糊住的低对比区）；
3. 图表真的画出来了（canvas 非空白）。

任一张不合格 → 回 Step 3 前先把根因解决。把每张的结论写进 LEDGER（Task 5 建表，此处先备好结论清单文本）。

- [ ] **Step 6: 提交基线**

```bash
git add frontend/e2e/visual/anchors.spec.ts frontend/e2e/visual/visual-helpers.ts frontend/e2e/visual/__snapshots__
git commit -m "test(visual): 12 锚点×明暗 24 张基线入库，二次跑零 diff 证明时钟/主题/稳定态三项都钉住了"
```

---

### Task 5: 台账 LEDGER.md 落地，用 Phase 0 自身当首批真实行

台账必须先于改动存在，否则"改前截图"字段无处可取。首批行就记 Phase 0 自己的单元——顺带验证协议真的写得下去，而不是一份好看但没人填的模板。

**Files:**
- Create: `docs/visual-upgrade/LEDGER.md`

**Interfaces:**
- Consumes: Task 4 的 24 张基线路径
- Produces: `LEDGER.md` 表结构与"单元 ID 命名法"，Phase 1–6 每行都必须沿用

- [ ] **Step 1: 建表**

Create `docs/visual-upgrade/LEDGER.md`:

```markdown
# 前端视觉升级 · 改动台账（唯一）

协议见 `docs/superpowers/specs/2026-10-18-frontend-visual-upgrade-design.md` §5。
追加式，不拆多份。改基线用 `PW_PORT=5175 npx playwright test -c playwright.visual.ts --update-snapshots`，
且仅在上一行结论为「保留」时执行。

## 判定字段口径

- **必要性**：不改会怎样。写具体后果，不写"提升观感"。
- **到位**：是否命中 spec §3 的三条根因之一（层次失效 / 圆角阶断裂 / 无生气），还是只在表层涂抹。
- **有效性**：有机器数值（对比度、alpha、radius 阶、覆盖率、diff %）或明确的主观标注，二者不得互冒充。
- **结论**：`保留` / `回退` / `需调`。

## 表

| ID | 改前（截图/数值） | 操作（file:line + 理由） | 改后（截图/数值） | 必要性 | 到位 | 有效性 | 结论 | 验证 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
```

- [ ] **Step 2: 写入 Phase 0 首批行**

在表下追加（数值须替换为本轮实测，不得留空）：

```markdown
| P0a-HARNESS-01 | 视觉基线 0 张（全库 `toHaveScreenshot` 命中 0） | 新增 `playwright.visual.ts` + `e2e/visual/`：独立 config、时钟冻结、稳定态轮询。理由＝spec §5 要求"改前截图"，此前无处可取 | 24 张（12 锚点 × 明暗），`frontend/e2e/visual/__snapshots__/anchors.spec.ts/` | 不建则 Phase 1–6 全部改动无痕可言 | 命中"留痕缺口"而非表层观感 | 二次跑 diff 像素比 __实测__ %；机器判 | 保留 | lint/typecheck/test/build/size:gate/visual 全绿 |
| P0b-MODAL-01 | `ui/Modal` 无 Teleport（`Modal.vue:19` 根为 `.overlay`） | 待 Phase 0b Task 8 回填 | 待回填 | 待回填 | 待回填 | 待回填 | 待回填 | 待回填 |
```

`__实测__` 是**必须在提交前替换掉的占位**：跑 `PW_PORT=5175 npx playwright test -c playwright.visual.ts` 后把实际 diff 比填入。留占位即视为该任务未完成。

- [ ] **Step 3: 确认表头与列数一致**

Run: `cd "C:/Users/ASUS/Desktop/大创赛/ai-course-platform" && awk -F'|' '/^\|/ {print NF-2": "substr($0,1,40)}' docs/visual-upgrade/LEDGER.md`
Expected: 每一行的列数都等于 9（表头、分隔行、两条数据行一致）。不一致即表格被内容里的 `|` 撑破，改用 `\|` 转义。

- [ ] **Step 4: 提交**

```bash
git add docs/visual-upgrade/LEDGER.md
git commit -m "docs(visual): LEDGER 唯一台账落地，首批行以 Phase 0 自身验证协议可填"
```

---

### Task 6: 12 个存量 Modal 调用点的行为钉测试（加能力之前先上锁）

给 `Modal` 加 v-model / Teleport / 焦点管理之前，必须先有测试能证明"老用法还活着"。否则兼容性是猜测。

**Files:**
- Create: `frontend/src/components/ui/__tests__/Modal.compat.test.ts`

**Interfaces:**
- Consumes: `Modal.vue` 现状——props 无（读 `$attrs.wide/xwide/narrow`）、`emits: ['close']`、具名 slot `title` / `sub` / `footer` / 默认
- Produces: 该测试文件成为 Phase 4 每批迁移前的"存量不破"门禁（同文件追加断言，不另开）

- [ ] **Step 1: 写钉测试**

Create `frontend/src/components/ui/__tests__/Modal.compat.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import Modal from '../Modal.vue'

// 12 个调用点的实际用法抽成三类：尺寸档、@close、插槽有无。
// 这一组测试在 Phase 0b 开始前必须全绿——它们钉的是"现状行为"，
// 一旦后续加能力把它们弄红，就说明改的是不兼容而非纯增强。
function factory(
  props: Record<string, unknown> = {},
  slots: Record<string, string> = {},
  attrs: Record<string, string> = {},
) {
  return mount(Modal, {
    props,
    attrs,
    slots: { title: '标题', ...slots },
    attachTo: document.body,
  })
}

describe('Modal 现状兼容钉', () => {
  it('默认宽度 640px 档（无修饰 prop）', () => {
    const w = factory().find('.modal')
    expect(w.classes()).not.toContain('wide')
    expect(w.classes()).not.toContain('narrow')
    expect(w.classes()).not.toContain('xwide')
  })

  it.each(['wide', 'xwide', 'narrow'])('%s 走 $attrs 布尔通道', async (size) => {
    // 用 attrs 而非 props：现状 Modal.vue 是从 $attrs 读尺寸档（Modal.vue:22-26），
    // 组件并未声明这些 props；用 props 传未声明键在 VTU 下的落点与 attrs 不同。
    const wrapper = factory({}, {}, { [size]: '' })
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.modal').classes()).toContain(size)
  })

  it('点击关闭按钮 emit close', async () => {
    const wrapper = factory()
    await wrapper.find('.modal-head .t-icon-btn').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('Esc emit close', async () => {
    const wrapper = factory()
    await wrapper.vm.$nextTick()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
  })

  it('按下遮罩本体 emit close；按下内容区不 emit', async () => {
    // jsdom 下 trigger('mousedown') 令 e.target === 该元素本体，故 .self 守卫在遮罩上成立
    const content = factory()
    await content.find('.modal').trigger('mousedown')
    expect(content.emitted('close')).toBeUndefined()
    content.unmount()

    const overlay = factory()
    await overlay.find('.overlay').trigger('mousedown')
    expect(overlay.emitted('close')).toHaveLength(1)
    overlay.unmount()
  })

  it('title 插槽渲染、sub 无则不渲染、footer 无则不渲染', () => {
    const wrapper = factory()
    expect(wrapper.find('.modal-title').text()).toBe('标题')
    expect(wrapper.find('.modal-sub').exists()).toBe(false)
    expect(wrapper.find('.modal-foot').exists()).toBe(false)
  })

  it('foot 与 sub 提供时渲染', () => {
    const wrapper = factory({}, { sub: '副标', footer: '<button>确定</button>' })
    expect(wrapper.find('.modal-sub').text()).toBe('副标')
    expect(wrapper.find('.modal-foot button').text()).toBe('确定')
  })

  it('卸载后 Esc 不再 emit（keydown 监听必须被清理）', async () => {
    const wrapper = factory()
    await wrapper.vm.$nextTick()
    wrapper.unmount()
    // 卸载后 wrapper 不再记录新事件，故观察点必须是"卸载前就持有的 emitted() 数组引用"
    const emittedBeforeUnmount = wrapper.emitted('close')
    const lenAtUnmount = emittedBeforeUnmount?.length ?? 0
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await new Promise((r) => setTimeout(r, 0))
    // 监听若未 removeEventListener（Modal.vue:8 的 onBeforeUnmount），残留 handler 会对
    // 已卸载实例再次触发 emit；Vue 仍会把事件记进 emitted 数组，长度因此增长。
    expect(wrapper.emitted('close')?.length ?? 0).toBe(lenAtUnmount)
  })
})
```

- [ ] **Step 2: 跑测试确认全绿（这是"钉现状"，不是红→绿循环）**

Run: `npx vitest run src/components/ui/__tests__/Modal.compat.test.ts`
Expected: PASS。
若 `$attrs` 尺寸档那条红——说明 `wide` 传 `''` 时 `$attrs.wide === ''` 分支与 `mount` 的 props 传递方式不匹配，需改用 `attrs: { wide: '' }`。按实际报错调整 factory，**不要**改 `Modal.vue` 来迁就测试。

- [ ] **Step 3: 全链验证并提交**

```bash
npx eslint src/components/ui/__tests__/Modal.compat.test.ts
npm run typecheck && npm run test && npm run build && npm run size:gate
git add frontend/src/components/ui/__tests__/Modal.compat.test.ts
git commit -m "test(ui): 钉住 Modal 12 个存量调用点的现状行为，加能力前先上兼容锁"
```

---

### Task 7: `Modal` 加 `v-model` 双通道

`emits` 需扩为 `['close', 'update:modelValue']`，`props.modelValue?: boolean` 为可选——**缺省时行为必须与今天完全一致**，这样 12 个老调用点零改动即通过 Task 6 的钉测试。

**Files:**
- Modify: `frontend/src/components/ui/Modal.vue`
- Test: `frontend/src/components/ui/__tests__/Modal.vmodel.test.ts`

**Interfaces:**
- Consumes: Task 6 的兼容钉（不得弄红）
- Produces: `props.modelValue?: boolean`（true 时渲染，false 时不渲染）、`emit('update:modelValue', false)` 与既有 `emit('close')` 同时触发；Phase 4 迁移点据此可写 `v-model="visible"` 或保留 `@close`

- [ ] **Step 1: 写失败测试**

Create `frontend/src/components/ui/__tests__/Modal.vmodel.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import Modal from '../Modal.vue'

describe('Modal v-model 通道', () => {
  it('modelValue=false 时不渲染遮罩', () => {
    const wrapper = mount(Modal, { props: { modelValue: false }, slots: { title: 'T' }, attachTo: document.body })
    expect(wrapper.find('.overlay').exists()).toBe(false)
  })

  it('modelValue=true 时渲染', () => {
    const wrapper = mount(Modal, { props: { modelValue: true }, slots: { title: 'T' }, attachTo: document.body })
    expect(wrapper.find('.overlay').exists()).toBe(true)
  })

  it('关闭时同时 emit close 与 update:modelValue(false)，两通道都通', async () => {
    const wrapper = mount(Modal, { props: { modelValue: true }, slots: { title: 'T' }, attachTo: document.body })
    await wrapper.find('.modal-head .t-icon-btn').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
  })

  it('不传 modelValue 时仍渲染（12 个老调用点用 v-if 自行控制可见性）', () => {
    const wrapper = mount(Modal, { slots: { title: 'T' }, attachTo: document.body })
    expect(wrapper.find('.overlay').exists()).toBe(true)
  })
})
```

- [ ] **Step 2: 跑测试确认失败**

Run: `npx vitest run src/components/ui/__tests__/Modal.vmodel.test.ts`
Expected: FAIL——前两条 `overlay` 仍渲染（因为现在无 `modelValue` 门），第三条 `update:modelValue` 为 undefined。

- [ ] **Step 3: 实现**

Modify `frontend/src/components/ui/Modal.vue` 的 `<script setup>`，替换为：

```ts
import { computed, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps<{ modelValue?: boolean }>()
const emit = defineEmits<{ close: []; 'update:modelValue': [v: boolean] }>()

// 缺省视为 true：12 个存量调用点都是外层自己 v-if 控制可见性，
// 若把 undefined 当 false，全部老页面一上来就空白。
const open = computed(() => props.modelValue !== false)

function onEsc(e: KeyboardEvent) {
  if (e.key === 'Escape') close()
}
function close() {
  emit('close')
  if (props.modelValue === true) emit('update:modelValue', false)
}
function onOverlay(e: MouseEvent) {
  if (e.target === e.currentTarget) close()
}
onMounted(() => document.addEventListener('keydown', onEsc))
onBeforeUnmount(() => document.removeEventListener('keydown', onEsc))
```

模板根改为条件渲染：

```html
<Teleport to="body">
  <div v-if="open" class="overlay" @mousedown.self="onOverlay">
    <!-- …既有内容不变… -->
  </div>
</Teleport>
```

> `Teleport` 一并在本节引入：`v-if` 的挂载时机若不加 Teleport，下一个任务就要把 `v-if` 再挪一次位置。两者同处一片模板，分开改反而更易出错。Teleport 的独立验证见 Task 8。

- [ ] **Step 4: 跑测试确认通过（含 Task 6 的兼容钉仍绿）**

Run: `npx vitest run src/components/ui/__tests__/Modal.vmodel.test.ts src/components/ui/__tests__/Modal.compat.test.ts`
Expected: 两个文件全 PASS。compat 一旦转红，说明 `v-if` 或 Teleport 破坏了现状语义，回退本步重做。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/components/ui/Modal.vue frontend/src/components/ui/__tests__/Modal.vmodel.test.ts
git commit -m "feat(ui): Modal 增 v-model 双通道，缺省视为 true 保 12 个存量调用点不破"
```

---

### Task 8: Teleport 落地并钉死 spec §2.6 的包含块陷阱

spec §2.6 记录：`.animate-in` 的 `animation: rise-in ... both`（`design-system.css:1358-1359`）会保留 `transform: translateY(0)`，成为 `position: fixed` 后代的包含块。实测三例当前为兄弟级、未爆。本任务把它**钉成测试**，防 Phase 4 扩量后爆发。

**已核定的实现路径（不留二选一给执行者）**：Vue 3.5 的 `TeleportProps` 为 `{ to, disabled?, defer? }`
（证据：`frontend/node_modules/@vue/runtime-core/dist/runtime-core.d.ts:1136-1140`）。
故用**单个** `<Teleport to="body" :disabled="!toBody">` 即可覆盖 teleported 开关，
不需要并列两条分支、不复制 DOM，也因此**不必**抽出 `ModalPanel.vue`——那层拆分在 `:disabled` 面前没有理由存在。

**Files:**
- Modify: `frontend/src/components/ui/Modal.vue`
- Modify: `frontend/src/components/ui/__tests__/Modal.compat.test.ts`、`Modal.vmodel.test.ts`（查询路径随 Teleport 迁移）
- Test: `frontend/src/components/ui/__tests__/Modal.teleport.test.ts`

**Interfaces:**
- Consumes: Task 7 已加好的 `<Teleport to="body">`
- Produces: `Modal` 的遮罩默认渲染在 `document.body` 下；`teleported?: boolean` prop（默认 `true`，置 `false` 时原位渲染，供极少数需继承层叠上下文的场景）。**Modal 组件文件仍只有一个**，Phase 4 迁移点的 `<Modal …>` 写法不变。

- [ ] **Step 1: 写失败测试**

Create `frontend/src/components/ui/__tests__/Modal.teleport.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import Modal from '../Modal.vue'

// 父容器带 transform —— 复现 .animate-in 的真实条件（rise-in 以 both 填充，
// 计算后 transform 非 none）。若不 Teleport，遮罩会被困在这个 300px 高的盒子里，
// 表现为"弹窗缩在卡片中间"，且 jsdom 不会报错——只有结构断言抓得到。
const Host = defineComponent({
  setup() {
    return () =>
      h(
        'div',
        { style: 'transform: translateY(0px); height: 300px; overflow: hidden' },
        [h(Modal, { modelValue: true }, { title: () => '标题' })],
      )
  },
})

describe('Modal Teleport 行为', () => {
  it('遮罩渲染到 body 下，不在带 transform 的父容器内', async () => {
    const wrapper = mount(Host, { attachTo: document.body })
    await nextTick()
    const overlay = document.body.querySelector('.overlay')
    expect(overlay).not.toBeNull()
    expect(overlay?.parentElement).toBe(document.body)
    expect(overlay?.closest('[style*="transform"]')).toBeNull()
    wrapper.unmount()
  })

  it('teleported=false 时退回原位（保留逃生门，默认仍 teleport）', async () => {
    const Inner = defineComponent({
      setup() {
        return () =>
          h('div', { class: 'host-box' }, [h(Modal, { modelValue: true, teleported: false }, { title: () => 'T' })])
      },
    })
    const wrapper = mount(Inner, { attachTo: document.body })
    await nextTick()
    expect(wrapper.find('.host-box .overlay').exists()).toBe(true)
    wrapper.unmount()
  })

  it('卸载后 body 上不残留遮罩节点', async () => {
    const wrapper = mount(Host, { attachTo: document.body })
    await nextTick()
    expect(document.body.querySelector('.overlay')).not.toBeNull()
    wrapper.unmount()
    await nextTick()
    expect(document.body.querySelector('.overlay')).toBeNull()
  })
})
```

- [ ] **Step 2: 跑测试确认失败**

Run: `npx vitest run src/components/ui/__tests__/Modal.teleport.test.ts`
Expected: FAIL——`teleported` prop 未定义（第二条）；第一条可能已因 Task 7 的 `<Teleport to="body">` 通过，那是预期的部分完成，不判为异常。

- [ ] **Step 3: 实现 `teleported` 开关（单一路径，不复制 DOM）**

Modify `frontend/src/components/ui/Modal.vue` — `<script setup>` 的 props 与派生值：

```ts
const props = defineProps<{ modelValue?: boolean; teleported?: boolean }>()
// 缺省视为 true：Phase 4 的 65 个迁移点不该被迫多写一个 prop 才拿到正确行为。
const toBody = computed(() => props.teleported !== false)
```

模板收成单个 Teleport，`:disabled` 关掉时原位渲染：

```html
<Teleport to="body" :disabled="!toBody">
  <div v-if="open" class="overlay" @mousedown.self="onOverlay">
    <div class="modal" :class="sizeClass" @click.stop>
      <!-- 既有 modal-head / modal-body / modal-foot 结构原样保留，不做任何抽取 -->
    </div>
  </div>
</Teleport>
```

`sizeClass` 是把原先内联的 `$attrs` 三档判断收进 `<script>`（Teleport 内层用 `$attrs` 读尺寸档仍可行，但显式 computed 更好读、也让 `data-test` 之类透传属性继续落在 `.modal` 上）：

```ts
const sizeClass = computed(() => ({
  wide: props.wide === '' || props.wide === true,
  xwide: props.xwide === '' || props.xwide === true,
  narrow: props.narrow === '' || props.narrow === true,
}))
```

对应地把 `wide` / `xwide` / `narrow` 声明进 props（`Boolean` 与 `String` 联合，容 `''` 空值写法）：

```ts
const props = defineProps<{
  modelValue?: boolean
  teleported?: boolean
  wide?: boolean | string
  xwide?: boolean | string
  narrow?: boolean | string
}>()
```

> **这一步会把 `$attrs` 布尔通道改成显式 props，是行为敏感的改动**：显式声明后 `wide` 不再出现在 `$attrs` 里、也就不会再作为属性落到根 DOM 节点上。Task 6 的 `$attrs` 尺寸档断言必须同步改为断言 `.modal` 的 class（而不是 `$attrs`），且这是**查询路径变更、不是行为变更**——逐条确认原断言意图仍是"该档位生效了"。若发现有调用点依赖 `getAttribute('wide')`（实测 grep 应为零），则改回保留 `$attrs` 的写法并在此注明。

- [ ] **Step 4: 把 compat / vmodel 两组测试的查询路径迁到 body**

Teleport 后 `wrapper.find('.modal')` 取不到节点（内容已不在 wrapper 的元素树里）。这是预期的连带影响，**不是**兼容锁失败：

```bash
grep -n "wrapper.find('\.\(modal\|overlay\)" src/components/ui/__tests__/Modal.compat.test.ts src/components/ui/__tests__/Modal.vmodel.test.ts
```

把命中的每一处从 `wrapper.find('.modal')` 改为 `document.body.querySelector('.modal')`（配 `attachTo: document.body`，两组测试已具备），并在用例末尾 `wrapper.unmount()` 防节点跨用例残留。**每条都要确认原断言意图不变**——若某条原意是"内容在 wrapper 内"，则该意图已被 Teleport 主动推翻，须在 LEDGER 的 `P0b-MODAL-01` 行写明，不许静默改绿。

- [ ] **Step 5: 跑测试确认通过**

Run: `npx vitest run src/components/ui/`
Expected: `Modal.compat` / `Modal.vmodel` / `Modal.teleport` 全 PASS。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/components/ui/Modal.vue frontend/src/components/ui/__tests__/
git commit -m "feat(ui): Modal 以单一路径 Teleport（:disabled 开关），并复现 animate-in 的 transform 包含块陷阱为测试"
```

---

### Task 9: 焦点接入——先实测 `$el` 可达性，再选方案

spec §9.2 明写这条未实测、不得凭推断。`useDialogFocus.ts:50` 取 `dialogRef?.value?.$el`；`<script setup>` 组件**未 `defineExpose`** 时模板 ref 拿到的是 public instance proxy，其可见面有边界。本任务先用一个最小探针实验把结论钉死，再按结论实施。

**Files:**
- Test: `frontend/src/components/ui/__tests__/Modal.focus.test.ts`
- Modify: `frontend/src/components/ui/Modal.vue`

**Interfaces:**
- Consumes: `useDialogFocus(dialogRef, visible)` from `@/composables/useDialogFocus`；`DIALOG_FOCUSABLE_SELECTOR`（`useDialogFocus.ts:60` 导出，供测试与脚本核验）
- Produces: `Modal` 打开后焦点落在首个可聚焦控件；与 `el-dialog` 现有行为一致，Phase 4 迁移点因此可继续沿用 `ref="dialogRef"` + 原语，或交给 Modal 内部自持

- [ ] **Step 1: 先做可达性探针（不是断言，是把未知量测出来）**

Create `frontend/src/components/ui/__tests__/Modal.focus.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import Modal from '../Modal.vue'

// 探针：确认经模板 ref 能否拿到 $el。这是 spec §9.2 记为"未实测"的前提。
// 结论决定 Modal 内部接焦点的写法：拿得到就沿用 dialogRef.$el 口径（与 63 个
// el-dialog 完全同构，Phase 4 迁移点可原样保留 ref 用法）；拿不到就改为
// Modal 自持根引用并 defineExpose 一个同形的 $el getter。
describe('Modal 焦点接入前提', () => {
  it('模板 ref 能取到 $el 且即遮罩节点', async () => {
    const wrapper = mount(Modal, { props: { modelValue: true }, slots: { title: 'T' }, attachTo: document.body })
    await wrapper.vm.$nextTick()
    const el = (wrapper.vm as unknown as { $el?: HTMLElement }).$el
    expect(el).toBeDefined()
    expect(el?.nodeType).toBe(1)
  })
})
```

- [ ] **Step 2: 跑探针，按结果分叉（两条路都写清楚，不留 TBD）**

Run: `npx vitest run src/components/ui/__tests__/Modal.focus.test.ts`

**若 PASS**（`$el` 可达）→ 走 Step 3-A：沿用与 `el-dialog` 同构的 `dialogRef.$el` 口径。
**若 FAIL** → 走 Step 3-B：`Modal.vue` 自持 `rootRef` 并 `defineExpose` 同形 `$el` getter，`useDialogFocus` 改接 `{ $el: rootRef }` 形状。
把实测结果与所选分支写进 `LEDGER.md` 的 `P0b-MODAL-02` 行——这是 spec §9.2 要求消解的条目。

- [ ] **Step 3-A: 实现（`$el` 可达分支）**

Modify `frontend/src/components/ui/Modal.vue`：在 `<script setup>` 追加

```ts
import { useDialogFocus } from '@/composables/useDialogFocus'
import { computed, ref } from 'vue'

const rootRef = ref<HTMLElement | null>(null)

// 复用既有原语，不另写一套焦点逻辑——口径必须和那 63 个 el-dialog 完全一致，
// 否则 Phase 4 之后"覆盖率 100%"这句话的所指就变了。
// 原语签名要 Ref<{ $el?: HTMLElement }>，此处是裸 HTMLElement，故包一层同形适配。
const dialogRef = computed(() => ({ $el: rootRef.value ?? undefined }))
useDialogFocus(dialogRef, open)
```

模板的 `.modal` 元素挂 `ref="rootRef"`。注意 `open` 是 Task 7 已定义的 `computed(() => props.modelValue !== false)`——它 `immediate` 不触发，故首次打开由 `watch` 正常捕获；若 `mount` 时即 `modelValue: true`，`watch` 不带 `immediate` 不会执行，**这是 Step 4 第一条用例能测出来的真实时序缺口**，须据实测决定是否给 `useDialogFocus` 传 `open` 之外再补一次 `onMounted` 触发。

- [ ] **Step 3-B: 实现（`$el` 不可达分支）**

`Modal.vue` 内部仍用 Step 3-A 同一段（组件自持 `rootRef` + `dialogRef` 适配），另对外暴露等价面，使**调用点**的 `ref="dialogRef" + useDialogFocus(dialogRef, visible)` 写法在 `Modal` 上与 `el-dialog` 上等效：

```ts
defineExpose({ $el: computed(() => rootRef.value ?? undefined) })
```

> `defineExpose({ $el })` 会让原语读到的 `dialogRef.value.$el` 是一个 `ComputedRef` 而非 `HTMLElement`——
> 必须确认原语内部 `root.querySelector` 的调用对象仍是元素。以 Step 2 的探针结果为准选择暴露**已解包的值**
> （`get $el()` getter 形式）还是维持 3-A 的内部接线。写不出可解包形式即判 3-B 不成立，回到 3-A。

- [ ] **Step 4: 写行为断言**

- [ ] **Step 4: 写行为断言**

追加到 `frontend/src/components/ui/__tests__/Modal.focus.test.ts`:

```ts
import { DIALOG_FOCUSABLE_SELECTOR } from '@/composables/useDialogFocus'

it('打开后焦点进入首个可聚焦控件', async () => {
  const wrapper = mount(Modal, {
    props: { modelValue: true },
    slots: { title: 'T', default: '<input data-testid="first" /><button>后</button>' },
    attachTo: document.body,
  })
  await wrapper.vm.$nextTick()
  // 原语用 nextTick + requestAnimationFrame 双等待（useDialogFocus.ts:47-55），jsdom 需手动推一帧
  await new Promise((r) => requestAnimationFrame(() => r(null)))
  await wrapper.vm.$nextTick()
  const active = document.activeElement
  expect(active?.tagName).toBe('INPUT')
  expect(document.querySelector(DIALOG_FOCUSABLE_SELECTOR)).toBe(active)
})

it('禁用首个控件时焦点跳过，落到下一个可聚焦元素', async () => {
  const wrapper = mount(Modal, {
    props: { modelValue: true },
    slots: { title: 'T', default: '<input disabled /><button data-testid="b">后</button>' },
    attachTo: document.body,
  })
  await wrapper.vm.$nextTick()
  await new Promise((r) => requestAnimationFrame(() => r(null)))
  await wrapper.vm.$nextTick()
  expect(document.activeElement?.tagName).toBe('BUTTON')
})
```

- [ ] **Step 5: 跑测试确认通过**

Run: `npx vitest run src/components/ui/`
Expected: 全 PASS。若"焦点进入"失败且 `activeElement` 是 `body`，检查 `requestAnimationFrame` 是否在 jsdom 下于 `nextTick` 之后执行；**不要**为了让它绿而在组件里加 `setTimeout`——那会把原语时序改掉，影响 63 个既有接线点。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/components/ui/Modal.vue frontend/src/components/ui/__tests__/Modal.focus.test.ts docs/visual-upgrade/LEDGER.md
git commit -m "feat(ui): Modal 复用 useDialogFocus 接焦点，先实测 \$el 可达性并消解 spec §9.2"
```

---

### Task 10: 滚动锁 + ARIA + 焦点返还 + 事件语义对齐

补齐 `el-dialog` 有而 `Modal` 没有的剩余能力：`role`/`aria-modal`/`aria-label`、body 滚动锁、关闭后焦点返还到打开前的元素、`@closed`（过渡结束后触发）、遮罩点击可关的开关（对应 `close-on-click-modal`，`el-dialog` 侧实测有 1 处在用）。

**Files:**
- Create: `frontend/src/composables/useOverlayScrollLock.ts`
- Modify: `frontend/src/components/ui/Modal.vue`
- Test: `frontend/src/components/ui/__tests__/Modal.a11y.test.ts`

**Interfaces:**
- Consumes: Task 7/8/9 的 `Modal` props
- Produces:
  - `useOverlayScrollLock(active: Ref<boolean>): void` — 进出计数式滚动锁（多个弹层叠加时，最后一个关闭才还原），供 `Modal` 与 Task 11 的 `Drawer` 共用
  - `Modal` 新增：`ariaLabel?: string`、`ariaLabelledby?: string`、`closeOnClickModal?: boolean`（默认 `true`）、`emit('closed')`

- [ ] **Step 1: 写失败测试**

Create `frontend/src/components/ui/__tests__/Modal.a11y.test.ts`:

```ts
import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import Modal from '../Modal.vue'

describe('Modal 可访问性与弹层语义', () => {
  beforeEach(() => {
    document.body.style.overflow = ''
    document.body.dataset.overlayCount = ''
  })

  it('带 role=dialog 与 aria-modal，标题经 aria-labelledby 关联', async () => {
    const wrapper = mount(Modal, { props: { modelValue: true }, slots: { title: '新建课程' }, attachTo: document.body })
    await wrapper.vm.$nextTick()
    const panel = document.body.querySelector('.modal')
    expect(panel?.getAttribute('role')).toBe('dialog')
    expect(panel?.getAttribute('aria-modal')).toBe('true')
    const labelId = panel?.getAttribute('aria-labelledby')
    expect(labelId).toBeTruthy()
    expect(document.getElementById(labelId!)?.textContent).toContain('新建课程')
  })

  it('打开时锁 body 滚动，关闭时还原', async () => {
    const wrapper = mount(Modal, { props: { modelValue: true }, attachTo: document.body })
    await wrapper.vm.$nextTick()
    expect(document.body.style.overflow).toBe('hidden')
    await wrapper.setProps({ modelValue: false })
    await wrapper.vm.$nextTick()
    expect(document.body.style.overflow).toBe('')
  })

  it('两个弹层叠加时，仅最后一个关闭才还原（引用计数，不是布尔）', async () => {
    const a = mount(Modal, { props: { modelValue: true }, attachTo: document.body })
    const b = mount(Modal, { props: { modelValue: true }, attachTo: document.body })
    await a.vm.$nextTick()
    await b.setProps({ modelValue: false })
    await b.vm.$nextTick()
    expect(document.body.style.overflow).toBe('hidden')
    await a.setProps({ modelValue: false })
    await a.vm.$nextTick()
    expect(document.body.style.overflow).toBe('')
  })

  it('关闭后焦点返还到打开前的元素', async () => {
    const trigger = document.createElement('button')
    document.body.append(trigger)
    trigger.focus()
    const wrapper = mount(Modal, { props: { modelValue: true }, attachTo: document.body })
    await wrapper.vm.$nextTick()
    await wrapper.find('.modal-head .t-icon-btn').trigger('click')
    await wrapper.setProps({ modelValue: false })
    await wrapper.vm.$nextTick()
    expect(document.activeElement).toBe(trigger)
    trigger.remove()
  })

  it('closeOnClickModal=false 时按下遮罩不关闭', async () => {
    const wrapper = mount(Modal, { props: { modelValue: true, closeOnClickModal: false }, attachTo: document.body })
    await wrapper.find('.overlay').trigger('mousedown')
    expect(wrapper.emitted('close')).toBeUndefined()
  })

  it('closed 在可见性转假之后触发', async () => {
    const wrapper = mount(Modal, { props: { modelValue: true }, attachTo: document.body })
    await wrapper.find('.modal-head .t-icon-btn').trigger('click')
    await wrapper.setProps({ modelValue: false })
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('closed')).toHaveLength(1)
    // 顺序判据：close 必须先于 closed（调用点靠 close 收可见性、靠 closed 清表单）
    const evts = Object.keys(wrapper.emitted())
    expect(evts.indexOf('close')).toBeLessThan(evts.indexOf('closed'))
  })
})
```

- [ ] **Step 2: 跑测试确认失败**

Run: `npx vitest run src/components/ui/__tests__/Modal.a11y.test.ts`
Expected: FAIL——无 `role`/`aria-modal`、无滚动锁、无 `closed` 事件。

- [ ] **Step 3: 写滚动锁 composable**

Create `frontend/src/composables/useOverlayScrollLock.ts`:

```ts
import { watch } from 'vue'
import type { Ref } from 'vue'

/**
 * 弹层期间的 body 滚动锁，用引用计数而非布尔。
 * 布尔版在"确认框叠在编辑框上"这种嵌套场景里，内层关闭即还原滚动，
 * 外层还开着但已能滚背景——是真实缺陷而非理论边界（本项目 65 个弹层中
 * 已存在 drawer/dialog 叠加，见 e2e/teacher-exam.spec.ts 的考试弹层链）。
 *
 * 键名统一用 dataset.overlayCount（camelCase）。DOMStringMap 的 supported property
 * names 是 camelCase 形式，dataset['overlay-count'] 不保证命中——若那样写，
 * 计数会恒读回 0、滚动锁永不还原，而单测里清的是另一个键，两边都"看起来对"。
 */
function count(): number {
  return Number(document.body.dataset.overlayCount ?? 0)
}

export function useOverlayScrollLock(active: Ref<boolean>): void {
  watch(
    active,
    (on, prev) => {
      if (on === prev) return
      const next = Math.max(0, count() + (on ? 1 : -1))
      document.body.dataset.overlayCount = String(next)
      document.body.style.overflow = next > 0 ? 'hidden' : ''
    },
    { immediate: true },
  )
}

/** 供单测与调试读取当前计数 */
export function overlayCount(): number {
  return count()
}
```

- [ ] **Step 4: 接到 `Modal.vue`**

Modify `frontend/src/components/ui/Modal.vue`：`Modal` 加 props `ariaLabel` / `ariaLabelledby`，`.modal` 元素：

```html
<div
  ref="rootRef"
  class="modal"
  :class="sizeClass"
  role="dialog"
  aria-modal="true"
  :aria-labelledby="hasTitle ? titleId : null"
  :aria-label="ariaLabel || null"
  @click.stop
>
  <div class="modal-head">
    <div>
      <div :id="titleId" class="modal-title"><slot name="title" /></div>
```

滚动锁与焦点返还接在 `Modal.vue` 的 `<script setup>`：

```ts
import { useOverlayScrollLock } from '@/composables/useOverlayScrollLock'
const open = computed(() => props.modelValue !== false)
useOverlayScrollLock(open)

let opener: HTMLElement | null = null
watch(open, (on, off) => {
  if (on && !off) opener = document.activeElement as HTMLElement | null
  if (!on && off) {
    emit('closed')
    opener?.focus?.()
    opener = null
  }
})
```

`closeOnClickModal` 门：

```ts
function onOverlay(e: MouseEvent) {
  if (props.closeOnClickModal === false) return
  if (e.target === e.currentTarget) close()
}
```

`titleId` 由 `Modal` 用实例唯一 id 生成（`getCurrentInstance()?.uid`），避免同页多个弹层 id 撞车。

- [ ] **Step 5: 跑测试确认通过**

Run: `npx vitest run src/components/ui/ src/composables/__tests__/`
Expected: 全部 PASS（含 Task 6 兼容钉）。`overlayCount` 相关若跨用例残留计数，说明 `beforeEach` 未清 `dataset`——按 Step 1 的 `beforeEach` 补齐，**不要**把断言改松。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/composables/useOverlayScrollLock.ts frontend/src/components/ui/Modal.vue frontend/src/components/ui/__tests__/Modal.a11y.test.ts
git commit -m "feat(ui): Modal 补 role/aria-modal/引用计数滚动锁/焦点返还/closed 与遮罩点击开关"
```

---

### Task 11: `Drawer` 同能力补强（Phase 4 要迁 4 处 `el-drawer`）

`ui/Drawer` 目前 2 个调用点，而 Phase 4 要收 4 处裸 `el-drawer`。不同批补强，Phase 4 就会遇到"抽屉比弹窗能力少一截"的二次阻塞。

**Files:**
- Modify: `frontend/src/components/ui/Drawer.vue`
- Test: `frontend/src/components/ui/__tests__/Drawer.test.ts`

**Interfaces:**
- Consumes: `useOverlayScrollLock`（Task 10）、`useDialogFocus`
- Produces: `Drawer` 具备 `v-model` / `modelValue`、Teleport、焦点接入、滚动锁、`aria-modal`、`placement`（`rtl`/`ltr`，对齐 `index.scss:342-347` 的既有圆角规则）

- [ ] **Step 1: 写失败测试**

Create `frontend/src/components/ui/__tests__/Drawer.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import Drawer from '../Drawer.vue'

describe('Drawer 与 el-drawer 能力对齐', () => {
  it('modelValue=false 不渲染', () => {
    const wrapper = mount(Drawer, { props: { modelValue: false }, slots: { title: 'T' }, attachTo: document.body })
    expect(document.body.querySelector('.drawer')).toBeNull()
    expect(wrapper.find('.drawer-overlay').exists()).toBe(false)
  })

  it('渲染到 body 且带 role=dialog / aria-modal', async () => {
    const wrapper = mount(Drawer, { props: { modelValue: true }, slots: { title: '风险明细' }, attachTo: document.body })
    await wrapper.vm.$nextTick()
    const panel = document.body.querySelector('.drawer')
    expect(panel?.getAttribute('role')).toBe('dialog')
    expect(panel?.getAttribute('aria-modal')).toBe('true')
    expect(panel?.parentElement).toBe(document.body)
  })

  it('Esc 与关闭按钮都 emit close + update:modelValue(false)', async () => {
    const wrapper = mount(Drawer, { props: { modelValue: true }, attachTo: document.body })
    await wrapper.vm.$nextTick()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
  })

  it('打开期间锁滚动', async () => {
    const wrapper = mount(Drawer, { props: { modelValue: true }, attachTo: document.body })
    await wrapper.vm.$nextTick()
    expect(document.body.style.overflow).toBe('hidden')
    await wrapper.setProps({ modelValue: false })
    await wrapper.vm.$nextTick()
    expect(document.body.style.overflow).toBe('')
  })

  it('placement=ltr 时挂到左侧类，圆角方向随之（对齐 index.scss:345-347）', async () => {
    const wrapper = mount(Drawer, { props: { modelValue: true, placement: 'ltr' }, attachTo: document.body })
    await wrapper.vm.$nextTick()
    expect(document.body.querySelector('.drawer')?.classList.contains('ltr')).toBe(true)
  })
})
```

- [ ] **Step 2: 跑测试确认失败**

Run: `npx vitest run src/components/ui/__tests__/Drawer.test.ts`
Expected: FAIL（多数项）。先读 `frontend/src/components/ui/Drawer.vue` 现状，按现状决定改哪几处，不凭本节假设——若某项其实已满足，把该条断言保留（它是回归锁），不要删。

- [ ] **Step 3: 实现**

`Drawer.vue` 的 `<script setup>` 统一为与 `Modal` 同一套通道（保持两原语行为一致，Phase 4 才不必对弹窗/抽屉写两套迁移规则）：

```ts
const props = defineProps<{
  modelValue?: boolean
  placement?: 'rtl' | 'ltr'
  teleported?: boolean
  closeOnClickModal?: boolean
}>()
const emit = defineEmits<{ close: []; 'update:modelValue': [v: boolean]; closed: [] }>()
const open = computed(() => props.modelValue !== false)
const rootRef = ref<HTMLElement | null>(null)
useOverlayScrollLock(open)
useDialogFocus(computed(() => ({ $el: rootRef.value ?? undefined })), open)
```

模板用 `<Teleport to="body">` 包 `.drawer-overlay` + `.drawer`，`.drawer` 挂 `ref="rootRef" role="dialog" aria-modal="true"` 与 `:class="placement ?? 'rtl'"`。

- [ ] **Step 4: 跑测试确认通过（含 2 个存量调用点不破）**

Run: `npx vitest run src/components/ui/`
Expected: 全 PASS。再确认两个存量调用点仍渲染：`grep -rln "ui/Drawer.vue" src --include=*.vue` 列出的文件各跑其所属测试。

- [ ] **Step 5: 提交**

```bash
git add frontend/src/components/ui/Drawer.vue frontend/src/components/ui/__tests__/Drawer.test.ts
git commit -m "feat(ui): Drawer 对齐 Modal 的 v-model/Teleport/焦点/滚动锁通道，为 Phase 4 收 4 处 el-drawer 铺路"
```

---

### Task 12: 其余 ui 原语补单测（StatCard / TabGroup / CollapsePanel / DropdownMenu / TreeView）

spec §4 Phase 0b 第 5 项。现状是 `components/ui/__tests__/` 只有 `EmptyState.test.ts` 一个——7 个原语裸奔，Phase 4/5 动它们等于无安全网作业。

**Files:**
- Create: `frontend/src/components/ui/__tests__/StatCard.test.ts`
- Create: `frontend/src/components/ui/__tests__/TabGroup.test.ts`
- Create: `frontend/src/components/ui/__tests__/CollapsePanel.test.ts`
- Create: `frontend/src/components/ui/__tests__/DropdownMenu.test.ts`
- Create: `frontend/src/components/ui/__tests__/TreeView.test.ts`

**Interfaces:**
- Consumes: 各原语现状 props
- Produces: 每原语的"现状行为快照"，Phase 5 质感升级时不得弄红

- [ ] **Step 1: 逐原语写最小现状断言**

`StatCard.test.ts`——`StatCard.vue` 有 `label` / `value` / `icon` / `tone` / `toneColor` / `foot` 六个 props，`foot` 经 `sanitizeHtml` 后 `v-html`（`StatCard.vue:89`），图标 path 取自内部 `ICONS` 常量：

```ts
import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import StatCard from '../StatCard.vue'

describe('StatCard 现状', () => {
  it('渲染 label 与 value', () => {
    const w = mount(StatCard, { props: { label: '在读学生', value: 128 } })
    expect(w.find('.s-label').text()).toBe('在读学生')
    expect(w.find('.s-value').text()).toBe('128')
  })
  it('foot 缺省时不渲染 s-foot', () => {
    expect(mount(StatCard, { props: { label: 'a', value: 1 } }).find('.s-foot').exists()).toBe(false)
  })
  it('foot 内容被净化（script 标签不落地）', () => {
    const w = mount(StatCard, { props: { label: 'a', value: 1, foot: '<img src=x onerror=alert(1)><script>bad()</script>' } })
    expect(w.find('.s-foot script').exists()).toBe(false)
  })
  it('未知 icon 名退化为 dot 而非抛错', () => {
    const w = mount(StatCard, { props: { label: 'a', value: 1, icon: 'no-such-icon-name' } })
    expect(w.find('.s-ico svg').exists()).toBe(true)
  })
})
```

`TabGroup` / `CollapsePanel` / `DropdownMenu` / `TreeView` 四个文件**先读现状再写**：各自 `Read` 组件的 `defineProps` 与模板根类名，然后按同一模式写 3–5 条断言——(a) 正常渲染、(b) 缺省不渲染某节点、(c) 交互事件 emit、(d) 未知输入不抛错。**不得**照抄 StatCard 的选择器，四个组件类名各不相同；写不出断言说明该组件无可测接口，此时记录在 LEDGER 并跳过，不硬凑空断言。

- [ ] **Step 2: 跑测试确认通过**

Run: `npx vitest run src/components/ui/__tests__/`
Expected: 全 PASS。

- [ ] **Step 3: 全链验证并提交**

```bash
npx eslint src/components/ui/__tests__/
npm run typecheck && npm run test && npm run build && npm run size:gate
git add frontend/src/components/ui/__tests__/
git commit -m "test(ui): 补齐 5 个裸奔原语现状断言，Phase 4/5 动它们前拉起安全网"
```

---

### Task 13: 焦点覆盖率护栏改写为"弹层实现无关"（Phase 4 的真正闸门）

这是 spec §9.5 标为 ⚠️ 的一条。现护栏（`src/composables/__tests__/useDialogFocus.coverage.test.ts:47-51`）扫描键是字面量 `<el-dialog`，并写死 `toBeGreaterThanOrEqual(50)` 防空转。把 65 处迁到 `ui/Modal` 会同时踩中两条失败：扫描数跌破 50（判为"扫描退化"）、`offenders` 空但什么都扫不到也算绿。**改法必须是扩口径，不能是降阈值。**

**Files:**
- Modify: `frontend/src/composables/__tests__/useDialogFocus.coverage.test.ts`
- Test: `frontend/src/composables/__tests__/useDialogFocus.coverage.guard.test.ts`（测这个护栏本身）

**Interfaces:**
- Consumes: `Modal`（Task 7–10）、`Drawer`（Task 11）的可识别标记
- Produces: `OVERLAY_MARKERS`（`<el-dialog` / `<Modal` / `<modal` / `<Drawer` / `<drawer` / `<el-drawer`）与 `EXPECTED_OVERLAY_MIN` 受控常量；Phase 4 每迁移一批，总数不降

- [ ] **Step 1: 先测准当前口径（消解 spec §9.4 的 54/55 与 63/65 差异）**

**要测的是"所有弹层实现"的并集，不只是 `el-dialog`**——`EXPECTED_OVERLAY_MIN` 守的是弹层总数，
而现仓已有 12 个文件用 `ui/Modal`、2 个用 `ui/Drawer`、4 个用 `el-drawer`。只测 el-dialog 会把地板定低，
迁移到 Modal 反而"总数上涨"而失去防丢失的能力。

Run: `cd frontend && node -e "
const fs=require('fs'),p=require('path');const S='src';
const MK=['<el-dialog','<Modal','<modal','<ui-modal','<Drawer','<drawer','<el-drawer'];
const strip=s=>s.replace(/<!--[\s\S]*?-->/g,'');let files=[],tags=0;
(function walk(d){for(const e of fs.readdirSync(d)){const x=p.join(d,e);
if(fs.statSync(x).isDirectory()){if(e!=='__tests__')walk(x);continue}
if(!x.endsWith('.vue'))continue;const s=strip(fs.readFileSync(x,'utf8'));
const c=MK.reduce((a,m)=>a+(s.split(m).length-1),0);
if(c){files.push([p.relative(S,x).replace(/\\\\/g,'/'),c]);tags+=c}}})(S);
console.log('弹层文件数=',files.length,'开标签总数=',tags);
console.log('未接焦点的=',files.filter(([f])=>{const s=strip(fs.readFileSync(p.join(S,f),'utf8'));return !(s.includes('useDialogFocus')||s.includes('ui/Modal.vue')||s.includes('ui/Drawer.vue'))}));
"
Expected: 打印出三个数。**把实测值抄进下一步**，不要沿用本文写的 54/55/63/65——这些数随时会漂。
差异定位：若"文件数"大于护栏的 54，逐个查是不是 `<el-dialog` 出现在 JS 字符串或 `import` 行里
（护栏只去 HTML 注释，不去 JS 源码）。

- [ ] **Step 2: 写护栏自身的失败测试**

Create `frontend/src/composables/__tests__/useDialogFocus.coverage.guard.test.ts`:

```ts
import { describe, expect, it } from 'vitest'
// 护栏的逻辑抽成可导入函数，否则"测护栏"只能靠真的破坏仓库
import { collectOverlayFiles, OVERLAY_MARKERS } from './coverage-lib'

describe('覆盖率护栏逻辑', () => {
  it('识别 el-dialog 与 Modal 两种实现', () => {
    const text = '<template><el-dialog ref="d"></el-dialog><Modal ref="m"></Modal></template>'
    const hits = OVERLAY_MARKERS.filter((m) => text.includes(m))
    expect(hits).toContain('<el-dialog')
    expect(hits).toContain('<Modal')
  })

  it('HTML 注释里的标记不计入（防既有假阳性回归）', () => {
    expect(collectOverlayFiles([{ file: 'a.vue', text: '<!-- <el-dialog /> -->' }])).toEqual([])
  })

  it('漏接焦点管理的弹层被列为 offender', () => {
    const r = collectOverlayFiles([{ file: 'a.vue', text: '<Modal ref="d"></Modal>' }])
    expect(r).toHaveLength(1)
    expect(r[0].wired).toBe(false)
  })

  it('经 ui/Modal 导入也算同一组件', () => {
    const r = collectOverlayFiles([{ file: 'a.vue', text: '<ui-modal ref="d"/>' }])
    expect(r).toHaveLength(1)
  })
})
```

- [ ] **Step 3: 抽逻辑成 lib 并实现**

Create `frontend/src/composables/__tests__/coverage-lib.ts`:

```ts
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

export const SRC = join(process.cwd(), 'src')

/**
 * 扫描键必须覆盖"弹层的两种实现"。原护栏只认 <el-dialog——那在它写下的日子里是对的
 * （当时全仓只有一种弹层）。Phase 4 把弹层迁向 ui/Modal 后，若扫描键不变，
 * 护栏会因"扫不到东西"而跌破防空转下限、或因"扫不到就不检查"而静默失效。
 * 两种口径都危险。故本表是弹层实现的并集，迁移过程里覆盖率始终为 100%。
 */
export const OVERLAY_MARKERS = ['<el-dialog', '<Modal', '<modal', '<ui-modal', '<Drawer', '<drawer', '<el-drawer'] as const

export interface OverlayFile {
  file: string
  wired: boolean
  /** 弹层**开标签出现次数**之和（不是"命中了哪几种标记"） */
  dialogs: number
  kinds: string[]
}

/**
 * 计数必须按"出现次数"而不是"命中了几种标记"。
 * 按种类计的话，一个含 3 个 el-dialog 的文件只算 1，总数就与真实弹层数脱钩——
 * 于是"总数只升不降"这条断言守的是一段没有意义的数字，迁移中真的丢了两个弹层也不会响。
 * CourseExams.vue 有 2 个、ResourceVersionManage.vue 有 2 个，正是会被漏掉的那种。
 */
export function countDialogs(text: string): { dialogs: number; kinds: string[] } {
  let dialogs = 0
  const kinds: string[] = []
  for (const m of OVERLAY_MARKERS) {
    const n = text.split(m).length - 1
    if (n > 0) {
      dialogs += n
      kinds.push(m)
    }
  }
  return { dialogs, kinds }
}

const stripComments = (text: string) => text.replace(/<!--[\s\S]*?-->/g, '')

export function readVueSources(dir: string = SRC, out: string[] = []): string[] {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      if (entry === '__tests__') continue
      readVueSources(full, out)
      continue
    }
    if (entry.endsWith('.vue')) out.push(full)
  }
  return out
}

export function collectOverlayFiles(
  sources: { file: string; text: string }[] = readVueSources(SRC).map((file) => ({
    file: relative(SRC, file).replace(/\\/g, '/'),
    text: stripComments(readFileSync(file, 'utf8')),
  })),
): OverlayFile[] {
  return sources
    .map(({ file, text }) => {
      const { dialogs, kinds } = countDialogs(text)
      return {
        file,
        // 焦点管理有两种等价保证方式：调用点手接原语（el-dialog 口径），
        // 或组件内部接入（ui/Modal、ui/Drawer 自持）。两者都算 wired；
        // 后者由 Modal.focus.test.ts / Drawer.test.ts 守住，不是空口计入。
        wired:
          text.includes('useDialogFocus') || text.includes('ui/Modal.vue') || text.includes('ui/Drawer.vue'),
        dialogs,
        kinds,
      }
    })
    .filter((r) => r.dialogs > 0)
}
```

> `wired` 的第二种成立条件是"该文件导入并使用了 `ui/Modal`/`ui/Drawer`——焦点接入在组件内部完成"。这与 `el-dialog` 靠调用点手接 `useDialogFocus` 是两种不同但等价的保证方式；`Modal` 的内部接入由 Task 9 的测试守住，因此这里允许它计入。若担心"导入了但没渲染"，保留 `markers` 字段供断言。

- [ ] **Step 4: 改写护栏本体（阈值改为受控总数，不是调低）**

Modify `frontend/src/composables/__tests__/useDialogFocus.coverage.test.ts`，把 `it(...)` 主体替换为：

```ts
import { collectOverlayFiles } from './coverage-lib'

// Task 13 Step 1 实测值抄在这里，作为"不得下降"的地面线。
// 关键区别：原写死的 50 是"el-dialog 文件数"的下限，迁移会让它自然下降；
// 本常量是"弹层总数"的下限，迁移不改变总数（一个 el-dialog 变成一个 Modal）。
// 因此它可以随迁移只升不降——下降即意味着有弹层凭空消失，正是该报警的事。
const EXPECTED_OVERLAY_MIN = 60
const EXPECTED_OVERLAY_COMPONENT_MIN = 54

it('全仓弹层组件必须接入焦点管理（实现无关）', () => {
  const files = collectOverlayFiles()

  expect(
    files.length,
    `弹层组件数 ${files.length} 低于 ${EXPECTED_OVERLAY_COMPONENT_MIN}——扫描逻辑可能退化，或弹层被静默删除`,
  ).toBeGreaterThanOrEqual(EXPECTED_OVERLAY_COMPONENT_MIN)

  const totalDialogs = files.reduce((s, f) => s + f.dialogs, 0)
  expect(
    totalDialogs,
    `弹层开标签总数 ${totalDialogs} 低于 ${EXPECTED_OVERLAY_MIN}——迁移过程中弹层总数不应下降`,
  ).toBeGreaterThanOrEqual(EXPECTED_OVERLAY_MIN)

  const offenders = files.filter((f) => !f.wired).map((f) => `${f.file} (${f.kinds.join(',')}, ${f.dialogs} 个)`)
  expect(offenders, `以下弹层未接入焦点管理：\n${offenders.join('\n')}`).toEqual([])
})
```

- [ ] **Step 5: 跑护栏与护栏测试**

Run: `npx vitest run src/composables/__tests__/useDialogFocus.coverage.test.ts src/composables/__tests__/useDialogFocus.coverage.guard.test.ts`
Expected: 全 PASS。若 `offenders` 非空，先查是不是 `ui/Drawer.vue` 的 2 个存量调用点被 `markers` 扫到却未接焦——那是**真实缺口**（Task 11 已给 Drawer 内部接入，应计入 wired）；修判定口径而不是把 `offenders` 断言删掉。

- [ ] **Step 6: 用一次破坏性实验证明护栏还有效（关键，防"改完就废"）**

临时把某个 `Modal` 调用点所在文件的 `ui/Modal.vue` 导入改掉、使 `wired` 为 false：

```bash
cp src/views/admin/user/components/UmDeleteDialog.vue /tmp/um.bak
# 手工把该文件的 useDialogFocus / ui/Modal 接线注释掉一行
npx vitest run src/composables/__tests__/useDialogFocus.coverage.test.ts
```
Expected: FAIL 且点名该文件。然后还原：`cp /tmp/um.bak src/views/admin/user/components/UmDeleteDialog.vue`，重跑须回绿。
**这一步不能跳**——它证明改写后的护栏仍在拦截，而不是换了一种方式静默通过。

- [ ] **Step 7: 提交并回填 LEDGER**

```bash
git add frontend/src/composables/__tests__/useDialogFocus.coverage.test.ts frontend/src/composables/__tests__/useDialogFocus.coverage.guard.test.ts frontend/src/composables/__tests__/coverage-lib.ts docs/visual-upgrade/LEDGER.md
git commit -m "test(a11y): 焦点覆盖率护栏改为弹层实现无关并加破坏性自证，Phase 4 闸门就位"
```

LEDGER 追加一行 `P0b-GUARD-01`，"有效性"栏写明 Step 6 的破坏性实验结论（这是本阶段唯一能证明"护栏没被改废"的证据）。

---

### Task 14: 非基线动效抽检组（补 reduced-motion 下看不到的另一半）

视觉 harness 全程 `reducedMotion: 'reduce'` + `animations: 'disabled'`，这是像素确定性的必要条件，但代价是**基线图里永远看不到动效**。spec §6.7 要求动效降级被证明，同时也要求动效本身被看过。本任务用一组不进基线的观察截图补上。

**Files:**
- Create: `frontend/e2e/visual/motion-observation.spec.ts`
- Create: `docs/visual-upgrade/evidence/P0a/motion/`（4 张，代表项，非全量）

**Interfaces:**
- Consumes: Task 3 的会话夹具
- Produces: 「动效开启」下的观察截图与一份"哪些动效在 reduced-motion 下确实停了"的结论，写进 LEDGER

- [ ] **Step 1: 写观察规格（不产基线）**

Create `frontend/e2e/visual/motion-observation.spec.ts`:

```ts
import { expect, test } from '@playwright/test'
import { freezeClock, setTheme, storagePathFor } from './visual-helpers'

// 与 anchors.spec.ts 的唯一区别：contextOptions 不关动效，且只 page.screenshot 落盘、
// 绝不用 toHaveScreenshot —— 动效开启时像素不确定，做成基线会天天红。
// 本组图的用途是"让人看见动效"，不是"机器判等价"。
test.describe('动效观察（非基线）', () => {
  test.use({ storageState: storagePathFor('ADMIN'), reducedMotion: 'no-preference' })

  const shots: Array<[string, string]> = [
    ['admin-dashboard', '/admin/dashboard'],
    ['admin-courses', '/admin/courses'],
    ['dialog-open', '/admin/courses'],
    ['drawer-open', '/admin/governance/exam-governance'],
  ]

  for (const [name, path] of shots) {
    test(name, async ({ page }) => {
      await setTheme(page, 'light')
      await freezeClock(page)
      await page.goto(path)
      await page.waitForLoadState('networkidle')
      if (name === 'dialog-open') {
        await page.getByRole('button', { name: /新建/ }).first().click()
        await page.locator('.el-dialog, .modal').first().waitFor({ state: 'visible' })
      }
      if (name === 'drawer-open') {
        await page.locator('tbody tr').first().getByRole('button').first().click()
        await page.locator('.el-drawer, .drawer').first().waitFor({ state: 'visible' })
      }
      await page.screenshot({ path: `docs/visual-upgrade/evidence/P0a/motion/${name}.png`, fullPage: name === 'admin-dashboard' })
    })
  }
})
```

- [ ] **Step 2: 跑并看图**

Run: `PW_PORT=5175 npx playwright test -c playwright.visual.ts motion-observation --update-snapshots`
Expected: 4 张 PNG 落在 `docs/visual-upgrade/evidence/P0a/motion/`。**逐张打开确认**：弹层确实在动（入场未瞬时完成）、无空白弹层。选择器若命中不到（按钮文案不是"新建"），按实际页面文案改正，**不要**改成更宽泛的选择器蒙过去。

- [ ] **Step 3: 验 reduced-motion 真的停了（spec §6.7）**

Run: `PW_PORT=5175 npx playwright test -c playwright.visual.ts anchors -g "admin-dashboard"`
Expected: PASS——`anchors` 在 `reducedMotion: 'reduce'` 下与基线零 diff，说明该页 CSS 动效在降级下确实停了（若没停，同一页在 Task 4 Step 4 的二次跑就会飘）。把这一条作为"CSS 降级已被间接证明"的证据写进 LEDGER；**并注明它不覆盖 JS 驱动动画**（ECharts 靠 `freezeClock` + `runFor` 收敛，`motion-v` 属 Phase 6，届时须单独验）。

- [ ] **Step 4: 提交（4 张观察图作为"代表项"入 evidence/）**

```bash
git add frontend/e2e/visual/motion-observation.spec.ts docs/visual-upgrade/evidence/P0a/motion/ docs/visual-upgrade/LEDGER.md
git commit -m "test(visual): 动效观察组补上 reduced-motion 基线看不到的另一半，并间接证明 CSS 降级生效"
```

---

### Task 15: Phase 0 收口与 Phase 1–3 计划的准入核对

Phase 0 的产物是"后续所有阶段的地基"，收口判据必须逐条对照 spec，不能自我感觉良好地宣布完成。

**Files:**
- Modify: `docs/visual-upgrade/LEDGER.md`（补 Phase 0 收口行）
- Modify: `docs/superpowers/specs/2026-10-18-frontend-visual-upgrade-design.md`（§9.2/§9.3/§9.4 三条未验证项标注实测结论）

**Interfaces:**
- Consumes: Task 1–14 全部产物
- Produces: 「Phase 0 验收清单」书面结论；Phase 1–3 计划的开工许可

- [ ] **Step 1: 跑全套验证链（一次跑齐，不留"上次绿过"的口头保证）**

```bash
cd frontend
npm run lint                    # src 口径（package.json 的 lint 脚本只覆盖 src/**）
npx eslint e2e/visual/ playwright.visual.ts   # e2e 口径：eslint.config.js 的 **/*.ts 规则块覆盖它们
npm run typecheck               # tsconfig.json 已 include e2e/**/*.ts，故会类型检查视觉 spec
npm run test
npm run build
npm run size:gate
PW_PORT=5175 npx playwright test -c playwright.visual.ts
PW_PORT=5175 npx playwright test          # 既有 9 个流程 spec，确认没被视觉改动带偏
```

**不要用 `npx eslint .`**：那会把 `dist/`、`node_modules` 之外的全部目录首次纳入 lint，
在收口这一步爆出一堆与 Phase 0 无关的历史错——把"验收"变成"补债"。上面两条分别按 src / e2e 口径跑，
且 `npx eslint e2e/visual/` 从 Task 1 起就应随每个视觉任务执行，不留到最后。

Expected: 全绿；视觉 24 项零 diff；`test` 用例数相对 Phase 0 开始前**只增不减**（本阶段新增了 Modal/Drawer/护栏测试）。记录实际数字。

- [ ] **Step 2: 逐条核对 spec 的 Phase 0 准出项**

对照下表，每条填实测值，填不出就是没做完：

| spec 条目 | 准出判据 | 实测 |
| --- | --- | --- |
| §4 Phase 0a | 24 张基线（12 页 × 明暗），二次跑零 diff | |
| §4 Phase 0a | 阈值经实测标定（非照抄经验值） | |
| §4 Phase 0b-1 | `v-model` 与既有 `@close` 双通道，12 个存量调用点不破 | |
| §4 Phase 0b-2 | Teleport + 包含块风险被测试钉住 | |
| §4 Phase 0b-3 | 焦点接入 + 滚动锁 + `role`/`aria-modal` + 焦点返还 | |
| §4 Phase 0b-4 | `width`/`title`/`#footer`/`#header`/`@closed`/遮罩开关 语义齐平 | |
| §4 Phase 0b-5 | ui 原语补测（7 个裸奔组件） | |
| §4 Phase 0b-6 | 焦点护栏实现无关 + 破坏性自证通过 | |
| §9.2 | `$el` 可达性实测结论（选 3-A 还是 3-B） | |
| §9.3 | EP 2.4.4 `el-dialog` 默认 teleport 行为实测结论 | |
| §9.4 | 54/55 与 63/65 口径对齐为单一数字 | |
| §6.2 | axe 违规数 ≤ 升级前基线 | |

`§9.3` 与 `§6.2` 的实测在本阶段补做：
Run: `grep -c "append-to-body" $(grep -rl '<el-dialog' src --include='*.vue') 2>/dev/null | awk -F: '{s+=$2} END{print "显式 append-to-body 总数:", s}'`，并打开 `node_modules/element-plus/es/components/dialog/src/dialog.mjs` 查 `appendTo`/`teleported` 默认值——**以文件内容为准，不凭记忆**。
axe 基线：`PW_PORT=5175 npx playwright test -c playwright.visual.ts` 里临时挂 `@axe-core/playwright`？—— 该包不在依赖里，Phase 0 不装。改为**手工**用浏览器扩展或 devtools 的 accessibility 检查记录 3 个锚点页的违规项，写进 LEDGER 作为升级前基线，并注明"手工、非自动"。

- [ ] **Step 3: 回填 spec 的三条未验证项**

Modify `docs/superpowers/specs/2026-10-18-frontend-visual-upgrade-design.md` §9：把第 2、3、4 条从"未实测"改写为实测结论 + 证据位置（测试文件名）。保留原判断过程，只把结论落地——不要抹掉"当时是推测"这一层信息，它是后续读者判断该文档可信度的依据。

- [ ] **Step 4: 登记 DR-94**

Run: `grep -oh "DR-[0-9]\+" docs/项目手册.md | sort -n | tail -1`
Expected: 读实际最高号。若仍是 DR-93，则按 spec 文首预留登记 DR-94；若已被并发批次占用，**顺延取新号**并同步更新 spec 文首。登记内容含：Phase 0 交付清单、LEDGER 位置、护栏改写说明、D2-8 措辞废止（spec §8）。

- [ ] **Step 5: 台账里不许留"待回填"（先验后提交，否则占位会被当成成果一起入库）**

Task 5 Step 2 曾为 `P0b-MODAL-01` 行写下 7 处 `待回填`——那是"先建表再填"的临时占位，其唯一正当性在于 Phase 0 做完时它必须已被真实数值替换。

Run: `grep -n "待回填\|__实测__" docs/visual-upgrade/LEDGER.md`
Expected: **无输出**。仍有命中即该单元的留痕缺失，视为 Phase 0 未收口；回去把对应任务的实测值填入，不得删行蒙过。

- [ ] **Step 6: 提交**

```bash
git add docs/visual-upgrade/LEDGER.md docs/superpowers/specs/2026-10-18-frontend-visual-upgrade-design.md docs/项目手册.md
git commit -m "docs(phase0): 收口核对表逐条回填实测值，spec 三条未验证项据实测定案并登记 DR-94"
```

- [ ] **Step 7: 出 Phase 1–3 计划的准入判断**

只有 Step 2 表格全部有实测值、Step 1 全绿、Step 5 的 grep 无命中，才转入 `superpowers:writing-plans` 出第二份计划（Phase 1–3 表现层）。任一项空缺 → 停，把缺的补成任务，不要带着"地基差不多"的判断进入 token 与覆写层改动。

---

## Self-Review

**1. Spec coverage（Phase 0a / 0b 逐条）**

| spec 条目 | 落在哪个任务 |
| --- | --- |
| Phase 0a 锚点 12 页 × 明暗 = 24 张 | Task 4 |
| Phase 0a viewport 1440×900 / DPR 1 / forced reduced-motion | Task 1 Step 4（config） |
| Phase 0a 字体就绪 + 网络空闲 + 时序组件冻结 | Task 1 Step 3（`settlePage` / `freezeClock`） |
| Phase 0a 逐页阈值实测标定 | Task 4 Step 3–4 + Task 15 Step 2 |
| Phase 0a 修 `only-on-failure` 与流程 spec 解耦 | Task 1 Step 4（独立 config） |
| Phase 0b-1 v-model 且保留 `@close` | Task 7 |
| Phase 0b-2 Teleport + §2.6 包含块 | Task 8 |
| Phase 0b-3 焦点 + 滚动锁 + aria + 焦点返还 | Task 9 + Task 10 |
| Phase 0b-4 全属性语义对齐 | Task 10（`width`/插槽/`@closed`/遮罩开关）+ Task 7（尺寸档） |
| Phase 0b-5 原语补测 | Task 12 + Task 6/11 |
| Phase 0b-6 护栏改写（本计划新增，spec 由自审补入） | Task 13 |
| §5 LEDGER 唯一台账 | Task 5 |
| §5 图片留存策略 | Task 2（临时图 ignore）+ Task 14（代表项入 evidence） |
| §9.2 `$el` 未验证 | Task 9 Step 1–2 探针分叉 |
| §9.3 `append-to-body` 未测准 | Task 15 Step 2 |
| §9.4 口径 54/55、63/65 | Task 13 Step 1 + Task 15 Step 3 |
| §9.5 护栏易踩警示 | Task 13 全任务 + Step 6 破坏性自证 |
| §6.6 暗色同等 | Task 4（两主题各自断言 `data-theme`） |
| §6.7 动效降级 | Task 14 Step 3 |
| §6.2 axe 只降不升 | Task 15 Step 2（**降级为手工基线**，见下） |
| §6.9 `PW_PORT=5175` | Global Constraints + 每条命令 |
| §8 D2-8 废止 | Task 15 Step 4（随 DR 登记） |

**有意留下的缺口（不是遗漏）**：axe 自动化需要 `@axe-core/playwright`，与"Phase 0 不引入新依赖"冲突，故 Task 15 改为手工记录 3 页违规项作为升级前基线并标注"手工、非自动"。**这条对外宣称已降级**——它不构成机器证明。若要求机器证明，须在 Phase 1 计划里批准装该包。

**2. Placeholder scan** — 已扫：Task 5 Step 2 的 `__实测__` 与 Task 15 Step 2 的空表列是**要求执行者填入实测值的字段**，且都写了"必须替换、否则视为未完成"的判据，与"我不知道所以留空"性质不同。Task 12 Step 1 对四个组件的断言内容给出了模式与"写不出就记录并跳过，不硬凑"的处置，未留 TBD。

**3. Type consistency** — 核对通过：`setTheme`/`freezeClock`/`settlePage`/`snapName`/`maskOf`/`storagePathFor`/`AnchorSpec`/`ThemeName`/`OVERLAY_MARKERS`/`collectOverlayFiles`/`OverlayFile`/`useOverlayScrollLock`/`overlayCount`/`DIALOG_FOCUSABLE_SELECTOR` 在各任务间的调用名与参数形状一致；`Modal` 的 `open` computed 与 `useOverlayScrollLock(active: Ref<boolean>)` 签名匹配（`computed` 即 `Ref`）；`useDialogFocus` 在 Task 9/11 两处均以 `{ $el: rootRef.value ?? undefined }` 适配，形状与 `useDialogFocus.ts:42` 的 `Ref<{ $el?: HTMLElement } | null | undefined>` 一致。

一处曾作为"留给执行者二选一"的不确定性，现已核实并定案：Vue 3.5 的 `TeleportProps` 含 `disabled?: boolean`
（证据 `frontend/node_modules/@vue/runtime-core/dist/runtime-core.d.ts:1136-1140`，接口为 `{ to, disabled?, defer? }`），
因此 Task 8 采**单个** `<Teleport to="body" :disabled="!toBody">`，不存在"并列两分支须复制 DOM"的两难，
`ModalPanel.vue` 的抽取随之取消（Task 8/9/10 的改动文件已统一为 `Modal.vue` 一个）。
连带效应已写入 Task 8 Step 4：Teleport 后 `wrapper.find('.modal')` 取不到节点，
compat / vmodel 两组现状断言须迁到 `document.body.querySelector`，且逐条确认断言意图未被偷换。
