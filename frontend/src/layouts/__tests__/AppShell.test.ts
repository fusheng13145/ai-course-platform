import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * AppShell 用户菜单单元测试
 * <p>验证「新手引导」入口：侧栏底部用户区与顶栏头像渲染同一份 items，点它要能接着看完；
 * 同时原有两项（个人中心 / 退出登录）行为不回归。</p>
 * <p>浮层不再是动态 DOM 挂 body，而是壳层里的 v-if 组件；断言仍走 document 查询，
 * 这样"关闭后真从文档里消失"也一并被读到。afterEach 清 body，避免跨用例残留。</p>
 */

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  logout: vi.fn(),
  guideShow: vi.fn(),
  guideResume: vi.fn(),
  guideProps: [] as Record<string, unknown>[],
  route: { path: '/admin/dashboard', matched: [] as unknown[] },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
  useRoute: () => mocks.route,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ logout: mocks.logout, userId: 'u-1' }),
}))

// 侧边栏只作为「用户区点击」的事件来源，导航/接口逻辑不在本用例目标内；
// 类名带上 side-user，壳层才能按真实的触发物取矩形
vi.mock('../components/AppSidebar.vue', () => ({
  default: {
    name: 'AppSidebar',
    props: { role: { type: String }, sidebarMini: { type: Boolean } },
    emits: ['user-menu'],
    template:
      '<button class="user-menu-btn side-user" @click="$emit(\'user-menu\')">用户区</button>',
  },
}))

vi.mock('../components/AppTopbar.vue', () => ({
  default: { name: 'AppTopbar', template: '<div class="topbar-stub"></div>' },
}))

// 用暴露 show()/resume() 的替身复现 OnboardingGuide 的对外契约，并记下壳层喂进来的身份
vi.mock('@/components/OnboardingGuide.vue', () => ({
  default: {
    name: 'OnboardingGuide',
    props: { userId: { type: [Number, String], default: null }, role: { type: String } },
    setup(props: Record<string, unknown>, { expose }: { expose: (obj: unknown) => void }) {
      mocks.guideProps.push({ ...props })
      expose({ show: mocks.guideShow, resume: mocks.guideResume })
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
    global: { stubs: { 'router-view': true, KeyboardShortcutsDialog: true } },
  })
  await flushPromises()
  return wrapper
}

/** 点击用户区展开浮层；浮层由壳层的 v-if 控制，故可反复调用 */
async function openMenu(wrapper: Awaited<ReturnType<typeof mountShell>>) {
  await wrapper.find('.user-menu-btn').trigger('click')
}

function menuEl(): HTMLElement | null {
  return document.body.querySelector('.pop-menu') as HTMLElement | null
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
    mocks.guideProps.length = 0
    document.body.replaceChildren()
  })

  afterEach(() => {
    document.body.replaceChildren()
  })

  it('菜单含「新手引导」，点击后从暂停处续看引导组件', async () => {
    const wrapper = await mountShell()
    await openMenu(wrapper)
    expect(menuItems().map((el) => el.textContent?.trim())).toEqual([
      '个人中心',
      '新手引导',
      '退出登录',
    ])

    itemByLabel('新手引导')!.click()
    await flushPromises()
    expect(mocks.guideResume).toHaveBeenCalledTimes(1)
    // paused 用户要能续，所以走 resume 而不是从头 show
    expect(mocks.guideShow).not.toHaveBeenCalled()
    // 点击任一项都应先关闭浮层
    expect(menuEl()).toBeNull()
    wrapper.unmount()
  })

  it('负控制：个人中心/退出登录不会唤起引导，原有跳转不回归', async () => {
    mocks.logout.mockResolvedValue(undefined)
    const wrapper = await mountShell()

    await openMenu(wrapper)
    itemByLabel('个人中心')!.click()
    expect(mocks.push).toHaveBeenCalledWith('/admin/profile')
    expect(mocks.guideResume).not.toHaveBeenCalled()

    await openMenu(wrapper)
    itemByLabel('退出登录')!.click()
    expect(mocks.logout).toHaveBeenCalledTimes(1)
    expect(mocks.guideResume).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('壳层喂给引导组件的身份含 userId 与 role', async () => {
    const wrapper = await mountShell()
    expect(mocks.guideProps).toEqual([{ userId: 'u-1', role: 'admin' }])
    wrapper.unmount()
  })

  it('点菜单外面 ⇒ 浮层收起（closed 由壳层接住）', async () => {
    const wrapper = await mountShell()
    await openMenu(wrapper)
    expect(menuEl()).toBeTruthy()

    document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }))
    await flushPromises()
    expect(menuEl()).toBeNull()
    wrapper.unmount()
  })

  it('浮层跟随触发物：触发物在下半屏时向上开，且不再写死 right', async () => {
    const wrapper = await mountShell()
    const trigger = document.querySelector('.side-user') as HTMLElement
    trigger.getBoundingClientRect = () =>
      ({
        width: 220,
        height: 58,
        top: 834,
        left: 0,
        right: 220,
        bottom: 892,
        x: 0,
        y: 0,
        toJSON: () => ({}),
      }) as DOMRect

    await openMenu(wrapper)
    const style = menuEl()!.getAttribute('style') ?? ''
    expect(Number(/top:\s*(-?\d+)px/.exec(style)?.[1])).toBeLessThan(834)
    expect(style).not.toMatch(/right/)
    wrapper.unmount()
  })

  // 事件名按字面量钉住：源里改名而这条仍绿的话，顶栏那条重开路径就断了没人知道
  it('顶栏用户区的重开请求经窗口事件送到同一个引导实例', async () => {
    const wrapper = await mountShell()
    window.dispatchEvent(new Event('ac:reopen-onboarding'))
    expect(mocks.guideResume).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  /**
   * 上一条只钉了监听半边。这条从真实触发物走完整链路：顶栏头像（真 AtbUserMenu）
   * 展开菜单 → 点「新手引导」→ 派发重开事件 → 壳层监听把请求送到唯一的引导 ref。
   * 顶栏替身只负责把真实用户区组件挂进来，导航与接口逻辑仍不在本用例目标内。
   */
  it('链路：点顶栏头像菜单里的「新手引导」，续看走 resume 不从头 show', async () => {
    const { default: AtbUserMenu } = await import('../components/topbar/AtbUserMenu.vue')
    const wrapper = mount(AppShell, {
      props: { role: 'ADMIN', profilePath: '/admin/profile' },
      attachTo: document.body,
      global: {
        stubs: {
          'router-view': true,
          KeyboardShortcutsDialog: true,
          AppTopbar: {
            name: 'AppTopbar',
            props: {
              role: { type: String },
              notificationPath: { type: String },
              profilePath: { type: String },
            },
            components: { AtbUserMenu },
            template:
              '<div class="topbar-stub"><AtbUserMenu :role="role" :profile-path="profilePath" /></div>',
          },
        },
      },
    })
    await flushPromises()

    // 顶栏确实挂的是真实用户区（替身没生效时这条就是空转，先把它钉住）
    const avatar = wrapper.get('.user-btn')
    await avatar.trigger('click')
    await flushPromises()
    expect(menuItems().map((el) => el.textContent?.trim())).toEqual([
      '个人中心',
      '新手引导',
      '退出登录',
    ])

    itemByLabel('新手引导')!.click()
    await flushPromises()
    expect(mocks.guideResume).toHaveBeenCalledTimes(1)
    expect(mocks.guideShow).not.toHaveBeenCalled()
    expect(menuEl()).toBeNull()
    wrapper.unmount()
  })
})
