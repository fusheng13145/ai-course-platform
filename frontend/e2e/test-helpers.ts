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

  await page.evaluate(
    ({ user, permissions }: {
      user: { id: number; username: string; fullName: string; role: string; email: string }
      permissions: { permissions: unknown[]; menus: string[] }
    }) => {
      localStorage.setItem('user', JSON.stringify(user))
      localStorage.setItem('permissions', JSON.stringify(permissions))
    },
    { user, permissions },
  )
}
