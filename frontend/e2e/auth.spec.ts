import { test, expect } from '@playwright/test'
import { fillLoginField, loginAs, setupMockLogin } from './test-helpers'

test.describe('登录流程', () => {
  test.beforeEach(async ({ page }) => {
    await setupMockLogin(page)
  })

  test('访问登录页 - 正确渲染登录表单', async ({ page }) => {
    await page.goto('/login')

    // 验证登录表单元素存在
    await expect(page.locator('input[type="text"], input[name="username"]').first()).toBeVisible()
    await expect(page.locator('input[type="password"]').first()).toBeVisible()
    await expect(page.getByRole('button', { name: /登录|登 录|Login/i })).toBeVisible()
  })

  test('填写凭据并登录 - 学生跳转到仪表盘', async ({ page }) => {
    await page.goto('/login')

    // 填写用户名/密码
    // fillLoginField 负责两件事：先 focus 再 fill（登录框默认 readonly 防自动填充，
    // Login.vue:147-150），以及等过 onMounted 的 50ms 二次清空再校验（Login.vue:176-191）。
    await fillLoginField(
      page,
      page.locator('input[name="login-username"], input[type="text"]').first(),
      'testuser',
    )
    await fillLoginField(page, page.locator('input[type="password"]').first(), 'password123')

    // 点击登录按钮
    const loginButton = page.getByRole('button', { name: /登录|登 录|Login/i })
    await loginButton.click()

    // 等待导航到仪表盘（学生默认跳转到 /student/dashboard）
    await page.waitForURL(/\/student\/dashboard/, { timeout: 10000 })
    await expect(page).toHaveURL(/\/student\/dashboard/)
  })

  test('空表单提交 - 显示校验提示', async ({ page }) => {
    await page.goto('/login')

    // 不填写任何内容，直接点登录
    const loginButton = page.getByRole('button', { name: /登录|登 录|Login/i })
    await loginButton.click()

    // 应该停留在登录页
    await page.waitForTimeout(500)
    expect(page.url()).toContain('/login')
  })
})

test.describe('登录态重定向', () => {
  test('已登录用户访问 /login 自动跳转默认落地页', async ({ page }) => {
    // 【为什么用真实登录】原实现往 localStorage 手写 user/permissions 再 reload。
    // auth 加固后这条路径不成立：落地页里的业务请求会 401 → request.ts 刷新失败 → 登出，
    // 于是 /student/dashboard 立刻被弹回 /login，断言变成假缺陷。登录态必须由
    // 后端 HttpOnly Cookie 真正确立，才能验证守卫的 P0-3 重定向分支。
    await loginAs(page, 'STUDENT')

    await page.goto('/login')

    // 路由守卫（router/index.ts:499-512）对「已登录访问 /login」跳 getDefaultRoute()，
    // 且对目标===当前的自指向重定向做了 next(false) 兜底，所以这里必然离开 /login。
    await expect(page).toHaveURL(/\/student\/dashboard/)
  })
})
