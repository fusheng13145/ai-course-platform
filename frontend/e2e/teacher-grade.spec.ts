import { test, expect, type Page, type Route } from '@playwright/test'
import { loginAs } from './test-helpers'

/**
 * 教师端成绩管理主链路 e2e（会话由真实登录建立，业务数据用 API Mock）。
 * 覆盖：成绩列表按「课程 × 学生」聚合渲染 / 待批阅任务跳转到提交页 / 提交页 AI 评分。
 *
 * 数据源口径（DR-77：成绩页重构后由 `useTeacherGrades` 聚合，本页不再发
 * `/api/exam-sessions/my` 与 `/api/submissions`——旧 spec 的 mock 正打在这两个已死的端点上，
 * 于是行集合为空，且被 `/api/**` 兜底静默吞掉，看不出是取数面漂了）：
 *   GET  /api/courses/my                                课程下拉，兼按课程并发取数的驱动集合
 *   GET  /api/analytics/courses/{id}/student-progress   作业均分 / 考试均分 / 学号
 *   GET  /api/grade/report/course/{id}                  成绩方案总评与构成项（等级由总评推出）
 *   GET  /api/tasks/pending-grading                     待批阅任务（必须带 courseId 才能跳转）
 *   GET  /api/tasks?courseId=…                          提交页任务列表
 *   GET  /api/submissions/task/{id}                     提交页提交记录
 *   POST /api/submissions/{id}/ai-grade                 AI 评分请求
 *
 * 成绩表已无 AI 按钮：批阅入口在「待批阅任务 → 课程驾驶舱 · 作业批改」，本文件按真实产品路径走。
 *
 * Mock 只注册一条 `/api/**`，由 `handle()` 按 pathname 分派：未登记的端点落兜底空列表并被
 * 记进 `fallback`，断言层要求成绩页四个数据源必须出现在 `served`、且不得出现在 `fallback`。
 * （分多条 `page.route` 也行，但 Playwright 是后注册者优先，宽窄规则的相对顺序会左右结果，
 * 单入口分派把这类顺序坑彻底消除。）
 */

const courses = [{ id: 1, name: '数据结构', teacherId: 1, code: 'CS201', semester: '2026 秋' }]

/** 学情：只提供作业/考试均分与学号，不提供总评 */
const studentProgress = [
  { studentId: 5, studentNo: '2023001', studentName: '张三', avgAssignmentScore: 80, avgExamScore: 90 },
  { studentId: 6, studentNo: '2023002', studentName: '李四', avgAssignmentScore: 68, avgExamScore: null },
]

/** 成绩方案报表：总评与构成项；页面等级由总评推出（85→B，72→C） */
const courseReport = [
  {
    studentId: 5,
    studentName: '张三',
    finalScore: 85,
    components: [
      { key: 'exam', name: '期末考试', weight: 0.6, score: 90, contribution: 54, graded: true },
    ],
  },
  {
    studentId: 6,
    studentName: '李四',
    finalScore: 72,
    components: [
      { key: 'homework', name: '第三章作业', weight: 0.4, score: 72, contribution: 29, graded: false },
    ],
  },
]

const pendingTasks = [{ id: 7, courseId: 1, title: '第三章作业', pendingCount: 1 }]
const tasksOfCourse = [{ id: 7, courseId: 1, title: '第三章作业', type: 'homework', status: 'published' }]

const submissions = [
  {
    id: 101,
    taskId: 7,
    studentId: 6,
    studentName: '李四',
    status: 'submitted',
    score: null,
    content: '第三章作业作答',
    submittedAt: '2026-10-18 10:00:00',
  },
]

/** 成绩页必须真正命中的数据源（缺一即视为页面取数面又漂了） */
const GRADE_PAGE_SOURCES = [
  '/api/courses/my',
  '/api/analytics/courses/1/student-progress',
  '/api/grade/report/course/1',
  '/api/tasks/pending-grading',
]

/** 按 pathname 分派的只读数据表；命中不到即落兜底 */
function readMock(path: string): { data: unknown } | null {
  if (path === '/api/courses/my') return { data: courses }
  if (path === '/api/courses/1') return { data: courses[0] }
  if (path === '/api/courses/1/stats') return { data: { totalStudents: 2 } }
  if (path === '/api/analytics/courses/1/student-progress') return { data: studentProgress }
  if (path === '/api/grade/report/course/1') return { data: courseReport }
  if (path === '/api/tasks/pending-grading') return { data: pendingTasks }
  if (path === '/api/tasks') return { data: tasksOfCourse }
  if (path === '/api/submissions/task/7') return { data: { records: submissions, total: 1 } }
  return null
}

async function fulfill(route: Route, data: unknown) {
  await route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 200, message: 'success', data }),
  })
}

/** 注册唯一入口：命中登记表记入 served，POST ai-grade 单独记，其余进 fallback */
async function mockApi(page: Page) {
  const served: string[] = []
  const fallback: string[] = []

  await page.route('/api/**', async (route) => {
    const path = new URL(route.request().url()).pathname
    const method = route.request().method()

    if (method === 'POST' && /^\/api\/submissions\/\d+\/ai-grade$/.test(path)) {
      served.push(path)
      await fulfill(route, { ...submissions[0], score: 72, status: 'graded' })
      return
    }

    const hit = method === 'GET' ? readMock(path) : null
    if (hit) {
      served.push(path)
      await fulfill(route, hit.data)
      return
    }

    fallback.push(path)
    await fulfill(route, [])
  })

  return { served, fallback }
}

test.describe('成绩管理（教师端主链路）', () => {
  test.beforeEach(async ({ page }) => {
    await loginAs(page, 'TEACHER')
  })

  test('成绩列表渲染：按课程聚合学情与总评，且四个数据源全部命中专用 mock', async ({ page }) => {
    const { served, fallback } = await mockApi(page)

    await page.goto('/teacher/grades-manage')
    await page.waitForLoadState('networkidle')

    // 页面标题与统计卡标签
    await expect(page.getByText('成绩管理').first()).toBeVisible()
    await expect(page.getByText('平均分')).toBeVisible()

    // 取数面守卫：漂到新端点时这里先红，而不是让兜底把表格静默清空
    expect(served).toEqual(expect.arrayContaining(GRADE_PAGE_SOURCES))
    expect(fallback.filter((path) => GRADE_PAGE_SOURCES.includes(path))).toEqual([])

    // 两名学生来自聚合结果：行文本含学号、姓名、总评与等级
    const zhang = page.locator('tr', { hasText: '张三' })
    const li = page.locator('tr', { hasText: '李四' })
    await expect(zhang.first()).toBeVisible()
    await expect(li.first()).toBeVisible()
    await expect(zhang.getByText('2023001')).toBeVisible()
    await expect(zhang.getByText('80')).toBeVisible()
    await expect(zhang.getByText('90')).toBeVisible()
    await expect(zhang.getByText('85')).toBeVisible()
    await expect(zhang.getByText('B')).toBeVisible()
    await expect(li.getByText('2023002')).toBeVisible()
    await expect(li.getByText('68')).toBeVisible()
    await expect(li.getByText('72')).toBeVisible()
    await expect(li.getByText('C')).toBeVisible()
    // 考试均分为 null 时页面显示 --，不得伪装成 0
    await expect(li.getByText('--')).toBeVisible()
    // 统计卡由聚合总评算出：(85 + 72) / 2 = 78.5
    await expect(page.getByText('78.5')).toBeVisible()
    // 待批阅任务面板条目来自 /api/tasks/pending-grading
    await expect(page.getByRole('button', { name: /第三章作业/ })).toBeVisible()
  })

  test('AI 批阅：从待批阅任务进入提交页，点击后对提交 id 发起 AI 评分', async ({ page }) => {
    const { served, fallback } = await mockApi(page)

    await page.goto('/teacher/grades-manage')
    await page.waitForLoadState('networkidle')

    // 真实入口：成绩表已无 AI 按钮，批阅要从待批阅任务快捷入口进入
    await page.getByRole('button', { name: /第三章作业/ }).click()
    await expect(page).toHaveURL(/\/teacher\/courses\/1\/submissions/)
    // 驾驶舱子视图是懒加载块，且成绩页也有「提交记录」字样（页副标题）与同名学生行，
    // 必须等这条只属于提交页的说明文案出现再断言——否则命中的是还没换掉的成绩页 DOM。
    await expect(page.getByText('按任务查看提交记录并进行批改')).toBeVisible()
    // taskId 已被 honor：面板标题回显所选任务（URL 上的 query 会被路由归一化去掉）
    await expect(page.getByText('任务：第三章作业')).toBeVisible()

    const row = page.locator('tr', { hasText: '李四' })
    await expect(row).toBeVisible()
    // 任务列表与提交记录都必须走登记表，不能落到兜底
    expect(served).toEqual(
      expect.arrayContaining(['/api/tasks', '/api/submissions/task/7']),
    )
    expect(fallback.filter((path) => path.startsWith('/api/submissions/task/'))).toEqual([])

    await row.getByText('AI 评分').click()

    await expect(page.getByText('AI 评分完成')).toBeVisible()
    // 评分对象是提交 id（101），不是 studentId
    expect(served).toContain('/api/submissions/101/ai-grade')
    expect(fallback).not.toContain('/api/submissions/101/ai-grade')
  })
})
