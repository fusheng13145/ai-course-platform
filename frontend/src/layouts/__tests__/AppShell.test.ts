import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * AppShell 用户菜单单元测试
 * <p>验证「新手引导」入口：侧边栏用户区弹出菜单里必须能一键重新唤起 OnboardingGuide，
 * 同时原有两项（个人中心 / 退出登录）行为不回归。</p>
 * <p>pop-menu 由动态 DOM 挂到 document.body，断言走 document 查询； afterEach 清理，避免跨用例残留。</p>
 */

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  logout: vi.fn(),
  guideShow: vi.fn(),
  route: { path: '/admin/dashboard', matched: [] as unknown[] },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
  useRoute: () => mocks.route,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ logout: mocks.logout }),
}))

// 侧边栏只作为「用户区点击」的事件来源，导航/接口逻辑不在本用例目标内
vi.mock('../components/AppSidebar.vue', () => ({
  default: {
    name: 'AppSidebar',
    props: { role: { type: String }, sidebarMini: { type: Boolean } },
    emits: ['user-menu'],
    template: '<button class="user-menu-btn" @click="$emit(\'user-menu\')">用户区</button>',
  },
}))

vi.mock('../components/AppTopbar.vue', () => ({
  default: { name: 'AppTopbar', template: '<div class="topbar-stub"></div>' },
}))

// 用暴露 show() 的替身复现 OnboardingGuide 的对外契约
vi.mock('@/components/OnboardingGuide.vue', () => ({
  default: {
    name: 'OnboardingGuide',
    setup(_props: Record<string, unknown>, { expose }: { expose: (obj: unknown) => void }) {
      expose({ show: mocks.guideShow })
      return () => null
    },
  },
}))

const AppShell = (await import('../AppShell.vue')).default

/** 挂载管理端壳层；侧边栏/顶栏用最小替身，只保留用户菜单事件 */
async function mountShell() {
  const wrapper = mount(AppShell, {
    props: { role: 'ADMIN', profilePath: '/admin/profile' },
    attachTo: document.body,
    global: { stubs: { 'router-view': true } },
  })
  await flushPromises()
  return wrapper
}

/** 点击用户区展开浮层；点击任一项后浮层自删，故可反复调用 */
async function openMenu(wrapper: Awaited<ReturnType<typeof mountShell>>) {
  await wrapper.find('.user-menu-btn').trigger('click')
}

function menuItems(): HTMLElement[] {
  return Array.from(document.body.querySelectorAll('.pop-menu .menu-item')) as HTMLElement[]
}

function itemByLabel(label: string): HTMLElement | undefined {
  return menuItems().find((el) => el.textContent?.includes(label))
}

describe('AppShell 用户菜单', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    document.body.replaceChildren()
  })

  afterEach(() => {
    document.body.replaceChildren()
  })

  it('菜单含「新手引导」，点击后唤起引导组件', async () => {
    const wrapper = await mountShell()
    await openMenu(wrapper)
    expect(menuItems().map((el) => el.textContent?.trim())).toEqual([
      '个人中心',
      '新手引导',
      '退出登录',
    ])

    itemByLabel('新手引导')!.click()
    await flushPromises()
    expect(mocks.guideShow).toHaveBeenCalledTimes(1)
    // 点击任一项都应先关闭浮层
    expect(document.body.querySelector('.pop-menu')).toBeNull()
    wrapper.unmount()
  })

  it('负控制：个人中心/退出登录不会唤起引导，原有跳转不回归', async () => {
    mocks.logout.mockResolvedValue(undefined)
    const wrapper = await mountShell()

    await openMenu(wrapper)
    itemByLabel('个人中心')!.click()
    expect(mocks.push).toHaveBeenCalledWith('/admin/profile')
    expect(mocks.guideShow).not.toHaveBeenCalled()

    await openMenu(wrapper)
    itemByLabel('退出登录')!.click()
    expect(mocks.logout).toHaveBeenCalledTimes(1)
    expect(mocks.guideShow).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
