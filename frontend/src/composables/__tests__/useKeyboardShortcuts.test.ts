import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import {
  useGlobalHotkeys,
  isEditableTarget,
  hasOpenOverlay,
  type HotkeyBinding,
} from '@/composables/useKeyboardShortcuts'

/**
 * 全局快捷键原语（D2-7 第八批：快捷键体系）
 *
 * <p>锁定四条核心口径：①带修饰/不带修饰互斥（Ctrl+K 不命中裸 k 绑定，反之亦然）；
 * ②输入态守卫（input 里打字不被单键快捷键劫持，allowInEditable 可豁免）；
 * ③弹层遮罩守卫（.el-overlay 打开时不穿透，allowWhenOverlayOpen 可豁免）；
 * ④命中即 preventDefault（/ 与 Ctrl+K 都是浏览器保留行为）；另含卸载清理。</p>
 */

function mountWithBindings(bindings: HotkeyBinding[]) {
  return mount(
    defineComponent({
      setup() {
        useGlobalHotkeys(bindings)
        return () => null
      },
    }),
    { attachTo: document.body },
  )
}

/** 在 window 上派发 keydown（默认模拟「焦点在壳层」：target 非可编辑元素） */
function press(key: string, init: KeyboardEventInit = {}): KeyboardEvent {
  const event = new KeyboardEvent('keydown', { key, cancelable: true, ...init })
  window.dispatchEvent(event)
  return event
}

describe('useKeyboardShortcuts — 全局快捷键原语', () => {
  beforeEach(() => {
    document.body.replaceChildren()
  })
  afterEach(() => {
    document.body.replaceChildren()
  })

  it('带修饰绑定：Ctrl+K 触发、裸 K 不触发（互斥）', () => {
    const handler = vi.fn()
    const wrapper = mountWithBindings([{ key: 'k', mod: true, handler }])
    press('k', { ctrlKey: true })
    press('k')
    expect(handler).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('裸键绑定：/ 触发、带 Ctrl 不触发（互斥）；Alt 组合不误触', () => {
    const handler = vi.fn()
    const wrapper = mountWithBindings([{ key: '/', handler }])
    press('/')
    press('/', { ctrlKey: true })
    press('/', { altKey: true })
    expect(handler).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('命中即 preventDefault（/ 是 Firefox 快速查找、Ctrl+K 是 Chrome 地址栏保留键）', () => {
    const handler = vi.fn()
    const wrapper = mountWithBindings([{ key: '/', handler }])
    const event = press('/')
    expect(event.defaultPrevented).toBe(true)
    wrapper.unmount()
  })

  it('输入态守卫：在 input 上打字不触发单键快捷键；allowInEditable 可豁免', () => {
    const guarded = vi.fn()
    const exempt = vi.fn()
    const input = document.createElement('input')
    document.body.appendChild(input)
    const wrapper = mountWithBindings([
      { key: '/', handler: guarded },
      { key: '?', handler: exempt, allowInEditable: true },
    ])

    input.dispatchEvent(new KeyboardEvent('keydown', { key: '/', bubbles: true, cancelable: true }))
    expect(guarded).not.toHaveBeenCalled()

    input.dispatchEvent(new KeyboardEvent('keydown', { key: '?', bubbles: true, cancelable: true }))
    expect(exempt).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('弹层遮罩守卫：.el-overlay 打开时不触发；allowWhenOverlayOpen 可豁免', () => {
    const guarded = vi.fn()
    const exempt = vi.fn()
    const wrapper = mountWithBindings([
      { key: '/', handler: guarded },
      { key: '?', handler: exempt, allowWhenOverlayOpen: true },
    ])

    const overlay = document.createElement('div')
    overlay.className = 'el-overlay'
    document.body.appendChild(overlay)

    press('/')
    expect(guarded).not.toHaveBeenCalled()
    press('?')
    expect(exempt).toHaveBeenCalledTimes(1)

    overlay.remove()
    press('/')
    expect(guarded).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('首个命中项生效（数组顺序即优先级），同一击键只触发一个动作', () => {
    const first = vi.fn()
    const second = vi.fn()
    const wrapper = mountWithBindings([
      { key: '/', handler: first },
      { key: '/', handler: second },
    ])
    press('/')
    expect(first).toHaveBeenCalledTimes(1)
    expect(second).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('卸载后不再响应（生命周期清理）', () => {
    const handler = vi.fn()
    const wrapper = mountWithBindings([{ key: '/', handler }])
    wrapper.unmount()
    press('/')
    expect(handler).not.toHaveBeenCalled()
  })

  it('isEditableTarget：input/textarea/select/contenteditable 为真，普通节点为假', () => {
    const input = document.createElement('input')
    const textarea = document.createElement('textarea')
    const select = document.createElement('select')
    const rich = document.createElement('div')
    rich.setAttribute('contenteditable', 'true')
    const plain = document.createElement('div')
    // 真实事件目标都在文档内；游离节点在 jsdom 下不计算 isContentEditable
    for (const el of [input, textarea, select, rich, plain]) document.body.appendChild(el)

    expect(isEditableTarget(input)).toBe(true)
    expect(isEditableTarget(textarea)).toBe(true)
    expect(isEditableTarget(select)).toBe(true)
    expect(isEditableTarget(rich)).toBe(true)
    expect(isEditableTarget(plain)).toBe(false)
    expect(isEditableTarget(null)).toBe(false)
    expect(isEditableTarget(window)).toBe(false)
  })

  it('hasOpenOverlay：以 .el-overlay 存在与否判定', () => {
    expect(hasOpenOverlay(document)).toBe(false)
    const overlay = document.createElement('div')
    overlay.className = 'el-overlay'
    document.body.appendChild(overlay)
    expect(hasOpenOverlay(document)).toBe(true)
    overlay.remove()
  })
})
