import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import CctStudentTable from '@/components/course/CctStudentTable.vue'
import ClsStudentTable from '@/components/course/ClsStudentTable.vue'
import CgtGradeTable from '@/components/course/CgtGradeTable.vue'

/**
 * 课程域三张大表客户端分页（D2-5 第三批）
 *
 * <p>CctStudentTable（学生提交统计）/ ClsStudentTable（课堂学情）/
 * CgtGradeTable（课程成绩）均按课程人数伸缩（大课程数百行）。三表同一原语
 * 接入，本文件锁定共同语义：小列表零变化、大列表切片、翻页、收缩收敛、空态保留。</p>
 */

function makeRows(n) {
  return Array.from({ length: n }, (_, i) => ({
    id: i + 1,
    studentName: `学生${i + 1}`,
    studentId: `S${i + 1}`,
    studentNo: `S${i + 1}`,
    totalScore: 80,
    gradeLevel: 'B',
  }))
}

const stubs = {
  'el-table': {
    props: ['data'],
    // 计数独立 span，并暴露 #empty 插槽供空态断言
    template:
      '<div class="tbl-stub"><span class="tbl-count">{{ (data || []).length }}</span><slot name="empty" /></div>',
  },
  'el-table-column': true,
  'el-pagination': {
    props: ['total', 'pageSize', 'currentPage'],
    emits: ['current-change'],
    template:
      '<div class="pager-stub" :data-total="total" :data-page="currentPage" @click="$emit(\'current-change\', 3)" />',
  },
  'el-progress': true,
  'el-tag': { template: '<span><slot /></span>' },
  'el-button': { template: '<button><slot /></button>' },
  EmptyState: { props: ['title'], template: '<div class="empty-stub">{{ title }}</div>' },
}

function mountAny(comp, props) {
  return mount(comp, {
    props,
    global: { stubs, directives: { loading: { mounted() {}, updated() {} } } },
  })
}

const rowKey = {
  CctStudentTable: 'data',
  ClsStudentTable: 'filteredStudents',
  CgtGradeTable: 'filteredGrades',
}

describe.each([
  ['CctStudentTable', CctStudentTable, '暂无学生数据'],
  ['ClsStudentTable', ClsStudentTable, '暂无学生数据'],
  ['CgtGradeTable', CgtGradeTable, '暂无成绩数据'],
])('%s — D2-5 客户端分页', (name, comp, emptyTitle) => {
  it(`小列表（≤20）：全量渲染且无分页器`, () => {
    const wrapper = mountAny(comp, { [rowKey[name]]: makeRows(10), loading: false })
    expect(wrapper.get('.tbl-count').text()).toBe('10')
    expect(wrapper.find('.pager-stub').exists()).toBe(false)
  })

  it('大列表（45）：首页 20 行，分页器出现且 total=45', () => {
    const wrapper = mountAny(comp, { [rowKey[name]]: makeRows(45), loading: false })
    expect(wrapper.get('.tbl-count').text()).toBe('20')
    expect(wrapper.get('.pager-stub').attributes('data-total')).toBe('45')
  })

  it('翻页到第 3 页：渲染末页切片（45 → 5 行）', async () => {
    const wrapper = mountAny(comp, { [rowKey[name]]: makeRows(45), loading: false })
    await wrapper.get('.pager-stub').trigger('click')
    expect(wrapper.get('.pager-stub').attributes('data-page')).toBe('3')
    expect(wrapper.get('.tbl-count').text()).toBe('5')
  })

  it('数据收缩：45 → 8 行后分页器消失且全量渲染', async () => {
    const wrapper = mountAny(comp, { [rowKey[name]]: makeRows(45), loading: false })
    await wrapper.setProps({ [rowKey[name]]: makeRows(8) })
    expect(wrapper.find('.pager-stub').exists()).toBe(false)
    expect(wrapper.get('.tbl-count').text()).toBe('8')
  })

  it('空列表：空态保留原语义且无分页器', () => {
    const wrapper = mountAny(comp, { [rowKey[name]]: [], loading: false })
    expect(wrapper.find('.pager-stub').exists()).toBe(false)
    expect(wrapper.get('.empty-stub').text()).toBe(emptyTitle)
  })
})
