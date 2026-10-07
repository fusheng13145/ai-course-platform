/**
 * Q-3：断线续考 + 实时通道重连 —— 真实浏览器闭环验证
 *
 * 【背景】Q-3 自 2026-09-14 起一直挂着「纯验证缺口：需真实浏览器/真实网络环境，
 * 本机无法闭环」，代码硬化（#18 会话复用、服务端剩余时间为准、answers 必须是 Map）
 * 都在，但从没有人**真的**在浏览器里把网络掐断跑一遍。本 spec 就是那次「跑一遍」，
 * 并作为回归护栏长期保留。
 *
 * 【验收标准】（2026-10-06 由本次验证首次成文，此前文档只写「待验证」没有判定口径）
 *  A. 实时通道可用：应用自己的 /api/ws socket 能经前端边缘（vite dev/preview 代理）完成
 *     upgrade 握手、通过后端鉴权，并把 30s 心跳发到拦截层（证明真的连通）。
 *  B. 断线期间作答不丢：网络中断时自动保存失败必须**显式告知**用户，页面不崩、作答仍在内存。
 *  C. 断线不产生副作用：自动保存失败**绝不**导致交卷（会话仍为 STARTED、服务端作答未变）。
 *  D. 恢复后补存落库：网络恢复后的下一次自动保存把断线期间的作答一并写入服务端。
 *  E. 刷新可续答：重载后重新进入同一场考试，走**恢复**路径（复用会话 + 回填作答），
 *     且倒计时以服务端剩余时间为准（不得重置为满时长 = 无限续时漏洞）。
 *  F. 断线重连退避：WS 断开后按指数退避重试（实测间隔 1s→2s→4s 递增），
 *     刷新页面后实时通道能重新建立并恢复收发（不依赖 login() 再跑一次）。
 *  G. 交卷与阅卷口径：交卷后成绩卡显示 "--" + 「成绩正在批阅中」（submit 不判分）；
 *     教师端自动阅卷后按**选项字母**判分（本卷 3 题各 10 分、题库正确答案均为 A、
 *     学生答 A/B/未答 → 得分必须恰好 10/30）。本条是 DR-25 的端到端护栏。
 *
 * 【本次验证得到的两条事实】（都已写进断言口径，不是推测）
 *  1. 浏览器的离线模拟不等于真实断网：`setOffline(true)` 会让同源 XHR 立刻
 *     net::ERR_INTERNET_DISCONNECTED（所以 REST 断线可复现），但**不会**掐断已建立的
 *     WebSocket —— 断网期间心跳照发。因此「实时通道断线」只能由测试显式 close 制造。
 *  2. 客户端对通道故障的感知依赖 30s 心跳：显式 close 后实测约 31s 才发起第 1 次重连。
 *     这是真实检测延迟（监考类实时告警在故障窗口内会静默最长 ~30s），
 *     已作为遗留项 DR-26 登记，不在本次改动范围内。
 *
 * 【为什么自建课程/试卷而不复用种子数据】所有内置试卷的起止时间都已过期
 * （列表按钮渲染成「已过期」且禁用），浏览器路径进不去；故按已证明可行的链路
 * （scripts/e2e/e2e_verify_network_course.py 环节 B/F/G）用 API 现造一份唯一命名的
 * 课程→审批→选课→题→组卷→发布，跑完 best-effort 清理。
 *
 * 【为什么 API 直连 8080 而不走前端端口】夹具只做 REST，直连后端可绕开 dev/preview
 * 代理这层变量，让失败原因只可能来自被测系统本身；浏览器侧仍走同源代理（真实用户路径）。
 */
import { test, expect, request, type APIRequestContext, type Page } from '@playwright/test'
import { loginAs } from './test-helpers'

const API_ORIGIN = process.env.E2E_API_ORIGIN || 'http://127.0.0.1:8080'
/**
 * 后端 context-path 是 /api，但 /api **不能**写进 baseURL：
 * APIRequestContext 按 `new URL(path, baseURL)` 解析，以 / 开头的 path 会把 base 上的
 * 路径段整体替换掉（实测：baseURL=http://127.0.0.1:8080/api + '/auth/login' → /auth/login → 404）。
 * 故 base 只放 origin，/api 前缀在调用侧统一补。
 */
const apiPath = (path: string) => `/api${path}`
/** V1 迁移内置演示账号口令（见 backend SeedAccounts，三级账号同口令） */
const SEED_PASSWORD = '123456'
/** useStudentExams 的自动保存节拍 */
const AUTOSAVE_INTERVAL_MS = 30_000
/** 等一次自动保存节拍：定时器周期 + 请求往返与 Playwright 事件余量 */
const TICK_TIMEOUT_MS = AUTOSAVE_INTERVAL_MS + 15_000

const stamp = Date.now()
const COURSE_CODE = `CS-Q3-${stamp}`
const PAPER_TITLE = `断线续考验证卷 ${stamp}`
const QUESTION_COUNT = 3
/** 试卷时长（分钟）；用于校验「首次进入 = 满时长」 */
const PAPER_DURATION_MIN = 30

let teacherCtx: APIRequestContext
let adminCtx: APIRequestContext
let studentCtx: APIRequestContext
let courseId = 0
let paperId = 0

async function apiLogin(username: string): Promise<APIRequestContext> {
  const ctx = await request.newContext({ baseURL: API_ORIGIN })
  const res = await ctx.post(apiPath('/auth/login'), {
    data: { username, password: SEED_PASSWORD },
  })
  const body = await res.json().catch(() => ({}))
  if (body.code !== 200) {
    throw new Error(`账号 ${username} 登录失败：HTTP ${res.status()} code=${body.code} msg=${body.message}`)
  }
  return ctx
}

/**
 * 夹具用的 REST 调用：非 200 业务码直接抛错。
 * XSRF 头按需附带（secure 姿态下后端校验双重提交；functional 姿态 CSRF_ENABLED=false 时无该 Cookie）。
 */
async function call(
  ctx: APIRequestContext,
  method: string,
  path: string,
  data?: unknown,
): Promise<any> {
  // APIRequestContext 没有 cookies() 方法（那是 BrowserContext 的），
  // 请求侧取 Cookie 只能经 storageState() 的 cookies 列表。
  const { cookies } = await ctx.storageState()
  const xsrf = cookies.find((c) => c.name === 'XSRF-TOKEN')?.value
  const res = await ctx.fetch(apiPath(path), {
    method,
    data,
    headers: xsrf ? { 'X-XSRF-TOKEN': xsrf } : {},
  })
  const body = await res.json().catch(() => ({}))
  if (body.code !== 200) {
    throw new Error(`${method} ${path} 失败：HTTP ${res.status()} code=${body.code} msg=${body.message}`)
  }
  return body.data
}

interface SavedAnswers {
  [questionId: string]: unknown
}

/** 服务端 answers 兼容 JSON 字符串/对象两种形态（与 useStudentExams.parseSavedAnswers 同口径） */
function parseSavedAnswers(raw: unknown): SavedAnswers {
  const parsed = typeof raw === 'string' ? JSON.parse(raw) : raw
  return parsed && typeof parsed === 'object' ? (parsed as SavedAnswers) : {}
}

/**
 * 已作答题数。
 * collectAnswers 会把**全部**题目一起上报，未作答的是空串，因此「键个数」恒等于题数，
 * 只有「非空值个数」才反映真实进度 —— 这也是断线期间进度未被悄悄改动、
 * 以及恢复后新作答确实补存的唯一可靠判据。
 */
function answeredCount(saved: SavedAnswers): number {
  return Object.values(saved).filter((v) => v !== '' && v !== null && v !== undefined).length
}

async function fetchSession(): Promise<{ id: number; status: string; answers: SavedAnswers }> {
  const s = await call(studentCtx, 'GET', `/exam-sessions/paper/${paperId}`)
  return { id: s.id, status: s.status, answers: parseSavedAnswers(s.answers) }
}

async function remainingSeconds(page: Page): Promise<number> {
  const text = await page.locator('.countdown-text').innerText()
  const [m, s] = text.split(':').map(Number)
  return m * 60 + s
}

/**
 * 等一次自动保存节拍落地。
 * saved = 收到 2xx 响应；failed = 请求在网络层失败（断网时压根没有响应，只能取 requestfailed）。
 * 两者都从 URL 里取回会话 id，顺带证明「复用」的是同一个会话而非新建。
 */
const AUTOSAVE_PATH = /\/exam-sessions\/(\d+)\/auto-save/
const sidOf = (url: string) => Number(url.match(AUTOSAVE_PATH)?.[1] ?? 0)

async function waitForAutoSave(page: Page, outcome: 'saved' | 'failed'): Promise<number> {
  if (outcome === 'saved') {
    const res = await page.waitForResponse(
      (r) => AUTOSAVE_PATH.test(r.url()) && r.status() >= 200 && r.status() < 300,
      { timeout: TICK_TIMEOUT_MS },
    )
    return sidOf(res.url())
  }
  return new Promise((resolve, reject) => {
    const onFailed = (req) => {
      if (!AUTOSAVE_PATH.test(req.url())) return
      page.off('requestfailed', onFailed)
      resolve(sidOf(req.url()))
    }
    page.on('requestfailed', onFailed)
    setTimeout(() => {
      page.off('requestfailed', onFailed)
      reject(new Error('等待自动保存（failed）超时'))
    }, TICK_TIMEOUT_MS)
  })
}

/**
 * 把实时通道变成测试可控的开关：拦截 /api/ws，可放行（连真后端）可丢弃（模拟断线），
 * 并记录「连接尝试时间戳」与「客户端发出的帧」（应用每 30s 发一个 ping）。
 *
 * 【为什么必须自己掐线，而不是只靠离线模拟】2026-10-06 探针实测：
 *   ① `context.setOffline(true)` 能让**新建**的同源 XHR 立刻 net::ERR_INTERNET_DISCONNECTED
 *      —— 所以 REST 侧的自动保存断线是真实可复现的；
 *   ② 但它对**已建立**的 WebSocket 毫无影响 —— 断网期间应用心跳照发、socket 仍是 OPEN。
 *      即浏览器离线模拟并不等价于真实断网对实时通道的效果，「断线重连」只能由测试显式 close；
 *   ③ 显式 close 后客户端也不是秒级察觉：连接存活判定完全依赖 30s 心跳，
 *      实测约 31s 后才发起第 1 次重连。这是真实的检测延迟（不是脚本问题），已作为发现记入文档。
 */
async function interceptWs(page: Page) {
  const attempts: number[] = []
  const frames: string[] = []
  const live: any[] = []
  const state = { mode: 'pass' as 'pass' | 'drop' }

  await page.routeWebSocket(/\/api\/ws$/, (route) => {
    attempts.push(Date.now())
    if (state.mode === 'drop') {
      route.close()
      return
    }
    live.push(route)
    route.connectToServer()
    route.onMessage((msg: any) => frames.push(String(msg.payload ?? msg)))
  })

  return {
    attempts,
    setMode: (mode: 'pass' | 'drop') => {
      state.mode = mode
    },
    /** 掐断当前已建立的连接，模拟运行中断线 */
    killLive: () => {
      const route = live.pop()
      if (!route) throw new Error('没有已建立的 /api/ws 路由可关闭（通道从未连通）')
      route.close()
    },
    /** mark 之后是否出现过客户端心跳帧 —— 通道「真的连通」的唯一被动证据 */
    hasHeartbeatAfter: (mark: number) =>
      frames.slice(mark).some((f) => f.includes('"type":"ping"')),
    framesMark: () => frames.length,
  }
}

test.beforeAll(async () => {
  teacherCtx = await apiLogin('teacher')
  adminCtx = await apiLogin('admin')
  studentCtx = await apiLogin('student')

  courseId = (
    await call(teacherCtx, 'POST', '/teacher/courses', {
      name: `断线续考验证课程 ${stamp}`,
      code: COURSE_CODE,
      credits: 2,
      enrollmentType: 'FREE',
      maxStudents: 50,
      startDate: '2026-01-01 00:00:00',
      endDate: '2027-12-31 23:59:59',
    })
  ).id

  await call(adminCtx, 'POST', `/courses/${courseId}/approve`)
  await call(studentCtx, 'POST', `/student/courses/${courseId}/enroll`)

  for (let i = 1; i <= QUESTION_COUNT; i++) {
    await call(teacherCtx, 'POST', '/questions/create', {
      type: 'SINGLE_CHOICE',
      content: `断线续考验证题 ${i}`,
      options: JSON.stringify(['甲', '乙', '丙', '丁']),
      correctAnswers: '"A"',
      difficulty: 'medium',
      score: 10,
      courseId,
      isActive: true,
    })
  }

  paperId = (
    await call(teacherCtx, 'POST', '/exam-papers/auto-compose', {
      courseId,
      title: PAPER_TITLE,
      description: 'Q-3 断线续考与实时通道重连验证专用',
      duration: PAPER_DURATION_MIN,
      timeLimit: PAPER_DURATION_MIN,
      maxAttempts: 3,
      shuffleQuestions: false,
      showScore: true,
      typeRules: { SINGLE_CHOICE: QUESTION_COUNT },
      scoreRules: { SINGLE_CHOICE: 10 },
    })
  ).id
  await call(teacherCtx, 'POST', `/exam-papers/${paperId}/publish`)
})

test.afterAll(async () => {
  // beforeAll 失败时夹具上下文可能不存在，直接跳过（真实原因由 beforeAll 抛出）
  if (!teacherCtx) return
  // best-effort：会话/成绩记录可能外键引用试卷与课程，删不掉只告警，
  // 试卷标题带时间戳，残留不会污染后续判定。
  for (const [label, method, path] of [
    ['试卷', 'DELETE', `/exam-papers/${paperId}`],
    ['课程', 'DELETE', `/courses/${courseId}`],
  ] as const) {
    try {
      await call(teacherCtx, method, path)
    } catch (e) {
      console.warn(`[Q-3] 夹具清理跳过：${label} ${method} ${path} → ${(e as Error).message}`)
    }
  }
  await Promise.allSettled([teacherCtx.dispose(), adminCtx.dispose(), studentCtx.dispose()])
})

test.describe('Q-3 断线续考与实时通道重连', () => {
  test('掐网→作答保留→恢复补存→刷新续答，且倒计时以服务端为准', async ({ page }) => {
    // 全程要跨 3 个 30s 自动保存节拍、2 个 30s 心跳周期，另加登录/导航/恢复，预算 10 分钟
    test.setTimeout(600_000)

    // ---- A. 实时通道可用 ----
    // 判据取「客户端心跳帧出现在拦截层」而非一次手动握手的返回值：ping 每 30s 一发，
    // 能在路由里看到它，同时证明了 dev 代理的 upgrade 转发、后端握手鉴权、通道可读写
    // 整条链路；vite 缺 `ws: true`（或 nginx 缺 Upgrade 头）时这条 socket 根本连不上，
    // 本断言即失败 —— 这正是 2026-10-06 之前实时通道在本机从未真正可用的回归护栏。
    const chan = await interceptWs(page)
    await loginAs(page, 'STUDENT')
    await expect
      .poll(() => chan.hasHeartbeatAfter(0), { timeout: 50_000, intervals: [1000] })
      .toBe(true)

    // ---- 进入考试：首次进入应拿到满时长 ----
    // 走**课程**入口而非全局 /student/exams：后者是无分页控件的第 1 页（10 条），
    // 本机库存 114 份试卷时新建的验证卷根本不在第 1 页（该缺陷已按课程维度修好，
    // 见 useStudentExams.loadExamList 注释；全局入口的分页缺失作为遗留项记录在文档里）。
    await page.goto(`/student/courses/${courseId}/exams`)
    const card = page.locator('.exam-card').filter({ hasText: PAPER_TITLE })
    await expect(card).toBeVisible()
    await card.getByRole('button', { name: '开始考试' }).click()
    await page.getByRole('button', { name: '确定' }).click()
    await expect(page.locator('.countdown-text')).toBeVisible()
    const t0 = await remainingSeconds(page)
    expect(t0).toBeGreaterThan(PAPER_DURATION_MIN * 60 - 15)

    // ---- B/C/D 前置：先做一次成功自动保存（第 1 题选 A）----
    await page.locator('.answer-area .el-radio').nth(0).click()
    const sessionId = await waitForAutoSave(page, 'saved')
    expect(sessionId, '自动保存请求 URL 应带会话 id').toBeGreaterThan(0)

    const first = await fetchSession()
    expect(first.id).toBe(sessionId)
    expect(first.status).toBe('STARTED')
    expect(Object.keys(first.answers)).toHaveLength(QUESTION_COUNT)
    expect(answeredCount(first.answers)).toBe(1)

    // ---- B. 断线：改答第 2 题，自动保存必须失败且**显式告知**，作答留在内存 ----
    await page.context().setOffline(true)
    // 断网同时把实时通道也打回故障态：离线模拟对已建立 socket 无效（见 interceptWs 注释②），
    // 只能由测试显式 close；close 之后应用的 30s 心跳会撞上发送失败，从而进入退避重连。
    chan.setMode('drop')
    chan.killLive()
    const outageAttemptsBase = chan.attempts.length
    await page.locator('.nav-dot').nth(1).click()
    await page.locator('.answer-area .el-radio').nth(1).click()
    await waitForAutoSave(page, 'failed')

    await expect(page.locator('.el-message').filter({ hasText: '网络异常' })).toBeVisible()
    // 页面没崩、没被导航走、答题视图仍在（历史缺陷：失败静默回退到 submit → 直接交卷）
    await expect(page.locator('.countdown-text')).toBeVisible()
    await expect(page.locator('.answer-area .el-radio').nth(1).locator('input')).toBeChecked()

    const duringOutage = await fetchSession()
    expect(duringOutage.status, '自动保存失败绝不能把会话推成交卷状态').toBe('STARTED')
    expect(answeredCount(duringOutage.answers), '断线期间的作答尚未落库（符合预期，靠内存续跑）').toBe(1)

    // ---- F. 断线重连：指数退避（1s→2s→4s，间隔递增）----
    // 预算给到 150s：故障检测本身要等一个 30s 心跳周期（实测 ~31s），
    // 之后才是退避序列 1s/2s/4s；这两个 30s 与自动保存节拍重叠，但仍要留够余量。
    await expect
      .poll(() => chan.attempts.length - outageAttemptsBase, { timeout: 150_000, intervals: [2000] })
      .toBeGreaterThanOrEqual(3)
    const retries = chan.attempts.slice(outageAttemptsBase)
    const gaps = retries.slice(1).map((t, i) => t - retries[i])
    expect(
      gaps[gaps.length - 1],
      `重连间隔未随尝试次数递增：${gaps.join(',')}ms（应为 1s→2s→4s 量级）`,
    ).toBeGreaterThan(gaps[0])

    // ---- D. 网络恢复：下一次节拍把断线期间的作答一并补存 ----
    await page.context().setOffline(false)
    await waitForAutoSave(page, 'saved')
    const recovered = await fetchSession()
    expect(answeredCount(recovered.answers)).toBe(2)
    expect(
      Object.values(recovered.answers)
        .filter((v) => v !== '')
        .sort(),
      // 落库值必须是**选项字母**：题库 correct_answers 按字母存，送下标会让自动阅卷恒判错（DR-25）。
      // 第 1 题选第 1 项（A）、第 2 题选第 2 项（B）。
    ).toEqual(['A', 'B'])

    // ---- F. 刷新后实时通道重建（不依赖再跑一次 login()）----
    // 退避预算（5 次）在断线期间已耗尽，之后客户端自身不会再连；这正是历史缺陷
    // 「按 F5 之后 WebSocket 永久不再连接」的同一族问题，现由路由守卫的
    // fetchPermissions() 成功后显式 connect() 兜住（stores/auth.ts）。
    chan.setMode('pass')
    const attemptsBeforeReload = chan.attempts.length
    const framesBeforeReload = chan.framesMark()
    await page.reload()
    await expect
      .poll(() => chan.attempts.length, { timeout: 30_000, intervals: [1000] })
      .toBeGreaterThan(attemptsBeforeReload)
    // 不只是「发起过尝试」，而是真的重新连通并能收发（心跳回到拦截层）
    await expect
      .poll(() => chan.hasHeartbeatAfter(framesBeforeReload), { timeout: 50_000, intervals: [1000] })
      .toBe(true)

    // ---- E. 刷新续答：命中恢复路径，作答回填 + 剩余时间以服务端为准 ----
    await expect(card).toBeVisible()
    await card.getByRole('button', { name: '开始考试' }).click()
    await page.getByRole('button', { name: '确定' }).click()
    await expect(page.locator('.el-message').filter({ hasText: '已恢复上次未完成的作答' })).toBeVisible()
    await expect(page.locator('.answer-area .el-radio').nth(0).locator('input')).toBeChecked()
    await page.locator('.nav-dot').nth(1).click()
    await expect(page.locator('.answer-area .el-radio').nth(1).locator('input')).toBeChecked()

    const t1 = await remainingSeconds(page)
    expect(t1, '恢复后倒计时被重置为满时长 = 退出重进即可无限续时').toBeLessThan(t0)
    const serverRemaining = await call(studentCtx, 'GET', `/exam-sessions/${sessionId}/time-remaining`)
    expect(Math.abs(t1 - serverRemaining), `前端倒计时 ${t1}s 与服务端 ${serverRemaining}s 偏离过大`).toBeLessThanOrEqual(8)

    // ---- 收尾：正式交卷走通（answers 以 Map 上报，400 回归护栏），并验证「交卷 ≠ 阅卷」口径 ----
    await page.getByRole('button', { name: '提交试卷' }).click()
    await page.getByRole('button', { name: '确认提交' }).click()
    await expect(page.locator('.result-card')).toBeVisible()
    // submitExam 只把会话置为 SUBMITTED，自动阅卷由教师端接口或超时收卷调度触发
    // （ExamSessionLifecycleComponent#submitExam / ExamTimeoutScheduler）。
    // 因此学生端此刻**必须**显示占位符并明确告知批阅中，而不是拿 0 分冒充已判分。
    await expect(page.locator('.score-number')).toHaveText('--')
    await expect(page.locator('.result-message')).toHaveText('成绩正在批阅中，请稍后查看...')

    const submitted = await fetchSession()
    expect(submitted.status).toBe('SUBMITTED')

    // 教师端触发自动阅卷后：第 1 题送字母 A 与题库 correct_answers("A") 相等 → 得满分；
    // 第 2 题送 B 判错；第 3 题未作答不计错题。合计 10 / 30。
    // 这条断言同时是 DR-25 的端到端护栏：答题视图一旦回退成上报下标，得分会掉回 0。
    await call(teacherCtx, 'POST', `/exam-sessions/${sessionId}/auto-grade`)
    const graded = await call(studentCtx, 'GET', `/exam-sessions/${sessionId}/result`)
    expect(graded.status).toBe('GRADED')
    expect(Number(graded.totalScore), `自动阅卷得分应为 10，实际 ${graded.totalScore}`).toBe(10)
    expect(Number(graded.percentage)).toBeCloseTo(33.33, 1)
  })
})
