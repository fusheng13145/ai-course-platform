import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import TreeView from '../TreeView.vue'

/**
 * Task 12：TreeView（唯一调用点 UmDetailDialog.vue:44，权限树）现状快照。
 * 契约来源（TreeView.vue 现状行号）：
 * - props：nodes(必填) / selectedKey / depth / opsMenu(Function, 默认 null)，emit：select（:3-9）
 * - 展开态是**每层实例各自**的内部 Set（:10），不受 props 控制、也不向上同步；
 *   组件没有 expose，父组件读不到。所以「展开状态」在多实例间不复用，Phase 5 别把它
 *   当成可持久化字段来动。
 * - 递归靠 SFC 文件名自引用（:74），子层拿到 depth+1 与同一个 selectedKey，
 *   子层的 select 由 :79 的 @select="emit('select', $event)" 逐层向上转发。
 * - 类名：根 .tree、节点 .tree-node、行 .tree-row(.selected)、尖角 .tree-caret(.open/.leaf)、
 *   图标 .tree-ico、标签 .tree-label(title=n.label)、计数 .tree-count、
 *   标记 .tag.<tagCls>、操作位 .tree-extra > .t-icon-btn、子层容器 .tree-children(.open) > .tree-indent。
 *
 * 与 CollapsePanel 同理：模板 :38-39 有前置注释 → 多根 Fragment → wrapper.element 落到挂载
 * 容器、classes() 恒为 []（vue-test-utils.cjs.js:7788-7791 与 :7304-7311）。本文件一律用
 * get('.tree') / get('.tree-row') 取真实元素。
 */

const TREE = [
  { key: 'p', label: '课程组', children: [{ key: 'c', label: '课程标准', count: 0 }] },
  { key: 'l', label: '独立文件', tag: '新', tagCls: 'green' },
]

function mountTree(props: Record<string, unknown> = {}) {
  return mount(TreeView, { props: { nodes: TREE, ...props } })
}

describe('TreeView 现状', () => {
  it('递归渲染：DOM 顺序为 父行 → 子行 → 叶行；空 nodes 只剩 .tree 容器', () => {
    const w = mountTree()
    expect(w.findAll('.tree-row').map((r) => r.get('.tree-label').text())).toEqual([
      '课程组',
      '课程标准',
      '独立文件',
    ])
    // label 同时进 title，长名省略靠的是它
    expect(w.findAll('.tree-label')[0].attributes('title')).toBe('课程组')
    expect(w.findAll('.tree-node')).toHaveLength(3)

    const empty = mountTree({ nodes: [] })
    expect(empty.find('.tree').exists()).toBe(true)
    expect(empty.findAll('.tree-node')).toHaveLength(0)
  })

  it('selectedKey 命中才挂 selected，递归子层同样生效；未命中时一个都不高亮', () => {
    const w = mountTree({ selectedKey: 'c' })
    const rows = w.findAll('.tree-row')
    expect(rows[1].classes()).toContain('selected')
    expect(rows[0].classes()).not.toContain('selected')
    expect(rows[2].classes()).not.toContain('selected')
    // 选中项的图标位被内联上色（:52）
    expect(rows[1].get('.tree-ico').attributes('style') ?? '').toContain('color: var(--accent)')
    expect(mountTree().findAll('.tree-row.selected')).toHaveLength(0)
  })

  it('叶子 caret 挂 leaf、有 children 的不挂；children:[] 也算叶子且不出 .tree-children', () => {
    const w = mountTree()
    const carets = w.findAll('.tree-caret')
    expect(carets[0].classes()).not.toContain('leaf')
    expect(carets[1].classes()).toContain('leaf')
    expect(carets[2].classes()).toContain('leaf')
    expect(w.findAll('.tree-children')).toHaveLength(1)

    const emptyDir = mountTree({ nodes: [{ key: 'e', label: '空目录', children: [] }] })
    expect(emptyDir.get('.tree-caret').classes()).toContain('leaf')
    expect(emptyDir.find('.tree-children').exists()).toBe(false)
  })

  it('点 .tree-caret 只切展开态（caret 与 .tree-children 同时挂 open），不 emit select', async () => {
    const w = mountTree()
    expect(w.get('.tree-children').classes()).not.toContain('open')

    await w.findAll('.tree-caret')[0].trigger('click')
    expect(w.findAll('.tree-caret')[0].classes()).toContain('open')
    expect(w.get('.tree-children').classes()).toContain('open')

    await w.findAll('.tree-caret')[0].trigger('click')
    expect(w.get('.tree-children').classes()).not.toContain('open')
    // :47 的 @click.stop 挡住了行的 select：展开不是选中
    expect(w.emitted('select')).toBeUndefined()
  })

  it('点 .tree-row emit select，载荷是整个节点对象（不是 key）', async () => {
    const w = mountTree()
    await w.findAll('.tree-row')[2].trigger('click')
    expect(w.emitted('select')).toEqual([[TREE[1]]])
  })

  it('子层 select 经 @select 逐层转发到根 wrapper；子层拿到 depth+1 与同一个 selectedKey', async () => {
    const w = mountTree({ selectedKey: 'p' })
    await w.findAll('.tree-row')[1].trigger('click')
    expect(w.emitted('select')).toEqual([[TREE[0].children[0]]])

    // findAllComponents 是从 currentComponent.subTree 起搜的
    // （vue-test-utils.cjs.js:7280-7289 → find(currentComponent.subTree, selector)），
    // 所以它只返回**后代**实例、不含根自己：这里就是那一个递归子层。
    // 收起状态（未点 caret）下子层依然已挂载——折叠是 .tree-children 的 CSS，不是 v-if。
    const nested = w.findAllComponents(TreeView)
    expect(nested).toHaveLength(1)
    expect(nested[0].props('depth')).toBe(1)
    expect(nested[0].props('selectedKey')).toBe('p')
    expect(nested[0].props('nodes')).toEqual([TREE[0].children[0]])
  })

  it('count 用 != null 判定（0 也渲染）；tag 有值才渲染 .tag 并带 tagCls', () => {
    const w = mountTree()
    expect(w.findAll('.tree-count').map((c) => c.text())).toEqual(['0'])
    expect(w.get('.tag').classes()).toEqual(['tag', 'green'])
    expect(w.get('.tag').text()).toBe('新')

    const bare = mountTree({ nodes: [{ key: 'x', label: '无附加' }] })
    expect(bare.findAll('.tree-count')).toHaveLength(0)
    expect(bare.findAll('.tag')).toHaveLength(0)
  })

  it('ops/opsMenu 只决定 .tree-extra 是否出现：按钮无独立处理器，点它冒泡成 select，opsMenu 永不被调用', async () => {
    const opsMenu = vi.fn()
    expect(mountTree().find('.tree-extra').exists()).toBe(false)

    const w = mountTree({ nodes: [{ key: 'x', label: '带操作', ops: true }], opsMenu })
    expect(w.get('.tree-extra .t-icon-btn').find('svg').exists()).toBe(true)
    await w.get('.tree-extra .t-icon-btn').trigger('click')
    // 模板里这个按钮没有任何 @click（:65-69），点击冒泡到 .tree-row 变成 select；
    // opsMenu 只是 `v-if="n.ops || opsMenu"` 的渲染条件（:64），从未被调用。
    expect(w.emitted('select')).toHaveLength(1)
    expect(opsMenu).not.toHaveBeenCalled()

    const onlyMenu = mountTree({ nodes: [{ key: 'x', label: '仅 opsMenu' }], opsMenu })
    expect(onlyMenu.find('.tree-extra').exists()).toBe(true)
  })

  it('现状钉：.tree-ico 的 path d 就是 icon 名字符串本身，未经 ICONS 表查表', () => {
    const w = mountTree()
    // :53 是 ico(n.icon || (hasKids ? 'folder' : 'file'))，而 ico(d) 把它当 path 用，
    // 所以落地的是 <path d="folder"> 这种无效路径数据——图标在今天其实是画不出来的。
    // caret 走的是另一条路：:48 传的是 ICONS.chevronR（真实 path）。
    expect(w.findAll('.tree-ico')[0].get('path').attributes('d')).toBe('folder')
    expect(w.findAll('.tree-ico')[1].get('path').attributes('d')).toBe('file')
    expect(w.findAll('.tree-ico')[2].get('path').attributes('d')).toBe('file')
    expect(w.findAll('.tree-caret')[0].get('path').attributes('d')).toBe('m9 18 6-6-6-6')
    // 显式传 icon 也一样直落：:53 传的是名字，而 ico(d) 把它当 path 用
    const starred = mountTree({ nodes: [{ key: 'i', label: 'I', icon: 'star' }] })
    expect(starred.get('.tree-ico path').attributes('d')).toBe('star')
    // 本用例是「已知缺陷」的锁：Phase 5 若改成查 ICONS 表，这里会红——那是修 bug，
    // 不是兼容性回归，评审时请连同这条注释一起删掉，不要当成「测试写错了」。
  })
})
