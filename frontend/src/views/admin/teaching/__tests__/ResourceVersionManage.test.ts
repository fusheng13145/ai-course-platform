import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * ResourceVersionManage.vue（资源版本管理）— 加载失败可见（2026-10-14）
 *
 * <p>原实现两处静默：① loadResources 失败仅 console.error 后清空，管理员会误判平台
 * 无任何资源；② loadVersions 用 allSettled 却不检测 rejected，版本历史/当前版本任一
 * 失败都被静默吞掉，表格照常渲染「暂无版本记录」、统计卡谎报 0。现补
 * resourcesError/versionsError 独立归因 + 聚合 loadError + 持久 el-alert，
 * 空态文案诚实化，失败时统计卡改显 '--'。</p>
 */

const mocks = vi.hoisted(() => ({
  searchResources: vi.fn(),
  getVersionHistory: vi.fn(),
  getActiveVersion: vi.fn(),
  createVersion: vi.fn(),
  rollbackVersion: vi.fn(),
}))

vi.mock('@/api/resource', () => ({
  default: { searchResources: mocks.searchResources },
}))
vi.mock('@/api/resourceVersion', () => ({
  default: {
    getVersionHistory: mocks.getVersionHistory,
    getActiveVersion: mocks.getActiveVersion,
    createVersion: mocks.createVersion,
    rollbackVersion: mocks.rollbackVersion,
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

import ResourceVersionManage from '@/views/admin/teaching/ResourceVersionManage.vue'
import StatCard from '@/components/ui/StatCard.vue'

// el-select 显式桩：声明 modelValue 与 change，测试经 $emit 驱动「选中资源 → 加载版本」
const ElSelectStub = {
  props: ['modelValue'],
  emits: ['update:modelValue', 'change'],
  template: '<div class="select-stub"><slot /></div>',
}

function mountPage() {
  return mount(ResourceVersionManage, {
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        'el-select': ElSelectStub,
        // el-table 桩：渲染 #empty 具名插槽，使内部 EmptyState 的诚实空态文案可断言
        'el-table': {
          props: ['data', 'emptyText'],
          template: '<div class="tbl-stub"><slot name="empty" /></div>',
        },
        // D2-3：空态统一为 EmptyState；shallow 下显式桩，断言 title 双态文案
        EmptyState: {
          props: ['title', 'desc', 'icon', 'size'],
          template: '<div class="empty-stub">{{ title }}</div>',
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

/** 选中资源（id=101）并触发版本加载 */
async function selectResource(wrapper: Awaited<ReturnType<typeof mountPage>>) {
  const select = wrapper.findComponent(ElSelectStub)
  await select.vm.$emit('update:modelValue', 101)
  await select.vm.$emit('change', 101)
  await flushPromises()
}

describe('ResourceVersionManage — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('资源列表加载失败：给出持久提示，不得静默渲染成无任何资源', async () => {
    mocks.searchResources.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('资源列表加载失败，请检查网络后重试')
  })

  it('版本历史失败（当前版本成功）：提示只归因版本历史，统计卡改显 --，空态诚实化', async () => {
    mocks.searchResources.mockResolvedValue({ data: { records: [{ id: 101, name: '教案R1' }] } })
    mocks.getVersionHistory.mockRejectedValue(new Error('500'))
    mocks.getActiveVersion.mockResolvedValue({ data: { id: 900 } })
    const wrapper = mountPage()
    await flushPromises()
    await selectResource(wrapper)

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('版本历史加载失败')
    expect(wrapper.text()).not.toContain('当前版本加载失败')
    expect(statValue(wrapper, '版本总数')).toBe('--')
    expect(statValue(wrapper, '当前版本')).toBe('--')
    expect(wrapper.get('.empty-stub').text()).toContain('版本记录未能加载，请查看上方提示后重试')
    expect(wrapper.text()).not.toContain('暂无版本记录')
  })

  it('阴性对照：全部成功（空版本列表）时无失败提示，空态保留原语义且统计为 0', async () => {
    mocks.searchResources.mockResolvedValue({ data: { records: [{ id: 101, name: '教案R1' }] } })
    mocks.getVersionHistory.mockResolvedValue({ data: { list: [] } })
    mocks.getActiveVersion.mockResolvedValue({ data: null })
    const wrapper = mountPage()
    await flushPromises()
    await selectResource(wrapper)

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.get('.empty-stub').text()).toContain('暂无版本记录')
    expect(wrapper.text()).not.toContain('未能加载')
    expect(statValue(wrapper, '版本总数')).toBe(0)
  })

  it('串味锁定：版本加载成功不得清掉资源列表的失败提示', async () => {
    mocks.searchResources.mockRejectedValue(new Error('500'))
    mocks.getVersionHistory.mockResolvedValue({ data: { list: [{ id: 1, versionNumber: 2 }] } })
    mocks.getActiveVersion.mockResolvedValue({ data: { id: 1 } })
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.text()).toContain('资源列表加载失败')

    await selectResource(wrapper)

    // 版本路成功（版本总数恢复真实计数），但资源错误提示必须仍在
    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('资源列表加载失败')
    expect(wrapper.text()).not.toContain('版本历史加载失败')
    expect(statValue(wrapper, '版本总数')).toBe(1)
  })

  it('失败后再成功：版本错误提示必须被清空（每次 loadVersions 复位本路）', async () => {
    mocks.searchResources.mockResolvedValue({ data: { records: [{ id: 101, name: '教案R1' }] } })
    mocks.getVersionHistory
      .mockRejectedValueOnce(new Error('500'))
      .mockResolvedValue({ data: { list: [] } })
    mocks.getActiveVersion.mockResolvedValue({ data: null })
    const wrapper = mountPage()
    await flushPromises()

    await selectResource(wrapper)
    expect(wrapper.text()).toContain('版本历史加载失败')

    await selectResource(wrapper)
    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('版本历史加载失败')
  })
})
