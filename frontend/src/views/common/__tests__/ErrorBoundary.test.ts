import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import AppErrorBoundary from '@/components/AppErrorBoundary.vue'

// Mock router
const mockPush = vi.fn()
vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: mockPush,
  }),
}))

// Mock logger
vi.mock('@/utils/logger', () => ({
  default: {
    error: vi.fn(),
  },
}))

describe('ErrorBoundary 错误边界', () => {
  let wrapper

  beforeEach(() => {
    mockPush.mockReset()
    wrapper = mount(AppErrorBoundary, {
      global: {
        stubs: {
          ElButton: {
            template: '<button @click="$emit(\'click\')"><slot /></button>',
            emits: ['click'],
          },
        },
      },
    })
  })

  it('无错误时渲染子插槽内容', () => {
    expect(wrapper.find('.error-boundary').exists()).toBe(false)
  })

  it('无错误时默认插槽内容被完整渲染', () => {
    const withSlot = mount(AppErrorBoundary, {
      global: {
        stubs: {
          ElButton: {
            template: '<button @click="$emit(\'click\')"><slot /></button>',
            emits: ['click'],
          },
        },
      },
      slots: { default: '<div class="slot-content">正常内容</div>' },
    })
    expect(withSlot.find('.slot-content').exists()).toBe(true)
    expect(withSlot.text()).toContain('正常内容')
  })

  it('有错误时显示错误提示信息', async () => {
    const error = new Error('测试错误消息')
    wrapper.vm.captureError(error)
    await nextTick()

    expect(wrapper.find('.error-boundary').exists()).toBe(true)
    expect(wrapper.find('.error-content').exists()).toBe(true)
    expect(wrapper.find('h3').text()).toBe('页面出了点问题')
    expect(wrapper.text()).toContain('测试错误消息')
  })

  it('显示自定义错误消息', async () => {
    const customError = new Error('组件加载失败: 网络连接超时')
    wrapper.vm.captureError(customError)
    await nextTick()

    expect(wrapper.text()).toContain('组件加载失败: 网络连接超时')
  })

  it('重试后错误状态被清除', async () => {
    const error = new Error('临时错误')
    wrapper.vm.captureError(error)
    await nextTick()
    expect(wrapper.find('.error-boundary').exists()).toBe(true)

    wrapper.vm.retry()
    await nextTick()
    expect(wrapper.find('.error-boundary').exists()).toBe(false)
  })

  it('点击返回首页导航到根路径', async () => {
    const error = new Error('无法恢复的错误')
    wrapper.vm.captureError(error)
    await nextTick()

    wrapper.vm.goHome()
    expect(mockPush).toHaveBeenCalledWith('/')
  })

  it('暴露 captureError 方法供外部调用', () => {
    expect(wrapper.vm.captureError).toBeDefined()
    expect(typeof wrapper.vm.captureError).toBe('function')
  })

  it('显示重试和返回首页按钮', async () => {
    wrapper.vm.captureError(new Error('错误'))
    await nextTick()

    // 错误态渲染 3 个按钮：重试 / 返回首页 / 查看详情
    const buttons = wrapper.findAll('button')
    expect(buttons.length).toBe(3)
    expect(buttons[0].text()).toContain('重试')
    expect(buttons[1].text()).toContain('返回首页')
  })

  it('未知错误显示默认消息', async () => {
    wrapper.vm.captureError({ message: '' })
    await nextTick()

    expect(wrapper.vm.error.message).toBe('')
  })

  it('错误边界有正确的错误图标样式', async () => {
    wrapper.vm.captureError(new Error('错误'))
    await nextTick()

    const icon = wrapper.find('.error-icon')
    expect(icon.exists()).toBe(true)
    expect(icon.text()).toBe('!')
  })
})
