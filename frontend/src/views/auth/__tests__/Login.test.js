import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

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

describe('Login 页面组件', () => {
  beforeEach(() => {
    mocks.mockLogin.mockReset()
    mocks.mockGetDefaultRoute.mockReset()
    mocks.mockGetDefaultRoute.mockReturnValue('/student/dashboard')
    mocks.mockPush.mockReset()
    mocks.mockMessage.success.mockReset()
    mocks.mockMessage.error.mockReset()
    mocks.mockMessage.warning.mockReset()
    mocks.state.isLoading = false
    mocks.state.token = ''
    mocks.mockRoute.query = {}
  })

  describe('结构渲染', () => {
    it('渲染 auth-page 容器', () => {
      const wrapper = mountLogin()
      expect(wrapper.find('.auth-page').exists()).toBe(true)
    })

    it('渲染 auth-card 卡片', () => {
      const wrapper = mountLogin()
      expect(wrapper.find('.auth-card').exists()).toBe(true)
    })

    it('渲染左侧品牌区', () => {
      const wrapper = mountLogin()
      expect(wrapper.find('.brand-side').exists()).toBe(true)
    })

    it('渲染右侧表单区', () => {
      const wrapper = mountLogin()
      expect(wrapper.find('.form-side').exists()).toBe(true)
    })

    it('渲染主题切换行', () => {
      const wrapper = mountLogin()
      expect(wrapper.find('.theme-row').exists()).toBe(true)
    })

    it('渲染注册跳转链接', () => {
      const wrapper = mountLogin()
      expect(wrapper.html()).toContain('还没有账号')
      expect(wrapper.html()).toContain('立即注册')
    })
  })

  describe('登录行为', () => {
    it('登录成功后调用 auth.login 并携带 rememberMe', async () => {
      const wrapper = mountLogin()
      wrapper.vm.form.username = 'testuser'
      wrapper.vm.form.password = 'password123'
      wrapper.vm.form.remember = false
      mocks.mockLogin.mockResolvedValue({ success: true, user: { role: 'STUDENT' } })

      await wrapper.vm.handleLogin()

      expect(mocks.mockLogin).toHaveBeenCalledWith({
        username: 'testuser',
        password: 'password123',
        rememberMe: false,
      })
    })

    it('remember 为 true 时 payload 携带 rememberMe: true', async () => {
      const wrapper = mountLogin()
      wrapper.vm.form.username = 'testuser'
      wrapper.vm.form.password = 'password123'
      wrapper.vm.form.remember = true
      mocks.mockLogin.mockResolvedValue({ success: true })

      await wrapper.vm.handleLogin()

      expect(mocks.mockLogin).toHaveBeenCalledWith({
        username: 'testuser',
        password: 'password123',
        rememberMe: true,
      })
    })

    it('登录成功后跳转到默认路由', async () => {
      const wrapper = mountLogin()
      wrapper.vm.form.username = 'test'
      wrapper.vm.form.password = 'pass'
      mocks.mockLogin.mockResolvedValue({ success: true, user: { role: 'STUDENT' } })

      await wrapper.vm.handleLogin()

      expect(mocks.mockMessage.success).toHaveBeenCalledWith('登录成功')
      expect(mocks.mockPush).toHaveBeenCalledWith('/student/dashboard')
    })

    it('登录成功后优先跳转 redirect query 参数', async () => {
      mocks.mockRoute.query = { redirect: '/student/courses' }
      const wrapper = mountLogin()
      wrapper.vm.form.username = 'test'
      wrapper.vm.form.password = 'pass'
      mocks.mockLogin.mockResolvedValue({ success: true, user: { role: 'STUDENT' } })

      await wrapper.vm.handleLogin()

      expect(mocks.mockPush).toHaveBeenCalledWith('/student/courses')
    })

    it('登录失败时显示错误且不跳转', async () => {
      const wrapper = mountLogin()
      wrapper.vm.form.username = 'bad'
      wrapper.vm.form.password = 'wrong'
      mocks.mockLogin.mockRejectedValue(new Error('认证失败'))

      await wrapper.vm.handleLogin()

      expect(wrapper.vm.showError).toBe(true)
      expect(mocks.mockMessage.error).toHaveBeenCalledWith('登录失败，请检查用户名和密码')
      expect(mocks.mockPush).not.toHaveBeenCalled()
    })

    it('登录失败且后端返回消息时优先展示后端消息', async () => {
      const wrapper = mountLogin()
      wrapper.vm.form.username = 'bad'
      wrapper.vm.form.password = 'wrong'
      mocks.mockLogin.mockRejectedValue({
        response: { data: { message: '账号已锁定' } },
      })

      await wrapper.vm.handleLogin()

      expect(wrapper.vm.showError).toBe(true)
      expect(mocks.mockMessage.error).toHaveBeenCalledWith('账号已锁定')
    })

    it('登录失败后显示演示账号区域', async () => {
      const wrapper = mountLogin()
      wrapper.vm.form.username = 'bad'
      wrapper.vm.form.password = 'wrong'
      mocks.mockLogin.mockRejectedValue(new Error('认证失败'))

      await wrapper.vm.handleLogin()

      expect(wrapper.find('.demo-row').exists()).toBe(true)
    })

    it('点击演示账号 tag 填充表单', async () => {
      const wrapper = mountLogin()
      wrapper.vm.showError = true
      await wrapper.vm.$nextTick()

      await wrapper.findAll('.tag')[2].trigger('click')

      expect(wrapper.vm.form.username).toBe('student')
      expect(wrapper.vm.form.password).toBe('student123')
    })

    it('已登录（有 token）时挂载直接跳转默认路由', () => {
      mocks.state.token = 'existing-token'
      mountLogin()

      expect(mocks.mockPush).toHaveBeenCalledWith('/student/dashboard')
    })
  })
})
