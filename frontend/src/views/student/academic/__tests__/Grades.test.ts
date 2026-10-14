import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * Grades.vue（student 我的成绩）— 加载失败诚实化（D2-1）
 *
 * <p>原实现页面级已有 el-alert，但：统计卡在失败时显示「平均分 0 / 已完成考试 0 /
 * 班级排名 --」中的前两项为谎报读数；子面板 SgChartsPanel / SgCourseGrades 的空态
 * 文案硬编码，失败时仍显示「暂无成绩记录 / 暂无考试记录 / 选课不足」，学生会误判
 * 「自己还没考过试」。现统计卡失败显 '--'，双态空文案经 props 下传给子面板。</p>
 */

const mocks = vi.hoisted(() => ({
  getAllCourses: vi.fn(),
  getAllExamPapers: vi.fn(),
  getMyExamSessions: vi.fn(),
}))

vi.mock('@/api/course', () => ({
  default: { getAllCourses: mocks.getAllCourses },
}))

vi.mock('@/api/exam', () => ({
  default: {
    getAllExamPapers: mocks.getAllExamPapers,
    getMyExamSessions: mocks.getMyExamSessions,
  },
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: {}, query: {} }),
}))

import Grades from '@/views/student/academic/Grades.vue'

const LOAD_FAILED_TEXT = '成绩数据未能加载，请查看上方提示后重试'

function mountPage() {
  return mount(Grades, {
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        PageHeader: {
          props: ['title', 'subtitle'],
          template: '<div class="ph-stub"><slot /></div>',
        },
        StatCard: {
          props: ['label', 'value', 'icon'],
          template: '<div class="sc-stub" :data-label="label">{{ label }}|{{ value }}</div>',
        },
        // 子面板桩：把收到的双态空文案props暴露为属性，供断言
        SgChartsPanel: {
          props: ['loading', 'groupedByCourse', 'list', 'radarEmptyText', 'trendEmptyText'],
          template:
            '<div class="sg-charts" :data-radar="radarEmptyText" :data-trend="trendEmptyText" />',
        },
        SgCourseGrades: {
          props: ['loading', 'groupedByCourse', 'list', 'showTable', 'emptyText'],
          template: '<div class="sg-grades" :data-empty="emptyText" />',
        },
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

describe('Grades 加载失败反馈（D2-1）', () => {
  beforeEach(() => {
    mocks.getAllCourses.mockReset()
    mocks.getAllExamPapers.mockReset()
    mocks.getMyExamSessions.mockReset()
  })

  it('接口失败：alert + 统计读数显 -- + 子面板空态诚实化', async () => {
    mocks.getAllCourses.mockResolvedValue([])
    mocks.getAllExamPapers.mockResolvedValue([])
    mocks.getMyExamSessions.mockRejectedValue(new Error('network down'))
    const wrapper = mountPage()
    await flushPromises()

    const alert = wrapper.find('.alert-stub')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toBe('成绩数据加载失败，请检查网络后重试')

    const stats = wrapper.findAll('.sc-stub')
    expect(stats[0].text()).toBe('平均分|--')
    expect(stats[1].text()).toBe('已完成考试|--')
    expect(stats[2].text()).toBe('班级排名|--')

    expect(wrapper.find('.sg-grades').attributes('data-empty')).toBe(LOAD_FAILED_TEXT)
    expect(wrapper.find('.sg-charts').attributes('data-radar')).toBe(LOAD_FAILED_TEXT)
    expect(wrapper.find('.sg-charts').attributes('data-trend')).toBe(LOAD_FAILED_TEXT)
  })

  it('加载成功且无成绩：无 alert，读数真实（0），子面板用正常空态文案', async () => {
    mocks.getAllCourses.mockResolvedValue([])
    mocks.getAllExamPapers.mockResolvedValue([])
    mocks.getMyExamSessions.mockResolvedValue({ data: [] })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)

    const stats = wrapper.findAll('.sc-stub')
    expect(stats[0].text()).toBe('平均分|0')
    expect(stats[1].text()).toBe('已完成考试|0')
    expect(stats[2].text()).toBe('班级排名|--')

    expect(wrapper.find('.sg-grades').attributes('data-empty')).toBe('暂无成绩记录')
    expect(wrapper.find('.sg-charts').attributes('data-trend')).toBe('暂无考试记录')
    expect(wrapper.find('.sg-charts').attributes('data-radar')).toBe(
      '选课不足（至少3门），暂无法生成多维对比分析',
    )
  })
})
