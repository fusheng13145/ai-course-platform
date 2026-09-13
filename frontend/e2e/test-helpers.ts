/**
 * E2E 测试辅助工具
 */

import type { Page, Route } from '@playwright/test'

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
 */
export function createMockPermissionsResponse() {
  return {
    code: 200,
    message: 'success',
    data: {
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
 * 通过 localStorage 直接设置登录状态
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
}
