import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

/**
 * 管理端「AI 使用与治理」页（M-14）接真判据。
 *
 * 这一页此前是明确的「待扩展」空态，转真之后最大的风险不是画不出来，而是**画多了**：
 * by-course 端点只覆盖「按课程 × 近 N 天」这一个维度（数据源 ai_call_logs，2026-10-13 起埋点），
 * 既没有按能力/按日的细分，也不含配额与限制出题。页面若顺势补一张示例聚合表，
 * 就把「只有课程维度」说成了「AI 治理已具备」。所以这里的断言分两半：
 * 一半钉真读数（请求参数、KPI、表格行、未绑定课程行保留），
 * 一半钉口径（collectionNote 原样透出、不出现示例数据、按能力/按日明写待扩展）。
 */
const adminApi = vi.hoisted(() => ({ getAIUsageByCourse: vi.fn() }))
vi.mock('@/api/admin', () => ({ ...adminApi, default: adminApi }))

const toast = vi.hoisted(() => vi.fn())
vi.mock('../composables/ui', () => ({ useUi: () => ({ toast }) }))

import AiUsageBody from '../views/specials/AiUsageBody.vue'

const envelope = (data: unknown) => ({ code: 200, message: 'ok', data })

const COLLECTION_NOTE = '仅统计 2026-10-13 埋点上线后的调用，历史数据不可回填'

function payload(overrides: Record<string, unknown> = {}) {
  return {
    rangeDays: 30,
    since: '2026-10-06T00:00',
    until: '2026-11-04T18:00',
    collectionNote: COLLECTION_NOTE,
    totalCalls: 201,
    totalTokens: 128000,
    distinctCourses: 2,
    unboundCalls: 17,
    avgDurationMs: 812.35,
    successRate: 0.975,
    courses: [
      {
        courseId: 4047,
        courseName: '计算机网络',
        calls: 150,
        tokens: 96000,
        avgDurationMs: 790.2,
        successCalls: 148,
        successRate: 0.9867,
      },
      {
        // 真栈实测：未绑定课程那一行**整个没有 courseId 键**（后端 NON_NULL 序列化把 null 省了），
        // 不是 courseId:null。页面必须按「缺键」也算未绑定，否则会渲染成 "课程 ID undefined"。
        courseName: '未绑定课程',
        calls: 17,
        tokens: 8000,
        avgDurationMs: 640,
        successCalls: 16,
        successRate: 0.9412,
      },
    ],
    ...overrides,
  }
}

async function settle() {
  await flushPromises()
  await flushPromises()
}

function mountPage() {
  return mount(AiUsageBody)
}

beforeEach(() => {
  vi.clearAllMocks()
  adminApi.getAIUsageByCourse.mockResolvedValue(envelope(payload()))
})

describe('管理端 AI 用量：接真与口径边界', () => {
  it('挂载即按默认 30 天请求 by-course 端点', async () => {
    mountPage()
    await settle()
    expect(adminApi.getAIUsageByCourse).toHaveBeenCalledTimes(1)
    expect(adminApi.getAIUsageByCourse).toHaveBeenCalledWith({ days: 30 })
  })

  it('渲染总量 KPI 与课程行读数（成功率按比例显示，不是原样小数）', async () => {
    const wrapper = mountPage()
    await settle()
    const text = wrapper.text()
    expect(text).toContain('201') // 总调用
    expect(text).toContain('97.5%') // 成功率 0.975
    expect(text).toContain('计算机网络')
    expect(text).toContain('98.7%') // 单课程成功率 0.9867
    wrapper.unmount()
  })

  it('「未绑定课程」行必须保留（丢掉它总量就对不上）', async () => {
    const wrapper = mountPage()
    await settle()
    expect(wrapper.text()).toContain('未绑定课程')
    expect(wrapper.text()).toContain('17') // unboundCalls
    // 缺 courseId 键时不能渲染成「课程 ID undefined」
    expect(wrapper.text()).not.toContain('undefined')
    wrapper.unmount()
  })

  it('collectionNote 原样透出，且按能力/按日维度明写待扩展', async () => {
    const wrapper = mountPage()
    await settle()
    const text = wrapper.text()
    expect(text).toContain(COLLECTION_NOTE)
    expect(text).toContain('按能力')
    expect(text).toContain('待扩展')
    wrapper.unmount()
  })

  it('totalCalls=0 时不渲染课程表，只给「近 N 天无调用」空态', async () => {
    adminApi.getAIUsageByCourse.mockResolvedValue(
      envelope(
        payload({
          totalCalls: 0,
          totalTokens: 0,
          distinctCourses: 0,
          unboundCalls: 0,
          successRate: null,
          courses: [],
        }),
      ),
    )
    const wrapper = mountPage()
    await settle()
    expect(wrapper.find('table').exists()).toBe(false)
    expect(wrapper.text()).toContain('未产生 AI 调用')
    wrapper.unmount()
  })

  it('请求失败给出错误与重试；重试会再打一次接口', async () => {
    adminApi.getAIUsageByCourse.mockRejectedValueOnce(new Error('boom'))
    const wrapper = mountPage()
    await settle()
    const alert = wrapper.find('[role="alert"]')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('失败')
    adminApi.getAIUsageByCourse.mockResolvedValue(envelope(payload()))
    await alert.find('button').trigger('click')
    await settle()
    expect(adminApi.getAIUsageByCourse).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('计算机网络')
    wrapper.unmount()
  })

  it('切换统计窗（7／90 天）会带新 days 重新请求', async () => {
    const wrapper = mountPage()
    await settle()
    const button = wrapper.findAll('button').find((node) => node.text() === '近 7 天')
    expect(button, '缺少「近 7 天」窗口切换').toBeTruthy()
    await button!.trigger('click')
    await settle()
    expect(adminApi.getAIUsageByCourse).toHaveBeenLastCalledWith({ days: 7 })
    wrapper.unmount()
  })

  it('页面不出现「示例数据」字样（示例读数不得伪装成线上数据）', async () => {
    const wrapper = mountPage()
    await settle()
    expect(wrapper.text()).not.toContain('示例数据')
    wrapper.unmount()
  })
})
