import { test, expect, type Page } from '@playwright/test'
import { loginAs } from './test-helpers'

/**
 * 教师端题库管理主链路 e2e（会话由真实登录建立，业务数据用 API Mock）。
 * 覆盖：题目列表渲染（含状态统计）/ 新建题目（对话框 + 创建请求）。
 */

const questions = {
  records: [
    {
      id: 1,
      type: 'SINGLE',
      stem: '数据结构中，栈的特点是？',
      difficulty: 'MEDIUM',
      reviewStatus: 'APPROVED',
    },
    {
      id: 2,
      type: 'MULTIPLE',
      stem: '以下哪些属于线性表？',
      difficulty: 'HARD',
      reviewStatus: 'PENDING',
    },
  ],
  total: 2,
}

const courses = [{ id: 1, name: '数据结构', teacherId: 1 }]

/** 注册题库/课程基础 mock */
async function mockQuestionBasics(page: Page) {
  await page.route('/api/questions**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, message: 'success', data: questions }),
    })
  })
  await page.route('/api/courses/my**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, message: 'success', data: courses }),
    })
  })
}

test.describe('题库管理（教师端主链路）', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'TEACHER')
    await page.route('/api/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, message: 'success', data: [] }),
      })
    })
  })

  test('题库列表渲染：标题、统计卡与题目行', async ({ page }) => {
    await mockQuestionBasics(page)

    await page.goto('/teacher/bank')
    await page.waitForLoadState('networkidle')

    await expect(page.getByText('题库管理').first()).toBeVisible()
    await expect(page.getByRole('button', { name: /新建题目/ })).toBeVisible()
    await expect(page.getByText('数据结构中，栈的特点是？')).toBeVisible()
    await expect(page.getByText('以下哪些属于线性表？')).toBeVisible()
    // 统计卡：题目总数 / 待审核（表格状态 tag 也含「待审核」，取统计卡首个）
    await expect(page.getByText('题目总数')).toBeVisible()
    await expect(page.getByText('待审核').first()).toBeVisible()
  })

  test('新建题目：填写题干并提交创建请求', async ({ page }) => {
    await mockQuestionBasics(page)

    const createRequest = page.waitForRequest(
      (req) => req.method() === 'POST' && req.url().includes('/api/questions'),
    )

    await page.goto('/teacher/bank')
    await page.waitForLoadState('networkidle')

    // 打开新建题目对话框
    await page.getByRole('button', { name: /新建题目/ }).click()
    await expect(page.getByPlaceholder('请输入题干内容')).toBeVisible()

    const dialog = page.locator('.el-dialog', { hasText: '新建题目' })

    // 题型（必填）：单选（:visible 排除残留 popper 选项）
    await dialog.locator('.el-select').nth(0).click()
    await page.locator('.el-select-dropdown__item:visible', { hasText: '单选题' }).first().click()
    // 难度（必填）：中等
    await dialog.locator('.el-select').nth(1).click()
    await page.locator('.el-select-dropdown__item:visible', { hasText: '中等' }).first().click()

    // 填写题干（必填）与参考答案
    await page.getByPlaceholder('请输入题干内容').fill('链表的时间复杂度是？')
    await page.getByPlaceholder('请输入参考答案').fill('O(1)')

    // 提交（新建态按钮文本为「创建题目」）
    await dialog.getByRole('button', { name: /创建题目/ }).click()

    const req = await createRequest
    const payload = req.postDataJSON()
    expect(payload.content).toBe('链表的时间复杂度是？')
    expect(payload.type).toBe('SINGLE_CHOICE')
    expect(payload.difficulty).toBe('MEDIUM')
  })
})
