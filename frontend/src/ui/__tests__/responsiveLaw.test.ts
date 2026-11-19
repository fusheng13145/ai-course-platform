import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

/**
 * 窄屏回归守卫（2026-11-19，P-10）。
 *
 * 真实浏览器探针在 360x740 抓到两处横向溢出：教师顶栏多一个 AI 图标、
 * 创建课程表单的 select 以 420px 固有宽度撑破容器。这里把修复钉在源码层，
 * 防止后续改顶栏或表单样式时把两条窄屏规则删回去；像素面仍由 p10 探针负责。
 */
const RECORD = readFileSync(join(__dirname, '../styles/record.css'), 'utf8')
const FORM = readFileSync(join(__dirname, '../styles/form.css'), 'utf8')

/** 取指定媒体查询的完整块；找不到返回空串，便于断言失败而不是抛错。 */
function mediaBlock(css: string, header: string): string {
  const at = css.indexOf(header)
  if (at < 0) return ''
  const open = css.indexOf('{', at)
  if (open < 0) return ''
  let depth = 0
  for (let i = open; i < css.length; i += 1) {
    if (css[i] === '{') depth += 1
    if (css[i] === '}') {
      depth -= 1
      if (depth === 0) return css.slice(open, i + 1)
    }
  }
  return ''
}

describe('P-10 窄屏回归守卫', () => {
  it('教师顶栏 AI 快捷入口在 620px 以下隐藏', () => {
    const block = mediaBlock(RECORD, '@media (max-width: 620px)')
    expect(block, '缺少 620px 媒体查询').not.toBe('')
    expect(block).toMatch(/\.ai-trigger\s*\{[^}]*display:\s*none/)
  })

  it('表单单列时切断控件固有宽度', () => {
    const block = mediaBlock(FORM, '@media (max-width: 720px)')
    expect(block, '缺少 720px 媒体查询').not.toBe('')
    expect(block).toMatch(/\.form-grid\s+\.field\s*,[^}]*min-width:\s*0/)
    expect(block).toMatch(/\.form-grid\s+\.field\s*>\s*\.input[^}]*width:\s*100%/)
  })

  it('守卫对缺失规则的合成样本保持阴性', () => {
    const fake = '@media (max-width: 620px) { .topbar { padding: 0; } }'
    expect(mediaBlock(fake, '@media (max-width: 620px')).not.toBe('')
    expect(mediaBlock(fake, '@media (max-width: 620px')).not.toMatch(/\.ai-trigger/)
  })
})
