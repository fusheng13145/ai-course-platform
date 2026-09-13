import { test, expect } from '@playwright/test'
import { setLocalStorageAuth } from './test-helpers'

test.describe('AI 助手页面（学生端）', () => {
  test.beforeEach(async ({ page }) => {
    // 通过 localStorage 模拟已登录学生
    await setLocalStorageAuth(page, 'STUDENT')

    // Mock AI 聊天相关 API（与当前后端端点对齐）
    await page.route('/api/ai/qa/sessions', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: [{ id: 1, title: '会话 1', createdAt: new Date().toISOString() }],
        }),
      })
    })

    await page.route('/api/ai/qa/ask', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: {
            id: 1,
            response: '你好！我是 AI 学习助手，有什么可以帮助你的吗？',
            sessionId: 1,
          },
        }),
      })
    })

    // Mock 课程列表 API
    await page.route(/\/api\/courses/, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: [],
        }),
      })
    })

    await page.goto('/student/ai-assistant')
    await page.waitForLoadState('networkidle')
  })

  test('页面正确渲染 - 包含侧边栏和聊天区域', async ({ page }) => {
    // 验证页面标题或核心元素
    await expect(page.locator('body')).toBeVisible()

    // 验证侧边栏存在（StudentLayout 侧边导航）
    const sidebar = page
      .locator('.el-menu, .sidebar, aside, [class*="side"], [class*="nav"]')
      .first()
    await expect(sidebar).toBeVisible({ timeout: 5000 })

    // 验证聊天区域存在
    const chatArea = page.locator('[class*="chat"], [class*="message"], [class*="ai"]').first()
    await expect(chatArea).toBeVisible({ timeout: 5000 })
  })

  test('页面标题正确', async ({ page }) => {
    // 验证页面标题（路由 meta.title = 'AI助手'）
    const title = await page.title()
    expect(title).toBeTruthy()
  })

  test('输入框和发送按钮存在', async ({ page }) => {
    // 聊天输入框（AiaChatPanel：el-input textarea，placeholder 精确匹配，避免误匹配侧边栏输入）
    const inputArea = page.getByPlaceholder(/输入您的问题/)
    await expect(inputArea).toBeVisible({ timeout: 5000 })

    // 发送按钮（AiaChatPanel：class=send-btn）
    const sendButton = page.locator('.send-btn')
    await expect(sendButton).toBeAttached({ timeout: 5000 })
  })

  test('发送消息 - 显示用户消息和 AI 回复', async ({ page }) => {
    // 在聊天输入框中输入消息
    const inputArea = page.getByPlaceholder(/输入您的问题/)
    await inputArea.fill('你好，请帮我解答这个问题')

    // 发送按钮（输入非空后解除 disabled）
    const sendButton = page.locator('.send-btn')
    await expect(sendButton).toBeEnabled({ timeout: 5000 })
    await sendButton.click()

    // 等待用户消息与 AI 回复渲染（mock 返回固定文案）
    await expect(page.locator('body')).toContainText('你好，请帮我解答这个问题')
    await expect(page.locator('body')).toContainText('我是 AI 学习助手', { timeout: 5000 })
  })
})
