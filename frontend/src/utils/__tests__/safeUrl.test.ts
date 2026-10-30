import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'
import { safeUrl } from '@/utils/safeUrl'

/**
 * F-3：URL 协议白名单。实现由 TasksTab 的本地函数原样上提，
 * 这组用例同时是"上提前后口径一致"的证据（判据本体：只放行 http(s)，其余回空串）。
 */
describe('utils/safeUrl · 协议白名单', () => {
  it('happy：绝对 http(s) 原样放行，相对地址按当前源解析', () => {
    expect(safeUrl('https://cdn.example.com/a.png')).toBe('https://cdn.example.com/a.png')
    expect(safeUrl('http://cdn.example.com/a.png')).toBe('http://cdn.example.com/a.png')
    expect(safeUrl('/uploads/cover.png')).toBe(`${window.location.origin}/uploads/cover.png`)
    expect(safeUrl('uploads/cover.png')).toBe(`${window.location.origin}/uploads/cover.png`)
  })

  it('边界：空串／纯空白／非字符串／无法解析的地址一律回空串', () => {
    expect(safeUrl('')).toBe('')
    expect(safeUrl('   ')).toBe('')
    expect(safeUrl(undefined)).toBe('')
    expect(safeUrl(null as unknown as string)).toBe('')
    expect(safeUrl(42 as unknown as string)).toBe('')
    expect(safeUrl({} as unknown as string)).toBe('')
    // 前后空白按 trim 处理，与上提前一致
    expect(safeUrl('  https://cdn.example.com/a.png  ')).toBe('https://cdn.example.com/a.png')
    // 连 http:// 都补不出主机的畸形值走 catch
    expect(safeUrl('http://')).toBe('')
  })

  it('恶意：伪协议与非网络协议全部拒收（含大小写／制表符绕过）', () => {
    for (const evil of [
      'javascript:alert(1)',
      'JaVaScRiPt:alert(1)',
      '  javascript:alert(1)',
      // 浏览器在解析 URL 前会剥掉 \t\n\r，所以这条绕过在 WHATWG 口径下同样落进 javascript: 分支
      'java\tscript:alert(1)',
      'java\nscript:alert(1)',
      'data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg==',
      'vbscript:msgbox(1)',
      'file:///etc/passwd',
      'blob:https://example.com/1f2e3d',
      'about:blank',
    ]) {
      expect(safeUrl(evil), evil).toBe('')
    }
  })

  it('消费面：TasksTab 改为引用共享实现，本地那份已不存在', () => {
    const tasksTab = readFileSync(
      join(__dirname, '../../v2/views/course/tabs/TasksTab.vue'),
      'utf8',
    )
    expect(tasksTab).toContain("import { safeUrl } from '@/utils/safeUrl'")
    expect(tasksTab).not.toMatch(/function safeUrl\s*\(/)
    // 模板里的两处绑定（附件下载／附件列表）仍按同名函数消费，调用点零改动
    expect(tasksTab.match(/safeUrl\(/g)?.length).toBeGreaterThanOrEqual(2)
  })
})
