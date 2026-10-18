import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import EmptyState from '../EmptyState.vue'

/**
 * D2-3：统一空状态组件单测。
 * 锁定默认文案 / 自定义文案 / 图标回退 / compact 尺寸类名，
 * 保证散落 el-empty+empty-deco 迁移到本组件后行为一致。
 */
describe('EmptyState 统一空状态组件', () => {
  it('默认渲染「暂无内容」标题，无 desc', () => {
    const wrapper = mount(EmptyState)
    expect(wrapper.get('.e-title').text()).toBe('暂无内容')
    expect(wrapper.find('.e-desc').exists()).toBe(false)
    expect(wrapper.classes()).not.toContain('is-compact')
  })

  it('渲染自定义 title 与 desc', () => {
    const wrapper = mount(EmptyState, {
      props: { title: '暂无待办提醒', desc: '创建提醒后这里会显示' },
    })
    expect(wrapper.get('.e-title').text()).toBe('暂无待办提醒')
    expect(wrapper.get('.e-desc').text()).toBe('创建提醒后这里会显示')
  })

  it('已知图标渲染对应 path，未知图标回退 archive', () => {
    const known = mount(EmptyState, { props: { icon: 'bell' } })
    expect(known.get('.e-ico path').attributes('d')).toContain('M6 8a6')

    const unknown = mount(EmptyState, { props: { icon: 'not-exist' } })
    expect(unknown.get('.e-ico path').attributes('d')).toContain('M21 8v13')
  })

  it('size=compact 时挂载 is-compact 类（面板/抽屉内嵌空态）', () => {
    const wrapper = mount(EmptyState, { props: { size: 'compact', title: '暂无异常成绩' } })
    expect(wrapper.classes()).toContain('is-compact')
    expect(wrapper.get('.e-title').text()).toBe('暂无异常成绩')
  })

  it('默认插槽渲染空态 CTA（如「新建」按钮），无插槽时不渲染动作区', () => {
    const withSlot = mount(EmptyState, {
      props: { title: '暂无成绩方案' },
      slots: { default: '<button class="cta">新建成绩方案</button>' },
    })
    expect(withSlot.get('.e-action .cta').text()).toBe('新建成绩方案')

    const noSlot = mount(EmptyState, { props: { title: '暂无' } })
    expect(noSlot.find('.e-action').exists()).toBe(false)
  })
})
