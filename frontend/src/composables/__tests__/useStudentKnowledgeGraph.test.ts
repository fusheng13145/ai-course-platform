import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'

/**
 * useStudentKnowledgeGraph（学生知识图谱）单元测试
 * <p>本轮（2026-10-13）补「加载失败可见」契约：图谱数据走「关系 API 优先 + 基础 API 回退」，
 * 两条路都失败时原先静默置空 → 页面只剩「请选择课程查看知识图谱」，学生会误判该课程没有知识点。</p>
 *
 * <p>⚠️ 关键边界：**回退成功时绝不能置错**（用例 ③ 锁死），否则会对可用数据误报失败。</p>
 */

const mocks = vi.hoisted(() => ({
  getAllCourses: vi.fn(),
  getGraphData: vi.fn(),
  getKnowledgeGraph: vi.fn(),
  getMastery: vi.fn(),
  planLearningPath: vi.fn(),
  push: vi.fn(),
  route: { query: {}, params: {} },
}))

vi.mock('@/api/course', () => ({ default: { getAllCourses: mocks.getAllCourses } }))
vi.mock('@/api/knowledge', () => ({
  default: {
    getKnowledgeGraph: mocks.getKnowledgeGraph,
    getMastery: mocks.getMastery,
    planLearningPath: mocks.planLearningPath,
  },
}))
vi.mock('@/api/knowledgePointRelation', () => ({
  default: { getGraphData: mocks.getGraphData, delete: vi.fn() },
}))
vi.mock('vue-router', () => ({
  useRoute: () => mocks.route,
  useRouter: () => ({ push: mocks.push }),
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

// 组合式函数初始化即构建图谱配置（读 chartTheme → matchMedia），jsdom 需桩
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

import { useStudentKnowledgeGraph } from '@/composables/useStudentKnowledgeGraph'

function withSetup() {
  let state!: ReturnType<typeof useStudentKnowledgeGraph>
  mount(
    defineComponent({
      setup() {
        state = useStudentKnowledgeGraph()
        return () => h('div')
      },
    }),
  )
  return state
}

describe('useStudentKnowledgeGraph — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('关系 API 与回退 API 双双失败时必须给出持久提示，且不得表现为「暂无数据」', async () => {
    mocks.getGraphData.mockRejectedValue(new Error('500'))
    mocks.getKnowledgeGraph.mockRejectedValue(new Error('500'))
    const state = withSetup()
    state.selectedCourseId.value = 7

    await state.loadGraphData()

    expect(state.loadError.value).toBe('知识图谱加载失败，请检查网络后重试')
    expect(state.rawNodes.value).toEqual([])
    expect(state.graphLoading.value).toBe(false)
  })

  it('阴性对照：关系 API 成功时不得出现失败提示', async () => {
    mocks.getGraphData.mockResolvedValue({ data: { nodes: [{ id: 1, name: '知识点' }] } })
    const state = withSetup()
    state.selectedCourseId.value = 7

    await state.loadGraphData()

    expect(state.loadError.value).toBe('')
    expect(state.rawNodes.value).toHaveLength(1)
    expect(mocks.getKnowledgeGraph).not.toHaveBeenCalled()
  })

  it('关系 API 失败但回退 API 成功时：不得误报失败（数据仍可用）', async () => {
    mocks.getGraphData.mockRejectedValue(new Error('500'))
    mocks.getKnowledgeGraph.mockResolvedValue({ data: { nodes: [{ id: 2, name: '回退知识点' }] } })
    const state = withSetup()
    state.selectedCourseId.value = 7

    await state.loadGraphData()

    expect(state.loadError.value).toBe('')
    expect(state.rawNodes.value).toHaveLength(1)
  })

  it('课程列表失败时给出提示（否则下拉为空、图谱区误导为「请选择课程」）', async () => {
    mocks.getAllCourses.mockRejectedValue(new Error('500'))
    const state = withSetup()

    await state.loadCourseList()

    expect(state.loadError.value).toBe('课程列表加载失败，请检查网络后重试')
  })

  it('未选课程时不置错，也不发起图谱请求', async () => {
    const state = withSetup()

    await state.loadGraphData()

    expect(state.loadError.value).toBe('')
    expect(mocks.getGraphData).not.toHaveBeenCalled()
  })
})
