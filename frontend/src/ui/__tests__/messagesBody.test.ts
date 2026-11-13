import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const api = vi.hoisted(() => ({
  getNotificationList: vi.fn(),
  markAsRead: vi.fn(),
  markAllAsRead: vi.fn(),
}))

vi.mock('@/api/notification', () => ({
  default: api,
  markAsRead: api.markAsRead,
  markAllAsRead: api.markAllAsRead,
}))

const toast = vi.hoisted(() => vi.fn())
vi.mock('../composables/ui', () => ({
  useUi: () => ({ toast }),
}))

import MessagesBody from '../views/specials/MessagesBody.vue'

const envelope = (data: unknown) => ({ code: 200, message: 'ok', data })
const listOf = (records: Record<string, unknown>[]) => envelope({ records })

const unreadRow = {
  id: 1,
  title: '期中成绩已发布',
  content: '计算机网络期中成绩已发布，请及时查看。',
  createdAt: '2026-10-20T10:00:00',
  type: '课程通知',
  isRead: false,
}

const readRow = {
  id: 2,
  title: '系统维护通知',
  content: '10 月 22 日 23:00 起进行系统维护。',
  createdAt: '2026-10-19T22:00:00',
  type: '系统',
  isRead: true,
}

async function mountBody() {
  const wrapper = mount(MessagesBody)
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  api.getNotificationList.mockResolvedValue(listOf([unreadRow, readRow]))
  api.markAsRead.mockResolvedValue({ data: {} })
  api.markAllAsRead.mockResolvedValue({ data: {} })
})

describe('消息中心', () => {
  it('列表渲染接口数据：未读与已读分开展示，不再出现示例数据字样', async () => {
    const wrapper = await mountBody()
    const items = wrapper.findAll('.message-item')

    expect(items).toHaveLength(2)
    expect(items[0].classes()).toContain('is-unread')
    expect(items[1].classes()).not.toContain('is-unread')

    const text = wrapper.text()
    expect(text).toContain('期中成绩已发布')
    expect(text).toContain('计算机网络期中成绩已发布，请及时查看。')
    expect(text).toContain('2026-10-20 10:00')
    expect(text).toContain('课程通知')
    expect(text).toContain('1 条未读')
    expect(text).not.toContain('示例数据')
  })

  it('单条已读：接口成功后才置为已读', async () => {
    const wrapper = await mountBody()

    await wrapper.find('.message-read-btn').trigger('click')
    await flushPromises()

    expect(api.markAsRead).toHaveBeenCalledTimes(1)
    expect(api.markAsRead).toHaveBeenCalledWith(1)
    expect(wrapper.findAll('.message-item')[0].classes()).not.toContain('is-unread')
    expect(wrapper.find('.message-read-btn').exists()).toBe(false)
  })

  it('单条已读失败：danger toast，且该行保持未读', async () => {
    api.markAsRead.mockRejectedValueOnce(new Error('500'))
    const wrapper = await mountBody()

    await wrapper.find('.message-read-btn').trigger('click')
    await flushPromises()

    expect(toast).toHaveBeenCalledWith('标记已读失败，请重试', 'danger')
    expect(wrapper.findAll('.message-item')[0].classes()).toContain('is-unread')
    expect(wrapper.find('.message-read-btn').exists()).toBe(true)
  })

  it('全部已读：调用 markAllAsRead 后本地未读清零', async () => {
    const wrapper = await mountBody()

    await wrapper.find('.mark-all-btn').trigger('click')
    await flushPromises()

    expect(api.markAllAsRead).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('.message-item.is-unread')).toHaveLength(0)
    expect(wrapper.find('.message-read-btn').exists()).toBe(false)
    expect(toast).toHaveBeenCalledWith('全部通知已标记为已读', 'ok')
  })

  it('全部已读失败：danger toast，且所有行保持未读', async () => {
    api.markAllAsRead.mockRejectedValueOnce(new Error('500'))
    const wrapper = await mountBody()

    await wrapper.find('.mark-all-btn').trigger('click')
    await flushPromises()

    expect(toast).toHaveBeenCalledWith('全部已读失败，请重试', 'danger')
    expect(wrapper.findAll('.message-item.is-unread')).toHaveLength(1)
    expect(wrapper.find('.message-read-btn').exists()).toBe(true)
  })

  it('失败态与空态可区分：失败可重试，重试成功后落到空态', async () => {
    api.getNotificationList.mockRejectedValueOnce(new Error('500'))
    const wrapper = await mountBody()

    expect(wrapper.find('.callout.danger').text()).toContain('通知加载失败，请重试')
    expect(wrapper.text()).not.toContain('暂无通知')

    api.getNotificationList.mockResolvedValueOnce(listOf([]))
    await wrapper.find('.callout.danger button').trigger('click')
    await flushPromises()

    expect(wrapper.find('.callout.danger').exists()).toBe(false)
    expect(wrapper.text()).toContain('暂无通知')
    expect(api.getNotificationList).toHaveBeenCalledTimes(2)
    expect(api.getNotificationList).toHaveBeenLastCalledWith({ page: 1, size: 20 })
  })

  it('接口成功但返回空列表时只渲染空态，不算失败', async () => {
    api.getNotificationList.mockResolvedValueOnce(listOf([]))
    const wrapper = await mountBody()

    expect(wrapper.find('.callout').exists()).toBe(false)
    expect(wrapper.text()).toContain('暂无通知')
    expect(api.getNotificationList).toHaveBeenCalledTimes(1)
  })
})
