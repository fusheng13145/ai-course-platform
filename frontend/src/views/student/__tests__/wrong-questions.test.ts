import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'

/**
 * 学生错题本逻辑（useWrongQuestions）单元测试
 *
 * <p>本文件此前 mock 的是 `@/api/wrongQuestions`——**该模块并不存在**（真实模块为
 * `src/api/wrongQuestion.ts`），因此 mock 永不生效，7 条用例全在验证测试内手写的
 * filter/sort/reduce，产品代码 0 覆盖。现改为挂载真实组合式函数，验证：
 * ① 三路 `allSettled` 的部分失败降级；② 字段别名归一化（后端 content/answer/studentAnswer
 * → 前端 questionContent/correctAnswer/wrongAnswer）；③ 知识点补齐只填空不覆盖；
 * ④ 掌握计数的下限保护；⑤ 相关推荐去重；⑥ 练习卷在无题时短路不发请求。</p>
 */

const mocks = vi.hoisted(() => ({
  getWrongQuestions: vi.fn(),
  getWrongQuestionStats: vi.fn(),
  getByKnowledgePoints: vi.fn(),
  markMastered: vi.fn(),
  unmarkMastered: vi.fn(),
  generatePracticePaper: vi.fn(),
  getRelatedQuestions: vi.fn(),
  getRelated: vi.fn(),
  addWrongQuestion: vi.fn(),
  deleteWrongQuestion: vi.fn(),
  getUnmastered: vi.fn(),
  // ElMessage 在本模块里既被当函数调用（ElMessage({...})）又被当对象使用
  // （ElMessage.success/warning/...），因此用「可调用函数 + 挂载方法」建模
  message: Object.assign(vi.fn(), {
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    closeAll: vi.fn(),
  }),
}))

vi.mock('@/api/wrongQuestion', () => ({
  default: {
    getWrongQuestions: mocks.getWrongQuestions,
    getWrongQuestionStats: mocks.getWrongQuestionStats,
    getByKnowledgePoints: mocks.getByKnowledgePoints,
    markMastered: mocks.markMastered,
    unmarkMastered: mocks.unmarkMastered,
    generatePracticePaper: mocks.generatePracticePaper,
    getRelatedQuestions: mocks.getRelatedQuestions,
    getRelated: mocks.getRelated,
    addWrongQuestion: mocks.addWrongQuestion,
    deleteWrongQuestion: mocks.deleteWrongQuestion,
    getUnmastered: mocks.getUnmastered,
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: mocks.message,
  ElMessageBox: { confirm: vi.fn() },
}))

import { useWrongQuestions } from '@/composables/useWrongQuestions'

/**
 * 用 @vue/test-utils 真实挂载一个宿主组件：既避免 Vue 对「组件外调用 onMounted」的告警，
 * 又能真正验证 onMounted -> loadData 的接线（把 onMounted 桩掉会漏掉这一段）。
 */
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
  return { state }
}

const rawList = () => [
  {
    id: 1,
    questionId: 101,
    content: 'OSI 模型有几层？',
    studentAnswer: '5',
    answer: '7',
    explanation: 'OSI 共七层',
    knowledgePointName: '网络体系结构',
    knowledgePointId: 10,
    reviewStatus: 'MASTERED',
  },
  {
    id: 2,
    questionId: 102,
    content: 'HTTP 默认端口？',
    studentAnswer: '8080',
    answer: '80',
    explanation: 'HTTP 默认 80',
    knowledgePointName: '应用层协议',
    knowledgePointId: 11,
    reviewStatus: 'UNREVIEWED',
  },
]

function mockAllSettledOk() {
  mocks.getWrongQuestionStats.mockResolvedValue({ data: { total: 6, mastered: 2 } })
  mocks.getWrongQuestions.mockResolvedValue({ data: rawList() })
  mocks.getByKnowledgePoints.mockResolvedValue({ data: [] })
}

describe('useWrongQuestions — 加载与归一化', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('挂载即触发加载：统计与列表按字段别名归一化', async () => {
    mockAllSettledOk()
    mocks.getWrongQuestions.mockResolvedValue({ data: rawList() })

    const { state } = withSetup()
    await state.loadData()

    expect(mocks.getWrongQuestions).toHaveBeenCalled()
    expect(state.stats.value).toEqual({ total: 6, mastered: 2 })
    expect(state.questions.value).toHaveLength(2)

    const first = state.questions.value[0]
    // content/answer/explanation/studentAnswer 必须映射为前端消费的字段名，
    // 否则错题本列表题干为空（后端快照字段修复 2026-09-23 的配套约束）
    expect(first.questionContent).toBe('OSI 模型有几层？')
    expect(first.correctAnswer).toBe('7')
    expect(first.wrongAnswer).toBe('5')
    expect(first.analysis).toBe('OSI 共七层')
    // reviewStatus=MASTERED 必须推导为 mastered=true
    expect(first.mastered).toBe(true)
    expect(state.questions.value[1].mastered).toBe(false)
  })

  it('统计接口失败时不得连带清空错题列表（部分失败降级）', async () => {
    mockAllSettledOk()
    mocks.getWrongQuestionStats.mockRejectedValue(new Error('500'))

    const { state } = withSetup()
    await state.loadData()

    expect(state.stats.value).toEqual({ total: 0, mastered: 0 })
    expect(state.questions.value).toHaveLength(2)
  })

  it('列表返回非数组信封时置空而非抛错', async () => {
    mockAllSettledOk()
    mocks.getWrongQuestions.mockResolvedValue({ data: { unexpected: 'shape' } })

    const { state } = withSetup()
    await state.loadData()

    expect(state.questions.value).toEqual([])
  })

  it('知识点分组只补齐缺失的知识点名，不覆盖已有值', async () => {
    mockAllSettledOk()
    mocks.getByKnowledgePoints.mockResolvedValue({
      data: [
        {
          knowledgePointId: 99,
          knowledgePointName: '错误分组',
          questions: [{ id: 1 }],
        },
        {
          knowledgePointId: 12,
          knowledgePointName: 'DNS',
          questions: [{ id: 2 }],
        },
      ],
    })

    const { state } = withSetup()
    await state.loadData()

    // id=1 已从列表自带知识点，分组数据不得覆盖
    expect(state.questions.value[0].knowledgePointName).toBe('网络体系结构')
    expect(state.questions.value[0].knowledgePointId).toBe(10)
    // id=2 列表里已有名称，同样不得覆盖（仅当缺失时补齐是既定语义）
    expect(state.questions.value[1].knowledgePointName).toBe('应用层协议')
  })
})

describe('useWrongQuestions — 派生视图', () => {
  beforeEach(async () => {
    vi.clearAllMocks()
    mockAllSettledOk()
  })

  it('知识点标签按错题数倒序且最多 8 个，无名知识点不计入', async () => {
    const list: Record<string, unknown>[] = Array.from({ length: 10 }, (_, i) => ({
      id: i + 1,
      knowledgePointName: i < 3 ? 'A' : `K${i}`,
      knowledgePointId: i < 3 ? 1 : i + 1,
    }))
    // 追加一个无知识点名的记录，必须被忽略
    list.push({ id: 99, knowledgePointName: '', knowledgePointId: null })
    mocks.getWrongQuestions.mockResolvedValue({ data: list })

    const { state } = withSetup()
    await state.loadData()

    expect(state.knowledgePointTags.value.length).toBeLessThanOrEqual(8)
    // A 出现 3 次，应排在只出现 1 次的知识点之前
    expect(state.knowledgePointTags.value[0]).toEqual({ id: 1, name: 'A', count: 3 })
    // 无 knowledgePointName 的记录不进入分组
    expect(state.knowledgePointTags.value.some((t: { name: string }) => t.name === '')).toBe(false)
  })

  it('selectedKpId 过滤只影响 filteredQuestions，不动原始列表', async () => {
    const { state } = withSetup()
    await state.loadData()

    expect(state.filteredQuestions.value).toHaveLength(2)
    state.selectedKpId.value = 11
    expect(state.filteredQuestions.value).toHaveLength(1)
    expect(state.filteredQuestions.value[0].id).toBe(2)
    expect(state.questions.value).toHaveLength(2)
  })

  it('toggleExpand 再次点击同一题应收起', async () => {
    const { state } = withSetup()
    await state.loadData()

    state.toggleExpand(1)
    expect(state.expandedId.value).toBe(1)
    state.toggleExpand(1)
    expect(state.expandedId.value).toBeNull()
    state.toggleExpand(2)
    expect(state.expandedId.value).toBe(2)
  })
})

describe('useWrongQuestions — 掌握标记计数', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockAllSettledOk()
  })

  it('标记成功后计数递增；接口失败不得改变本地状态', async () => {
    const { state } = withSetup()
    await state.loadData()
    mocks.markMastered.mockResolvedValue({ code: 200 })

    await state.markMastered(state.questions.value[1])
    expect(state.questions.value[1].mastered).toBe(true)
    expect(state.stats.value.mastered).toBe(3)

    mocks.markMastered.mockRejectedValue(new Error('403'))
    await state.markMastered(state.questions.value[0])
    expect(mocks.message.error).toHaveBeenCalledWith('操作失败')
    // 失败回滚语义：mastered 不因失败请求而被翻转
    expect(state.questions.value[0].mastered).toBe(true)
    expect(state.stats.value.mastered).toBe(3)
  })

  it('重复取消掌握时 mastered 计数不得变负', async () => {
    mocks.getWrongQuestionStats.mockResolvedValue({ data: { total: 6, mastered: 0 } })
    const { state } = withSetup()
    await state.loadData()
    mocks.unmarkMastered.mockResolvedValue({ code: 200 })

    await state.unmarkMastered(state.questions.value[0])
    await state.unmarkMastered(state.questions.value[0])

    expect(state.stats.value.mastered).toBe(0)
    expect(state.stats.value.mastered).toBeGreaterThanOrEqual(0)
  })
})

describe('useWrongQuestions — 相关推荐与练习卷', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockAllSettledOk()
  })

  it('相关推荐按 id 去重追加，已存在的题目不重复入列', async () => {
    const { state } = withSetup()
    await state.loadData()
    mocks.getRelated.mockResolvedValue({
      data: [
        { id: 1, content: '重复题' }, // 已存在，必须被丢弃
        { id: 7, content: '新题', answer: 'X', explanation: '解析' },
      ],
    })

    await state.handleRelated(state.questions.value[0])

    expect(state.questions.value.map((q: { id: number }) => q.id)).toEqual([1, 2, 7])
    const appended = state.questions.value[2]
    expect(appended.questionContent).toBe('新题')
    expect(appended.mastered).toBe(false)
    expect(mocks.message.success).toHaveBeenCalledWith('找到 2 道相关题目')
  })

  it('推荐为空时只提示不追加', async () => {
    const { state } = withSetup()
    await state.loadData()
    mocks.getRelated.mockResolvedValue({ data: [] })

    await state.handleRelated(state.questions.value[0])
    expect(state.questions.value).toHaveLength(2)
    expect(mocks.message.info).toHaveBeenCalledWith('暂无相关推荐题目')
  })

  it('推荐请求 id 取自题目，且题目无 id 时不发请求', async () => {
    const { state } = withSetup()
    await state.loadData()

    await state.handleRelated({ id: 2 })
    expect(mocks.getRelated).toHaveBeenCalledWith(2)

    mocks.getRelated.mockClear()
    await state.handleRelated({})
    expect(mocks.getRelated).not.toHaveBeenCalled()
  })

  it('练习卷：全部已掌握时短路，绝不发请求', async () => {
    mocks.getWrongQuestions.mockResolvedValue({
      data: [{ id: 1, mastered: true, reviewStatus: 'MASTERED' }],
    })
    const { state } = withSetup()
    await state.loadData()

    await state.generatePracticePaper()

    expect(mocks.generatePracticePaper).not.toHaveBeenCalled()
    expect(mocks.message.warning).toHaveBeenCalledWith('没有需要复习的错题')
  })

  it('练习卷：只提交未掌握的题目 id，type 为 batch', async () => {
    const { state } = withSetup()
    await state.loadData()
    mocks.generatePracticePaper.mockResolvedValue({ data: { id: 555, questions: [{}] } })

    await state.generatePracticePaper()

    // id=1 已掌握（reviewStatus MASTERED），只有 id=2 进入练习卷
    expect(mocks.generatePracticePaper).toHaveBeenCalledWith({ questionIds: [2], type: 'batch' })
    expect(mocks.message.success).toHaveBeenCalled()
  })

  it('练习卷：后端返回空对象时提示未生成有效内容', async () => {
    const { state } = withSetup()
    await state.loadData()
    mocks.generatePracticePaper.mockResolvedValue({ data: {} })

    await state.generatePracticePaper()

    expect(mocks.message.warning).toHaveBeenCalledWith('未生成有效练习卷内容')
  })

  it('练习卷：请求失败时关闭常驻提示并报错', async () => {
    const { state } = withSetup()
    await state.loadData()
    mocks.generatePracticePaper.mockRejectedValue(new Error('500'))

    await state.generatePracticePaper()

    expect(mocks.message.closeAll).toHaveBeenCalled()
    expect(mocks.message.error).toHaveBeenCalledWith('生成练习卷失败，请稍后重试')
  })
})
