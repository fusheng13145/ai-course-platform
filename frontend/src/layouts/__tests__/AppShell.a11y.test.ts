import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { reactive } from 'vue'

/**
 * AppShell 无障碍接线（D2-7）
 *
 * <p>①`main` 内容区绑定 `aria-busy`：导航期间标记忙态，读屏不朗读中间态；
 * ②路由切换播报：SPA 无声跳转对读屏不可见，进入新页面后经 useAnnouncer
 * 播报 `meta.title`；③侧边栏 nav landmark 由 AppSidebar 测试锁定。</p>
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

import AppShell from '../AppShell.vue'
import { resetAnnouncerForTest } from '@/composables/useAnnouncer'

function mountShell() {
  return mount(AppShell, {
    props: { role: 'ADMIN', profilePath: '/admin/profile' },
    attachTo: document.body,
    global: { stubs: { 'router-view': true } },
  })
}

describe('AppShell — D2-7 无障碍接线', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    resetAnnouncerForTest()
    // jsdom 未实现 Element.scrollTo：AppShell 既有「路由切换回顶」watcher 依赖它，
    // 不补桩会同步抛出并中断 scheduler flush 队列（后续播报 watcher 不执行）
    if (!Element.prototype.scrollTo) Element.prototype.scrollTo = vi.fn()
    mocks.route = reactive({
      path: '/admin/dashboard',
      fullPath: '/admin/dashboard',
      matched: [{ meta: { title: '管理端工作台' } }],
    })
  })

  it('main 内容区带 aria-busy：导航起 true、导航止 false', async () => {
    // 捕获路由钩子回调（模拟 vue-router 注册行为）
    let beforeCb: (() => void) | undefined
    let afterCb: (() => void) | undefined
    mocks.beforeEach.mockImplementation((cb: () => void) => {
      beforeCb = cb
      return () => {}
    })
    mocks.afterEach.mockImplementation((cb: () => void) => {
      afterCb = cb
      return () => {}
    })

    const wrapper = mountShell()
    await flushPromises()

    const main = wrapper.get('main.content')
    expect(main.attributes('aria-busy')).toBe('false')

    beforeCb?.()
    await flushPromises()
    expect(wrapper.get('main.content').attributes('aria-busy')).toBe('true')

    afterCb?.()
    await flushPromises()
    expect(wrapper.get('main.content').attributes('aria-busy')).toBe('false')
    wrapper.unmount()
  })

  it('路由切换播报：进入带 meta.title 的页面后 live 区域出现标题', async () => {
    const wrapper = mountShell()
    await flushPromises()

    const announcer = () => document.getElementById('a11y-announcer')
    expect(announcer()?.textContent).toBe('已进入管理端工作台页面')

    // 模拟路由切换（reactive route 变更触发 watch）
    mocks.route.path = '/admin/users'
    mocks.route.fullPath = '/admin/users'
    mocks.route.matched = [{ meta: { title: '用户管理' } }]
    await flushPromises()

    expect(announcer()?.textContent).toBe('已进入用户管理页面')
    wrapper.unmount()
  })

  it('路由无 meta.title：不播报（live 区域保持空）', async () => {
    mocks.route.matched = [{ meta: {} }]
    const wrapper = mountShell()
    await flushPromises()
    expect(document.getElementById('a11y-announcer')).toBeNull()
    wrapper.unmount()
  })
})
