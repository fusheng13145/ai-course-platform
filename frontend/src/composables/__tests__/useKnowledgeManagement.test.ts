import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'

/**
 * useKnowledgeManagement（教师知识管理）单元测试
 * <p>本轮（2026-10-13）补「加载失败可见」契约：知识树与知识点列表两路加载失败后静默置空 →
 * 树显示「请先选择课程加载知识点」、表格显示「暂无知识点数据」，均属误导。</p>
 *
 * <p>⚠️ 两路在 loadAllData 内以 Promise.all **并发**触发，失败信号必须各自独立
 * （treeError / listError）再以 computed loadError 聚合；用例 ② 专门锁死
 * 「树成功、列表失败」不得被成功方复位而串味。</p>
 */

const mocks = vi.hoisted(() => ({
  getAllMyCourses: vi.fn(),
  getTree: vi.fn(),
  getKnowledgePoints: vi.fn(),
  getPrerequisites: vi.fn(),
  getGraphData: vi.fn(),
  createKnowledgePoint: vi.fn(),
  updateKnowledgePoint: vi.fn(),
  deleteKnowledgePoint: vi.fn(),
  addRelation: vi.fn(),
}))

vi.mock('@/api/course', () => ({ default: { getAllMyCourses: mocks.getAllMyCourses } }))
vi.mock('@/api/knowledge', () => ({
  default: {
    getTree: mocks.getTree,
    getKnowledgePoints: mocks.getKnowledgePoints,
    getPrerequisites: mocks.getPrerequisites,
    createKnowledgePoint: mocks.createKnowledgePoint,
    updateKnowledgePoint: mocks.updateKnowledgePoint,
    deleteKnowledgePoint: mocks.deleteKnowledgePoint,
    addRelation: mocks.addRelation,
  },
}))
vi.mock('@/api/knowledgePointRelation', () => ({
  default: { getGraphData: mocks.getGraphData, delete: vi.fn() },
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

import { useKnowledgeManagement } from '@/composables/useKnowledgeManagement'

/** 组合式函数无 onMounted，挂宿主组件后显式触发加载 */
function withSetup() {
  let state!: ReturnType<typeof useKnowledgeManagement>
  mount(
    defineComponent({
      setup() {
        state = useKnowledgeManagement()
        return () => h('div')
      },
    }),
  )
  return state
}

describe('useKnowledgeManagement — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getGraphData.mockResolvedValue({ data: {} })
  })

  it('知识树与列表双双失败时必须给出持久提示，且不得表现为「暂无知识点数据」', async () => {
    mocks.getTree.mockRejectedValue(new Error('500'))
    mocks.getKnowledgePoints.mockRejectedValue(new Error('500'))
    const state = withSetup()
    state.selectedCourseId.value = 7

    await state.loadAllData()

    expect(state.loadError.value).toBe('知识树加载失败，请检查网络后重试')
    expect(state.knowledgeTree.value).toEqual([])
    expect(state.knowledgeList.value).toEqual([])
  })

  it('树成功但列表失败时：聚合提示仍必须报出列表失败（两路信号不得互相串味）', async () => {
    mocks.getTree.mockResolvedValue({ data: [] })
    mocks.getKnowledgePoints.mockRejectedValue(new Error('500'))
    const state = withSetup()
    state.selectedCourseId.value = 7

    await state.loadAllData()

    expect(state.loadError.value).toBe('知识点列表加载失败，请检查网络后重试')
    expect(state.knowledgeList.value).toEqual([])
  })

  it('阴性对照：两路都成功时不得出现失败提示', async () => {
    mocks.getTree.mockResolvedValue({ data: [{ id: 1, name: '根' }] })
    mocks.getKnowledgePoints.mockResolvedValue({ data: [{ id: 1, name: '根' }] })
    const state = withSetup()
    state.selectedCourseId.value = 7

    await state.loadAllData()

    expect(state.loadError.value).toBe('')
    expect(state.knowledgeList.value).toHaveLength(1)
  })

  it('未选课程时不置错，也不发起请求', async () => {
    const state = withSetup()

    await state.loadAllData()

    expect(state.loadError.value).toBe('')
    expect(mocks.getTree).not.toHaveBeenCalled()
  })
})
