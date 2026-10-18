import { test, expect } from '@playwright/test'
import { API_BASE, createMockPermissionsResponse, loginAs } from './test-helpers'

/**
 * 权限拒绝（403）语义的端到端回归用例。
 *
 * 背景（2026-09-14 修复）：后端在「已登录但无权限」时曾错误返回 **401**，而前端
 * `api/request.ts` 对 401 的处理是「尝试刷新 → 失败则调用 /auth/logout 并跳转 /login」。
 * 结果是：学生只要访问了一个自己无权访问的接口，就会被**登出并踢回登录页**。
 *
 * 后端已修正为对已登录用户返回 403（并附标准业务信封），本用例锁死该契约的前端侧表现：
 *  - 收到 403 时**不得**调用 /auth/logout；
 *  - **不得**跳转到 /login；
 *  - 本地登录态（localStorage.user）必须保留。
 */
test.describe('权限拒绝（403）不应触发登出', () => {
  test('已登录用户遇 403：保留登录态且不跳转登录页', async ({ page }) => {
    let logoutCalled = false

    // 登录态（localStorage）与权限/用户信息 mock
    await loginAs(page, 'STUDENT')
    await page.route(`${API_BASE}/auth/permissions`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(createMockPermissionsResponse()),
      })
    })
    await page.route(`${API_BASE}/auth/me`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: { id: 1, username: 'testuser', fullName: '测试用户', role: 'STUDENT' },
        }),
      })
    })
    await page.route(`${API_BASE}/auth/refresh`, async (route) => {
      // 故意让刷新失败：用于暴露「401 分支会走登出」的旧行为
      await route.fulfill({ status: 401, contentType: 'application/json', body: '{}' })
    })
    // 关键：受保护接口返回 403（而非 401）
    await page.route(`${API_BASE}/student/dashboard`, async (route) => {
      await route.fulfill({
        status: 403,
        contentType: 'application/json',
        body: JSON.stringify({ code: 403, message: '权限不足，禁止访问', data: null }),
      })
    })
    await page.route(`${API_BASE}/auth/logout`, async (route) => {
      logoutCalled = true
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, message: '已退出', data: null }),
      })
    })

    await page.goto('/student/dashboard')

    // 给拦截器与提示留出处理时间
    await page.waitForTimeout(2500)

    // 1) 不得跳转登录页
    expect(page.url()).not.toMatch(/\/login/)
    // 2) 必须保留本地登录态
    const user = await page.evaluate(() => localStorage.getItem('user'))
    expect(user).not.toBeNull()
    // 3) 不得调用登出接口
    expect(logoutCalled).toBe(false)
  })
})
