import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import TgGradeTable from '@/views/teacher/components/TgGradeTable.vue'

/**
 * TgGradeTable 大列表客户端分页（D2-5）
 *
 * <p>课程成绩报告（getCourseReport）全量返回，原实现整表渲染。现渲染层
 * 客户端分页（每页 20）：小课程零变化；大课程切片渲染、越界收敛；空态保留。</p>
 */

function makeGrades(n) {
  return Array.from({ length: n }, (_, i) => ({
    id: i + 1,
    studentNo: `S${i + 1}`,
    studentName: `学生${i + 1}`,
    homeworkScore: 80,
    examScore: 85,
    totalScore: 83,
    gradeLevel: 'B',
    courseName: '测试课程',
  }))
}

const stubs = {
  'el-table': {
    props: ['data'],
    template: '<div class="tbl-stub">{{ (data || []).length }}</div>',
  },
  'el-table-column': true,
  'el-tag': { template: '<span><slot /></span>' },
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

function mountTable(gradeList) {
  return mount(TgGradeTable, {
    props: { gradeList, getGradeTagType: () => 'info', loading: false },
    global: { stubs, directives: { loading: { mounted() {}, updated() {} } } },
  })
}

describe('TgGradeTable — D2-5 客户端分页', () => {
  it('小列表（≤20）：全量渲染且无分页器', () => {
    const wrapper = mountTable(makeGrades(15))
    expect(wrapper.get('.tbl-stub').text()).toBe('15')
    expect(wrapper.find('.pager-stub').exists()).toBe(false)
  })

  it('大列表（65）：首页 20 行，分页器出现且 total=65', () => {
    const wrapper = mountTable(makeGrades(65))
    expect(wrapper.get('.tbl-stub').text()).toBe('20')
    expect(wrapper.get('.pager-stub').attributes('data-total')).toBe('65')
  })

  it('翻页到第 3 页：渲染第 3 页切片（65 行 → 第 3 页 20 行）', async () => {
    const wrapper = mountTable(makeGrades(65))
    await wrapper.get('.pager-stub').trigger('click')
    expect(wrapper.get('.pager-stub').attributes('data-page')).toBe('3')
    expect(wrapper.get('.tbl-stub').text()).toBe('20')
  })

  it('数据收缩：65 → 8 行后分页器消失，空态条件不受影响', async () => {
    const wrapper = mountTable(makeGrades(65))
    await wrapper.setProps({ gradeList: makeGrades(8) })
    expect(wrapper.find('.pager-stub').exists()).toBe(false)
    expect(wrapper.get('.tbl-stub').text()).toBe('8')
    expect(wrapper.find('.empty-stub').exists()).toBe(false)
  })

  it('空列表：空态展示且无分页器', () => {
    const wrapper = mountTable([])
    expect(wrapper.find('.pager-stub').exists()).toBe(false)
    expect(wrapper.get('.empty-stub').text()).toBe('暂无成绩数据')
  })
})
