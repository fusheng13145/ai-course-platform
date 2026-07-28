import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'
import Login from '../Login.vue'

// Mock router
vi.mock('vue-router', () => ({
  useRouter: () => ({
    push: vi.fn(),
  }),
  useRoute: () => ({
    query: {},
  }),
  createRouter: vi.fn(),
  createWebHistory: vi.fn(),
}))

// Mock auth store
const mockLogin = vi.fn()
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    isLoading: false,
    login: mockLogin,
    getDefaultRoute: () => '/student/dashboard',
  }),
}))

// Mock element-plus
vi.mock('element-plus', () => ({
  ElMessage: {
    success: vi.fn(),
    error: vi.fn(),
  },
}))

// Mock ThemeSwitcher component
vi.mock('@/components/ThemeSwitcher.vue', () => ({
  default: {
    name: 'ThemeSwitcher',
    template: '<div class="theme-switcher-mock"></div>',
  },
}))

describe('LoginForm 表单验证', () => {
  let wrapper
  let pinia

  beforeEach(async () => {
    pinia = createPinia()
    setActivePinia(pinia)
    mockLogin.mockReset()

    wrapper = mount(Login, {
      global: {
        plugins: [pinia],
        stubs: {
          ElForm: true,
          ElFormItem: true,
          ElInput: true,
          ElButton: true,
          ElCheckbox: true,
          ElIcon: true,
          ThemeSwitcher: true,
          RouterLink: true,
        },
      },
    })
    await nextTick()
  })

  it('组件正常渲染', () => {
    expect(wrapper.exists()).toBe(true)
  })

  it('验证规则要求用户名必填', () => {
    const component = wrapper.vm
    const rules = component.rules
    expect(rules.username).toBeDefined()
    expect(rules.username[0].required).toBe(true)
    expect(rules.username[0].message).toBe('请输入用户名')
    expect(rules.username[0].trigger).toBe('blur')
  })

  it('验证规则要求密码必填', () => {
    const component = wrapper.vm
    const rules = component.rules
    expect(rules.password).toBeDefined()
    expect(rules.password[0].required).toBe(true)
    expect(rules.password[0].message).toBe('请输入密码')
    expect(rules.password[0].trigger).toBe('blur')
  })

  it('表单初始状态: 用户名为空', () => {
    const component = wrapper.vm
    expect(component.form.username).toBe('')
  })

  it('表单初始状态: 密码为空', () => {
    const component = wrapper.vm
    expect(component.form.password).toBe('')
  })

  it('表单初始状态: 记住我为true', () => {
    const component = wrapper.vm
    expect(component.form.remember).toBe(true)
  })

  it('表单数据可以修改', async () => {
    const component = wrapper.vm
    component.form.username = 'testuser'
    component.form.password = 'TestPass123'
    component.form.remember = false

    await nextTick()

    expect(component.form.username).toBe('testuser')
    expect(component.form.password).toBe('TestPass123')
    expect(component.form.remember).toBe(false)
  })

  it('formRef初始为null', () => {
    const component = wrapper.vm
    expect(component.formRef).toBe(null)
  })

  it('登录按钮显示正确文本', () => {
    const html = wrapper.html()
    // 文件包含"登 录"文本（有空格）
    expect(html).toContain('登录')
  })

  it('登录函数可以处理校验失败', async () => {
    const component = wrapper.vm
    // formRef 为 null 时 handleLogin 应直接 return
    component.formRef = null
    const result = await component.handleLogin()
    expect(result).toBeUndefined()
  })
})
