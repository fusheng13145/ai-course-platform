import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

/**
 * 管理端「数据分析」接真判据（M-12g）。
 *
 * 平台总览 / 课程排行 / 用户增长 / 平台热力图是四个独立端点：
 * 任一路失败只能影响自己那块，不能把其余读数一起打成空态，也不能回退成假 0。
 */
const analyticsApi = vi.hoisted(() => ({
  getPlatformOverview: vi.fn(),
  getCourseRanking: vi.fn(),
  getUserGrowth: vi.fn(),
  getPlatformHeatmap: vi.fn(),
}))

vi.mock('@/api/analytics', () => ({ ...analyticsApi, default: analyticsApi }))

import AdminAnalyticsPanel from '../views/specials/AdminAnalyticsPanel.vue'

const envelope = (data: unknown) => ({ code: 200, message: 'ok', data })

const OVERVIEW = {
  totalUsers: 120,
  activeUsers: 110,
  totalCourses: 9,
  activeCourses: 7,
  totalEnrollments: 450,
  activeLearningUsers: 88,
  completionRate: 12.34,
}

const RANKING = [
  { courseId: 4178, courseName: '计算机网络', courseCode: 'CN202', enrollmentCount: 5 },
  { courseId: 1, courseName: 'Python 程序设计', courseCode: 'CS101', enrollmentCount: 3 },
]

const GROWTH = Array.from({ length: 30 }, (_, index) => ({
  date: `2026-11-${String(index + 1).padStart(2, '0')}`,
  newUsers: index + 1,
}))

const HEATMAP = Array.from({ length: 7 }, (_, day) =>
  Array.from({ length: 4 }, (_, slot) => ({
    dayOfWeek: day,
    hour: slot,
    value: day * 4 + slot,
  })),
).flat()

beforeEach(() => {
  vi.clearAllMocks()
  analyticsApi.getPlatformOverview.mockResolvedValue(envelope(OVERVIEW))
  analyticsApi.getCourseRanking.mockResolvedValue(envelope(RANKING))
  analyticsApi.getUserGrowth.mockResolvedValue(envelope(GROWTH))
  analyticsApi.getPlatformHeatmap.mockResolvedValue(envelope(HEATMAP))
})

describe('管理端 · 数据分析', () => {
  it('挂载即并发读四个平台端点，读数来自接口', async () => {
    const wrapper = mount(AdminAnalyticsPanel)
    await flushPromises()

    expect(analyticsApi.getPlatformOverview).toHaveBeenCalledTimes(1)
    expect(analyticsApi.getCourseRanking).toHaveBeenCalledTimes(1)
    expect(analyticsApi.getUserGrowth).toHaveBeenCalledWith(30)
    expect(analyticsApi.getPlatformHeatmap).toHaveBeenCalledWith({ days: 30 })

    expect(wrapper.text()).toContain('120')
    expect(wrapper.text()).toContain('7')
    expect(wrapper.text()).toContain('12.34%')
    expect(wrapper.text()).toContain('计算机网络')
    expect(wrapper.text()).toContain('450')
    expect(wrapper.text()).not.toContain('示例数据')
  })

  it('切换统计区间后增长与热力图按新天数重拉', async () => {
    const wrapper = mount(AdminAnalyticsPanel)
    await flushPromises()

    await wrapper.find('select').setValue('7')
    await flushPromises()

    expect(analyticsApi.getUserGrowth).toHaveBeenLastCalledWith(7)
    expect(analyticsApi.getPlatformHeatmap).toHaveBeenLastCalledWith({ days: 7 })
  })

  it('单路失败不拖垮其余三块，也不把失败伪装成空数据', async () => {
    analyticsApi.getPlatformOverview.mockRejectedValueOnce(new Error('overview down'))
    const wrapper = mount(AdminAnalyticsPanel)
    await flushPromises()

    expect(wrapper.text()).toContain('平台总览加载失败')
    expect(wrapper.text()).toContain('计算机网络')
    expect(wrapper.text()).toContain('用户增长')
    expect(wrapper.text()).not.toContain('暂无注册增长记录')
  })
})
