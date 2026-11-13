import { expect, test } from '@playwright/test'

/**
 * 自有守卫的真浏览器取证：未登录直访管理端深链接必须落到 登录页并带 redirect，
 * 角色不匹配必须落到 ui/403。这条判据在旧 router 被摘除后仍需成立（M-22 / L-08）。
 */
test.describe('路由守卫（未登录）', () => {
  test('管理端深链接被拦到 登录页', async ({ page }) => {
    await page.context().clearCookies()
    await page.addInitScript(() => {
      localStorage.clear()
    })
    await page.goto('/admin/questions')
    await expect(page).toHaveURL(/\/login\?redirect=/)
    await expect(page.getByRole('heading', { name: '欢迎回来' })).toBeVisible()
  })

  test('学生端深链接同样被拦到 登录页', async ({ page }) => {
    await page.context().clearCookies()
    await page.addInitScript(() => {
      localStorage.clear()
    })
    await page.goto('/student/tasks')
    await expect(page).toHaveURL(/\/login\?redirect=/)
  })
})
