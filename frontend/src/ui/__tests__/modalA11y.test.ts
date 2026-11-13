import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'
import Modal from '../components/Modal.vue'

const FOCUSABLE =
  'a[href],button:not([disabled]),textarea:not([disabled]),input:not([disabled]),select:not([disabled]),[tabindex]:not([tabindex="-1"])'

/**
 * 宿主同时驱动「单弹层」与「双弹层叠加」两种形态，避免一个测试文件里出现两个组件。
 */
const Host = defineComponent({
  props: { nested: { type: Boolean, default: false } },
  setup(props) {
    const outer = ref(false)
    const inner = ref(false)

    function modal(open: boolean, title: string, onToggle: (value: boolean) => void) {
      if (!open) return null
      return h(
        Modal,
        { open: true, title, 'onUpdate:open': onToggle },
        {
          default: () => [
            h('input', { id: `field-${title}`, 'aria-label': '文件名' }),
            h('button', { id: `confirm-${title}` }, '确定'),
          ],
        },
      )
    }

    return () => [
      h('button', { id: 'trigger-outer', onClick: () => (outer.value = true) }, '打开外层'),
      props.nested
        ? h('button', { id: 'trigger-inner', onClick: () => (inner.value = true) }, '打开内层')
        : null,
      modal(outer.value, '确认导入题库', (value) => (outer.value = value)),
      props.nested ? modal(inner.value, '内层确认', (value) => (inner.value = value)) : null,
    ]
  },
})

const wrappers: VueWrapper[] = []

function mountHost(nested = false): VueWrapper {
  const wrapper = mount(Host, { props: { nested }, attachTo: document.body })
  wrappers.push(wrapper)
  return wrapper
}

function panel(index = 0): HTMLElement | null {
  return document.querySelectorAll<HTMLElement>('.modal-panel')[index] ?? null
}

function liveText(): string {
  return document.querySelector('[aria-live="polite"]')?.textContent ?? ''
}

function closePanel(index = 0): void {
  panel(index)!.querySelector<HTMLElement>('.icon-btn')!.click()
}

async function openModal(): Promise<HTMLElement> {
  mountHost()
  const trigger = document.getElementById('trigger-outer')!
  trigger.focus()
  trigger.click()
  await flushPromises()
  return trigger
}

beforeEach(() => {
  while (document.body.firstChild) document.body.removeChild(document.body.firstChild)
  document.body.style.overflow = ''
  document.body.style.paddingRight = ''
})

afterEach(() => {
  while (wrappers.length) wrappers.pop()!.unmount()
  document.body.style.overflow = ''
})

describe('弹层无障碍', () => {
  it('打开时锁背景滚动、初始焦点入弹层并播报', async () => {
    await openModal()
    expect(panel()).not.toBeNull()
    expect(document.body.style.overflow).toBe('hidden')
    expect(panel()!.contains(document.activeElement)).toBe(true)
    expect(liveText()).toContain('确认导入题库')
  })

  it('Tab 到底部回卷到首个可聚焦元素，Shift+Tab 反向回卷', async () => {
    await openModal()
    const items = Array.from(panel()!.querySelectorAll<HTMLElement>(FOCUSABLE))
    expect(items.length).toBeGreaterThan(1)

    items[items.length - 1].focus()
    panel()!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }))
    expect(document.activeElement).toBe(items[0])

    items[0].focus()
    panel()!.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Tab', shiftKey: true, bubbles: true }),
    )
    expect(document.activeElement).toBe(items[items.length - 1])
  })

  it('焦点在背景时按 Tab 会被拉回弹层内', async () => {
    const background = await openModal()
    background.focus()
    panel()!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Tab', bubbles: true }))
    expect(panel()!.contains(document.activeElement)).toBe(true)
    expect(document.activeElement).not.toBe(background)
  })

  it('关闭后恢复滚动与触发元素焦点，并播报关闭', async () => {
    const trigger = await openModal()
    expect(document.body.style.overflow).toBe('hidden')

    closePanel()
    await flushPromises()

    expect(panel()).toBeNull()
    expect(document.body.style.overflow).toBe('')
    expect(document.activeElement).toBe(trigger)
    expect(liveText()).toContain('对话框已关闭')
  })

  it('两个弹层叠加时，只有最后一个关闭才解锁滚动', async () => {
    mountHost(true)
    document.getElementById('trigger-outer')!.click()
    document.getElementById('trigger-inner')!.click()
    await flushPromises()

    expect(document.querySelectorAll('.modal-panel')).toHaveLength(2)
    expect(document.body.style.overflow).toBe('hidden')

    closePanel(0)
    await flushPromises()
    expect(document.querySelectorAll('.modal-panel')).toHaveLength(1)
    expect(document.body.style.overflow, '内层还开着就不能解锁').toBe('hidden')

    closePanel(0)
    await flushPromises()
    expect(document.body.style.overflow, '最后一个弹层关闭后必须恢复滚动').toBe('')
  })
})
