import { expect, test } from '@playwright/test'
import { loginAs } from './test-helpers'

/**
 * 课程知识图谱页的真栈巡检（`docs/alignment/09` 成功判据 2：图谱可交互、节点/关系可验证）。
 *
 * 为什么这么写：图谱页的单测只能证明"给了 nodes/links 会画出来"，证明不了
 * ① 它画的点数/边数就是接口给的那些（口径错一位都看不出来）；
 * ② 点一个节点后列出的邻居，正好是这张图里与它相连的边（关系方向的读法最容易错）。
 * 所以这里先取接口读数，再拿页面上的点和邻居条目逐一对账。
 *
 * 课程用 1（Python 程序设计，V1 结构基线 4 点／3 边，student 已选），与"单课垂直"的定位一致。
 * 2026-11-07 V18 演示种子清除后，原 4007（65 点／120 边，st001）已不在库，改用 V1 课程。
 */

interface GraphNode {
  id: number
  name: string
}
interface GraphLink {
  source: number
  target: number
  relationType?: string
}
interface GraphBody {
  data: { nodes?: GraphNode[]; links?: GraphLink[] }
}
interface PathPlan {
  found: boolean
  hops: number
  mode?: string
  path: Array<{ id: number; name?: string }>
}

const COURSE_ID = 1

test('课程图谱：点数/边数与接口一致，点节点能看到它的直接关系', async ({ page }) => {
  test.setTimeout(180000)
  const pageErrors: string[] = []
  page.on('pageerror', (error) => pageErrors.push(`${page.url()} :: ${error.message}`))

  await loginAs(page, 'STUDENT')
  await page.goto(`/student/courses/${COURSE_ID}/graph`, { waitUntil: 'domcontentloaded' })

  const graphResp = await page.request.get(`/api/knowledge/relations/graph/${COURSE_ID}`)
  expect(graphResp.status()).toBe(200)
  const graph = ((await graphResp.json()) as GraphBody).data
  const nodes = graph.nodes || []
  const links = graph.links || []
  expect(nodes.length, '本课程图谱没有节点，巡检无从谈起').toBeGreaterThan(0)

  const kg = page.locator('.kg')
  await expect(kg).toBeVisible({ timeout: 25000 })

  // ① 概览口径：标题上的「N 点 · M 边」必须等于接口读到的规模
  await expect(kg).toContainText(`${nodes.length} 点 · ${links.length} 边`, { timeout: 25000 })

  // ② 图上真的画了这么多点
  const drawn = kg.locator('.kg-node')
  await expect(drawn).toHaveCount(nodes.length, { timeout: 25000 })

  // ③ 点节点 → 邻居列表条数 = 这张图里与该点相连的边数
  const target = nodes[0]
  const expectedNeighbours = links.filter(
    (link) =>
      Number(link.source) === Number(target.id) || Number(link.target) === Number(target.id),
  ).length
  expect(
    expectedNeighbours,
    `首点「${target.name}」在图里没有连边，换一个用例再断言`,
  ).toBeGreaterThan(0)

  // 用节点名定位（SVG 里 text 与 circle 同属一个 <g>）；必要时回退到第 0 个
  // 力导向布局是逐帧收敛的，节点坐标一直在动，Playwright 的"元素稳定"检查会一直等下去
  // （实测 180s 超时）。图谱节点用 force 点击：这里要验的是点击后的关系读数，不是命中精度。
  await page.waitForTimeout(1200)
  const nodeLocator = kg.locator('.kg-node', { hasText: target.name }).first()
  const clickable = (await nodeLocator.count()) ? nodeLocator : drawn.first()
  await clickable.click({ force: true })

  const neighbours = page.locator('.nb-list li')
  await expect(neighbours).toHaveCount(expectedNeighbours, { timeout: 20000 })
  await expect(kg).toContainText('取消选中')

  // ④ 学生端不提供路径规划：入口必须按真实能力隐藏，而不是点进去收一条「规划失败」
  //    （POST /knowledge/path/plan 目前仅 TEACHER/ADMIN，学生恒 403，见后端待办项 B-13㉖）
  await expect(kg).not.toContainText('学习路径规划')

  // ④ 页面不得出现示例读数，也不得有未捕获异常
  await expect(kg).not.toContainText('示例数据')
  expect(pageErrors, '图谱页出现未捕获异常').toEqual([])
})

test('教师端路径规划：步骤/跳数与接口一致，高亮只落在路径上', async ({ page }) => {
  test.setTimeout(180000)
  // 课程 1 是 ts01 任教的 Python 课，知识点 1→3 有直达前置关系（实测 hops=1）
  const courseId = 1
  const sourceId = 1
  const targetId = 3

  await loginAs(page, 'TEACHER')
  await page.goto(`/teacher/courses/${courseId}/graph`, { waitUntil: 'domcontentloaded' })
  const kg = page.locator('.kg')
  await expect(kg).toBeVisible({ timeout: 25000 })

  const planResp = await page.request.post('/api/knowledge/path/plan', {
    data: { sourceId, targetId, maxHops: 5 },
  })
  expect(planResp.status(), '教师调用路径规划应为 200').toBe(200)
  const plan = (await planResp.json()).data as PathPlan
  expect(plan.found, '课程 1 的 1→3 应有可达路径').toBe(true)

  const selects = kg.locator('.form-grid select')
  await selects.nth(0).selectOption(String(sourceId))
  await selects.nth(1).selectOption(String(targetId))
  await kg.getByRole('button', { name: /规划路径|规划中/ }).click()

  await expect(kg.locator('.path-list li')).toHaveCount(plan.path.length, { timeout: 20000 })
  await expect(kg).toContainText(`${plan.hops} 跳`)
  await expect(kg.locator('.kg-node.is-path')).toHaveCount(plan.path.length)
  await expect(kg.locator('.kg-svg line.is-path')).toHaveCount(Math.max(0, plan.hops))
})
