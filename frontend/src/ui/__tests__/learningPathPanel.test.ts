import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import LearningPathPanel from '../views/specials/academic/LearningPathPanel.vue'

const studentApi = vi.hoisted(() => ({ getAllMyCourses: vi.fn() }))
const learningPathApi = vi.hoisted(() => ({
  getActivePath: vi.fn(),
  getMyPaths: vi.fn(),
  getPathDetail: vi.fn(),
  getDailyTasks: vi.fn(),
  pausePath: vi.fn(),
  resumePath: vi.fn(),
}))
const aiApi = vi.hoisted(() => ({ generateLearningPath: vi.fn() }))

vi.mock('@/api/student', () => ({
  getAllMyCourses: studentApi.getAllMyCourses,
}))
vi.mock('@/api/learningPath', () => ({ default: learningPathApi }))
vi.mock('@/api/ai', () => ({ default: aiApi }))

const COURSE = { id: 4178, name: '计算机网络' }
const activePath = {
  id: 9001,
  courseId: 4178,
  pathName: '计算机网络 · 传输层补强',
  status: 'ACTIVE',
  completionRate: 62.5,
  currentStage: 3,
  totalStages: 5,
  estimatedCompletionDate: '2026-12-10T00:00:00',
}
const pathDetail = {
  path: activePath,
  nodes: [
    {
      id: 1,
      title: 'TCP 三次握手',
      nodeType: 'STUDY',
      status: 'COMPLETED',
      orderIndex: 1,
      estimatedTime: 30,
      actualTime: 25,
    },
    {
      id: 2,
      title: '拥塞控制',
      nodeType: 'PRACTICE',
      status: 'IN_PROGRESS',
      orderIndex: 2,
      estimatedTime: 45,
      actualTime: 20,
    },
  ],
  statusStats: { COMPLETED: 1, IN_PROGRESS: 1 },
  totalNodes: 2,
  stagesParsed: [
    {
      stageNumber: 1,
      stageName: '传输层基础',
      status: 'COMPLETED',
      nodeCount: 1,
      estimatedTime: 30,
      nodeIds: [1],
    },
    {
      stageNumber: 2,
      stageName: '拥塞控制',
      status: 'IN_PROGRESS',
      nodeCount: 1,
      estimatedTime: 45,
      nodeIds: [2],
    },
  ],
}
const dailyTasks = [
  {
    dayNumber: 1,
    date: '2026-11-18',
    taskTitle: '复习三次握手',
    estimatedMinutes: 30,
    status: 'TODAY',
  },
]

function envelope(data: unknown) {
  return { code: 200, message: 'ok', data }
}

function findButton(wrapper: VueWrapper, label: string) {
  return wrapper.findAll('button').find((node) => node.text().includes(label))
}

async function settle() {
  await flushPromises()
  await flushPromises()
}

beforeEach(() => {
  vi.clearAllMocks()
  studentApi.getAllMyCourses.mockResolvedValue([COURSE])
  learningPathApi.getActivePath.mockResolvedValue(envelope(activePath))
  learningPathApi.getMyPaths.mockResolvedValue(envelope([]))
  learningPathApi.getPathDetail.mockResolvedValue(envelope(pathDetail))
  learningPathApi.getDailyTasks.mockResolvedValue(envelope(dailyTasks))
  learningPathApi.pausePath.mockResolvedValue(envelope(null))
  learningPathApi.resumePath.mockResolvedValue(envelope(null))
  aiApi.generateLearningPath.mockResolvedValue(envelope({ status: 'created' }))
})

describe('学业档案 · 学习路径', () => {
  it('渲染路径进度、阶段、节点与每日任务，并可暂停', async () => {
    const wrapper = mount(LearningPathPanel)
    await settle()

    expect(learningPathApi.getActivePath).toHaveBeenCalledWith(4178)
    expect(wrapper.text()).toContain('计算机网络 · 传输层补强')
    expect(wrapper.text()).toContain('63%')
    expect(wrapper.text()).toContain('3/5')
    expect(wrapper.text()).toContain('1/2')
    expect(wrapper.text()).toContain('TCP 三次握手')
    expect(wrapper.text()).toContain('拥塞控制')
    expect(wrapper.text()).toContain('复习三次握手')

    await findButton(wrapper, '暂停')?.trigger('click')
    await settle()
    expect(learningPathApi.pausePath).toHaveBeenCalledWith(9001)
    wrapper.unmount()
  })

  it('无路径时展示生成入口，生成后重新加载', async () => {
    learningPathApi.getActivePath
      .mockResolvedValueOnce(envelope(null))
      .mockResolvedValue(envelope(activePath))
    const wrapper = mount(LearningPathPanel)
    await settle()

    expect(wrapper.text()).toContain('生成学习路径')
    await wrapper.find('input').setValue('补齐传输层薄弱点')
    await findButton(wrapper, '生成学习路径')?.trigger('click')
    await settle()

    expect(aiApi.generateLearningPath).toHaveBeenCalledWith({
      courseId: '4178',
      goal: '补齐传输层薄弱点',
    })
    expect(wrapper.text()).toContain('计算机网络 · 传输层补强')
    wrapper.unmount()
  })
})
