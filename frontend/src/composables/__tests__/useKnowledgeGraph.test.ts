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
  ingestKnowledgeGraph: vi.fn(),
  getKnowledgeSubGraph: vi.fn(),
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
    ingestKnowledgeGraph: mocks.ingestKnowledgeGraph,
    getKnowledgeSubGraph: mocks.getKnowledgeSubGraph,
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

describe('useKnowledgeGraph — 图谱引擎（同步 / 子图）失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getKnowledgeOverview.mockResolvedValue({ data: {} })
    mocks.getKnowledgePoints.mockResolvedValue({ data: [] })
    mocks.getRelationGraphData.mockResolvedValue({ data: { nodes: [], links: [] } })
  })

  it('同步成功（mode=ENGINE）时给出模式与说明，不置错', async () => {
    mocks.ingestKnowledgeGraph.mockResolvedValue({
      data: { mode: 'ENGINE', note: '已同步至图谱引擎', syncedPoints: 12 },
    })
    const state = withSetup()
    state.courseId.value = '7'

    await state.syncKnowledgeGraphEngine()

    expect(state.engineSyncError.value).toBe('')
    expect(state.engineSyncMode.value).toBe('ENGINE')
    expect(state.engineSyncNote.value).toBe('已同步至图谱引擎')
    expect(state.engineSyncStale.value).toBe(false)
  })

  it('同步失败时必须给出持久提示（不得静默）', async () => {
    mocks.ingestKnowledgeGraph.mockRejectedValue(new Error('500'))
    const state = withSetup()
    state.courseId.value = '7'

    await state.syncKnowledgeGraphEngine()

    expect(state.engineSyncError.value).toBe('图谱引擎同步失败，请检查网络后重试')
    expect(state.engineSyncMode.value).toBe('')
  })

  it('引擎未接入时（mode=LOCAL）如实标注本地兜底', async () => {
    mocks.ingestKnowledgeGraph.mockResolvedValue({
      data: {
        mode: 'LOCAL',
        note: '知识图谱引擎未接入，已在消费侧确认知识点数据（未真正摄取至引擎）',
      },
    })
    const state = withSetup()
    state.courseId.value = '7'

    await state.syncKnowledgeGraphEngine()

    expect(state.engineSyncMode.value).toBe('LOCAL')
    expect(state.engineSyncNote.value).toContain('未接入')
  })

  it('子图加载成功时给出节点/关系读数与来源模式', async () => {
    mocks.getKnowledgeSubGraph.mockResolvedValue({
      data: {
        mode: 'ENGINE',
        nodes: [{ id: 1 }, { id: 2 }],
        edges: [{ sourceId: 1, targetId: 2 }],
      },
    })
    const state = withSetup()
    state.courseId.value = '7'

    await state.loadEngineSubGraph()

    expect(state.engineSubGraphError.value).toBe('')
    expect(state.engineSubGraphNodeCount.value).toBe(2)
    expect(state.engineSubGraphEdgeCount.value).toBe(1)
    expect(state.engineSubGraphMode.value).toBe('ENGINE')
  })

  it('子图加载失败时必须给出持久提示，且不得表现为「暂无子图」', async () => {
    mocks.getKnowledgeSubGraph.mockRejectedValue(new Error('500'))
    const state = withSetup()
    state.courseId.value = '7'

    await state.loadEngineSubGraph()

    expect(state.engineSubGraphError.value).toBe('引擎子图加载失败，请检查网络后重试')
    expect(state.engineSubGraph.value).toBeNull()
  })

  it('不同路失败不得串味：同步失败不影响子图成功结果', async () => {
    mocks.ingestKnowledgeGraph.mockRejectedValue(new Error('500'))
    mocks.getKnowledgeSubGraph.mockResolvedValue({
      data: { mode: 'LOCAL', nodes: [{ id: 1 }], edges: [] },
    })
    const state = withSetup()
    state.courseId.value = '7'

    await state.syncKnowledgeGraphEngine()
    await state.loadEngineSubGraph()

    expect(state.engineSyncError.value).toBe('图谱引擎同步失败，请检查网络后重试')
    expect(state.engineSubGraphError.value).toBe('')
    expect(state.engineSubGraphNodeCount.value).toBe(1)
  })

  it('失败后再成功可复位（同步路）', async () => {
    const state = withSetup()
    state.courseId.value = '7'
    mocks.ingestKnowledgeGraph.mockRejectedValueOnce(new Error('500'))
    await state.syncKnowledgeGraphEngine()
    expect(state.engineSyncError.value).not.toBe('')

    mocks.ingestKnowledgeGraph.mockResolvedValue({ data: { mode: 'ENGINE', note: 'ok' } })
    await state.syncKnowledgeGraphEngine()

    expect(state.engineSyncError.value).toBe('')
    expect(state.engineSyncMode.value).toBe('ENGINE')
  })

  it('AI 抽取成功后置位「建议重新同步引擎」并刷新列表', async () => {
    mocks.extractKnowledgePoints.mockResolvedValue({ data: { nodeCount: 2, relationCount: 1 } })
    const state = withSetup()
    state.courseId.value = '7'
    state.extractText.value = '课程内容'

    await state.runExtract()

    expect(state.engineSyncStale.value).toBe(true)
    expect(mocks.getKnowledgePoints).toHaveBeenCalled()
  })

  it('未选课程时不发起请求，也不置错（未选课 ≠ 加载失败）', async () => {
    const state = withSetup()

    await state.syncKnowledgeGraphEngine()
    await state.loadEngineSubGraph()

    expect(mocks.ingestKnowledgeGraph).not.toHaveBeenCalled()
    expect(mocks.getKnowledgeSubGraph).not.toHaveBeenCalled()
    expect(state.engineSyncError.value).toBe('')
    expect(state.engineSubGraphError.value).toBe('')
  })

  it('模式文案/标签色映射：ENGINE→引擎计算、LOCAL→本地兜底、UNAVAILABLE→引擎不可用', () => {
    const state = withSetup()
    expect(state.engineModeText('ENGINE')).toBe('引擎计算')
    expect(state.engineModeText('LOCAL')).toBe('本地兜底')
    expect(state.engineModeText('UNAVAILABLE')).toBe('引擎不可用')
    expect(state.engineModeType('ENGINE')).toBe('success')
    expect(state.engineModeType('LOCAL')).toBe('warning')
    expect(state.engineModeType('UNAVAILABLE')).toBe('danger')
  })
})
