import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * useAIUsageByCourse 单元测试（跨课程 AI 统计）
 * <p>钉两类契约：① 加载失败必须"失败可见"（loadError 非空 + 统计被清空，不得保留上一次数据或表现为"没有调用"）；
 * ② 诚实边界——后端未下发 collectionNote 时由前端给同一句兜底，不许页面出现空说明或自行编造趋势。</p>
 */

const mocks = vi.hoisted(() => ({
  getAIUsageByCourse: vi.fn(),
}))

vi.mock('@/api/admin', () => ({
  default: { getAIUsageByCourse: mocks.getAIUsageByCourse },
}))

import { useAIUsageByCourse } from '@/composables/useAIUsageByCourse'

/** 组合式函数含 onMounted，须由宿主组件驱动（本用例显式调 load，不依赖自动触发） */
function withSetup() {
  let state!: ReturnType<typeof useAIUsageByCourse>
  mount(
    defineComponent({
      setup() {
        state = useAIUsageByCourse()
        return () => h('div')
      },
    }),
  )
  return state
}

const PAYLOAD = {
  rangeDays: 30,
  collectionNote: '仅采集自 2026-10-13 埋点上线后的调用；此前的历史调用未留痕，不可回填。',
  totalCalls: 4,
  totalTokens: 350,
  distinctCourses: 1,
  unboundCalls: 1,
  avgDurationMs: 125,
  successRate: 0.75,
  courses: [
    {
      courseId: 4007,
      courseName: '计算机网络',
      calls: 3,
      tokens: 300,
      avgDurationMs: 100,
      successCalls: 3,
      successRate: 1,
    },
    {
      courseId: null,
      courseName: null,
      calls: 1,
      tokens: 50,
      avgDurationMs: 200,
      successCalls: 0,
      successRate: 0,
    },
  ],
}

describe('useAIUsageByCourse — 聚合读取与失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('默认按近 30 天请求，并解包 data 信封（含"未绑定课程"行）', async () => {
    mocks.getAIUsageByCourse.mockResolvedValue({ data: PAYLOAD })
    const state = withSetup()

    await state.load()
    await flushPromises()

    expect(mocks.getAIUsageByCourse).toHaveBeenCalledWith({ days: 30 })
    expect(state.loadError.value).toBe('')
    expect(state.stats.value.totalCalls).toBe(4)
    expect(state.rows.value).toHaveLength(2)
    // 未绑定课程行必须保留（丢掉它总量就对不上），且进图时给出可读标签
    expect(state.topCourses.value.map((d) => d.label)).toEqual(['计算机网络', '未绑定课程'])
    expect(state.hasData.value).toBe(true)
  })

  it('请求失败时必须给出持久提示并清空统计，不得表现为"没有调用"', async () => {
    mocks.getAIUsageByCourse.mockRejectedValue(new Error('500'))
    const state = withSetup()

    await state.load()
    await flushPromises()

    expect(state.loadError.value).toBe('AI 使用统计加载失败，请检查网络后重试')
    expect(state.stats.value).toEqual({})
    expect(state.hasData.value).toBe(false)
    expect(state.loading.value).toBe(false)
  })

  it('失败后再成功：提示必须被清空（证明每次加载都复位，不是一次失败永久残留）', async () => {
    mocks.getAIUsageByCourse.mockRejectedValueOnce(new Error('500'))
    const state = withSetup()
    await state.load()
    expect(state.loadError.value).not.toBe('')

    mocks.getAIUsageByCourse.mockResolvedValue({ data: PAYLOAD })
    await state.load()

    expect(state.loadError.value).toBe('')
    expect(state.stats.value.totalCalls).toBe(4)
  })

  it('切换时间窗：按所选天数重新请求（days 随之下沉到入参）', async () => {
    mocks.getAIUsageByCourse.mockResolvedValue({ data: PAYLOAD })
    const state = withSetup()

    await state.load(7)
    await flushPromises()

    expect(state.days.value).toBe(7)
    expect(mocks.getAIUsageByCourse).toHaveBeenCalledWith({ days: 7 })
  })

  it('诚实口径：后端未下发 collectionNote 时给同一句兜底，绝不出现空说明', async () => {
    mocks.getAIUsageByCourse.mockResolvedValue({ data: { totalCalls: 0, courses: [] } })
    const state = withSetup()

    await state.load()
    await flushPromises()

    expect(state.collectionNote.value).toContain('2026-10-13')
    expect(state.collectionNote.value).toContain('不可回填')
  })

  it('后端下发 collectionNote 时原样采用（不在前端另造一份口径）', async () => {
    mocks.getAIUsageByCourse.mockResolvedValue({
      data: { ...PAYLOAD, collectionNote: '后端口径声明' },
    })
    const state = withSetup()

    await state.load()
    await flushPromises()

    expect(state.collectionNote.value).toBe('后端口径声明')
  })
})
