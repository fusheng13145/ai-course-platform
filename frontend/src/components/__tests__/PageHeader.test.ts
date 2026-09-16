import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'

const PageHeader = (await import('@/components/PageHeader.vue')).default

describe('PageHeader', () => {
  it('渲染页面头部容器', () => {
    const wrapper = mount(PageHeader, { props: { title: 'Test Page' } })
    expect(wrapper.find('.pg-head').exists()).toBe(true)
  })

  it('渲染 title 文本', () => {
    const wrapper = mount(PageHeader, { props: { title: 'Test Page' } })
    expect(wrapper.find('.pg-title').text()).toBe('Test Page')
  })

  it('提供 subtitle 时渲染副标题', () => {
    const wrapper = mount(PageHeader, {
      props: { title: 'Test Page', subtitle: 'This is a subtitle' },
    })
    expect(wrapper.find('.pg-sub').exists()).toBe(true)
    expect(wrapper.find('.pg-sub').text()).toBe('This is a subtitle')
  })

  it('未提供 subtitle 时不渲染副标题', () => {
    const wrapper = mount(PageHeader, { props: { title: 'Test Page' } })
    expect(wrapper.find('.pg-sub').exists()).toBe(false)
  })

  it('始终渲染图标块（默认 grid 图标，未知图标回退 dot）', () => {
    const wrapper = mount(PageHeader, { props: { title: 'Test Page' } })
    expect(wrapper.find('.pg-ico').exists()).toBe(true)
    expect(wrapper.find('.pg-ico').html()).toContain('<svg')

    const emptyIcon = mount(PageHeader, { props: { title: 'Test Page', icon: '' } })
    expect(emptyIcon.find('.pg-ico').exists()).toBe(true)
  })

  it('渲染 actions 插槽内容', () => {
    const wrapper = mount(PageHeader, {
      props: { title: 'Test Page' },
      slots: { default: '<button class="action-btn">Create</button>' },
    })
    const action = wrapper.find('.pg-actions .action-btn')
    expect(action.exists()).toBe(true)
    expect(action.text()).toBe('Create')
  })

  it('默认带渐变分隔线（rule），rule=false 时移除', () => {
    const wrapper = mount(PageHeader, { props: { title: 'Test Page' } })
    expect(wrapper.find('.pg-head.no-rule').exists()).toBe(false)

    const noRule = mount(PageHeader, { props: { title: 'Test Page', rule: false } })
    expect(noRule.find('.pg-head.no-rule').exists()).toBe(true)
  })

  it('compact 模式添加 compact 类', () => {
    const wrapper = mount(PageHeader, { props: { title: 'Test Page', compact: true } })
    expect(wrapper.find('.pg-head.compact').exists()).toBe(true)
  })

  it('空 title 正常渲染', () => {
    const wrapper = mount(PageHeader, { props: { title: '' } })
    expect(wrapper.find('.pg-title').text()).toBe('')
  })
})
