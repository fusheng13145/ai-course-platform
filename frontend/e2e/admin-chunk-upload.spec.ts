import { test, expect } from '@playwright/test'
import { loginAs } from './test-helpers'
import fs from 'node:fs'
import path from 'node:path'

/**
 * 本地文件分片上传走一次真实产品路径（DR-69 = A 验收线④）
 *
 * 与单元/组件测试的分工：那里钉的是报文与状态机；这里钉的是
 * 「页面 → 会话 → 分片落盘 → 合并 → 直链可取回」整条链路真的通，
 * 且中途断掉一片后，续传是按服务端磁盘真值补片而不是重开一个会话。
 *
 * 刻意不自己 POST 造会话：那样测的是脚本，不是产品。
 */

const MB = 1024 * 1024

/** 2.5MB 的确定性字节流：分片大小 1MB → 必然切成 3 片 */
function writeSamplePdf(dir: string, name: string): string {
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true })
  const file = path.join(dir, name)
  const bytes = Buffer.allocUnsafe(Math.round(2.5 * MB))
  let s = 20261017
  for (let i = 0; i < bytes.length; i++) {
    s = (s * 1103515245 + 12345) & 0x7fffffff
    bytes[i] = s % 251
  }
  fs.writeFileSync(file, bytes)
  return file
}

async function openUploadPage(page) {
  await loginAs(page, 'ADMIN')
  await page.goto('/admin/file-upload')
  await expect(page.getByRole('heading', { name: '本地文件分片上传' })).toBeVisible()
}

async function fillKnobs(page, chunkMb: number, thresholdMb: number) {
  await page.getByLabel(/分片大小/).fill(String(chunkMb))
  await page.getByLabel(/切片阈值/).fill(String(thresholdMb))
}

test.describe('分片上传入口（管理员）', () => {
  test('选文件 → 3 片上传 → 合并 → 直链能取回完整字节', async ({ page }, testInfo) => {
    const sample = writeSamplePdf(testInfo.outputDir, 'chunk-upload.pdf')
    await openUploadPage(page)

    await page.setInputFiles('input[type="file"]', sample)
    await expect(page.locator('.picked')).toContainText('chunk-upload.pdf')
    await fillKnobs(page, 1, 1)

    await page.getByRole('button', { name: '开始上传' }).click()

    const result = page.getByTestId('upload-result')
    await expect(result).toBeVisible({ timeout: 30000 })
    const href = await result.locator('a').getAttribute('href')
    expect(href).toMatch(/^\/api\/uploads\/\d{4}\/\d{2}\/[0-9a-f]{32}_chunk-upload\.pdf$/)

    // 直链必须真的取得回，且字节数等于源文件：只看 UI 等于什么都没验
    const resp = await page.request.get(href!)
    expect(resp.status()).toBe(200)
    const body = await resp.body()
    expect(body.length).toBe(Math.round(2.5 * MB))

    // 合并后的会话要出现在列表里（不靠手点刷新）
    await expect(page.getByText('chunk-upload.pdf').first()).toBeVisible()
  })

  test('第 2 片传输中断：续传按服务端缺失索引补片，不重建会话', async ({ page }, testInfo) => {
    const sample = writeSamplePdf(testInfo.outputDir, 'chunk-resume.pdf')
    await openUploadPage(page)

    await page.setInputFiles('input[type="file"]', sample)
    await fillKnobs(page, 1, 1)

    // 只掐断第 1 序号（第 2 片）那一次请求，其余照常落盘
    let aborted = false
    await page.route('**/upload-sessions/*/chunks/1', (route) => {
      if (aborted) return route.continue()
      aborted = true
      return route.abort('connectionreset')
    })

    await page.getByRole('button', { name: '开始上传' }).click()
    const resumeButton = page.getByRole('button', { name: '按磁盘真值续传' })
    await expect(resumeButton).toBeVisible({ timeout: 30000 })

    // 记下中断时使用的会话号：续传必须沿用同一个会话，而不是悄悄重开一个
    const sessionLine = page.locator('.plan-hint')
    const sidFirstRound = (await sessionLine.textContent())?.match(/会话\s+(\d+)/)?.[1]
    expect(sidFirstRound).toBeTruthy()

    await page.unroute('**/upload-sessions/*/chunks/1')
    await resumeButton.click()

    const result = page.getByTestId('upload-result')
    await expect(result).toBeVisible({ timeout: 30000 })
    const href = await result.locator('a').getAttribute('href')
    const body = await (await page.request.get(href!)).body()
    expect(body.length).toBe(Math.round(2.5 * MB))
    expect((await sessionLine.textContent())?.match(/会话\s+(\d+)/)?.[1]).toBe(sidFirstRound)
  })
})
