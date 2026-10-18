import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref, nextTick } from 'vue'
import { useDialogFocus, DIALOG_FOCUSABLE_SELECTOR } from '@/composables/useDialogFocus'

/**
 * useDialogFocus 原语测试（D2-7）
 *
 * <p>锁定语义：①打开时焦点移入首个可聚焦控件；②关闭不抢焦点；
 * ③禁用控件被跳过；④DOM 未就绪（ref 为空）安全静默；⑤rAF 等待存在
 * （teleport + 过渡使 DOM 下一帧才稳定）。</p>
 */
describe('useDialogFocus — 对话框焦点管理原语', () => {
  let rafQueue: FrameRequestCallback[] = []
  let mountedRoots: HTMLElement[] = []

  beforeEach(() => {
    rafQueue = []
    mountedRoots = []
    // 以可控队列替换 requestAnimationFrame（测试环境默认实现时序不稳定）
    vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => {
      rafQueue.push(cb)
      return rafQueue.length
    })
    vi.stubGlobal('cancelAnimationFrame', () => {})
  })
  afterEach(() => {
    mountedRoots.forEach((r) => r.remove())
    vi.unstubAllGlobals()
  })

  function makeDialog(firstFocusable: HTMLElement | null) {
    const root = document.createElement('div')
    root.setAttribute('role', 'dialog')
    if (firstFocusable) root.appendChild(firstFocusable)
    // jsdom 中未挂载文档的元素 focus() 无效，需真实挂载
    document.body.appendChild(root)
    mountedRoots.push(root)
    return { $el: root }
  }

  async function openAndFlush(dialogRef: { value: unknown }, visible: ReturnType<typeof ref>) {
    visible.value = true
    await nextTick() // watch 回调（同步）
    await nextTick() // nextTick 内的 focus 逻辑
    rafQueue.forEach((cb) => cb(0)) // 手动冲刷 rAF 队列
    rafQueue = []
  }

  it('打开 → 焦点移入首个可聚焦控件', async () => {
    const input = document.createElement('input')
    const dialogRef = ref(makeDialog(input))
    const visible = ref(false)
    useDialogFocus(dialogRef, visible)

    await openAndFlush(dialogRef, visible)

    expect(document.activeElement).toBe(input)
  })

  it('关闭 → 不抢焦点（activeElement 保持原状）', async () => {
    const input = document.createElement('input')
    const outside = document.createElement('button')
    document.body.appendChild(outside)
    outside.focus()
    const dialogRef = ref(makeDialog(input))
    const visible = ref(false)
    useDialogFocus(dialogRef, visible)

    visible.value = true
    await nextTick()
    visible.value = false // 立即关闭（未冲刷 rAF）
    await nextTick()

    expect(document.activeElement).toBe(outside)
    outside.remove()
  })

  it('禁用控件被跳过：聚焦第一个可用输入框', async () => {
    const disabled = document.createElement('input')
    disabled.setAttribute('disabled', '')
    const usable = document.createElement('input')
    const root = document.createElement('div')
    root.append(disabled, usable)
    document.body.appendChild(root) // jsdom 需挂载后 focus 才生效
    mountedRoots.push(root)
    const dialogRef = ref({ $el: root })
    const visible = ref(false)
    useDialogFocus(dialogRef, visible)

    await openAndFlush(dialogRef, visible)

    expect(document.activeElement).toBe(usable)
  })

  it('DOM 未就绪（dialogRef 为空）→ 安全静默不抛错', async () => {
    const dialogRef = ref(null)
    const visible = ref(false)
    useDialogFocus(dialogRef, visible)

    await expect(openAndFlush(dialogRef, visible)).resolves.toBeUndefined()
  })

  it('选择器覆盖输入/选择/文本域/按钮/链接/tabindex', () => {
    for (const sel of ['input', 'select', 'textarea', 'button', 'a[href]', '[tabindex]']) {
      expect(DIALOG_FOCUSABLE_SELECTOR).toContain(sel)
    }
    expect(DIALOG_FOCUSABLE_SELECTOR).toContain('not([disabled])')
  })
})
