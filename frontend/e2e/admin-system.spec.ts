import { expect, test } from '@playwright/test'
import { loginAs } from './test-helpers'

/**
 * 管理端「系统设置」的真栈对账（批次 24）。
 *
 * 这一页此前是「待扩展」空态。接真后要钉的是三件事：
 * ① 配置条数与接口一致（分页前总数、分页器读数）；
 * ② 服务状态与 `/admin/health` 一致（不能只显示「UP」而不给依赖明细）；
 * ③ **密钥类配置不得明文出现在页面上**——`ai_api_key` 这类键在列表里必须掩码。
 */
interface ConfigRow {
  key: string
  value?: string | null
  group?: string | null
}

test.describe('管理端系统设置（真栈）', () => {
  test('配置条数与服务状态与接口一致；密钥类配置掩码显示', async ({ page }) => {
    test.setTimeout(120000)
    await loginAs(page, 'ADMIN')
    await page.goto('/admin/system', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.system-body')).toBeVisible({ timeout: 25000 })
    await expect(page.locator('.system-body tbody tr').first()).toBeVisible({ timeout: 25000 })

    const configsResp = await page.request.get('/api/admin/configs')
    expect(configsResp.status()).toBe(200)
    const configs = ((await configsResp.json()).data as ConfigRow[]) ?? []
    expect(configs.length, '接口没有返回配置项').toBeGreaterThan(0)

    const healthResp = await page.request.get('/api/admin/health')
    expect(healthResp.status()).toBe(200)
    const health = (await healthResp.json()).data as {
      status?: string
      database?: string
      redis?: string
    }

    // 分页器读数与接口总数一致
    await expect(page.locator('.system-body .card-foot')).toContainText(`共 ${configs.length} 条`)
    // 首屏行数＝一页（20 条），且与服务端总数不矛盾
    const rows = await page.locator('.system-body tbody tr').count()
    expect(rows).toBe(Math.min(20, configs.length))

    // 依赖状态与接口一致
    const healthText = await page.locator('.system-body .health-card').innerText()
    expect(healthText).toContain(health.status || '—')
    expect(healthText).toContain(health.database || '—')
    expect(healthText).toContain(health.redis || '—')

    // 密钥类配置：掩码显示，真实值不出现在页面上
    const sensitive = configs.filter((row) => /(key|secret|password|token)/i.test(row.key))
    expect(sensitive.length, '库内没有密钥类配置，用例失去意义').toBeGreaterThan(0)
    const pageText = await page.locator('.system-body').innerText()
    for (const row of sensitive) {
      if (!row.value) continue
      expect(pageText, `密钥 ${row.key} 的真实值出现在页面上`).not.toContain(String(row.value))
    }
    await expect(page.locator('.system-body')).toContainText('••••••')

    await expect(page.locator('.system-body')).not.toContainText('示例数据')
  })

  test('按分组筛选后行数收敛到该组配置数', async ({ page }) => {
    test.setTimeout(120000)
    await loginAs(page, 'ADMIN')
    await page.goto('/admin/system', { waitUntil: 'domcontentloaded' })
    await expect(page.locator('.system-body tbody tr').first()).toBeVisible({ timeout: 25000 })

    const configsResp = await page.request.get('/api/admin/configs')
    const configs = ((await configsResp.json()).data as ConfigRow[]) ?? []
    const group = configs[0]?.group
    expect(group, '接口没有返回分组').toBeTruthy()

    await page.locator('.system-body select').selectOption(String(group))
    await expect(page.locator('.system-body .card-foot')).toContainText(
      `共 ${configs.filter((row) => row.group === group).length} 条`,
    )
  })
})
