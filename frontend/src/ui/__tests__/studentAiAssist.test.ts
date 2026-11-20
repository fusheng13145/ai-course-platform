import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { nextTick } from 'vue'

const aiApi = vi.hoisted(() => ({
  weakPointsAnalysis: vi.fn(),
  learningAdvice: vi.fn(),
  qaAsk: vi.fn(),
  qaContinue: vi.fn(),
  getSessions: vi.fn(),
  essayCorrect: vi.fn(),
  essayPolish: vi.fn(),
  generateLearningPath: vi.fn(),
}))
const courseApi = vi.hoisted(() => ({ getCourseById: vi.fn() }))
const knowledgeApi = vi.hoisted(() => ({
  getRelationGraphData: vi.fn(),
  getKnowledgeOverview: vi.fn(),
  getMastery: vi.fn(),
  getKnowledgePointById: vi.fn(),
  planLearningPath: vi.fn(),
  getKnowledgeSubGraph: vi.fn(),
  ingestKnowledgeGraph: vi.fn(),
}))
const toast = vi.hoisted(() => vi.fn())

vi.mock('@/api/ai', () => ({ default: aiApi }))
vi.mock('@/api/course', () => ({ default: courseApi }))
vi.mock('@/api/knowledge', () => ({ default: knowledgeApi }))
vi.mock('../composables/ui', () => ({ useUi: () => ({ toast }) }))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ userId: 42 }) }))

import AiCenterTab from '../views/course/tabs/AiCenterTab.vue'
import KnowledgeGraphTab from '../views/course/tabs/KnowledgeGraphTab.vue'

const COURSE_ID = 4007
let courseId = COURSE_ID

const findButton = (wrapper: ReturnType<typeof mount>, label: string) =>
  wrapper.findAll('button').find((node) => node.text().includes(label))

const tabLabels = (wrapper: ReturnType<typeof mount>) =>
  wrapper.findAll('[role="tab"]').map((node) => node.text())

beforeEach(() => {
  vi.resetAllMocks()
  courseId += 1
  aiApi.weakPointsAnalysis.mockResolvedValue({ data: [] })
  aiApi.learningAdvice.mockResolvedValue({ data: {} })
  aiApi.getSessions.mockResolvedValue({ data: [] })
  // 组件会读课程名做顶栏副标题：拦掉，免得住测试里真发一次 /courses/:id
  courseApi.getCourseById.mockResolvedValue({ data: { id: COURSE_ID, name: '计算机网络' } })
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
  knowledgeApi.getKnowledgePointById.mockResolvedValue({ data: null })
  knowledgeApi.getMastery.mockResolvedValue({ data: { score: 78, level: '熟练' } })
})

describe('学生侧 AI 助教：复用教师端真接口，只裁教师专属动作', () => {
  it('学生端不用页签：学习工具变成输入框上方的提问模板；teacher 端保留六项页签', async () => {
    const student = mount(AiCenterTab, { props: { courseId, mode: 'student' } })
    await flushPromises()
    expect(student.text()).toContain('AI 学习助手')
    // 学生端没有 role=tab，三个工具以快捷提问按钮出现
    expect(tabLabels(student)).toEqual([])
    const chips = student.findAll('.quick-chip')
    expect(chips.map((node) => node.text())).toEqual(['薄弱点分析', '学习建议', '学习路径'])
    expect(student.findAll('.quick-prompts')).toHaveLength(1)

    const teacher = mount(AiCenterTab, { props: { courseId, mode: 'teacher' } })
    await flushPromises()
    expect(tabLabels(teacher)).toContain('作文批改')
    expect(tabLabels(teacher)).toContain('课程语料')
    expect(teacher.find('.quick-chip').exists()).toBe(false)
    expect(teacher.text()).toContain('AI 助教')
  })

  it('学生端不预拉薄弱点/建议（面板已移除），教师端仍按原口径拉取', async () => {
    mount(AiCenterTab, { props: { courseId, mode: 'student' } })
    await flushPromises()
    expect(aiApi.weakPointsAnalysis).not.toHaveBeenCalled()
    expect(aiApi.learningAdvice).not.toHaveBeenCalled()

    mount(AiCenterTab, { props: { courseId: courseId + 1, mode: 'teacher' } })
    await flushPromises()
    expect(aiApi.weakPointsAnalysis).toHaveBeenCalledWith({ courseId: courseId + 1 })
    expect(aiApi.learningAdvice).toHaveBeenCalledWith({ courseId: courseId + 1 })
  })

  it('点「薄弱点分析」只把模板填进输入框并聚焦，不自动发送', async () => {
    const wrapper = mount(AiCenterTab, { props: { courseId, mode: 'student' } })
    await flushPromises()
    const chip = wrapper.findAll('.quick-chip').find((node) => node.text().includes('薄弱点分析'))
    await chip!.trigger('click')
    await flushPromises()

    const textarea = wrapper.find<HTMLTextAreaElement>('.composer-input')
    expect(textarea.element.value).toBe(
      '请帮我分析一下我在《计算机网络》各章节的薄弱点，并说明判断依据。',
    )
    expect(aiApi.qaAsk).not.toHaveBeenCalled()
    expect(wrapper.findAll('.turn')).toHaveLength(0)
  })

  it('三类模板各自带上真实课程名，且都能覆盖上一次的草稿', async () => {
    const wrapper = mount(AiCenterTab, { props: { courseId, mode: 'student' } })
    await flushPromises()
    const textarea = wrapper.find<HTMLTextAreaElement>('.composer-input')
    const chipOf = (label: string) =>
      wrapper.findAll('.quick-chip').find((node) => node.text().includes(label))!

    await chipOf('学习建议').trigger('click')
    await flushPromises()
    expect(textarea.element.value).toContain('《计算机网络》')
    expect(textarea.element.value).toContain('学习建议')

    await chipOf('学习路径').trigger('click')
    await flushPromises()
    expect(textarea.element.value).toContain('《计算机网络》')
    expect(textarea.element.value).toContain('学习路径')
    expect(aiApi.qaAsk).not.toHaveBeenCalled()
  })

  it('学生端默认进欢迎态：给出课程名与可点击的提问建议', async () => {
    const wrapper = mount(AiCenterTab, { props: { courseId, mode: 'student' } })
    await flushPromises()
    expect(wrapper.text()).toContain('你好，我是你的 AI 学习助手')
    expect(wrapper.text()).toContain('计算机网络')
    const suggestions = wrapper.findAll('.suggest-item')
    expect(suggestions).toHaveLength(4)
    expect(suggestions[0].text()).toBe('帮我梳理本课程的核心知识点')
  })

  it('空历史显示「暂无历史对话」，且不编造条目', async () => {
    const wrapper = mount(AiCenterTab, { props: { courseId, mode: 'student' } })
    await flushPromises()
    expect(wrapper.text()).toContain('暂无历史对话')
    expect(wrapper.findAll('.side-item')).toHaveLength(0)
  })

  it('点建议即按真实提问发送：先落用户消息，回答只用接口返回值', async () => {
    aiApi.qaAsk.mockResolvedValue({
      data: {
        data: { sessionId: 901, answer: '先讲分层。', sources: [{ documentName: '第 1 章' }] },
      },
    })
    const wrapper = mount(AiCenterTab, { props: { courseId, mode: 'student' } })
    await flushPromises()

    await wrapper.findAll('.suggest-item')[1].trigger('click')
    await flushPromises()

    expect(aiApi.qaAsk).toHaveBeenCalledWith({ courseId, question: '这个概念可以举例说明吗？' })
    expect(wrapper.findAll('.turn-user')).toHaveLength(1)
    expect(wrapper.text()).toContain('先讲分层。')
    // 资料出处按 sources 展开，不存在的出处不渲染
    expect(wrapper.text()).toContain('参考了 1 份资料')
  })

  it('同一会话继续追问走 qaContinue，并把会话挂进侧栏', async () => {
    aiApi.qaAsk.mockResolvedValue({ data: { data: { sessionId: 902, answer: '第一条回答。' } } })
    aiApi.qaContinue.mockResolvedValue({ data: { data: { answer: '第二条回答。' } } })
    const wrapper = mount(AiCenterTab, { props: { courseId, mode: 'student' } })
    await flushPromises()

    const input = wrapper.find('textarea')
    await input.setValue('什么是分层？')
    await input.trigger('keydown', { key: 'Enter' })
    await flushPromises()

    await input.setValue('那协议呢？')
    await wrapper.find('.composer-send').trigger('click')
    await flushPromises()

    expect(aiApi.qaAsk).toHaveBeenCalledTimes(1)
    expect(aiApi.qaContinue).toHaveBeenCalledWith({ sessionId: '902', message: '那协议呢？' })
    expect(wrapper.findAll('.turn-ai')).toHaveLength(2)
    expect(wrapper.findAll('.side-item')).toHaveLength(1)
    expect(wrapper.find('.side-item-title').text()).toBe('什么是分层？')
  })

  it('「新对话」清空消息与会话，回到欢迎态', async () => {
    aiApi.qaAsk.mockResolvedValue({ data: { data: { sessionId: 903, answer: '回答。' } } })
    const wrapper = mount(AiCenterTab, { props: { courseId, mode: 'student' } })
    await flushPromises()
    await wrapper.findAll('.suggest-item')[0].trigger('click')
    await flushPromises()
    expect(wrapper.findAll('.turn')).toHaveLength(2)

    await wrapper.find('.top-new').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.turn')).toHaveLength(0)
    expect(wrapper.text()).toContain('你好，我是你的 AI 学习助手')
  })

  it('请求失败保留用户消息，只给错误与重试，不伪造 AI 回答', async () => {
    aiApi.qaAsk.mockRejectedValueOnce(new Error('boom'))
    aiApi.qaAsk.mockResolvedValueOnce({ data: { data: { sessionId: 904, answer: '重试成功。' } } })
    const wrapper = mount(AiCenterTab, { props: { courseId, mode: 'student' } })
    await flushPromises()

    const input = wrapper.find('textarea')
    await input.setValue('会失败的问题')
    await wrapper.find('.composer-send').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('.turn-user')).toHaveLength(1)
    expect(wrapper.text()).toContain('回答失败，请检查网络后重试。')
    // 失败只留占位与重试入口，绝不伪造一条 AI 正文
    expect(wrapper.findAll('.turn-ai .turn-text')).toHaveLength(0)

    await wrapper.find('.mini-btn').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('重试成功。')
    // 重试不重复插入用户消息
    expect(wrapper.findAll('.turn-user')).toHaveLength(1)
  })

  it('请求进行中禁用发送，避免重复提交', async () => {
    let resolveAsk: (value: unknown) => void = () => {}
    aiApi.qaAsk.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveAsk = resolve
        }),
    )
    const wrapper = mount(AiCenterTab, { props: { courseId, mode: 'student' } })
    await flushPromises()

    await wrapper.find('textarea').setValue('慢问题')
    await wrapper.find('.composer-send').trigger('click')
    await nextTick()
    expect(wrapper.text()).toContain('正在思考…')
    expect(wrapper.find('.composer-send').attributes('disabled')).toBeDefined()

    resolveAsk({ data: { data: { sessionId: 905, answer: '慢回答。' } } })
    await flushPromises()
    expect(wrapper.text()).toContain('慢回答。')
  })

  it('知识图谱 student 模式隐藏「同步到引擎」，teacher 模式保留', async () => {
    const student = mount(KnowledgeGraphTab, { props: { courseId, mode: 'student' } })
    await flushPromises()
    expect(findButton(student, '同步到引擎')).toBeUndefined()
    expect(findButton(student, '重载图谱')).toBeTruthy()

    const teacher = mount(KnowledgeGraphTab, { props: { courseId, mode: 'teacher' } })
    await flushPromises()
    expect(findButton(teacher, '同步到引擎')).toBeTruthy()
  })

  it('学生点节点：按当前学生拉掌握度并在卡片上显示', async () => {
    const wrapper = mount(KnowledgeGraphTab, { props: { courseId, mode: 'student' } })
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
      props: { courseId, mode: 'teacher' },
    })
    await flushPromises()
    await wrapper.findAll('.kg-node')[0].trigger('click')
    await flushPromises()
    expect(knowledgeApi.getMastery).not.toHaveBeenCalled()
    expect(wrapper.text()).not.toContain('我的掌握度')
  })

  it('无作答证据：显示未评估并说明原因，不用“暂无数据”占位', async () => {
    knowledgeApi.getMastery.mockResolvedValue({
      data: {
        score: null,
        level: '未评估',
        answered: false,
        totalQuestions: 2,
        answeredQuestions: 0,
        note: '该知识点暂无作答记录',
      },
    })
    const wrapper = mount(KnowledgeGraphTab, { props: { courseId, mode: 'student' } })
    await flushPromises()
    await wrapper.findAll('.kg-node')[0].trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('我的掌握度 未评估')
    const chip = wrapper.findAll('.chip').find((node) => node.text().includes('我的掌握度'))
    expect(chip?.attributes('title')).toContain('该知识点暂无作答记录')
    expect(wrapper.text()).not.toContain('暂无数据')
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
    const wrapper = mount(KnowledgeGraphTab, { props: { courseId, mode: 'teacher' } })
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
    const wrapper = mount(KnowledgeGraphTab, { props: { courseId, mode: 'teacher' } })
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
    const wrapper = mount(KnowledgeGraphTab, { props: { courseId, mode: 'teacher' } })
    await flushPromises()

    await planTo(wrapper, '1', '3')

    expect(wrapper.text()).toContain('不可达')
    expect(wrapper.findAll('.kg-node.is-path')).toHaveLength(0)
    expect(wrapper.findAll('.kg-svg line.is-path')).toHaveLength(0)
    expect(wrapper.findAll('.kg-node.is-dim')).toHaveLength(0)
  })
})
