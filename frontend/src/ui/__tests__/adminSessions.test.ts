import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

/**
 * 管理端「会话管理」接真判据（批次 23）。
 *
 * 这一页有一个必须先承认的事实：`GET /user-sessions/online` 实际返回**全部**会话
 * （后端注释写「查询所有活跃且未过期」，实现是 `list()`，没有过滤条件）。
 * 所以判据的重点不是「能画表格」，而是**不把失效会话说成在线**：
 * 默认只显示 `is_active=true` 且未过期的行，其余靠「显示全部」显式打开；
 * 强制下线必须二次确认，且当前会话不允许在本地把自己踢掉。
 */
const sessionApi = vi.hoisted(() => ({ getOnlineUsers: vi.fn(), forceLogout: vi.fn() }))
const adminApi = vi.hoisted(() => ({ getAllUsers: vi.fn() }))

vi.mock('@/api/userSession', () => ({ ...sessionApi, default: sessionApi }))
vi.mock('@/api/admin', () => ({ ...adminApi, default: adminApi }))

const toast = vi.hoisted(() => vi.fn())
vi.mock('../composables/ui', () => ({ useUi: () => ({ toast }) }))

import SessionsBody from '../views/specials/SessionsBody.vue'

const envelope = (data: unknown) => ({ code: 200, message: 'ok', data })

const SESSIONS = [
  {
    id: 11,
    userId: 4,
    deviceName: 'Computer',
    browser: 'Chrome 15',
    operatingSystem: 'Windows 10',
    ipAddress: '127.0.0.1',
    isActive: true,
    isCurrent: false,
    loginTime: '2026-11-04T09:00:00',
    lastActivity: '2026-11-04T12:00:00',
    expiresAt: '2099-01-01T00:00:00',
  },
  {
    id: 12,
    userId: 5,
    deviceName: 'Phone',
    browser: 'Safari',
    ipAddress: '10.0.0.5',
    isActive: false,
    isCurrent: false,
    loginTime: '2026-10-01T09:00:00',
    lastActivity: '2026-10-01T10:00:00',
    expiresAt: '2099-01-01T00:00:00',
  },
  {
    // is_active 还是 true，但过期时间早于现在——按「在线」算就是假读数
    id: 13,
    userId: 6,
    deviceName: 'Tablet',
    ipAddress: '10.0.0.6',
    isActive: true,
    isCurrent: false,
    loginTime: '2020-01-01T09:00:00',
    lastActivity: '2020-01-01T10:00:00',
    expiresAt: '2020-01-02T00:00:00',
  },
  {
    id: 14,
    userId: 1,
    deviceName: 'Computer',
    browser: 'Chrome 15',
    ipAddress: '127.0.0.1',
    isActive: true,
    isCurrent: true,
    loginTime: '2026-11-04T08:00:00',
    lastActivity: '2026-11-04T13:00:00',
    expiresAt: '2099-01-01T00:00:00',
  },
]

function statValues(wrapper: ReturnType<typeof mount>): string[] {
  return wrapper.findAll('.stat-value').map((node) => node.text().replace(/\s+/g, ''))
}

function modalButton(text: string): HTMLButtonElement | undefined {
  return Array.from(document.querySelectorAll('.modal-panel button')).find(
    (node) => node.textContent?.trim() === text,
  ) as HTMLButtonElement | undefined
}

beforeEach(() => {
  vi.clearAllMocks()
  sessionApi.getOnlineUsers.mockResolvedValue(envelope(SESSIONS))
  sessionApi.forceLogout.mockResolvedValue(envelope(null))
  adminApi.getAllUsers.mockResolvedValue([
    { id: 1, username: 'admin', fullName: '管理员' },
    { id: 4, username: 'st001', fullName: '张三' },
  ])
})

describe('管理端 · 会话管理', () => {
  it('默认只显示有效且未过期的会话；KPI 的在线口径与表格一致', async () => {
    const wrapper = mount(SessionsBody)
    await flushPromises()

    expect(sessionApi.getOnlineUsers).toHaveBeenCalledTimes(1)
    // 4 条里：id 12 已失效、id 13 已过期 ⇒ 默认只剩 2 条
    expect(wrapper.findAll('tbody tr')).toHaveLength(2)
    expect(wrapper.text()).not.toContain('Phone')
    expect(wrapper.text()).not.toContain('Tablet')
    expect(statValues(wrapper)).toEqual(['2个', '2人', '4个', '2个'])
    // 账号名映射成功时不再显示「用户 #4」
    expect(wrapper.text()).toContain('张三')
    wrapper.unmount()
  })

  it('「显示全部」把失效与过期会话放出来，状态列逐行区分，不说成在线', async () => {
    const wrapper = mount(SessionsBody)
    await flushPromises()

    const toggle = wrapper.findAll('button').find((node) => node.text() === '显示全部')
    await toggle!.trigger('click')
    await flushPromises()

    expect(wrapper.findAll('tbody tr')).toHaveLength(4)
    expect(wrapper.text()).toContain('已失效')
    expect(wrapper.text()).toContain('已过期')
    expect(wrapper.text()).toContain('当前会话')
    // 失效行的下线按钮不可点
    const rows = wrapper.findAll('tbody tr')
    const deadRow = rows.find((row) => row.text().includes('Phone'))
    expect(deadRow!.find('.text-btn').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('强制下线要二次确认；确认后打 DELETE 端点并刷新列表', async () => {
    const wrapper = mount(SessionsBody)
    await flushPromises()

    const row = wrapper.findAll('tbody tr').find((node) => node.text().includes('张三'))
    await row!.find('.text-btn').trigger('click')

    expect(modalButton('确认下线'), '未弹出二次确认').toBeTruthy()
    modalButton('确认下线')!.click()
    await flushPromises()

    expect(sessionApi.forceLogout).toHaveBeenCalledWith(11)
    expect(toast).toHaveBeenCalledWith(expect.stringContaining('已强制下线'), 'ok')
    // 成功后会重新拉一次列表
    expect(sessionApi.getOnlineUsers).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('取消确认不发请求；失败时把后端原因留在弹层里且不关弹层', async () => {
    const wrapper = mount(SessionsBody)
    await flushPromises()

    const row = wrapper.findAll('tbody tr').find((node) => node.text().includes('张三'))
    await row!.find('.text-btn').trigger('click')
    modalButton('取消')!.click()
    await flushPromises()
    expect(sessionApi.forceLogout).not.toHaveBeenCalled()

    await row!.find('.text-btn').trigger('click')
    sessionApi.forceLogout.mockRejectedValue({ response: { data: { message: '会话不存在' } } })
    modalButton('确认下线')!.click()
    await flushPromises()

    expect(toast).toHaveBeenCalledWith('会话不存在', 'danger')
    expect(document.body.textContent).toContain('会话不存在')
    expect(modalButton('确认下线'), '失败后弹层不应自动关闭').toBeTruthy()
    wrapper.unmount()
  })

  it('is_current 在多条会话上为 true 时视为字段不可用：不标「当前会话」、也不禁用下线', async () => {
    // 真栈实测：536 条会话里 450 条 is_current=true（与 is_active 同值）。
    // 若照单采信，整页都会标「当前会话」，下线按钮也会被全量禁用。
    const flagged = SESSIONS.map((row) => ({ ...row, isCurrent: true }))
    sessionApi.getOnlineUsers.mockResolvedValue(envelope(flagged))
    const wrapper = mount(SessionsBody)
    await flushPromises()

    const chips = wrapper.findAll('tbody .chip').map((node) => node.text())
    expect(chips).not.toContain('当前会话')
    expect(wrapper.text()).toContain('is_current')
    expect(wrapper.text()).toContain('不据此判定')
    const firstRow = wrapper.findAll('tbody tr')[0]
    expect(firstRow.find('.text-btn').attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('当前登录会话不允许在本地强制下线', async () => {
    const wrapper = mount(SessionsBody)
    await flushPromises()

    const row = wrapper.findAll('tbody tr').find((node) => node.text().includes('管理员'))
    expect(row!.find('.text-btn').attributes('disabled')).toBeDefined()
    wrapper.unmount()
  })

  it('加载失败给错误与重试，空列表给可执行空态', async () => {
    sessionApi.getOnlineUsers.mockRejectedValueOnce(new Error('500'))
    const wrapper = mount(SessionsBody)
    await flushPromises()

    expect(wrapper.find('[role="alert"]').text()).toContain('会话列表加载失败')
    expect(wrapper.text()).toContain('无法确认会话数据')

    sessionApi.getOnlineUsers.mockResolvedValue(envelope([]))
    const retry = wrapper.findAll('button').find((node) => node.text() === '重试')
    await retry!.trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('暂无有效会话')
    wrapper.unmount()
  })

  it('账号名映射失败时降级为用户 #ID 并说明原因，不阻断会话列表', async () => {
    adminApi.getAllUsers.mockRejectedValue(new Error('403'))
    const wrapper = mount(SessionsBody)
    await flushPromises()

    expect(wrapper.text()).toContain('账号名映射加载失败')
    expect(wrapper.text()).toContain('用户 #4')
    expect(wrapper.findAll('tbody tr')).toHaveLength(2)
    wrapper.unmount()
  })
})
