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
//
// ⚠️ Task 8 起 Modal 走 `<Teleport to="body">`：遮罩子树**不在** wrapper 的元素树里，
// `wrapper.find/get('.modal')` 一律取不到，所以本文件的查询全走 document.body。
// 「节点必须存在」由此前的 `get`（缺即抛）改写成 `expect(el).not.toBeNull()`——
// 语义等价（缺节点就红），只是报错更直白；「不存在」用 `toBeNull()`。
// 这只改「怎么取节点」，一条断言的**判定对象**都没动，特别是那三条尺寸档：
// 它们仍断言 `.modal` 上渲染出的 class 列表，绝不许换成 `wrapper.props('wide')`
//（prop 断言在模板读错源、13 个调用点全塌回 640px 的情况下照样绿，等于自己拆网）。

/** 卸载夹具挂在**文件级**，不套在任何 describe 内：登记表 `registry` 是模块作用域的，
 *  把它放进某个 describe 只对该块生效，同文件的第二个顶层 describe 就会把僵尸 `.overlay`
 *  和残留 keydown 监听原样留给后面的用例（Task 8 评审 Step 3b-3 的 load-bearing 项）。 */
afterEach(unmountTracked)

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

/** Teleport 后要在 body 子树里触发事件：原生 dispatch 即可，Vue 的监听器就挂在元素上。
 *  bubbles 必须为 true——「按内容区不该关」那条靠的就是事件冒泡到 .overlay 后被 `.self` 挡掉。 */
function fire(el: Element | null, type: 'click' | 'mousedown'): void {
  expect(el, `Teleport 后 body 里找不到 ${type} 目标节点`).not.toBeNull()
  el!.dispatchEvent(new MouseEvent(type, { bubbles: true }))
}

describe('Modal 现状兼容钉', () => {
  it('默认宽度 640px 档（无修饰 prop）', () => {
    factory()
    const el = document.body.querySelector('.modal')
    expect(el).not.toBeNull()
    expect(el!.classList.contains('wide')).toBe(false)
    expect(el!.classList.contains('narrow')).toBe(false)
    expect(el!.classList.contains('xwide')).toBe(false)
  })

  it.each(['wide', 'xwide', 'narrow'])('%s 走属性通道落到 .modal 的 class 上', async (size) => {
    // 仍用 attrs 传：这跟线上 `<Modal wide>` 的编译结果等价——VTU 把 attrs 与 props 合并进
    // 同一个 vnode props 对象（vue-test-utils.cjs.js:8278-8300），所以测的是生产通道。
    // Task 8 起 Modal.vue **声明**了这三个 props，模板改读 props.wide（sizeClass computed）；
    // 若有人只改声明不改读取，这里拿到的 class 列表就会缺该档 ⇒ 本条红。
    const wrapper = factory({}, {}, { [size]: '' })
    await wrapper.vm.$nextTick()
    expect(document.body.querySelector('.modal')?.classList.contains(size)).toBe(true)
  })

  it('点击关闭按钮 emit close', () => {
    const wrapper = factory()
    fire(document.body.querySelector('.modal-head .t-icon-btn'), 'click')
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

  it('按下遮罩本体 emit close；按下内容区不 emit', () => {
    // jsdom 下把 mousedown 直接 dispatch 到元素本体，令 e.target === 该元素，故 .self 守卫在遮罩上成立。
    // 卸载点：这条要 emit() 计数，而 unmount() 会 removeEventHistory（vue-test-utils.cjs.js:7863）、
    // emit() 起手又对已卸载实例早退（runtime-core.cjs.js:4484）——所以必须先取完证据再摘。
    const content = factory()
    fire(document.body.querySelector('.modal'), 'mousedown')
    expect(content.emitted('close')).toBeUndefined()
    content.unmount()

    const overlay = factory()
    fire(document.body.querySelector('.overlay'), 'mousedown')
    expect(overlay.emitted('close')).toHaveLength(1)
    overlay.unmount()
  })

  it('title 插槽渲染、sub 无则不渲染、footer 无则不渲染', () => {
    factory()
    expect(document.body.querySelector('.modal-title')?.textContent).toBe('标题')
    expect(document.body.querySelector('.modal-sub')).toBeNull()
    expect(document.body.querySelector('.modal-foot')).toBeNull()
  })

  it('foot 与 sub 提供时渲染', () => {
    factory({}, { sub: '副标', footer: '<button>确定</button>' })
    expect(document.body.querySelector('.modal-sub')?.textContent).toBe('副标')
    expect(document.body.querySelector('.modal-foot button')?.textContent).toBe('确定')
  })

  it('卸载必须摘掉挂载期注册的 document keydown 监听（后续 Esc 不能打到僵尸实例）', async () => {
    // 观察点落在监听器注册表，而不是 emitted 计数。实测（见 task-6-report §2 D-1）：
    // VTU 的 unmount() 会 removeEventHistory（vue-test-utils.cjs.js:7863），而 Vue 的
    // emit() 起手就是 `if (instance.isUnmounted) return`（runtime-core.cjs.js:4484）——
    // 所以「卸载后 dispatch Esc、断言 emitted close 不增长」无论 removeEventListener 有没
    // 被调用都会绿，它钉不住任何东西；而调用点全靠 v-if 反复开关实例，残留 handler 是真实
    // 的内存/幽灵回调来源。故把观察点钉在注册表本身。
    //
    // 拦截用 vi.spyOn + mockRestore。这里**不援引任何「仓库惯例」**（Task 8 评审 Step 3b-2：
    // 全仓 grep `spyOn(document|window, ['"]addEventListener` 只有本文件这一处，先例并不存在；
    // AppShell.shortcuts.test.ts 的 window 那条是普通监听不是 spy，useTheme.test.ts 的 :24-27
    // 反倒是「手改全局且不还原」的例子，只能当反面）。选 spyOn 的理由只讲机制：
    // `addEventListener` 是从 EventTarget.prototype 继承来的，不是 document 的 own 属性，
    // tinyspy 的 restore 走 Reflect.deleteProperty（tinyspy/dist/index.js:135-137），
    // 把 spy 留下的 own 属性整个删掉、恢复成继承来的原型方法；手工赋值则会在 document 上
    // 永久留下一个 own 属性。
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

    // 两个 spy 的安装**都在 try 之内**（Step 3b-2b）：否则两次 spyOn 之间抛异常会让
    // addEventListener 在本文件后续用例里保持被 mock。实际不可达，但不值得留这个形状。
    let addSpy: ReturnType<typeof vi.spyOn> | undefined
    let removeSpy: ReturnType<typeof vi.spyOn> | undefined
    try {
      addSpy = vi
        .spyOn(document, 'addEventListener')
        .mockImplementation((type, listener, options) => {
          added.push([type, listener as EventListener, captureOf(options)])
          rawAdd(type, listener as EventListener, options)
        })
      removeSpy = vi
        .spyOn(document, 'removeEventListener')
        .mockImplementation((type, listener, options) => {
          removed.push([type, listener as EventListener, captureOf(options)])
          rawRemove(type, listener as EventListener, options)
        })

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
      addSpy?.mockRestore()
      removeSpy?.mockRestore()
    }
  })
})
