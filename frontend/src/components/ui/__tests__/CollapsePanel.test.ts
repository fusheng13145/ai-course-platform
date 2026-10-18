import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import CollapsePanel from '../CollapsePanel.vue'

/**
 * Task 12：CollapsePanel（9 个调用点）现状快照。
 * 契约来源（CollapsePanel.vue 现状行号）：
 * - props：title / desc / icon / open(默认 true) / flush（:3-9）
 * - 组件**没有 defineEmits**：展开状态是内部 ref + watch(props.open)（:10-14），父组件
 *   拿不到折叠事件。第 3 个用例把「不 emit」钉住——Phase 5 若要加 v-model:open 是兼容性
 *   变更，必须显式评审，不能悄悄加。
 * - 类名：根 l2-panel + open、头 l2-head、尖角 l2-caret、标题 l2-title、描述 l2-desc、
 *   右侧插槽容器 l2-extra no-fold、内容 l2-body > l2-body-inner(flush)（:23-43）。
 * - 折叠靠 CSS（.l2-panel.open .l2-body{max-height:none}，:93-95）而不是 v-if：收起时
 *   内容仍在 DOM 里、表单状态不丢。最后一个用例钉的就是这条。
 *
 * 为什么这里一律走 get('.l2-panel') 而不用 wrapper.classes()：
 * CollapsePanel 的模板在根元素前面留了两行 HTML 注释（:21-22），dev 模式下 Vue 保留注释，
 * 子树成了多根 Fragment。VTU 的 `get element()` 对多根组件返回的是**父容器**
 * （`hasMultipleRoots ? this.parentElement : this.vm.$el`，vue-test-utils.cjs.js:7788-7791，
 * hasMultipleRoots 判定见 :7715-7738），实测 wrapper.element 是挂载用的 `<div data-v-app="">`；
 * 而 classes() 取的就是这个 element 的 classList（:7304-7311）→ 恒为 []。
 * 于是 `expect(wrapper.classes()).not.toContain('open')` 无论组件怎么改都是绿的
 * （正是 Task 6 评审抓到的那类假断言，见 task-6 那条「卸载后不增长」的教训）。
 * find/get 不受影响：它们走 getRootNodes()，会跳过非 Element 根节点
 * （vue-test-utils.cjs.js:7218-7227 与 :7505-7539）。
 */

/** 组件内 ICON 表只有这一个键（:15），其值即 chevron 的 path */
const CHEVRON_PATH = 'm9 18 6-6-6-6'

function mountPanel(props: Record<string, unknown> = {}, slots: Record<string, string> = {}) {
  return mount(CollapsePanel, { props: { title: '筛选', ...props }, slots })
}

/** 根元素类名——见文件头：不能用 wrapper.classes()，那恒为 [] */
function panelClassList(w: ReturnType<typeof mountPanel>) {
  return w.get('.l2-panel').classes()
}

describe('CollapsePanel 现状', () => {
  it('open 缺省为 true：根挂 open 类，caret 内联 rotate(90deg)', () => {
    const w = mountPanel()
    expect(panelClassList(w)).toContain('l2-panel')
    expect(panelClassList(w)).toContain('open')
    expect(w.get('.l2-caret').attributes('style') ?? '').toContain('rotate(90deg)')
  })

  it('open=false 时根不挂 open 类，caret 不带 transform', () => {
    const w = mountPanel({ open: false })
    expect(panelClassList(w)).not.toContain('open')
    expect(panelClassList(w)).toContain('l2-panel')
    expect(w.get('.l2-caret').attributes('style') ?? '').not.toContain('rotate')
  })

  it('点 .l2-head 只翻内部状态：来回切换根类，且组件不 emit 任何事件', async () => {
    const w = mountPanel()
    await w.get('.l2-head').trigger('click')
    expect(panelClassList(w)).not.toContain('open')
    await w.get('.l2-head').trigger('click')
    expect(panelClassList(w)).toContain('open')
    // 组件没有任何 emit 通道：既没有 v-model:open 也没有 toggle。
    // 注意不能写成 expect(w.emitted()).toEqual({}) —— VTU 会把根元素上的原生 DOM 事件
    // 也记进同一张表（attachNativeEventListener 逐个 domEvents 名 addEventListener 后
    // recordEvent，vue-test-utils.cjs.js:7755-7786），trigger('click') 之后 emitted()
    // 必然带一个 click 键，那条断言会永远红、也就永远不红（反向的假断言）。
    // 所以只按「未来最可能被加上的两个事件名」定点检查。
    expect(w.emitted('update:open')).toBeUndefined()
    expect(w.emitted('toggle')).toBeUndefined()
  })

  it('open prop 变化经 watch 回写内部状态（父组件单向控制通道）', async () => {
    const w = mountPanel({ open: true })
    await w.setProps({ open: false })
    expect(panelClassList(w)).not.toContain('open')
    await w.setProps({ open: true })
    expect(panelClassList(w)).toContain('open')
  })

  it('desc 缺省不渲染 .l2-desc；title 即便是空串也渲染 .l2-title（无 v-if）', () => {
    expect(mountPanel().find('.l2-desc').exists()).toBe(false)
    expect(mountPanel({ desc: '按学期过滤' }).get('.l2-desc').text()).toBe('按学期过滤')
    expect(mountPanel({ title: '' }).find('.l2-title').exists()).toBe(true)
  })

  it('icon 有值才多渲染一个图标 span；表内只有 chevronR，任何 icon 名都退化成它', () => {
    expect(mountPanel().findAll('.l2-head > span')).toHaveLength(1)

    const spans = mountPanel({ icon: 'bell' }).findAll('.l2-head > span')
    expect(spans).toHaveLength(2)
    // 第 2 个 span 才是 icon 位（第 1 个是 caret）；查不到键就回退 chevronR（:33）
    expect(spans[1].get('path').attributes('d')).toBe(CHEVRON_PATH)
    expect(spans[0].get('path').attributes('d')).toBe(CHEVRON_PATH)
  })

  it('flush 缺省不挂 flush 类，flush=true 挂到 .l2-body-inner', () => {
    expect(mountPanel().get('.l2-body-inner').classes()).not.toContain('flush')
    expect(mountPanel({ flush: true }).get('.l2-body-inner').classes()).toContain('flush')
  })

  it('默认插槽进 .l2-body-inner、extra 插槽进 .l2-extra；收起时内容仍在 DOM（CSS 折叠非 v-if）', () => {
    const w = mountPanel(
      {},
      { default: '<p class="in">内容</p>', extra: '<button class="ex">操作</button>' },
    )
    expect(w.get('.l2-body-inner .in').text()).toBe('内容')
    expect(w.get('.l2-extra .ex').text()).toBe('操作')

    const closed = mountPanel({ open: false }, { default: '<p class="in">内容</p>' })
    expect(panelClassList(closed)).not.toContain('open')
    expect(closed.find('.l2-body-inner .in').exists()).toBe(true)
    // .l2-extra 容器无 v-if，不传 extra 也照样占位（靠 margin-left:auto 把内容顶到右侧）
    expect(mountPanel().get('.l2-extra').text()).toBe('')
  })
})
