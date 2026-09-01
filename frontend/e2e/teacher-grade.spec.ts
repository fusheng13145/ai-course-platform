import { test, expect, type Page } from '@playwright/test'
import { setLocalStorageAuth } from './test-helpers'

/**
 * 教师端成绩管理主链路 e2e（API Mock 模式，无需真实后端）。
 * 覆盖：成绩列表多源渲染（考试会话 + 作业提交合并）/ AI 批阅（确认 + 请求）。
 * 数据源：/api/courses/my、/api/analytics/dashboard、/api/exam-sessions/my、
 * /api/submissions（role=teacher）、/api/tasks/pending-grading、POST /api/submissions/:id/ai-grade。
 */

const courses = [{ id: 1, name: '数据结构', teacherId: 1 }]

const examSessions = {
  list: [
    { studentId: 5, studentName: '张三', score: 85, isGraded: true, status: 'graded', courseName: '数据结构' },
  ],
}

const submissions = {
  list: [
    { id: 101, studentId: 6, studentName: '李四', score: null, status: 'submitted', courseName: '数据结构' },
  ],
}

/** 注册成绩页全部数据源 mock */
async function mockGradeBasics(page: Page) {
  await page.route('/api/courses/my**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, message: 'success', data: courses }),
    })
  })
  await page.route('/api/analytics/dashboard**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: { avgScore: 85, maxScore: 95, minScore: 60, pendingCount: 1 },
      }),
    })
  })
  await page.route('/api/exam-sessions/my**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, message: 'success', data: examSessions }),
    })
  })
  await page.route('/api/submissions**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, message: 'success', data: submissions }),
    })
  })
  await page.route('/api/tasks/pending-grading**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        code: 200,
        message: 'success',
        data: [{ id: 101, taskName: '第三章作业', studentName: '李四' }],
      }),
    })
  })
}

test.describe('成绩管理（教师端主链路）', () => {
  test.beforeEach(async ({ page }) => {
    await setLocalStorageAuth(page, 'TEACHER')
    await page.route('/api/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, message: 'success', data: [] }),
      })
    })
  })

  test('成绩列表渲染：多源合并展示学生成绩', async ({ page }) => {
    await mockGradeBasics(page)

    await page.goto('/teacher/grades-manage')
    await page.waitForLoadState('networkidle')

    // 页面标题与统计卡
    await expect(page.getByText('成绩管理').first()).toBeVisible()
    await expect(page.getByText('平均分')).toBeVisible()
    // 考试会话学生（张三）与作业提交学生（李四）均出现在成绩列表
    await expect(page.getByText('张三')).toBeVisible()
    await expect(page.getByText('李四')).toBeVisible()
  })

  test('AI 批阅：确认后触发 AI 评分请求', async ({ page }) => {
    await mockGradeBasics(page)

    const aiGradeRequest = page.waitForRequest(
      // 成绩行 id 为 studentId（6），而非 submission id（101）
      (req) => req.method() === 'POST' && /\/api\/submissions\/\d+\/ai-grade$/.test(req.url()),
    )

    await page.goto('/teacher/grades-manage')
    await page.waitForLoadState('networkidle')

    // 对李四（submitted 状态，可 AI 批阅）点击 AI 按钮
    const row = page.locator('tr', { hasText: '李四' })
    await row.getByRole('button', { name: /AI/i }).click()

    // ElMessageBox 确认框（项目未配 zh-cn locale，确认按钮为英文 OK）
    await expect(page.getByText('AI评分确认')).toBeVisible()
    await page.getByRole('button', { name: /OK|确定/ }).click()

    const req = await aiGradeRequest
    expect(req.url()).toMatch(/\/api\/submissions\/\d+\/ai-grade$/)
  })
})
