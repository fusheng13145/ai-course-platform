import { expect, test } from '@playwright/test'
import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { loginAs } from './test-helpers'

/**
 * 课程资源闭环（F3）：教师经 UI 上传 → 学生端课程空间看得到 → 下载拿到原文件 → 自清理。
 *
 * 为什么要有这一条：资源上传此前只有「管理端分片会话」那条后台链路的用例，
 * 课程级的「教师传、学生取」从未在真栈上跑通过；而学生端资源区是本批新加的界面
 * （此前学生侧只有章节正文，没有资源入口），必须有一条端到端证据证明它不是摆设。
 *
 * 口径：只动自己造的那一个文件——上传后用 **唯一前缀** 定位资源 ID，清理只删这一条；
 * 绝不碰种子资源。课程用 1（教师 ts01 任教、学生 st001 已选），与题库那条 spec 同源。
 */

const COURSE_ID = 1
const STAMP = `E2E-RES-${Date.now()}`

test.describe('课程资源（真栈）', () => {
  test('教师上传 → 学生看到并可下载 → 自清理', async ({ browser }) => {
    test.setTimeout(120000)
    // 用真 PNG（1x1）：上传端按扩展名白名单拦 .txt，后端还要过 MIME 与魔数校验，
    // 随手写个假后缀的文件连第一道门都过不去。
    const fileName = `${STAMP}.png`
    const content = Buffer.from(
      'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
      'base64',
    )
    const dir = mkdtempSync(join(tmpdir(), 'e2e-res-'))
    const filePath = join(dir, fileName)
    writeFileSync(filePath, content)

    // ① 教师经 UI 上传
    const teacherCtx = await browser.newContext()
    const teacherPage = await teacherCtx.newPage()
    await loginAs(teacherPage, 'TEACHER')
    await teacherPage.goto(`/teacher/courses/${COURSE_ID}/chapters`, {
      waitUntil: 'domcontentloaded',
    })
    await expect(teacherPage.getByRole('button', { name: '上传资源' })).toBeVisible({
      timeout: 20000,
    })
    await teacherPage.getByRole('button', { name: '上传资源' }).click()
    await teacherPage.locator('input[type="file"]').setInputFiles(filePath)
    await expect(teacherPage.getByText(fileName)).toBeVisible({ timeout: 15000 })
    await teacherPage.getByRole('button', { name: '上传', exact: true }).click()
    await expect(teacherPage.locator('tbody')).toContainText(fileName, { timeout: 30000 })

    // 取本次造出来的那一条（按唯一前缀过滤，绝不按第一条猜）
    const listResp = await teacherCtx.request.get(`/api/resources/course/${COURSE_ID}`)
    expect(listResp.status()).toBe(200)
    const listBody = await listResp.json()
    const rows: Array<{ id: number; fileName: string }> = Array.isArray(listBody?.data)
      ? listBody.data
      : []
    const created = rows.find((row) => String(row.fileName || '').includes(STAMP))
    expect(created, `上传后在资源列表里找不到 ${fileName}`).toBeTruthy()
    const resourceId = created!.id

    try {
      // ② 学生端：课程空间的章节页能看到这条资源
      const studentCtx = await browser.newContext()
      const studentPage = await studentCtx.newPage()
      await loginAs(studentPage, 'STUDENT')
      await studentPage.goto(`/student/courses/${COURSE_ID}/chapters`, {
        waitUntil: 'domcontentloaded',
      })
      const resourceSection = studentPage.locator('.reader-resources')
      await expect(resourceSection).toBeVisible({ timeout: 20000 })
      await expect(resourceSection).toContainText(fileName, { timeout: 20000 })

      // ③ 下载：真拿到文件，文件名与内容都对得上
      const downloadPromise = studentPage.waitForEvent('download', { timeout: 30000 })
      await resourceSection
        .locator('li', { hasText: fileName })
        .getByRole('button', { name: '下载' })
        .click()
      const download = await downloadPromise
      expect(download.suggestedFilename()).toBe(fileName)
      const savedPath = await download.path()
      expect(savedPath, '下载事件没有落到磁盘').toBeTruthy()
      expect(readFileSync(savedPath!).equals(content), '下载内容与上传内容不一致').toBe(true)

      await studentCtx.close()
    } finally {
      // ④ 自清理：只删本次造的那一条
      const del = await teacherCtx.request.delete(`/api/resources/${resourceId}`)
      expect([200, 204].includes(del.status()), `清理资源失败：HTTP ${del.status()}`).toBe(true)
      const after = await teacherCtx.request.get(`/api/resources/course/${COURSE_ID}`)
      const afterBody = await after.json()
      const remaining = (Array.isArray(afterBody?.data) ? afterBody.data : []).filter(
        (row: { fileName?: string }) => String(row.fileName || '').includes(STAMP),
      )
      expect(remaining, '清理后仍有本次前缀的资源残留').toHaveLength(0)
      await teacherCtx.close()
    }
  })
})
