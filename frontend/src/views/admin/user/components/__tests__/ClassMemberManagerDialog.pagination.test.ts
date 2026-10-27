import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ClassMemberManagerDialog from '@/views/admin/user/components/ClassMemberManagerDialog.vue'

/**
 * ClassMemberManagerDialog 双表客户端分页（D2-5）
 *
 * <p>成员表（班级学生）与候选学生表（全量学生池）均为无界数据。渲染层各持
 * 独立分页实例（互不干扰）：≤20 行零变化；超出切片渲染；空态保留。</p>
 */

function makeRows(n, prefix) {
  return Array.from({ length: n }, (_, i) => ({
    id: i + 1,
    username: `${prefix}${i + 1}`,
    fullName: `学生${i + 1}`,
  }))
}

const stubs = {
  // 批 3b 迁移后弹层由 ui/Modal 承载；本套件只断分页逻辑，Modal 原位渲染插槽的桩与原 el-dialog 桩等价
  Modal: { template: '<div><slot /></div>' },
  'el-table': {
    props: ['data'],
    // 计数放独立 span（避免与空态插槽文本混杂），并暴露 #empty 供空态断言
    template:
      '<div class="tbl-stub"><span class="tbl-count">{{ (data || []).length }}</span><slot name="empty" /></div>',
  },
  'el-table-column': true,
  'el-input': { template: '<input />' },
  'el-button': { template: '<button><slot /></button>' },
  'el-icon': { template: '<i><slot /></i>' },
  'el-pagination': {
    props: ['total', 'pageSize', 'currentPage'],
    emits: ['current-change'],
    template:
      '<div class="pager-stub" :data-total="total" @click="$emit(\'current-change\', 3)" />',
  },
  EmptyState: { props: ['title'], template: '<div class="empty-stub">{{ title }}</div>' },
}

function mountDialog(props = {}) {
  return mount(ClassMemberManagerDialog, {
    props: {
      modelValue: true,
      currentClass: { id: 1, name: '计算机2201班' },
      studentList: [],
      addVisible: true,
      filteredCandidates: [],
      ...props,
    },
    global: { stubs, directives: { loading: { mounted() {}, updated() {} } } },
  })
}

describe('ClassMemberManagerDialog — D2-5 双表分页', () => {
  it('两表均 ≤20：全量渲染且无分页器', () => {
    const wrapper = mountDialog({
      studentList: makeRows(8, 'M'),
      filteredCandidates: makeRows(12, 'C'),
    })
    const counts = wrapper.findAll('.tbl-count')
    expect(counts[0].text()).toBe('8')
    expect(counts[1].text()).toBe('12')
    expect(wrapper.find('.pager-stub').exists()).toBe(false)
  })

  it('两表均超页：各自切片 20 行，各出现分页器（total 独立）', () => {
    const wrapper = mountDialog({
      studentList: makeRows(35, 'M'),
      filteredCandidates: makeRows(45, 'C'),
    })
    const counts = wrapper.findAll('.tbl-count')
    expect(counts[0].text()).toBe('20')
    expect(counts[1].text()).toBe('20')
    const pagers = wrapper.findAll('.pager-stub')
    expect(pagers).toHaveLength(2)
    expect(pagers[0].attributes('data-total')).toBe('35')
    expect(pagers[1].attributes('data-total')).toBe('45')
  })

  it('翻页互不干扰：候选表翻到第 3 页不影响成员表', async () => {
    const wrapper = mountDialog({
      studentList: makeRows(35, 'M'),
      filteredCandidates: makeRows(65, 'C'),
    })
    const pagers = wrapper.findAll('.pager-stub')
    await pagers[1].trigger('click') // 候选表 → 第 3 页（65 → 第 3 页 20 行）
    const counts = wrapper.findAll('.tbl-count')
    expect(counts[0].text()).toBe('20') // 成员表仍第 1 页
    expect(counts[1].text()).toBe('20')
  })

  it('空列表：两表空态保留原语义', () => {
    const wrapper = mountDialog()
    const empties = wrapper.findAll('.empty-stub')
    expect(empties[0].text()).toBe('该班级暂无学生')
    expect(empties[1].text()).toBe('无可添加学生')
  })
})
