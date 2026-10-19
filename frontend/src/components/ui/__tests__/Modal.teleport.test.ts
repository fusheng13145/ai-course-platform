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
 * `transform` / `filter` / `backdrop-filter` / `will-change` 祖先包含。本仓实测两处**今日就生效**
 * （锚点一律给选择器/类名，不给行号——下面点名的两个视图与两条 CSS 规则都是 Phase 1–3 的改动对象）：
 * - `views/student/components/SdCourseGrid.vue` 里嵌在该组件模板根
 *   `<section class="l1-card animate-in d2">` 之内的 `<Modal narrow>`；`.animate-in` 是
 *   `animation: rise-in 0.42s var(--ease-out) both`（design-system.css 的 `.animate-in` 规则），
 *   `rise-in` 的 `to` 帧是 `transform: translateY(0)` —— `both` 是**填充**，动画跑完这一帧永久留在
 *   计算值里，而 `translateY(0)` ≠ `none` ⇒ 包含块永久存在。`.l1-card` 还带 `overflow: hidden`，
 *   于是 `inset: 0` 的遮罩被裁进卡片，表现就是「弹窗缩在卡片中间」。
 * - `views/teacher/tools/KnowledgeManagement.vue` 页面根 `<div class="fade-in">` 之下的
 *   `<ExcelImportDialog>`（其模板根即 `<Modal wide>`）；`.fade-in` 是 `animation: fadeIn .35s ... forwards`
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
    // Task 8 之前它们落在根 `.overlay` 上，所以 Modal.vue 显式写了 `v-bind="$attrs"` 把**原落点**保住
    // （挪到 `.modal` 会改变 class 的落点，没采纳；`inheritAttrs:false` 的角色见 Modal.vue 注释——
    // 它是保险不是承重，Teleport 根本来就没有可抑制的运行时二次套用）。
    // 现存 13 个调用点一个都不传 class/data-*，这条钉的是 Phase 4 的 65 个迁移点。
    track(
      mount(Modal, {
        props: { wide: '' },
        attrs: { 'data-test': 'x', class: 'extra-cls' },
        slots: { title: 'T' },
        attachTo: document.body,
      }),
    )
    await nextTick()
    const overlay = document.body.querySelector('.overlay')
    const modal = document.body.querySelector('.modal')
    expect(overlay).not.toBeNull()
    expect(modal).not.toBeNull()
    expect(overlay!.getAttribute('data-test')).toBe('x')
    expect(overlay!.classList.contains('extra-cls')).toBe(true)
    /**
     * 「只落一份」用**出现次数**判，不用整串字符串比：旧写法 `toBe('overlay extra-cls')` 把 Vue 的
     * class 合并顺序也钉成了契约（`mergeProps` 先静后绑不是公开承诺，编译器等价改写就会假红），
     * 而它要防的其实是"同一份 class 落两遍"。次数写法保住原意又与顺序无关；
     * 变异实测（两轮都实跑过，还原后回到 10 files / 97 tests）：
     * - 静态 class 写成 `class="overlay overlay"` ⇒ 上面 `times(overlay!, 'overlay')` 那条报
     *   `expected 2 to be 1`（修复轮 2 复跑，报错落在那一行）。
     * - 卸掉模板根 `<Teleport>` 再翻 `inheritAttrs: true`（元素根世界）⇒ `times(overlay!, 'extra-cls')`
     *   那条同样报 `expected 2 to be 1`；这是 `Modal.vue` 注释里残余价值 (b) 的对偶实验。
     * ⚠️ 反过来，"给 `.overlay` 再叠第二个 `v-bind="$attrs"`"**不是**这两条断言的可达对偶实验：它在编译期就是
     * `SyntaxError: Duplicate attribute`，用例根本不跑（第一轮记为 M8 并作废，`acecd4a` 提交信息里也写了这条）。
     */
    const times = (el: Element, token: string) =>
      el.getAttribute('class')!.split(/\s+/).filter((c) => c === token).length
    // .overlay：自己的类与透传的类各一份，没有第二遍。
    expect(times(overlay!, 'overlay')).toBe(1)
    expect(times(overlay!, 'extra-cls')).toBe(1)
    // `.modal` 这一侧此前没钉，由修复轮的 commit `acecd4a` 补上（可 `git show acecd4a --
    // frontend/src/components/ui/__tests__/Modal.teleport.test.ts` 复核）：透传属性**不该**出现在它身上
    // （落点裁定是 .overlay），
    // 而它自己的 class（静态 `modal` + `sizeClass` 的 wide）也只落一遍。
    expect(times(modal!, 'extra-cls')).toBe(0)
    expect(modal!.getAttribute('data-test')).toBeNull()
    expect(times(modal!, 'modal')).toBe(1)
    expect(times(modal!, 'wide')).toBe(1)
    // 尺寸档已是 prop，不再是 attr ⇒ 不该跟着透传落到 .overlay（Modal.vue 注释里的那条旧行为对比）。
    expect(times(overlay!, 'wide')).toBe(0)
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
