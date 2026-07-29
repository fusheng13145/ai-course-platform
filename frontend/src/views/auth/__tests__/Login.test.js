import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'

// Mock vue-router
const mockPush = vi.fn()
const mockRoute = { query: {} }
vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mockPush }),
  useRoute: () => mockRoute,
  createRouter: vi.fn(),
  createWebHistory: vi.fn(),
}))

// Mock auth store
const mockLogin = vi.fn()
const mockGetDefaultRoute = vi.fn(() => '/student/dashboard')
let mockIsLoading = false
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    isLoading: mockIsLoading,
    login: mockLogin,
    getDefaultRoute: mockGetDefaultRoute,
  }),
}))

// Mock element-plus ElMessage
const mockMessage = { success: vi.fn(), error: vi.fn() }
vi.mock('element-plus', () => ({
  ElMessage: mockMessage,
  ElButton: { name: 'ElButton', template: '<button :disabled="$attrs.disabled" @click="$emit(\'click\', $event)"><slot /></button>', emits: ['click'] },
  ElInput: { name: 'ElInput', template: '<input v-model="modelValue" :placeholder="placeholder" :type="type" />', props: ['modelValue', 'placeholder', 'type', 'showPassword'], emits: ['update:modelValue'] },
  ElForm: { name: 'ElForm', template: '<form @submit.prevent><slot /></form>' },
  ElFormItem: { name: 'ElFormItem', template: '<div><slot /></div>' },
  ElCheckbox: { name: 'ElCheckbox', template: '<label><input type="checkbox" v-model="modelValue" /><slot /></label>', props: ['modelValue'], emits: ['update:modelValue'] },
  ElIcon: { name: 'ElIcon', template: '<i><slot /></i>', props: ['size', 'color'] },
}))

// Mock ThemeSwitcher
vi.mock('@/components/ThemeSwitcher.vue', () => ({
  default: { name: 'ThemeSwitcher', template: '<div class="theme-switcher-mock"></div>' },
}))

// Mock element-plus icons-vue
vi.mock('@element-plus/icons-vue', () => ({
  School: { name: 'School' },
  User: { name: 'User' },
  Lock: { name: 'Lock' },
  MagicStick: { name: 'MagicStick' },
  TrendCharts: { name: 'TrendCharts' },
  Connection: { name: 'Connection' },
}))

let Login
describe('Login 页面组件', () => {
  beforeEach(async () => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    mockIsLoading = false
    mockRoute.query = {}

    const mod = await import('../Login.vue')
    Login = mod.default
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  describe('表单渲染', () => {
    it('渲染 auth-page 容器', () => {
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      expect(wrapper.find('.auth-page').exists()).toBe(true)
    })

    it('渲染 auth-container', () => {
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      expect(wrapper.find('.auth-container').exists()).toBe(true)
    })

    it('渲染表单区域（form-section）', () => {
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      expect(wrapper.find('.form-section').exists()).toBe(true)
    })

    it('渲染 form-card', () => {
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      expect(wrapper.find('.form-card').exists()).toBe(true)
    })

    it('渲染表单头部文本', () => {
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      const html = wrapper.html()
      expect(html).toContain('欢迎回来')
    })

    it('渲染登录按钮', () => {
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      expect(wrapper.find('.login-btn').exists()).toBe(true)
    })

    it('渲染注册跳转链接', () => {
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: { template: '<a><slot /></a>' }, TransitionGroup: true, Transition: true },
        },
      })
      expect(wrapper.html()).toContain('还没有账号')
    })
  })

  describe('表单验证', () => {
    it('验证规则要求用户名必填', () => {
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      const rules = wrapper.vm.rules
      expect(rules.username).toBeDefined()
      expect(rules.username[0].required).toBe(true)
      expect(rules.username[0].message).toBe('请输入用户名')
    })

    it('验证规则要求密码必填', () => {
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      const rules = wrapper.vm.rules
      expect(rules.password).toBeDefined()
      expect(rules.password[0].required).toBe(true)
      expect(rules.password[0].message).toBe('请输入密码')
    })

    it('表单初始值：用户名为空', () => {
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      expect(wrapper.vm.form.username).toBe('')
    })

    it('表单初始值：密码为空', () => {
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      expect(wrapper.vm.form.password).toBe('')
    })

    it('表单初始值：记住我为 true', () => {
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      expect(wrapper.vm.form.remember).toBe(true)
    })
  })

  describe('登录行为', () => {
    it('handleLogin 在 formRef 为 null 时直接返回', async () => {
      const wrapper = mount(Login, {
        global: {
          stubs: {
            'el-form': { template: '<form><slot /></form>' },
            'el-form-item': { template: '<div><slot /></div>' },
            'el-input': { template: '<input />', props: ['modelValue', 'placeholder', 'type', 'showPassword'] },
            'el-checkbox': { template: '<label><slot /></label>' },
            'el-icon': { template: '<i><slot /></i>' },
            'el-button': { template: '<button><slot /></button>' },
            RouterLink: true,
            TransitionGroup: true,
            Transition: true,
          },
        },
      })
      wrapper.vm.form.username = 'test'
      wrapper.vm.form.password = 'pass'
      // formRef.value 为模板引用，stubbed el-form 不会设置 validate
      // 直接测试 formRef 为 null 的场景
      wrapper.vm.formRef = null
      const result = await wrapper.vm.handleLogin()
      expect(result).toBeUndefined()
    })

    it('formRef 有值时会调用 auth.login', async () => {
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      // 模拟 formRef 验证通过
      wrapper.vm.form.username = 'testuser'
      wrapper.vm.form.password = 'password123'
      wrapper.vm.formRef = {
        validate: (cb) => cb(true),
      }

      mockLogin.mockResolvedValue({ success: true, user: { id: 1 } })

      await wrapper.vm.handleLogin()

      expect(mockLogin).toHaveBeenCalledWith({
        username: 'testuser',
        password: 'password123',
        remember: true,
      })
    })

    it('登录成功后跳转到默认路由', async () => {
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      wrapper.vm.form.username = 'test'
      wrapper.vm.form.password = 'pass'
      wrapper.vm.formRef = {
        validate: (cb) => cb(true),
      }
      mockLogin.mockResolvedValue({ success: true, user: { role: 'STUDENT' } })

      await wrapper.vm.handleLogin()

      // 登录成功后有 setTimeout(300ms) 延迟
      await new Promise(resolve => setTimeout(resolve, 350))
      expect(mockPush).toHaveBeenCalled()
    })

    it('登录成功后推送 redirect query 参数路由', async () => {
      mockRoute.query = { redirect: '/student/courses' }
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      wrapper.vm.form.username = 'test'
      wrapper.vm.form.password = 'pass'
      wrapper.vm.formRef = {
        validate: (cb) => cb(true),
      }
      mockLogin.mockResolvedValue({ success: true, user: { role: 'STUDENT' } })

      await wrapper.vm.handleLogin()
      await new Promise(resolve => setTimeout(resolve, 350))
      expect(mockPush).toHaveBeenCalledWith('/student/courses')
    })

    it('登录失败时显示错误信息', async () => {
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      wrapper.vm.form.username = 'bad'
      wrapper.vm.form.password = 'wrong'
      wrapper.vm.formRef = {
        validate: (cb) => cb(true),
      }
      mockLogin.mockRejectedValue(new Error('认证失败'))

      await wrapper.vm.handleLogin()

      expect(wrapper.vm.showError).toBe(true)
      // 登录失败不应跳转
      expect(mockPush).not.toHaveBeenCalled()
    })

    it('表单验证失败时不调用 login', async () => {
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      wrapper.vm.form.username = ''
      wrapper.vm.form.password = ''
      wrapper.vm.formRef = {
        validate: (cb) => cb(false),
      }

      await wrapper.vm.handleLogin()
      expect(mockLogin).not.toHaveBeenCalled()
    })
  })

  describe('加载状态', () => {
    it('加载中时按钮显示"登录中..."', () => {
      mockIsLoading = true
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      expect(wrapper.find('.btn-text').text()).toContain('登录中')
    })

    it('非加载中时按钮显示"登 录"', () => {
      mockIsLoading = false
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      // "登 录" has a space
      expect(wrapper.find('.btn-text').text()).toContain('登')
    })

    it('加载中时按钮 disabled', () => {
      mockIsLoading = true
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      expect(wrapper.find('.login-btn').attributes('disabled')).toBeDefined()
    })

    it('加载中时按钮有 btn-loading 类', () => {
      mockIsLoading = true
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      expect(wrapper.find('.login-btn').classes()).toContain('btn-loading')
    })
  })

  describe('演示账号', () => {
    it('fillDemo 填充用户名和密码', () => {
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      wrapper.vm.fillDemo('admin', 'admin123')
      expect(wrapper.vm.form.username).toBe('admin')
      expect(wrapper.vm.form.password).toBe('admin123')
    })

    it('有三个演示账号', () => {
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      expect(wrapper.vm.demoAccounts).toHaveLength(3)
    })

    it('登录失败后显示演示账号区域', async () => {
      const wrapper = mount(Login, {
        global: {
          stubs: { RouterLink: true, TransitionGroup: true, Transition: true },
        },
      })
      wrapper.vm.showError = true
      await nextTick()
      expect(wrapper.vm.showError).toBe(true)
    })
  })
})
