import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CourseAnalytics.vue（课程分析页）与后端 Analytics 契约的对齐回归 —— 缺陷 B4。
 *
 * <p>取证结论（2026-10-10）：该页"60 学生的课显示 0/无数据"**不是后端聚合问题**，而是前端读了一组
 * 后端从未返回的键。两个端点的真实形状（源：`CourseAnalyticsComponent.java:136-168` 与 `:83-131`）：</p>
 *
 * <pre>
 * GET /analytics/courses/{id}
 *   courseInfo / enrollmentCount / taskCount / averageProgress / averageScore
 * GET /analytics/courses/{id}/grades/histogram
 *   total / average / maxScore / minScore / distribution(Map<String,Long>，只含非空分数段)
 *   / passRate / excellentRate
 * </pre>
 *
 * <p>而页面原先读 `totalStudents|studentCount`、`activeStudents`（后端根本没有该字段）、`avgScore`、
 * `completionRate`、`h.buckets`（数组）、`extra.count` → 四张指标卡恒 0/'--'，直方图恒空态，
 * 参考人数整块隐藏。两条必须钉住的口径：
 * 1. `passRate/excellentRate` 后端已是 0–100 百分数，不是 0–1 小数；
 * 2. `averageProgress`/`progress` 在库里是百分比（V1__init_schema.sql:271），不是 0–1。</p>
 */

const mocks = vi.hoisted(() => ({
  getCourseAnalytics: vi.fn(),
  getGradeHistogram: vi.fn(),
  route: { params: { id: '4007' } },
}))

vi.mock('@/api/analytics', () => ({
  default: {
    getCourseAnalytics: mocks.getCourseAnalytics,
    getGradeHistogram: mocks.getGradeHistogram,
  },
}))
vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), {
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    closeAll: vi.fn(),
  }),
}))

import CourseAnalytics from '@/views/teacher/cockpit/CourseAnalytics.vue'
import StatCard from '@/components/ui/StatCard.vue'

/**
 * jsdom 不提供 window.matchMedia，而页面在 setup 里就构造图表配置，会经
 * chartTheme.resolveTheme 读系统偏好（与 useKnowledgeTab.test.ts:55 同一桩法）。
 */
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

/** 后端 /analytics/courses/{id} 的真实响应（HashMap 序列化结果，无 totalStudents/activeStudents） */
const COURSE_ANALYSIS = {
  courseInfo: { id: 4007, courseName: '示范课' },
  enrollmentCount: 60,
  taskCount: 24,
  averageProgress: 42.5,
  averageScore: 85.5,
}

/** 后端 histogram 的真实响应：distribution 是 Map，且只有非空段 */
const HISTOGRAM = {
  total: 20,
  average: 78.6,
  maxScore: 98,
  minScore: 35,
  distribution: {
    '90-100（优秀）': 2,
    '0-59（不及格）': 3,
    '60-69（及格）': 5,
    '80-89（良好）': 10,
  },
  passRate: 85,
  excellentRate: 10,
}

async function mountPage() {
  return mount(CourseAnalytics, {
    shallow: true,
    global: {
      // v-chart 用声明了 option prop 的桩件：shallow 自动生成的桩不声明 props，
      // 读 props('option') 会拿到 undefined（值落在 attrs 上），断言会假绿/假红。
      stubs: {
        'v-chart': { name: 'v-chart', props: ['option'], template: '<div class="chart-stub" />' },
        'el-icon': true,
        'el-progress': true,
        'el-empty': true,
      },
      // element-plus 的 loading 指令在全局插件里注册，测试环境需自带，否则 Vue 告警淹没断言
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

function cardValues(wrapper: Awaited<ReturnType<typeof mountPage>>) {
  return wrapper.findAllComponents(StatCard).map((c) => ({
    label: c.props('label'),
    value: c.props('value'),
  }))
}

describe('CourseAnalytics — 指标卡读后端真实键（缺陷 B4 写侧读数）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getCourseAnalytics.mockResolvedValue({ code: 200, data: COURSE_ANALYSIS })
    mocks.getGradeHistogram.mockResolvedValue({ code: 200, data: HISTOGRAM })
  })

  it('选课学生取 enrollmentCount，不再恒 0', async () => {
    const wrapper = await mountPage()
    await flushPromises()

    expect(mocks.getCourseAnalytics).toHaveBeenCalledWith('4007')
    const cards = cardValues(wrapper)
    expect(cards.find((c) => c.label === '选课学生')?.value).toBe(60)
  })

  it('平均分取 averageScore、平均进度取 averageProgress（百分比口径，不再显示 --）', async () => {
    const wrapper = await mountPage()
    await flushPromises()

    const cards = cardValues(wrapper)
    expect(cards.find((c) => c.label === '平均分')?.value).toBe(85.5)
    expect(cards.find((c) => c.label === '平均进度')?.value).toBe('42.5%')
  })

  it('不再渲染后端从未返回的活跃学生卡（避免出现恒 0 的假读数）', async () => {
    const wrapper = await mountPage()
    await flushPromises()

    expect(cardValues(wrapper).map((c) => c.label)).not.toContain('活跃学生')
  })
})

describe('CourseAnalytics — 直方图由 distribution 映射为有序 buckets', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getCourseAnalytics.mockResolvedValue({ code: 200, data: COURSE_ANALYSIS })
    mocks.getGradeHistogram.mockResolvedValue({ code: 200, data: HISTOGRAM })
  })

  it('五个分数段固定顺序、缺失段补 0（后端 Map 只含非空段且无序）', async () => {
    const wrapper = await mountPage()
    await flushPromises()

    const chart = wrapper.findComponent({ name: 'v-chart' })
    expect(chart.exists()).toBe(true)
    const option = chart.props('option')
    expect(option.xAxis.data).toEqual([
      '0-59（不及格）',
      '60-69（及格）',
      '70-79（中等）',
      '80-89（良好）',
      '90-100（优秀）',
    ])
    expect(option.series[0].data).toEqual([3, 5, 0, 10, 2])
  })

  it('参考人数块读 total/average/maxScore/minScore（原先读不存在的 extra.count 而整块隐藏）', async () => {
    const wrapper = await mountPage()
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('参考人数')
    expect(text).toMatch(/20\s*参考人数|参考人数\s*20/)
    expect(text).toContain('78.6')
    expect(text).toContain('98')
    expect(text).toContain('35')
  })

  it('及格率/优秀率按 0–100 百分数直显，不二次放大', async () => {
    const wrapper = await mountPage()
    await flushPromises()

    expect(wrapper.text()).toContain('85%')
    expect(wrapper.text()).toContain('10%')
  })

  it('无人有成绩时（total=0）回到空态而不是画一屏全 0 柱', async () => {
    mocks.getGradeHistogram.mockResolvedValue({
      code: 200,
      data: {
        total: 0,
        average: 0,
        maxScore: 0,
        minScore: 0,
        distribution: {},
        passRate: 0,
        excellentRate: 0,
      },
    })
    const wrapper = await mountPage()
    await flushPromises()

    expect(wrapper.findComponent({ name: 'v-chart' }).exists()).toBe(false)
    expect(wrapper.text()).not.toContain('参考人数')
  })
})
