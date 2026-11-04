/**
 * E2E 测试辅助工具
 */

import type { Locator, Page, Route } from '@playwright/test'

export const API_BASE = '/api'

/**
 * e2e 的站点根，与 `playwright.config.ts` 的 `baseURL` 同源。
 *
 * 为什么要有它：三份 spec 原先各写一遍 `process.env.PW_BASE_URL || 'http://localhost:5173'`，
 * 而 config 的 baseURL 走的是 `PW_PORT`。于是按文档跑 `PW_PORT=5174 npx playwright test` 时，
 * webServer 起在 5174、spec 却把绝对 URL 拼到 5173，整轮读成 `ERR_CONNECTION_REFUSED`
 * （2026-11-04 实测，3 例失败 + 11 例未执行）。只留这一处口径：显式 `PW_BASE_URL` 优先，
 * 否则由 `PW_PORT` 派生，最后才回落 5173。
 */
export const SITE_BASE =
  process.env.PW_BASE_URL || `http://localhost:${process.env.PW_PORT || '5173'}`

/**
 * 模拟的登录响应
 */
export function createMockLoginResponse(role: string = 'STUDENT') {
  return {
    code: 200,
    message: 'success',
    data: {
      user: {
        id: 1,
        username: 'testuser',
        fullName: '测试用户',
        role,
        email: 'test@example.com',
      },
      token: 'mock-jwt-token',
    },
  }
}

/**
 * 模拟的权限响应
 *
 * 【为什么必须带 role】stores/auth.ts 的 effectiveRole 优先取 serverRole（由
 * /auth/permissions 响应体的 data.role / data.userRole 置位，见 auth.ts:166），
 * 本地可篡改的 user.role 只是回退。router/index.ts 的守卫先判 isLoggedIn、
 * 再 `await fetchPermissions()`：若该请求真打到后端并 401，request.ts 会刷新失败→登出，
 * 于是守卫拿到的角色已变空 → 一律 next('/403')。所以只写 localStorage 的夹具
 * 在"服务端确认会话"加固后必然失败，必须让权限响应回传角色。
 */
export function createMockPermissionsResponse(role: string = 'STUDENT') {
  return {
    code: 200,
    message: 'success',
    data: {
      role,
      permissions: [
        { permissionKey: 'dashboard', permissionType: 'MENU', role: 'STUDENT', isActive: true },
        { permissionKey: 'courses', permissionType: 'MENU', role: 'STUDENT', isActive: true },
        { permissionKey: 'ai-assistant', permissionType: 'MENU', role: 'STUDENT', isActive: true },
        { permissionKey: 'profile', permissionType: 'MENU', role: 'STUDENT', isActive: true },
        { permissionKey: 'notifications', permissionType: 'MENU', role: 'STUDENT', isActive: true },
      ],
      menus: ['dashboard', 'courses', 'ai-assistant', 'profile', 'notifications'],
    },
  }
}

/**
 * 设置登录 Mock（拦截 login API 并设置 localStorage）
 */
export async function setupMockLogin(page: Page, role: string = 'STUDENT') {
  const loginResponse = createMockLoginResponse(role)
  const permissionsResponse = createMockPermissionsResponse()

  // Mock login API
  await page.route(`${API_BASE}/auth/login`, async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(loginResponse),
    })
  })

  // Mock permissions API
  await page.route(`${API_BASE}/auth/permissions`, async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(permissionsResponse),
    })
  })

  // Mock refresh token API
  await page.route(`${API_BASE}/auth/refresh`, async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, message: 'success', data: {} }),
    })
  })

  // Mock me API
  await page.route(`${API_BASE}/auth/me`, async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: loginResponse.data.user,
      }),
    })
  })
}

/**
 * 填入登录表单字段，绕开 Login.vue 的「防自动填充二次清空」
 *
 * views/auth/Login.vue:176-191 在 onMounted 里先把账号/密码清空一次，再用
 * setTimeout(50ms) 二次清空（防浏览器将已存凭据回填进来）。Playwright 的 fill
 * 常落在这两次清空之间：实测失败快照里用户名是空的、密码却留着 123456，于是
 * handleLogin 走到「请填写用户名和密码」的早退分支 → 永远不发生导航。
 * 真实用户不可能在挂载后 50ms 内输入完毕，所以这是测试时序问题而非产品缺陷：
 * 填完等过那个定时器再校验，被清空就重填一次。
 */
export async function fillLoginField(page: Page, input: Locator, value: string) {
  await input.focus()
  await input.fill(value)
  await page.waitForTimeout(80)
  if ((await input.inputValue()) !== value) {
    await input.focus()
    await input.fill(value)
  }
}

/**
 * 关掉首次引导浮层（components/OnboardingGuide.vue）
 *
 * 该浮层是 `position: fixed; inset: 0` 的整屏遮罩，未点「跳过 / 开始使用」之前会拦截页面上
 * 的一切点击（实测：Playwright 报 `<div class="ob-overlay"> intercepts pointer events` 并反复重试）。
 * 「已看过」标记自 2026-10-13 起**按账号**写入（v1 键 `ai-course:onboarding:done:v1:<userId>`，
 * 契约 起改读 `ai-course:onboarding::<userId>` 那条 `{status, atStep, role, seenAt}`），
 * 所以旧 e2e 里 `localStorage.setItem('ai-course:onboarding:done:v1', '1')` 那种写裸前缀键的
 * 做法已经不再命中判定分支——等于没关。这里改按真实用户行为点掉它：点一次即由组件自己写入
 * 正确键，同一上下文后续导航不再弹出。
 * 【点掉的语义是 skipped 不是 done】「跳过」按钮在出口语义分离后写 `skipped@0`；
 * 这一条只用于"把浮层弄走好测别的页面"，不代表激活动作发生过——引导本身的三态断言在
 * `e2e/onboarding.spec.ts`，别拿这里的读数当完成率的证据。
 * 浮层在 AppShell 挂载 600ms 后才出现，故先等过那个延时。
 */
export async function dismissOnboarding(page: Page) {
  await page.waitForTimeout(900)
  const skip = page.getByRole('button', { name: '跳过' })
  if (await skip.isVisible().catch(() => false)) {
    await skip.click()
    await page.locator('.ob-overlay').waitFor({ state: 'hidden', timeout: 5000 })
  }
}

/**
 * 通过真实登录建立会话（E2E 首选）
 *
 * 【为什么不用 localStorage 伪造】auth 加固后 `permissionsLoaded` 只由一次真实的
 * /auth/permissions 成功响应置位，`effectiveRole` 优先取服务端回传角色；且 request.ts
 * 对任意 401 都会走「刷新失败 → 登出」。于是要么守卫把请求判成 /403，要么页面里任何一个
 * 未 mock 的业务端点 401 后把会话清掉、被踢回 /login。逐个 mock 端点既脆弱又掩盖真实链路。
 *
 * 账号来自 V1 迁移的内置演示账号（口令 123456，见 backend SeedAccounts）：
 * STUDENT→student、TEACHER→teacher、ADMIN→admin。三者都在 db/migration（进生产路径）
 * 里，不依赖 V5/V6 演示种子，因此 CI 的最小库也能用。
 *
 * 登录框默认为 readonly、focus 时才摘掉（防自动填充），所以必须先 focus 再 fill；
 * 并且要避开 Login.vue 的延时二次清空（见 fillLoginField）。
 */
export async function loginAs(page: Page, role: 'STUDENT' | 'TEACHER' | 'ADMIN' = 'STUDENT') {
  const account = { STUDENT: 'student', TEACHER: 'teacher', ADMIN: 'admin' }[role]

  await page.goto('/login')
  // 登录页的字段名是 username/password（旧端是 login-username）
  await fillLoginField(page, page.locator('input[name="username"]').first(), account)
  await fillLoginField(page, page.locator('input[name="password"]').first(), '123456')
  // 「记住我」决定 user/permissions 落 localStorage 还是 sessionStorage；storageState 只带后者
  // 不带 sessionStorage，不勾会导致复用的登录态等于未登录
  await page
    .locator('.av-check input[type=checkbox]')
    .check()
    .catch(() => {})
  // 先挂上响应监听再点提交：429 的 `ratelimit.login`（默认 10 次/60 秒/IP）在后端**不打日志**，
  // 只让 waitForURL 超时的话，现象是"随机红灯"，查不出是被限流还是页面坏了（DR-85 实测）。
  const loginResponse = page
    .waitForResponse((res) => new URL(res.url()).pathname === '/api/auth/login', { timeout: 15000 })
    .catch(() => null)
  await page.getByRole('button', { name: /登录|登 录|Login/i }).click()
  const res = await loginResponse
  if (res && res.status() === 429) {
    throw new Error(
      '登录被 IP 限流（HTTP 429，`ratelimit.login` 默认 10 次/60 秒）。整份 e2e 要登录 35 次，' +
        '跑之前必须把后端起成 `RATELIMIT_LOGIN_LIMIT=200`（或改用会话复用），' +
        '否则失败集合随测试快慢漂移，读到的红灯不是产品缺陷。',
    )
  }

  // 登录后按角色落到各自默认页（getDefaultRoute），失败即说明会话没建起来，
  // 让 waitForURL 直接暴露原因，而不是后面几十个断言一起变成假缺陷。
  await page.waitForURL(new RegExp(`/${role.toLowerCase()}/`), { timeout: 15000 })
}

/**
 * 通过 localStorage 直接设置登录状态
 *
 * 【适用边界】仅适合「不触发任何后端请求」的纯渲染断言。auth 加固后（见上方 loginAs 说明）
 * 凡要进入带业务请求的页面，都应改用 loginAs，否则会因 401→登出被踢回 /login。
 *
 * 注意：使用 addInitScript 而非 page.evaluate——Chromium 新版本对 about:blank
 * 文档的 localStorage 访问返回 SecurityError（"Access is denied for this document"），
 * 在 page.goto 之前执行 evaluate 会失败；addInitScript 在目标页导航前注入，
 * 规避该问题且对每次导航幂等（CI Linux 无此问题，本地 Windows 必须用此方式）。
 */
export async function setLocalStorageAuth(page: Page, role: string = 'STUDENT') {
  const user = {
    id: 1,
    username: 'testuser',
    fullName: '测试用户',
    role,
    email: 'test@example.com',
  }
  const permissions = {
    permissions: [],
    menus: ['dashboard', 'courses', 'ai-assistant', 'profile', 'notifications'],
  }

  await page.addInitScript(
    ({
      user,
      permissions,
    }: {
      user: { id: number; username: string; fullName: string; role: string; email: string }
      permissions: { permissions: unknown[]; menus: string[] }
    }) => {
      localStorage.setItem('user', JSON.stringify(user))
      localStorage.setItem('permissions', JSON.stringify(permissions))
      // 关闭首次引导层。键必须带账号段：引导读的是 `…:<userId>`（2026-10-13 起按账号隔离），
      // 本夹具的 user.id 固定为 1，故写 `:1`。
      // v1 键保留是给迁移断言用的（写 v1 ⇒ 组件按"老用户"迁成 done，绝不再莫名重弹），
      // 两键并存的读法见 LEDGER `P0c-ONB-03`；才是现在真正被读的那一条。
      // `dismissOnboarding` 仍按真实用户行为点「跳过」——那个按钮现在写的是 `skipped`，不再是 `done`。
      localStorage.setItem('ai-course:onboarding:done:v1:1', '1')
      localStorage.setItem('ai-course:onboarding::1', JSON.stringify({ status: 'done', atStep: 0 }))
    },
    { user, permissions },
  )

  // 【必须配套 mock 会话端点】仅写 localStorage 已不足以通过守卫：见
  // createMockPermissionsResponse 的说明——router 守卫会 await fetchPermissions()，
  // 打到真实后端即 401 → request.ts 走「刷新失败→登出」→ 角色变空 → /403。
  await page.route(`${API_BASE}/auth/permissions`, async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(createMockPermissionsResponse(role)),
    })
  })
  await page.route(`${API_BASE}/auth/me`, async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, message: 'success', data: user }),
    })
  })
  await page.route(`${API_BASE}/auth/refresh`, async (route: Route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, message: 'success', data: {} }),
    })
  })
}
