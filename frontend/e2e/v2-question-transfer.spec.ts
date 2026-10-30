import {
  expect,
  test,
  request,
  type APIRequestContext,
  type Browser,
  type BrowserContext,
  type Page,
} from '@playwright/test'
import { existsSync, mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'
import * as XLSX from 'xlsx'
import { loginAs } from './test-helpers'

/**
 * v2 题库批量导入/导出真栈取证（`docs/前端待办项.md` §12.1 的 B 条）。
 *
 * 前置：本机后端 8080 与前端 5173 在线。会话复用视觉套件 globalSetup 落盘的
 * `e2e/visual/.auth/teacher.json`（把它记在 5175 源下的 localStorage 改挂到本次 BASE 源；
 * 会话本体在 HttpOnly Cookie 里，domain=localhost 与端口无关）——**本轮不额外真登录**：
 * `ratelimit.login` 默认 10 次/60 秒/IP，既有 4 份 spec 已吃掉 9 次（v2-core 3、
 * v2-teacher-write 3、a11y 3），再登就靠撞限流制造假红。夹具失效时才回退一次真登录。
 *
 * 口径（与 v2-teacher-write.spec.ts 同范式）：
 * ① 每个动作都由真浏览器点 UI 发出——导出＝点「导出」并收下真实下载件；
 *    导入＝点「导入」并在文件选择器里交上真实 .xlsx（`handleImport` 的
 *    `<input type=file>` 是临时创建、不挂 DOM 的，所以只能拦 filechooser，没有 locator 可点）；
 * ② 断言取**接口读数**（GET /questions 的列表行、导入响应体本身、导出件的单元格），
 *    绝不以「出现成功 toast」自证；
 * ③ 失败区分「后端拒绝（HTTP 状态＋响应体 code/message）」与「前端根本没发请求」；
 * ④ 本轮造的题按唯一题干前缀筛出、删干净，并断言删除计数与库内净增为零。
 *
 * 「下载模板」的真实口径：本仓**没有**独立模板端点——`api/question.ts` 只有
 * exportQuestionsExcel / importQuestionsExcel 两个文件口，`TeacherController` 里也没有任何
 * `*template*` 映射；而 `ExcelUtil.QUESTION_HEADERS` 明写「题库导出列头（与导入列严格一致）」，
 * 所以导出件就是权威模板：先经 UI 导出拿到真实文件，**读它的首行作表头**（不把 8 列写死在
 * 用例里），再在其下填 2 道自造题交给导入。模板端点缺失按清单外发现登记，不在本轮补。
 *
 * 字段口径（本仓先例：字段名猜错会让「参考答案从未落库」）：答案的真实实体字段是
 * `correctAnswers`（不是 `answer`）；`options` / `knowledgePoints` 是**字符串列**，
 * 装的是 JSON 文本而非数组，所以断言一律「解析后比较」而不是比字节串。
 */

const BASE = process.env.PW_BASE_URL || 'http://localhost:5173'
const STAMP = Date.now()
/** 唯一前缀：自造题的题干都带它，删除只按它筛，绝不碰种子题 */
const PREFIX = `E2E-题库导入-${STAMP}-`
const FIXTURE = fileURLToPath(new URL('./visual/.auth/teacher.json', import.meta.url))

/** 导出/导入共用的列下标（与 ExcelUtil.readQuestions 的 getCell(i) 同序） */
const COL = {
  type: 0,
  content: 1,
  options: 2,
  correctAnswers: 3,
  difficulty: 4,
  score: 5,
  knowledgePoints: 6,
  explanation: 7,
} as const

const TEMPLATE_HEADERS = [
  '题型',
  '题干',
  '选项(JSON)',
  '正确答案',
  '难度',
  '分值',
  '知识点',
  '解析',
] as const

type StorageState = Awaited<ReturnType<BrowserContext['storageState']>>

/** 自造题：写入文件的原值 + 接口里应当读到的值（后半个是归一化后的契约） */
interface MadeQuestion {
  stem: string
  cells: string[]
  type: string
  options: unknown
  correctAnswers: unknown
  difficulty: string
  score: number
  knowledgePoints: unknown
  explanation: string
}

const MADE: MadeQuestion[] = [
  {
    stem: `${PREFIX}甲-单选`,
    // 全 8 列都给值：合法 JSON 数组原样保留、合法 JSON 标量原样保留
    cells: [
      'SINGLE_CHOICE',
      `${PREFIX}甲-单选`,
      JSON.stringify([
        'A. 循环队列判满',
        'B. 链栈无需扩容',
        'C. 完全二叉树最矮',
        'D. 哈希表必有序',
      ]),
      JSON.stringify('B'),
      'MEDIUM',
      '5',
      JSON.stringify(['循环结构', '顺序结构']),
      `${PREFIX}甲-解析：循环队列以 (rear+1)%size==front 判满。`,
    ],
    type: 'SINGLE_CHOICE',
    options: ['A. 循环队列判满', 'B. 链栈无需扩容', 'C. 完全二叉树最矮', 'D. 哈希表必有序'],
    correctAnswers: 'B',
    difficulty: 'MEDIUM',
    score: 5,
    knowledgePoints: ['循环结构', '顺序结构'],
    explanation: `${PREFIX}甲-解析：循环队列以 (rear+1)%size==front 判满。`,
  },
  {
    stem: `${PREFIX}乙-判断`,
    // 中文题型/难度 + 分号分隔的裸文本知识点 + 留空的选项与解析：验 ExcelUtil.normalize* 的归一化
    cells: ['判断题', `${PREFIX}乙-判断`, '', 'true', '困难', '3', '变量命名；代码风格', ''],
    type: 'TRUE_FALSE',
    options: null,
    correctAnswers: true,
    difficulty: 'HARD',
    score: 3,
    knowledgePoints: ['变量命名', '代码风格'],
    explanation: '',
  },
]

let states: Record<'teacher', StorageState> | null = null
let apiTeacher: APIRequestContext | null = null
/** 会话来源读数：复用夹具 or 回退一次真登录，报告里要说清走的哪条 */
let sessionSource = '未建立'

const target = { courseId: 0, courseName: '', baselineTotal: -1 }
const workspace = { dir: '', templatePath: '', filledPath: '' }
/** 本轮经接口读数确认创建的题目 id；afterAll 只按这份名单动手 */
const createdIds: number[] = []

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

/** 点 UI 触发的动作：把「前端没发请求」与「后端拒绝」分成两类报错 */
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
      { timeout: 30000 },
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

/**
 * 后端 DoubleSubmitCsrfFilter 开启时下发/校验的 XSRF 令牌。
 * APIRequestContext 没有 cookies() 取值口（那是 BrowserContext 的方法），所以从
 * 响应的 set-cookie 头里自行记一份；后端关 CSRF 时该值恒为空，写请求就不带头。
 */
let xsrfToken = ''

/** 纯 API 读数（断言与自清理都只走这里） */
async function apiJson(
  api: APIRequestContext,
  method: string,
  path: string,
  data?: unknown,
): Promise<{ status: number; code: string; message: string; data: unknown }> {
  const headers: Record<string, string> = {}
  if (method !== 'GET' && xsrfToken) headers['X-XSRF-TOKEN'] = xsrfToken
  const res = await api.fetch(path, { method, data, headers })
  const minted = /XSRF-TOKEN=([^;]+)/.exec(res.headers()['set-cookie'] ?? '')
  if (minted) xsrfToken = minted[1]
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

/** 取证课程的接口读数：行 + 服务端 total（净增判定用 total，不用被 size 截断的行数） */
async function courseRead(): Promise<{ rows: Array<Record<string, unknown>>; total: number }> {
  const read = await apiJson(
    apiTeacher!,
    'GET',
    `/api/questions?courseId=${target.courseId}&page=1&size=200`,
  )
  const rows = rowsOf(read.data)
  const total = Number(((read.data || {}) as Record<string, unknown>).total ?? rows.length)
  return { rows, total }
}

/** 接口读数里本轮前缀命中的题；种子题不带这个前缀，天然零命中 */
async function prefixedRows(): Promise<Array<Record<string, unknown>>> {
  const { rows } = await courseRead()
  return rows.filter((row) => String(row.content ?? '').startsWith(PREFIX))
}

function findRow(rows: Array<Record<string, unknown>>, stem: string): Record<string, unknown> {
  const hit = rows.find((row) => String(row.content) === stem)
  expect(hit, `接口读数里没有题干为「${stem}」的题`).toBeTruthy()
  return hit as Record<string, unknown>
}

/**
 * 教师自己的课程：下拉框取数口是 /courses/my，归属校验口是 /teacher/courses，
 * 两边都有的那门课才能既被选中又不被后端 403——取交集的第一门。
 */
async function pickCourse(teacherId: number): Promise<void> {
  const dropdown = rowsOf(
    (await apiJson(apiTeacher!, 'GET', '/api/courses/my?page=1&size=50')).data,
  )
  const owned = new Set(
    rowsOf((await apiJson(apiTeacher!, 'GET', '/api/teacher/courses?page=1&size=50')).data)
      .filter((row) => Number(row.teacherId) === teacherId)
      .map((row) => Number(row.id)),
  )
  const usable = dropdown.find((row) => owned.has(Number(row.id)))
  expect(
    usable,
    `没有既出现在题库筛选下拉、又归当前教师所有的课程：下拉=${dropdown.map((r) => r.id).join(',')} 名下=${[...owned].join(',')}`,
  ).toBeTruthy()
  target.courseId = Number(usable!.id)
  target.courseName = String(usable!.name ?? usable!.title ?? '')
}

/**
 * 会话夹具：复用 e2e/visual/.auth/teacher.json（零新增登录）。
 * 夹具缺失或会话已失效时退化成**一次**真登录——loginAs 已勾「记住我」并对 429 显式报错。
 */
async function teacherState(browser: Browser): Promise<StorageState> {
  if (existsSync(FIXTURE)) {
    const raw = JSON.parse(readFileSync(FIXTURE, 'utf8')) as {
      cookies?: unknown[]
      origins?: Array<{ localStorage?: unknown[] }>
    }
    const probe = await request.newContext({ baseURL: BASE, storageState: raw as StorageState })
    const read = await apiJson(probe, 'GET', '/api/auth/me')
    await probe.dispose()
    const me = (read.data || {}) as Record<string, unknown>
    if (read.status === 200 && String(me.role) === 'TEACHER') {
      sessionSource = 'fixture（复用视觉套件夹具，零新增登录）'
      return {
        cookies: raw.cookies ?? [],
        origins: [{ origin: BASE, localStorage: raw.origins?.[0]?.localStorage ?? [] }],
      } as StorageState
    }
    console.log(
      `[v2-question-transfer] 夹具会话不可复用（GET /auth/me→HTTP ${read.status} code=${read.code || '无'}），回退一次真登录`,
    )
  } else {
    console.log('[v2-question-transfer] 磁盘无 e2e/visual/.auth/teacher.json，回退一次真登录')
  }
  const context = await browser.newContext({ baseURL: BASE })
  const page = await context.newPage()
  await loginAs(page, 'TEACHER')
  const state = await context.storageState()
  await context.close()
  sessionSource = 'login（夹具失效时的单次真登录）'
  return state
}

/**
 * 把下载/上传件的首个工作表读成二维数组（含表头行，空单元读成 ''）。
 * 走 readFileSync + XLSX.read(buffer)：xlsx 的 ESM 入口不自动接 Node fs，
 * 直接 readFile 会抛「Cannot access file」——本仓实测。
 */
function sheetRows(path: string): unknown[][] {
  const wb = XLSX.read(readFileSync(path), { type: 'buffer' })
  const sheet = wb.Sheets[wb.SheetNames[0]]
  return XLSX.utils.sheet_to_json<unknown[]>(sheet, { header: 1, defval: '' }) as unknown[][]
}

function cell(row: unknown[], index: number): string {
  return String(row[index] ?? '').trim()
}

test.describe.serial('v2 题库导入导出（真栈）', () => {
  test.beforeAll(async ({ browser }) => {
    // 模板件/填报件/复核件都落在系统临时目录，不进工作树（跑测会整清 test-results/）
    workspace.dir = mkdtempSync(join(tmpdir(), 'e2e-question-transfer-'))
    const built = await teacherState(browser)
    states = { teacher: built }
    apiTeacher = await request.newContext({ baseURL: BASE, storageState: built })

    const me = await apiJson(apiTeacher, 'GET', '/api/auth/me')
    const teacherId = Number((me.data as Record<string, unknown>)?.id)
    expect(teacherId, `会话未落到教师账号：/auth/me→HTTP ${me.status}`).toBeGreaterThan(0)
    await pickCourse(teacherId)

    const baseline = await courseRead()
    target.baselineTotal = baseline.total
    expect(
      baseline.rows.length,
      `取证课程 ${target.courseId} 的读数已达分页上限 200，净增判定会失真`,
    ).toBeLessThan(200)
    console.log(
      `[v2-question-transfer] 会话=${sessionSource}；取证课程=${target.courseId}(${target.courseName})；` +
        `导入前接口读数=${target.baselineTotal} 题`,
    )
  })

  test.afterAll(async () => {
    if (!apiTeacher) return
    const report: string[] = []
    // 自清理是硬要求：断言失败必须把这一轮判红，不能被 catch 吞成一行日志（那就成了「静默留在库里」）
    let failure: unknown = null
    try {
      const hits = await prefixedRows()
      const hitIds = hits.map((row) => Number(row.id)).sort((a, b) => a - b)
      const ledger = [...createdIds].sort((a, b) => a - b)
      const allMine = hits.every((row) => String(row.content ?? '').startsWith(PREFIX))
      if (!hits.length) {
        report.push('库里本轮前缀命中 0 题（未产生数据，或已被删除）')
      } else if (!allMine) {
        // 删除前的硬闸：只要命中行里混进不带本轮前缀的题干，一律不动手，宁可留证也不误删种子题
        report.push(`命中行含非本轮前缀题干，已拒绝删除：${hitIds.join(',')}`)
      } else {
        // 判据：前缀命中必须与本轮登记的造题名单逐一对应。登记为空说明导入用例没跑到
        // 读数那步——此时仍按唯一前缀把残留清掉（不误伤种子题），但不拿它冒充计数判据。
        if (ledger.length) {
          expect(
            hitIds.join(','),
            `前缀命中与本轮登记名单不是一一对应（登记=${ledger.join(',')} 命中=${hitIds.join(',')}）`,
          ).toBe(ledger.join(','))
        } else {
          report.push(`登记名单为空但有前缀命中（${hitIds.join(',')}），按前缀清残不留库`)
        }
        const deleted: string[] = []
        for (const id of hitIds) {
          const done = await apiJson(apiTeacher, 'DELETE', `/api/questions/${id}`)
          if (done.status === 200 && (!done.code || done.code === '200')) deleted.push(String(id))
          else deleted.push(`${id}→HTTP ${done.status} code=${done.code || '无'} 未删`)
        }
        const removed = deleted.filter((item) => /^\d+$/.test(item)).map(Number)
        const after = await prefixedRows()
        const residual = await courseRead()
        report.push(
          `造题=${hits.length} 删除成功=${removed.length}；删后前缀命中=${after.length}；` +
            `该课程读数 ${target.baselineTotal}→${residual.total}（净增 ${residual.total - target.baselineTotal}）`,
        )
        expect(removed.length, `删除计数未覆盖本轮自造题：${deleted.join(' ')}`).toBe(hits.length)
        expect(removed.length, '删除计数下限判据（≥1）未达成').toBeGreaterThanOrEqual(1)
        expect(
          after.length,
          `删后仍有本轮前缀的题留在库里：${after.map((row) => row.id).join(',')}`,
        ).toBe(0)
        expect(
          residual.total,
          `自清理未回到导入前的库内读数：${target.baselineTotal} → ${residual.total}`,
        ).toBe(target.baselineTotal)
      }
    } catch (error) {
      failure = error
      report.push(`自清理未达成：${redact(String(error))}`)
    } finally {
      console.log(`[v2-question-transfer] afterAll 自清理读数：${report.join(' ; ') || '无'}`)
      await apiTeacher.dispose()
    }
    if (failure) throw failure
  })

  test('教师端导出题库：真实下载件的首行就是 8 列导入模板', async ({ browser }) => {
    const context = await browser.newContext({ baseURL: BASE, storageState: states!.teacher })
    const page = await context.newPage()
    const calls = watchApi(page)
    await page.goto('/teacher/bank', { waitUntil: 'domcontentloaded' })
    await expect(page.getByRole('button', { name: '导出', exact: true })).toBeVisible()

    // 课程筛选定到取证课程：导出与导入都按 filters.courseId 打，他人课程会被后端 403
    const courseSelect = page.locator('select.filter-course')
    await expect(courseSelect, '题库页没有课程筛选下拉').toBeVisible()
    await courseSelect.selectOption(String(target.courseId))

    const downloadWaiter = page.waitForEvent('download', { timeout: 30000 })
    await page.getByRole('button', { name: '导出', exact: true }).click()
    const download = await downloadWaiter
    workspace.templatePath = join(workspace.dir, 'template.xlsx')
    await download.saveAs(workspace.templatePath)

    expect(
      download.suggestedFilename(),
      `导出件不是 .xlsx：${download.suggestedFilename()}`,
    ).toMatch(/\.xlsx$/i)
    const header = (sheetRows(workspace.templatePath)[0] ?? []).map((item) => String(item).trim())
    expect(header, '导出件首行不是导入契约的 8 列表头').toEqual([...TEMPLATE_HEADERS])
    expect(
      calls.some(
        (item) =>
          item.method === 'GET' &&
          /\/api\/teacher\/questions\/export$/.test(item.path) &&
          item.status === 200,
      ),
      `导出接口未被真实调用：同类调用观测=${lastCall(calls, 'GET', /export$/)}；近期 API=${tailCalls(calls)}`,
    ).toBeTruthy()
    await context.close()
  })

  test('模板填 2 道自造题后经 UI 导入：导入响应与列表读数都含这 2 题且字段一致', async ({
    browser,
  }) => {
    test.setTimeout(90000)
    test.skip(!workspace.templatePath, '前置导出未落盘，没有模板可填')

    // 表头取自已下载的真实模板件，不取用例里写死的常量
    const header = (sheetRows(workspace.templatePath)[0] ?? []).map((item) => String(item))
    expect(header, '模板件表头在读取时已走样').toEqual([...TEMPLATE_HEADERS])
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(
      wb,
      XLSX.utils.aoa_to_sheet([header, ...MADE.map((q) => q.cells)]),
      '题库',
    )
    workspace.filledPath = join(workspace.dir, 'filled.xlsx')
    // 同上：ESM 入口不接 Node fs，写出走 XLSX.write(buffer) + writeFileSync
    writeFileSync(
      workspace.filledPath,
      Buffer.from(XLSX.write(wb, { bookType: 'xlsx', type: 'array' })),
    )

    const context = await browser.newContext({ baseURL: BASE, storageState: states!.teacher })
    const page = await context.newPage()
    const calls = watchApi(page)
    await page.goto('/teacher/bank', { waitUntil: 'domcontentloaded' })
    await page.locator('select.filter-course').selectOption(String(target.courseId))

    const imported = await clickWrite(
      page,
      calls,
      'POST',
      /\/api\/teacher\/questions\/import-excel$/,
      '导入题库',
      async () => {
        const chooser = page.waitForEvent('filechooser', { timeout: 20000 })
        await page.getByRole('button', { name: '导入', exact: true }).click()
        await (await chooser).setFiles(workspace.filledPath)
      },
    )
    expect(
      Number(imported.importedCount),
      `导入响应读数：入库条数不等于 2（响应体=${redact(JSON.stringify(imported))}）`,
    ).toBe(2)
    expect(Number(imported.total), '导入响应读数：解析条数不等于 2').toBe(2)

    const hits = await prefixedRows()
    expect(
      hits.length,
      `GET /questions?courseId=${target.courseId} 的前缀命中不是 2（实得 ${hits.length}）`,
    ).toBe(2)
    createdIds.push(...hits.map((row) => Number(row.id)))
    expect(new Set(createdIds).size, `登记的自造题 id 出现重复：${createdIds.join(',')}`).toBe(
      createdIds.length,
    )

    for (const made of MADE) {
      const row = findRow(hits, made.stem)
      expect(String(row.type), `题型未落库（写入列=${made.cells[COL.type]}）`).toBe(made.type)
      expect(String(row.difficulty), `难度未落库（写入列=${made.cells[COL.difficulty]}）`).toBe(
        made.difficulty,
      )
      expect(Number(row.score), '分值未落库').toBe(made.score)
      expect(Number(row.courseId), '题目未归属到取证课程').toBe(target.courseId)
      expect(String(row.content), '题干与写入值不一致').toBe(made.stem)
      // 答案必须落在 correctAnswers 上；后端实体没有 answer 字段，读到了就是口径写错
      expect(row.answer, '接口读数出现 answer 字段，说明断言口径与后端实体不符').toBeUndefined()
      expect(String(row.createdBy) !== '', '导入未回写 createdBy').toBeTruthy()
      expect(row.isActive, '导入的题目应为启用态').toBe(true)
      if (made.options === null) {
        expect(
          row.options,
          '留空的选项列应不入库（后端 normalizeJsonArray 对空白返回 null）',
        ).toBeUndefined()
      } else {
        expect(JSON.parse(String(row.options)), '选项列（JSON 字符串）与写入不一致').toEqual(
          made.options,
        )
      }
      expect(JSON.parse(String(row.correctAnswers)), '参考答案未落到 correctAnswers').toEqual(
        made.correctAnswers,
      )
      expect(
        JSON.parse(String(row.knowledgePoints)),
        '知识点列（JSON 字符串）与写入不一致',
      ).toEqual(made.knowledgePoints)
      if (made.explanation) expect(String(row.explanation), '解析未落库').toBe(made.explanation)
    }

    await page.reload({ waitUntil: 'domcontentloaded' })
    for (const made of MADE) {
      await expect(
        page.locator('tbody tr', { hasText: made.stem }),
        `题库列表页没有渲染刚导入的「${made.stem}」`,
      ).toBeVisible()
    }
    await context.close()
  })

  test('再导出：2 道自造题出现在导出文件里，且与接口读数一致', async ({ browser }) => {
    test.skip(!createdIds.length, '前置导入未成功，没有可复核的题目')
    const context = await browser.newContext({ baseURL: BASE, storageState: states!.teacher })
    const page = await context.newPage()
    const calls = watchApi(page)
    await page.goto('/teacher/bank', { waitUntil: 'domcontentloaded' })
    await page.locator('select.filter-course').selectOption(String(target.courseId))

    const downloadWaiter = page.waitForEvent('download', { timeout: 30000 })
    await page.getByRole('button', { name: '导出', exact: true }).click()
    const download = await downloadWaiter
    const exportedPath = join(workspace.dir, 'exported.xlsx')
    await download.saveAs(exportedPath)

    const rows = sheetRows(exportedPath)
    const dataRows = rows.slice(1).filter((row) => cell(row, COL.content).startsWith(PREFIX))
    expect(
      dataRows.length,
      `导出件里本轮前缀命中的题不是 2（实得 ${dataRows.length}，导出共 ${rows.length - 1} 行数据）`,
    ).toBe(2)

    const read = await prefixedRows()
    for (const made of MADE) {
      const exported = dataRows.find((row) => cell(row, COL.content) === made.stem)
      expect(exported, `导出件里没有「${made.stem}」`).toBeTruthy()
      const server = findRow(read, made.stem)
      expect(cell(exported!, COL.type), '导出题型与接口读数不一致').toBe(String(server.type))
      expect(cell(exported!, COL.difficulty), '导出难度与接口读数不一致').toBe(
        String(server.difficulty),
      )
      expect(cell(exported!, COL.score), '导出分值与接口读数不一致').toBe(
        String(Number(server.score)),
      )
      expect(JSON.parse(cell(exported!, COL.correctAnswers)), '导出答案与接口读数不一致').toEqual(
        JSON.parse(String(server.correctAnswers)),
      )
      if (made.options !== null) {
        expect(JSON.parse(cell(exported!, COL.options)), '导出选项与接口读数不一致').toEqual(
          JSON.parse(String(server.options)),
        )
      }
      expect(
        JSON.parse(cell(exported!, COL.knowledgePoints)),
        '导出知识点与接口读数不一致',
      ).toEqual(JSON.parse(String(server.knowledgePoints)))
    }
    expect(
      calls.some(
        (item) =>
          item.method === 'GET' &&
          /\/api\/teacher\/questions\/export$/.test(item.path) &&
          item.status === 200,
      ),
      `导出接口未被真实调用：同类调用观测=${lastCall(calls, 'GET', /export$/)}；近期 API=${tailCalls(calls)}`,
    ).toBeTruthy()
    await context.close()
  })
})
