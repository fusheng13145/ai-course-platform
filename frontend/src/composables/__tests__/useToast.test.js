import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

describe('useToast', () => {
  let toastModule

  beforeEach(async () => {
    vi.resetModules()
    toastModule = await import('@/composables/useToast')
  })

  afterEach(() => {
    document.body.innerHTML = ''
    vi.restoreAllMocks()
  })

  it('toast.show 渲染消息文本', () => {
    toastModule.toast.show('操作成功', 'ok')
    const stack = document.querySelector('.toast-stack')
    expect(stack).not.toBeNull()
    expect(stack.querySelector('.toast.ok')).not.toBeNull()
  })

  it('消息中的 HTML 被转义（XSS 防护）', () => {
    toastModule.toast.show('<img src=x onerror=alert(1)>', 'err')
    const el = document.querySelector('.toast.err')
    expect(el.innerHTML).not.toContain('<img')
    expect(el.innerHTML).toContain('&lt;img src=x onerror=alert(1)&gt;')
  })

  it('脚本标签内容被转义而非执行', () => {
    toastModule.toast.show('<script>window.__xss = 1</script>', 'info')
    const el = document.querySelector('.toast.info')
    expect(el.innerHTML).not.toContain('<script>')
    expect(el.innerHTML).toContain('&lt;script&gt;')
    expect(window.__xss).toBeUndefined()
  })

  it('图标部分保留受控 SVG', () => {
    toastModule.toast.show('正常消息', 'ok')
    const el = document.querySelector('.toast.ok')
    expect(el.innerHTML).toContain('<svg')
    expect(el.innerHTML).toContain('正常消息')
  })

  it('未知类型回退 info 图标', () => {
    toastModule.toast.show('提示', 'unknown-type')
    const el = document.querySelector('.toast.unknown-type')
    expect(el.innerHTML).toContain('<svg')
  })

  it('useToast() 返回 show 方法且行为一致', () => {
    const { show } = toastModule.useToast()
    show('消息', 'info')
    expect(document.querySelector('.toast.info')).not.toBeNull()
  })
})
