/* eslint-disable vue/one-component-per-file --
   本文件的前两条用例必须自带探针宿主（PanelRefProbe 与给 Modal 挂模板 ref 的 Host）：
   要测的就是「模板 ref 在 Teleport 根之下究竟拿到什么」这件事本身，而 Modal.vue 是被测对象、
   不能顺手改成夹具。与 Modal.teleport.test.ts 同一处置、同一条理由（该规则针对产品组件）。 */
import { afterEach, describe, expect, it } from 'vitest'
import { Teleport, defineComponent, h, nextTick, ref } from 'vue'
import type { Ref } from 'vue'
import { mount } from '@vue/test-utils'
import Modal from '../Modal.vue'
import { track, unmountTracked } from './helpers/trackMount'

/**
 * Task 9：Modal 的焦点接入。本文件 Step 1 只做一件事——把 LEDGER `P0b-MODAL-02`
 * 记给 Task 9 的**两条未入库读数**（③ 后半句、④，探针 stdout 只在未入 git 的 task-8-report.md 里）
 * 接成入库断言，并补上该行没碰过的第三条通道：**调用方模板 ref** 读到的 `$el` 是什么。
 * 焦点行为断言要等 Modal.vue 真的接上 `useDialogFocus` 才有对象，见文件末尾的 Step 4 块。
 *
 * 编号按控制器的口径（P-a / P-b / P-c），不用 brief 的 ①/②——那两个符号与
 * `P0b-MODAL-02` 里的 ③/④ 撞名。
 *
 * 与 Modal.vmodel.test.ts 的分工（不重述已入库事实）：那份钉的是 **VTU 的 `wrapper.element`**
 * 通道（`nodeType` 8、`data` 'teleport start'，开与关两态都是那条锚点注释）。
 * 本文件钉的是另外两条它没有的通道——组件**内部**的对象 ref，与**调用方**的模板 ref。
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
 * 差别只在落到哪儿，所以这条读数对 Modal 里将要写的 `ref="rootRef"` 直接适用。
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

/** 调用方模板 ref 拿到的那个对象的可见面（本 commit 前是 `instance.proxy`；只读需要的两个键） */
interface ModalPublicSurface {
  $el?: unknown
  close?: unknown
}

/** 每条用例自己置空再挂载，避免上一条的捕获漏进断言 */
let capturedModalInstance: ModalPublicSurface | null = null

/**
 * P-b 探针宿主：以**调用方模板 ref**（函数 ref 形式）捕获 `<Modal>` 的公开实例。
 * 函数 ref 与字符串/对象 ref 在 `setRef` 里收到的是同一个 `refValue`（:1770 那个三元表达式），
 * 只是落点不同（函数被调用 vs 写进 ref/setupState），所以这条通道读到的就是
 * `ref="dialogRef"` 之后 `dialogRef.value` 里的东西——正是 `useDialogFocus.ts:50` 要读的那一个。
 */
function mountModalHost(props: { modelValue?: boolean } = {}) {
  const Host = defineComponent({
    setup() {
      return () =>
        h('div', { class: 'focus-host' }, [
          h(
            Modal,
            {
              modelValue: props.modelValue,
              ref: (el: unknown) => {
                capturedModalInstance = el as ModalPublicSurface | null
              },
            },
            { title: () => 'T' },
          ),
        ])
    },
  })
  return track(mount(Host, { attachTo: document.body }))
}

/** 卸载夹具挂**文件级**：registry 是模块作用域的，见 helpers/trackMount */
afterEach(() => {
  unmountTracked()
  document.body
    .querySelectorAll('.overlay, .modal, .probe-overlay, .probe-panel, .focus-host')
    .forEach((n) => n.remove())
  probeRootRef = null
  capturedModalInstance = null
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
    expect(wrapper.element).not.toBe(panel)
    expect(wrapper.element.nodeType).toBe(8)
  })

  it('P-b：调用方模板 ref 在今天（尚无 defineExpose）的 Modal 上读到的 $el 是锚点注释、不是面板 ⇒ 3-A 死', async () => {
    mountModalHost({ modelValue: true })
    await nextTick()

    expect(capturedModalInstance).not.toBeNull()
    const anchor = capturedModalInstance!.$el as Node | undefined
    expect(anchor).toBeDefined()
    expect(anchor!.nodeType).toBe(8)
    expect((anchor as Comment).data).toBe('teleport start')
    // 它既不是面板、也不是面板的祖先：面板住在 body 下，锚点住在调用方容器里。
    const overlay = document.body.querySelector('.overlay')
    expect(overlay).not.toBeNull()
    expect(anchor!.contains(overlay!)).toBe(false)
    // 承重的最后一步：注释节点上没有 querySelector，而 useDialogFocus.ts:50-51 拿到的就是它——
    // `if (!root || typeof root.querySelector !== 'function') return` 当场静默 return。
    // ⇒ 计划里的 3-A（照抄 el-dialog 那套 `dialogRef.$el` 口径）在 Teleport 根下**接不上焦点**，
    //   且是静默接不上，不报错。3-B（自持 rootRef）由此不是"更干净的写法"而是唯一可走的写法。
    expect(typeof (anchor as unknown as { querySelector?: unknown }).querySelector).toBe(
      'undefined',
    )
    /**
     * 本条实测**否证**了交接给我的那句"defineExpose 一写，父级可见面就从 instance.proxy
     * （全部 `<script setup>` 绑定）收窄到 exposed 的键 + public properties"：
     * 今天在**没有任何 expose** 的世界里 `captured.close` 已经是 `undefined`，因为
     * `<script setup>` 的绑定从来就不挂在 `instance.proxy` 上——
     * `hasSetupBinding`（本机 vue 3.5.43 的 runtime-core.cjs.js:3435）明确要求 `!state.__isScriptSetup`，
     * 而 script-setup 组件的 `setupState` 正是带 `__isScriptSetup` 标记的那一个。
     * ⇒ `defineExpose({ $el })` 对父级可见面是**加**了一个键，不是**减**；Phase 4 的 65 个迁移点
     *   不会从这里继承到"读不到别的绑定"这道闸（它们本来就只读得到 public properties）。
     * 可翻红：把本行的期望改成 `'function'` 即红（那才是"绑定挂在 proxy 上"的世界）。
     */
    expect(typeof capturedModalInstance!.close).toBe('undefined')
  })
})
