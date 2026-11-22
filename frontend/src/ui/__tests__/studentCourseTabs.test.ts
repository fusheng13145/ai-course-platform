import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { existsSync } from 'node:fs'
import { join } from 'node:path'

/** 作业页在新标签打开，列表只负责 resolve + window.open */
const routerResolve = vi.hoisted(() => vi.fn((path: string) => ({ href: path })))
const openMock = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({
  useRouter: () => ({ resolve: routerResolve }),
}))

const taskApi = vi.hoisted(() => ({
  getAllTasks: vi.fn(),
  listByCourse: vi.fn(),
  getTaskById: vi.fn(),
}))
const chapterApi = vi.hoisted(() => ({ getChapters: vi.fn() }))
const submissionApi = vi.hoisted(() => ({
  submitTask: vi.fn(),
  getAllMySubmissions: vi.fn(),
  getUnsubmitted: vi.fn(),
  remindUnsubmitted: vi.fn(),
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
  useUi: () => ({ toast }),
}))
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ userId: 2 }),
}))

import TasksTab from '../views/course/tabs/TasksTab.vue'
import GradesTab from '../views/course/tabs/GradesTab.vue'
import DiscussionTab from '../views/course/tabs/DiscussionTab.vue'

/** Modal 走 Teleport，测试里换成同 slot 结构的存根，确认按钮仍能被点到 */
const stubs = {
  Modal: {
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
  vi.stubGlobal('open', openMock)
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

describe('学生课程 · 作业页签', () => {
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
      { id: 9005, title: '未明确发布的任务' },
      { id: 9006, title: '测验不该出现', taskType: 'QUIZ', isPublished: true },
      { id: 9007, title: '视频不该出现', taskType: 'VIDEO', isPublished: true },
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
    // 草稿、isPublished 缺失、测验 / 视频任务都不进入作业提交列表
    expect(wrapper.text()).not.toContain('草稿不该出现')
    expect(wrapper.text()).not.toContain('未明确发布的任务')
    expect(wrapper.text()).not.toContain('测验不该出现')
    expect(wrapper.text()).not.toContain('视频不该出现')
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

  it('列表只负责跳转：重做列与查看详情都进独立作业页', async () => {
    taskApi.getAllTasks.mockResolvedValue([
      {
        id: 9001,
        title: '已批改作业',
        dueDate: '2099-01-01T00:00:00',
        maxScore: 100,
        isPublished: true,
      },
      {
        id: 9002,
        title: '未提交作业',
        dueDate: '2099-01-01T00:00:00',
        maxScore: 100,
        isPublished: true,
      },
    ])
    submissionApi.getAllMySubmissions.mockResolvedValue([
      { id: 5700, taskId: 9001, status: 'GRADED', score: 88, attemptNumber: 1, content: '旧作答' },
    ])

    const wrapper = mountTasks()
    await flushPromises()

    // 表头独立列出「重做」列
    expect(wrapper.findAll('thead th').map((th) => th.text())).toEqual([
      '作业',
      '状态',
      '截止时间',
      '得分',
      '重做',
      '操作',
    ])

    const rows = wrapper.findAll('tbody tr')
    const rowOf = (title: string) => {
      const matched = rows.find((tr) => tr.text().includes(title))
      expect(matched).toBeTruthy()
      return matched as (typeof rows)[number]
    }

    // 已批改 → 重新提交；未提交 → 提交作业
    expect(rowOf('已批改作业').findAll('td')[4].text()).toBe('重新提交')
    expect(rowOf('未提交作业').findAll('td')[4].text()).toBe('提交作业')

    // 点「重新提交」在新标签打开沉浸式作业页，列表保留
    await rowOf('已批改作业').findAll('td')[4].find('button').trigger('click')
    await flushPromises()
    expect(openMock).toHaveBeenCalledWith('/assignment/9001', '_blank', 'noopener,noreferrer')
    expect(wrapper.find('.modal-stub').exists()).toBe(false)

    // 「查看详情」同样开新标签
    openMock.mockClear()
    await rowOf('未提交作业').findAll('td')[5].find('button').trigger('click')
    await flushPromises()
    expect(openMock).toHaveBeenCalledWith('/assignment/9002', '_blank', 'noopener,noreferrer')

    wrapper.unmount()
  })
})

describe('课程 · 教师任务页签（回归守卫）', () => {
  it('教师仍看到任务管理列表、发布态与新建入口', async () => {
    taskApi.getAllTasks.mockResolvedValue([
      {
        id: 9100,
        title: '期中项目',
        taskType: '作业',
        dueDate: '2099-01-01T00:00:00',
        isPublished: true,
      },
      { id: 9101, title: '未发布草稿', taskType: '作业', isPublished: false },
    ])

    const wrapper = mountTasks('teacher')
    await flushPromises()

    expect(taskApi.getAllTasks).toHaveBeenCalledWith({ courseId: COURSE_ID })
    expect(chapterApi.getChapters).toHaveBeenCalledWith(COURSE_ID)
    expect(wrapper.text()).toContain('课程任务')
    expect(wrapper.text()).toContain('期中项目')
    expect(wrapper.text()).toContain('未发布草稿')
    expect(wrapper.text()).toContain('已发布')
    expect(wrapper.text()).toContain('草稿')
    expect(findButton(wrapper, '新建任务')).toBeTruthy()
    // 教师视图不触发学生侧数据聚合
    expect(submissionApi.getAllMySubmissions).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('教师可查看未交名单并一键催交', async () => {
    taskApi.getAllTasks.mockResolvedValue([
      {
        id: 9100,
        title: '期中项目',
        taskType: '作业',
        dueDate: '2099-01-01T00:00:00',
        isPublished: true,
      },
    ])
    submissionApi.getUnsubmitted.mockResolvedValue({
      data: [{ studentId: 2016, username: 'st016', fullName: '林宇', studentNo: '202503008' }],
    })
    submissionApi.remindUnsubmitted.mockResolvedValue({ data: { count: 1 } })

    const wrapper = mountTasks('teacher')
    await flushPromises()

    await findButton(wrapper, '未交名单')?.trigger('click')
    await flushPromises()

    expect(submissionApi.getUnsubmitted).toHaveBeenCalledWith(9100)
    expect(wrapper.text()).toContain('st016')
    expect(wrapper.text()).toContain('林宇')
    expect(wrapper.text()).toContain('202503008')

    await findButton(wrapper, '一键催交')?.trigger('click')
    await flushPromises()
    expect(submissionApi.remindUnsubmitted).toHaveBeenCalledWith(9100)
    expect(toast).toHaveBeenCalledWith('已向 1 名学生发送催交通知', 'ok')
    wrapper.unmount()
  })
})

describe('学生课程 · 成绩页签', () => {
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
    // 表头：得分与满分两列（考试得分由得分率折算到卷面分）
    const th = wrapper.findAll('thead th').map((node) => node.text())
    expect(th).toEqual(['成绩项', '类型', '得分', '满分', '状态', '提交时间', '教师评语'])
    // 默认按提交时间倒序：10-20 的考试在前，10-01 的作业在后
    // 考试行：76% × 100 = 得分 76 / 满分 100；作业行：得分 88 / 满分 100
    const rows = wrapper.findAll('tbody tr')
    expect(rows[0].findAll('td').map((td) => td.text())).toEqual([
      '期中测验',
      '考试',
      '76',
      '100',
      '已批改',
      '2026-10-20 09:00',
      '—',
    ])
    expect(rows[1].findAll('td').map((td) => td.text())).toEqual([
      '实验一',
      '作业',
      '88',
      '100',
      '已批改',
      '2026-10-01 10:00',
      '报告完整',
    ])
    expect(wrapper.text()).toContain('报告完整')

    // 切换为最早优先：10-01 的作业回到第一行
    const orderToggle = wrapper
      .findAll('button')
      .find((node) => node.text().includes('时间 ↓ 最新优先'))
    expect(orderToggle).toBeTruthy()
    await orderToggle?.trigger('click')
    const ascRows = wrapper.findAll('tbody tr')
    expect(ascRows[0].findAll('td')[0].text()).toBe('实验一')
    expect(ascRows[1].findAll('td')[0].text()).toBe('期中测验')
    // 只看本课程：其它课程的数据不得混入
    expect(wrapper.text()).not.toContain('数据结构')
    wrapper.unmount()
  })

  it('缺卷面分的考试行：满分与得分都给 —，不拿百分比冒充分数', async () => {
    studentApi.getGrades.mockResolvedValue({
      data: {
        courses: [
          {
            courseId: COURSE_ID,
            courseName: '计算机网络',
            finalScore: null,
            assignments: [],
            exams: [
              {
                sessionId: 7,
                title: '缺卷面分的测验',
                score: 66.7,
                status: 'GRADED',
                submittedAt: '2026-10-01T10:00:00',
              },
            ],
          },
        ],
      },
    })

    const wrapper = mountGrades()
    await flushPromises()

    expect(wrapper.text()).toContain('缺卷面分的测验')
    const cells = wrapper
      .findAll('tbody tr')[0]
      .findAll('td')
      .map((td) => td.text())
    // 没有卷面分就没有可折算的满分，得分与满分都给 —
    expect(cells[2]).toBe('—')
    expect(cells[3]).toBe('—')
    // 也不拿百分制得分率冒充分数
    expect(wrapper.text()).not.toContain('66.7')
    wrapper.unmount()
  })

  it('空态与失败态可区分，且不提供假导出', async () => {
    // 真·空态：课程条目存在，但作业 / 考试都为空
    studentApi.getGrades.mockResolvedValueOnce({
      data: {
        courses: [
          {
            courseId: COURSE_ID,
            courseName: '计算机网络',
            finalScore: null,
            assignments: [],
            exams: [],
          },
        ],
      },
    })
    const empty = mountGrades()
    await flushPromises()
    expect(empty.text()).toContain('本课程暂无成绩记录')
    expect(empty.text()).not.toContain('成绩数据未能获取')
    expect(empty.find('.callout.danger').exists()).toBe(false)
    expect(empty.text()).not.toContain('成绩导出待扩展')
    expect(findButton(empty, '导出成绩')).toBeUndefined()
    empty.unmount()

    studentApi.getGrades.mockRejectedValueOnce(new Error('500'))
    const failed = mountGrades()
    await flushPromises()
    expect(failed.find('.callout.danger').text()).toContain('成绩数据加载失败')
    expect(failed.text()).not.toContain('本课程暂无成绩记录')
    failed.unmount()
  })

  it('课程条目缺失（后端吞异常）显示不确定态并可重试，不冒充空成绩', async () => {
    // StudentDashboardComponent#getGrades 捕获异常后返回 { courses: [] }
    studentApi.getGrades.mockResolvedValueOnce({ data: { courses: [] } })
    const wrapper = mountGrades()
    await flushPromises()

    expect(wrapper.text()).toContain('成绩数据未能获取（可能暂不可用）')
    expect(wrapper.text()).not.toContain('本课程暂无成绩记录')
    const retry = findButton(wrapper, '重试')
    expect(retry).toBeTruthy()

    await retry?.trigger('click')
    await flushPromises()
    expect(studentApi.getGrades).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('同一任务多次提交只保留最新一次，成绩行不重复', async () => {
    studentApi.getGrades.mockResolvedValue({
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
                score: 70,
                maxScore: 100,
                status: 'GRADED',
                submittedAt: '2026-10-01T10:00:00',
              },
              {
                submissionId: 5502,
                taskId: 9001,
                title: '实验一',
                score: 92,
                maxScore: 100,
                status: 'GRADED',
                submittedAt: '2026-10-05T10:00:00',
              },
            ],
            exams: [],
          },
        ],
      },
    })

    const wrapper = mountGrades()
    await flushPromises()

    expect(wrapper.findAll('tbody tr')).toHaveLength(1)
    // 表格不再显示得分列，用提交时间证明留下的是「最近一次」而不是旧的那次
    const cells = wrapper
      .findAll('tbody tr')[0]
      .findAll('td')
      .map((td) => td.text())
    expect(cells[5]).toBe('2026-10-05 10:00')
    expect(wrapper.text()).not.toContain('2026-10-01 10:00')
    wrapper.unmount()
  })

  it('管理员/教师不再看到示例成绩视图：只给一句明确说明，不画示例均分与示例表格', async () => {
    // 原先是硬编码演示视图（示例均分 82.4、示例表格、示例绩点环），
    // batch 24 死示例层清算整段删除；管理员该页签实际走只读视图。
    const wrapper = mountGrades('admin')
    await flushPromises()
    expect(studentApi.getGrades).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('该角色暂无此页签')
    expect(wrapper.text()).not.toContain('课程成绩概览')
    expect(wrapper.text()).not.toContain('82.4')
    wrapper.unmount()
  })
})

describe('学生课程 · 讨论页签', () => {
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

  it('学生作者可管理自己的话题与回复，非作者仍不显示管理动作', async () => {
    const ownTopic = { ...topic, authorId: 2 }
    discussionApi.getTopics.mockResolvedValue({ data: { records: [ownTopic] } })
    discussionApi.getTopicDetail.mockResolvedValue({
      data: { topic: ownTopic, posts: [{ id: 78, content: '我的回复', authorId: 2 }] },
    })

    const wrapper = mountDiscussion('student')
    await flushPromises()
    await findButton(wrapper, '进入话题')?.trigger('click')
    await flushPromises()

    expect(wrapper.find('.thread-actions').exists()).toBe(true)
    expect(findButton(wrapper, '置顶')).toBeTruthy()
    expect(findButton(wrapper, '删除')).toBeTruthy()
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

  it('回复失败保留输入内容并恢复按钮，可直接重试', async () => {
    discussionApi.getTopics.mockResolvedValue({ data: { records: [topic] } })
    discussionApi.getTopicDetail.mockResolvedValue({ data: { topic, posts: [] } })
    discussionApi.replyTopic.mockRejectedValueOnce(new Error('502'))

    const wrapper = mountDiscussion('student')
    await flushPromises()
    await findButton(wrapper, '进入话题')?.trigger('click')
    await flushPromises()

    await wrapper.find('.modal-stub textarea').setValue('需要保留的回复')
    const buttons = wrapper.findAll('.modal-stub button')
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(discussionApi.replyTopic).toHaveBeenCalledWith(1, { content: '需要保留的回复' })
    expect(wrapper.find('.form-error').text()).toContain('回复失败')
    const textarea = wrapper.find('.modal-stub textarea').element as HTMLTextAreaElement
    expect(textarea.value).toBe('需要保留的回复')
    const retry = findButton(wrapper, '发表回复')
    expect(retry).toBeTruthy()
    expect(retry?.attributes('disabled')).toBeUndefined()
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
  it('示例块底座已整体删除：课程页签不再有示例数据来源', () => {
    // 示例层于 batch 24 整体删除；此处改为钉住「不再出现」这个事实，
    // 具体守卫见 noDemoLayer.test.ts（源码级扫描＋阴性对照）。
    const courseTabData = join(__dirname, '../data/courseTabData.ts')
    expect(existsSync(courseTabData), '示例块底座仍在').toBe(false)
  })
})
