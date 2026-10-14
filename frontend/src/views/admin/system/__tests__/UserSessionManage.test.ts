import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * UserSessionManage.vue（admin 会话管理）— 双路加载失败可见（D2-1）
 *
 * <p>原实现在线用户路失败仅弹一次性 toast、空态谎称「暂无在线用户」；会话统计路
 * 更是静默失败只留默认值——统计卡与概览读数谎报 0，管理员无法区分「确无会话」
 * 与「接口失败」。现两路各自接入 useListFeedback：单路失败只污染该路提示，
 * 不把另一路数据一起清掉；失败路读数显 '--'。</p>
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

import UserSessionManage from '@/views/admin/system/UserSessionManage.vue'

function mountPage() {
  return mount(UserSessionManage, {
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        'el-empty': {
          props: ['description'],
          template: '<div class="empty-stub">{{ description }}</div>',
        },
        // 需渲染 #empty 插槽，否则内部的双态空文案无法进入 DOM 供断言
        'el-table': {
          props: ['data'],
          template: '<div class="table-stub"><slot name="empty" /></div>',
        },
        'el-descriptions': { template: '<div class="desc-stub" />' },
        'el-descriptions-item': { props: ['label'], template: '<div class="desc-item-stub" />' },
        'el-table-column': { template: '<div class="tbl-col-stub" />' },
        'el-tag': { template: '<span class="tag-stub" />' },
        'el-button': { template: '<button class="btn-stub" />' },
        'el-dialog': true,
        PageHeader: {
          props: ['title', 'subtitle'],
          template: '<div class="ph-stub"><slot /></div>',
        },
        StatCard: {
          props: ['label', 'value', 'icon'],
          template: '<div class="sc-stub" :data-label="label">{{ label }}|{{ value }}</div>',
        },
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

const USERS_OK = { data: [{ id: 1, username: 'admin', role: 'ADMIN', ip: '10.0.0.1' }] }
const STATS_OK = {
  data: { total: 10, active: 2, guestCount: 1, abnormalCount: 0, todayNew: 3, yesterdayNew: 5 },
}

describe('UserSessionManage 加载失败反馈（D2-1）', () => {
  beforeEach(() => {
    mocks.getOnlineUsers.mockReset()
    mocks.getSessionStats.mockReset()
  })

  it('两路均成功：无 alert，读数真实', async () => {
    mocks.getOnlineUsers.mockResolvedValue(USERS_OK)
    mocks.getSessionStats.mockResolvedValue(STATS_OK)
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.findAll('.alert-stub')).toHaveLength(0)
    const stats = wrapper.findAll('.sc-stub')
    expect(stats[0].text()).toBe('在线用户数|2')
    expect(stats[1].text()).toBe('总会话数|10')
  })

  it('在线用户路失败：该路 alert + 空态诚实化；统计路不受污染', async () => {
    mocks.getOnlineUsers.mockRejectedValue(new Error('boom'))
    mocks.getSessionStats.mockResolvedValue(STATS_OK)
    const wrapper = mountPage()
    await flushPromises()

    const alerts = wrapper.findAll('.alert-stub')
    expect(alerts).toHaveLength(1)
    expect(alerts[0].text()).toBe('在线用户加载失败，请稍后刷新；已保留上次显示的数据。')
    expect(wrapper.find('.empty-stub').text()).toBe('在线用户未能加载，请查看上方提示后重试')

    // 统计路成功：读数仍真实（单路失败不清掉另一路数据与提示）
    const stats = wrapper.findAll('.sc-stub')
    expect(stats[0].text()).toBe('在线用户数|2')
    expect(stats[1].text()).toBe('总会话数|10')
  })

  it('统计路失败：统计 alert + 读数显 --（原静默失败谎报 0）', async () => {
    mocks.getOnlineUsers.mockResolvedValue(USERS_OK)
    mocks.getSessionStats.mockRejectedValue(new Error('boom'))
    const wrapper = mountPage()
    await flushPromises()

    const alerts = wrapper.findAll('.alert-stub')
    expect(alerts).toHaveLength(1)
    expect(alerts[0].text()).toBe('会话统计加载失败，上方与会话概览读数暂不可信。')

    const stats = wrapper.findAll('.sc-stub')
    expect(stats[0].text()).toBe('在线用户数|--')
    expect(stats[1].text()).toBe('总会话数|--')
  })
})
