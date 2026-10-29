import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'

const studentApi = vi.hoisted(() => ({
  getPendingAssignments: vi.fn(),
  getCompletedAssignments: vi.fn(),
  getAllMyCourses: vi.fn(),
  getGrades: vi.fn(),
}))
const taskApi = vi.hoisted(() => ({ getAllTasks: vi.fn() }))
const authApi = vi.hoisted(() => ({
  updateCurrentUser: vi.fn(),
  changePassword: vi.fn(),
}))
const userApi = vi.hoisted(() => ({ getProfile: vi.fn() }))
const router = vi.hoisted(() => ({ push: vi.fn() }))

const uiState = vi.hoisted(() => ({
  toast: vi.fn(),
  setTheme: vi.fn(),
  theme: null as unknown as { value: 'light' | 'dark' },
  role: null as unknown as { value: string },
  auth: {
    user: {} as Record<string, unknown>,
    userId: 11 as number | null,
  },
}))

vi.mock('@/api/student', () => ({ default: studentApi }))
vi.mock('@/api/task', () => ({ default: taskApi }))
vi.mock('@/api/auth', () => ({ default: authApi }))
vi.mock('@/api/user', () => ({ default: userApi }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: router.push }) }))

// ui 组合式函数持有共享 ref：主题开关与角色必须给真 ref，否则模板不会更新
vi.mock('../composables/ui', async () => {
  const { ref } = await import('vue')
  uiState.theme = ref<'light' | 'dark'>('light')
  uiState.role = ref('student')
  return {
    useV2Ui: () => ({
      toast: uiState.toast,
      setTheme: uiState.setTheme,
      theme: uiState.theme,
      role: uiState.role,
      auth: uiState.auth,
    }),
  }
})

import TasksBody from '../views/specials/TasksBody.vue'
import AcademicBody from '../views/specials/AcademicBody.vue'
import ProfileBody from '../views/specials/ProfileBody.vue'
import { pageDef } from '../nav'
import { specialBodyLoader } from '../views/specials/registry'

const envelope = (data: unknown) => ({ code: 200, message: 'ok', data })

async function settle() {
  await flushPromises()
  await flushPromises()
}

function mountTasks(): VueWrapper {
  return mount(TasksBody)
}
function mountAcademic(): VueWrapper {
  return mount(AcademicBody)
}
function mountProfile(): VueWrapper {
  return mount(ProfileBody)
}

function findButton(wrapper: VueWrapper, label: string) {
  return wrapper.findAll('button').find((node) => node.text().includes(label))
}

const COURSE_A = { id: 4007, name: '计算机网络' }
const COURSE_B = { id: 4008, name: '数据结构' }

const pendingFixtures = [
  { id: 9001, courseId: 4007, title: '实验一报告', dueDate: '2099-01-01T10:00:00' },
  { id: 9002, courseId: 4008, title: '图论习题', dueDate: '2099-02-01T10:00:00' },
]

const completedFixtures = [
  {
    id: 5501,
    taskId: 9003,
    status: 'GRADED',
    score: 88,
    submittedAt: '2026-10-01T10:00:00',
    attemptNumber: 1,
  },
  {
    id: 5502,
    taskId: 9004,
    status: 'SUBMITTED',
    submittedAt: '2026-10-02T11:00:00',
    attemptNumber: 1,
  },
]

const courseTaskFixtures: Record<number, Record<string, unknown>[]> = {
  4007: [
    {
      id: 9003,
      courseId: 4007,
      title: '实验二报告',
      dueDate: '2026-09-20T23:59:00',
      maxScore: 100,
    },
  ],
  4008: [
    { id: 9004, courseId: 4008, title: '期中作业', dueDate: '2026-09-25T23:59:00', maxScore: 50 },
  ],
}

beforeEach(() => {
  vi.clearAllMocks()
  uiState.theme.value = 'light'
  uiState.role.value = 'student'
  uiState.auth.user = {
    id: 11,
    username: 'linzhiyuan',
    fullName: '林知远',
    email: 'lin@example.com',
    phone: '13800138000',
    role: 'STUDENT',
  }
  uiState.auth.userId = 11

  studentApi.getPendingAssignments.mockResolvedValue(envelope([]))
  studentApi.getCompletedAssignments.mockResolvedValue(envelope([]))
  studentApi.getAllMyCourses.mockResolvedValue(envelope({ records: [] }))
  studentApi.getGrades.mockResolvedValue(envelope({ courses: [] }))
  taskApi.getAllTasks.mockResolvedValue([])
  authApi.updateCurrentUser.mockResolvedValue(envelope({}))
  authApi.changePassword.mockResolvedValue(envelope(null))
  userApi.getProfile.mockResolvedValue(
    envelope({
      id: 11,
      username: 'linzhiyuan',
      fullName: '林知远',
      email: 'lin@example.com',
      phone: '13800138000',
      studentId: '2023010101',
      major: '计算机科学与技术',
      grade: '2023',
      department: '计算机学院',
      role: 'STUDENT',
      lastLogin: '2026-10-20T10:00:00',
    }),
  )
})

describe('v2 学生全局页 · 任务', () => {
  it('跨课程聚合待办与已完成，展示课程、任务、截止时间与状态', async () => {
    studentApi.getAllMyCourses.mockResolvedValue(envelope({ records: [COURSE_A, COURSE_B] }))
    studentApi.getPendingAssignments.mockResolvedValue(envelope(pendingFixtures))
    studentApi.getCompletedAssignments.mockResolvedValue(envelope(completedFixtures))
    taskApi.getAllTasks.mockImplementation(({ courseId }: { courseId: number }) =>
      Promise.resolve(courseTaskFixtures[courseId] ?? []),
    )

    const wrapper = mountTasks()
    await settle()

    const text = wrapper.text()
    expect(text).toContain('计算机网络')
    expect(text).toContain('数据结构')
    expect(text).toContain('实验一报告')
    expect(text).toContain('图论习题')
    expect(text).toContain('实验二报告')
    expect(text).toContain('期中作业')
    expect(text).toContain('2099-01-01 10:00')
    expect(text).toContain('待完成')
    expect(text).toContain('已批改')
    expect(text).toContain('已提交待批改')
    expect(text).toContain('88 / 100')
    expect(text).not.toContain('示例数据')
    expect(wrapper.find('.callout.danger').exists()).toBe(false)
    // 已完成作业的标题/课程靠课程任务列表补齐（与 Task 7 的任务 + 提交聚合同口径）
    expect(taskApi.getAllTasks).toHaveBeenCalledWith({ courseId: 4007 })
    expect(taskApi.getAllTasks).toHaveBeenCalledWith({ courseId: 4008 })
    wrapper.unmount()
  })

  it('点击任务行跳到对应课程任务页；没有 courseId 的行不跳转', async () => {
    studentApi.getAllMyCourses.mockResolvedValue(envelope([COURSE_A]))
    studentApi.getPendingAssignments.mockResolvedValue(envelope([pendingFixtures[0]]))
    // taskId 9999 在课程任务里不存在 → 行无 courseId，按「不跳转」处理
    studentApi.getCompletedAssignments.mockResolvedValue(
      envelope([{ id: 5599, taskId: 9999, status: 'SUBMITTED' }]),
    )
    taskApi.getAllTasks.mockResolvedValue(courseTaskFixtures[4007])

    const wrapper = mountTasks()
    await settle()

    const navigable = wrapper.findAll('tr').find((node) => node.text().includes('实验一报告'))
    expect(navigable?.classes()).toContain('row-link')
    await navigable?.trigger('click')
    expect(router.push).toHaveBeenCalledWith('/v2/student/courses/4007/tasks')

    router.push.mockClear()
    const orphan = wrapper.findAll('tr').find((node) => node.text().includes('任务 #9999'))
    expect(orphan, '未补齐任务信息的已完成行仍应可见').toBeTruthy()
    expect(orphan?.classes()).not.toContain('row-link')
    await orphan?.trigger('click')
    expect(router.push).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('空态与失败态分离：空列表只落空态，接口失败落错误态并可重试', async () => {
    const empty = mountTasks()
    await settle()
    expect(empty.text()).toContain('暂无待办任务')
    expect(empty.text()).toContain('暂无已完成作业')
    expect(empty.find('.callout.danger').exists()).toBe(false)
    empty.unmount()

    studentApi.getPendingAssignments.mockRejectedValueOnce(new Error('500'))
    studentApi.getCompletedAssignments.mockRejectedValueOnce(new Error('500'))
    const failed = mountTasks()
    await settle()
    const callouts = failed.findAll('.callout.danger')
    expect(callouts).toHaveLength(2)
    expect(callouts[0].text()).toContain('待办任务加载失败')
    expect(callouts[1].text()).toContain('已完成作业加载失败')
    expect(failed.text()).not.toContain('暂无待办任务')
    expect(failed.text()).not.toContain('暂无已完成作业')
    expect(failed.text()).not.toContain('示例数据')

    studentApi.getPendingAssignments.mockResolvedValueOnce(envelope([pendingFixtures[0]]))
    studentApi.getCompletedAssignments.mockResolvedValueOnce(envelope([]))
    await failed.findAll('.callout.danger button')[0].trigger('click')
    await settle()
    expect(failed.text()).toContain('实验一报告')
    expect(failed.find('.callout.danger').exists()).toBe(false)
    failed.unmount()
  })

  it('单边失败不拖垮另一侧：待办失败时已完成仍渲染', async () => {
    studentApi.getAllMyCourses.mockResolvedValue(envelope([COURSE_A]))
    studentApi.getPendingAssignments.mockRejectedValueOnce(new Error('500'))
    studentApi.getCompletedAssignments.mockResolvedValue(envelope([completedFixtures[0]]))
    taskApi.getAllTasks.mockResolvedValue(courseTaskFixtures[4007])

    const wrapper = mountTasks()
    await settle()

    expect(wrapper.find('.callout.danger').text()).toContain('待办任务加载失败')
    expect(wrapper.text()).toContain('实验二报告')
    expect(wrapper.text()).not.toContain('暂无已完成作业')
    wrapper.unmount()
  })
})

describe('v2 学生全局页 · 学业档案（仅成绩）', () => {
  const gradesPayload = envelope({
    courses: [
      {
        courseId: 4007,
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
      {
        courseId: 4008,
        courseName: '数据结构',
        finalScore: null,
        assignments: [],
        exams: [
          {
            sessionId: 45,
            title: '单元测验',
            score: 90,
            totalScore: 100,
            status: 'SUBMITTED',
          },
        ],
      },
    ],
  })

  it('按课程渲染成绩并支持切换课程；不提供导出，也不出现错题本/学习路径', async () => {
    studentApi.getGrades.mockResolvedValue(gradesPayload)

    const wrapper = mountAcademic()
    await settle()

    expect(studentApi.getGrades).toHaveBeenCalledTimes(1)
    const text = wrapper.text()
    expect(text).toContain('计算机网络')
    expect(text).toContain('86.5')
    expect(text).toContain('实验一')
    expect(text).toContain('88')
    expect(text).toContain('期中测验')
    expect(text).toContain('76%')
    expect(text).toContain('已批改')
    expect(text).toContain('待出分')
    expect(text).not.toContain('错题本')
    expect(text).not.toContain('学习路径')
    expect(text).not.toContain('示例数据')
    expect(findButton(wrapper, '导出')).toBeUndefined()
    expect(findButton(wrapper, '导出成绩')).toBeUndefined()

    const dataStructureChip = wrapper
      .findAll('.course-chip')
      .find((node) => node.text().includes('数据结构'))
    expect(dataStructureChip).toBeTruthy()
    await dataStructureChip?.trigger('click')
    expect(wrapper.text()).toContain('单元测验')
    expect(wrapper.text()).toContain('90%')
    expect(wrapper.text()).toContain('已提交')
    expect(wrapper.text()).not.toContain('实验一')
    wrapper.unmount()
  })

  it('后端只给扁平行时退化为扁表，不虚构课程选择器', async () => {
    studentApi.getGrades.mockResolvedValue(
      envelope([
        {
          courseName: '操作系统',
          title: '进程调度作业',
          score: 80,
          maxScore: 100,
          status: 'GRADED',
          submittedAt: '2026-10-03T08:30:00',
        },
      ]),
    )

    const wrapper = mountAcademic()
    await settle()

    expect(wrapper.find('.course-picker').exists()).toBe(false)
    expect(wrapper.text()).toContain('操作系统')
    expect(wrapper.text()).toContain('进程调度作业')
    expect(wrapper.text()).toContain('80')
    expect(wrapper.text()).toContain('已批改')
    wrapper.unmount()
  })

  it('空态与失败态分离，失败可重试', async () => {
    const empty = mountAcademic()
    await settle()
    expect(empty.text()).toContain('暂无成绩记录')
    expect(empty.find('.callout.danger').exists()).toBe(false)
    empty.unmount()

    studentApi.getGrades.mockRejectedValueOnce(new Error('500'))
    const failed = mountAcademic()
    await settle()
    expect(failed.find('.callout.danger').text()).toContain('成绩数据加载失败')
    expect(failed.text()).not.toContain('暂无成绩记录')

    studentApi.getGrades.mockResolvedValueOnce(gradesPayload)
    await failed.find('.callout.danger button').trigger('click')
    await settle()
    expect(failed.find('.callout.danger').exists()).toBe(false)
    expect(failed.text()).toContain('计算机网络')
    failed.unmount()
  })
})

describe('v2 学生全局页 · 个人中心', () => {
  it('只渲染基本资料 / 安全 / 偏好三面，字段来自资料接口', async () => {
    const wrapper = mountProfile()
    await settle()

    expect(userApi.getProfile).toHaveBeenCalledWith(11)
    expect(wrapper.findAll('.profile-card')).toHaveLength(3)
    const text = wrapper.text()
    expect(text).toContain('基本资料')
    expect(text).toContain('安全')
    expect(text).toContain('偏好')
    expect(text).toContain('2023010101')
    expect(text).toContain('计算机科学与技术')
    expect(text).toContain('2023')
    expect(text).toContain('林知远')
    expect(text).toContain('2026-10-20 10:00')
    expect(text).not.toContain('活动')
    expect(text).not.toContain('课程')
    expect(text).not.toContain('卡片')
    expect(text).not.toContain('示例数据')

    const fullName = wrapper.find('[data-field="fullName"]').element as HTMLInputElement
    const email = wrapper.find('[data-field="email"]').element as HTMLInputElement
    const phone = wrapper.find('[data-field="phone"]').element as HTMLInputElement
    expect(fullName.value).toBe('林知远')
    expect(email.value).toBe('lin@example.com')
    expect(phone.value).toBe('13800138000')
    wrapper.unmount()
  })

  it('保存资料走 PUT /auth/me 白名单字段', async () => {
    const wrapper = mountProfile()
    await settle()

    await wrapper.find('[data-field="email"]').setValue('new@example.com')
    await findButton(wrapper, '保存修改')?.trigger('click')
    await settle()

    expect(authApi.updateCurrentUser).toHaveBeenCalledTimes(1)
    expect(authApi.updateCurrentUser).toHaveBeenCalledWith({
      fullName: '林知远',
      email: 'new@example.com',
      phone: '13800138000',
    })
    expect(uiState.toast).toHaveBeenCalledWith('资料已更新', 'ok')
    wrapper.unmount()
  })

  it('保存失败显式报错，不静默吞掉', async () => {
    authApi.updateCurrentUser.mockRejectedValueOnce(new Error('500'))
    const wrapper = mountProfile()
    await settle()

    await wrapper.find('[data-field="fullName"]').setValue('林知远')
    await findButton(wrapper, '保存修改')?.trigger('click')
    await settle()

    expect(wrapper.find('.form-error').text()).toContain('保存失败')
    expect(uiState.toast).not.toHaveBeenCalledWith('资料已更新', 'ok')
    wrapper.unmount()
  })

  it('改密先做本地校验，再按 ChangePasswordDTO 三字段提交', async () => {
    const wrapper = mountProfile()
    await settle()

    await wrapper.find('[data-field="oldPassword"]').setValue('oldpass1')
    await wrapper.find('[data-field="newPassword"]').setValue('Abc12345!')
    await wrapper.find('[data-field="confirmPassword"]').setValue('Abc12345?')
    await findButton(wrapper, '修改密码')?.trigger('click')
    await settle()

    expect(authApi.changePassword).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('两次输入的新密码不一致')

    await wrapper.find('[data-field="confirmPassword"]').setValue('Abc12345!')
    await findButton(wrapper, '修改密码')?.trigger('click')
    await settle()

    expect(authApi.changePassword).toHaveBeenCalledTimes(1)
    expect(authApi.changePassword).toHaveBeenCalledWith({
      oldPassword: 'oldpass1',
      newPassword: 'Abc12345!',
      confirmPassword: 'Abc12345!',
    })
    expect(uiState.toast).toHaveBeenCalledWith('密码已修改，请重新登录', 'ok')
    wrapper.unmount()
  })

  it('偏好主题跟随 v2 现有开关', async () => {
    const wrapper = mountProfile()
    await settle()

    await findButton(wrapper, '深色')?.trigger('click')
    expect(uiState.setTheme).toHaveBeenCalledWith('dark')
    await findButton(wrapper, '浅色')?.trigger('click')
    expect(uiState.setTheme).toHaveBeenCalledWith('light')
    wrapper.unmount()
  })

  it('可选资料为空时以占位符呈现，不虚构字段', async () => {
    userApi.getProfile.mockResolvedValueOnce(
      envelope({ id: 11, username: 'linzhiyuan', role: 'STUDENT' }),
    )
    const wrapper = mountProfile()
    await settle()

    expect(wrapper.text()).toContain('用户名')
    expect(wrapper.text()).toContain('学号')
    expect(wrapper.text()).toContain('—')
    expect(wrapper.find('[data-field="fullName"]').element).toHaveProperty('value', '')
    expect(wrapper.findAll('.profile-card')).toHaveLength(3)
    expect(wrapper.text()).not.toContain('示例数据')
    wrapper.unmount()
  })

  it('资料接口失败时保留登录缓存并显式提示，不回落示例数据', async () => {
    userApi.getProfile.mockRejectedValueOnce(new Error('500'))
    const wrapper = mountProfile()
    await settle()

    expect(wrapper.text()).toContain('详细资料未能加载')
    expect(wrapper.text()).toContain('林知远')
    const email = wrapper.find('[data-field="email"]').element as HTMLInputElement
    const phone = wrapper.find('[data-field="phone"]').element as HTMLInputElement
    expect(email.value).toBe('lin@example.com')
    expect(phone.value).toBe('13800138000')
    expect(wrapper.findAll('.profile-card')).toHaveLength(3)
    expect(wrapper.text()).not.toContain('示例数据')
    wrapper.unmount()
  })
})

describe('v2 学生全局页 · 注册表接管', () => {
  it('三页的 special 都由 Task 6 的约定注册表加载 Body', () => {
    expect(pageDef('student', 'tasks').special).toBe('tasks')
    expect(pageDef('student', 'academic').special).toBe('academic')
    expect(pageDef('student', 'profile').special).toBe('profile')
    for (const special of ['tasks', 'academic', 'profile']) {
      expect(typeof specialBodyLoader(special), `${special} 没有被注册表加载`).toBe('function')
    }
  })
})
