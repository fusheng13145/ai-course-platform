import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CourseGrades.vue（课程成绩）— 加载失败可见（2026-10-13）
 * <p>原实现成绩方案 / 成绩报告加载失败仅 logger.error 后静默 →
 * 页面显示「该课程尚未配置成绩方案 / 暂无学生成绩」，属误导。</p>
 *
 * <p>方案与报告两路失败信号独立记录（policyError / reportError）再聚合为 loadError，
 * 以便空态分别诚实化；本用例分别锁定两路。</p>
 */

const mocks = vi.hoisted(() => ({
  getGradePolicy: vi.fn(),
  getCourseReport: vi.fn(),
  route: { params: { id: '4007' } },
}))

vi.mock('@/api/grade', () => ({
  default: {
    getGradePolicy: mocks.getGradePolicy,
    getCourseReport: mocks.getCourseReport,
  },
}))
vi.mock('vue-router', () => ({ useRoute: () => mocks.route }))
vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    closeAll: vi.fn(),
  }),
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') },
}))

import CourseGrades from '@/views/teacher/cockpit/CourseGrades.vue'

const POLICY = { id: 1, name: '默认方案', components: [] }

function mountPage() {
  return mount(CourseGrades, {
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        CgReportTable: {
          props: {
            policy: Object,
            report: Array,
            reportLoading: Boolean,
            emptyText: String,
            policyEmptyText: String,
          },
          template:
            '<div class="cg-stub"><span class="cg-policy">{{ policyEmptyText }}</span><span class="cg-report">{{ emptyText }}</span></div>',
        },
        'el-button': true,
        'el-icon': true,
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

describe('CourseGrades — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('成绩方案加载失败时给出持久提示，且「无方案」空态不得谎称「尚未配置」', async () => {
    mocks.getGradePolicy.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').text()).toBe('成绩方案加载失败，请检查网络后重试')
    expect(wrapper.find('.cg-policy').text()).toBe('成绩方案未能加载，请查看上方提示后重试')
    expect(wrapper.find('.cg-report').text()).toBe('暂无学生成绩')
  })

  it('成绩报告加载失败时给出持久提示，且报告空态不得谎称「暂无学生成绩」', async () => {
    mocks.getGradePolicy.mockResolvedValue({ data: POLICY })
    mocks.getCourseReport.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').text()).toBe('成绩报告加载失败，请检查网络后重试')
    expect(wrapper.find('.cg-report').text()).toBe('成绩报告未能加载，请查看上方提示后重试')
    expect(wrapper.find('.cg-policy').text()).toBe('该课程尚未配置成绩方案')
  })

  it('阴性对照：两路都成功时不得出现失败提示，且空态保持默认文案', async () => {
    mocks.getGradePolicy.mockResolvedValue({ data: POLICY })
    mocks.getCourseReport.mockResolvedValue({ data: { records: [] } })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.find('.cg-report').text()).toBe('暂无学生成绩')
    expect(wrapper.find('.cg-policy').text()).toBe('该课程尚未配置成绩方案')
  })
})
