import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

/**
 * 管理端「催交提醒」接真判据（M-12c 收口）。
 *
 * 后端 `/reminders` 五方法一直都在，但新版前端此前没有任何入口；本页只做集成，
 * 不改后端契约。断言重点：列表真读、创建/改期/取消/发送到期的载荷形状、
 * 失败态不伪装成空列表。
 */
const reminderApi = vi.hoisted(() => ({
  getTodos: vi.fn(),
  createReminder: vi.fn(),
  updateReminder: vi.fn(),
  cancelReminder: vi.fn(),
  sendDue: vi.fn(),
}))
const toast = vi.hoisted(() => vi.fn())

vi.mock('@/api/reminder', () => ({ ...reminderApi, default: reminderApi }))
vi.mock('../composables/ui', () => ({ useUi: () => ({ toast }) }))

import RemindersPanel from '../views/specials/RemindersPanel.vue'

const envelope = (data: unknown) => ({ code: 200, message: 'ok', data })

const ROWS = [
  {
    id: 501,
    reminderType: 'BEFORE_DEADLINE',
    status: 'PENDING',
    userId: 1001,
    taskId: 88,
    courseId: 4178,
    title: '作业截止提醒',
    message: '请尽快提交第一次作业',
    dueDate: '2026-11-20T23:59:00',
    triggerDay: 3,
    scheduledAt: '2026-11-17T23:59:00',
    sentAt: null,
  },
  {
    id: 502,
    reminderType: 'DAILY_STUDY',
    status: 'SENT',
    userId: 1002,
    taskId: null,
    courseId: null,
    title: '每日学习提醒',
    message: '今天也别忘了学习',
    dueDate: '2026-11-18T23:59:00',
    triggerDay: 1,
    scheduledAt: '2026-11-17T23:59:00',
    sentAt: '2026-11-17T23:59:05',
  },
]

function buttonByText(wrapper: ReturnType<typeof mount>, text: string) {
  return wrapper.findAll('button').find((button) => button.text().includes(text))
}

beforeEach(() => {
  vi.clearAllMocks()
  reminderApi.getTodos.mockResolvedValue(envelope(ROWS))
  reminderApi.createReminder.mockResolvedValue(envelope({ id: 503 }))
  reminderApi.updateReminder.mockResolvedValue(envelope({ id: 501 }))
  reminderApi.cancelReminder.mockResolvedValue(envelope(null))
  reminderApi.sendDue.mockResolvedValue(envelope([{ id: 501 }]))
})

describe('管理端 · 催交提醒', () => {
  it('挂载即读 /reminders/todos，并按真实状态渲染列表与 KPI', async () => {
    const wrapper = mount(RemindersPanel)
    await flushPromises()

    expect(reminderApi.getTodos).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('作业截止提醒')
    expect(wrapper.text()).toContain('每日学习提醒')
    expect(wrapper.text()).toContain('待发送')
    expect(wrapper.text()).toContain('已发送')
    expect(wrapper.text()).toContain('#1001')
    expect(wrapper.text()).toContain('课程 #4178')
    expect(wrapper.text()).toContain('截止前提醒')
    expect(wrapper.text()).not.toContain('示例数据')
  })

  it('创建提醒：userId 转数字、datetime-local 补秒、提前天数按数字上报', async () => {
    const wrapper = mount(RemindersPanel)
    await flushPromises()

    await buttonByText(wrapper, '新建提醒')!.trigger('click')
    await wrapper.find('[data-testid="reminder-user"]').setValue('1003')
    await wrapper.find('[data-testid="reminder-type"]').setValue('DAILY_STUDY')
    await wrapper.find('[data-testid="reminder-due"]').setValue('2026-12-01T23:59')
    await wrapper.find('[data-testid="reminder-trigger"]').setValue('2')
    await wrapper.find('[data-testid="reminder-title"]').setValue('期末复习提醒')
    await wrapper.find('[data-testid="reminder-message"]').setValue('请按计划复习')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(reminderApi.createReminder).toHaveBeenCalledWith({
      title: '期末复习提醒',
      message: '请按计划复习',
      reminderType: 'DAILY_STUDY',
      dueDate: '2026-12-01T23:59:00',
      triggerDay: 2,
      userId: 1003,
    })
    expect(reminderApi.getTodos).toHaveBeenCalledTimes(2)
  })

  it('编辑提醒：只发改期接口支持的四个字段', async () => {
    const wrapper = mount(RemindersPanel)
    await flushPromises()

    await buttonByText(wrapper, '编辑')!.trigger('click')
    await wrapper.find('[data-testid="reminder-title"]').setValue('作业截止提醒（已改期）')
    await wrapper.find('[data-testid="reminder-remind-at"]').setValue('2026-11-18T09:00')
    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(reminderApi.updateReminder).toHaveBeenCalledWith(501, {
      title: '作业截止提醒（已改期）',
      content: '请尽快提交第一次作业',
      remindAt: '2026-11-18T09:00:00',
      targetUserId: 1001,
    })
  })

  it('取消提醒走 PATCH 端点并刷新列表', async () => {
    const wrapper = mount(RemindersPanel)
    await flushPromises()

    await buttonByText(wrapper, '取消')!.trigger('click')
    await flushPromises()

    expect(reminderApi.cancelReminder).toHaveBeenCalledWith(501)
    expect(reminderApi.getTodos).toHaveBeenCalledTimes(2)
  })

  it('发送到期提醒：上报实际发送条数，而不是固定成功文案', async () => {
    const wrapper = mount(RemindersPanel)
    await flushPromises()

    await buttonByText(wrapper, '发送到期')!.trigger('click')
    await flushPromises()

    expect(reminderApi.sendDue).toHaveBeenCalledTimes(1)
    expect(toast).toHaveBeenCalledWith('已发送 1 条到期提醒', 'ok')
  })

  it('列表失败与真空分开：失败时不渲染「暂无催交提醒」', async () => {
    reminderApi.getTodos.mockRejectedValueOnce(new Error('network down'))
    const wrapper = mount(RemindersPanel)
    await flushPromises()

    expect(wrapper.text()).toContain('提醒列表加载失败')
    expect(wrapper.text()).not.toContain('暂无催交提醒')
    expect(buttonByText(wrapper, '重试')).toBeTruthy()
  })
})
