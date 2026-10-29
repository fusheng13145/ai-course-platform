import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const taskApi = vi.hoisted(() => ({
  getAllTasks: vi.fn(),
  listByCourse: vi.fn(),
  getTaskById: vi.fn(),
}))
const chapterApi = vi.hoisted(() => ({ getChapters: vi.fn() }))
const submissionApi = vi.hoisted(() => ({
  submitTask: vi.fn(),
  getAllMySubmissions: vi.fn(),
}))
const studentApi = vi.hoisted(() => ({
  getGrades: vi.fn(),
  getAssignmentDetail: vi.fn(),
}))
const commonApi = vi.hoisted(() => ({ uploadFile: vi.fn() }))
const discussionApi = vi.hoisted(() => ({
  getTopics: vi.fn(),
  getTopicDetail: vi.fn(),
  replyTopic: vi.fn(),
  likePost: vi.fn(),
  createTopic: vi.fn(),
  pinTopic: vi.fn(),
  closeTopic: vi.fn(),
  deletePost: vi.fn(),
}))

vi.mock('@/api/task', () => ({ default: taskApi }))
vi.mock('@/api/chapter', () => ({ default: chapterApi }))
vi.mock('@/api/submission', () => ({ default: submissionApi }))
vi.mock('@/api/student', () => ({ default: studentApi }))
vi.mock('@/api/common', () => ({ default: commonApi }))
vi.mock('@/api/discussion', () => ({
  default: discussionApi,
  normalizeDiscussionTopic: (topic: unknown) => topic,
  normalizeDiscussionPost: (post: unknown) => post,
}))

const toast = vi.hoisted(() => vi.fn())
vi.mock('../composables/ui', () => ({
  useV2Ui: () => ({ toast }),
}))

import TasksTab from '../views/course/tabs/TasksTab.vue'
import GradesTab from '../views/course/tabs/GradesTab.vue'
import DiscussionTab from '../views/course/tabs/DiscussionTab.vue'
import { courseTabBlock } from '../data/courseTabData'

/** V2Modal 走 Teleport，测试里换成同 slot 结构的存根，确认按钮仍能被点到 */
const stubs = {
  V2Modal: {
    props: ['open', 'title'],
    template: '<div v-if="open" class="modal-stub"><slot /><slot name="footer" /></div>',
  },
}

const COURSE_ID = 4007

function mountTasks(role: 'student' | 'teacher' = 'student') {
  return mount(TasksTab, { props: { courseId: COURSE_ID, role }, global: { stubs } })
}
function mountGrades(role: 'student' | 'admin' = 'student') {
  return mount(GradesTab, { props: { role, courseId: COURSE_ID } })
}
function mountDiscussion(role: 'student' | 'teacher' = 'student') {
  return mount(DiscussionTab, { props: { courseId: COURSE_ID, role }, global: { stubs } })
}

const findButton = (wrapper: ReturnType<typeof mount>, label: string) =>
  wrapper.findAll('button').find((node) => node.text().includes(label))

beforeEach(() => {
  vi.clearAllMocks()
  toast.mockReset()
  taskApi.getAllTasks.mockResolvedValue([])
  chapterApi.getChapters.mockResolvedValue({ data: [] })
  submissionApi.getAllMySubmissions.mockResolvedValue([])
  submissionApi.submitTask.mockResolvedValue({ data: {} })
  studentApi.getGrades.mockResolvedValue({ data: { courses: [] } })
  studentApi.getAssignmentDetail.mockResolvedValue({ data: { assignment: null, submission: null } })
  commonApi.uploadFile.mockResolvedValue({ data: { url: '/uploads/report.pdf' } })
  discussionApi.getTopics.mockResolvedValue({ data: { records: [] } })
  discussionApi.getTopicDetail.mockResolvedValue({ data: { topic: null, posts: [] } })
  discussionApi.replyTopic.mockResolvedValue({ data: {} })
  discussionApi.likePost.mockResolvedValue({ data: { liked: true, likeCount: 1 } })
  discussionApi.createTopic.mockResolvedValue({ data: {} })
})

describe('v2 学生课程 · 作业页签', () => {
  it('列表按真实任务与我的提交派生 待完成 / 逾期 / 已完成', async () => {
    taskApi.getAllTasks.mockResolvedValue([
      {
        id: 9001,
        title: '实验一报告',
        description: '提交实验报告',
        dueDate: '2099-01-01T00:00:00',
        maxScore: 100,
        isPublished: true,
      },
      { id: 9002, title: '错过截止的作业', dueDate: '2020-01-01T00:00:00', isPublished: true },
      { id: 9003, title: '已批改的作业', dueDate: '2020-02-01T00:00:00', isPublished: true },
      { id: 9004, title: '草稿不该出现', isPublished: false },
    ])
    submissionApi.getAllMySubmissions.mockResolvedValue([
      { id: 5503, taskId: 9003, status: 'GRADED', score: 88, maxScore: 100, attemptNumber: 1 },
    ])

    const wrapper = mountTasks()
    await flushPromises()

    expect(taskApi.getAllTasks).toHaveBeenCalledWith({ courseId: COURSE_ID })
    expect(wrapper.text()).toContain('实验一报告')
    expect(wrapper.text()).toContain('待完成')
    expect(wrapper.text()).toContain('逾期')
    expect(wrapper.text()).toContain('已完成')
    expect(wrapper.text()).toContain('88 / 100')
    // 草稿任务对学生不可见
    expect(wrapper.text()).not.toContain('草稿不该出现')
    wrapper.unmount()
  })

  it('空态与失败态可区分：空列表不等于加载失败', async () => {
    const empty = mountTasks()
    await flushPromises()
    expect(empty.text()).toContain('本课程暂无已发布作业')
    expect(empty.find('.callout.danger').exists()).toBe(false)
    empty.unmount()

    taskApi.getAllTasks.mockRejectedValueOnce(new Error('500'))
    const failed = mountTasks()
    await flushPromises()
    expect(failed.find('.callout.danger').text()).toContain('作业列表加载失败')
    expect(failed.text()).not.toContain('本课程暂无已发布作业')
    failed.unmount()
  })

  it('提交作业走真实接口：POST /submissions 的 { content, fileUrls } 白名单字段', async () => {
    taskApi.getAllTasks.mockResolvedValue([
      {
        id: 9001,
        title: '实验一报告',
        description: '提交实验报告',
        dueDate: '2099-01-01T00:00:00',
        maxScore: 100,
        isPublished: true,
      },
    ])
    studentApi.getAssignmentDetail.mockResolvedValue({
      data: {
        assignment: {
          id: 9001,
          title: '实验一报告',
          description: '提交实验报告',
          dueDate: '2099-01-01T00:00:00',
          maxScore: 100,
        },
        submission: null,
      },
    })

    const wrapper = mountTasks()
    await flushPromises()
    await findButton(wrapper, '查看详情')?.trigger('click')
    await flushPromises()

    expect(studentApi.getAssignmentDetail).toHaveBeenCalledWith(9001)
    expect(wrapper.find('.modal-stub').text()).toContain('提交实验报告')

    await wrapper.find('.modal-stub textarea').setValue('我的实验结果')
    const buttons = wrapper.findAll('.modal-stub button')
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(submissionApi.submitTask).toHaveBeenCalledTimes(1)
    expect(submissionApi.submitTask).toHaveBeenCalledWith(9001, {
      content: '我的实验结果',
      fileUrls: [],
    })
    expect(toast).toHaveBeenCalledWith('提交成功，等待教师批改', 'ok')
    wrapper.unmount()
  })

  it('提交失败保留表单并允许重试', async () => {
    taskApi.getAllTasks.mockResolvedValue([
      {
        id: 9001,
        title: '实验一报告',
        dueDate: '2099-01-01T00:00:00',
        isPublished: true,
      },
    ])
    submissionApi.submitTask.mockRejectedValueOnce(new Error('502'))

    const wrapper = mountTasks()
    await flushPromises()
    await findButton(wrapper, '查看详情')?.trigger('click')
    await flushPromises()
    await wrapper.find('.modal-stub textarea').setValue('保留这段作答')
    const buttons = wrapper.findAll('.modal-stub button')
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(wrapper.find('.form-error').text()).toContain('提交失败')
    const textarea = wrapper.find('.modal-stub textarea').element as HTMLTextAreaElement
    expect(textarea.value).toBe('保留这段作答')
    // 提交按钮仍在，可直接重试
    expect(findButton(wrapper, '提交作业')).toBeTruthy()
    wrapper.unmount()
  })
})

describe('v2 学生课程 · 成绩页签', () => {
  const gradesPayload = {
    data: {
      courses: [
        {
          courseId: COURSE_ID,
          courseName: '计算机网络',
          finalScore: 86.5,
          assignments: [
            {
              submissionId: 5501,
              taskId: 9001,
              title: '实验一',
              score: 88,
              maxScore: 100,
              status: 'GRADED',
              submittedAt: '2026-10-01T10:00:00',
            },
          ],
          exams: [
            {
              sessionId: 44,
              title: '期中测验',
              score: 76,
              totalScore: 100,
              status: 'GRADED',
              submittedAt: '2026-10-20T09:00:00',
            },
          ],
        },
        { courseId: 4008, courseName: '数据结构', finalScore: 91, assignments: [], exams: [] },
      ],
    },
  }

  it('按 courseId 过滤 /student/grades，展示总评、成绩项与教师评语', async () => {
    studentApi.getGrades.mockResolvedValue(gradesPayload)
    submissionApi.getAllMySubmissions.mockResolvedValue([{ id: 5501, feedback: '报告完整' }])

    const wrapper = mountGrades()
    await flushPromises()

    expect(studentApi.getGrades).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('86.5')
    expect(wrapper.text()).toContain('实验一')
    expect(wrapper.text()).toContain('期中测验')
    expect(wrapper.text()).toContain('76%')
    expect(wrapper.text()).toContain('报告完整')
    // 只看本课程：其它课程的数据不得混入
    expect(wrapper.text()).not.toContain('数据结构')
    wrapper.unmount()
  })

  it('空态与失败态可区分，且不提供假导出', async () => {
    const empty = mountGrades()
    await flushPromises()
    expect(empty.text()).toContain('本课程暂无成绩记录')
    expect(empty.find('.callout.danger').exists()).toBe(false)
    expect(empty.text()).toContain('不提供「导出成绩」')
    expect(findButton(empty, '导出成绩')).toBeUndefined()
    empty.unmount()

    studentApi.getGrades.mockRejectedValueOnce(new Error('500'))
    const failed = mountGrades()
    await flushPromises()
    expect(failed.find('.callout.danger').text()).toContain('成绩数据加载失败')
    expect(failed.text()).not.toContain('本课程暂无成绩记录')
    failed.unmount()
  })

  it('管理员仍走既有演示视图（本期不动）', async () => {
    const wrapper = mountGrades('admin')
    await flushPromises()
    expect(studentApi.getGrades).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('课程成绩概览')
    wrapper.unmount()
  })
})

describe('v2 学生课程 · 讨论页签', () => {
  const topic = {
    id: 1,
    title: '如何准备期中',
    content: '想听听大家的复习安排',
    authorName: '林知远',
    createdAt: '2026-10-01T09:00:00',
    postCount: 2,
    pinned: true,
    closed: false,
  }

  it('学生可读话题列表，教师动作不渲染', async () => {
    discussionApi.getTopics.mockResolvedValue({ data: { records: [topic] } })
    discussionApi.getTopicDetail.mockResolvedValue({
      data: {
        topic,
        posts: [{ id: 77, content: '我先说', authorName: '王雨桐', likeCount: 3 }],
      },
    })

    const wrapper = mountDiscussion('student')
    await flushPromises()
    expect(discussionApi.getTopics).toHaveBeenCalledWith(COURSE_ID, {})
    expect(wrapper.text()).toContain('如何准备期中')

    await findButton(wrapper, '进入话题')?.trigger('click')
    await flushPromises()
    expect(wrapper.find('.thread-actions').exists()).toBe(false)
    expect(findButton(wrapper, '置顶')).toBeUndefined()
    expect(findButton(wrapper, '关闭话题')).toBeUndefined()
    expect(findButton(wrapper, '删除')).toBeUndefined()
    expect(wrapper.text()).toContain('我先说')
    wrapper.unmount()
  })

  it('回复走真实接口：POST /discussion/topic/{id}/reply', async () => {
    discussionApi.getTopics.mockResolvedValue({ data: { records: [topic] } })
    discussionApi.getTopicDetail.mockResolvedValue({ data: { topic, posts: [] } })

    const wrapper = mountDiscussion('student')
    await flushPromises()
    await findButton(wrapper, '进入话题')?.trigger('click')
    await flushPromises()

    await wrapper.find('.modal-stub textarea').setValue('我也想参加')
    const buttons = wrapper.findAll('.modal-stub button')
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(discussionApi.replyTopic).toHaveBeenCalledWith(1, { content: '我也想参加' })
    wrapper.unmount()
  })

  it('空态与失败态可区分', async () => {
    const empty = mountDiscussion()
    await flushPromises()
    expect(empty.text()).toContain('暂无讨论话题')
    expect(empty.find('.callout').exists()).toBe(false)
    empty.unmount()

    discussionApi.getTopics.mockRejectedValueOnce(new Error('500'))
    const failed = mountDiscussion()
    await flushPromises()
    expect(failed.find('.callout').text()).toContain('话题列表加载失败')
    expect(failed.text()).not.toContain('暂无讨论话题')
    failed.unmount()
  })

  it('教师仍看到置顶 / 关闭动作（回归守卫）', async () => {
    discussionApi.getTopics.mockResolvedValue({ data: { records: [topic] } })
    discussionApi.getTopicDetail.mockResolvedValue({ data: { topic, posts: [] } })

    const wrapper = mountDiscussion('teacher')
    await flushPromises()
    await findButton(wrapper, '进入话题')?.trigger('click')
    await flushPromises()

    expect(wrapper.find('.thread-actions').exists()).toBe(true)
    expect(findButton(wrapper, '取消置顶')).toBeTruthy()
    expect(findButton(wrapper, '关闭话题')).toBeTruthy()
    wrapper.unmount()
  })
})

describe('示例数据层收缩', () => {
  it('学生三个已接真的页签不再返回示例块', () => {
    expect(courseTabBlock('student', 'tasks')).toBeNull()
    expect(courseTabBlock('student', 'discussion')).toBeNull()
    expect(courseTabBlock('student', 'grades')).toBeNull()
    // 对照：管理员题库仍由 ModuleTab 渲染，示例块必须保留警示
    expect(courseTabBlock('admin', 'bank')?.note || '').toContain('示例')
  })
})
