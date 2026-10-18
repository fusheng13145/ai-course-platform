import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CourseOverview.vue（课程总览）— 加载失败可见（2026-10-14）
 *
 * <p>原实现 getCourseDashboard 失败仅 logger.error 后静默 → 各子块照常渲染
 * 「暂无风险学生 / 暂无章节」，教师会误判该课程确实没有数据。现补页面级 `loadError`
 * + 持久 el-alert，并把诚实文案经 `emptyText` 透传给 CoTrendRisk / CoChapterTree。</p>
 */

const mocks = vi.hoisted(() => ({
  getCourseDashboard: vi.fn(),
  route: { params: { id: '4007' } },
}))

vi.mock('@/api/teacher', () => ({ default: { getCourseDashboard: mocks.getCourseDashboard } }))
vi.mock('vue-router', () => ({ useRoute: () => mocks.route }))

import CourseOverview from '@/views/teacher/cockpit/CourseOverview.vue'

/** jsdom 无 window.matchMedia，而页面在 setup 里即构造图表主题配置 */
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

function mountPage() {
  return mount(CourseOverview, {
    shallow: true,
    global: {
      stubs: {
        // 自定义 el-alert 桩：渲染 title，便于断言持久失败提示
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        // 两个承载空态的子块用声明了 emptyText 的桩件渲染文案，
        // 以验证父组件是否把失败文案透传下去（失败不得显示成「暂无」）
        CoChapterTree: {
          name: 'CoChapterTree',
          props: ['chapterTree', 'emptyText'],
          template: '<div class="ct-stub">{{ emptyText }}</div>',
        },
        CoTrendRisk: {
          name: 'CoTrendRisk',
          props: ['trendOption', 'atRisk', 'emptyText'],
          template: '<div class="tr-stub">{{ emptyText }}</div>',
        },
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

describe('CourseOverview — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getCourseDashboard.mockResolvedValue({ code: 200, data: {} })
  })

  it('接口失败：给出持久提示，且两个空态子块不得谎称「暂无」', async () => {
    mocks.getCourseDashboard.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    expect(mocks.getCourseDashboard).toHaveBeenCalledWith('4007')
    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('课程总览加载失败，请检查网络后重试')
    // 失败文案须透传到子块空态
    expect(wrapper.get('.ct-stub').text()).toContain('章节未能加载')
    expect(wrapper.get('.tr-stub').text()).toContain('风险学生未能加载')
    expect(wrapper.text()).not.toContain('暂无章节')
    expect(wrapper.text()).not.toContain('暂无风险学生')
  })

  it('阴性对照：加载成功（空数据）时无失败提示，子块保留原空态语义', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.get('.ct-stub').text()).toContain('暂无章节')
    expect(wrapper.get('.tr-stub').text()).toContain('暂无风险学生')
  })

  it('失败后再成功：提示必须被清空（每次 load 复位）', async () => {
    mocks.getCourseDashboard
      .mockRejectedValueOnce(new Error('500'))
      .mockResolvedValue({ code: 200, data: {} })
    const first = mountPage()
    await flushPromises()
    expect(first.find('.alert-stub').exists()).toBe(true)

    const second = mountPage()
    await flushPromises()

    expect(second.find('.alert-stub').exists()).toBe(false)
    expect(second.text()).not.toContain('课程总览加载失败')
  })
})
