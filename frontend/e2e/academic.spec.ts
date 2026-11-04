import { expect, test } from '@playwright/test'
import { loginAs } from './test-helpers'

/**
 * 学业档案三页签的真栈巡检（F3「成绩 / 学习中心 / 错题本」）。
 *
 * 为什么这样写：这三块此前只有组件级单测（喂 mock），没有一次「界面读数 vs 接口读数」
 * 的对账。而这类页最容易出的不是崩溃，是**静默的 0**——字段名对不上、作用域取错、
 * 分路失败被吞，页面都会安静地显示 0，看起来像"确实没有数据"。所以这里不硬编码业务数字
 * （种子会变），而是用同一会话直接打接口，断言界面显示的就是接口给的数。
 *
 * 口径：只读，不做任何写操作（标记掌握/移除会改种子数据，本巡检不碰）。
 * 页面文案里出现的「未知知识点」「断线续考验证题」是早前 e2e/探针留下的数据面问题，
 * 已在待办里登记，不属本用例的失败条件。
 */

interface WrongStats {
  total: number
  mastered: number
  unmastered: number
  masteryRate: number
}
interface LearningStats {
  totalDuration: number
  learningDays: number
  currentCourses: number
  completedCourses: number
}
interface PageBody<T> {
  data: T
}

test('学业档案三页签：界面读数与接口读数一致（成绩／学习中心／错题本）', async ({ page }) => {
  test.setTimeout(180000)
  const pageErrors: string[] = []
  page.on('pageerror', (error) => pageErrors.push(`${page.url()} :: ${error.message}`))

  // 用 st001：这个内置账号有真实学习记录（student 账号的学习统计全为 0，
  // 那样 KPI 断言会退化成"0 等于 0"，抓不到字段名对不上这类缺陷）。
  await loginAs(page, 'STUDENT', 'st001')
  await page.goto('/student/academic', { waitUntil: 'domcontentloaded' })

  const body = page.locator('.academic-body')
  await expect(body).toBeVisible({ timeout: 20000 })
  await expect(body.locator('> .tabs button')).toHaveCount(3)
  // 任何一页都不许出现「示例数据」，也不许出现读失败横幅时仍当正常页
  await expect(body).not.toContainText('示例数据')

  // ① 成绩：课程列表来自 GET /student/grades，至少要有课程行
  const gradesResp = await page.request.get('/api/student/grades')
  expect(gradesResp.status()).toBe(200)
  const gradesBody = (await gradesResp.json()) as PageBody<{ courses?: unknown[] }>
  const gradeCourses = gradesBody.data?.courses?.length ?? 0
  if (gradeCourses > 0) {
    await expect(body.locator('table tbody tr').first()).toBeVisible({ timeout: 20000 })
  } else {
    await expect(body).toContainText('暂无')
  }

  // ② 学习中心：KPI 必须等于 GET /learning-records/stats 的读数
  const statsResp = await page.request.get('/api/learning-records/stats')
  expect(statsResp.status()).toBe(200)
  const learning = ((await statsResp.json()) as PageBody<LearningStats>).data

  await body.locator('> .tabs button', { hasText: '学习中心' }).click()
  const lc = page.locator('.lc-panel')
  await expect(lc).toBeVisible({ timeout: 20000 })
  const kpis = lc.locator('.stat .stat-value')
  await expect(kpis).toHaveCount(4)
  await expect(kpis.nth(1)).toHaveText(`${learning.totalDuration ?? 0} 分钟`)
  await expect(kpis.nth(2)).toHaveText(`${learning.learningDays ?? 0} 天`)
  await expect(kpis.nth(3)).toHaveText(`${learning.currentCourses ?? 0} 门`)
  // 日历：当月天数必须画全（28–31 格），不画空月
  const dayCells = lc.locator('.lc-grid .lc-cell:not(.is-blank)')
  expect(await dayCells.count()).toBeGreaterThanOrEqual(28)

  // ③ 错题本：统计卡必须等于 GET /wrong-questions/stats 的读数
  const wrongResp = await page.request.get('/api/wrong-questions/stats')
  expect(wrongResp.status()).toBe(200)
  const wrong = ((await wrongResp.json()) as PageBody<WrongStats>).data

  await body.locator('> .tabs button', { hasText: '错题本' }).click()
  const wb = page.locator('.wb-panel')
  await expect(wb).toBeVisible({ timeout: 20000 })
  const statValues = wb.locator('.wb-stats .stat-value')
  await expect(statValues).toHaveCount(4)
  await expect(statValues.nth(0)).toHaveText(String(wrong.total ?? 0))
  await expect(statValues.nth(1)).toHaveText(String(wrong.unmastered ?? 0))
  await expect(statValues.nth(2)).toHaveText(String(wrong.mastered ?? 0))
  await expect(statValues.nth(3)).toHaveText(`${Number(wrong.masteryRate ?? 0).toFixed(1)}%`)

  // 列表视图：有错题就必须有行；没有错题就给空态（两者不得同时成立/同时不成立）
  if ((wrong.total ?? 0) > 0) {
    await expect(wb.locator('table tbody tr').first()).toBeVisible({ timeout: 20000 })
  } else {
    await expect(wb).toContainText('还没有错题')
  }

  // ④ 切到「按知识点」：分组视图要出内容，且不是空壳
  const groupedResp = await page.request.get('/api/wrong-questions/by-kp')
  const grouped = ((await groupedResp.json()) as PageBody<unknown[]>).data || []
  await wb.locator('.tabs button', { hasText: '按知识点' }).click()
  await expect(wb).toBeVisible({ timeout: 20000 })
  if (grouped.length > 0) {
    await expect(wb.locator('[class*="group"]').first()).toBeVisible({ timeout: 20000 })
  }
  // 视图切换不得把页面打成读失败
  await expect(wb.locator('[role="alert"]')).toHaveCount(0)

  expect(pageErrors, '巡检期间出现未捕获异常').toEqual([])
})
