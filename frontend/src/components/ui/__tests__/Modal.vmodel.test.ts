import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import Modal from '../Modal.vue'

/**
 * Modal 的 v-model 通道（Task 7）
 * ==============================
 * Task 7 只加「可见性门 + update:modelValue 第二通道」，**不加 Teleport**（Teleport 归 Task 8，
 * 见 task-7-brief 的修正说明与 progress.md 的 R-T6-4）。所以本文件的查询一律用 `wrapper.find`：
 * 内容仍在 wrapper 的元素树内，Task 8 引入 Teleport 时会把这里的查询一起迁到 `document.body`。
 *
 * 缺省语义是本任务唯一的破/不破分水岭：13 个调用点（12 个文件）一个都不传 modelValue，
 * 全靠外层 `v-if` 控制可见性，所以「没传」必须继续等于「可见」。
 * `不传 modelValue 时仍渲染` 那条 + Modal.compat.test.ts 的 10 条一起把这一点钉住。
 */
interface TrackedWrapper {
  readonly element: Element
  unmount(): void
}
const mounted: TrackedWrapper[] = []

/** 显式建模，和 Drawer.test.ts 的 DrawerProps 同一写法（避免用 Record<string, unknown> 绕过类型） */
interface ModalProps {
  modelValue?: boolean
}

/**
 * 与 brief 的唯一写法差别：brief 的四个用例各自内联 `mount(...)`，这里统一走 factory()。
 * 挂载选项逐字相同（props / slots.title='T' / attachTo: document.body），断言一字未改，
 * 差别只是每个 wrapper 都被登记、由 afterEach 卸载——本文件全部 attach 到 body，
 * 不卸载就会把 `.overlay` 与 document keydown 监听留给后面的用例。Task 8 会把本文件的
 * 查询迁到 `document.body.querySelector`（计划 Task 8 Step 4 点名 Modal.vmodel.test.ts），
 * 那时僵尸节点会直接污染断言。这是 Modal.compat.test.ts:10-14 同一处置的前置应用。
 */
function factory(props: ModalProps = {}, slots: Record<string, string> = { title: 'T' }) {
  const wrapper = mount(Modal, { props, slots, attachTo: document.body })
  mounted.push(wrapper)
  return wrapper
}

describe('Modal v-model 通道', () => {
  afterEach(() => {
    for (const w of mounted) {
      if (w.element.isConnected) w.unmount()
    }
    mounted.length = 0
  })

  it('modelValue=false 时不渲染遮罩', () => {
    const wrapper = factory({ modelValue: false })
    expect(wrapper.find('.overlay').exists()).toBe(false)
  })

  it('modelValue=true 时渲染', () => {
    const wrapper = factory({ modelValue: true })
    expect(wrapper.find('.overlay').exists()).toBe(true)
  })

  it('关闭时同时 emit close 与 update:modelValue(false)，两通道都通', async () => {
    const wrapper = factory({ modelValue: true })
    await wrapper.find('.modal-head .t-icon-btn').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
  })

  it('不传 modelValue 时仍渲染（12 个老调用点用 v-if 自行控制可见性）', () => {
    const wrapper = factory()
    expect(wrapper.find('.overlay').exists()).toBe(true)
  })

  // 以下三条钉的是 brief Step 3 代码里另外三个可写错的分支，
  // 每条都在报告里做过变异证明（改坏 → 本条转红 → 还原）。

  it('可见性是响应式而非初值：true→false 会撤掉整棵遮罩，false→true 会重新挂上', async () => {
    // 若 `open` 只在挂载时算一次（或 v-if 写成了 v-once / 常量），这条会红。
    // Phase 4 的 `v-model="visible"` 调用点正是靠这个边沿反复开关，Modal 实例全程存活。
    const wrapper = factory({ modelValue: true })
    expect(wrapper.find('.overlay').exists()).toBe(true)
    await wrapper.setProps({ modelValue: false })
    expect(wrapper.find('.overlay').exists()).toBe(false)
    await wrapper.setProps({ modelValue: true })
    expect(wrapper.find('.overlay').exists()).toBe(true)
  })

  it('Esc 与关闭按钮共用同一 close 通道：两个入口都双发', async () => {
    // 现有 onEsc 与按钮 @click 都调 close()（Modal.vue:30-32 与 :33-38，按钮在 :60）。
    // 若日后有人把 Esc 分支写成只 emit close，Phase 4 的 `v-model` 调用点会「按 Esc 关不掉」，
    // 而 Modal.compat.test.ts 只钉了 close 这一路，抓不到 update:modelValue 缺失。
    const wrapper = factory({ modelValue: true })
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([false])
  })

  it('不传 modelValue 时只 emit close，不 emit update:modelValue（缺省通道的负半边）', async () => {
    // 钉住 brief 的门卫 `if (props.modelValue === true)`：老调用点没绑 v-model，
    // 给它们补一个没人听的 update 事件是噪声；缺省语义的完整表述是
    // 「不传 = 恒可见 + 仍走旧的单一 close 通道」。
    const wrapper = factory()
    await wrapper.find('.modal-head .t-icon-btn').trigger('click')
    expect(wrapper.emitted('close')).toHaveLength(1)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
  })
})
