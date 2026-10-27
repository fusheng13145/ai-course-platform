import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * LoginLogs.vue（登录日志）— 加载失败可见（2026-10-14）
 *
 * <p>原实现三路接口一把 `Promise.all` + 空 catch：任一失败则三路数据全丢，且页面
 * 照常显示全 0 统计与「暂无登录记录」，管理员会误判平台确实没有登录记录。
 * 现改为 allSettled 三路独立归因 + 聚合 `loadError` + 持久 el-alert，并做「单路失败
 * 不污染另两路」的锁定。</p>
 */

const mocks = vi.hoisted(() => ({
  getStats: vi.fn(),
  getList: vi.fn(),
  getTrends: vi.fn(),
}))

vi.mock('@/api/loginLog', () => ({
  default: {
    getStats: mocks.getStats,
    getList: mocks.getList,
    getTrends: mocks.getTrends,
  },
}))

import LoginLogs from '@/views/admin/system/LoginLogs.vue'
import StatCard from '@/components/ui/StatCard.vue'

/** jsdom 无 window.matchMedia，而趋势数据映射时经 chartTheme.getMonoSeries 读系统偏好 */
window.matchMedia = vi.fn().mockImplementation((query: string) => ({
  matches: false,
  media: query,
  onchange: null,
  addListener: vi.fn(),
  removeListener: vi.fn(),
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
  dispatchEvent: vi.fn(),
})) as unknown as typeof window.matchMedia

const STATS_OK = {
  data: {
    data: { todayCount: 12, totalCount: 300, failedCount: 4, weekCount: 60, successRate: 96 },
  },
}
const LIST_OK = {
  data: {
    data: { list: [{ id: 1, username: 'admin', ipAddress: '10.0.0.1', loginStatus: 'SUCCESS' }] },
  },
}
const TRENDS_OK = { data: { data: [{ date: '2026-10-01', totalCount: 3 }] } }

function mountPage() {
  return mount(LoginLogs, {
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        // D2-3：空态统一为 EmptyState；shallow 下显式桩，断言 title 双态文案
        EmptyState: {
          props: ['title', 'desc', 'icon', 'size'],
          template: '<div class="empty-stub">{{ title }}</div>',
        },
        // CollapsePanel 必须渲染默认插槽，否则内部的列表/空态无法进入 DOM
        CollapsePanel: {
          props: ['title', 'icon', 'open'],
          template: '<div class="cp-stub"><slot /></div>',
        },
        TabGroup: { props: ['tabs', 'active'], template: '<div class="tg-stub" />' },
        PageHeader: { props: ['title', 'subtitle', 'icon'], template: '<div><slot /></div>' },
        StatCard: {
          props: ['label', 'value', 'icon', 'tone', 'toneColor', 'foot'],
          template:
            '<div class="sc-stub" :data-label="label">{{ label }}|{{ value }}|{{ foot }}</div>',
        },
        BarChart: true,
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

describe('LoginLogs — 加载失败可见（三路独立归因）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getStats.mockResolvedValue(STATS_OK)
    mocks.getList.mockResolvedValue(LIST_OK)
    mocks.getTrends.mockResolvedValue(TRENDS_OK)
  })

  it('三路都失败：逐路给出提示，统计卡改显 --，空态不谎称「暂无登录记录」', async () => {
    mocks.getStats.mockRejectedValue(new Error('500'))
    mocks.getList.mockRejectedValue(new Error('500'))
    mocks.getTrends.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    const alert = wrapper.get('.alert-stub').text()
    expect(alert).toContain('统计概览未能加载')
    expect(alert).toContain('登录记录未能加载')
    expect(alert).toContain('登录趋势未能加载')

    expect(statValue(wrapper, '今日登录')).toBe('--')
    expect(wrapper.get('.empty-stub').text()).toContain('登录记录未能加载，请查看上方提示后重试')
    expect(wrapper.text()).not.toContain('暂无登录记录')
  })

  it('单路失败不污染另两路：仅记录接口失败时统计读数仍为真实值，提示只报这一路', async () => {
    mocks.getList.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    const alert = wrapper.get('.alert-stub').text()
    expect(alert).toContain('登录记录未能加载')
    expect(alert).not.toContain('统计概览未能加载')
    expect(alert).not.toContain('登录趋势未能加载')
    // 统计这一路成功，不得被清成 '--'
    expect(statValue(wrapper, '今日登录')).toBe(12)
    expect(statValue(wrapper, '累计登录')).toBe(300)
  })

  it('单路失败不污染另两路：仅统计接口失败时记录这一路仍渲染真实数据', async () => {
    mocks.getStats.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    const alert = wrapper.get('.alert-stub').text()
    expect(alert).toContain('统计概览未能加载')
    expect(alert).not.toContain('登录记录未能加载')
    expect(statValue(wrapper, '今日登录')).toBe('--')
    expect(wrapper.find('.empty-stub').exists()).toBe(false)
    expect(wrapper.text()).toContain('admin')
  })

  it('阴性对照：三路都成功时无失败提示，空态保留原语义', async () => {
    mocks.getList.mockResolvedValue({ data: { data: { list: [] } } })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.get('.empty-stub').text()).toContain('暂无登录记录')
  })
})
