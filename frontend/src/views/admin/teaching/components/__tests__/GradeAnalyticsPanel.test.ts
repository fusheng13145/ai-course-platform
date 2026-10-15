import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import GradeAnalyticsPanel from '../GradeAnalyticsPanel.vue'

/**
 * D2-3：图表区内嵌空态统一为 EmptyState（compact 尺寸）。
 */
function mountPanel(abnormalGrades: unknown[] = []) {
  return mount(GradeAnalyticsPanel, {
    props: {
      distributionOption: { series: [] },
      courseCompareOption: { series: [] },
      abnormalGrades,
    },
    global: {
      stubs: {
        'v-chart': { template: '<div class="chart-stub" />' },
        'el-icon': true,
      },
    },
  })
}

describe('GradeAnalyticsPanel 图表区空态（D2-3）', () => {
  it('无异常成绩：渲染统一 EmptyState（compact 尺寸 + alert 图标语义）', () => {
    const wrapper = mountPanel([])
    const empty = wrapper.get('.empty-state')
    expect(empty.classes()).toContain('is-compact')
    expect(empty.get('.e-title').text()).toBe('暂无异常成绩')
  })

  it('有异常成绩：渲染预警列表，不显示空态', () => {
    const wrapper = mountPanel([
      { studentName: '张三', score: 55, courseName: '高等数学' },
      { studentName: '李四', score: 90, courseName: '线性代数' },
    ])
    expect(wrapper.find('.empty-state').exists()).toBe(false)
    expect(wrapper.findAll('.abnormal-item')).toHaveLength(2)
    expect(wrapper.text()).toContain('张三')
    expect(wrapper.text()).toContain('高等数学')
  })
})
