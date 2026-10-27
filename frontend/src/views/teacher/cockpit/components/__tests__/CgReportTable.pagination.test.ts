import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import CgReportTable from '@/views/teacher/cockpit/components/CgReportTable.vue'

/**
 * CgReportTable 课程成绩报告客户端分页（D2-5 第三批）
 *
 * <p>成绩报告按课程人数伸缩（大课程数百行），max-height=560 内整表渲染。
 * 现渲染层客户端分页（每页 20）：小课程零变化；大课程切片 + 分页器；
 * 空态与「无成绩方案」空态保留。</p>
 */

function makeReport(n) {
  return Array.from({ length: n }, (_, i) => ({
    studentName: `学生${i + 1}`,
    finalScore: 85,
  }))
}

const stubs = {
  'el-table': {
    props: ['data'],
    template:
      '<div class="tbl-stub"><span class="tbl-count">{{ (data || []).length }}</span></div>',
  },
  'el-table-column': true,
  'el-pagination': {
    props: ['total', 'currentPage'],
    emits: ['current-change'],
    template:
      '<div class="pager-stub" :data-total="total" :data-page="currentPage" @click="$emit(\'current-change\', 3)" />',
  },
  'el-button': { template: '<button><slot /></button>' },
  EmptyState: { props: ['title'], template: '<div class="empty-stub">{{ title }}</div>' },
}

function mountTable(report) {
  return mount(CgReportTable, {
    props: { policy: { id: 1 }, report, reportLoading: false },
    global: { stubs, directives: { loading: { mounted() {}, updated() {} } } },
  })
}

describe('CgReportTable — D2-5 客户端分页', () => {
  it('小报告（≤20）：全量渲染且无分页器', () => {
    const wrapper = mountTable(makeReport(12))
    expect(wrapper.get('.tbl-count').text()).toBe('12')
    expect(wrapper.find('.pager-stub').exists()).toBe(false)
  })

  it('大报告（65）：首页 20 行且分页器 total=65', () => {
    const wrapper = mountTable(makeReport(65))
    expect(wrapper.get('.tbl-count').text()).toBe('20')
    expect(wrapper.get('.pager-stub').attributes('data-total')).toBe('65')
  })

  it('翻页到第 3 页：渲染第 3 页切片（65 → 20 行）', async () => {
    const wrapper = mountTable(makeReport(65))
    await wrapper.get('.pager-stub').trigger('click')
    expect(wrapper.get('.pager-stub').attributes('data-page')).toBe('3')
    expect(wrapper.get('.tbl-count').text()).toBe('20')
  })

  it('空报告：表格空态保留且无分页器', () => {
    const wrapper = mountTable([])
    expect(wrapper.find('.pager-stub').exists()).toBe(false)
    expect(wrapper.find('.empty-stub').exists()).toBe(true)
    expect(wrapper.get('.empty-stub').text()).toBe('暂无学生成绩')
  })
})
