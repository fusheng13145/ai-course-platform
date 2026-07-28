import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'

// We need to mock Element Plus icon component
vi.mock('element-plus', () => ({
  ElIcon: {
    name: 'ElIcon',
    template: '<span class="el-icon-mock"><slot /></span>',
  },
}))

const PageHeader = (await import('@/components/PageHeader.vue')).default

describe('PageHeader', () => {
  it('renders title prop', () => {
    const wrapper = mount(PageHeader, {
      props: { title: 'Test Page' },
    })
    expect(wrapper.find('.page-header__title').text()).toBe('Test Page')
  })

  it('renders subtitle when provided', () => {
    const wrapper = mount(PageHeader, {
      props: { title: 'Test Page', subtitle: 'This is a subtitle' },
    })
    expect(wrapper.find('.page-header__subtitle').exists()).toBe(true)
    expect(wrapper.find('.page-header__subtitle').text()).toBe('This is a subtitle')
  })

  it('does not render subtitle when not provided', () => {
    const wrapper = mount(PageHeader, {
      props: { title: 'Test Page' },
    })
    expect(wrapper.find('.page-header__subtitle').exists()).toBe(false)
  })

  it('renders icon element when icon prop is provided', () => {
    const wrapper = mount(PageHeader, {
      props: { title: 'Test Page', icon: 'Document' },
    })
    // The icon container should be present
    expect(wrapper.find('.page-header__icon').exists()).toBe(true)
  })

  it('does not render icon when icon prop is empty', () => {
    const wrapper = mount(PageHeader, {
      props: { title: 'Test Page', icon: '' },
    })
    expect(wrapper.find('.page-header__icon').exists()).toBe(false)
  })

  it('renders actions slot content', () => {
    const wrapper = mount(PageHeader, {
      props: { title: 'Test Page' },
      slots: {
        actions: '<button class="action-btn">Create</button>',
      },
    })
    expect(wrapper.find('.action-btn').exists()).toBe(true)
    expect(wrapper.find('.action-btn').text()).toBe('Create')
  })

  it('does not render actions area when no slot content', () => {
    const wrapper = mount(PageHeader, {
      props: { title: 'Test Page' },
    })
    expect(wrapper.find('.page-header__actions').exists()).toBe(false)
  })

  it('renders header structure correctly', () => {
    const wrapper = mount(PageHeader, {
      props: { title: 'Dashboard', subtitle: 'Overview' },
    })
    expect(wrapper.find('.page-header').exists()).toBe(true)
    expect(wrapper.find('.page-header__main').exists()).toBe(true)
  })

  it('handles empty string title', () => {
    const wrapper = mount(PageHeader, {
      props: { title: '' },
    })
    expect(wrapper.find('.page-header__title').text()).toBe('')
  })
})
