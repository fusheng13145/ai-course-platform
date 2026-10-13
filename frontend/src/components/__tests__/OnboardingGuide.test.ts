import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'

/**
 * OnboardingGuide 单元测试
 * <p>聚焦三条契约：</p>
 * <ol>
 *   <li>「已看过」标记**按账号**记录：同账号已看过不再自动弹，换账号登录必须重新看到；</li>
 *   <li>已看过（或经补看入口主动唤起）时，键盘（Esc/←/→）仍然可用——原实现把监听器注册写在
 *       done 判断之后，done 分支直接 return，导致重开的引导只剩鼠标可操作；</li>
 *   <li>主动唤起总是回到第 1 步。</li>
 * </ol>
 * <p>组件 Teleport 到 body，断言一律走 document 查询，不用 wrapper.html()。
 * 宿主用 render 函数而非 template 字符串，避免依赖运行时模板编译器。</p>
 */

// 组件按账号取键，需读 auth store；这里用可变 userId 替身模拟「换账号」
const mocks = vi.hoisted(() => ({ userId: 7 }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ userId: mocks.userId }) }))

const OnboardingGuide = (await import('../OnboardingGuide.vue')).default

/** done 标记按账号键：`<前缀>:<userId>`（与组件实现保持一致） */
const keyFor = (id: number) => `ai-course:onboarding:done:v1:${id}`
const STORAGE_KEY = keyFor(7)

type GuideApi = { show: () => void }

function card() {
  return document.body.querySelector('.ob-card')
}

function countLabel() {
  return document.body.querySelector('.ob-count')?.textContent?.trim()
}

/** 宿主：用组件 ref 复现 AppShell 的调用方式 */
async function mountHost() {
  const guide = ref<GuideApi | null>(null)
  const Host = defineComponent({ setup: () => () => h(OnboardingGuide, { ref: guide }) })
  const wrapper = mount(Host, { attachTo: document.body })
  await flushPromises()
  return { wrapper, guide }
}

/** 向 window 派发按键（组件监听的是 window，不是 document） */
function press(key: string) {
  window.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true }))
}

describe('OnboardingGuide 重开入口', () => {
  beforeEach(() => {
    mocks.userId = 7
    localStorage.clear()
    document.body.replaceChildren()
    // 只 fake setTimeout：600ms 自动弹出是本组件唯一用到的定时器
    vi.useFakeTimers({ toFake: ['setTimeout'] })
  })

  afterEach(() => {
    vi.useRealTimers()
    localStorage.clear()
    document.body.replaceChildren()
  })

  it('按账号记录：换账号登录必须重新看到引导', async () => {
    localStorage.setItem(keyFor(7), '1') // 账号 7 已看过
    mocks.userId = 8 // 同一浏览器换成账号 8

    const { wrapper } = await mountHost()
    vi.advanceTimersByTime(600)
    await flushPromises()

    expect(card(), '新账号不应被旧账号的标记压掉').not.toBeNull()
    expect(countLabel()).toBe('1 / 5')
    // 旧账号的标记不受影响（未互相覆盖）
    expect(localStorage.getItem(keyFor(7))).toBe('1')
    wrapper.unmount()
  })

  it('未看过：挂载后 600ms 自动弹出（原行为不回归）', async () => {
    const { wrapper } = await mountHost()
    expect(card()).toBeNull()

    vi.advanceTimersByTime(600)
    await flushPromises()
    expect(card()).not.toBeNull()
    expect(countLabel()).toBe('1 / 5')
    wrapper.unmount()
  })

  it('负控制：已看过时挂载不自动弹出', async () => {
    localStorage.setItem(STORAGE_KEY, '1')
    const { wrapper } = await mountHost()
    vi.advanceTimersByTime(5000)
    await flushPromises()
    expect(card()).toBeNull()
    wrapper.unmount()
  })

  it('已看过时仍可通过组件 ref 主动唤起（defineExpose show）', async () => {
    localStorage.setItem(STORAGE_KEY, '1')
    const { wrapper, guide } = await mountHost()
    expect(guide.value?.show, '需 defineExpose({ show }) 供 AppShell 菜单唤起').toBeTypeOf(
      'function',
    )

    guide.value!.show()
    await flushPromises()
    expect(card()).not.toBeNull()
    expect(countLabel()).toBe('1 / 5')
    wrapper.unmount()
  })

  it('重开后 Esc 仍能关闭（done 分支也要注册键盘监听）', async () => {
    localStorage.setItem(STORAGE_KEY, '1')
    const { wrapper, guide } = await mountHost()
    guide.value!.show()
    await flushPromises()
    expect(card()).not.toBeNull()

    press('Escape')
    await flushPromises()
    expect(card()).toBeNull()
    wrapper.unmount()
  })

  it('重开后 → 走到第 2 步；再次重开必须回到第 1 步', async () => {
    localStorage.setItem(STORAGE_KEY, '1')
    const { wrapper, guide } = await mountHost()
    guide.value!.show()
    await flushPromises()

    press('ArrowRight')
    await flushPromises()
    expect(countLabel()).toBe('2 / 5')
    // 侧边栏/顶栏在隔离环境里不存在，定位失败时应回退为居中卡片而不是报错
    expect(card()!.className).toContain('centered')

    guide.value!.show()
    await flushPromises()
    expect(countLabel()).toBe('1 / 5')
    wrapper.unmount()
  })

  it('点遮罩空白处即跳过，并写入 done 标记', async () => {
    const { wrapper } = await mountHost()
    vi.advanceTimersByTime(600)
    await flushPromises()

    const overlay = document.body.querySelector('.ob-overlay') as HTMLElement
    overlay.click()
    await flushPromises()
    expect(card()).toBeNull()
    expect(localStorage.getItem(STORAGE_KEY)).toBe('1')
    wrapper.unmount()
  })

  it('负控制：未弹出时按键不渲染卡片', async () => {
    const { wrapper } = await mountHost()
    press('Escape')
    press('ArrowRight')
    await flushPromises()
    expect(card()).toBeNull()
    wrapper.unmount()
  })
})
