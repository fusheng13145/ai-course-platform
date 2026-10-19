/* eslint-disable vue/one-component-per-file --
   本文件必须自己造两个「带 transform 的父容器」组件（Host / Inner）才能复现包含块条件：
   Teleport 的落点是相对**调用方 DOM** 而言的，用一个真实包裹层才有意义，
   而 Modal.vue 本身不能变成测试夹具。vue/one-component-per-file 针对的是产品组件，不是测试宿主。 */
import { afterEach, describe, expect, it } from 'vitest'
import { defineComponent, h, nextTick } from 'vue'
import { mount } from '@vue/test-utils'
import Modal from '../Modal.vue'
import { track, unmountTracked } from './helpers/trackMount'

/**
 * Task 8：把 spec §2.6 的「transform 包含块」陷阱钉成测试。
 *
 * 为什么必须钉：`position: fixed` 的后代会被**任何计算值非 `none`** 的
 * `transform` / `filter` / `backdrop-filter` / `will-change` 祖先包含。本仓实测两处**今日就生效**：
 * - `views/student/components/SdCourseGrid.vue:111` 的 `<Modal narrow>` 就在模板根
 *   `<section class="l1-card animate-in d2">` 之内；`.animate-in` 是
 *   `animation: rise-in 0.42s var(--ease-out) both`（design-system.css 的 `.animate-in` 规则），
 *   `rise-in` 的 `to` 帧是 `transform: translateY(0)` —— `both` 是**填充**，动画跑完这一帧永久留在
 *   计算值里，而 `translateY(0)` ≠ `none` ⇒ 包含块永久存在。`.l1-card` 还带 `overflow: hidden`，
 *   于是 `inset: 0` 的遮罩被裁进卡片，表现就是「弹窗缩在卡片中间」。
 * - `views/teacher/tools/KnowledgeManagement.vue:96` 的 `<ExcelImportDialog>`（其模板根即 `<Modal wide>`）
 *   挂在页面根 `<div class="fade-in">` 下；`.fade-in` 是 `animation: fadeIn .35s ... forwards`
 *   （index.scss 的 `.fade-in` 规则），`to` 帧同样是 `translateY(0)`，机制一模一样。
 *
 * ⚠️ 这条缺陷**基线拍不到**：design-system.css 的 `prefers-reduced-motion: reduce` 块把
 * `.animate-in, .fade-in` 一并降为 `animation: none` ⇒ 计算 transform 回 `none`、包含块消失，
 * 而视觉基线正是在 `reducedMotion: 'reduce'` + `animations: 'disabled'` 下采集的（spec §6.11）。
 * 只有结构断言抓得到，所以写在 vitest 里，不指望 Playwright。
 *
 * jsdom 不应用 `<style>`，本文件证明的是「DOM 落在哪」，不是「宽度/位置按 CSS 真的对不对」；
 * 后者的缺口仍由 Playwright 基线承担（brief Step 3 已承认这条边界）。
 */

/**
 * 父容器带 transform —— 复现上面两例的真实条件（rise-in/fadeIn 以 both/forwards 填充，
 * 计算后 transform 非 none）。若不 Teleport，遮罩会被困在这个 300px 高的盒子里，
 * 而 jsdom 不会报错——只有结构断言抓得到。
 */
const Host = defineComponent({
  setup() {
    return () =>
      h(
        'div',
        { style: 'transform: translateY(0px); height: 300px; overflow: hidden' },
        [h(Modal, { modelValue: true }, { title: () => '标题' })],
      )
  },
})

describe('Modal Teleport 行为', () => {
  afterEach(unmountTracked)

  it('遮罩渲染到 body 下，不在带 transform 的父容器内', async () => {
    track(mount(Host, { attachTo: document.body }))
    await nextTick()
    const overlay = document.body.querySelector('.overlay')
    expect(overlay).not.toBeNull()
    expect(overlay?.parentElement).toBe(document.body)
    expect(overlay?.closest('[style*="transform"]')).toBeNull()
  })

  it('teleported=false 时退回原位（保留逃生门，默认仍 teleport）', async () => {
    const Inner = defineComponent({
      setup() {
        return () =>
          h('div', { class: 'host-box' }, [
            h(Modal, { modelValue: true, teleported: false }, { title: () => 'T' }),
          ])
      },
    })
    const wrapper = track(mount(Inner, { attachTo: document.body }))
    await nextTick()
    expect(wrapper.find('.host-box .overlay').exists()).toBe(true)
    // 原位渲染时它**不该**同时是 body 的直接子节点——否则「关掉 teleport」是假的。
    expect(document.body.querySelector(':scope > .overlay')).toBeNull()
  })

  it('透传属性仍落在 .overlay 上（Teleport 根的实测回归兜底）', async () => {
    // 实测过的行为差别：根从 `<div class="overlay">` 换成 `<Teleport>` 之后，Vue **不再**把
    // class / data-* 这些 fall-through attrs 自动贴到任何元素上——探针跑出来是
    // `.overlay` 只剩 `class="overlay"`、`.modal` 只剩 `class="modal"`，属性静默消失。
    // Task 8 之前它们落在根 `.overlay` 上，所以 Modal.vue 显式写了 `v-bind="$attrs"` + `inheritAttrs:false`
    // 把**原落点**保住（挪到 `.modal` 会改变 class 的落点，没采纳）。
    // 现存 13 个调用点一个都不传 class/data-*，这条钉的是 Phase 4 的 65 个迁移点。
    track(
      mount(Modal, {
        props: {},
        attrs: { 'data-test': 'x', class: 'extra-cls' },
        slots: { title: 'T' },
        attachTo: document.body,
      }),
    )
    await nextTick()
    const overlay = document.body.querySelector('.overlay')
    expect(overlay).not.toBeNull()
    expect(overlay!.getAttribute('data-test')).toBe('x')
    expect(overlay!.classList.contains('extra-cls')).toBe(true)
    // 只落一份：inheritAttrs:false 若被删，运行时会自动再套一次，class 会重复出现。
    expect(overlay!.getAttribute('class')).toBe('overlay extra-cls')
  })

  it('卸载后 body 上不残留遮罩节点', async () => {
    const wrapper = track(mount(Host, { attachTo: document.body }))
    await nextTick()
    expect(document.body.querySelector('.overlay')).not.toBeNull()
    wrapper.unmount()
    await nextTick()
    expect(document.body.querySelector('.overlay')).toBeNull()
  })
})
