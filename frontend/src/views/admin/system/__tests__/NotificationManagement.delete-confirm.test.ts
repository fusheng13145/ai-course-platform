import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * NotificationManagement 删除通知确认语（D2-2）
 *
 * <p>原实现右键菜单「删除」点击即删、**无任何二次确认**（D2-2 扫描发现的
 * 唯一「零确认删除」缺口）。改造为 confirmDanger 后锁定：取消不调接口、
 * 确认语含动作/对象/影响范围三要素。</p>
 */

const mocks = vi.hoisted(() => ({
  getList: vi.fn(),
  delNotification: vi.fn(),
  confirmDanger: vi.fn(),
  toastShow: vi.fn(),
}))

vi.mock('@/api/notification', () => ({
  default: {
    getNotificationList: mocks.getList,
    deleteNotification: mocks.delNotification,
    markAsRead: vi.fn(),
    publishNotification: vi.fn(),
  },
}))
vi.mock('@/utils/dangerConfirm', () => ({
  confirmDanger: mocks.confirmDanger,
}))
vi.mock('@/composables/useToast', () => ({
  toast: { show: mocks.toastShow },
}))

import NotificationManagement from '@/views/admin/system/NotificationManagement.vue'

function mountPage() {
  return mount(NotificationManagement, {
    shallow: true,
    global: {
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

const NOTICE = { id: 11, title: '期末考试安排', isRead: false }

/** script setup 内部绑定在测试环境经 vm 暴露；以类型桥接访问（对齐 ai-assistant-render 范式） */
interface PageInternal {
  doDelete: (n: { id: number; title?: string; isRead?: boolean }) => Promise<void>
  unread: Array<{ id: number; title?: string; isRead?: boolean }>
  read: Array<{ id: number; title?: string; isRead?: boolean }>
}
const pageVm = (wrapper: { vm: unknown }) => wrapper.vm as unknown as PageInternal

describe('NotificationManagement — 删除通知确认语（D2-2）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getList.mockResolvedValue({ data: { records: [], total: 0 } })
  })

  it('取消删除 → 不调删除接口，不提示成功', async () => {
    mocks.confirmDanger.mockResolvedValue(false)
    const wrapper = mountPage()
    await flushPromises()

    await pageVm(wrapper).doDelete(NOTICE)
    await flushPromises()

    // 确认语三要素：动作 / 对象 / 影响范围（恢复路径由原语默认补充）
    expect(mocks.confirmDanger).toHaveBeenCalledWith(
      expect.objectContaining({
        verb: '删除',
        target: '通知',
        subject: '期末考试安排',
        impact: expect.stringContaining('发送记录中移除'),
      }),
    )
    expect(mocks.delNotification).not.toHaveBeenCalled()
    expect(mocks.toastShow).not.toHaveBeenCalledWith('通知已删除', 'ok')
  })

  it('确认删除 → 调接口并从列表移除，提示成功', async () => {
    mocks.confirmDanger.mockResolvedValue(true)
    mocks.delNotification.mockResolvedValue({})
    const wrapper = mountPage()
    await flushPromises()

    // 预置一条未读，验证删除后从列表移除
    pageVm(wrapper).unread.splice(0, pageVm(wrapper).unread.length, { ...NOTICE })
    await pageVm(wrapper).doDelete(NOTICE)
    await flushPromises()

    expect(mocks.delNotification).toHaveBeenCalledWith(11)
    expect(pageVm(wrapper).unread.length).toBe(0)
    expect(mocks.toastShow).toHaveBeenCalledWith('通知已删除', 'ok')
  })

  it('删除接口失败 → 提示失败且列表保留', async () => {
    mocks.confirmDanger.mockResolvedValue(true)
    mocks.delNotification.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    pageVm(wrapper).unread.splice(0, pageVm(wrapper).unread.length, { ...NOTICE })
    await pageVm(wrapper).doDelete(NOTICE)
    await flushPromises()

    expect(mocks.toastShow).toHaveBeenCalledWith('删除失败', 'error')
    expect(pageVm(wrapper).unread.length).toBe(1)
  })
})
