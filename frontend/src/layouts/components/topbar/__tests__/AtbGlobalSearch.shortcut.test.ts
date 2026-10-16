import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * AtbGlobalSearch 快捷键聚焦入口（D2-7 第八批：快捷键体系）
 *
 * <p>消费 FOCUS_GLOBAL_SEARCH_EVENT：AppShell 的 Ctrl/⌘+K 与 / 经事件解耦
 * 落到本组件——锁定「事件 → 输入框获得焦点」，以及卸载后的监听清理。</p>
 */

vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/api/search', () => ({ searchAll: vi.fn() }))

import AtbGlobalSearch from '@/layouts/components/topbar/AtbGlobalSearch.vue'
import { FOCUS_GLOBAL_SEARCH_EVENT } from '@/composables/useKeyboardShortcuts'

function mountSearch() {
  return mount(AtbGlobalSearch, {
    props: { role: 'ADMIN' },
    attachTo: document.body,
  })
}

describe('AtbGlobalSearch — D2-7 快捷键聚焦', () => {
  beforeEach(() => {
    document.body.replaceChildren()
  })
  afterEach(() => {
    document.body.replaceChildren()
  })

  it('收到聚焦事件后输入框获得焦点', async () => {
    const wrapper = mountSearch()
    await flushPromises()

    const input = wrapper.get('input.gs-input').element
    expect(document.activeElement).not.toBe(input)

    window.dispatchEvent(new CustomEvent(FOCUS_GLOBAL_SEARCH_EVENT))
    await flushPromises()

    expect(document.activeElement).toBe(input)
    wrapper.unmount()
  })

  it('卸载后监听已清理：再派发事件不再持有焦点', async () => {
    const wrapper = mountSearch()
    await flushPromises()
    wrapper.unmount()

    const other = document.createElement('button')
    document.body.appendChild(other)
    other.focus()

    window.dispatchEvent(new CustomEvent(FOCUS_GLOBAL_SEARCH_EVENT))
    await flushPromises()

    expect(document.activeElement).toBe(other)
  })
})
