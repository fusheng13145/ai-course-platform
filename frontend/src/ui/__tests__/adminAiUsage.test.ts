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
const adminApi = vi.hoisted(() => ({
  getAIUsageByCourse: vi.fn(),
  getAIUsageByDay: vi.fn(),
  getAIUsageByScene: vi.fn(),
  getAiRestrictions: vi.fn(),
  restrictAiGeneration: vi.fn(),
  unrestrictAiGeneration: vi.fn(),
  getConfigs: vi.fn(),
  updateConfig: vi.fn(),
}))
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
  adminApi.getAIUsageByDay.mockResolvedValue(
    envelope({
      rangeDays: 30,
      days: [
        {
          day: '2026-11-06',
          calls: 12,
          tokens: 900,
          avgDurationMs: 400,
          successCalls: 12,
          successRate: 1,
        },
      ],
    }),
  )
  adminApi.getAIUsageByScene.mockResolvedValue(
    envelope({
      rangeDays: 30,
      scenes: [
        {
          scene: 'QA_ASK',
          calls: 120,
          tokens: 64000,
          avgDurationMs: 700,
          successCalls: 118,
          successRate: 0.9833,
        },
      ],
    }),
  )
  adminApi.getAiRestrictions.mockResolvedValue(envelope([]))
  adminApi.getConfigs.mockResolvedValue(
    envelope([{ configKey: 'ai.question-generation.teacher-enabled', value: 'true' }]),
  )
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

  it('collectionNote 原样透出，且按日/按能力分区可见（2026-11-07 P2 批次接真）', async () => {
    const wrapper = mountPage()
    await settle()
    const text = wrapper.text()
    expect(text).toContain(COLLECTION_NOTE)
    expect(text).toContain('按日趋势')
    expect(text).toContain('按能力聚合')
    expect(text).toContain('课程问答') // scene=QA_ASK 的中文映射
    // 出题治理区：总闸读数 + 名单空态
    expect(text).toContain('出题治理')
    expect(text).toContain('教师出题允许')
    expect(text).toContain('没有逐人限制')
    wrapper.unmount()
  })

  it('按日趋势以柱图渲染并补零区间（有调用日显示峰值读数）', async () => {
    const wrapper = mountPage()
    await settle()
    expect(adminApi.getAIUsageByDay).toHaveBeenCalledWith({ days: 30 })
    expect(wrapper.find('.bar-chart').exists()).toBe(true)
    expect(wrapper.find('.bar-chart').text()).toContain('12')
    wrapper.unmount()
  })

  it('出题治理：全禁/恢复写入 ai.question-generation.teacher-enabled 配置键', async () => {
    adminApi.updateConfig.mockResolvedValue(envelope(null))
    const wrapper = mountPage()
    await settle()
    const toggle = wrapper.findAll('button').find((node) => node.text() === '全禁教师出题')
    expect(toggle, '缺少总闸切换按钮').toBeTruthy()
    await toggle!.trigger('click')
    await settle()
    expect(adminApi.updateConfig).toHaveBeenCalledWith('ai.question-generation.teacher-enabled', {
      value: 'false',
      group: 'ai',
    })
    expect(toast).toHaveBeenCalledWith(expect.stringContaining('全禁'), 'ok')
    wrapper.unmount()
  })

  it('出题治理：加入限制名单调用 POST {userId, reason}', async () => {
    adminApi.restrictAiGeneration.mockResolvedValue(envelope({ userId: 7 }))
    const wrapper = mountPage()
    await settle()
    const idInput = wrapper.find('input[aria-label="教师用户 ID"]')
    await idInput.setValue('7')
    const reasonInput = wrapper.find('input[aria-label="限制原因"]')
    await reasonInput.setValue('测试原因')
    const submit = wrapper.findAll('button').find((node) => node.text() === '加入限制名单')
    await submit!.trigger('click')
    await settle()
    expect(adminApi.restrictAiGeneration).toHaveBeenCalledWith(7, { reason: '测试原因' })
    expect(toast).toHaveBeenCalledWith('已加入限制名单', 'ok')
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
    // 只断言「按课程聚合」分区：页面还有按日/按能力/出题治理分区，各自有独立口径
    const courseSection = wrapper.findAll('section.table-card')[0]
    expect(courseSection.find('table').exists()).toBe(false)
    expect(courseSection.text()).toContain('未产生 AI 调用')
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
