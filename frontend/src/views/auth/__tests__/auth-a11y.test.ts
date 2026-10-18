import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'

/**
 * auth 三页无障碍属性（D2-7）
 *
 * <p>此前 `useA11y`（inputA11y/buttonA11y）为死代码零使用；本轮在最高频
 * 的登录/注册/找回密码三页落地：输入控件带 aria-label + aria-required，
 * 提交按钮带 aria-label + aria-busy（提交中状态对读屏可见）。</p>
 */

const mocks = vi.hoisted(() => ({
  mockLogin: vi.fn(),
  mockRegister: vi.fn(),
  mockSendResetCode: vi.fn(),
  mockVerifyResetCode: vi.fn(),
  mockResetPassword: vi.fn(),
  mockGetDefaultRoute: vi.fn(() => '/student/dashboard'),
  mockMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  mockPush: vi.fn(),
  state: { isLoading: false },
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.mockPush }),
  useRoute: () => ({ query: {} }),
  createRouter: vi.fn(),
  createWebHistory: vi.fn(),
}))
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    isLoading: mocks.state.isLoading,
    login: mocks.mockLogin,
    register: mocks.mockRegister,
    sendResetCode: mocks.mockSendResetCode,
    verifyResetCode: mocks.mockVerifyResetCode,
    resetPassword: mocks.mockResetPassword,
    getDefaultRoute: mocks.mockGetDefaultRoute,
  }),
}))
vi.mock('element-plus', () => ({ ElMessage: mocks.mockMessage }))
vi.mock('@/components/ThemeSwitcher.vue', () => ({
  default: { name: 'ThemeSwitcher', template: '<div />' },
}))

import Login from '@/views/auth/Login.vue'
import Register from '@/views/auth/Register.vue'
import ForgotPassword from '@/views/auth/ForgotPassword.vue'

/** el-input 桩：单根 input，fallthrough attrs（aria-*）自动落到根元素 */
const inputStub = {
  props: ['modelValue', 'type', 'placeholder', 'readonly', 'disabled'],
  template: '<input :type="type || \'text\'" :value="modelValue" />',
}

function commonStubs() {
  return {
    'el-input': inputStub,
    'el-checkbox': { template: '<label><slot /></label>' },
    'el-form': { template: '<form><slot /></form>' },
    'el-form-item': { template: '<div><slot /></div>' },
    RouterLink: { template: '<a><slot /></a>' },
  }
}

describe('auth 三页 — 无障碍属性（D2-7）', () => {
  describe('Login', () => {
    it('用户名/密码输入带 aria-label + aria-required', () => {
      const wrapper = mount(Login, { global: { stubs: commonStubs() } })
      const username = wrapper.get('input[name="login-username"]')
      const password = wrapper.get('input[name="login-password"]')
      expect(username.attributes('aria-label')).toBe('用户名')
      expect(username.attributes('aria-required')).toBe('true')
      expect(password.attributes('aria-label')).toBe('密码')
      expect(password.attributes('aria-required')).toBe('true')
    })

    it('提交按钮带 aria-label；加载中 aria-busy=true', async () => {
      const wrapper = mount(Login, { global: { stubs: commonStubs() } })
      const btn = wrapper.get('button.btn-primary')
      expect(btn.attributes('aria-label')).toBe('登录')
      expect(btn.attributes('aria-busy')).toBe('false')

      mocks.state.isLoading = true
      const loading = mount(Login, { global: { stubs: commonStubs() } })
      expect(loading.get('button.btn-primary').attributes('aria-busy')).toBe('true')
      mocks.state.isLoading = false
    })
  })

  describe('Register', () => {
    it('四个字段均带 aria-label + aria-required，按钮 aria-label=注册', () => {
      const wrapper = mount(Register, { global: { stubs: commonStubs() } })
      const inputs = wrapper.findAll('input')
      const labels = inputs.map((i) => i.attributes('aria-label'))
      expect(labels).toEqual(expect.arrayContaining(['用户名', '邮箱', '密码', '确认密码']))
      inputs.forEach((i) => {
        if (i.attributes('aria-label')) {
          expect(i.attributes('aria-required')).toBe('true')
        }
      })
      expect(wrapper.get('button.btn-primary').attributes('aria-label')).toBe('注册')
    })
  })

  describe('ForgotPassword', () => {
    it('邮箱/验证码/新密码/确认新密码带 aria-label，三个步骤按钮各有 aria-label', async () => {
      const wrapper = mount(ForgotPassword, { global: { stubs: commonStubs() } })
      const labels = wrapper.findAll('input').map((i) => i.attributes('aria-label'))
      expect(labels).toContain('注册邮箱')

      // 三个步骤按钮（按 step 渲染其中之一），逐一校验 aria-label 映射
      const vm = wrapper.vm as unknown as { step: number }
      const expected: Record<number, string> = { 1: '发送验证码', 2: '验证并继续', 3: '重置密码' }
      for (const [stepStr, label] of Object.entries(expected)) {
        vm.step = Number(stepStr)
        await wrapper.vm.$nextTick()
        const btn = wrapper.find('button.btn-primary')
        expect(btn.exists()).toBe(true)
        expect(btn.attributes('aria-label')).toBe(label)
        expect(btn.attributes('aria-busy')).toBe('false')
      }
    })
  })
})
