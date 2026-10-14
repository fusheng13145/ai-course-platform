import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * ReminderManage.vue（提醒管理）— 加载失败可见（2026-10-14）
 *
 * <p>原实现 getTodos 失败仅 console.error 后静默 → 页面照常显示「暂无待办提醒」与三张
 * 全 0 统计卡，管理员会误判平台确实没有待办。现补 `loadError` + 持久 el-alert，
 * 空态文案诚实化，失败时统计卡改显 '--'。</p>
 */

const mocks = vi.hoisted(() => ({
  getTodos: vi.fn(),
  createReminder: vi.fn(),
  updateReminder: vi.fn(),
  cancelReminder: vi.fn(),
  sendDue: vi.fn(),
}))

vi.mock('@/api/reminder', () => ({
  default: {
    getTodos: mocks.getTodos,
    createReminder: mocks.createReminder,
    updateReminder: mocks.updateReminder,
    cancelReminder: mocks.cancelReminder,
    sendDue: mocks.sendDue,
  },
}))
vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    closeAll: vi.fn(),
  }),
}))

import ReminderManage from '@/views/admin/system/ReminderManage.vue'
import StatCard from '@/components/ui/StatCard.vue'

function mountPage() {
  return mount(ReminderManage, {
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        // el-table 桩：渲染 #empty 具名插槽，使内部 el-empty 的诚实空态文案可断言
        'el-table': {
          props: ['data', 'emptyText'],
          template: '<div class="tbl-stub"><slot name="empty" /></div>',
        },
        'el-empty': {
          props: ['description'],
          template: '<div class="empty-stub">{{ description }}</div>',
        },
        PageHeader: { props: ['title', 'subtitle', 'icon', 'iconBg'], template: '<div><slot /></div>' },
        StatCard: {
          props: ['label', 'value', 'icon', 'tone'],
          template: '<div class="sc-stub" :data-label="label">{{ label }}|{{ value }}</div>',
        },
        'el-icon': true,
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

function statValue(wrapper: Awaited<ReturnType<typeof mountPage>>, label: string) {
  return wrapper.findAllComponents(StatCard).find((c) => c.props('label') === label)?.props('value')
}

describe('ReminderManage — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载失败：给出持久提示，统计卡改显 --，空态不谎称「暂无待办提醒」', async () => {
    mocks.getTodos.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('待办提醒加载失败，请检查网络后重试')
    expect(wrapper.get('.empty-stub').text()).toContain('待办提醒未能加载，请查看上方提示后重试')
    expect(wrapper.text()).not.toContain('暂无待办提醒')
    expect(statValue(wrapper, '总提醒')).toBe('--')
    expect(statValue(wrapper, '待处理')).toBe('--')
  })

  it('阴性对照：加载成功（空列表）时无失败提示，空态保留原语义且统计为 0', async () => {
    mocks.getTodos.mockResolvedValue({ data: { list: [] } })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.get('.empty-stub').text()).toContain('暂无待办提醒')
    expect(wrapper.text()).not.toContain('未能加载')
    expect(statValue(wrapper, '总提醒')).toBe(0)
  })

  it('成功时统计卡按真实列表计数', async () => {
    mocks.getTodos.mockResolvedValue({
      data: { list: [{ id: 1, title: 'A', status: 'PENDING' }, { id: 2, title: 'B', status: 'COMPLETED' }] },
    })
    const wrapper = mountPage()
    await flushPromises()

    expect(statValue(wrapper, '总提醒')).toBe(2)
    expect(statValue(wrapper, '待处理')).toBe(1)
    expect(statValue(wrapper, '已完成')).toBe(1)
  })

  it('失败后再成功：提示必须被清空（每次 load 复位）', async () => {
    mocks.getTodos.mockRejectedValueOnce(new Error('500')).mockResolvedValue({ data: { list: [] } })
    const first = mountPage()
    await flushPromises()
    expect(first.find('.alert-stub').exists()).toBe(true)

    const second = mountPage()
    await flushPromises()

    expect(second.find('.alert-stub').exists()).toBe(false)
    expect(second.text()).not.toContain('待办提醒加载失败')
  })
})
