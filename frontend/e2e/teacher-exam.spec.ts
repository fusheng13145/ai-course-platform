import { test, expect, type Page } from '@playwright/test'
import { setLocalStorageAuth } from './test-helpers'

/**
 * 教师端试卷管理主链路 e2e（API Mock 模式，无需真实后端）。
 * 覆盖：试卷列表渲染 / 新建试卷（对话框 + 创建请求）/ 发布试卷（确认 + 发布请求）。
 * 补充建议：Q3/4.3-6 所指教师端主链路（试卷/题库/成绩）覆盖缺口。
 */

const examPapers = {
  records: [
    {
      id: 1,
      title: '期中考试卷',
      courseId: 1,
      courseName: '数据结构',
      status: 'PUBLISHED',
      duration: 90,
      totalScore: 100,
    },
    {
      id: 2,
      title: '单元测验',
      courseId: 1,
      courseName: '数据结构',
      status: 'DRAFT',
      duration: 45,
      totalScore: 50,
    },
  ],
  total: 2,
}

const courses = [{ id: 1, name: '数据结构', teacherId: 1 }]

/** 注册试卷/课程基础 mock（列表 + 我的课程） */
async function mockExamBasics(page: Page) {
  await page.route('/api/exam-papers**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, message: 'success', data: examPapers }),
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

test.describe('试卷管理（教师端主链路）', () => {
  test.beforeEach(async ({ page }) => {
    await setLocalStorageAuth(page, 'TEACHER')
    // 兜底 mock：未精确匹配的 API 返回空数据，避免请求失败打断页面
    await page.route('/api/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, message: 'success', data: [] }),
      })
    })
  })

  test('试卷列表渲染：标题、统计卡与试卷行', async ({ page }) => {
    await mockExamBasics(page)

    await page.goto('/teacher/exams')
    await page.waitForLoadState('networkidle')

    await expect(page.getByText('试卷管理').first()).toBeVisible()
    await expect(page.getByRole('button', { name: /新建试卷/ })).toBeVisible()
    // 两条试卷行：状态不同（PUBLISHED / DRAFT）
    await expect(page.getByText('期中考试卷')).toBeVisible()
    await expect(page.getByText('单元测验')).toBeVisible()
    // 统计卡（试卷总数 = 2）
    await expect(page.getByText('试卷总数')).toBeVisible()
  })

  test('新建试卷：填写表单并提交创建请求', async ({ page }) => {
    await mockExamBasics(page)

    // 捕获创建请求
    const createRequest = page.waitForRequest(
      (req) => req.method() === 'POST' && req.url().includes('/api/exam-papers'),
    )

    await page.goto('/teacher/exams')
    await page.waitForLoadState('networkidle')

    // 打开新建对话框
    await page.getByRole('button', { name: /新建试卷/ }).click()
    await expect(page.getByPlaceholder('请输入试卷标题')).toBeVisible()

    // 填写标题
    await page.getByPlaceholder('请输入试卷标题').fill('期末综合卷')

    // 选择所属课程（对话框作用域定位；:visible 排除关闭状态下表格筛选下拉的残留 popper 选项）
    const dialog = page.locator('.el-dialog', { hasText: '新建试卷' })
    await dialog.locator('.el-select').first().click()
    await page.locator('.el-select-dropdown__item:visible', { hasText: '数据结构' }).first().click()

    // 点击「创建试卷」提交
    await dialog.getByRole('button', { name: /创建试卷/ }).click()

    // 断言 POST /api/exam-papers 携带正确载荷
    const req = await createRequest
    const payload = req.postDataJSON()
    expect(payload.title).toBe('期末综合卷')
    expect(payload.courseId).toBe(1)
  })

  test('发布试卷：确认后触发发布请求', async ({ page }) => {
    await mockExamBasics(page)

    const publishRequest = page.waitForRequest(
      (req) => req.method() === 'POST' && /\/api\/exam-papers\/2\/publish$/.test(req.url()),
    )

    await page.goto('/teacher/exams')
    await page.waitForLoadState('networkidle')

    // 对 DRAFT 试卷（单元测验）点击发布
    const row = page.locator('tr', { hasText: '单元测验' })
    await row.getByRole('button', { name: /发布/i }).click()

    // 确认弹窗（ElMessageBox；项目未配 zh-cn locale，确认按钮为英文 OK）
    await expect(page.getByText('发布确认')).toBeVisible()
    await page.getByRole('button', { name: /OK|确定/ }).click()

    const req = await publishRequest
    expect(req.url()).toContain('/api/exam-papers/2/publish')
  })
})
