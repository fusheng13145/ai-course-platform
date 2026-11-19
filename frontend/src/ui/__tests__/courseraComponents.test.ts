import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import NumberBadge from '../components/NumberBadge.vue'
import Pagination from '../components/Pagination.vue'
import ProgressBar from '../components/ProgressBar.vue'

describe('Coursera 公共组件', () => {
  it('分页器保留上一页 / 下一页 delta 事件与禁用态', async () => {
    const wrapper = mount(Pagination, { props: { page: 2, pageCount: 3 } })
    const buttons = wrapper.findAll('button')
    await buttons[0].trigger('click')
    await buttons[1].trigger('click')
    expect(wrapper.emitted('turn')).toEqual([[-1], [1]])

    await wrapper.setProps({ loading: true })
    expect(buttons[0].attributes('disabled')).toBeDefined()
    expect(buttons[1].attributes('disabled')).toBeDefined()
  })

  it('进度条同步可访问数值并约束展示范围', () => {
    const wrapper = mount(ProgressBar, { props: { value: 108, label: '整体进度' } })
    const bar = wrapper.get('[role="progressbar"]')
    expect(bar.attributes('aria-label')).toBe('整体进度')
    expect(bar.attributes('aria-valuenow')).toBe('100')
    expect(bar.attributes('aria-valuemin')).toBe('0')
    expect(bar.attributes('aria-valuemax')).toBe('100')
  })

  it('编号徽章统一补零且支持 32px 规格', () => {
    const wrapper = mount(NumberBadge, { props: { value: 3, size: 32 } })
    expect(wrapper.text()).toBe('03')
    expect(wrapper.classes()).toContain('number-badge-lg')
  })
})
