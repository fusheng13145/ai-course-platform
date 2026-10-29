import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

/**
 * 全局页外壳的数据来源章必须与真实渲染内容一致：
 * - 约定 Body 已落地（如 admin/users）→ 「接口数据」，不再挂「示例数据」；
 * - 无 Body 的页面（如 admin/audit）仍走示例块 → 「示例数据」；
 * - 学业档案本期只提供成绩页签。
 */
const mocks = vi.hoisted(() => ({
  route: { meta: {} as Record<string, unknown>, query: {} as Record<string, string> },
  role: { value: 'admin' as string },
  push: vi.fn(),
  replace: vi.fn(),
  toast: vi.fn(),
  getUsers: vi.fn(),
  updateUserStatus: vi.fn(),
  getGrades: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({ push: mocks.push, replace: mocks.replace }),
}))

vi.mock('../composables/ui', async () => {
  const { ref } = await import('vue')
  // 组件模板直接使用 role，必须是 ref 才会被自动解包成字符串。
  const role = ref<string>('admin')
  mocks.role = role
  return { useV2Ui: () => ({ role, toast: mocks.toast }) }
})

vi.mock('@/api/admin', () => ({ getUsers: mocks.getUsers }))
vi.mock('@/api/user', () => ({ updateUserStatus: mocks.updateUserStatus }))
vi.mock('@/api/student', () => ({ default: { getGrades: mocks.getGrades } }))

import V2GlobalPage from '../views/V2GlobalPage.vue'

function mountPage(v2page: string, role: 'admin' | 'student') {
  mocks.route.meta.v2page = v2page
  mocks.route.query = {}
  mocks.role.value = role
  return mount(V2GlobalPage)
}

function sourceChip(wrapper: ReturnType<typeof mount>) {
  return wrapper.find('.page-kicker .chip')
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.getUsers.mockResolvedValue({
    code: 200,
    message: 'ok',
    data: {
      records: [
        {
          id: 11,
          username: 'zhangsan',
          fullName: '张三',
          email: 'zhangsan@example.com',
          role: 'TEACHER',
          isActive: true,
        },
      ],
      total: 1,
    },
  })
  mocks.getGrades.mockResolvedValue({ code: 200, message: 'ok', data: { courses: [] } })
})

describe('v2 全局页外壳 · 数据来源章', () => {
  it('约定 Body 已落地的页面标「接口数据」，正文真实渲染且不再出现「示例数据」', async () => {
    const wrapper = mountPage('users', 'admin')
    await vi.waitFor(() => {
      expect(wrapper.find('.users-body').exists()).toBe(true)
    })

    expect(sourceChip(wrapper).text()).toBe('接口数据')
    expect(wrapper.text()).not.toContain('示例数据')
    expect(mocks.getUsers).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('没有约定 Body 的页面仍走示例块，保留「示例数据」且不误标接口', () => {
    const wrapper = mountPage('audit', 'admin')

    expect(wrapper.find('.users-body').exists()).toBe(false)
    expect(sourceChip(wrapper).text()).toBe('示例数据')
    expect(wrapper.text()).not.toContain('接口数据')
    wrapper.unmount()
  })

  it('学业档案只保留成绩页签，错题本 / 学习路径不再出现', async () => {
    const wrapper = mountPage('academic', 'student')
    await flushPromises()

    const tabs = wrapper.findAll('.page-tabs button')
    expect(tabs.map((tab) => tab.text())).toEqual(['成绩'])
    expect(wrapper.find('.page-tabs').text()).not.toContain('错题本')
    expect(wrapper.find('.page-tabs').text()).not.toContain('学习路径')
    wrapper.unmount()
  })
})
