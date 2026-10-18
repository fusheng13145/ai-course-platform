import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * UserSessionManage 在线用户客户端分页（D2-5）
 *
 * <p>getOnlineUsers 全量返回（无分页参数），平台高峰在线数可能上千。现渲染层
 * 客户端分页（每页 20）：≤20 零变化；超出切片 + 分页器；D2-1 双路失败态不受影响。</p>
 */

const mocks = vi.hoisted(() => ({
  getOnlineUsers: vi.fn(),
  getSessionStats: vi.fn(),
}))

vi.mock('@/api/userSession', () => ({
  default: {
    getOnlineUsers: mocks.getOnlineUsers,
    getSessionStats: mocks.getSessionStats,
    forceLogout: vi.fn(),
  },
}))
vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn(), prompt: vi.fn() },
}))
vi.mock('@/utils/dangerConfirm', () => ({
  confirmDanger: vi.fn().mockResolvedValue(false),
}))

import UserSessionManage from '@/views/admin/system/UserSessionManage.vue'

function makeUsers(n) {
  return Array.from({ length: n }, (_, i) => ({
    id: i + 1,
    username: `user${i + 1}`,
    role: 'STUDENT',
    ip: '127.0.0.1',
    loginTime: '2026-10-15 09:00',
    lastActiveTime: '2026-10-15 10:00',
  }))
}

function mountPage() {
  return mount(UserSessionManage, {
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        'el-table': {
          props: ['data'],
          template: '<div class="tbl-stub">{{ (data || []).length }}</div>',
        },
        'el-table-column': true,
        'el-tag': true,
        'el-button': { template: '<button><slot /></button>' },
        'el-pagination': {
          props: ['total'],
          template: '<div class="pager-stub" :data-total="total" />',
        },
        EmptyState: { props: ['title'], template: '<div class="empty-stub">{{ title }}</div>' },
        PageHeader: { template: '<div />' },
        StatCard: { template: '<div />' },
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

describe('UserSessionManage — D2-5 客户端分页', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getSessionStats.mockResolvedValue({ data: {} })
  })

  it('在线用户 ≤20：全量渲染且无分页器', async () => {
    mocks.getOnlineUsers.mockResolvedValue({ data: makeUsers(11) })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('.tbl-stub').text()).toBe('11')
    expect(wrapper.find('.pager-stub').exists()).toBe(false)
  })

  it('在线用户 45（高峰）：首页 20 行且分页器 total=45', async () => {
    mocks.getOnlineUsers.mockResolvedValue({ data: makeUsers(45) })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('.tbl-stub').text()).toBe('20')
    expect(wrapper.get('.pager-stub').attributes('data-total')).toBe('45')
  })

  it('在线用户加载失败：D2-1 失败提示保留、无分页器（不渲染成有数据）', async () => {
    mocks.getOnlineUsers.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.pager-stub').exists()).toBe(false)
    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.get('.tbl-stub').text()).toBe('0')
  })
})
