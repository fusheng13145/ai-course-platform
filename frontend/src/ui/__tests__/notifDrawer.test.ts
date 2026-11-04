import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'

const ui = vi.hoisted(() => ({
  notifOpen: null as unknown as { value: boolean },
  unread: null as unknown as { value: number },
  role: null as unknown as { value: string },
  toast: vi.fn(),
}))

const dataApi = vi.hoisted(() => ({ loadNotifications: vi.fn() }))
const notificationApi = vi.hoisted(() => ({ markAllAsRead: vi.fn() }))
const router = vi.hoisted(() => ({ push: vi.fn() }))

vi.mock('../composables/ui', async () => {
  const { ref } = await import('vue')
  ui.notifOpen = ref(true)
  ui.unread = ref(0)
  ui.role = ref('student')
  return {
    useUi: () => ({
      role: ui.role,
      notifOpen: ui.notifOpen,
      unread: ui.unread,
      toast: ui.toast,
    }),
  }
})
vi.mock('../data/api', () => dataApi)
vi.mock('@/api/notification', () => notificationApi)
vi.mock('vue-router', () => ({ useRouter: () => ({ push: router.push }) }))

import NotifDrawer from '../components/NotifDrawer.vue'

function mountDrawer(): VueWrapper {
  return mount(NotifDrawer, {
    global: { stubs: { Icon: true } },
  })
}

async function settle() {
  await flushPromises()
  await flushPromises()
}

const oneUnread = {
  id: 1,
  title: '真实通知',
  content: '来自接口',
  time: '2026-10-31 10:00',
  unread: true,
  kind: '系统',
}

beforeEach(() => {
  vi.clearAllMocks()
  ui.notifOpen.value = true
  ui.unread.value = 0
  ui.role.value = 'student'
  notificationApi.markAllAsRead.mockResolvedValue({})
})

describe('通知抽屉：真实数据、失败态与已读语义', () => {
  it('真实通知为空时不回落到演示通知', async () => {
    dataApi.loadNotifications.mockResolvedValue([])
    const wrapper = mountDrawer()
    await settle()

    expect(wrapper.findAll('.notif-item')).toHaveLength(0)
    expect(wrapper.text()).toContain('暂无通知')
    wrapper.unmount()
  })

  it('加载失败时不显示假通知，给出可区分的失败态', async () => {
    dataApi.loadNotifications.mockRejectedValue(new Error('500'))
    const wrapper = mountDrawer()
    await settle()

    expect(wrapper.findAll('.notif-item')).toHaveLength(0)
    expect(wrapper.text()).toContain('通知加载失败')
    wrapper.unmount()
  })

  it('全部已读调用真接口，成功后清零未读', async () => {
    dataApi.loadNotifications.mockResolvedValue([oneUnread])
    const wrapper = mountDrawer()
    await settle()

    expect(wrapper.findAll('.notif-item.is-unread')).toHaveLength(1)
    await wrapper.find('.notif-tools .text-btn').trigger('click')
    await settle()

    expect(notificationApi.markAllAsRead).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('.notif-item.is-unread')).toHaveLength(0)
    expect(ui.unread.value).toBe(0)
    expect(ui.toast).toHaveBeenCalledWith('全部通知已标记为已读', 'ok')
    wrapper.unmount()
  })

  it('全部已读失败时保留未读并提示，不谎报成功', async () => {
    dataApi.loadNotifications.mockResolvedValue([oneUnread])
    notificationApi.markAllAsRead.mockRejectedValueOnce(new Error('500'))
    const wrapper = mountDrawer()
    await settle()

    await wrapper.find('.notif-tools .text-btn').trigger('click')
    await settle()

    expect(notificationApi.markAllAsRead).toHaveBeenCalledTimes(1)
    expect(wrapper.findAll('.notif-item.is-unread')).toHaveLength(1)
    expect(ui.unread.value).toBe(1)
    expect(ui.toast).toHaveBeenCalledWith('全部已读失败，请重试', 'danger')
    wrapper.unmount()
  })
})
