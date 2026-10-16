import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { reactive, nextTick } from 'vue'

/**
 * AppShell 全局快捷键接线（D2-7 第八批：快捷键体系）
 *
 * <p>锁定壳层接线：①Ctrl/⌘+K 与 / 派发 FOCUS_GLOBAL_SEARCH_EVENT（消费方为
 * AtbGlobalSearch）；②? 打开/关闭快捷键说明弹层（再按 ? 关闭；其他弹层打开时
 * 不叠加）；③输入态下 / 不触发（守卫在原语层，这里验证接线未绕过）。</p>
 */

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  logout: vi.fn(),
  guideShow: vi.fn(),
  beforeEach: vi.fn(),
  afterEach: vi.fn(),
  route: {
    path: '/admin/dashboard',
    fullPath: '/admin/dashboard',
    matched: [] as unknown[],
  },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: mocks.push,
    beforeEach: mocks.beforeEach,
    afterEach: mocks.afterEach,
  }),
  useRoute: () => mocks.route,
}))
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ logout: mocks.logout }),
}))
vi.mock('../components/AppSidebar.vue', () => ({
  default: { name: 'AppSidebar', template: '<div class="sidebar-stub" />' },
}))
vi.mock('../components/AppTopbar.vue', () => ({
  default: { name: 'AppTopbar', template: '<div class="topbar-stub" />' },
}))
vi.mock('@/components/OnboardingGuide.vue', () => ({
  default: {
    name: 'OnboardingGuide',
    setup(_p: unknown, { expose }: { expose: (o: unknown) => void }) {
      expose({ show: mocks.guideShow })
      return () => null
    },
  },
}))
vi.mock('../components/KeyboardShortcutsDialog.vue', () => ({
  default: {
    name: 'KeyboardShortcutsDialog',
    props: { modelValue: { type: Boolean, default: false } },
    template: '<div class="ks-stub" :data-visible="String(modelValue)" />',
  },
}))

import AppShell from '../AppShell.vue'
import { FOCUS_GLOBAL_SEARCH_EVENT } from '@/composables/useKeyboardShortcuts'

function mountShell() {
  return mount(AppShell, {
    props: { role: 'ADMIN', profilePath: '/admin/profile' },
    attachTo: document.body,
    global: { stubs: { 'router-view': true } },
  })
}

function press(key: string, init: KeyboardEventInit = {}): KeyboardEvent {
  const event = new KeyboardEvent('keydown', { key, cancelable: true, ...init })
  window.dispatchEvent(event)
  return event
}

describe('AppShell — D2-7 全局快捷键接线', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.replaceChildren()
    // jsdom 未实现 Element.scrollTo（AppShell 回顶 watcher 依赖）
    if (!Element.prototype.scrollTo) Element.prototype.scrollTo = vi.fn()
    mocks.route = reactive({
      path: '/admin/dashboard',
      fullPath: '/admin/dashboard',
      matched: [{ meta: { title: '管理端工作台' } }],
    })
  })
  afterEach(() => {
    document.body.replaceChildren()
  })

  it('Ctrl+K 与 / 派发聚焦搜索事件', async () => {
    const heard = vi.fn()
    window.addEventListener(FOCUS_GLOBAL_SEARCH_EVENT, heard)
    const wrapper = mountShell()
    await flushPromises()

    press('k', { ctrlKey: true })
    expect(heard).toHaveBeenCalledTimes(1)
    press('/')
    expect(heard).toHaveBeenCalledTimes(2)

    window.removeEventListener(FOCUS_GLOBAL_SEARCH_EVENT, heard)
    wrapper.unmount()
  })

  it('? 打开快捷键说明弹层，再按 ? 关闭（toggle）', async () => {
    const wrapper = mountShell()
    await flushPromises()

    expect(wrapper.get('.ks-stub').attributes('data-visible')).toBe('false')
    press('?')
    await nextTick()
    expect(wrapper.get('.ks-stub').attributes('data-visible')).toBe('true')

    press('?')
    await nextTick()
    expect(wrapper.get('.ks-stub').attributes('data-visible')).toBe('false')
    wrapper.unmount()
  })

  it('其他弹层打开时：/ 不穿透，? 不叠加说明层', async () => {
    const heard = vi.fn()
    window.addEventListener(FOCUS_GLOBAL_SEARCH_EVENT, heard)
    const wrapper = mountShell()
    await flushPromises()

    const overlay = document.createElement('div')
    overlay.className = 'el-overlay'
    document.body.appendChild(overlay)

    press('/')
    expect(heard).not.toHaveBeenCalled()
    press('?')
    await nextTick()
    expect(wrapper.get('.ks-stub').attributes('data-visible')).toBe('false')

    overlay.remove()
    window.removeEventListener(FOCUS_GLOBAL_SEARCH_EVENT, heard)
    wrapper.unmount()
  })

  it('输入态下按 / 不派发（原语守卫生效，接线未绕过）', async () => {
    const heard = vi.fn()
    window.addEventListener(FOCUS_GLOBAL_SEARCH_EVENT, heard)
    const wrapper = mountShell()
    await flushPromises()

    const input = document.createElement('input')
    document.body.appendChild(input)
    input.focus()
    input.dispatchEvent(new KeyboardEvent('keydown', { key: '/', bubbles: true, cancelable: true }))
    expect(heard).not.toHaveBeenCalled()

    window.removeEventListener(FOCUS_GLOBAL_SEARCH_EVENT, heard)
    wrapper.unmount()
  })
})
