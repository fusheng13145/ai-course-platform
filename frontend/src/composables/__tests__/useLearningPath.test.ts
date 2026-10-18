import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * useLearningPath 单元测试
 * <p>本轮（2026-10-13）为学习路径页补「加载失败可见」契约：失败时路径列表被置空，
 * 页面只剩"暂无学习路径" + "AI 为我生成"按钮——学生会误判为"我确实没有学习路径"，
 * 进而重复触发生成。故本组合式函数暴露 `loadError`，由页面渲染持久提示并切换空态文案。</p>
 */

const mocks = vi.hoisted(() => ({
  getMyPaths: vi.fn(),
  getPathById: vi.fn(),
  getPathNodes: vi.fn(),
  getDailyTasks: vi.fn(),
}))

vi.mock('@/api/learningPath', () => ({
  default: {
    getMyPaths: mocks.getMyPaths,
    getPathById: mocks.getPathById,
    getPathNodes: mocks.getPathNodes,
    getDailyTasks: mocks.getDailyTasks,
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
  ElMessageBox: { confirm: vi.fn().mockResolvedValue(undefined) },
}))

import { useLearningPath } from '@/composables/useLearningPath'

/** 组合式函数含生命周期钩子，须挂宿主组件驱动（本用例显式调用 loadPaths，不依赖自动触发） */
function withSetup() {
  let state!: ReturnType<typeof useLearningPath>
  mount(
    defineComponent({
      setup() {
        state = useLearningPath()
        return () => h('div')
      },
    }),
  )
  return state
}

describe('useLearningPath — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('路径列表请求失败时必须给出持久提示，且不得表现为"没有学习路径"', async () => {
    mocks.getMyPaths.mockRejectedValue(new Error('500'))
    const state = withSetup()

    await state.loadPaths()
    await flushPromises()

    expect(state.loadError.value).toBe('学习路径加载失败，请检查网络后重试')
    expect(state.paths.value).toEqual([])
    expect(state.loading.value).toBe(false)
  })

  it('阴性对照：加载成功（空列表）时不得出现失败提示', async () => {
    mocks.getMyPaths.mockResolvedValue({ data: [] })
    const state = withSetup()

    await state.loadPaths()
    await flushPromises()

    expect(state.loadError.value).toBe('')
    expect(state.paths.value).toEqual([])
  })

  it('失败后再成功：提示必须被清空（证明每次加载都复位，不是一次失败永久残留）', async () => {
    mocks.getMyPaths.mockRejectedValueOnce(new Error('500'))
    const state = withSetup()
    await state.loadPaths()
    expect(state.loadError.value).not.toBe('')

    mocks.getMyPaths.mockResolvedValue({ data: [] })
    await state.loadPaths()

    expect(state.loadError.value).toBe('')
  })
})
