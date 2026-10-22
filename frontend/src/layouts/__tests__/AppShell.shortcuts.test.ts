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
  guideResume: vi.fn(),
  guideProps: [] as Record<string, unknown>[],
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
  useAuthStore: () => ({ logout: mocks.logout, userId: 'u-1' }),
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
    props: { userId: { type: [Number, String], default: null }, role: { type: String } },
    setup(props: Record<string, unknown>, { expose }: { expose: (o: unknown) => void }) {
      mocks.guideProps.push({ ...props })
      expose({ show: mocks.guideShow, resume: mocks.guideResume })
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

/**
 * 遮罩判的是「可见且有面积」，而 jsdom 的 getBoundingClientRect 恒返回 0×0，
 * 所以正向读数必须自己补这块面积（反向读数用裸节点，天然 0×0）。
 */
function putOverlay(className: string, visible: boolean): HTMLElement {
  const el = document.createElement('div')
  el.className = className
  el.style.display = visible ? 'block' : 'none'
  if (visible) {
    el.getBoundingClientRect = () =>
      ({
        width: 800,
        height: 600,
        top: 0,
        left: 0,
        right: 800,
        bottom: 600,
        x: 0,
        y: 0,
        toJSON: () => ({}),
      }) as DOMRect
  }
  document.body.appendChild(el)
  return el
}

describe('AppShell — D2-7 全局快捷键接线', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.guideProps.length = 0
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

    putOverlay('el-overlay', true)

    press('/')
    expect(heard).not.toHaveBeenCalled()
    press('?')
    await nextTick()
    expect(wrapper.get('.ks-stub').attributes('data-visible')).toBe('false')

    window.removeEventListener(FOCUS_GLOBAL_SEARCH_EVENT, heard)
    wrapper.unmount()
  })

  // 改前 F11 的产品症状：EP 收起态在 body 里留一个 display:none 的 .el-overlay，
  // 存在性守卫把它当成"弹层开着"，于是全站快捷键集体失灵。
  it('常驻的隐藏 .el-overlay 在场时 Ctrl+K 与 / 照常响应', async () => {
    const heard = vi.fn()
    window.addEventListener(FOCUS_GLOBAL_SEARCH_EVENT, heard)
    const wrapper = mountShell()
    await flushPromises()

    putOverlay('el-overlay el-modal-dialog', false)

    press('k', { ctrlKey: true })
    press('/')
    expect(heard).toHaveBeenCalledTimes(2)

    window.removeEventListener(FOCUS_GLOBAL_SEARCH_EVENT, heard)
    wrapper.unmount()
  })

  // 组件不再自读 auth store，身份全靠壳层喂；这两个 prop 少传一个就是静默退化
  it('壳层把 userId 与 role 交给引导组件', async () => {
    const wrapper = mountShell()
    await flushPromises()
    expect(mocks.guideProps).toEqual([{ userId: 'u-1', role: 'admin' }])
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
