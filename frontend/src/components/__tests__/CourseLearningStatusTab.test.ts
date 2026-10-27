import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CourseLearningStatusTab.vue + useCourseLearningStatusTab —— 加载失败可见（2026-10-14，批次 17）
 *
 * <p>原实现双路 Promise.allSettled 只处理 fulfilled、完全没有 rejected 分支：
 * 班级分析失败时统计卡照显全 0、学生名单失败时空态谎称「暂无学生数据」。
 * 现拆为 classError / studentsError 两个独立错误状态 + 聚合 loadError，
 * 逐路判 rejected；统计卡只随班级路失败显 '--'，学生表空态只随学生路失败诚实化
 * —— 单路失败不得污染另一路（串味锁定）。</p>
 */

const mocks = vi.hoisted(() => ({
  getClassAnalytics: vi.fn(),
  getAllStudents: vi.fn(),
  getTeacherStudents: vi.fn(),
}))

vi.mock('@/api/teacher', () => ({
  default: { getClassAnalytics: mocks.getClassAnalytics },
}))
vi.mock('@/api/course', () => ({
  default: { getAllStudents: mocks.getAllStudents },
}))
vi.mock('@/api/user', () => ({
  default: { getTeacherStudents: mocks.getTeacherStudents },
}))

import CourseLearningStatusTab from '@/components/CourseLearningStatusTab.vue'
import ClsStatsRow from '@/components/course/ClsStatsRow.vue'

/** jsdom 无 window.matchMedia，而组合式函数在初始化即构造图表主题配置 */
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

const HONEST_EMPTY = '学生名单未能加载，请查看上方提示后重试'
const DEFAULT_EMPTY = '暂无学生数据'

function mountTab() {
  return mount(CourseLearningStatusTab, {
    props: { courseId: 5 },
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        ClsStatsRow: {
          props: ['overview', 'loadFailed'],
          template:
            '<div class="stats-stub" :data-failed="loadFailed ? \'true\' : \'false\'">{{ overview.activeStudents }}</div>',
        },
        ClsStudentTable: {
          props: ['loading', 'searchKeyword', 'filteredStudents', 'emptyText'],
          template: '<div class="table-stub">{{ emptyText }}</div>',
        },
        ClsChartsPanel: true,
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

describe('CourseLearningStatusTab — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getTeacherStudents.mockResolvedValue({ data: [] })
  })

  it('双路均失败：给出持久提示，统计卡标失败，空态不谎称「暂无学生数据」', async () => {
    mocks.getClassAnalytics.mockRejectedValue(new Error('500'))
    mocks.getAllStudents.mockRejectedValue(new Error('500'))
    const wrapper = mountTab()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.get('.stats-stub').attributes('data-failed')).toBe('true')
    expect(wrapper.get('.table-stub').text()).toContain(HONEST_EMPTY)
    expect(wrapper.text()).not.toContain(DEFAULT_EMPTY)
  })

  it('串味锁定：仅班级路失败时，学生表空态保留原语义（不被班级失败污染）', async () => {
    mocks.getClassAnalytics.mockRejectedValue(new Error('500'))
    mocks.getAllStudents.mockResolvedValue([])
    const wrapper = mountTab()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.get('.stats-stub').attributes('data-failed')).toBe('true')
    expect(wrapper.get('.table-stub').text()).toContain(DEFAULT_EMPTY)
    expect(wrapper.get('.table-stub').text()).not.toContain(HONEST_EMPTY)
  })

  it('串味锁定：仅学生路失败时，统计卡仍按班级真实数据渲染、不显 --', async () => {
    mocks.getClassAnalytics.mockResolvedValue({ data: { activeStudents: 8 } })
    mocks.getAllStudents.mockRejectedValue(new Error('500'))
    const wrapper = mountTab()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.get('.stats-stub').attributes('data-failed')).toBe('false')
    expect(wrapper.get('.stats-stub').text()).toContain('8')
    expect(wrapper.get('.table-stub').text()).toContain(HONEST_EMPTY)
  })

  it('阴性对照：双路均成功（空数据）时无提示，统计卡正常，空态保留原语义', async () => {
    mocks.getClassAnalytics.mockResolvedValue({ data: {} })
    mocks.getAllStudents.mockResolvedValue([])
    const wrapper = mountTab()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.get('.stats-stub').attributes('data-failed')).toBe('false')
    expect(wrapper.get('.table-stub').text()).toContain(DEFAULT_EMPTY)
    expect(wrapper.text()).not.toContain('未能加载')
  })

  it('失败后再成功：提示与失败标记必须被清空（每次 load 复位）', async () => {
    mocks.getClassAnalytics.mockRejectedValueOnce(new Error('500')).mockResolvedValue({ data: {} })
    mocks.getAllStudents.mockRejectedValueOnce(new Error('500')).mockResolvedValue([])

    const first = mountTab()
    await flushPromises()
    expect(first.find('.alert-stub').exists()).toBe(true)
    expect(first.get('.stats-stub').attributes('data-failed')).toBe('true')

    const second = mountTab()
    await flushPromises()

    expect(second.find('.alert-stub').exists()).toBe(false)
    expect(second.get('.stats-stub').attributes('data-failed')).toBe('false')
    expect(second.get('.table-stub').text()).toContain(DEFAULT_EMPTY)
  })
})

describe('ClsStatsRow — 统计卡失败显 --', () => {
  it('loadFailed 时四个读数均显 --', () => {
    const wrapper = mount(ClsStatsRow, {
      props: {
        loadFailed: true,
        overview: { activeStudents: 8, avgOnlineHours: 2.5, avgEngagement: 60, avgCompletion: 50 },
      },
    })
    expect((wrapper.text().match(/--/g) || []).length).toBe(4)
    expect(wrapper.text()).not.toContain('60%')
    expect(wrapper.text()).not.toContain('50%')
  })

  it('阴性对照：未失败时按真实读数渲染', () => {
    const wrapper = mount(ClsStatsRow, {
      props: {
        overview: { activeStudents: 8, avgOnlineHours: 2.5, avgEngagement: 60, avgCompletion: 50 },
      },
    })
    expect(wrapper.text()).toContain('8')
    expect(wrapper.text()).toContain('2.5h')
    expect(wrapper.text()).toContain('60%')
    expect(wrapper.text()).toContain('50%')
    expect(wrapper.text()).not.toContain('--')
  })
})
