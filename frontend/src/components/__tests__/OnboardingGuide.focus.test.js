import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createRouter, createWebHistory } from 'vue-router'
import OnboardingGuide from '@/components/OnboardingGuide.vue'
import { readState, writeStatus } from '@/onboarding/state'
import { DIALOG_FOCUSABLE_SELECTOR } from '@/composables/useDialogFocus'

/**
 * 出口语义与焦点陷阱接线。
 * <p>改前四条出口（跳过／Esc／点遮罩／开始使用）往同一个键写 '1'，"完成"与"中途 Esc"
 * 在数据上是同一件事；焦点更是一次不响——组件根本没接 useFocusTrap。</p>
 * <p>定时器口径沿用旧 OnboardingGuide.test.ts：只 fake setTimeout，用来按住 onMounted 的
 * 600ms 自动弹；每条测试末尾 unmount，否则上一个实例的定时器会在下一条里把面板又打开。
 * 探针（锚点元素）全部放进 beforeEach 的 body HTML——不许用 `document.body.innerHTML +=`，
 * 那会整块重建 body、把 teleport 中的卡片一起换掉，"卡片已消失"就变成必然。
 * 断言一律走 document 查询（组件 Teleport 到 body）。</p>
 */

const USER = 4
const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', component: { template: '<div/>' } },
    { path: '/student/dashboard', component: { template: '<div/>' } },
    { path: '/:any(.*)', component: { template: '<div/>' } },
  ],
})

window.HTMLElement.prototype.scrollIntoView =
  window.HTMLElement.prototype.scrollIntoView || (() => {})

const open = async () => {
  const wrapper = mount(OnboardingGuide, {
    attachTo: document.body,
    props: { userId: USER, role: 'student' },
    global: { plugins: [createPinia(), router] },
  })
  wrapper.vm.show()
  await wrapper.vm.$nextTick()
  await wrapper.vm.$nextTick()
  return wrapper
}

const frames = (n) =>
  new Promise((resolve) => {
    const step = (left) => (left <= 0 ? resolve() : requestAnimationFrame(() => step(left - 1)))
    step(n)
  })

describe('OnboardingGuide 出口语义与焦点', () => {
  beforeEach(() => {
    localStorage.clear()
    // student 五步用到的四个锚点全部在场：这样"解析不到锚点"只可能发生在故意摘掉它们的那条测试里
    document.body.innerHTML =
      '<div data-onboard="side-user"></div>' +
      '<div data-onboard="nav:ai-assistant"></div>' +
      '<div data-onboard="nav:graph"></div>' +
      '<div data-onboard="join-course" id="join"></div>'
    vi.spyOn(console, 'warn').mockImplementation(() => {})
  })

  afterEach(() => {
    // 还原 console（并顺带清掉 spy 历史）：anchor-miss 那条只许命中自己这一条测试的 warn
    vi.restoreAllMocks()
    localStorage.clear()
    document.body.innerHTML = ''
  })

  it('Tab 连续 12 次，焦点始终留在卡内（改前第 3 次就逃到 BODY）', async () => {
    const wrapper = await open()
    const card = document.querySelector('.ob-card')
    card.focus()
    const visited = new Set()
    for (let i = 0; i < 12; i++) {
      wrapper.vm.__trapStep()
      await wrapper.vm.$nextTick()
      const a = document.activeElement
      visited.add(a)
      expect(
        card === a || card.contains(a),
        `第 ${i + 1} 次 Tab 逃到 ${a?.tagName}.${a?.className}`,
      ).toBe(true)
    }
    // 正读数：只钉"没逃出去"会绿在"焦点一动不动"上——钩子被摘掉时同样成立。
    // 陷阱真的在环里走，必须至少走过 2 个不同元素。
    expect(
      visited.size,
      `12 步只走过 ${[...visited].map((el) => el.className || el.tagName)}`,
    ).toBeGreaterThanOrEqual(2)
    wrapper.unmount()
  })

  it('Esc ⇒ paused@当前步，不写 done（改前 Esc 就是永久完成）', async () => {
    const wrapper = await open()
    await wrapper.vm.__goto(2)
    wrapper.vm.__key({ key: 'Escape' })
    expect(readState(USER)).toEqual({ status: 'paused', atStep: 2 })
    wrapper.unmount()
  })

  it('点洞外压暗块 ⇒ paused，不写 done', async () => {
    const wrapper = await open()
    await wrapper.vm.__goto(1)
    await wrapper.vm.__scrimClick()
    expect(readState(USER)).toEqual({ status: 'paused', atStep: 1 })
    wrapper.unmount()
  })

  it('中间步骤点洞内真元素 ⇒ 前进、不写任何终态、不导航', async () => {
    const wrapper = await open()
    const push = vi.spyOn(router, 'push')
    await wrapper.vm.__goto(1)
    await wrapper.vm.__anchorClick({ preventDefault() {}, stopPropagation() {} })
    await flushPromises()
    expect(wrapper.vm.getIndex()).toBe(2)
    expect(readState(USER).status).toBe('idle')
    expect(push).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('真点洞内的锚点元素 ⇒ 同样被引导消费（真实面，不是钩子面）', async () => {
    const wrapper = await open()
    const push = vi.spyOn(router, 'push')
    await wrapper.vm.__goto(1)
    // 走 document 捕获监听：只测 __anchorClick 的话，把监听摘掉测试照样绿＝空壳判据
    const anchor = document.querySelector('[data-onboard="nav:ai-assistant"]')
    anchor.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }))
    await flushPromises()
    expect(wrapper.vm.getIndex()).toBe(2)
    expect(readState(USER).status).toBe('idle')
    expect(push, '中间步骤点洞内不得导航').not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('激活动作步点它 ⇒ 真执行、写 done、组件关闭', async () => {
    const wrapper = await open()
    await wrapper.vm.__goto(4)
    const el = document.getElementById('join')
    const clicked = vi.spyOn(el, 'click')
    // 点之前卡片必须在场：否则后面那条"卡片没了"是必然绿
    expect(document.querySelector('.ob-card')).not.toBeNull()
    await wrapper.vm.__anchorClick({ preventDefault() {}, stopPropagation() {} })
    await flushPromises()
    expect(clicked, 'student 端末步是 click-anchor，必须真的点到那个卡').toHaveBeenCalledTimes(1)
    expect(readState(USER).status).toBe('done')
    expect(document.querySelector('.ob-card')).toBeNull()
    wrapper.unmount()
  })

  it('「稍后再看」写 paused@n；resume() 从第 n 步续', async () => {
    const wrapper = await open()
    await wrapper.vm.__goto(3)
    // 卡片 Teleport 到 body，wrapper.get 只看组件自身 DOM（那里只剩 teleport 注释）⇒ 走 document 查询
    const later = document.querySelector('button[data-exit="paused"]')
    expect(later, '「稍后再看」是本次新增的第四个出口，按钮必须在场').not.toBeNull()
    later.click()
    await flushPromises()
    expect(readState(USER)).toEqual({ status: 'paused', atStep: 3 })
    wrapper.unmount()

    const second = await open()
    second.vm.resume()
    await second.vm.$nextTick()
    expect(second.vm.getIndex()).toBe(3)
    second.unmount()
  })

  it('「跳过」写 skipped（与 done 在数据上可区分）', async () => {
    const wrapper = await open()
    await wrapper.vm.__goto(1)
    // e2e 夹具 dismissOnboarding 靠「跳过」这个角色名关浮层：按钮名与位置都不许动
    const skip = document.querySelector('button[data-exit="skipped"]')
    expect(skip, '「跳过」不许删（e2e 与视觉流程都靠它关浮层）').not.toBeNull()
    skip.click()
    await flushPromises()
    expect(readState(USER)).toEqual({ status: 'skipped', atStep: 1 })
    wrapper.unmount()
  })

  it('anchor-miss 留痕且不阻塞：解析不到锚点时 warn 且卡片仍在', async () => {
    const wrapper = await open()
    await wrapper.vm.__goto(1)
    // 只摘锚点节点，不清 body——清 body 会把挂载中的卡片一起删掉，测的就不是"降级"而是"消失"
    document.querySelectorAll('[data-onboard]').forEach((el) => el.remove())
    await wrapper.vm.__relocate()
    expect(console.warn).toHaveBeenCalledWith(expect.stringContaining('anchor-miss'))
    expect(document.querySelector('.ob-card')).not.toBeNull()
    wrapper.unmount()
  })

  it('跨角色 resume：paused@3 记的是别的角色时从第 1 步起，不照着别人的锚点跳', async () => {
    writeStatus(USER, 'paused', 3, 'admin') // 存一份 admin 的 paused@3
    const wrapper = mount(OnboardingGuide, {
      attachTo: document.body,
      props: { userId: USER, role: 'student' }, // 换成 student 挂载
      global: { plugins: [createPinia(), router] },
    })
    wrapper.vm.resume()
    await wrapper.vm.$nextTick()
    // 三端都是 5 步，Math.min 会把 3 夹住不报错，但锚点与文案是 admin 的 ⇒ 必须回第 1 步
    expect(wrapper.vm.getIndex()).toBe(0)
    wrapper.unmount()
  })

  it('Shift+Tab 回绕（反向半边）：卡内首元素上 Shift+Tab ⇒ 回绕到尾且不逃逸', async () => {
    const wrapper = await open()
    const card = document.querySelector('.ob-card')
    const items = Array.from(card.querySelectorAll(DIALOG_FOCUSABLE_SELECTOR))
    const first = items[0]
    const last = items[items.length - 1]
    first.focus()
    // 向 document 派发真实 Shift+Tab：读组件级陷阱的回绕语义（共享层的正向已有用例，这里不重复）
    const evt = new KeyboardEvent('keydown', {
      key: 'Tab',
      shiftKey: true,
      bubbles: true,
      cancelable: true,
    })
    document.dispatchEvent(evt)
    await wrapper.vm.$nextTick()
    expect(evt.defaultPrevented, '首元素 Shift+Tab 必须被陷阱拦下').toBe(true)
    expect(card.contains(document.activeElement), 'Shift+Tab 后焦点不得逃出卡内').toBe(true)
    expect(document.activeElement, '首元素的 Shift+Tab 回绕到尾元素').toBe(last)
    wrapper.unmount()
  })

  it('捕获监听随可见性装卸：开边沿挂一个、关边沿摘回，关掉后再点锚点不前进', async () => {
    // 探针：证明"关边沿真摘了"必须看监听账——行为面上 onDocumentClick 有 visible 闸，摘不摘都不前进
    const added = []
    const removed = []
    const rawAdd = document.addEventListener.bind(document)
    const rawRemove = document.removeEventListener.bind(document)
    const capOf = (o) => (typeof o === 'boolean' ? o : !!o?.capture)
    const addSpy = vi.spyOn(document, 'addEventListener').mockImplementation((t, fn, o) => {
      added.push([t, fn, capOf(o)])
      rawAdd(t, fn, o)
    })
    const removeSpy = vi.spyOn(document, 'removeEventListener').mockImplementation((t, fn, o) => {
      removed.push([t, fn, capOf(o)])
      rawRemove(t, fn, o)
    })

    const wrapper = mount(OnboardingGuide, {
      attachTo: document.body,
      props: { userId: USER, role: 'student' },
      global: { plugins: [createPinia(), router] },
    })
    wrapper.vm.show()
    await wrapper.vm.$nextTick()
    await wrapper.vm.$nextTick()
    const clickAdds = added.filter(([t, , cap]) => t === 'click' && cap === true)
    expect(clickAdds, '开边沿须在 document 上挂恰好一个捕获 click').toHaveLength(1)

    await wrapper.vm.__goto(1)
    document.querySelector('button[data-exit="skipped"]').click()
    await flushPromises()
    const leaks = clickAdds.filter(
      ([t, fn, cap]) => !removed.some(([rt, rf, rc]) => rt === t && rf === fn && rc === cap),
    )
    expect(leaks, '关边沿必须把捕获 click 摘回：关闭态不留 document 级监听').toEqual([])

    const before = wrapper.vm.getIndex()
    document
      .querySelector('[data-onboard="nav:ai-assistant"]')
      .dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }))
    await flushPromises()
    expect(wrapper.vm.getIndex(), '关掉后再点锚点不得前进').toBe(before)

    wrapper.unmount()
    addSpy.mockRestore()
    removeSpy.mockRestore()
  })

  it('show() 后切到带锚点那一步、随即卸载 ⇒ 卸载后帧链与 follow 都停住', async () => {
    const wrapper = await open()
    const scrolled = vi.spyOn(window.HTMLElement.prototype, 'scrollIntoView')
    wrapper.vm.__goto(1)
    wrapper.unmount()
    await frames(3)
    expect(document.querySelectorAll('.ob-scrim').length, '卸载后不得再有后续写入').toBe(0)
    expect(scrolled, '卸载后不得再对锚点调 follow()').not.toHaveBeenCalled()
  })
})
