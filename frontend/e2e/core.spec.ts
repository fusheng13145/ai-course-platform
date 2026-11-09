import {
  test,
  expect,
  request,
  type APIRequestContext,
  type Browser,
  type BrowserContext,
} from '@playwright/test'
import { SITE_BASE, DEMO_PASS } from './test-helpers'

/**
 * 核心链路真栈巡检（L-02 的 M-04／M-11 段 + M-02 的开考/续考段）。
 *
 * 前置：本机起过后端（2026-11-09 起 V4 批并行端口方案：8081 + VITE_PROXY_TARGET），
 * 演示账号 admin/teacher/student 可用（V18 演示种子清除后的正式库口径；锚点课程＝课程 1）。
 * 口径：断言真实 API 读数与真实状态迁移，不打桩；不交卷（避免留下不可撤销的判分记录）。
 * 说明：开考用的试卷每轮新建并发布，跑完在 afterAll 按 id 删除（发布态的删除端点实测存在
 * 且真删），不再往库里留取证卷；除这张卷外不做其它写操作。
 */

const BASE = SITE_BASE
const PASS = DEMO_PASS
const ACCOUNTS = {
  admin: process.env.PW_ADMIN_USER || 'admin',
  // V18 清除演示种子后，ts01/st001 已不存在；正式库的 teacher/student 与课程 1 的
  // 师生/选课关系齐备（teacher id=2 任课程 1，student id=4 对课程 1 为 APPROVED 在读）。
  teacher: process.env.PW_TEACHER_USER || 'teacher',
  student: process.env.PW_STUDENT_USER || 'student',
} as const
type Role = keyof typeof ACCOUNTS

const PAPER_PREFIX = 'E2E-开考续考-守护卷'
// V18 后锚点课程＝课程 1（Python 程序设计）：teacher 任教、student APPROVED 在读、题库 3 题
const EXAM_COURSE_ID = 1

type StorageState = Awaited<ReturnType<BrowserContext['storageState']>>
let states: Record<Role, StorageState> | null = null
let paperTitle = ''
/** 本轮自己创建并发布的那张取证卷 id；afterAll 只按这一个 id 删除 */
let createdPaperId = 0

async function loginState(browser: Browser, role: Role) {
  const context = await browser.newContext({ baseURL: BASE })
  const page = await context.newPage()
  await page.goto('/login', { waitUntil: 'domcontentloaded' })
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
  await expect(page, `${role} 未落到 首页`).toHaveURL(new RegExp(`/${role}/`))
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
  // 开考/续考会先弹确认层（Modal）；出现就点确认，不出现就直接进答题
  const dialog = page.locator('.modal-panel')
  await dialog.waitFor({ state: 'visible', timeout: 3000 }).catch(() => {})
  if (await dialog.count()) {
    await dialog
      .getByRole('button', { name: /确定|开始|继续|进入/ })
      .first()
      .click()
  }
}

async function ensureExamPaper(
  teacherApi: APIRequestContext,
  adminApi: APIRequestContext,
): Promise<string> {
  // 每次跑新建一张独立卷：旧卷被自动交卷后按钮会变成「已完成」，复用会假红。
  // 记下本轮自己的卷 id，跑完由 afterAll 按 id 删除，不留取证卷。
  // 身份分工按 WP6 裁定：管理员只读治理、不得直接发布教学活动（/publish 对 ADMIN 返回 403），
  // 所以建卷→写题→提交审核走课程授课教师，审核通过走管理员 PATCH /approve。
  const questions = await teacherApi.get(`/api/questions?courseId=${EXAM_COURSE_ID}&page=1&size=2`)
  expect(questions.ok(), '题库接口不可用').toBeTruthy()
  const questionRows = pickList(await questions.json())
  if (!questionRows.length) return ''

  const title = `${PAPER_PREFIX}-${EXAM_COURSE_ID}-${Date.now()}`
  const created = await teacherApi.post('/api/exam-papers', {
    data: {
      courseId: EXAM_COURSE_ID,
      title,
      description: '主链 e2e 自动取证卷；跑完按 id 自清理',
      duration: 30,
      totalScore: 100,
    },
  })
  expect(created.ok(), '建卷失败').toBeTruthy()
  const paperId = Number(unwrap<Record<string, unknown>>(await created.json()).id)
  expect(paperId, '建卷未返回 id').toBeGreaterThan(0)
  createdPaperId = paperId

  const payload = {
    questions: questionRows.slice(0, 2).map((row, index) => ({
      questionId: Number(row.id),
      points: 50,
      orderIndex: index,
    })),
  }
  const saved = await teacherApi.post(`/api/exam-papers/${paperId}/questions`, { data: payload })
  expect(saved.ok(), '写题失败').toBeTruthy()
  const submitted = await teacherApi.post(`/api/exam-papers/${paperId}/publish`)
  expect(submitted.ok(), '提交审核失败').toBeTruthy()
  const approved = await adminApi.patch(`/api/exam-papers/${paperId}/approve`)
  expect(approved.ok(), '审核通过失败').toBeTruthy()
  return title
}

test.describe.serial('核心链路（真栈）', () => {
  test.beforeAll(async ({ browser }) => {
    states = {
      admin: await loginState(browser, 'admin'),
      teacher: await loginState(browser, 'teacher'),
      student: await loginState(browser, 'student'),
    }
  })

  test.afterAll(async () => {
    // 只删本轮 ensureExamPaper 自己造的那一张：删前按 id 读回、标题前缀复核后才动手，
    // 绝不碰种子卷与他人历史卷（删除对题目行／考试会话／监考事件是级联语义）。
    if (!createdPaperId || !states) return
    const paperId = createdPaperId
    createdPaperId = 0
    const api = await request.newContext({ baseURL: BASE, storageState: states.admin })
    // 注意不能用 unwrap：data 为 null 时它会回落成整个响应体，「已删干净」会被读成「仍在库」
    const dataOf = async (res: Awaited<ReturnType<APIRequestContext['get']>>) =>
      ((await res.json().catch(() => ({}))) as Record<string, unknown>).data ?? null
    const before = await api.get(`/api/exam-papers/${paperId}`)
    const title = String(((await dataOf(before)) as Record<string, unknown> | null)?.title ?? '')
    if (!title.startsWith(PAPER_PREFIX)) {
      console.log(`[core] 跳过清理：卷 ${paperId} 的标题「${title}」不带本轮取证卷前缀`)
      await api.dispose()
      return
    }
    const removed = await api.delete(`/api/exam-papers/${paperId}`)
    // 2026-11-01 项目主裁定（B-13 ⑳）：已产生 exam_sessions 的试卷**不得物理删除**，后端返回 409，
    // 避免 fk 级联销毁题目行／考试会话／监考事件；409 文案指定的出路是「改为归档」。
    // 本用例必然开考（就是那条 30 秒自动保存链），所以 409 是**设计内的预期结果**，不是缺陷。
    // 归档同样能把卷撤出学生可见面（学生列表只筛 status=PUBLISHED），故按裁定走归档收口。
    let archived = false
    if (!removed.ok()) {
      expect(removed.status(), `删除取证卷失败且不是裁定允许的 409：HTTP ${removed.status()}`).toBe(
        409,
      )
      // 实体带 @NotBlank(title)，只发 status 会被 400「试卷标题不能为空」挡回；
      // 带上刚读回的原标题即可（updateById 只覆盖非 null 字段，不会清掉其它列）。
      const put = await api.put(`/api/exam-papers/${paperId}`, {
        data: { title, status: 'ARCHIVED' },
      })
      expect(put.ok(), `归档取证卷失败：HTTP ${put.status()}`).toBeTruthy()
      archived = true
    }
    const gone = await api.get(`/api/exam-papers/${paperId}`)
    const residual = (await dataOf(gone)) as Record<string, unknown> | null
    console.log(
      `[core] 取证卷自清理：DELETE → HTTP ${removed.status()}` +
        (archived ? '（已产生考试记录，按裁定改归档）' : '') +
        `；读回 HTTP ${gone.status()} data=${archived ? `status=${String(residual?.status)}` : residual ? '仍在库' : 'null（已删干净）'}`,
    )
    if (archived) {
      expect(String(residual?.status), `归档后 ${paperId} 的状态应为 ARCHIVED`).toBe('ARCHIVED')
    } else {
      expect(residual, `删除后 ${paperId} 仍能读到试卷，自清理未生效`).toBeNull()
    }
    await api.dispose()
  })

  test('管理员用户治理页是真实读数，不戴示例数据章', async ({ browser }) => {
    const context = await browser.newContext({ baseURL: BASE, storageState: states!.admin })
    const page = await context.newPage()
    await page.goto('/admin/users', { waitUntil: 'domcontentloaded' })
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
    await page.goto('/teacher/inbox', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.inbox-body')).toBeVisible()
    await expect(page.locator('[data-block]')).toHaveCount(3)
    await expect(page.locator('.page-kicker .chip')).toHaveText('接口数据')

    await page.goto('/teacher/bank', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('button', { name: '导入', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: '导出', exact: true })).toBeVisible()
    await expect(page.locator('body')).not.toContainText('示例数据')
    await context.close()
  })

  test('学生任务与消息是真实读数，不戴示例数据章', async ({ browser }) => {
    const context = await browser.newContext({ baseURL: BASE, storageState: states!.student })
    const page = await context.newPage()
    await page.goto('/student/tasks', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.tasks-body')).toBeVisible()
    await expect(page.locator('.page-kicker .chip')).toHaveText('接口数据')

    await page.goto('/student/messages', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.messages-body')).toBeVisible()
    await expect(page.locator('body')).not.toContainText('示例数据')
    await context.close()
  })

  test('其余核心页可打开并读到真数据（上传会话／AI 用量／试卷库／成绩管理）', async ({
    browser,
  }) => {
    const admin = await browser.newContext({ baseURL: BASE, storageState: states!.admin })
    const adminPage = await admin.newPage()
    await adminPage.goto('/admin/uploads', { waitUntil: 'domcontentloaded' })
    await expect(adminPage.getByText(/上传会话/).first()).toBeVisible()
    await expect(adminPage.locator('body')).not.toContainText('示例数据')

    // M-14：AI 用量页从「待扩展」空态转真后，必须真的走接口渲染，而不是又落回空态
    await adminPage.goto('/admin/ai-usage', { waitUntil: 'domcontentloaded' })
    await expect(adminPage.locator('.ai-usage-body')).toBeVisible()
    await expect(adminPage.locator('[data-testid="page-not-provided"]')).toHaveCount(0)
    await expect(adminPage.getByText('数据源', { exact: false }).first()).toBeVisible()
    await expect(adminPage.locator('body')).not.toContainText('示例数据')
    await admin.close()

    const teacher = await browser.newContext({ baseURL: BASE, storageState: states!.teacher })
    const teacherPage = await teacher.newPage()
    await teacherPage.goto('/teacher/exams', { waitUntil: 'domcontentloaded' })
    await expect(teacherPage.getByRole('heading', { name: '试卷库' })).toBeVisible()
    await teacherPage.goto('/teacher/grades', { waitUntil: 'domcontentloaded' })
    await expect(teacherPage.getByRole('heading', { name: '成绩管理' })).toBeVisible()
    await expect(teacherPage.locator('body')).not.toContainText('示例数据')
    await teacher.close()
  })

  test('学生开考→答题→刷新后续考（真实会话）', async ({ browser }) => {
    // 作答自动保存周期是 30 秒，这条用例必须等满一个周期
    test.setTimeout(120000)
    const teacherApi = await request.newContext({
      baseURL: BASE,
      storageState: states!.teacher,
    })
    const api = await request.newContext({ baseURL: BASE, storageState: states!.admin })
    paperTitle = await ensureExamPaper(teacherApi, api)
    await teacherApi.dispose()
    await api.dispose()
    test.skip(!paperTitle, '课程题库没有可用题目，无法造卷；本环境跳过开考链')

    const context = await browser.newContext({ baseURL: BASE, storageState: states!.student })
    const page = await context.newPage()
    await page.goto('/student/exams', { waitUntil: 'domcontentloaded' })
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
