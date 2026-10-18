import { test, expect } from '@playwright/test'
import { loginAs } from './test-helpers'

test.describe('导航流程（学生端）', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'STUDENT')
  })

  test('仪表盘导航', async ({ page }) => {
    await page.goto('/student/dashboard')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('body')).toBeVisible()
    // 学生仪表盘应该渲染
    const url = page.url()
    expect(url).toContain('/student/dashboard')
  })

  test('我的课程导航', async ({ page }) => {
    // Mock 课程列表 API
    await page.route('/api/courses', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          code: 200,
          message: 'success',
          data: [{ id: 1, name: '测试课程', description: '课程描述', status: 'PUBLISHED' }],
        }),
      })
    })

    await page.goto('/student/courses')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('body')).toBeVisible()
    expect(page.url()).toContain('/student/courses')
  })

  test('知识图谱导航', async ({ page }) => {
    // Mock 知识点 API
    await page.route('/api/knowledge-points/**', async (route) => {
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

    await page.goto('/student/knowledge-graph')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('body')).toBeVisible()
    expect(page.url()).toContain('/student/knowledge-graph')
  })

  test('学习路径导航', async ({ page }) => {
    await page.route('/api/learning-paths/**', async (route) => {
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

    await page.goto('/student/learning-path')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('body')).toBeVisible()
    expect(page.url()).toContain('/student/learning-path')
  })

  test('模拟考试导航', async ({ page }) => {
    await page.route('/api/exams/**', async (route) => {
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

    await page.goto('/student/exams')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('body')).toBeVisible()
    expect(page.url()).toContain('/student/exams')
  })

  test('我的成绩导航', async ({ page }) => {
    await page.route('/api/grades/**', async (route) => {
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

    await page.goto('/student/grades')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('body')).toBeVisible()
    expect(page.url()).toContain('/student/grades')
  })

  test('错题本导航', async ({ page }) => {
    await page.route('/api/wrong-questions/**', async (route) => {
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

    await page.goto('/student/wrong-questions')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('body')).toBeVisible()
    expect(page.url()).toContain('/student/wrong-questions')
  })

  test('个人中心导航', async ({ page }) => {
    await page.goto('/student/profile')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('body')).toBeVisible()
    expect(page.url()).toContain('/student/profile')
  })
})

test.describe('未登录访问控制', () => {
  test('未登录访问受保护路由 - 重定向到登录页', async ({ page }) => {
    // 独立 describe：不注入登录态（setLocalStorageAuth 的 addInitScript 会在每次导航
    // 重新写入 localStorage，与「清除登录态」语义冲突，故移出共享 describe）
    await page.goto('/student/dashboard')
    await page.waitForLoadState('networkidle')

    // 路由守卫应重定向到 /login
    await page.waitForTimeout(1000)
    expect(page.url()).toContain('/login')
  })
})

test.describe('导航流程（教师端）', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'TEACHER')

    // 默认 mock
    await page.route('/api/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: [], message: 'success' }),
      })
    })
  })

  test('教师仪表盘导航', async ({ page }) => {
    await page.goto('/teacher/dashboard')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('body')).toBeVisible()
    expect(page.url()).toContain('/teacher/dashboard')
  })

  test('教师课程管理导航', async ({ page }) => {
    await page.goto('/teacher/courses')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('body')).toBeVisible()
    expect(page.url()).toContain('/teacher/courses')
  })

  test('AI 教学中心导航', async ({ page }) => {
    await page.goto('/teacher/ai')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('body')).toBeVisible()
    expect(page.url()).toContain('/teacher/ai')
  })

  test('知识图谱导航（教师端）', async ({ page }) => {
    await page.goto('/teacher/knowledge')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('body')).toBeVisible()
    expect(page.url()).toContain('/teacher/knowledge')
  })
})

test.describe('导航流程（管理员端）', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'ADMIN')

    await page.route('/api/**', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ code: 200, data: [], message: 'success' }),
      })
    })
  })

  test('管理员仪表盘导航', async ({ page }) => {
    await page.goto('/admin/dashboard')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('body')).toBeVisible()
    expect(page.url()).toContain('/admin/dashboard')
  })

  test('用户管理导航', async ({ page }) => {
    await page.goto('/admin/users')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('body')).toBeVisible()
    expect(page.url()).toContain('/admin/users')
  })

  test('课程管理导航', async ({ page }) => {
    await page.goto('/admin/courses')
    await page.waitForLoadState('networkidle')
    await expect(page.locator('body')).toBeVisible()
    expect(page.url()).toContain('/admin/courses')
  })
})
