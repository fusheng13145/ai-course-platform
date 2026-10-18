import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * useWrongQuestions 单元测试
 * <p>本轮（2026-10-13）为错题页补「加载失败可见」契约：接口层虽已弹瞬时 toast，
 * 但 toast 消失后列表只剩"暂无错题记录，继续保持！"，学生会误判"我没有错题"。
 * 故本组合式函数暴露 `loadError`，由页面渲染持久提示并切换空态文案。</p>
 *
 * <p>⚠️ 本用例锁定的是一条**容易写错**的实现点：`loadData` 用 `Promise.allSettled`，
 * 请求失败**不会**抛到 catch，只能按 `status === 'rejected'` 判定。若有人日后误把
 * 该判定挪进 catch（或删掉 else 分支），用例 1 会立刻失败。</p>
 */

const mocks = vi.hoisted(() => ({
  getWrongQuestionStats: vi.fn(),
  getWrongQuestions: vi.fn(),
  getByKnowledgePoints: vi.fn(),
  markMastered: vi.fn(),
  unmarkMastered: vi.fn(),
}))

vi.mock('@/api/wrongQuestion', () => ({
  default: {
    getWrongQuestionStats: mocks.getWrongQuestionStats,
    getWrongQuestions: mocks.getWrongQuestions,
    getByKnowledgePoints: mocks.getByKnowledgePoints,
    markMastered: mocks.markMastered,
    unmarkMastered: mocks.unmarkMastered,
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    warning: vi.fn(),
    closeAll: vi.fn(),
  }),
}))

import { useWrongQuestions } from '@/composables/useWrongQuestions'

/** 组合式函数含 onMounted，须挂一个宿主组件驱动真实生命周期 */
function withSetup() {
  let state!: ReturnType<typeof useWrongQuestions>
  mount(
    defineComponent({
      setup() {
        state = useWrongQuestions()
        return () => h('div')
      },
    }),
  )
  return state
}

describe('useWrongQuestions — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getWrongQuestionStats.mockResolvedValue({ data: { total: 3, mastered: 1 } })
    mocks.getByKnowledgePoints.mockResolvedValue({ data: [] })
  })

  it('错题列表请求失败时必须给出持久提示，且不得表现为"没有错题"', async () => {
    mocks.getWrongQuestions.mockRejectedValue(new Error('500'))
    const state = withSetup()
    await flushPromises()

    expect(state.loadError.value).toBe('错题列表加载失败，请检查网络后重试')
    expect(state.questions.value).toEqual([])
    expect(state.loading.value).toBe(false)
  })

  it('阴性对照：加载成功时不得出现失败提示（证明用例不是恒真）', async () => {
    mocks.getWrongQuestions.mockResolvedValue({
      data: [{ id: 1, content: '题干', answer: 'A' }],
    })
    const state = withSetup()
    await flushPromises()

    expect(state.loadError.value).toBe('')
    expect(state.questions.value).toHaveLength(1)
    expect(state.questions.value[0].questionContent).toBe('题干')
  })

  it('统计接口失败不应误报列表失败（两个信号互不串味）', async () => {
    mocks.getWrongQuestionStats.mockRejectedValue(new Error('500'))
    mocks.getWrongQuestions.mockResolvedValue({ data: [] })
    const state = withSetup()
    await flushPromises()

    expect(state.loadError.value).toBe('')
  })
})
