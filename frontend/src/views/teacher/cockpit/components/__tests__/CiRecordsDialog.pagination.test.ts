import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import CiRecordsDialog from '@/views/teacher/cockpit/components/CiRecordsDialog.vue'

/**
 * CiRecordsDialog 签到记录客户端分页（D2-5）
 *
 * <p>签到记录按课程人数无界（大课程数百条），渲染层客户端分页（每页 20）：
 * ≤20 条零变化；超出切片渲染且出现分页器；数据收缩收敛；空态保留。</p>
 */

function makeRecords(n) {
  return Array.from({ length: n }, (_, i) => ({
    studentName: `学生${i + 1}`,
    status: 'SIGNED',
    time: '10:00',
  }))
}

const stubs = {
  Modal: { template: '<div><slot /></div>' },
  'el-table': {
    props: ['data'],
    template: '<div class="tbl-stub">{{ (data || []).length }}</div>',
  },
  'el-table-column': true,
  'el-tag': { template: '<span><slot /></span>' },
  'el-pagination': {
    props: ['total', 'pageSize', 'currentPage'],
    emits: ['current-change'],
    template: '<div class="pager-stub" :data-total="total" @click="$emit(\'current-change\', 3)" />',
  },
}

function mountDialog(records) {
  return mount(CiRecordsDialog, {
    props: { modelValue: true, records },
    global: { stubs },
  })
}

describe('CiRecordsDialog — D2-5 客户端分页', () => {
  it('小列表（≤20）：全量渲染且无分页器', () => {
    const wrapper = mountDialog(makeRecords(9))
    expect(wrapper.get('.tbl-stub').text()).toBe('9')
    expect(wrapper.find('.pager-stub').exists()).toBe(false)
  })

  it('大列表（45）：首页 20 条，分页器出现且 total=45', () => {
    const wrapper = mountDialog(makeRecords(45))
    expect(wrapper.get('.tbl-stub').text()).toBe('20')
    expect(wrapper.get('.pager-stub').attributes('data-total')).toBe('45')
  })

  it('翻页到第 3 页：渲染末页切片（45 → 5 条）', async () => {
    const wrapper = mountDialog(makeRecords(45))
    await wrapper.get('.pager-stub').trigger('click')
    expect(wrapper.get('.tbl-stub').text()).toBe('5')
  })

  it('数据收缩：45 → 8 条后分页器消失', async () => {
    const wrapper = mountDialog(makeRecords(45))
    await wrapper.setProps({ records: makeRecords(8) })
    expect(wrapper.find('.pager-stub').exists()).toBe(false)
    expect(wrapper.get('.tbl-stub').text()).toBe('8')
  })

  it('空列表：分页器隐藏、表格空态由 empty-text 保留', () => {
    const wrapper = mountDialog([])
    expect(wrapper.find('.pager-stub').exists()).toBe(false)
    expect(wrapper.get('.tbl-stub').text()).toBe('0')
  })
})
