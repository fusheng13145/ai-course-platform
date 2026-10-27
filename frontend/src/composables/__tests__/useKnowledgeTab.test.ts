import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * useKnowledgeTab（课程知识点 Tab 逻辑）单元测试
 *
 * <p>本文件此前 0% 覆盖，且承载 P1-10 缺陷修复：旧的「多字段兼容」写法
 * `Array.isArray(res.data?.list || res.data?.records || [])` 里空数组是 truthy，
 * `||` 恒短路取到 `list` —— 后端把数据放在 `records` 时真实数据被永久吞掉，
 * 界面空列表且毫无报错。现由 utils/response 的 hasNonEmptyArray / firstNonEmptyArray
 * 按「非空数组优先级」判定。下面把 list/records/data/裸数组各信封逐个钉成回归用例。</p>
 */

const mocks = vi.hoisted(() => ({
  getTree: vi.fn(),
  getKnowledgePoints: vi.fn(),
  getRelationGraphData: vi.fn(),
  createKnowledgePoint: vi.fn(),
  updateKnowledgePoint: vi.fn(),
  deleteKnowledgePoint: vi.fn(),
  extractKnowledgePoints: vi.fn(),
  message: Object.assign(vi.fn(), {
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    closeAll: vi.fn(),
  }),
  confirm: vi.fn(),
}))

vi.mock('@/api/knowledge', () => ({
  default: {
    getTree: mocks.getTree,
    getKnowledgePoints: mocks.getKnowledgePoints,
    getRelationGraphData: mocks.getRelationGraphData,
    createKnowledgePoint: mocks.createKnowledgePoint,
    updateKnowledgePoint: mocks.updateKnowledgePoint,
    deleteKnowledgePoint: mocks.deleteKnowledgePoint,
    extractKnowledgePoints: mocks.extractKnowledgePoints,
  },
}))
vi.mock('element-plus', () => ({
  ElMessage: mocks.message,
  ElMessageBox: { confirm: mocks.confirm },
}))

import { useKnowledgeTab } from '@/composables/useKnowledgeTab'

/**
 * jsdom 不提供 window.matchMedia，而图表主题（chartTheme.resolveTheme）会读系统偏好，
 * 组合式函数初始化图表配置时即触发（与 useGrades.test.ts 同一桩法）。
 */
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

type Node = { id?: number; name?: string; children?: unknown[] }
type Point = { id?: number; name?: string; importance?: number; weight?: number }

/** treeData/list 等 ref 由 `ref([])` 推导为 never[]，测试内按声明类型读取 */
const rows = <T>(v: unknown) => v as unknown as T[]

function withSetup(courseId: unknown = 1) {
  const courseIdRef = ref(courseId)
  let state!: ReturnType<typeof useKnowledgeTab>
  mount(
    defineComponent({
      setup() {
        state = useKnowledgeTab(courseIdRef)
        return () => h('div')
      },
    }),
  )
  return { state, courseIdRef }
}

const kp = (name: string, extra: Record<string, unknown> = {}) => ({
  id: 1,
  name,
  difficulty: 'MEDIUM',
  ...extra,
})

describe('useKnowledgeTab — 树数据信封归一化（P1-10 回归）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getKnowledgePoints.mockResolvedValue({ data: [] })
    mocks.getRelationGraphData.mockResolvedValue({ data: {} })
  })

  // 显式标注表格类型，避免 it.each 把「payload / 期望长度」推成联合类型
  const treeCases: Array<[string, unknown, number]> = [
    ['list 为空数组、数据在 records', { data: { list: [], records: [kp('A'), kp('B')] } }, 2],
    ['list 有值、records 为空', { data: { list: [kp('A')], records: [] } }, 1],
    ['只有 records', { data: { records: [kp('A')] } }, 1],
    ['data 直接是数组', { data: [kp('A'), kp('B')] }, 2],
    ['双层信封 data.data', { data: { data: [kp('A')] } }, 1],
    ['axios 顶层即数组', [kp('A')], 1],
  ]
  it.each(treeCases)('%s 必须解析出 %i 个树节点', async (_name, payload, expected) => {
    mocks.getTree.mockResolvedValue(payload as never)
    const { state } = withSetup()
    await state.loadAll()

    expect(rows<Node>(state.treeData.value)).toHaveLength(expected)
  })

  it('records 优先于空 list 时取到的就是 records 本身（不是 list 的占位空数组）', async () => {
    mocks.getTree.mockResolvedValue({ data: { list: [], records: [{ id: 9, name: '真实节点' }] } })
    const { state } = withSetup()
    await state.loadAll()

    expect(rows<Node>(state.treeData.value)).toEqual([{ id: 9, name: '真实节点' }])
  })

  it('后端确实没有知识点（空数组）时保持空树，不得把信封包成节点', async () => {
    mocks.getTree.mockResolvedValue({ data: [] })
    const { state } = withSetup()
    await state.loadAll()

    expect(state.treeData.value).toEqual([])
  })

  it('树接口失败时回退到知识点列表并本地建树', async () => {
    mocks.getTree.mockRejectedValue(new Error('404'))
    mocks.getKnowledgePoints.mockResolvedValue({
      data: { records: [kp('子节点', { parentId: 5 })] },
    })
    const { state } = withSetup()
    await state.loadAll()

    expect(mocks.getTree).toHaveBeenCalledWith(1)
    expect(mocks.getKnowledgePoints).toHaveBeenCalled()
    expect(rows<Node>(state.treeData.value)).toHaveLength(1)
    // 回退分支同样走 firstNonEmptyArray，records 不被空 list 吞掉
    expect(rows<Point>(state.list.value)).toHaveLength(1)
    expect(state.treeLoading.value).toBe(false)
  })

  it('树接口与回退接口双双失败时置空而不抛异常', async () => {
    mocks.getTree.mockRejectedValue(new Error('404'))
    mocks.getKnowledgePoints.mockRejectedValue(new Error('500'))
    const { state } = withSetup()

    await expect(state.loadAll()).resolves.toBeUndefined()
    expect(state.treeData.value).toEqual([])
    expect(state.treeLoading.value).toBe(false)
  })
})

describe('useKnowledgeTab — 列表数据信封（P1-10 回归）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getTree.mockResolvedValue({ data: [] })
    mocks.getRelationGraphData.mockResolvedValue({ data: {} })
  })

  const listCases: Array<[string, unknown, number]> = [
    ['list 空、数据在 records', { data: { list: [], records: [kp('A'), kp('B')] } }, 2],
    ['只有 records', { data: { records: [kp('A')] } }, 1],
    ['data 直接是数组', { data: [kp('A')] }, 1],
    ['axios 顶层即数组', [kp('A')], 1],
    ['全空信封', { data: { list: [], records: [] } }, 0],
  ]
  it.each(listCases)('%s → 列表长度 %i', async (_name, payload, expected) => {
    mocks.getKnowledgePoints.mockResolvedValue(payload as never)
    const { state } = withSetup()
    await state.loadAll()

    expect(rows<Point>(state.list.value)).toHaveLength(expected)
    expect(state.tableLoading.value).toBe(false)
  })

  it('importance 缺失时按 weight、再按 1 兜底', async () => {
    mocks.getKnowledgePoints.mockResolvedValue({
      data: {
        records: [
          { name: '有权重', weight: 5 },
          { name: '裸节点' },
          { name: '自带', importance: 3 },
        ],
      },
    })
    const { state } = withSetup()
    await state.loadAll()

    const list = rows<Point>(state.list.value)
    expect(list.map((x) => x.importance)).toEqual([5, 1, 3])
  })

  it('列表接口失败时降级为空列表且不冒泡', async () => {
    mocks.getKnowledgePoints.mockRejectedValue(new Error('500'))
    const { state } = withSetup()
    await state.loadAll()

    expect(state.list.value).toEqual([])
    expect(state.tableLoading.value).toBe(false)
  })
})

describe('useKnowledgeTab — 请求参数基准与 courseId 响应', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getRelationGraphData.mockResolvedValue({ data: {} })
  })

  it('courseId 为字符串时也必须以数字发给三个接口', async () => {
    mocks.getTree.mockResolvedValue({ data: [] })
    mocks.getKnowledgePoints.mockResolvedValue({ data: [] })
    const { state } = withSetup('7')
    await state.loadAll()

    expect(mocks.getTree).toHaveBeenCalledWith(7)
    expect(mocks.getKnowledgePoints).toHaveBeenCalledWith(7)
    expect(mocks.getRelationGraphData).toHaveBeenCalledWith(7)
  })

  it('courseId 为空时不发起任何请求', async () => {
    const { state } = withSetup('')
    await state.loadAll()

    expect(mocks.getTree).not.toHaveBeenCalled()
    expect(mocks.getKnowledgePoints).not.toHaveBeenCalled()
  })

  it('courseId 变化后自动重载（watch 接线）', async () => {
    mocks.getTree.mockResolvedValue({ data: [] })
    mocks.getKnowledgePoints.mockResolvedValue({ data: { records: [kp('A')] } })
    const { state, courseIdRef } = withSetup(1)
    await flushPromises()
    expect(mocks.getTree).toHaveBeenCalledTimes(1)

    courseIdRef.value = 2
    await flushPromises()

    expect(mocks.getTree).toHaveBeenLastCalledWith(2)
    expect(mocks.getTree).toHaveBeenCalledTimes(2)
    void state
  })

  it('关系图数据按 nodes/links 落到力导向配置，edges 别名同样兼容', async () => {
    mocks.getTree.mockResolvedValue({ data: [] })
    mocks.getKnowledgePoints.mockResolvedValue({ data: [] })
    mocks.getRelationGraphData.mockResolvedValue({
      data: { nodes: [{ id: 1, name: 'A' }], edges: [{ source: 1, target: 1 }] },
    })
    const { state } = withSetup()
    await state.loadAll()

    expect(state.forceOption.value.series[0].links).toHaveLength(1)
  })

  it('关系图接口失败时用已加载的知识点兜底成图节点', async () => {
    mocks.getTree.mockResolvedValue({ data: [] })
    mocks.getKnowledgePoints.mockResolvedValue({ data: { records: [kp('A'), kp('B')] } })
    mocks.getRelationGraphData.mockRejectedValue(new Error('500'))
    const { state } = withSetup()
    await state.loadAll()

    expect(state.forceOption.value.series[0].data).toHaveLength(2)
  })
})

describe('useKnowledgeTab — 检索与派生视图', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    mocks.getTree.mockResolvedValue({ data: [] })
    mocks.getRelationGraphData.mockResolvedValue({ data: {} })
    mocks.getKnowledgePoints.mockResolvedValue({
      data: {
        records: [
          kp('数据结构', { description: '线性表' }),
          kp('Network', { description: 'HTTP' }),
        ],
      },
    })
  })

  it('关键词命中名称或描述（大小写不敏感）', async () => {
    const { state } = withSetup()
    await state.loadAll()

    state.searchText.value = 'network'
    expect(rows<Node>(state.filteredData.value)).toHaveLength(1)
    state.searchText.value = '线性表'
    expect(rows<Node>(state.filteredData.value)).toHaveLength(1)
    state.searchText.value = ''
    expect(rows<Node>(state.filteredData.value)).toHaveLength(2)
  })

  it('flatTreeOptions 把空 children 归一为 undefined，避免下拉出现空展开箭头', async () => {
    mocks.getTree.mockResolvedValue({ data: [{ id: 1, name: '根', children: [] }] })
    const { state } = withSetup()
    await state.loadAll()

    expect(state.flatTreeOptions.value[0]).toMatchObject({ name: '根' })
    expect('children' in (state.flatTreeOptions.value[0] as Record<string, unknown>)).toBe(true)
    expect((state.flatTreeOptions.value[0] as { children?: unknown[] }).children).toBeUndefined()
  })

  it('难度标签/文案映射覆盖未知值', () => {
    const { state } = withSetup()
    expect(state.getDiffTag('HARD')).toBe('danger')
    expect(state.getDiffTag('EASY')).toBe('success')
    expect(state.getDiffTag('WEIRD')).toBe('info')
    expect(state.getDiffLabel('MEDIUM')).toBe('中等')
    expect(state.getDiffLabel('WEIRD')).toBe('-')
  })
})

describe('useKnowledgeTab — 新增/编辑/删除', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getTree.mockResolvedValue({ data: [] })
    mocks.getKnowledgePoints.mockResolvedValue({ data: [] })
    mocks.getRelationGraphData.mockResolvedValue({ data: {} })
    mocks.createKnowledgePoint.mockResolvedValue({ code: 200 })
    mocks.updateKnowledgePoint.mockResolvedValue({ code: 200 })
    mocks.deleteKnowledgePoint.mockResolvedValue({ code: 200 })
  })

  it('openCreateDialog 重置表单并打开弹窗', async () => {
    const { state } = withSetup()
    state.form.name = '脏数据'

    state.openCreateDialog()

    expect(state.isEdit.value).toBe(false)
    expect(state.form).toMatchObject({
      name: '',
      difficulty: 'MEDIUM',
      importance: 2,
      parentId: null,
    })
    expect(state.formVisible.value).toBe(true)
  })

  it('新增走 create 并带上数字 courseId，成功后重载', async () => {
    const { state } = withSetup('3')
    state.openCreateDialog()
    Object.assign(state.form, {
      name: '新知识点',
      difficulty: 'HARD',
      importance: 5,
      description: '说明',
    })

    await state.savePoint()

    expect(mocks.createKnowledgePoint).toHaveBeenCalledWith({
      name: '新知识点',
      difficulty: 'HARD',
      importance: 5,
      parentId: null,
      description: '说明',
      courseId: 3,
    })
    expect(mocks.updateKnowledgePoint).not.toHaveBeenCalled()
    expect(mocks.message.success).toHaveBeenCalledWith('创建成功')
    expect(state.formVisible.value).toBe(false)
    expect(state.submitting.value).toBe(false)
  })

  it('编辑走 update 并携带被编辑行的 id', async () => {
    const { state } = withSetup(3)
    state.openEditDialog({
      id: 88,
      name: '旧名',
      difficulty: 'EASY',
      importance: 1,
      description: 'd',
    } as never)

    await state.savePoint()

    expect(state.isEdit.value).toBe(true)
    // payload = 表单 + 数字 courseId，走 update 分支且带上被编辑行 id
    expect(mocks.updateKnowledgePoint).toHaveBeenCalledWith(88, {
      name: '旧名',
      difficulty: 'EASY',
      importance: 1,
      parentId: null,
      description: 'd',
      courseId: 3,
    })
    expect(mocks.createKnowledgePoint).not.toHaveBeenCalled()
    expect(mocks.message.success).toHaveBeenCalledWith('更新成功')
  })

  it('保存失败时展示后端 message 并保持 submitting=false', async () => {
    mocks.createKnowledgePoint.mockRejectedValue({ response: { data: { message: '名称重复' } } })
    const { state } = withSetup()
    state.openCreateDialog()
    state.form.name = '重复名'

    await state.savePoint()

    expect(mocks.message.error).toHaveBeenCalledWith('名称重复')
    expect(state.submitting.value).toBe(false)
    expect(state.formVisible.value).toBe(true)
  })

  it('删除确认后调用接口并重载', async () => {
    mocks.confirm.mockResolvedValue('confirm')
    const { state } = withSetup()
    await state.loadAll()
    mocks.getKnowledgePoints.mockClear()

    await state.handleDelete({ id: 5, name: '待删' } as never)

    expect(mocks.deleteKnowledgePoint).toHaveBeenCalledWith(5)
    expect(mocks.message.success).toHaveBeenCalledWith('已删除')
    expect(mocks.getKnowledgePoints).toHaveBeenCalled()
  })

  it('用户取消删除时不发请求也不报错', async () => {
    mocks.confirm.mockRejectedValue('cancel')
    const { state } = withSetup()

    await state.handleDelete({ id: 5, name: '待删' } as never)

    expect(mocks.deleteKnowledgePoint).not.toHaveBeenCalled()
    expect(mocks.message.error).not.toHaveBeenCalled()
  })

  it('删除接口失败时提示删除失败', async () => {
    mocks.confirm.mockResolvedValue('confirm')
    mocks.deleteKnowledgePoint.mockRejectedValue(new Error('500'))
    const { state } = withSetup()

    await state.handleDelete({ id: 5, name: '待删' } as never)

    expect(mocks.message.error).toHaveBeenCalledWith('删除失败')
  })
})

describe('useKnowledgeTab — AI 提取导入', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getTree.mockResolvedValue({ data: [] })
    mocks.getKnowledgePoints.mockResolvedValue({ data: [] })
    mocks.getRelationGraphData.mockResolvedValue({ data: {} })
    mocks.createKnowledgePoint.mockResolvedValue({ code: 200 })
  })

  it('空文本只提示不发请求', async () => {
    const { state } = withSetup()
    state.aiText.value = '   '

    await state.doAIExtract()

    expect(mocks.message.warning).toHaveBeenCalledWith('请输入文本')
    expect(mocks.extractKnowledgePoints).not.toHaveBeenCalled()
  })

  it('提取成功时逐条入库并提示数量', async () => {
    mocks.extractKnowledgePoints.mockResolvedValue({
      data: {
        points: [{ name: 'A' }, { name: 'B', difficulty: 'HARD', importance: 4, description: 'd' }],
      },
    })
    const { state } = withSetup(9)
    state.aiText.value = '图谱文本'

    await state.doAIExtract()

    expect(mocks.extractKnowledgePoints).toHaveBeenCalledWith({ text: '图谱文本', courseId: 9 })
    expect(mocks.message.success).toHaveBeenCalledWith('提取到 2 个知识点')
    expect(mocks.createKnowledgePoint).toHaveBeenCalledTimes(2)
    expect(mocks.createKnowledgePoint).toHaveBeenLastCalledWith({
      name: 'B',
      description: 'd',
      courseId: 9,
      difficulty: 'HARD',
      importance: 4,
    })
    expect(state.aiVisible.value).toBe(false)
    expect(state.aiExtracting.value).toBe(false)
  })

  it('AI 无结果时提示本地分析且不入库', async () => {
    mocks.extractKnowledgePoints.mockResolvedValue({ data: { points: [] } })
    const { state } = withSetup()
    state.aiText.value = '随便一段文本'

    await state.doAIExtract()

    expect(mocks.message.info).toHaveBeenCalledWith('AI暂不可用，已使用本地分析')
    expect(mocks.createKnowledgePoint).not.toHaveBeenCalled()
  })

  it('AI 接口失败时按句子本地兜底入库', async () => {
    mocks.extractKnowledgePoints.mockRejectedValue(new Error('503'))
    const { state } = withSetup(2)
    state.aiText.value = '这是第一个知识点说明。这是第二个知识点说明！太短'

    await state.doAIExtract()

    expect(mocks.createKnowledgePoint).toHaveBeenCalledTimes(2)
    expect(mocks.createKnowledgePoint).toHaveBeenCalledWith({
      name: '这是第一个知识点说明',
      courseId: 2,
      difficulty: 'MEDIUM',
      importance: 2,
    })
    expect(mocks.message.success).toHaveBeenCalledWith('导入 2 个知识点')
  })

  it('部分入库失败时给出失败计数提醒', async () => {
    mocks.extractKnowledgePoints.mockResolvedValue({
      data: { points: [{ name: 'A' }, { name: 'B' }] },
    })
    mocks.createKnowledgePoint
      .mockResolvedValueOnce({ code: 200 })
      .mockRejectedValueOnce(new Error('500'))
    const { state } = withSetup()
    state.aiText.value = '图谱文本'

    await state.doAIExtract()

    expect(mocks.message.warning).toHaveBeenCalledWith('有 1 个知识点导入失败，请稍后重试')
    expect(state.aiExtracting.value).toBe(false)
  })
})

describe('useKnowledgeTab — CSV 导出', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getTree.mockResolvedValue({ data: [] })
    mocks.getRelationGraphData.mockResolvedValue({ data: {} })
    mocks.getKnowledgePoints.mockResolvedValue({
      data: { records: [kp('A,B', { description: '含,号' })] },
    })
    // jsdom 未实现对象 URL，导出路径只需可调用
    window.URL.createObjectURL = vi.fn(() => 'blob:fake')
    window.URL.revokeObjectURL = vi.fn()
    // 真实 a.click() 会让 jsdom 尝试导航并抛「Not implemented」噪声，此处只关心是否触发下载
    vi.spyOn(window.HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
  })

  it('有数据时生成下载并提示成功', async () => {
    const { state } = withSetup(4)
    await state.loadAll()

    state.handleExport()

    expect(window.URL.createObjectURL).toHaveBeenCalled()
    expect(mocks.message.success).toHaveBeenCalledWith('导出成功')
  })

  it('无数据时直接返回，不产生空文件', async () => {
    mocks.getKnowledgePoints.mockResolvedValue({ data: [] })
    const { state } = withSetup()
    await state.loadAll()
    vi.mocked(window.URL.createObjectURL).mockClear()

    state.handleExport()

    expect(window.URL.createObjectURL).not.toHaveBeenCalled()
    expect(mocks.message.success).not.toHaveBeenCalled()
  })
})

describe('useKnowledgeTab — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getRelationGraphData.mockResolvedValue({ data: {} })
  })

  it('列表请求失败时必须给出持久提示，且不得表现为「暂无知识点」', async () => {
    mocks.getTree.mockResolvedValue({ data: [] })
    mocks.getKnowledgePoints.mockRejectedValue(new Error('500'))
    const { state } = withSetup()
    await flushPromises()

    await state.loadAll()

    expect(state.loadError.value).toBe('知识点列表加载失败，请检查网络后重试')
    expect(state.list.value).toEqual([])
    expect(state.tableLoading.value).toBe(false)
  })

  it('阴性对照：加载成功时不得出现失败提示', async () => {
    mocks.getTree.mockResolvedValue({ data: [] })
    mocks.getKnowledgePoints.mockResolvedValue({ data: { records: [kp('A')] } })
    const { state } = withSetup()
    await flushPromises()

    await state.loadAll()

    expect(state.loadError.value).toBe('')
    expect(rows<Point>(state.list.value)).toHaveLength(1)
  })

  it('失败后再成功：提示必须被清空（每次 loadAll 都复位，不是一次失败永久残留）', async () => {
    // 首轮为挂载时 onMounted 触发的 loadAll（列表失败），次轮显式 loadAll 成功
    mocks.getTree.mockResolvedValue({ data: [] })
    mocks.getKnowledgePoints.mockRejectedValueOnce(new Error('500')).mockResolvedValue({ data: [] })
    const { state } = withSetup()
    await flushPromises()
    expect(state.loadError.value).not.toBe('')

    await state.loadAll()

    expect(state.loadError.value).toBe('')
  })

  it('未选课程时不置错，也不发起请求（未选课 ≠ 加载失败）', async () => {
    const { state } = withSetup('')
    await flushPromises()

    await state.loadAll()

    expect(state.loadError.value).toBe('')
    expect(mocks.getTree).not.toHaveBeenCalled()
  })
})
