import {
  expect,
  test,
  request,
  type APIRequestContext,
  type Browser,
  type BrowserContext,
  type Page,
} from '@playwright/test'
import { loginAs } from './test-helpers'

/**
 * v2 教师写链路真栈取证（教学主链：建课→章节→任务发布→提交→批阅／退回→成绩）。
 *
 * 前置：本机后端 8080 与前端 5173 在线，V1 种子账号 teacher／student／admin 可用
 * （口令只经 test-helpers.loginAs 使用，本文件不出现凭据）。
 * 口径：每个写操作都由真浏览器点 UI 发出；断言取接口或页面的真实读数（状态字段与分数本身），
 * 不以「出现成功 toast」自证。本轮造的课／章节／任务在 afterAll 按 id 自清理，
 * 删前用名称前缀复核，只动自己造的那一套。
 * 失败归因：逐步区分「后端拒绝（HTTP 状态＋响应体 code/message）」与「前端根本没发请求」。
 */

const BASE = process.env.PW_BASE_URL || 'http://localhost:5173'
const PREFIX = 'E2E-教师写链-'
const STAMP = Date.now()

type Role = 'admin' | 'teacher' | 'student'
type StorageState = Awaited<ReturnType<BrowserContext['storageState']>>

let states: Record<Role, StorageState> | null = null
let apiAdmin: APIRequestContext | null = null
let apiTeacher: APIRequestContext | null = null
let apiStudent: APIRequestContext | null = null

const course = { id: 0, name: `${PREFIX}课-${STAMP}`, code: `E2E-WRITE-${STAMP}`, teacherId: 0 }
const chapter = { id: 0, title: `${PREFIX}章节-${STAMP}` }
const task = { id: 0, title: `${PREFIX}任务-${STAMP}`, score: 77.5 }
const submission = { id: 0, reason: `${PREFIX}退回原因-${STAMP}` }
/** 前置步骤的完成标记：后续步骤据此 skip，而不是把「没跑到」读成「后端拒绝」 */
const chain = { graded: false }

interface ApiCall {
  method: string
  path: string
  status: number
  code: string
  message: string
}

/** 抹掉响应体里可能带出的凭据字段，避免口令／令牌进报告与终端输出 */
function redact(text: string): string {
  return text
    .replace(/("(?:password|token|refreshToken|accessToken)"\s*:\s*)"[^"]*"/gi, '$1"[已隐去]"')
    .slice(0, 240)
}

function parseBody(raw: string): { code: string; message: string; data: unknown } {
  try {
    const body = JSON.parse(raw) as Record<string, unknown>
    return {
      code: String(body.code ?? ''),
      message: redact(String(body.message ?? '')),
      data: body.data ?? null,
    }
  } catch {
    return { code: '', message: redact(raw), data: null }
  }
}

/** 记下页面上所有 /api 往来，供「前端到底发没发这个请求」取证 */
function watchApi(page: Page): ApiCall[] {
  const calls: ApiCall[] = []
  page.on('response', (res) => {
    const path = new URL(res.url()).pathname
    if (!path.startsWith('/api/')) return
    const method = res.request().method()
    const status = res.status()
    const push = (code: string, message: string) =>
      calls.push({ method, path, status, code, message })
    res
      .text()
      .then((raw) => {
        const parsed = parseBody(raw)
        push(parsed.code, parsed.message)
      })
      .catch(() => push('', '<响应体不可读>'))
  })
  return calls
}

function lastCall(calls: ApiCall[], method: string, pattern: RegExp): string {
  const hit = [...calls].reverse().find((item) => item.method === method && pattern.test(item.path))
  return hit ? `${hit.method} ${hit.path}→HTTP ${hit.status} code=${hit.code}` : '无'
}

function tailCalls(calls: ApiCall[], limit = 8): string {
  return calls
    .slice(-limit)
    .map((item) => `${item.method} ${item.path}→${item.status}`)
    .join(' | ')
}

/** 点 UI 触发的写操作：把「前端没发请求」与「后端拒绝」分成两类报错 */
async function clickWrite(
  page: Page,
  calls: ApiCall[],
  method: string,
  pattern: RegExp,
  stepName: string,
  action: () => Promise<void>,
): Promise<Record<string, unknown>> {
  const waiter = page
    .waitForResponse(
      (res) => res.request().method() === method && pattern.test(new URL(res.url()).pathname),
      { timeout: 20000 },
    )
    .catch(() => null)
  await action()
  const res = await waiter
  if (!res) {
    throw new Error(
      `${stepName}｜前端根本没发请求：期望 ${method} ${pattern}；` +
        `同类调用观测=${lastCall(calls, method, pattern)}；近期 API=${tailCalls(calls)}`,
    )
  }
  const path = new URL(res.url()).pathname
  const parsed = parseBody(await res.text().catch(() => ''))
  if (res.status() < 200 || res.status() >= 300 || (parsed.code && parsed.code !== '200')) {
    throw new Error(
      `${stepName}｜后端拒绝：${method} ${path} → HTTP ${res.status()}，` +
        `响应体 code=${parsed.code || '无'} message=${parsed.message || '无'}`,
    )
  }
  return (parsed.data as Record<string, unknown>) ?? {}
}

/** 纯 API 读数（断言用）；删除类只在 afterAll 走这里，且只删自建数据 */
async function apiJson(
  api: APIRequestContext,
  method: string,
  path: string,
  data?: unknown,
): Promise<{ status: number; code: string; message: string; data: unknown }> {
  const res = await api.fetch(path, { method, data })
  const raw = await res.text().catch(() => '')
  const parsed = parseBody(raw)
  return { status: res.status(), code: parsed.code, message: parsed.message, data: parsed.data }
}

function rowsOf(payload: unknown): Array<Record<string, unknown>> {
  if (Array.isArray(payload)) return payload as Array<Record<string, unknown>>
  const record = (payload || {}) as Record<string, unknown>
  const list = (record.list ?? record.records ?? record.rows) as unknown
  return Array.isArray(list) ? (list as Array<Record<string, unknown>>) : []
}

async function taskSubmissionRows(): Promise<Array<Record<string, unknown>>> {
  const read = await apiJson(apiTeacher!, 'GET', `/api/submissions/task/${task.id}?page=1&size=20`)
  return rowsOf(read.data)
}

async function submissionRow(id: number): Promise<Record<string, unknown> | undefined> {
  return (await taskSubmissionRows()).find((item) => Number(item.id) === id)
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

/**
 * 等这条提交的异步 AI 预批回写落地，再放行教师写法。
 * 实测：POST /submissions 之后后端异步跑 aiGradeSubmission——先整行读回、约 3 秒后原样
 * updateById 写回，教师若落在这个窗口里退回／批阅，状态会被冲回 SUBMITTED（分数因
 * updateById 跳过 null 列而残留）。所以写链路要按「预批已落库」排序，而不是赌时序。
 */
async function waitForAiPreGrade(submissionId: number, timeoutMs = 30000): Promise<boolean> {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    const row = await submissionRow(submissionId)
    const data = String(row?.submissionData ?? '')
    if (row && (String(row.aiFeedback ?? '') || data.includes('aiGradedAt'))) return true
    await sleep(1000)
  }
  return false
}

/** 写法落库后的稳定性复核：状态若在观察窗内被别的写入冲掉，这里就是取证点 */
async function expectStatusHolds(submissionId: number, expected: string, windowMs = 8000) {
  const seen: string[] = []
  const deadline = Date.now() + windowMs
  while (Date.now() < deadline) {
    const row = await submissionRow(submissionId)
    const status = String(row?.status ?? '<无记录>')
    if (!seen.length || seen[seen.length - 1] !== status) seen.push(status)
    if (seen.length > 1) break
    await sleep(1500)
  }
  expect(
    seen.length === 1 && seen[0] === expected,
    `接口读数不稳定：提交 ${submissionId} 在写法之后 ${windowMs}ms 内状态轨迹为 ${seen.join('→')}（期望一直是 ${expected}）`,
  ).toBeTruthy()
}

async function stateOf(browser: Browser, role: Role): Promise<StorageState> {
  const context = await browser.newContext({ baseURL: BASE })
  const page = await context.newPage()
  // loginAs 已勾「记住我」并对 429 限流显式报错；storageState 需要 localStorage＋会话 Cookie 双份
  await loginAs(page, role.toUpperCase() as 'STUDENT' | 'TEACHER' | 'ADMIN')
  const state = await context.storageState()
  await context.close()
  return state
}

function fieldInput(page: Page, labelText: string, tag: string) {
  return page.locator('.field', { hasText: labelText }).locator(tag).first()
}

function panelField(panel: ReturnType<Page['locator']>, labelText: string, tag: string) {
  return panel.locator('.field', { hasText: labelText }).locator(tag).first()
}

test.describe.serial('v2 教师写链路（真栈）', () => {
  test.beforeAll(async ({ browser }) => {
    states = {
      admin: await stateOf(browser, 'admin'),
      teacher: await stateOf(browser, 'teacher'),
      student: await stateOf(browser, 'student'),
    }
    apiAdmin = await request.newContext({ baseURL: BASE, storageState: states.admin })
    apiTeacher = await request.newContext({ baseURL: BASE, storageState: states.teacher })
    apiStudent = await request.newContext({ baseURL: BASE, storageState: states.student })
  })

  test.afterAll(async () => {
    if (!apiTeacher || !apiAdmin) return
    const log: string[] = []
    if (task.id) {
      const read = await apiJson(apiTeacher, 'GET', `/api/tasks/${task.id}`)
      const owned = String((read.data as Record<string, unknown>)?.title || '').startsWith(PREFIX)
      if (owned) {
        const done = await apiJson(apiTeacher, 'DELETE', `/api/tasks/${task.id}`)
        log.push(`task=${task.id}→HTTP ${done.status} code=${done.code}`)
      } else log.push(`task=${task.id} 标题不含本轮前缀，跳过删除`)
    }
    if (chapter.id) {
      const done = await apiJson(apiTeacher, 'DELETE', `/api/chapters/${chapter.id}`)
      log.push(`chapter=${chapter.id}→HTTP ${done.status} code=${done.code}`)
    }
    if (course.id) {
      const read = await apiJson(apiTeacher, 'GET', `/api/courses/${course.id}`)
      const owned = String((read.data as Record<string, unknown>)?.name || '').startsWith(PREFIX)
      if (owned) {
        const done = await apiJson(apiAdmin, 'DELETE', `/api/courses/${course.id}`)
        log.push(`course=${course.id}→HTTP ${done.status} code=${done.code}`)
      } else log.push(`course=${course.id} 名称不含本轮前缀，跳过删除`)
    }
    console.log(`[v2-teacher-write] afterAll 自清理读数：${log.join(' ; ') || '本轮未造数据'}`)
    await apiTeacher.dispose()
    await apiStudent?.dispose()
    await apiAdmin.dispose()
  })

  test('教师经 UI 建课，接口读数与后续审批／选课状态一致', async ({ browser }) => {
    const context = await browser.newContext({ baseURL: BASE, storageState: states!.teacher })
    const page = await context.newPage()
    const calls = watchApi(page)
    await page.goto('/teacher/courses/create', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('heading', { name: '创建课程' })).toBeVisible()

    const created = await clickWrite(
      page,
      calls,
      'POST',
      /\/api\/courses$/,
      '教师建课',
      async () => {
        await fieldInput(page, '课程名称', 'input').fill(course.name)
        await fieldInput(page, '课程编码', 'input').fill(course.code)
        await fieldInput(page, '课程分类', 'select').selectOption('其他')
        await page.getByRole('button', { name: '提交审核' }).click()
      },
    )
    course.id = Number(created.id)
    course.teacherId = Number(created.teacherId)
    expect(course.id, '建课未返回课程 id').toBeGreaterThan(0)
    expect(String(created.name), '建课响应里的课程名不等于 UI 填的值').toBe(course.name)
    expect(String(created.approvalStatus), '新课程应为待审批').toBe('PENDING')

    // 审批只有管理端写法（教师侧无入口）；学生自选课在 v2 没有 UI 入口（按钮是演示 toast），
    // 两者按 API 落账，属本轮取证声明的前置条件而非「教师写链路」断言对象。
    const approved = await apiJson(apiAdmin!, 'POST', `/api/courses/${course.id}/approve`)
    expect(approved.status, `审批被拒：code=${approved.code} message=${approved.message}`).toBe(200)
    const enrolled = await apiJson(apiStudent!, 'POST', `/api/student/courses/${course.id}/enroll`)
    expect(enrolled.status, `学生选课被拒：code=${enrolled.code} message=${enrolled.message}`).toBe(
      200,
    )

    const read = await apiJson(apiTeacher!, 'GET', `/api/courses/${course.id}`)
    const detail = read.data as Record<string, unknown>
    expect(String(detail?.approvalStatus), '审批后课程状态未落库').toBe('APPROVED')
    expect(Number(detail?.teacherId), '课程未绑到当前登录教师').toBe(course.teacherId)
    await context.close()
  })

  test('教师经 UI 加章节，章节树接口与页面都读到该章节', async ({ browser }) => {
    test.skip(!course.id, '前置建课未成功，写链路后续步骤无从继续')
    const context = await browser.newContext({ baseURL: BASE, storageState: states!.teacher })
    const page = await context.newPage()
    const calls = watchApi(page)
    await page.goto(`/teacher/courses/${course.id}/chapters`, { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.chapters')).toBeVisible()

    await page.getByRole('button', { name: '新增顶级章节' }).click()
    const panel = page.locator('.v2-modal-panel')
    await expect(panel).toBeVisible()
    const created = await clickWrite(
      page,
      calls,
      'POST',
      /\/api\/chapters$/,
      '新增章节',
      async () => {
        await panelField(panel, '标题', 'input').fill(chapter.title)
        await panel.getByRole('button', { name: '保存' }).click()
      },
    )
    chapter.id = Number(created.id)
    expect(chapter.id, '章节创建未返回 id').toBeGreaterThan(0)
    expect(String(created.title), '章节创建响应里的标题不等于 UI 填的值').toBe(chapter.title)

    const tree = await apiJson(
      apiTeacher!,
      'GET',
      `/api/teacher/courses/${course.id}/chapters/tree`,
    )
    const nodes = rowsOf(tree.data)
    const mine = nodes.find((node) => Number(node.id) === chapter.id)
    expect(mine, `章节树接口里没有刚建的章节（该课程现有 ${nodes.length} 节）`).toBeTruthy()
    expect(String(mine?.title), '章节树里的标题不等于 UI 填的值').toBe(chapter.title)

    await page.reload({ waitUntil: 'domcontentloaded' })
    await expect(page.locator('.chapter-tree'), '章节页未渲染刚创建的章节').toContainText(
      chapter.title,
    )
    await context.close()
  })

  test('教师经 UI 建任务并发布，任务列表接口读数是已发布', async ({ browser }) => {
    test.skip(!course.id, '前置建课未成功，写链路后续步骤无从继续')
    const context = await browser.newContext({ baseURL: BASE, storageState: states!.teacher })
    const page = await context.newPage()
    const calls = watchApi(page)
    await page.goto(`/teacher/courses/${course.id}/tasks`, { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('button', { name: '新建任务' })).toBeVisible()

    await page.getByRole('button', { name: '新建任务' }).click()
    const panel = page.locator('.v2-modal-panel')
    await expect(panel).toBeVisible()
    const created = await clickWrite(page, calls, 'POST', /\/api\/tasks$/, '新建任务', async () => {
      await panelField(panel, '标题', 'input').fill(task.title)
      await panelField(panel, '描述', 'textarea').fill('e2e 教师写链路取证任务')
      await panel.getByRole('button', { name: '创建' }).click()
    })
    task.id = Number(created.id)
    expect(task.id, '任务创建未返回 id').toBeGreaterThan(0)
    expect((created.isPublished as boolean) ?? null, '新建任务不应一开始就是已发布态').toBeFalsy()

    const row = page.locator('tbody tr', { hasText: task.title })
    await expect(row, '任务列表里没有刚创建的任务').toBeVisible()
    await clickWrite(
      page,
      calls,
      'POST',
      new RegExp(`/api/tasks/${task.id}/publish$`),
      '发布任务',
      async () => {
        await row.getByRole('button', { name: '发布', exact: true }).click()
      },
    )
    await expect(
      row.locator('.chip', { hasText: '已发布' }),
      'UI 行内发布态未翻转成已发布',
    ).toBeVisible()

    const read = await apiJson(
      apiTeacher!,
      'GET',
      `/api/tasks?courseId=${course.id}&page=1&size=20`,
    )
    const mine = rowsOf(read.data).find((item) => Number(item.id) === task.id)
    expect(mine, '任务列表接口里没有刚发布的任务').toBeTruthy()
    expect(mine?.isPublished, '接口读数：任务未处于已发布态').toBe(true)
    expect(String(mine?.status), '接口读数：任务状态不是 PUBLISHED').toBe('PUBLISHED')
    await context.close()
  })

  test('学生经 UI 提交，教师侧提交列表读数转为待批', async ({ browser }) => {
    test.skip(!task.id, '前置任务未发布，写链路后续步骤无从继续')
    const context = await browser.newContext({ baseURL: BASE, storageState: states!.student })
    const page = await context.newPage()
    const calls = watchApi(page)
    await page.goto(`/student/courses/${course.id}/tasks`, { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.tasks')).toBeVisible()

    const row = page.locator('tbody tr', { hasText: task.title })
    await expect(row, '学生作业列表里没有教师刚发布的那条任务').toBeVisible()
    await row.getByRole('button', { name: '查看详情' }).click()
    const panel = page.locator('.v2-modal-panel')
    await expect(panel.locator('.submit-box'), '作业详情里没有提交表单').toBeVisible()

    const created = await clickWrite(
      page,
      calls,
      'POST',
      /\/api\/submissions$/,
      '学生提交作业',
      async () => {
        await panel
          .locator('.submit-box textarea')
          .fill(`${PREFIX}学生作答-${STAMP}：本条按接口读数复核。`)
        await panel.getByRole('button', { name: '提交作业' }).click()
      },
    )
    submission.id = Number(created.id)
    expect(submission.id, '提交未返回记录 id').toBeGreaterThan(0)
    expect(String(created.status), '提交响应里的状态不是 SUBMITTED').toBe('SUBMITTED')

    const rows = await taskSubmissionRows()
    const mine = rows.find((item) => Number(item.id) === submission.id)
    expect(mine, `教师侧提交列表里没有这条提交（现有 ${rows.length} 条）`).toBeTruthy()
    expect(String(mine?.status), '接口读数：提交状态未转为待批').toBe('SUBMITTED')

    await page.reload({ waitUntil: 'domcontentloaded' })
    await expect(
      page
        .locator('tbody tr', { hasText: task.title })
        .locator('.chip', { hasText: '已提交待批改' }),
      '学生端状态标签未转为已提交待批改',
    ).toBeVisible()
    await context.close()
  })

  test('教师退回→学生重交→教师批阅打分，状态与分数都是接口读数', async ({ browser }) => {
    test.skip(!submission.id, '前置提交未成功，批阅段无从继续')
    // 两个写法各带一段异步预批等待与稳定窗
    test.setTimeout(180000)

    const teacher = await browser.newContext({ baseURL: BASE, storageState: states!.teacher })
    const tPage = await teacher.newPage()
    const tCalls = watchApi(tPage)
    // 先把上一条提交的异步 AI 预批等落库，否则教师这次的退回会被那轮回写冲掉
    const settledFirst = await waitForAiPreGrade(submission.id)
    await tPage.goto(`/teacher/courses/${course.id}/grading?taskId=${task.id}`, {
      waitUntil: 'domcontentloaded',
    })
    await expect(tPage.locator('.grade-list'), '批阅页没有出现待批阅队列').toBeVisible()

    // 退回必须排在打分之前：实测已批阅（GRADED）的提交再调 return 会被后端判 400
    await tPage.locator('.grade-row').first().click()
    const peek = tPage.locator('.peek-panel')
    await expect(peek.locator('input[aria-label=评分]'), '选中提交后未出现批阅表单').toBeVisible()
    await peek.getByRole('button', { name: '退回重做' }).click()
    await expect(peek.locator('.return-box'), '未展开退回原因表单').toBeVisible()

    const returned = await clickWrite(
      tPage,
      tCalls,
      'POST',
      new RegExp(`/api/submissions/${submission.id}/return$`),
      '教师退回',
      async () => {
        await peek.locator('textarea[aria-label=退回原因]').fill(submission.reason)
        await peek.getByRole('button', { name: '确认退回' }).click()
      },
    )
    expect(Number(returned.id), '退回命中的不是那条提交').toBe(submission.id)
    expect(String(returned.status), '退回响应里的状态不是 RETURNED').toBe('RETURNED')

    const afterReturn = await submissionRow(submission.id)
    expect(String(afterReturn?.status), '接口读数：提交未转为已退回').toBe('RETURNED')
    expect(String(afterReturn?.feedback ?? ''), '接口读数：退回原因未回写到提交记录').toContain(
      submission.reason,
    )
    // 稳定窗：若仍有预批在飞（settledFirst=false），这里会把冲回状态读成取证点
    if (settledFirst) await expectStatusHolds(submission.id, 'RETURNED')

    // 学生重交走 UI（后端按新增一次尝试处理，不是原地覆盖）
    const stu = await browser.newContext({ baseURL: BASE, storageState: states!.student })
    const sPage = await stu.newPage()
    const sCalls = watchApi(sPage)
    await sPage.goto(`/student/courses/${course.id}/tasks`, { waitUntil: 'domcontentloaded' })
    const sRow = sPage.locator('tbody tr', { hasText: task.title })
    await expect(
      sRow.locator('.chip', { hasText: '已退回待重交' }),
      '学生端未读到退回态',
    ).toBeVisible()
    await sRow.getByRole('button', { name: '查看详情' }).click()
    const sPanel = sPage.locator('.v2-modal-panel')
    await expect(sPanel.locator('.submit-box'), '退回后未重新开放提交表单').toBeVisible()
    const attempt = await clickWrite(
      sPage,
      sCalls,
      'POST',
      /\/api\/submissions$/,
      '学生重交',
      async () => {
        await sPanel.locator('.submit-box textarea').fill(`${PREFIX}重交作答-${STAMP}`)
        await sPanel.getByRole('button', { name: '提交作业' }).click()
      },
    )
    const attemptId = Number(attempt.id)
    expect(attemptId, '重交未返回新的提交 id').toBeGreaterThan(0)
    expect(Number(attempt.attemptNumber), '重交未生成第二次尝试').toBeGreaterThanOrEqual(2)

    // 教师对最新那次尝试打分（同样先等这一发的异步预批落库，避免回写冲掉批阅）
    const settledSecond = await waitForAiPreGrade(attemptId)
    await tPage.goto(`/teacher/courses/${course.id}/grading?taskId=${task.id}`, {
      waitUntil: 'domcontentloaded',
    })
    const pendingRows = (await taskSubmissionRows()).filter((item) =>
      ['SUBMITTED', 'RESUBMITTED'].includes(String(item.status)),
    )
    await expect(
      tPage.locator('.grade-list'),
      `接口读数里本任务有 ${pendingRows.length} 条待批`,
    ).toBeVisible()
    await tPage.locator('.grade-row').first().click()
    await expect(peek.locator('input[aria-label=评分]'), '重交后未出现批阅表单').toBeVisible()
    await tPage.locator('input[aria-label=评分]').fill(String(task.score))
    await tPage.locator('textarea[aria-label=评语]').fill(`${PREFIX}批阅评语-${STAMP}`)
    const graded = await clickWrite(
      tPage,
      tCalls,
      'POST',
      /\/api\/submissions\/\d+\/grade$/,
      '教师批阅打分',
      async () => {
        await tPage.getByRole('button', { name: '提交批阅' }).click()
      },
    )
    expect(Number(graded.id), '批阅命中的提交不是学生最新那次尝试').toBe(attemptId)
    expect(String(graded.status), '批阅响应里的状态不是 GRADED').toBe('GRADED')
    expect(Number(graded.score), '批阅响应里的分数不等于 UI 填入的分数').toBe(task.score)

    const afterGrade = await submissionRow(attemptId)
    expect(String(afterGrade?.status), '接口读数：该次提交未落为 GRADED').toBe('GRADED')
    expect(Number(afterGrade?.score), '接口读数：分数未落库').toBe(task.score)
    if (settledSecond) await expectStatusHolds(attemptId, 'GRADED')
    chain.graded = true

    await teacher.close()
    await stu.close()
  })

  test('学生成绩页出现这条分数，页面与接口读数一致', async ({ browser }) => {
    test.skip(!chain.graded, '前置批阅未完成，没有成绩可核')
    const read = await apiJson(apiStudent!, 'GET', '/api/student/grades')
    const courses = ((read.data as Record<string, unknown>)?.courses || []) as Array<
      Record<string, unknown>
    >
    const mine = courses.find((item) => String(item.courseId) === String(course.id))
    expect(mine, `成绩接口没有本轮造的课程（返回 ${courses.length} 门）`).toBeTruthy()
    const assignments = ((mine?.assignments as Array<Record<string, unknown>>) || []).filter(
      (item) => String(item.status) === 'GRADED' && Number(item.score) === task.score,
    )
    expect(
      assignments.length,
      `成绩接口里本课程没有 ${task.score} 分的已批阅项：${JSON.stringify(mine?.assignments ?? []).slice(0, 300)}`,
    ).toBeGreaterThan(0)
    expect(String(assignments[0].title), '成绩项标题不是本轮那条任务').toBe(task.title)

    const context = await browser.newContext({ baseURL: BASE, storageState: states!.student })
    const page = await context.newPage()
    await page.goto(`/student/courses/${course.id}/grades`, { waitUntil: 'domcontentloaded' })
    await expect(page.getByText('我的成绩'), '学生成绩页未打开').toBeVisible()
    const row = page.locator('tbody tr', { hasText: task.title })
    await expect(row, '成绩页表格里没有本轮批阅的那条').toBeVisible()
    await expect(
      row.locator('td').nth(2),
      `成绩页得分列未读到教师写入的 ${task.score} 分`,
    ).toContainText(String(task.score))
    await expect(row.locator('.chip', { hasText: '已批改' })).toBeVisible()
    await context.close()
  })
})
