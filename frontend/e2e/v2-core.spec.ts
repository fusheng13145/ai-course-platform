import { test, expect, request, type APIRequestContext, type Browser } from '@playwright/test'

/**
 * v2 核心链路真栈巡检（L-02 的 M-04／M-11 段 + M-02 的开考/续考段）。
 *
 * 前置：本机 start.bat 起过后端 8080 与前端 5173，种子账号可用（README 演示账号）。
 * 口径：断言真实 API 读数与真实状态迁移，不打桩；不交卷（避免留下不可撤销的判分记录）。
 * 说明：开考用的试卷首次运行时自动创建并发布，之后按标题前缀复用；发布态后端无删除端点，
 * 因此这张「E2E-开考续考-守护卷」会长期留在库里，属已知代价，不做其它写操作。
 */

const BASE = process.env.PW_BASE_URL || 'http://localhost:5173'
const PASS = process.env.PW_PASS || '123456'
const ACCOUNTS = {
  admin: process.env.PW_ADMIN_USER || 'admin',
  teacher: process.env.PW_TEACHER_USER || 'ts01',
  student: process.env.PW_STUDENT_USER || 'st001',
} as const
type Role = keyof typeof ACCOUNTS

const PAPER_PREFIX = 'E2E-开考续考-守护卷'
const EXAM_COURSE_ID = 4001

let states: Record<Role, Awaited<ReturnType<Browser['newContext']>>['storageState']> | null = null
let paperTitle = ''

async function loginState(browser: Browser, role: Role) {
  const context = await browser.newContext({ baseURL: BASE })
  const page = await context.newPage()
  await page.goto('/v2/login', { waitUntil: 'domcontentloaded' })
  await page.fill('input[name=username]', ACCOUNTS[role])
  await page.fill('input[name=password]', PASS)
  // 「记住我」决定 user/permissions 落 localStorage 还是 sessionStorage；
  // storageState 只带 localStorage，因此这里必须勾上，否则复用态等于未登录。
  await page.check('.av-check input[type=checkbox]')
  await page.click('button[type=submit]')
  // 登录限流时页面会给 av-error；重试一次，避免把限流记成产品缺陷
  await page.waitForTimeout(2500)
  if (await page.locator('.av-error').count()) {
    const message = (await page.locator('.av-error').innerText()).trim()
    if (/频繁|限流|稍后/.test(message)) {
      await page.waitForTimeout(15000)
      await page.fill('input[name=password]', PASS)
      await page.click('button[type=submit]')
      await page.waitForTimeout(2500)
    }
  }
  await expect(page, `${role} 未落到 v2 首页`).toHaveURL(new RegExp(`/v2/${role}/`))
  const state = await context.storageState()
  await context.close()
  return state
}

function unwrap<T = unknown>(body: unknown): T {
  const record = (body || {}) as Record<string, unknown>
  return (record.data ?? record) as T
}

function pickList(body: unknown): Array<Record<string, unknown>> {
  const data = unwrap<Record<string, unknown>>(body)
  if (Array.isArray(data)) return data as Array<Record<string, unknown>>
  const list = (data.list || data.records || data.rows) as unknown
  return Array.isArray(list) ? (list as Array<Record<string, unknown>>) : []
}

async function startFromRow(
  page: import('@playwright/test').Page,
  row: import('@playwright/test').Locator,
) {
  await row.getByRole('button', { name: /开始作答|继续作答/ }).click()
  // 开考/续考会先弹确认层（V2Modal）；出现就点确认，不出现就直接进答题
  const dialog = page.locator('.v2-modal-panel')
  await dialog.waitFor({ state: 'visible', timeout: 3000 }).catch(() => {})
  if (await dialog.count()) {
    await dialog
      .getByRole('button', { name: /确定|开始|继续|进入/ })
      .first()
      .click()
  }
}

async function ensureExamPaper(api: APIRequestContext): Promise<string> {
  const papers = await api.get('/api/exam-papers?page=1&size=200')
  expect(papers.ok(), '试卷列表接口不可用').toBeTruthy()
  const existing = pickList(await papers.json()).find((row) =>
    String(row.title || '').startsWith(PAPER_PREFIX),
  )
  if (existing) return String(existing.title)

  const questions = await api.get(`/api/questions?courseId=${EXAM_COURSE_ID}&page=1&size=2`)
  expect(questions.ok(), '题库接口不可用').toBeTruthy()
  const questionRows = pickList(await questions.json())
  if (!questionRows.length) return ''

  const title = `${PAPER_PREFIX}-${EXAM_COURSE_ID}`
  const created = await api.post('/api/exam-papers', {
    data: {
      courseId: EXAM_COURSE_ID,
      title,
      description: 'v2 主链 e2e 自动取证卷；发布态后端无删除端点',
      duration: 30,
      totalScore: 100,
    },
  })
  expect(created.ok(), '建卷失败').toBeTruthy()
  const paperId = Number(unwrap<Record<string, unknown>>(await created.json()).id)
  expect(paperId, '建卷未返回 id').toBeGreaterThan(0)

  const payload = {
    questions: questionRows.slice(0, 2).map((row, index) => ({
      questionId: Number(row.id),
      points: 50,
      orderIndex: index,
    })),
  }
  const saved = await api.post(`/api/exam-papers/${paperId}/questions`, { data: payload })
  expect(saved.ok(), '写题失败').toBeTruthy()
  const published = await api.post(`/api/exam-papers/${paperId}/publish`)
  expect(published.ok(), '发布失败').toBeTruthy()
  return title
}

test.describe.serial('v2 核心链路（真栈）', () => {
  test.beforeAll(async ({ browser }) => {
    states = {
      admin: await loginState(browser, 'admin'),
      teacher: await loginState(browser, 'teacher'),
      student: await loginState(browser, 'student'),
    }
  })

  test('管理员用户治理页是真实读数，不戴示例数据章', async ({ browser }) => {
    const context = await browser.newContext({ baseURL: BASE, storageState: states!.admin })
    const page = await context.newPage()
    await page.goto('/v2/admin/users', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.users-body')).toBeVisible()
    await expect(page.locator('.page-kicker .chip')).toHaveText('接口数据')
    await expect(page.locator('.users-body tbody tr').first()).toBeVisible()
    expect(await page.locator('.users-body tbody tr').count()).toBeGreaterThan(0)
    await expect(page.locator('body')).not.toContainText('示例数据')
    await context.close()
  })

  test('教师收件箱三块真读数，题库有导入导出入口', async ({ browser }) => {
    const context = await browser.newContext({ baseURL: BASE, storageState: states!.teacher })
    const page = await context.newPage()
    await page.goto('/v2/teacher/inbox', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.inbox-body')).toBeVisible()
    await expect(page.locator('[data-block]')).toHaveCount(3)
    await expect(page.locator('.page-kicker .chip')).toHaveText('接口数据')

    await page.goto('/v2/teacher/bank', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('button', { name: '导入', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '导出', exact: true })).toBeVisible()
    await expect(page.locator('body')).not.toContainText('示例数据')
    await context.close()
  })

  test('学生任务与消息是真实读数，不戴示例数据章', async ({ browser }) => {
    const context = await browser.newContext({ baseURL: BASE, storageState: states!.student })
    const page = await context.newPage()
    await page.goto('/v2/student/tasks', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.tasks-body')).toBeVisible()
    await expect(page.locator('.page-kicker .chip')).toHaveText('接口数据')

    await page.goto('/v2/student/messages', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.messages-body')).toBeVisible()
    await expect(page.locator('body')).not.toContainText('示例数据')
    await context.close()
  })

  test('其余核心页可打开并读到真数据（上传会话／试卷库／成绩管理）', async ({ browser }) => {
    const admin = await browser.newContext({ baseURL: BASE, storageState: states!.admin })
    const adminPage = await admin.newPage()
    await adminPage.goto('/v2/admin/uploads', { waitUntil: 'domcontentloaded' })
    await expect(adminPage.getByText(/上传会话/).first()).toBeVisible()
    await expect(adminPage.locator('body')).not.toContainText('示例数据')
    await admin.close()

    const teacher = await browser.newContext({ baseURL: BASE, storageState: states!.teacher })
    const teacherPage = await teacher.newPage()
    await teacherPage.goto('/v2/teacher/exams', { waitUntil: 'domcontentloaded' })
    await expect(teacherPage.getByRole('heading', { name: '试卷库' })).toBeVisible()
    await teacherPage.goto('/v2/teacher/grades', { waitUntil: 'domcontentloaded' })
    await expect(teacherPage.getByRole('heading', { name: '成绩管理' })).toBeVisible()
    await expect(teacherPage.locator('body')).not.toContainText('示例数据')
    await teacher.close()
  })

  test('学生开考→答题→刷新后续考（真实会话）', async ({ browser }) => {
    // 作答自动保存周期是 30 秒，这条用例必须等满一个周期
    test.setTimeout(120000)
    const api = await request.newContext({ baseURL: BASE, storageState: states!.admin })
    paperTitle = await ensureExamPaper(api)
    await api.dispose()
    test.skip(!paperTitle, '课程题库没有可用题目，无法造卷；本环境跳过开考链')

    const context = await browser.newContext({ baseURL: BASE, storageState: states!.student })
    const page = await context.newPage()
    await page.goto('/v2/student/exams', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.exam-panel')).toBeVisible()

    const row = page.locator('.exam-row', { hasText: paperTitle })
    await expect(row, '学生考试列表里没有刚发布的卷子').toBeVisible()
    await startFromRow(page, row)

    await expect(page.locator('.question')).toBeVisible()
    // 进入答题后首帧还会拉一次会话/题目，等它稳定再点，避免元素被重渲染打断
    await page.waitForTimeout(2000)
    const firstOption = page.locator('.q-options input[type=radio]').first()
    if (!(await firstOption.isChecked())) {
      await page.locator('.q-options label').first().click()
    }
    await expect(firstOption).toBeChecked()
    await expect(page.locator('.sheet-progress')).toContainText('已作答 1')
    // 作答按 30 秒周期自动保存；等一个周期再刷新，验的是服务端真的存下了
    await page.waitForTimeout(32000)

    // 刷新后重新进入：服务端会话仍在，选项应回填
    await page.reload({ waitUntil: 'domcontentloaded' })
    await expect(page.locator('.exam-panel')).toBeVisible()
    const rowAfterReload = page.locator('.exam-row', { hasText: paperTitle })
    await expect(rowAfterReload).toBeVisible()
    await startFromRow(page, rowAfterReload)
    await expect(page.locator('.question')).toBeVisible()
    await expect(page.locator('.sheet-progress')).toContainText('已作答 1')
    await expect(page.locator('.q-options input[type=radio]').first()).toBeChecked()
    await context.close()
  })
})
