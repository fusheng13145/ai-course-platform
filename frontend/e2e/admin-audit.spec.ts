import { expect, test } from '@playwright/test'
import { loginAs } from './test-helpers'

/**
 * 管理端「操作与登录日志」「会话管理」的真栈对账（批次 23）。
 *
 * 单测只能证明「给了数据会画」。这两页真正的风险是**口径**：
 * - 操作日志 / 登录日志的 KPI 必须来自统计端点，不能拿当前页 15 行累加；
 * - 会话页不能把 `is_active=false` 与已过期的会话说成在线。
 * 所以这里不硬编码业务数字，而是同会话打接口、按同一口径算一遍再与界面比。
 *
 * 数据是会长的：本用例自己的登录就会写入一条登录日志与一条操作日志，
 * 因此总量允许极小误差；结构性断言（每页行数、筛选后状态全一致）必须严格。
 */
test.describe('管理端审计与会话（真栈）', () => {
  test('操作/登录日志的读数与筛选口径与接口一致', async ({ page }) => {
    test.setTimeout(120000)
    await loginAs(page, 'ADMIN')
    await page.goto('/admin/audit', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.audit-body')).toBeVisible({ timeout: 25000 })
    await expect(page.locator('.audit-body tbody tr').first()).toBeVisible({ timeout: 25000 })

    const opResp = await page.request.get('/api/admin/operation-logs?page=1&size=15')
    expect(opResp.status()).toBe(200)
    const opPage = (await opResp.json()).data as { list: unknown[]; total: number }
    const opStatsResp = await page.request.get('/api/admin/operation-logs/statistics')
    expect(opStatsResp.status()).toBe(200)
    const opStats = (await opStatsResp.json()).data as {
      totalCount: number
      successCount: number
      failedCount: number
    }

    await expect(page.locator('.audit-body tbody tr')).toHaveCount(Math.min(15, opPage.total), {
      timeout: 20000,
    })
    // KPI 必须是统计端点的数，而不是当前页 15 行
    const auditNumber = async (index: number) =>
      Number(
        (await page.locator('.audit-body .stat-value').nth(index).innerText()).replace(
          /[^\d]/g,
          '',
        ),
      )
    const uiTotal = await auditNumber(0)
    const uiSuccess = await auditNumber(1)
    // KPI 若拿当前页 15 行累加，这里会得到 15 而不是统计端点的数千
    expect(
      Math.abs(uiTotal - opStats.totalCount),
      '操作日志总数与统计端点偏差过大',
    ).toBeLessThanOrEqual(5)
    expect(
      Math.abs(uiSuccess - opStats.successCount),
      '操作日志成功数与统计端点偏差过大',
    ).toBeLessThanOrEqual(5)

    // 筛选：状态=成功 后每一行都必须是成功，不能有失败行混入
    await page.locator('.audit-body select').first().selectOption('SUCCESS')
    const opFiltered = page.waitForResponse(
      (res) => res.url().includes('/api/admin/operation-logs?') && res.status() === 200,
    )
    await page.locator('.audit-body form.search-form button[type="submit"]').click()
    await opFiltered
    await expect(page.locator('.audit-body tbody tr').first()).toBeVisible({ timeout: 20000 })
    const opStatuses = await page.locator('.audit-body tbody tr td:nth-child(5)').allInnerTexts()
    expect(opStatuses.length).toBeGreaterThan(0)
    for (const text of opStatuses) expect(text.trim()).toBe('成功')

    // 切到登录日志：同样对账，且状态筛选=失败 后不得出现成功行
    await page.locator('.audit-body .tabs button', { hasText: '登录日志' }).click()
    await expect(page.locator('.audit-body tbody tr').first()).toBeVisible({ timeout: 20000 })

    const loginResp = await page.request.get('/api/login-logs?page=1&size=15')
    expect(loginResp.status()).toBe(200)
    const loginPage = (await loginResp.json()).data as { list: unknown[]; total: number }
    await expect(page.locator('.audit-body tbody tr')).toHaveCount(Math.min(15, loginPage.total), {
      timeout: 20000,
    })

    await page.locator('.audit-body select').first().selectOption('FAILURE')
    const loginFiltered = page.waitForResponse(
      (res) => res.url().includes('/api/login-logs?') && res.status() === 200,
    )
    await page.locator('.audit-body form.search-form button[type="submit"]').click()
    await loginFiltered
    await expect(page.locator('.audit-body tbody tr').first()).toBeVisible({ timeout: 20000 })
    const loginStatuses = await page.locator('.audit-body tbody tr td:nth-child(3)').allInnerTexts()
    for (const text of loginStatuses) expect(text.trim()).toBe('失败')

    await expect(page.locator('.audit-body')).not.toContainText('示例数据')
  })

  test('会话页默认只展示在线会话，KPI 与接口按同一口径计算', async ({ page }) => {
    test.setTimeout(120000)
    await loginAs(page, 'ADMIN')
    await page.goto('/admin/sessions', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.sessions-body')).toBeVisible({ timeout: 25000 })
    await expect(page.locator('.sessions-body tbody tr').first()).toBeVisible({ timeout: 25000 })

    const resp = await page.request.get('/api/user-sessions/online')
    expect(resp.status()).toBe(200)
    const sessions = (await resp.json()).data as Array<{
      userId: number
      isActive?: boolean
      expiresAt?: string
    }>

    // 与页面同一口径：isActive=true 且未过期
    const now = Date.now()
    const online = sessions.filter(
      (row) => row.isActive === true && (!row.expiresAt || Date.parse(row.expiresAt) > now),
    )
    const onlineUsers = new Set(online.map((row) => String(row.userId))).size

    const kpi = page.locator('.sessions-body .stat-value')
    const onlineText = Number((await kpi.nth(0).innerText()).replace(/[^\d]/g, ''))
    const userText = Number((await kpi.nth(1).innerText()).replace(/[^\d]/g, ''))
    expect(
      Math.abs(onlineText - online.length),
      '在线会话数与接口口径偏差过大',
    ).toBeLessThanOrEqual(5)
    expect(Math.abs(userText - onlineUsers), '在线用户数口径不一致').toBeLessThanOrEqual(2)

    // 默认视图的每一行都只能是在线态（KPI 说明里有「已失效」字样，不能整页搜文本）
    await expect(page.locator('.sessions-body tbody tr')).toHaveCount(Math.min(12, online.length), {
      timeout: 20000,
    })
    const stateCells = await page.locator('.sessions-body tbody tr td:nth-child(6)').allInnerTexts()
    expect(stateCells.length).toBeGreaterThan(0)
    for (const text of stateCells) {
      expect(['在线', '当前会话']).toContain(text.trim().split('\n')[0])
    }

    // 显示全部后行数应覆盖失效会话（至少不比默认少）
    await page.locator('.sessions-body button', { hasText: '显示全部' }).click()
    await expect(page.locator('.sessions-body button', { hasText: '仅看在线' })).toBeVisible()
    const allRows = await page.locator('.sessions-body tbody tr').count()
    expect(allRows).toBeGreaterThanOrEqual(Math.min(12, online.length))
    expect(allRows).toBeLessThanOrEqual(12)

    await expect(page.locator('.sessions-body')).not.toContainText('示例数据')
  })
})
