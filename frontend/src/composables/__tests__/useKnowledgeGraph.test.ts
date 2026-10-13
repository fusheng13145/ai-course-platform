import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'

/**
 * useKnowledgeGraph（教师知识图谱）单元测试
 * <p>本轮（2026-10-13）补「加载失败可见」契约：知识点列表与图谱数据两路加载失败后
 * 均静默置空 → 页面只剩「暂无知识点 / 暂无图谱数据」，教师会误判该课程确实没有数据。</p>
 *
 * <p>⚠️ 两路在 onCourseChange 内**并发**触发，故失败信号必须各自独立（pointsError / graphError）
 * 再以 computed loadError 聚合；用例 ⑤ 专门锁死「知识点成功、图谱失败」不得被成功方复位而串味。</p>
 */

const mocks = vi.hoisted(() => ({
  getAllMyCourses: vi.fn(),
  getKnowledgeOverview: vi.fn(),
  getKnowledgePoints: vi.fn(),
  getRelationGraphData: vi.fn(),
  createKnowledgePoint: vi.fn(),
  updateKnowledgePoint: vi.fn(),
  deleteKnowledgePoint: vi.fn(),
  extractKnowledgePoints: vi.fn(),
  planLearningPath: vi.fn(),
}))

vi.mock('@/api/course', () => ({ default: { getAllMyCourses: mocks.getAllMyCourses } }))
vi.mock('@/api/knowledge', () => ({
  default: {
    getKnowledgeOverview: mocks.getKnowledgeOverview,
    getKnowledgePoints: mocks.getKnowledgePoints,
    getRelationGraphData: mocks.getRelationGraphData,
    createKnowledgePoint: mocks.createKnowledgePoint,
    updateKnowledgePoint: mocks.updateKnowledgePoint,
    deleteKnowledgePoint: mocks.deleteKnowledgePoint,
    extractKnowledgePoints: mocks.extractKnowledgePoints,
    planLearningPath: mocks.planLearningPath,
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

// 图表主题（chartTheme）会读系统偏好，jsdom 无 matchMedia，需桩
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

import { useKnowledgeGraph } from '@/composables/useKnowledgeGraph'

/** 组合式函数只注册了状态（无 onMounted），挂宿主组件后显式触发加载 */
function withSetup() {
  let state!: ReturnType<typeof useKnowledgeGraph>
  mount(
    defineComponent({
      setup() {
        state = useKnowledgeGraph()
        return () => h('div')
      },
    }),
  )
  return state
}

describe('useKnowledgeGraph — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getKnowledgeOverview.mockResolvedValue({ data: {} })
  })

  it('知识点列表失败时必须给出持久提示，且不得表现为「暂无知识点」', async () => {
    mocks.getKnowledgePoints.mockRejectedValue(new Error('500'))
    mocks.getRelationGraphData.mockResolvedValue({ data: {} })
    const state = withSetup()
    state.courseId.value = '7'

    await state.loadPoints()

    expect(state.loadError.value).toBe('知识点列表加载失败，请检查网络后重试')
    expect(state.points.value).toEqual([])
    expect(state.pointLoading.value).toBe(false)
  })

  it('图谱数据失败时必须给出持久提示，且不得表现为「暂无图谱数据」', async () => {
    mocks.getKnowledgePoints.mockResolvedValue({ data: [] })
    mocks.getRelationGraphData.mockRejectedValue(new Error('500'))
    const state = withSetup()
    state.courseId.value = '7'

    await state.loadGraph()

    expect(state.loadError.value).toBe('图谱数据加载失败，请检查网络后重试')
    expect(state.hasGraph.value).toBe(false)
  })

  it('阴性对照：两路都成功时不得出现失败提示', async () => {
    mocks.getKnowledgePoints.mockResolvedValue({ data: [{ id: 1, name: '知识点' }] })
    mocks.getRelationGraphData.mockResolvedValue({ data: { nodes: [], links: [] } })
    const state = withSetup()
    state.courseId.value = '7'

    await Promise.all([state.loadPoints(), state.loadGraph()])

    expect(state.loadError.value).toBe('')
    expect(state.points.value).toHaveLength(1)
  })

  it('未选课程时不置错，也不发起请求（未选课 ≠ 加载失败）', async () => {
    const state = withSetup()

    await state.loadPoints()
    await state.loadGraph()

    expect(state.loadError.value).toBe('')
    expect(mocks.getKnowledgePoints).not.toHaveBeenCalled()
    expect(mocks.getRelationGraphData).not.toHaveBeenCalled()
  })

  it('知识点成功但图谱失败时：聚合提示仍必须报出图谱失败（两路信号不得互相串味）', async () => {
    mocks.getKnowledgePoints.mockResolvedValue({ data: [{ id: 1, name: '知识点' }] })
    mocks.getRelationGraphData.mockRejectedValue(new Error('500'))
    const state = withSetup()
    state.courseId.value = '7'

    await Promise.all([state.loadPoints(), state.loadGraph()])

    expect(state.points.value).toHaveLength(1)
    expect(state.loadError.value).toBe('图谱数据加载失败，请检查网络后重试')
  })
})
