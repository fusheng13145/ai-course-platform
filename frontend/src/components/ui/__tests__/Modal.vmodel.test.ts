import { afterEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import Modal from '../Modal.vue'
import { track, unmountTracked } from './helpers/trackMount'

/**
 * Modal 的 v-model 通道（Task 7）
 * ==============================
 * Task 7 只加「可见性门 + update:modelValue 第二通道」，**不加 Teleport**（Teleport 归 Task 8，
 * 见 task-7-brief 的修正说明与 progress.md 的 R-T6-4）。Task 8 已经把 Teleport 落地，
 * 所以本文件的查询也随之一律走 `document.body`：遮罩不在 wrapper 的元素树里，
 * `wrapper.find('.overlay')` 从这一点起**恒为落空**，用它判"渲染没渲染"会得到假绿/假红。
 * 「节点不存在」用 `toBeNull()`，「节点必须存在」用 `not.toBeNull()`——判定对象一条没改，
 * 改的只是取节点的方式。
 *
 * 缺省语义是本文件唯一的破/不破分水岭：13 个调用点（12 个文件）一个都不传 modelValue，
 * 全靠外层 `v-if` 控制可见性，所以「没传」必须继续等于「可见」。
 * `不传 modelValue 时仍渲染` 那条与 Modal.compat.test.ts 的 legacy 钉一起把这一点钉住。
 *
 * 挂载/卸载夹具走 helpers/trackMount（与 Modal.compat.test.ts 共用，此处不再复制登记表）。
 */

/** 显式建模，和 Drawer.test.ts 的 DrawerProps 同一写法（避免用 Record<string, unknown> 绕过类型） */
interface ModalProps {
  modelValue?: boolean
}

/**
 * 与 brief 的唯一写法差别：brief 的四个用例各自内联 `mount(...)`，这里统一走 factory()。
 * 挂载选项逐字相同（props / slots.title='T' / attachTo: document.body），断言一字未改，
 * 差别只是每个 wrapper 都被登记、由 afterEach 卸载——本文件全部 attach 到 body，
 * 不卸载就会把 `.overlay` 与 document keydown 监听留给后面的用例（Task 8 迁 body 查询后
 * 僵尸节点会直接污染断言）。
 */
function factory(props: ModalProps = {}, slots: Record<string, string> = { title: 'T' }) {
  const wrapper = mount(Modal, { props, slots, attachTo: document.body })
  return track(wrapper)
}

/** Teleport 后要在 body 子树里触发事件：原生 dispatch 即可，Vue 的监听器就挂在元素上。
 *  bubbles 必须 true——「按内容区不该关」依赖事件冒到 .overlay 再被 `.self` 挡掉。 */
async function fireOn(sel: string, type: 'click' | 'mousedown'): Promise<void> {
  const el = document.body.querySelector(sel)
  expect(el, `Teleport 后 body 里找不到 ${sel}`).not.toBeNull()
  el!.dispatchEvent(new MouseEvent(type, { bubbles: true }))
  await nextTick()
}

/** 卸载夹具挂**文件级**（不套 describe）：registry 是模块作用域的，见 trackMount。 */
afterEach(unmountTracked)

describe('Modal v-model 通道', () => {
  it('modelValue=false 时不渲染遮罩', () => {
    const wrapper = factory({ modelValue: false })
    expect(document.body.querySelector('.overlay')).toBeNull()
    // trackMount 把 element 定为 Node 的事实依据：单根 v-if 关闭时组件根是 Comment 占位节点
    // （nodeType 8），`element: Element` 的旧声明在这条用例会说谎。
    // Task 8 上 Teleport 后重测仍成立（实测 nodeType=8、nodeName=#comment、isConnected=true）：
    // 占位符落在原位置，Teleport 的锚点没有顶掉它，所以这条不用跟着改。
    expect(wrapper.element.nodeType).toBe(8)
  })

  it('modelValue=true 时渲染', () => {
    factory({ modelValue: true })
    expect(document.body.querySelector('.overlay')).not.toBeNull()
  })

  it('关闭时同时 emit close 与 update:modelValue(false)，两通道都通', async () => {
    const wrapper = factory({ modelValue: true })
    await fireOn('.modal-head .t-icon-btn', 'click')
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
  })

  it('不传 modelValue 时仍渲染（12 个老调用点用 v-if 自行控制可见性）', () => {
    factory()
    expect(document.body.querySelector('.overlay')).not.toBeNull()
  })

  // 以下各条钉的是 brief Step 3 代码里其余可写错的分支与 Task 7 修复轮（评审 I-1/M-1/M-3）
  // 的新契约；每条在报告里做过变异证明（改坏 → 本条转红 → 还原）。

  it('可见性是响应式而非初值：true→false 会撤掉整棵遮罩，false→true 会重新挂上', async () => {
    // 若 `open` 只在挂载时算一次（或 v-if 写成了 v-once / 常量），这条会红。
    // Phase 4 的 `v-model="visible"` 调用点正是靠这个边沿反复开关，Modal 实例全程存活。
    // Teleport 不影响这条的判据：开关的是 Teleport **内部**的 v-if，不是 Teleport 本身。
    const wrapper = factory({ modelValue: true })
    expect(document.body.querySelector('.overlay')).not.toBeNull()
    await wrapper.setProps({ modelValue: false })
    expect(document.body.querySelector('.overlay')).toBeNull()
    await wrapper.setProps({ modelValue: true })
    expect(document.body.querySelector('.overlay')).not.toBeNull()
  })

  it('Esc 与关闭按钮共用同一 close 通道：两个入口都双发', async () => {
    // onEsc 与按钮 @click 都调 close()（`<script setup>` 的 onEsc/模板 .t-icon-btn）。
    // 若日后有人把 Esc 分支写成只 emit close，Phase 4 的 `v-model` 调用点会「按 Esc 关不掉」，
    // 而 Modal.compat.test.ts 只钉了 close 这一路，抓不到 update:modelValue 缺失。
    const wrapper = factory({ modelValue: true })
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
  })

  it('遮罩 mousedown.self 同样是双通道入口（modelValue:true 下双发）', async () => {
    // M-3：此前只有按钮与 Esc 两条入口钉了双发，onOverlay 这条路没有。
    // 变异证明：onOverlay 改成直接 emit('close') 后本条转红。
    const wrapper = factory({ modelValue: true })
    await fireOn('.overlay', 'mousedown')
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
  })

  it('不传 modelValue 时关闭同样双发——门卫是 open，不是 modelValue===true', async () => {
    // 门卫取派生值 open、不取 `modelValue === true`，依据要写清（Task 8 复核修正，别照旧注释理解）：
    // Interfaces 原文「关闭时同时触发 close 与 update:modelValue」确实支持这个改动；但 `=== true`
    // **并非未授权的发明**——它就是 brief Step 3 示例代码逐字的写法（task-7-brief.md 的 close()：
    // `if (props.modelValue === true) emit('update:modelValue', false)`），后由控制器裁定作废。
    // 裁定可以改文本，不能把改前的文本说成从来不存在。
    // 现状：13 个老调用点没人绑 v-model（grep `<Modal` 全仓 0 处 model 绑定），
    // 多收一个无人监听的 update:modelValue 在 Vue 里无害。
    const wrapper = factory()
    await fireOn('.modal-head .t-icon-btn', 'click')
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
  })

  it('v-model 绑 ref<boolean|undefined>()（显式传 undefined）时能被关闭，不会渲染得出关不掉', async () => {
    // 评审 M-1 的原始事故形状：props.modelValue 为 undefined ⇒ open 为 true（渲染遮罩），
    // 旧实现里 close() 的 `=== true` 守卫让三个入口（按钮/遮罩/Esc）都发不出回写，弹窗锁死。
    // 显式传 key=undefined 与 v-model 绑定一个空 ref 在 Vue 侧同为「有 key、值 undefined」
    // （setFullProps 的 castValues 仍 hasOwn，isAbsent=false，取值走 default=undefined）。
    const wrapper = factory({ modelValue: undefined })
    expect(document.body.querySelector('.overlay')).not.toBeNull()
    await fireOn('.modal-head .t-icon-btn', 'click')
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
  })

  it('关闭态（modelValue:false）按 Esc 什么都不 emit', async () => {
    // 评审 I-1：`v-if` 只摘子树、不销毁实例，keydown 监听整个挂载期常驻 document。
    // 不加 open 门卫时，这条必红（旧实现照发 close）；compat 的 `Esc emit close` 钉只测
    // 「开着时 Esc 有效」，抓不到「关着时 Esc 不该响」，所以本条是新能力侧的独有钉。
    const wrapper = factory({ modelValue: false })
    await wrapper.vm.$nextTick()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toBeUndefined()
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })

  it('开过→父组件置 false 关掉的实例，再按 Esc 不补发幽灵 close', async () => {
    // I-1 的真实事故剧本：Phase 4 的 `v-model="visible"` 调用点里实例全程存活。
    // 第一次 Esc 正常双发；父组件据此写回 false 后，第二次 Esc 不得再产生任何事件——
    // 否则一次按键会 re-fire 树上所有「曾经开过、现已关闭」Modal 的 close
    // （其中 5 个存量站点 `@close="emit('close')"` 会把它原样递给各自的父组件）。
    const wrapper = factory({ modelValue: true })
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toHaveLength(1)
    await wrapper.setProps({ modelValue: false })
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(wrapper.emitted('update:modelValue')).toHaveLength(1)
  })
})
