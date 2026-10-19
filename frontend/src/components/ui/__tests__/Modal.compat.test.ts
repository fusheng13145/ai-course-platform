import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import Modal from '../Modal.vue'
import { track, unmountTracked } from './helpers/trackMount'

// 13 个调用点的实际用法抽成三类：尺寸档、@close、插槽有无。
// 这一组测试是 Phase 4 每批迁移前的「存量不破」门禁：加能力之前先把现状钉死。
// 一旦它们变红，说明改的是不兼容而非纯增强——红的是信号，不是待修的 bug。
//
// 后续任务（Task 7-10）的新能力可另开文件（如 Modal.vmodel.test.ts），但挂载/卸载
// 夹具必须共用 helpers/trackMount，不得复制第二份——全部用例 attachTo document.body，
// 不卸载就会把 .overlay 节点连同 document keydown 监听留成僵尸实例，污染 body 作用域查询。

function factory(
  props: Record<string, unknown> = {},
  slots: Record<string, string> = {},
  attrs: Record<string, string> = {},
) {
  const wrapper = mount(Modal, {
    props,
    attrs,
    slots: { title: '标题', ...slots },
    attachTo: document.body,
  })
  return track(wrapper)
}

describe('Modal 现状兼容钉', () => {
  afterEach(unmountTracked)

  it('默认宽度 640px 档（无修饰 prop）', () => {
    // 用 get 而非 find：get 缺元素即抛，且错误信息带上 DOM，和 EmptyState.test.ts 的既有约定一致。
    // 注意别误信旧稿的说法——find 落空并不会返回空 wrapper 让 classes() 得到 []：它返回
    // createWrapperError 的代理，除 exists/then 外的任何属性读取都会被 get trap 抛错
    // （vue-test-utils.cjs.js:7247-7251 与 :1477-1492）。所以 find 同样能锁住「缺 .modal 即失败」，
    // 只是报错比 get 更晦涩，故统一用 get。
    const w = factory().get('.modal')
    expect(w.classes()).not.toContain('wide')
    expect(w.classes()).not.toContain('narrow')
    expect(w.classes()).not.toContain('xwide')
  })

  it.each(['wide', 'xwide', 'narrow'])('%s 走 $attrs 布尔通道', async (size) => {
    // 用 attrs 而非 props：现状 Modal.vue 是从 $attrs 读尺寸档（模板里 .modal 的 :class 三元判断），
    // 组件并未声明这些 props；用 props 传未声明键在 VTU 下的落点与 attrs 不同。
    const wrapper = factory({}, {}, { [size]: '' })
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.modal').classes()).toContain(size)
  })

  it('点击关闭按钮 emit close', async () => {
    const wrapper = factory()
    await wrapper.find('.modal-head .t-icon-btn').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
  })

  it('Esc emit close', async () => {
    const wrapper = factory()
    await wrapper.vm.$nextTick()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toHaveLength(1)
    wrapper.unmount()
  })

  it('按下遮罩本体 emit close；按下内容区不 emit', async () => {
    // jsdom 下 trigger('mousedown') 令 e.target === 该元素本体，故 .self 守卫在遮罩上成立
    const content = factory()
    await content.find('.modal').trigger('mousedown')
    expect(content.emitted('close')).toBeUndefined()
    content.unmount()

    const overlay = factory()
    await overlay.find('.overlay').trigger('mousedown')
    expect(overlay.emitted('close')).toHaveLength(1)
    overlay.unmount()
  })

  it('title 插槽渲染、sub 无则不渲染、footer 无则不渲染', () => {
    const wrapper = factory()
    expect(wrapper.find('.modal-title').text()).toBe('标题')
    expect(wrapper.find('.modal-sub').exists()).toBe(false)
    expect(wrapper.find('.modal-foot').exists()).toBe(false)
  })

  it('foot 与 sub 提供时渲染', () => {
    const wrapper = factory({}, { sub: '副标', footer: '<button>确定</button>' })
    expect(wrapper.find('.modal-sub').text()).toBe('副标')
    expect(wrapper.find('.modal-foot button').text()).toBe('确定')
  })

  it('卸载必须摘掉挂载期注册的 document keydown 监听（后续 Esc 不能打到僵尸实例）', async () => {
    // 观察点落在监听器注册表，而不是 emitted 计数。实测（见 task-6-report §2 D-1）：
    // VTU 的 unmount() 会 removeEventHistory（vue-test-utils.cjs.js:7863），而 Vue 的
    // emit() 起手就是 `if (instance.isUnmounted) return`（runtime-core.cjs.js:4484）——
    // 所以「卸载后 dispatch Esc、断言 emitted close 不增长」无论 removeEventListener 有没
    // 被调用都会绿，它钉不住任何东西；而调用点全靠 v-if 反复开关实例，残留 handler 是真实
    // 的内存/幽灵回调来源。故把观察点钉在注册表本身。
    //
    // 拦截改用 vi.spyOn + mockRestore（仓内既有习惯：AppShell.shortcuts.test.ts 用 window
    // 事件、useTheme.test.ts / useQuestions.test.ts:586 用 vi.fn / vi.spyOn 而非手改全局）。
    // 相比「把绑定的原函数赋回全局」，mockRestore 会把 document 上的 own 属性整个删掉、还原到
    // EventTarget.prototype，不在 document 上留下 own 属性残迹。
    //
    // 注册记录成 [type, listener, capture] 三元组，并按三元组身份比对：DOM 的移除匹配是
    // (type, callback, capture)——若将来有人 addEventListener('keydown', fn, true) 却用
    // removeEventListener('keydown', fn)（capture 不匹配＝真泄漏），只比函数引用会误判为干净。
    interface RawListeners {
      addEventListener: (type: string, listener: EventListener, options?: unknown) => void
      removeEventListener: (type: string, listener: EventListener, options?: unknown) => void
    }
    // 先绑定原始实现、再装 spy：rawAdd/rawRemove 拿到的是尚未被替换的原型方法，不会自我递归。
    const raw = document as unknown as RawListeners
    const rawAdd = raw.addEventListener.bind(document)
    const rawRemove = raw.removeEventListener.bind(document)
    const captureOf = (o?: boolean | { capture?: boolean }): boolean =>
      typeof o === 'boolean' ? o : !!o?.capture
    type Registration = readonly [type: string, listener: EventListener, capture: boolean]
    const added: Registration[] = []
    const removed: Registration[] = []

    const addSpy = vi
      .spyOn(document, 'addEventListener')
      .mockImplementation((type, listener, options) => {
        added.push([type, listener as EventListener, captureOf(options)])
        rawAdd(type, listener as EventListener, options)
      })
    const removeSpy = vi
      .spyOn(document, 'removeEventListener')
      .mockImplementation((type, listener, options) => {
        removed.push([type, listener as EventListener, captureOf(options)])
        rawRemove(type, listener as EventListener, options)
      })

    try {
      const wrapper = factory()
      await wrapper.vm.$nextTick()
      const keydownAdds = added.filter(([t]) => t === 'keydown')
      // 挂载期确实往 document 挂了 keydown：用 >0 而非 ===1，为 Phase 0b 的焦点管理预留
      //（允许再加别的 keydown，只要卸载时一起摘干净就不算破兼容）。
      // 这条同时也把「至少 1 个 keydown 挂在 document」钉死：现状 Modal.vue 的 `<script setup>`
      // onMounted 块确实挂 document，
      // Esc 用例也已钉住 document 通道；但若 Task 9 像 OnboardingGuide.vue:252 那样合法地把
      // keydown 改挂 window，本行会因 spy 只盯 document 而红——那需要连 spy 目标一起挪到 window
      // 的同步改动，不是兼容回归，别被这盏红灯唬住。
      expect(keydownAdds.length).toBeGreaterThan(0)
      wrapper.unmount()
      const leaked = keydownAdds.filter(
        ([t, fn, cap]) => !removed.some(([rt, rf, rc]) => rt === t && rf === fn && rc === cap),
      )
      expect(leaked).toEqual([])
    } finally {
      addSpy.mockRestore()
      removeSpy.mockRestore()
    }
  })
})
