/**
 * E2E 测试辅助工具
 */

import type { Locator, Page, Route } from '@playwright/test'

export const API_BASE = '/api'

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

  // 关闭首次引导浮层（OnboardingGuide：ai-course:onboarding:done:v1），否则它会遮挡
  // 表格操作按钮；对每次导航幂等，须在 goto 之前注入。
  await page.addInitScript(() => {
    localStorage.setItem('ai-course:onboarding:done:v1', '1')
  })

  await page.goto('/login')
  await fillLoginField(
    page,
    page.locator('input[name="login-username"], input[type="text"]').first(),
    account,
  )
  await fillLoginField(page, page.locator('input[type="password"]').first(), '123456')
  await page.getByRole('button', { name: /登录|登 录|Login/i }).click()

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
      // 关闭首次引导层（OnboardingGuide：ai-course:onboarding:done:v1），
      // 否则引导浮层遮挡表格操作按钮导致 e2e 点击失败
      localStorage.setItem('ai-course:onboarding:done:v1', '1')
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
