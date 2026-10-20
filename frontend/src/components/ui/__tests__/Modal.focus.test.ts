/* eslint-disable vue/one-component-per-file --
   本文件的每条前提用例都必须自带探针宿主（PanelRefProbe、ExposeProbe 与给 Modal 挂模板 ref 的 Host）：
   要测的就是「模板 ref 在 Teleport 根之下究竟拿到什么」这件事本身，而 Modal.vue 是被测对象、
   不能顺手改成夹具。与 Modal.teleport.test.ts 同一处置、同一条理由（该规则针对产品组件）。 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Teleport, computed, defineComponent, h, nextTick, ref } from 'vue'
import type { Ref } from 'vue'
import { mount } from '@vue/test-utils'
import Modal from '../Modal.vue'
import { DIALOG_FOCUSABLE_SELECTOR } from '@/composables/useDialogFocus'
import { track, unmountTracked } from './helpers/trackMount'

/**
 * Task 9：Modal 的焦点接入（走 `useDialogFocus` 原语，不改原语）。
 * ==============================================================
 * 文件里三块，按任务顺序：
 * 1. **可达性前提（Step 1）**——把台账 `P0b-MODAL-02` 记给 Task 9 的两条未入库读数
 *    （③ 后半句、④）接成入库断言，并补上该行没碰过的第三条通道：调用方模板 ref 读到的 `$el`。
 *    这两条在 `Modal.vue` 未改时入库（先量再写），对应 commit 8218ee8。
 * 2. **P-c：`defineExpose({ $el })` 的解包层**——钉在**探针组件**上而不是 Modal 上，
 *    因为 Modal 本体最终没写 defineExpose（理由见第 3 块与 Modal.vue 里那段 ⚠️ 注释：
 *    shadow 内置 `$el` 会连 VTU 的 `wrapper.element` 一起改掉，实测 18 条既有断言转红，
 *    而那批文件在本次改动许可之外）。机制是库的，钉在探针上同样可重放、同样可翻红。
 * 3. **焦点行为（Step 4）**——三条各带"怎么把它跑红"的用例。
 *
 * 编号按控制器口径 P-a / P-b / P-c，不用 brief 的 ①/②——那两个符号与 `P0b-MODAL-02` 的 ③/④ 撞名。
 *
 * 与 Modal.vmodel.test.ts 的分工（不重述已入库事实）：那份钉的是 **VTU 的 `wrapper.element`**
 * 通道（`nodeType` 8、`data` 'teleport start'，开与关两态都是那条锚点注释）。
 * 本文件钉的是它没有三条通道——组件**内部**的对象 ref、**调用方**的模板 ref、以及
 * expose 之后的这两条各变成什么。
 *
 * 挂载/卸载走 helpers/trackMount（族内规矩：不得复制第二份夹具）。全部 attach 到 body，
 * 且 afterEach 再扫一遍残留节点——本文件的断言全是 body 作用域查询，僵尸 `.overlay`
 * 会让 `document.querySelector` 命中上一条用例的节点（与 Drawer.test.ts 的 afterEach 同一处置）。
 */

/** 探针组件内部那个对象 ref 的**引用本身**（不是它的公开实例），便于直读 `rootRef.value` */
let probeRootRef: Ref<HTMLElement | null> | null = null

/**
 * P-a 探针：复刻 Modal 的根形状（模板根是 `<Teleport to="body">`，里面一块面板），
 * 把 ref 绑在面板节点上，量「Teleport 之内的模板 ref 到底拿到什么」。
 * 用**对象 ref**（`ref: rootRef`）：SFC 里写的 `ref="rootRef"` 编译产物就是对象 ref，
 * 两者在 runtime-core.cjs.js 的 `setRef`（本机 vue 3.5.43，函数体起始 :1751）里取的是
 * 同一个 `refValue`（:1770：元素 vnode 取 `vnode.el`，组件 vnode 取 `getComponentPublicInstance`），
 * 差别只在落到哪儿，所以这条读数对 Modal 里已经写的 `ref="rootRef"` 直接适用。
 */
const PanelRefProbe = defineComponent({
  setup() {
    const rootRef = ref<HTMLElement | null>(null)
    probeRootRef = rootRef
    return () =>
      h(Teleport, { to: 'body' }, [
        h('div', { class: 'probe-overlay' }, [
          h('div', { class: 'probe-panel', ref: rootRef }, '面板'),
        ]),
      ])
  },
})

/**
 * P-c 探针：同一形状，外加**一个同形的 `$el` 键被 expose 出去**——这就是 3-B 后半要写进 Modal 的东西。
 * 用 setup 上下文里的 `expose()`：`defineExpose(X)` 的产物就是拿 X 调这同一个入口
 * （createSetupContext 的 expose，runtime-core.cjs.js:8414 起；它把 X **原样**赋给 instance.exposed，
 * dev 只 warn「重复调用」与「不是纯对象」，**没有任何 `$` 前缀守卫**——
 * 在该文件的 expose 路径上 grep `Exposing a property` 与 `key[0] === '$'` 均 0 命中）。
 */
const ExposeProbe = defineComponent({
  setup(_props, { expose }) {
    const rootRef = ref<HTMLElement | null>(null)
    expose({ $el: computed(() => rootRef.value ?? undefined) })
    return () =>
      h(Teleport, { to: 'body' }, [
        h('div', { class: 'pcp-overlay' }, [
          h('div', { class: 'pcp-panel', ref: rootRef }, '面板'),
        ]),
      ])
  },
})

/** 调用方模板 ref 拿到的那个对象的可见面（只读断言需要的两个键；不依赖 Vue 的公开类型形状） */
interface PublicSurfaceLike {
  $el?: unknown
  close?: unknown
}

/** 每条用例自己置空再挂载，避免上一条的捕获漏进断言 */
let capturedInstance: PublicSurfaceLike | null = null

/**
 * 以**调用方模板 ref**（函数 ref 形式）捕获公开实例。
 * 函数 ref 与字符串/对象 ref 在 `setRef` 里收到的是同一个 `refValue`（:1770 那个三元表达式），
 * 只是落点不同（函数被调用 vs 写进 ref/setupState），所以这条通道读到的就是
 * `ref="dialogRef"` 之后 `dialogRef.value` 里的东西——正是 `useDialogFocus.ts:50` 要读的那一个。
 * 下面两个宿主只差被挂载的组件：写成两个而不是一个带参函数，是为了让 vue-tsc 对
 * `h(组件, props)` 的 props 形状各自推得出来（带参版本要把 props 收成 Record，
 * 那会让棘轮基线多一条错误——`npm run typecheck:gate` 只许持平）。
 */
function mountModalWithRef(props: { modelValue?: boolean }) {
  capturedInstance = null
  const Host = defineComponent({
    setup() {
      return () =>
        h('div', { class: 'focus-host' }, [
          h(
            Modal,
            {
              modelValue: props.modelValue,
              ref: (el: unknown) => {
                capturedInstance = el as PublicSurfaceLike | null
              },
            },
            { title: () => 'T' },
          ),
        ])
    },
  })
  return track(mount(Host, { attachTo: document.body }))
}

function mountProbeWithRef() {
  capturedInstance = null
  const Host = defineComponent({
    setup() {
      return () =>
        h('div', { class: 'focus-host' }, [
          h(ExposeProbe, {
            ref: (el: unknown) => {
              capturedInstance = el as PublicSurfaceLike | null
            },
          }),
        ])
    },
  })
  return track(mount(Host, { attachTo: document.body }))
}

/** 卸载夹具挂**文件级**：registry 是模块作用域的，见 helpers/trackMount */
afterEach(() => {
  unmountTracked()
  document.body
    .querySelectorAll(
      '.overlay, .modal, .probe-overlay, .probe-panel, .pcp-overlay, .pcp-panel, .focus-host',
    )
    .forEach((n) => n.remove())
  probeRootRef = null
  capturedInstance = null
})

describe('Modal 焦点接入的可达性前提（Task 9 Step 1）', () => {
  it('P-a：Teleport 之内挂在面板上的对象 ref 拿到的是真元素，就是 body 下那块面板', async () => {
    const wrapper = track(mount(PanelRefProbe, { attachTo: document.body }))
    await nextTick()

    expect(probeRootRef).not.toBeNull()
    const panel = probeRootRef!.value
    // 这就是 `P0b-MODAL-02` ④ 那条探针读数的入库版：真元素、住在 target 里。
    expect(panel).toBeInstanceOf(Element)
    expect(panel).toBe(document.body.querySelector('.probe-panel'))
    expect(panel?.parentElement?.classList.contains('probe-overlay')).toBe(true)
    expect(panel?.parentElement?.parentElement).toBe(document.body)
    // ref 拿到的节点与组件自己的根**不是同一个东西**（根仍是 Teleport 的锚点注释）。
    // 面板侧的 rootRef 因此是 3-B 唯一能碰得到面板的通道。
    // ⚠️ 这最后两条断言只对**没有 expose** 的探针成立：同一形状一旦 expose 一个 $el，
    //   wrapper.element 就跟着变成面板（见下面 P-c 的第二条）——那时这两条会红。
    //   可翻红：把上面的 `ref: rootRef` 从 .probe-panel 挪到 .probe-overlay，
    //   本轮实跑报 `expected <div class="probe-overlay">…(1)</div> to be <div class="probe-panel"></div>`。
    expect(wrapper.element).not.toBe(panel)
    expect(wrapper.element.nodeType).toBe(8)
  })

  it('P-b：调用方模板 ref 读到的 $el 仍是锚点注释、不是面板 ⇒ 3-A 死，且本轮 Modal 未 shadow 它', async () => {
    mountModalWithRef({ modelValue: true })
    await nextTick()
    const captured = capturedInstance
    expect(captured).not.toBeNull()

    const anchor = captured!.$el as Node | undefined
    expect(anchor).toBeDefined()
    // 通道与 Modal.vmodel.test.ts 不同：那两条钉的是 VTU 的 wrapper.element，
    // 本条钉的是 LEDGER `P0b-MODAL-02` 只当过一次性探针的**调用方模板 ref** 一侧。
    expect(anchor!.nodeType).toBe(8)
    expect((anchor as Comment).data).toBe('teleport start')
    // 它既不是面板、也不是面板的祖先：面板住在 body 下，锚点住在调用方容器里。
    const overlay = document.body.querySelector('.overlay')
    expect(overlay).not.toBeNull()
    expect(anchor!.contains(overlay!)).toBe(false)
    // 承重的最后一步：注释节点上没有 querySelector，而 useDialogFocus.ts:50-51 拿到的就是它——
    // `if (!root || typeof root.querySelector !== 'function') return` 当场**静默** return。
    // ⇒ 计划里的 3-A（照抄 el-dialog 那套 `dialogRef.$el` 口径）在 Teleport 根下接不上焦点，
    //   不报错也不红。3-B（自持 rootRef）由此不是"更干净的写法"而是唯一可走的写法。
    expect(typeof (anchor as unknown as { querySelector?: unknown }).querySelector).toBe(
      'undefined',
    )
    /**
     * 本条实测**否证**了交接给本任务的那句"defineExpose 一写，父级可见面就从 instance.proxy
     * （全部 `<script setup>` 绑定）收窄到 exposed 的键 + public properties"：
     * 在**没有任何 expose** 的今天 `captured.close` 就已经是 `undefined`——`hasSetupBinding`
     * （runtime-core.cjs.js:3435）明确要求 `!state.__isScriptSetup`，而 script-setup 组件的
     * setupState 正是带那个标记的，所以 `<script setup>` 的绑定从来就不挂在 instance.proxy 上。
     * ⇒ expose 一个键对这张面是**加**不是**减**；Phase 4 的迁移点不会从这里继承到
     *   "读不到别的绑定"那道闸（它们本来就只读得到 public properties）。
     * 可翻红：把期望改成 `'function'` 即红（那才是"绑定挂在 proxy 上"的世界，即 Options API）。
     */
    expect(typeof captured!.close).toBe('undefined')
  })
})

describe('defineExpose 一个 $el 时会发生什么（P-c，钉在探针上）', () => {
  it('调用方模板 ref 读到的是**已解包的元素**，不是 ComputedRef ⇒ 3-B 后半的机制成立', async () => {
    mountProbeWithRef()
    await nextTick()
    const captured = capturedInstance
    expect(captured).not.toBeNull()

    const exposed = captured!.$el as Element | undefined
    expect(exposed).toBeInstanceOf(Element)
    expect(exposed).toBe(document.body.querySelector('.pcp-panel'))
    // 解包发生在哪一层是量出来的：instance.exposed 里躺着的**是**那个 computed，
    // 解包动作在 getComponentPublicInstance（:8455-8472）构建的
    // new Proxy(proxyRefs(markRaw(instance.exposed)), ...) 上——get 见 key in target 成立就返回
    // target[key]，proxyRefs 于是把 computed 变成元素；只有 key 不在 target 里才回落 publicPropertiesMap。
    expect((exposed as unknown as { nodeType?: number }).nodeType).toBe(1)
    /**
     * 怎么把这三条跑红（三种各自独立的改法，本轮都实跑过）：
     * - 把 expose 的键改名 `$panel` ⇒ `$el` 回落内置那条锚点注释，`toBeInstanceOf(Element)` 红；
     * - 把探针里 `ref: rootRef` 从 .pcp-panel 挪到 .pcp-overlay ⇒ identity 那条红；
     * - 把 Modal.vmodel.test.ts 的「modelValue=true 时渲染」拿去对照：Modal 若真写上这个 expose，
     *   那条会报 `expected 1 to be 8`（见 Modal.vue 的 ⚠️ 注释与台账 P0b-MODAL-04 行）。
     */
  })

  it('同一个 shadow 也改掉 VTU 的 wrapper.element ⇒ 这就是本轮不给 Modal 写 defineExpose 的全部理由', async () => {
    const wrapper = track(mount(ExposeProbe, { attachTo: document.body }))
    await nextTick()
    // 与上面 P-a 的对照实验：形状完全相同，差别只有一个 expose({ $el: ... })。
    // VTU 的 createVMProxy（本机 @vue/test-utils 3.5.x 的 vue-test-utils.cjs.js:7617-7646）把
    // `key in vm.$.exposed` 排在 setupState 与 vm 之前，而 VueWrapper.element 就是 this.vm.$el
    // （:7788-7790）⇒ wrapper.element 从"锚点注释"变成"面板元素"。
    // 后果（本轮实跑，把这段 expose 原样写进 Modal.vue 之后）：
    //   src/components/ui/ = 18 failed / 82 passed（共 100），4 个文件红；
    //   关闭态 rootRef 为 null ⇒ wrapper.element 是 undefined ⇒
    //   helpers/trackMount.ts:48 的 `w.element.isConnected` 抛
    //   `TypeError: Cannot read properties of undefined (reading 'isConnected')`，
    //   整个 Modal 测试族的 afterEach 崩掉，僵尸实例与 document keydown 监听互相污染。
    expect(wrapper.element).toBe(document.body.querySelector('.pcp-panel'))
    expect(wrapper.element.nodeType).toBe(1)
  })
})

describe('Modal 的焦点行为（Task 9 Step 4）', () => {
  /**
   * 三条用例各有一个能把它跑红的改法，逐条写在注释里；本轮全部实跑过，报错原文与汇总行
   * 都在 task-9-report.md（那份未入 git，所以这里只写"怎么红"，不写"红过"当依据）。
   * 为什么是这三条（控制器裁定 R-T9-6）：brief Step 4 的第二条「禁用首个控件时焦点跳过」
   * 在 Modal 上是**伪命题**——头部关闭按钮永远不带 disabled，改不改跳过逻辑它都绿，
   * 所以换成了下面这三条各自可证伪的。
   */
  let rafQueue: FrameRequestCallback[] = []
  let focusCalls: Element[] = []
  const realFocus = HTMLElement.prototype.focus

  beforeEach(() => {
    rafQueue = []
    focusCalls = []
    // 接管 requestAnimationFrame（与 useDialogFocus.test.ts:19-24、Drawer.test.ts 同一做法）：
    // 原语的焦点动作排在 nextTick 之后的那一帧里，不接管就断不到确定时刻。
    // ⚠️ 这里偏离 brief Step 4 的写法（它 `await new Promise(r => requestAnimationFrame(r))` 用真帧）：
    //   接管队列是本仓原语测试与 Drawer 测试已经在用的做法，且 false→true 那条需要能反复冲刷。
    vi.stubGlobal('requestAnimationFrame', (cb: FrameRequestCallback) => {
      rafQueue.push(cb)
      return rafQueue.length
    })
    vi.stubGlobal('cancelAnimationFrame', () => {})
    // 数"原语一共调了几次 focus"——第三条用例（只聚焦一次）靠它，而不是靠数 DOM 事件。
    vi.spyOn(HTMLElement.prototype, 'focus').mockImplementation(function (this: HTMLElement) {
      focusCalls.push(this)
      realFocus.call(this)
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
    // 焦点本身也要复位，否则下一条用例的 activeElement 会停在上一条的面板里。
    ;(document.activeElement as HTMLElement | null)?.blur?.()
  })

  function flushFrames() {
    const queue = rafQueue
    rafQueue = []
    queue.forEach((cb) => cb(0))
  }

  /**
   * 等渲染 + 冲刷帧：useDialogFocus 的链路是 watch(visible) → nextTick → requestAnimationFrame
   * → querySelector → focus（useDialogFocus.ts:45-55）。挂载即打开时，翻 mounted 的那次触发
   * 落在哪一轮 flush 取决于 Vue 当时的队列位置，所以来回三轮、每轮都既推微任务又冲刷帧队列
   * （与 Drawer.test.ts 的 settle 同一做法；少冲一轮的话用例只会"红在时序"而不是红在行为）。
   */
  async function settle(wrapper: { vm: { $nextTick(): Promise<void> } }) {
    for (let i = 0; i < 3; i++) {
      await wrapper.vm.$nextTick()
      flushFrames()
    }
    await wrapper.vm.$nextTick()
  }

  const SLOT = {
    title: 'T',
    default: '<input class="probe-input" /><button class="probe-btn">后</button>',
  }

  it('首个焦点落在头部关闭按钮上（钉节点身份；这本身就是与 el-dialog 对齐的结果）', async () => {
    const wrapper = track(
      mount(Modal, { props: { modelValue: true }, slots: SLOT, attachTo: document.body }),
    )
    await settle(wrapper)

    const closeBtn = document.body.querySelector('.modal-head .t-icon-btn')
    expect(closeBtn).not.toBeNull()
    expect(document.activeElement).toBe(closeBtn)
    // 不是插槽里那个 input：原语取的是 root.querySelector(FOCUSABLE_SELECTOR)——
    // 一个逗号并集、按**文档顺序**取首个命中（useDialogFocus.ts:32-39 与 :52），
    // 而 Modal 的 .modal-head（含关闭按钮）排在 .modal-body（默认插槽）之前（Modal.vue 的模板）。
    // ⇒ 首个可聚焦控件就是那颗关闭按钮。这不是缺陷：EP 的 el-dialog 头部同样有关闭按钮排在前面，
    //   那两条 el-dialog 的既有接线点也是同一结果，所以"聚焦到关闭按钮"**就是** parity 的落点。
    //   要改落点只能改原语选择器或给面板加 tabindex——两者都超出本任务范围（控制器裁定 R-T9-5 明令不做）。
    expect(document.activeElement).not.toBe(document.body.querySelector('.probe-input'))
    // 焦点确实在面板内部（读屏/键盘用户不再被留在背景页里），这条用的是原语自己导出的选择器。
    expect(document.querySelector(DIALOG_FOCUSABLE_SELECTOR)).toBe(closeBtn)
    /**
     * 怎么跑红（三选一，本轮都实跑过）：
     * - 删掉 Modal.vue 里那句 useDialogFocus(...) ⇒ activeElement 停在 body，本条红；
     * - 把模板上的 `ref="rootRef"` 摘掉 ⇒ 原语拿到 rootRef.value 为 null，静默 return，本条红；
     * - 把适配器写成 computed(() => ({ $el: undefined })) ⇒ 同上，本条红。
     */
  })

  it('modelValue 由 false 翻到 true 时重新聚焦（R-T9-1 适配器要接住的那条边沿）', async () => {
    const wrapper = track(
      mount(Modal, { props: { modelValue: false }, slots: SLOT, attachTo: document.body }),
    )
    await settle(wrapper)
    // 关闭态绝不抢焦点：面板还没渲染，且原语的门是 `if (!open) return`。
    expect(focusCalls).toHaveLength(0)
    expect(document.activeElement).toBe(document.body)

    await wrapper.setProps({ modelValue: true })
    await settle(wrapper)
    const closeBtn = document.body.querySelector('.modal-head .t-icon-btn')
    expect(closeBtn).not.toBeNull()
    expect(document.activeElement).toBe(closeBtn)
    expect(focusCalls).toHaveLength(1)
    /**
     * 本条独有的对偶实验（其余三条用例在它都绿，所以它不是重复覆盖）：
     * 把 Modal.vue 里那个可见性源从 `computed(() => open.value && mounted.value)` 折成
     * `mounted`（丢掉 open 这一半）⇒ 挂载时翻 mounted 就发一次边沿，但此刻面板还没渲染、
     * rootRef 为 null，原语静默 return；真正 false→true 时可见性没有新边沿 ⇒ 焦点永远不来，
     * **只有本条转红**（本轮实跑：`Tests 1 failed / 99 passed (100)`）。
     */
  })

  it('挂载即打开（13 个存量调用点的形状）聚焦且只聚焦一次 ⇒ R-T9-1 适配器的证伪面', async () => {
    const wrapper = track(
      mount(Modal, { props: { modelValue: true }, slots: SLOT, attachTo: document.body }),
    )
    await settle(wrapper)

    const closeBtn = document.body.querySelector('.modal-head .t-icon-btn')
    expect(closeBtn).not.toBeNull()
    expect(focusCalls).toHaveLength(1)
    expect(focusCalls[0]).toBe(closeBtn)
    expect(document.activeElement).toBe(closeBtn)
    // 再推三轮帧也不该有第二次：一次可见性边沿只对应一次聚焦。
    await settle(wrapper)
    expect(focusCalls).toHaveLength(1)
    /**
     * 本条是 R-T9-1 那条适配器的**证伪面**：把可见性源改回 brief/原语口径的 `open`
     * （即 `useDialogFocus(dialogRef, open)`，不带 mounted）⇒ 挂载即打开永远等不到边沿
     * （watch 没有 immediate，这是原语的时序契约），focusCalls 变成 0 ⇒ 本条与第一条一起红，
     * 而 false→true 那条照旧绿（本轮实跑：`Tests 2 failed / 98 passed (100)`）。
     * 反向：把 useDialogFocus(...) 调用两次 ⇒ focusCalls 变 2，**只有本条**红（另两条比的是
     * 落点身份，不数次数）。没有这条用例的话，"删掉 mounted 这一半"这个改法不会有任何测试拦住。
     */
  })
})
