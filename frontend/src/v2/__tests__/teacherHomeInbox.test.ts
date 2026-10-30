import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

const mocks = vi.hoisted(() => ({
  loadStudentDashboard: vi.fn(),
  loadAdminOverview: vi.fn(),
  loadNotifications: vi.fn(),
  getTeacherDashboardV2: vi.fn(),
  getPendingGrading: vi.fn(),
  getAllExamPapersByCourse: vi.fn(),
  getAllCourses: vi.fn(),
  getMyCourseSummaries: vi.fn(),
  getAssignments: vi.fn(),
  getActiveExams: vi.fn(),
  getTeacherStudents: vi.fn(),
  toast: vi.fn(),
  push: vi.fn(),
}))

vi.mock('../composables/ui', async () => {
  const { ref } = await import('vue')
  const role = ref('teacher')
  const displayName = ref('王老师')
  return {
    useV2Ui: () => ({ role, displayName, toast: mocks.toast }),
    __setRole: (next: 'student' | 'teacher' | 'admin') => {
      role.value = next
    },
  }
})

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
}))

vi.mock('../data/api', () => ({
  loadStudentDashboard: mocks.loadStudentDashboard,
  loadAdminOverview: mocks.loadAdminOverview,
  loadNotifications: mocks.loadNotifications,
}))

vi.mock('@/api/teacher', () => ({
  default: {
    getTeacherDashboardV2: mocks.getTeacherDashboardV2,
    getMyCourseSummaries: mocks.getMyCourseSummaries,
    getAssignments: mocks.getAssignments,
  },
}))

vi.mock('@/api/task', () => ({
  default: { getPendingGrading: mocks.getPendingGrading },
}))

vi.mock('@/api/exam', () => ({
  default: {
    getAllExamPapersByCourse: mocks.getAllExamPapersByCourse,
    getActiveExams: mocks.getActiveExams,
  },
}))

vi.mock('@/api/course', () => ({
  default: { getAllCourses: mocks.getAllCourses },
}))

vi.mock('@/api/user', () => ({
  default: { getTeacherStudents: mocks.getTeacherStudents },
}))

import V2HomeView from '../views/V2HomeView.vue'
import InboxBody from '../views/specials/InboxBody.vue'

const envelope = (data: unknown) => ({ code: 200, message: 'ok', data })
const paged = (records: Record<string, unknown>[]) =>
  envelope({ records, total: records.length, page: 1, size: 100 })

async function setRole(role: 'student' | 'teacher' | 'admin') {
  const mod = (await import('../composables/ui')) as unknown as {
    __setRole: (next: 'student' | 'teacher' | 'admin') => void
  }
  mod.__setRole(role)
}

function statValues(wrapper: VueWrapper): Record<string, string> {
  const values: Record<string, string> = {}
  wrapper.findAll('.stat').forEach((stat) => {
    values[stat.find('.stat-label').text()] = stat.find('.stat-value').text().replace(/\s+/g, '')
  })
  return values
}

const teacherDashboard = {
  courseCount: 2,
  studentCount: 48,
  pendingGrading: 6,
  avgScore: 82.5,
  courseCards: [
    {
      courseId: 1,
      name: '计算机网络',
      code: 'CS-NET',
      studentCount: 30,
      avgProgress: 72,
      status: 'ACTIVE',
    },
    {
      courseId: 2,
      name: '数据结构',
      code: 'CS-DS',
      studentCount: 18,
      avgProgress: 55,
      status: 'ACTIVE',
    },
  ],
  atRiskStudents: [
    {
      studentId: 9,
      studentName: '张三',
      courseName: '数据结构',
      progress: 32,
      finalScore: 55,
      level: 'HIGH',
    },
  ],
  aiOverview: { summary: '本周 2 门课程有 6 份待批提交。' },
}

const pendingGrading = {
  id: 11,
  title: '网络层作业',
  taskType: 'ASSIGNMENT',
  courseId: 1,
  dueDate: '2026-11-01T23:59:00',
  isPublished: true,
}

const draftTask = {
  id: 21,
  title: '传输层作业',
  taskType: 'ASSIGNMENT',
  courseId: 1,
  isPublished: false,
  updatedAt: '2026-10-28T10:00:00',
}

const publishedTask = {
  id: 22,
  title: '已发布作业',
  taskType: 'ASSIGNMENT',
  courseId: 1,
  isPublished: true,
  updatedAt: '2026-10-01T10:00:00',
}

const draftPaper = {
  id: 31,
  title: '期中测验',
  courseId: 1,
  status: 'DRAFT',
  updatedAt: '2026-10-27T10:00:00',
}

const publishedPaper = {
  id: 32,
  title: '期末考试',
  courseId: 1,
  status: 'PUBLISHED',
  startTime: '2026-10-29T09:00:00',
  endTime: '2026-12-01T10:00:00',
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.loadStudentDashboard.mockResolvedValue(null)
  mocks.loadAdminOverview.mockResolvedValue({ overview: {}, dashboard: {} })
  mocks.loadNotifications.mockResolvedValue([])
  mocks.getTeacherDashboardV2.mockResolvedValue(envelope(teacherDashboard))
  mocks.getPendingGrading.mockResolvedValue(envelope([]))
  mocks.getAllExamPapersByCourse.mockResolvedValue([])
  mocks.getAllCourses.mockResolvedValue([])
  mocks.getMyCourseSummaries.mockResolvedValue(envelope([]))
  mocks.getAssignments.mockResolvedValue(paged([]))
  mocks.getActiveExams.mockResolvedValue(envelope([]))
  mocks.getTeacherStudents.mockResolvedValue(envelope([]))
})

describe('v2 首页按角色取真实读数', () => {
  it('教师：课程 / 待批阅 / 近期考试均来自接口，课程与队列无原型样本', async () => {
    await setRole('teacher')
    mocks.getPendingGrading.mockResolvedValue(envelope([pendingGrading]))
    mocks.getAllExamPapersByCourse.mockImplementation((courseId: number) =>
      Promise.resolve(courseId === 1 ? [publishedPaper, draftPaper] : []),
    )

    const wrapper = mount(V2HomeView)
    await flushPromises()

    const values = statValues(wrapper)
    expect(values['我的课程']).toBe('2门')
    expect(values['待批阅任务']).toBe('1项')
    expect(values['近期考试']).toBe('1场')
    expect(values['学生']).toBe('48人')

    expect(wrapper.find('.card-courses').text()).toContain('计算机网络')
    expect(wrapper.find('.card-courses').text()).toContain('数据结构')
    expect(wrapper.find('.card-queue').text()).toContain('网络层作业')
    expect(wrapper.find('.card-queue').text()).toContain('待批阅')
    expect(wrapper.find('.page-head').text()).toContain('接口数据')
    expect(wrapper.text()).not.toContain('示例数据')
    expect(wrapper.text()).not.toContain('慢启动')
  })

  it('学生：进度 / 待办 / 未读来自 /student/dashboard', async () => {
    await setRole('student')
    mocks.loadStudentDashboard.mockResolvedValue({
      courses: [
        { courseId: 1, courseName: '计算机网络', progress: 80 },
        { courseId: 2, courseName: '数据结构', progress: 60 },
      ],
      todos: {
        assignments: [
          { taskId: 21, title: '子网划分作业', courseId: 1, dueDate: '2026-11-02T23:59:00' },
        ],
        exams: [
          { examPaperId: 22, title: '期中测验', courseId: 1, startTime: '2026-11-05T09:00:00' },
        ],
        signIns: [],
      },
      unreadCount: 3,
      summary: { courseCount: 2, unreadCount: 3 },
    })

    const wrapper = mount(V2HomeView)
    await flushPromises()

    const values = statValues(wrapper)
    expect(values['已选课程']).toBe('2门')
    expect(values['学习进度']).toBe('70%')
    expect(values['待办事项']).toBe('2项')
    expect(values['未读通知']).toBe('3条')

    expect(wrapper.find('.card-courses').text()).toContain('计算机网络')
    expect(wrapper.find('.card-queue').text()).toContain('子网划分作业')
    expect(wrapper.find('.card-queue').text()).toContain('期中测验')
    expect(wrapper.text()).not.toContain('示例数据')
  })

  it('管理员：总览 / 待审批取 /admin/statistics/overview 与 /courses', async () => {
    await setRole('admin')
    mocks.loadAdminOverview.mockResolvedValue({
      overview: {
        totalUsers: 1200,
        totalCourses: 42,
        totalEnrollments: 800,
        activeCourses: 38,
      },
      dashboard: { totalUsers: 1200, totalCourses: 42, activeUsers: 900 },
    })
    mocks.getAllCourses.mockResolvedValue([
      {
        id: 1,
        name: '计算机网络',
        code: 'CS-NET',
        semester: '2026 秋',
        status: 'ACTIVE',
        approvalStatus: 'APPROVED',
      },
      {
        id: 2,
        name: '数据结构',
        code: 'CS-DS',
        semester: '2026 秋',
        status: 'DRAFT',
        approvalStatus: 'PENDING',
      },
    ])

    const wrapper = mount(V2HomeView)
    await flushPromises()

    const values = statValues(wrapper)
    expect(values['平台用户']).toBe('1200人')
    expect(values['课程总数']).toBe('42门')
    expect(values['待审批课程']).toBe('1门')
    expect(values['启用用户']).toBe('900人')

    expect(wrapper.find('.card-courses').text()).toContain('计算机网络')
    expect(wrapper.find('.card-courses').text()).toContain('待审批')
    expect(wrapper.find('.card-queue').text()).toContain('数据结构')
    expect(wrapper.find('.card-chart').text()).toContain('审批状态分布')
    expect(wrapper.text()).not.toContain('示例数据')
  })

  it('读数失败显示 -- / 暂不可用，不回落原型课程与示例队列', async () => {
    await setRole('teacher')
    mocks.getTeacherDashboardV2.mockRejectedValue(new Error('500'))
    mocks.getPendingGrading.mockRejectedValue(new Error('500'))
    mocks.loadNotifications.mockRejectedValue(new Error('500'))

    const wrapper = mount(V2HomeView)
    await flushPromises()

    const values = statValues(wrapper)
    expect(values['我的课程']).toBe('--')
    expect(values['待批阅任务']).toBe('--')
    expect(values['近期考试']).toBe('--')
    expect(values['学生']).toBe('--')
    expect(wrapper.findAll('.stat-note').map((note) => note.text())).toEqual([
      '暂不可用',
      '暂不可用',
      '暂不可用',
      '暂不可用',
    ])

    expect(wrapper.text()).toContain('课程数据加载失败，请重试')
    expect(wrapper.text()).toContain('待批阅加载失败，请重试')
    expect(wrapper.text()).toContain('通知加载失败，请重试')
    expect(wrapper.find('.course-list').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('示例数据')
    expect(wrapper.text()).not.toContain('计算机网络')
  })
})

describe('v2 教师收件箱三块真实读数', () => {
  it('成功：待批阅 / 待发布 / 监考分别渲染，只有本人课程的会话进入监考', async () => {
    mocks.getMyCourseSummaries.mockResolvedValue(envelope([{ courseId: 1, name: '计算机网络' }]))
    mocks.getPendingGrading.mockResolvedValue(envelope([pendingGrading]))
    mocks.getAssignments.mockResolvedValue(paged([draftTask, publishedTask]))
    mocks.getAllExamPapersByCourse.mockResolvedValue([draftPaper, publishedPaper])
    mocks.getActiveExams.mockResolvedValue(
      envelope([
        {
          id: 41,
          examPaperId: 32,
          studentId: 7,
          status: 'STARTED',
          startTime: '2026-10-29T09:00:00',
          timeSpent: 300,
        },
        {
          id: 42,
          examPaperId: 999,
          studentId: 8,
          status: 'STARTED',
          startTime: '2026-10-29T09:30:00',
          timeSpent: 90,
        },
      ]),
    )
    mocks.getTeacherStudents.mockResolvedValue(
      envelope([{ id: 7, fullName: '李四', username: 'lisi' }]),
    )

    const wrapper = mount(InboxBody)
    await flushPromises()

    const gradingBlock = wrapper.find('[data-block="grading"]')
    expect(gradingBlock.findAll('tbody tr')).toHaveLength(1)
    expect(gradingBlock.text()).toContain('网络层作业')
    expect(gradingBlock.text()).toContain('计算机网络')
    expect(gradingBlock.text()).toContain('待批阅')

    const publishBlock = wrapper.find('[data-block="publish"]')
    expect(publishBlock.findAll('tbody tr')).toHaveLength(2)
    expect(publishBlock.text()).toContain('传输层作业')
    expect(publishBlock.text()).toContain('期中测验')
    expect(publishBlock.text()).not.toContain('已发布作业')
    expect(publishBlock.text()).not.toContain('期末考试')

    const proctorBlock = wrapper.find('[data-block="proctor"]')
    expect(proctorBlock.findAll('tbody tr')).toHaveLength(1)
    expect(proctorBlock.text()).toContain('期末考试')
    expect(proctorBlock.text()).toContain('李四')
    expect(proctorBlock.text()).toContain('5 分')
    expect(proctorBlock.text()).not.toContain('学生 #8')
    expect(wrapper.find('.callout.danger').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('示例数据')

    await gradingBlock.find('.text-btn').trigger('click')
    expect(mocks.push).toHaveBeenCalledWith('/teacher/courses/1/grading')

    await publishBlock.find('.text-btn').trigger('click')
    expect(mocks.push).toHaveBeenCalledWith('/teacher/courses/1/tasks')

    await proctorBlock.find('.text-btn').trigger('click')
    expect(mocks.push).toHaveBeenCalledWith('/teacher/courses/1/proctor')
  })

  it('空：三块各自显示空态，不显示失败提示', async () => {
    mocks.getMyCourseSummaries.mockResolvedValue(envelope([{ courseId: 1, name: '计算机网络' }]))

    const wrapper = mount(InboxBody)
    await flushPromises()

    expect(wrapper.find('[data-block="grading"]').text()).toContain('暂无待批阅任务')
    expect(wrapper.find('[data-block="publish"]').text()).toContain('暂无待发布的作业或试卷')
    expect(wrapper.find('[data-block="proctor"]').text()).toContain('当前没有进行中的考试')
    expect(wrapper.find('.callout.danger').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('示例数据')
  })

  it('失败：课程与待批阅接口失败时三块显示失败态，空态文案不出现', async () => {
    mocks.getMyCourseSummaries.mockRejectedValue(new Error('500'))
    mocks.getPendingGrading.mockRejectedValue(new Error('500'))

    const wrapper = mount(InboxBody)
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('待批阅加载失败，请重试。')
    expect(text).toContain('课程列表加载失败，无法确定待发布范围。')
    expect(text).toContain('课程列表加载失败，无法按课程过滤进行中会话。')
    expect(wrapper.findAll('.callout.danger')).toHaveLength(3)
    expect(text).not.toContain('暂无待批阅任务')
    expect(text).not.toContain('暂无待发布的作业或试卷')
    expect(text).not.toContain('当前没有进行中的考试')
    expect(text).not.toContain('示例数据')
  })

  it('失败隔离：课程可用但作业 / 试卷接口失败时，监考与待发布各自报错', async () => {
    mocks.getMyCourseSummaries.mockResolvedValue(envelope([{ courseId: 1, name: '计算机网络' }]))
    mocks.getPendingGrading.mockRejectedValue(new Error('500'))
    mocks.getAssignments.mockRejectedValue(new Error('500'))
    mocks.getAllExamPapersByCourse.mockRejectedValue(new Error('500'))
    mocks.getActiveExams.mockResolvedValue(
      envelope([{ id: 41, examPaperId: 32, studentId: 7, status: 'STARTED' }]),
    )

    const wrapper = mount(InboxBody)
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('待批阅加载失败，请重试。')
    expect(text).toContain('待发布内容未能完整加载，请重试。')
    expect(text).toContain('试卷信息未能加载，无法按课程过滤进行中会话。')
    expect(text).not.toContain('暂无待批阅任务')
    expect(text).not.toContain('暂无待发布的作业或试卷')
    expect(text).not.toContain('当前没有进行中的考试')
    expect(text).not.toContain('示例数据')
  })

  it('收件箱不改写祖先 DOM，也不留隐藏的僵尸页签', async () => {
    const Host = defineComponent({
      render() {
        return h('section', { class: 'module' }, [
          h(
            'div',
            { class: 'page-tabs' },
            ['A', 'B', 'C', 'D'].map((label) => h('button', label)),
          ),
          h(InboxBody),
        ])
      },
    })

    const wrapper = mount(Host)
    await flushPromises()

    const buttons = wrapper.findAll('.page-tabs button')
    expect(buttons).toHaveLength(4)
    // 正文只渲染自己的三块，页签的取舍归壳层
    expect(wrapper.findAll('[data-block]')).toHaveLength(3)
    for (const button of buttons) {
      expect(button.attributes('style') ?? '').not.toContain('display: none')
      expect(button.attributes('data-inbox-hidden')).toBeUndefined()
    }
    wrapper.unmount()
  })
})
