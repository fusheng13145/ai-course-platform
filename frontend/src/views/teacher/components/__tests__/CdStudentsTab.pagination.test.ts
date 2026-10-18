import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import CdStudentsTab from '@/views/teacher/components/CdStudentsTab.vue'

/**
 * CdStudentsTab 大列表客户端分页（D2-5）
 *
 * <p>课程学生无界（getAllStudents 全量返回），原实现整表渲染。现渲染层
 * 客户端分页（每页 20）：小列表视觉零变化（无分页器）；大列表切片渲染；
 * 数据收缩时页码自动收敛，不出现空白页伪空态。</p>
 */

function makeStudents(n) {
  return Array.from({ length: n }, (_, i) => ({
    id: i + 1,
    studentId: `S${i + 1}`,
    studentName: `学生${i + 1}`,
    progress: 0.5,
    approvalStatus: 'APPROVED',
  }))
}

/** el-table 桩：渲染 data.length 便于断言切片行数；el-pagination 桩：暴露 total 与翻页 */
const stubs = {
  'el-table': {
    props: ['data'],
    template: '<div class="tbl-stub">{{ (data || []).length }}</div>',
  },
  'el-table-column': true,
  'el-progress': true,
  'el-button': { template: '<button><slot /></button>' },
  'el-pagination': {
    props: ['total', 'pageSize', 'currentPage'],
    emits: ['current-change'],
    template:
      '<div class="pager-stub" :data-total="total" :data-page="currentPage" @click="$emit(\'current-change\', 3)">{{ currentPage }}</div>',
  },
  EmptyState: {
    props: ['title', 'desc', 'icon', 'size'],
    template: '<div class="empty-stub">{{ title }}</div>',
  },
}

function mountTab(students) {
  return mount(CdStudentsTab, {
    props: { students, studentsLoading: false },
    global: { stubs, directives: { loading: { mounted() {}, updated() {} } } },
  })
}

describe('CdStudentsTab — D2-5 客户端分页', () => {
  it('小列表（≤20）：全量渲染且无分页器（视觉零变化）', () => {
    const wrapper = mountTab(makeStudents(12))
    expect(wrapper.get('.tbl-stub').text()).toBe('12')
    expect(wrapper.find('.pager-stub').exists()).toBe(false)
  })

  it('大列表（45）：首页仅渲染 20 行，分页器出现且 total=45', () => {
    const wrapper = mountTab(makeStudents(45))
    expect(wrapper.get('.tbl-stub').text()).toBe('20')
    const pager = wrapper.get('.pager-stub')
    expect(pager.attributes('data-total')).toBe('45')
  })

  it('翻页：current-change 到第 3 页后渲染末页切片（45 → 5 行）', async () => {
    const wrapper = mountTab(makeStudents(45))
    expect(wrapper.get('.tbl-stub').text()).toBe('20')
    await wrapper.get('.pager-stub').trigger('click') // 桩固定发 current-change(3)
    expect(wrapper.get('.pager-stub').attributes('data-page')).toBe('3')
    expect(wrapper.get('.tbl-stub').text()).toBe('5')
  })

  it('数据收缩：45 → 12 行后分页器消失且全量渲染', async () => {
    const wrapper = mountTab(makeStudents(45))
    expect(wrapper.find('.pager-stub').exists()).toBe(true)
    await wrapper.setProps({ students: makeStudents(12) })
    expect(wrapper.find('.pager-stub').exists()).toBe(false)
    expect(wrapper.get('.tbl-stub').text()).toBe('12')
  })

  it('空列表：空态保留原语义，无分页器', () => {
    const wrapper = mountTab([])
    expect(wrapper.find('.pager-stub').exists()).toBe(false)
    expect(wrapper.get('.empty-stub').text()).toBe('暂无学生')
  })
})
