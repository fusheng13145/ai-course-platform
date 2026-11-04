import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const aiApi = vi.hoisted(() => ({
  weakPointsAnalysis: vi.fn(),
  learningAdvice: vi.fn(),
  qaAsk: vi.fn(),
  qaContinue: vi.fn(),
  essayCorrect: vi.fn(),
  essayPolish: vi.fn(),
  generateLearningPath: vi.fn(),
}))
const knowledgeApi = vi.hoisted(() => ({
  getRelationGraphData: vi.fn(),
  getKnowledgeOverview: vi.fn(),
  getMastery: vi.fn(),
  planLearningPath: vi.fn(),
  getKnowledgeSubGraph: vi.fn(),
  ingestKnowledgeGraph: vi.fn(),
}))
const toast = vi.hoisted(() => vi.fn())

vi.mock('@/api/ai', () => ({ default: aiApi }))
vi.mock('@/api/knowledge', () => ({ default: knowledgeApi }))
vi.mock('../composables/ui', () => ({ useUi: () => ({ toast }) }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ userId: 42 }) }))

import AiCenterTab from '../views/course/tabs/AiCenterTab.vue'
import KnowledgeGraphTab from '../views/course/tabs/KnowledgeGraphTab.vue'

const COURSE_ID = 4007

const findButton = (wrapper: ReturnType<typeof mount>, label: string) =>
  wrapper.findAll('button').find((node) => node.text().includes(label))

const tabLabels = (wrapper: ReturnType<typeof mount>) =>
  wrapper.findAll('[role="tab"]').map((node) => node.text())

beforeEach(() => {
  vi.clearAllMocks()
  aiApi.weakPointsAnalysis.mockResolvedValue({ data: [] })
  aiApi.learningAdvice.mockResolvedValue({ data: {} })
  knowledgeApi.getRelationGraphData.mockResolvedValue({
    data: {
      nodes: [
        { id: 1, name: '绪论', category: 'CONCEPT' },
        { id: 2, name: '协议分层', category: 'CONCEPT' },
      ],
      links: [{ source: 1, target: 2, type: 'PREREQUISITE' }],
    },
  })
  knowledgeApi.getKnowledgeOverview.mockResolvedValue({ data: {} })
  knowledgeApi.getMastery.mockResolvedValue({ data: { score: 78, level: '熟练' } })
})

describe('学生侧 AI 助教：复用教师端真接口，只裁教师专属动作', () => {
  it('AI 页签在 student 模式隐藏「作文批改」并改用学生文案；teacher 模式保留五项', async () => {
    const student = mount(AiCenterTab, { props: { courseId: COURSE_ID, mode: 'student' } })
    await flushPromises()
    expect(student.text()).toContain('AI 学习助手')
    expect(tabLabels(student)).toContain('智能答疑')
    expect(tabLabels(student)).toContain('薄弱点分析')
    expect(tabLabels(student)).toContain('学习路径')
    expect(tabLabels(student)).not.toContain('作文批改')

    const teacher = mount(AiCenterTab, { props: { courseId: COURSE_ID, mode: 'teacher' } })
    await flushPromises()
    expect(tabLabels(teacher)).toContain('作文批改')
    expect(teacher.text()).toContain('AI 教学助手')
  })

  it('学生 AI 页签用的是真接口：挂载即拉薄弱点与学习建议', async () => {
    mount(AiCenterTab, { props: { courseId: COURSE_ID, mode: 'student' } })
    await flushPromises()
    expect(aiApi.weakPointsAnalysis).toHaveBeenCalledWith({ courseId: COURSE_ID })
    expect(aiApi.learningAdvice).toHaveBeenCalledWith({ courseId: COURSE_ID })
  })

  it('知识图谱 student 模式隐藏「同步到引擎」，teacher 模式保留', async () => {
    const student = mount(KnowledgeGraphTab, { props: { courseId: COURSE_ID, mode: 'student' } })
    await flushPromises()
    expect(findButton(student, '同步到引擎')).toBeUndefined()
    expect(findButton(student, '重载图谱')).toBeTruthy()

    const teacher = mount(KnowledgeGraphTab, { props: { courseId: COURSE_ID, mode: 'teacher' } })
    await flushPromises()
    expect(findButton(teacher, '同步到引擎')).toBeTruthy()
  })

  it('学生点节点：按当前学生拉掌握度并在卡片上显示', async () => {
    const wrapper = mount(KnowledgeGraphTab, { props: { courseId: COURSE_ID, mode: 'student' } })
    await flushPromises()
    const node = wrapper.findAll('.kg-node')[0]
    await node.trigger('click')
    await flushPromises()
    expect(knowledgeApi.getMastery).toHaveBeenCalledWith(1, 42)
    expect(wrapper.text()).toContain('我的掌握度')
    expect(wrapper.text()).toContain('78 分')
  })

  it('教师模式不拉掌握度（该读数只面向学生本人）', async () => {
    const wrapper = mount(KnowledgeGraphTab, {
      props: { courseId: COURSE_ID, mode: 'teacher' },
    })
    await flushPromises()
    await wrapper.findAll('.kg-node')[0].trigger('click')
    await flushPromises()
    expect(knowledgeApi.getMastery).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('我的掌握度')
  })
})

describe('知识图谱 · 路径规划与高亮', () => {
  /** 三个点两条边的线性图：1 → 2 → 3，方便断言"路径上有几个点、几条边" */
  function mockChainGraph() {
    knowledgeApi.getRelationGraphData.mockResolvedValue({
      data: {
        nodes: [
          { id: 1, name: '绪论', category: 'CONCEPT' },
          { id: 2, name: '协议分层', category: 'CONCEPT' },
          { id: 3, name: '传输层', category: 'CONCEPT' },
        ],
        links: [
          { source: 1, target: 2, type: 'PREREQUISITE' },
          { source: 2, target: 3, type: 'PREREQUISITE' },
        ],
      },
    })
  }

  async function planTo(wrapper: ReturnType<typeof mount>, source: string, target: string) {
    const selects = wrapper.findAll('select')
    await selects[0].setValue(source)
    await selects[1].setValue(target)
    const button = wrapper.findAll('button').find((node) => node.text().includes('规划路径'))
    await button!.trigger('click')
    await flushPromises()
  }

  it('找到路径：步骤、跳数与模式章按接口读数渲染，图上只点亮路径上的点与边', async () => {
    mockChainGraph()
    knowledgeApi.planLearningPath.mockResolvedValue({
      data: {
        found: true,
        hops: 2,
        mode: 'LOCAL',
        path: [
          { id: 1, name: '绪论' },
          { id: 2, name: '协议分层' },
          { id: 3, name: '传输层' },
        ],
      },
    })
    // 学生端已隐藏该入口（B-13㉖：端点恒 403），用例按教师视角验证规划与高亮
    const wrapper = mount(KnowledgeGraphTab, { props: { courseId: COURSE_ID, mode: 'teacher' } })
    await flushPromises()

    await planTo(wrapper, '1', '3')

    expect(knowledgeApi.planLearningPath).toHaveBeenCalledWith(1, 3, expect.anything())
    const steps = wrapper.findAll('.path-list li')
    expect(steps.map((node) => node.text())).toEqual([
      expect.stringContaining('绪论'),
      expect.stringContaining('协议分层'),
      expect.stringContaining('传输层'),
    ])
    expect(wrapper.text()).toContain('2 跳')
    // 高亮：3 个点在路径上、2 条边在路径上，其余（本例没有）被压暗
    expect(wrapper.findAll('.kg-node.is-path')).toHaveLength(3)
    expect(wrapper.findAll('.kg-svg line.is-path')).toHaveLength(2)
    expect(wrapper.findAll('.kg-node.is-dim')).toHaveLength(0)
  })

  it('后端 404（无路可走）按"不可达"呈现，不报"规划失败"', async () => {
    mockChainGraph()
    // 后端对"无路径"返回 404 + note，axios 会 reject —— 这是业务结论，不是故障
    knowledgeApi.planLearningPath.mockRejectedValue({
      response: { status: 404, data: { message: '未找到从起点到目标的学习路径' } },
    })
    // 学生端已隐藏该入口（B-13㉖：端点恒 403），用例按教师视角验证规划与高亮
    const wrapper = mount(KnowledgeGraphTab, { props: { courseId: COURSE_ID, mode: 'teacher' } })
    await flushPromises()

    await planTo(wrapper, '1', '3')

    expect(wrapper.text()).toContain('不可达')
    expect(wrapper.text()).not.toContain('路径规划失败')
    expect(wrapper.findAll('.kg-node.is-path')).toHaveLength(0)
  })

  it('未找到路径：如实提示不可达，且一个点都不点亮（不可达不等于全图重要）', async () => {
    mockChainGraph()
    knowledgeApi.planLearningPath.mockResolvedValue({
      data: { found: false, hops: 0, mode: 'LOCAL', path: [] },
    })
    // 学生端已隐藏该入口（B-13㉖：端点恒 403），用例按教师视角验证规划与高亮
    const wrapper = mount(KnowledgeGraphTab, { props: { courseId: COURSE_ID, mode: 'teacher' } })
    await flushPromises()

    await planTo(wrapper, '1', '3')

    expect(wrapper.text()).toContain('不可达')
    expect(wrapper.findAll('.kg-node.is-path')).toHaveLength(0)
    expect(wrapper.findAll('.kg-svg line.is-path')).toHaveLength(0)
    expect(wrapper.findAll('.kg-node.is-dim')).toHaveLength(0)
  })
})
