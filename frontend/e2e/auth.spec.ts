import { test, expect } from '@playwright/test'
import { setupMockLogin } from './test-helpers'

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

    // 填写用户名
    const usernameInput = page.locator('input[type="text"], input[name="username"]').first()
    await usernameInput.fill('testuser')

    // 填写密码
    const passwordInput = page.locator('input[type="password"]').first()
    await passwordInput.fill('password123')

    // 点击登录按钮
    const loginButton = page.getByRole('button', { name: /登录|登 录|Login/i })
    await loginButton.click()

    // 等待导航到仪表盘（学生默认跳转到 /student/dashboard）
    await page.waitForURL(/\/student\/dashboard/, { timeout: 10000 })
    await expect(page).toHaveURL(/\/student\/dashboard/)
  })

  test('登录后重定向 - 已登录用户访问 /login 自动跳转', async ({ page }) => {
    // Mock 登录态通过 localStorage
    await page.goto('/login')
    await page.evaluate(() => {
      localStorage.setItem(
        'user',
        JSON.stringify({
          id: 1,
          username: 'testuser',
          fullName: '测试用户',
          role: 'STUDENT',
          email: 'test@example.com',
        }),
      )
      localStorage.setItem('permissions', JSON.stringify({ menus: [] }))
    })

    // 模拟已登录用户访问登录页时被路由守卫重定向
    // 注意：路由守卫在导航时触发，所以需要 reload 或重新导航
    await page.reload()

    // 已登录用户访问 /login 时应该被重定向到对应角色首页
    // 如果路由守卫正常工作，URL 应该变更
    await page.waitForTimeout(500)
    const url = page.url()
    expect(url).not.toContain('/login')
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
