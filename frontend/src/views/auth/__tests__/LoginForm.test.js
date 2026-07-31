import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'

// vi.hoisted 保证 mock 工厂中的变量在 hoisting 时可用
const mocks = vi.hoisted(() => ({
  mockLogin: vi.fn(),
  mockGetDefaultRoute: vi.fn(() => '/student/dashboard'),
  mockMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  mockPush: vi.fn(),
  state: { isLoading: false, token: '' },
  mockRoute: { query: {} },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.mockPush }),
  useRoute: () => mocks.mockRoute,
  createRouter: vi.fn(),
  createWebHistory: vi.fn(),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    isLoading: mocks.state.isLoading,
    login: mocks.mockLogin,
    getDefaultRoute: mocks.mockGetDefaultRoute,
    token: mocks.state.token,
  }),
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.mockMessage,
}))

vi.mock('@/components/ThemeSwitcher.vue', () => ({
  default: {
    name: 'ThemeSwitcher',
    template: '<div class="theme-switcher-mock"></div>',
  },
}))

import Login from '../Login.vue'

function mountLogin() {
  return mount(Login, {
    global: {
      stubs: {
        'el-form': { template: '<form class="el-form-stub"><slot /></form>' },
        'el-input': {
          props: ['modelValue', 'type', 'placeholder', 'showPassword'],
          emits: ['update:modelValue'],
          template:
            '<input class="el-input-stub" :type="type || \'text\'" :placeholder="placeholder" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
        },
        'el-checkbox': { template: '<label class="el-checkbox-stub"><slot /></label>' },
        RouterLink: { template: '<a><slot /></a>' },
      },
    },
  })
}

describe('LoginForm 表单验证', () => {
  let wrapper

  beforeEach(() => {
    mocks.mockLogin.mockReset()
    mocks.mockPush.mockReset()
    mocks.mockMessage.success.mockReset()
    mocks.mockMessage.error.mockReset()
    mocks.mockMessage.warning.mockReset()
    mocks.state.isLoading = false
    mocks.state.token = ''
    mocks.mockRoute.query = {}

    wrapper = mountLogin()
  })

  it('组件正常渲染', () => {
    expect(wrapper.exists()).toBe(true)
  })

  it('验证规则要求用户名必填', () => {
    const rules = wrapper.vm.rules
    expect(rules.username).toBeDefined()
    expect(rules.username[0].required).toBe(true)
    expect(rules.username[0].message).toBe('请输入用户名')
    expect(rules.username[0].trigger).toBe('blur')
  })

  it('验证规则要求密码必填', () => {
    const rules = wrapper.vm.rules
    expect(rules.password).toBeDefined()
    expect(rules.password[0].required).toBe(true)
    expect(rules.password[0].message).toBe('请输入密码')
    expect(rules.password[0].trigger).toBe('blur')
  })

  it('表单初始状态: 用户名为空', () => {
    expect(wrapper.vm.form.username).toBe('')
  })

  it('表单初始状态: 密码为空', () => {
    expect(wrapper.vm.form.password).toBe('')
  })

  it('表单初始状态: 记住我为 false', () => {
    expect(wrapper.vm.form.remember).toBe(false)
  })

  it('表单数据可以修改', async () => {
    wrapper.vm.form.username = 'testuser'
    wrapper.vm.form.password = 'TestPass123'
    wrapper.vm.form.remember = true

    await nextTick()

    expect(wrapper.vm.form.username).toBe('testuser')
    expect(wrapper.vm.form.password).toBe('TestPass123')
    expect(wrapper.vm.form.remember).toBe(true)
  })

  it('渲染左侧品牌区', () => {
    expect(wrapper.find('.brand-side').exists()).toBe(true)
    expect(wrapper.find('.brand-title').text()).toContain('云枢教学平台')
  })

  it('渲染表单头部文本', () => {
    expect(wrapper.find('.form-header').text()).toContain('欢迎回来')
  })

  it('渲染记住我复选框', () => {
    expect(wrapper.find('.el-checkbox-stub').exists()).toBe(true)
    expect(wrapper.find('.el-checkbox-stub').text()).toContain('记住我')
  })

  it('渲染忘记密码链接', () => {
    expect(wrapper.find('.forgot').exists()).toBe(true)
  })

  it('登录按钮显示正确文本', () => {
    expect(wrapper.find('button.btn-primary').text()).toContain('登 录')
  })

  it('非加载中时按钮可点击', () => {
    expect(wrapper.find('button.btn-primary').attributes('disabled')).toBeUndefined()
  })

  it('加载中时按钮显示"登录中..."', () => {
    mocks.state.isLoading = true
    const loadingWrapper = mountLogin()
    expect(loadingWrapper.find('button.btn-primary').text()).toContain('登录中')
  })

  it('加载中时按钮 disabled', () => {
    mocks.state.isLoading = true
    const loadingWrapper = mountLogin()
    expect(loadingWrapper.find('button.btn-primary').attributes('disabled')).toBeDefined()
  })

  it('加载中时显示加载动画点', () => {
    mocks.state.isLoading = true
    const loadingWrapper = mountLogin()
    expect(loadingWrapper.find('.btn-loader-dot').exists()).toBe(true)
  })

  it('非加载中时无加载动画点', () => {
    expect(wrapper.find('.btn-loader-dot').exists()).toBe(false)
  })

  it('有三个演示账号', () => {
    expect(wrapper.vm.demoAccounts).toHaveLength(3)
  })

  it('fillDemo 填充用户名和密码', () => {
    wrapper.vm.fillDemo('admin', 'admin123')
    expect(wrapper.vm.form.username).toBe('admin')
    expect(wrapper.vm.form.password).toBe('admin123')
  })

  it('空表单登录时提示警告且不调用 login', async () => {
    wrapper.vm.form.username = ''
    wrapper.vm.form.password = ''

    await wrapper.vm.handleLogin()

    expect(mocks.mockMessage.warning).toHaveBeenCalledWith('请填写用户名和密码')
    expect(wrapper.vm.showError).toBe(true)
    expect(mocks.mockLogin).not.toHaveBeenCalled()
  })

  it('只填用户名（缺密码）也提示警告', async () => {
    wrapper.vm.form.username = 'admin'
    wrapper.vm.form.password = ''

    await wrapper.vm.handleLogin()

    expect(mocks.mockMessage.warning).toHaveBeenCalledWith('请填写用户名和密码')
    expect(mocks.mockLogin).not.toHaveBeenCalled()
  })
})
