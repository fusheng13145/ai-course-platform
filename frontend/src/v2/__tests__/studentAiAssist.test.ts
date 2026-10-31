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
vi.mock('../composables/ui', () => ({ useV2Ui: () => ({ toast }) }))
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
