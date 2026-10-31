import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const knowledgeApi = vi.hoisted(() => ({
  getTree: vi.fn(),
  getKnowledgePoints: vi.fn(),
  createKnowledgePoint: vi.fn(),
  updateKnowledgePoint: vi.fn(),
  deleteKnowledgePoint: vi.fn(),
  addRelation: vi.fn(),
  getMastery: vi.fn(),
}))
const relationApi = vi.hoisted(() => ({
  getPrerequisites: vi.fn(),
  addRelation: vi.fn(),
  removeRelation: vi.fn(),
}))
const courseApi = vi.hoisted(() => ({
  getAllMyCourses: vi.fn(),
}))
const toast = vi.hoisted(() => vi.fn())

vi.mock('@/api/knowledge', () => ({ default: knowledgeApi }))
vi.mock('@/api/knowledgePointRelation', () => ({ default: relationApi }))
vi.mock('@/api/course', () => ({ getAllMyCourses: courseApi.getAllMyCourses }))
vi.mock('../composables/ui', () => ({ useV2Ui: () => ({ toast }) }))
vi.mock('@/utils/logger', () => ({ default: { error: vi.fn(), warn: vi.fn(), info: vi.fn() } }))

import KnowledgeManagement from '../views/teacher/V2KnowledgeManagementView.vue'

const COURSES = [
  { id: 1, name: 'Python程序设计' },
  { id: 4007, name: '计算机网络' },
]
const POINTS = [
  { id: 11, name: '变量与类型', courseId: 1, difficulty: 'EASY' },
  { id: 12, name: '循环结构', courseId: 1, parentId: 11, difficulty: 'MEDIUM' },
]

const courseOptions = (wrapper: ReturnType<typeof mount>) =>
  wrapper.findAll('.course-pick option').map((node) => node.text())

beforeEach(() => {
  vi.clearAllMocks()
  courseApi.getAllMyCourses.mockResolvedValue(COURSES)
  knowledgeApi.getTree.mockResolvedValue({ data: [] })
  knowledgeApi.getKnowledgePoints.mockResolvedValue({ data: POINTS })
  relationApi.getPrerequisites.mockResolvedValue({ data: [] })
})

describe('教师端全局知识库入口（跨课程）', () => {
  it('挂载即用真实教师课程接口填下拉；未选课程时不发知识点请求', async () => {
    const wrapper = mount(KnowledgeManagement)
    await flushPromises()
    expect(courseApi.getAllMyCourses).toHaveBeenCalledTimes(1)
    expect(courseOptions(wrapper)).toContain('Python程序设计')
    expect(courseOptions(wrapper)).toContain('计算机网络')
    expect(knowledgeApi.getTree).not.toHaveBeenCalled()
    expect(knowledgeApi.getKnowledgePoints).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('请先选择')
  })

  it('选中课程后按课程拉真实知识树与列表（端点与参数取自后端契约）', async () => {
    const wrapper = mount(KnowledgeManagement)
    await flushPromises()
    await wrapper.find('.course-pick').setValue('1')
    await flushPromises()
    expect(knowledgeApi.getTree).toHaveBeenCalledWith(1)
    expect(knowledgeApi.getKnowledgePoints).toHaveBeenCalledWith(1)
    const text = wrapper.text()
    expect(text).toContain('变量与类型')
    expect(text).not.toContain('示例数据')
  })

  it('课程接口失败时下拉为空且不静默：空态文案与成功态可区分', async () => {
    courseApi.getAllMyCourses.mockRejectedValueOnce(new Error('offline'))
    const wrapper = mount(KnowledgeManagement)
    await flushPromises()
    expect(courseOptions(wrapper)).toEqual(['选择课程'])
    expect(wrapper.text()).toContain('请先选择')
  })

  it('课程无知识点时给出明确空态，而不是空白表格', async () => {
    knowledgeApi.getKnowledgePoints.mockResolvedValue({ data: [] })
    const wrapper = mount(KnowledgeManagement)
    await flushPromises()
    await wrapper.find('.course-pick').setValue('1')
    await flushPromises()
    expect(wrapper.text()).toContain('该课程还没有知识点')
  })
})
