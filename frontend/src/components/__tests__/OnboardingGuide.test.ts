import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h, ref } from 'vue'

/**
 * OnboardingGuide 单元测试
 * <p>聚焦「重新查看引导」入口的两条契约：</p>
 * <ol>
 *   <li>已看过（localStorage 有 done 标记）时不再自动弹出，但必须能被外部经组件 ref 主动唤起；</li>
 *   <li>主动唤起后键盘（Esc/←/→）仍然可用——原实现把监听器注册写在 done 判断之后，done 分支直接
 *       return，导致重开的引导只剩鼠标可操作。</li>
 * </ol>
 * <p>组件 Teleport 到 body，断言一律走 document 查询，不用 wrapper.html()。
 * 宿主用 render 函数而非 template 字符串，避免依赖运行时模板编译器。</p>
 */

const OnboardingGuide = (await import('../OnboardingGuide.vue')).default

const STORAGE_KEY = 'ai-course:onboarding:done:v1'

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
