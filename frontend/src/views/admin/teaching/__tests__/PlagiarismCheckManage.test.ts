import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * PlagiarismCheckManage.vue（查重管理）— 加载失败可见（2026-10-14）
 *
 * <p>原实现 fetchTaskList/fetchMatchList 两路失败均仅 console.error 后静默清空：
 * 任务列表失败时统计卡谎报全 0、表格渲染「暂无查重任务」，管理员会误判平台无任何
 * 查重任务。现补 tasksError/matchesError 独立归因 + 聚合 loadError + 持久 el-alert，
 * 统计卡失败改显 '--'，空态文案经子组件 emptyText prop 透传诚实化。</p>
 */

const mocks = vi.hoisted(() => ({
  getCheckHistory: vi.fn(),
  getCheckMatches: vi.fn(),
  createCheck: vi.fn(),
  executeCheck: vi.fn(),
  getCheckDetail: vi.fn(),
}))

vi.mock('@/api/plagiarismCheck', () => ({
  default: {
    getCheckHistory: mocks.getCheckHistory,
    getCheckMatches: mocks.getCheckMatches,
    createCheck: mocks.createCheck,
    executeCheck: mocks.executeCheck,
    getCheckDetail: mocks.getCheckDetail,
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
  ElMessageBox: { confirm: vi.fn() },
}))

import PlagiarismCheckManage from '@/views/admin/teaching/PlagiarismCheckManage.vue'
import PcTaskTable from '@/views/admin/teaching/components/PcTaskTable.vue'
import PcMatchTable from '@/views/admin/teaching/components/PcMatchTable.vue'
import StatCard from '@/components/ui/StatCard.vue'

function mountPage() {
  return mount(PlagiarismCheckManage, {
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        // 子组件显式桩：声明 emptyText 等 props 并渲染出来，使透传文案可断言
        PcTaskTable: {
          props: ['loading', 'taskList', 'page', 'emptyText'],
          emits: ['execute', 'view-detail', 'page-change', 'size-change'],
          template: '<div class="task-table-stub">{{ emptyText }}</div>',
        },
        PcMatchTable: {
          props: ['selectedTask', 'loading', 'matchList', 'page', 'emptyText'],
          emits: ['page-change', 'size-change'],
          template: '<div class="match-table-stub">{{ emptyText }}</div>',
        },
        PcCreateDialog: true,
        PageHeader: {
          props: ['title', 'subtitle', 'icon', 'iconBg'],
          template: '<div><slot /></div>',
        },
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
  return wrapper
    .findAllComponents(StatCard)
    .find((c) => c.props('label') === label)
    ?.props('value')
}

describe('PlagiarismCheckManage — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('任务列表加载失败：给出持久提示，统计卡改显 --，空态不谎称「暂无查重任务」', async () => {
    mocks.getCheckHistory.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('查重任务加载失败，请检查网络后重试')
    expect(wrapper.findComponent(PcTaskTable).props('emptyText')).toContain(
      '查重任务未能加载，请查看上方提示后重试',
    )
    expect(statValue(wrapper, '任务总数')).toBe('--')
    expect(statValue(wrapper, '执行中')).toBe('--')
    expect(statValue(wrapper, '已完成')).toBe('--')
    expect(statValue(wrapper, '待执行')).toBe('--')
  })

  it('匹配详情失败（任务成功）：提示只归因匹配详情，统计卡保留真实计数', async () => {
    mocks.getCheckHistory.mockResolvedValue({
      data: { records: [{ id: 7, assignmentTitle: '作业A', status: 'COMPLETED' }] },
    })
    mocks.getCheckMatches.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    await wrapper
      .findComponent(PcTaskTable)
      .vm.$emit('view-detail', { id: 7, assignmentTitle: '作业A' })
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('查重匹配详情加载失败，请检查网络后重试')
    expect(wrapper.text()).not.toContain('查重任务加载失败')
    expect(wrapper.findComponent(PcMatchTable).props('emptyText')).toContain(
      '匹配详情未能加载，请查看上方提示后重试',
    )
    // 任务路成功：统计卡必须显真实计数，不得被匹配路失败拖成 '--'
    expect(statValue(wrapper, '任务总数')).toBe(1)
    expect(statValue(wrapper, '已完成')).toBe(1)
  })

  it('阴性对照：加载成功（空列表）时无失败提示，空态保留原语义且统计为 0', async () => {
    mocks.getCheckHistory.mockResolvedValue({ data: { records: [] } })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.findComponent(PcTaskTable).props('emptyText')).toBe('暂无查重任务')
    expect(statValue(wrapper, '任务总数')).toBe(0)
  })

  it('串味锁定：匹配详情加载成功不得清掉任务列表的失败提示', async () => {
    mocks.getCheckHistory.mockRejectedValue(new Error('500'))
    mocks.getCheckMatches.mockResolvedValue({ data: { list: [] } })
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('查重任务加载失败')

    await wrapper.findComponent(PcTaskTable).vm.$emit('view-detail', { id: 7 })
    await flushPromises()

    // 匹配路成功，但任务错误提示必须仍在
    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('查重任务加载失败')
    expect(wrapper.text()).not.toContain('查重匹配详情加载失败')
  })

  it('失败后再成功：提示必须被清空（每次 fetch 复位本路）', async () => {
    mocks.getCheckHistory
      .mockRejectedValueOnce(new Error('500'))
      .mockResolvedValue({ data: { records: [] } })
    const first = mountPage()
    await flushPromises()
    expect(first.find('.alert-stub').exists()).toBe(true)

    const second = mountPage()
    await flushPromises()

    expect(second.find('.alert-stub').exists()).toBe(false)
    expect(second.text()).not.toContain('查重任务加载失败')
  })
})
