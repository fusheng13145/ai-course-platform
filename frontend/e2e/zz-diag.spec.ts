import { test } from '@playwright/test'
import { setLocalStorageAuth } from './test-helpers'

test('诊断新建试卷提交', async ({ page }) => {
  const logs: string[] = []
  page.on('console', (msg) => {
    if (msg.type() === 'error' || msg.type() === 'warning') logs.push(`[${msg.type()}] ${msg.text()}`)
  })
  page.on('request', (req) => {
    if (req.method() === 'POST') logs.push(`[REQ] ${req.method()} ${req.url()}`)
  })
  await setLocalStorageAuth(page, 'TEACHER')
  await page.route('/api/**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, message: 'success', data: [] }),
    })
  })
  await page.route('/api/exam-papers**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, message: 'success', data: { records: [], total: 0 } }),
    })
  })
  await page.route('/api/courses/my**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ code: 200, message: 'success', data: [{ id: 1, name: '数据结构' }] }),
    })
  })
  await page.goto('/teacher/exams')
  await page.waitForLoadState('networkidle')
  await page.getByRole('button', { name: /新建试卷/ }).click()

  const dialog = page.locator('.el-dialog', { hasText: '新建试卷' })
  await dialog.locator('.el-select').first().click()
  await page.locator('.el-select-dropdown__item:visible', { hasText: '数据结构' }).first().click()
  await page.getByPlaceholder('请输入试卷标题').fill('期末综合卷')

  // 检查课程 select 是否真正选中
  const selected = await dialog.locator('.el-select').first().locator('.el-select__selected-item, .el-select__placeholder, input').first().inputValue().catch(() => 'N/A')
  logs.push(`[SELECT-VALUE] ${selected}`)

  await dialog.getByRole('button', { name: /创建试卷/ }).click()
  await page.waitForTimeout(2500)

  const errors = await page.evaluate(() => {
    return Array.from(document.querySelectorAll('.el-form-item__error')).map((e) => e.textContent)
  })
  logs.push('[FORM-ERRORS] ' + JSON.stringify(errors))

  console.log('=== DIAG ===')
  console.log(logs.join('\n'))
})
