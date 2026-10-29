import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'

const studentApi = vi.hoisted(() => ({
  getPendingAssignments: vi.fn(),
  getCompletedAssignments: vi.fn(),
  getAllMyCourses: vi.fn(),
  getGrades: vi.fn(),
}))
const taskApi = vi.hoisted(() => ({ getTaskById: vi.fn() }))
const authApi = vi.hoisted(() => ({
  updateCurrentUser: vi.fn(),
  changePassword: vi.fn(),
}))
const userApi = vi.hoisted(() => ({
  getProfile: vi.fn(),
  updateUserStatus: vi.fn(),
}))
const adminApi = vi.hoisted(() => ({
  getUsers: vi.fn(),
  getSemesters: vi.fn(),
  createSemester: vi.fn(),
  archiveSemester: vi.fn(),
}))
const permissionApi = vi.hoisted(() => ({
  getAllPermissions: vi.fn(),
  createPermission: vi.fn(),
}))
const classroomApi = vi.hoisted(() => ({
  getClassroomList: vi.fn(),
  createClassroom: vi.fn(),
  updateClassroom: vi.fn(),
  deleteClassroom: vi.fn(),
}))
const courseApi = vi.hoisted(() => ({
  getAllCourses: vi.fn(),
  approveCourse: vi.fn(),
  rejectCourse: vi.fn(),
}))

const route = vi.hoisted(() => ({
  meta: { v2page: 'tasks' } as Record<string, unknown>,
  query: {} as Record<string, string>,
}))
const router = vi.hoisted(() => ({ push: vi.fn(), replace: vi.fn() }))

const uiState = vi.hoisted(() => ({
  role: null as unknown as { value: string },
  theme: null as unknown as { value: 'light' | 'dark' },
  toast: vi.fn(),
  setTheme: vi.fn(),
  auth: {
    user: {} as Record<string, unknown>,
    userId: 11 as number | null,
    resetAuthState: vi.fn(),
  },
}))

vi.mock('@/api/student', () => ({ default: studentApi }))
vi.mock('@/api/task', () => ({ default: taskApi }))
vi.mock('@/api/auth', () => ({ default: authApi }))
vi.mock('@/api/user', () => ({ default: userApi, ...userApi }))
vi.mock('@/api/admin', () => adminApi)
vi.mock('@/api/permission', () => permissionApi)
vi.mock('@/api/classroom', () => classroomApi)
vi.mock('@/api/course', () => courseApi)
vi.mock('vue-router', () => ({ useRoute: () => route, useRouter: () => router }))

vi.mock('../composables/ui', async () => {
  const { ref } = await import('vue')
  uiState.role = ref('student')
  uiState.theme = ref<'light' | 'dark'>('light')
  return {
    useV2Ui: () => ({
      role: uiState.role,
      theme: uiState.theme,
      setTheme: uiState.setTheme,
      toast: uiState.toast,
      auth: uiState.auth,
    }),
  }
})

import V2GlobalPage from '../views/V2GlobalPage.vue'
import { pageDef } from '../nav'

const envelope = (data: unknown) => ({ code: 200, message: 'ok', data })

async function settle() {
  await flushPromises()
  await flushPromises()
}

function mountPage(): VueWrapper {
  return mount(V2GlobalPage, {
    global: { stubs: { RouterLink: true } },
  })
}

function sourceChipText(wrapper: VueWrapper): string {
  return wrapper.find('.page-kicker .chip').text()
}

beforeEach(() => {
  vi.clearAllMocks()
  route.meta.v2page = 'tasks'
  route.query = {}
  uiState.role.value = 'student'
  uiState.theme.value = 'light'
  uiState.auth.user = {
    id: 11,
    username: 'linzhiyuan',
    fullName: '林知远',
    role: 'STUDENT',
  }

  studentApi.getPendingAssignments.mockResolvedValue(envelope([]))
  studentApi.getCompletedAssignments.mockResolvedValue(envelope([]))
  studentApi.getAllMyCourses.mockResolvedValue(envelope({ records: [] }))
  studentApi.getGrades.mockResolvedValue(envelope({ courses: [] }))
  taskApi.getTaskById.mockResolvedValue(envelope(null))
  authApi.updateCurrentUser.mockResolvedValue(envelope({}))
  authApi.changePassword.mockResolvedValue(envelope(null))
  userApi.getProfile.mockResolvedValue(
    envelope({ id: 11, username: 'linzhiyuan', fullName: '林知远', role: 'STUDENT' }),
  )
  adminApi.getUsers.mockResolvedValue(envelope({ records: [], total: 0 }))
  adminApi.getSemesters.mockResolvedValue(envelope({ records: [], total: 0 }))
  permissionApi.getAllPermissions.mockResolvedValue(envelope([]))
  classroomApi.getClassroomList.mockResolvedValue(envelope({ records: [], total: 0 }))
  courseApi.getAllCourses.mockResolvedValue(envelope({ records: [], total: 0 }))
})

describe('v2 全局页壳层：真实页面不得再戴「示例数据」章', () => {
  it('学生任务 / 学业档案 / 个人中心都标「接口数据」', async () => {
    const bodies: Record<string, string> = {
      tasks: '.tasks-body',
      academic: '.academic-body',
      profile: '.profile-body',
    }
    for (const page of ['tasks', 'academic', 'profile']) {
      route.meta.v2page = page
      const wrapper = mountPage()
      await settle()
      await vi.waitFor(() => {
        expect(wrapper.find(bodies[page]).exists(), `student/${page} 真实正文未渲染`).toBe(true)
      })
      expect(sourceChipText(wrapper), `student/${page} 仍标示例`).toBe('接口数据')
      expect(wrapper.text(), `student/${page} 出现示例数据`).not.toContain('示例数据')
      wrapper.unmount()
    }
  })

  it('管理端 users / roles / semesters / adminClasses / approvals 都标「接口数据」', async () => {
    uiState.role.value = 'admin'
    for (const page of ['users', 'roles', 'semesters', 'adminClasses', 'approvals']) {
      route.meta.v2page = page
      expect(pageDef('admin', page).special, `admin/${page} 未声明 special`).toBeTruthy()
      const wrapper = mountPage()
      await settle()
      if (page === 'users') {
        await vi.waitFor(() => {
          expect(wrapper.find('.users-body').exists(), 'admin/users 真实正文未渲染').toBe(true)
        })
      }
      expect(sourceChipText(wrapper), `admin/${page} 仍标示例`).toBe('接口数据')
      expect(wrapper.text(), `admin/${page} 出现示例数据`).not.toContain('示例数据')
      wrapper.unmount()
    }
  })

  it('没有真实 Body 的页面仍如实标「示例数据」（阴性对照）', async () => {
    uiState.role.value = 'teacher'
    route.meta.v2page = 'resources'
    const wrapper = mountPage()
    await settle()
    expect(sourceChipText(wrapper)).toBe('示例数据')
    wrapper.unmount()
  })

  it('学业档案只留一个「成绩」页签，且不提供导出成绩动作', async () => {
    route.meta.v2page = 'academic'
    const wrapper = mountPage()
    await settle()
    await vi.waitFor(() => {
      expect(wrapper.find('.academic-body').exists(), '学业档案正文未渲染').toBe(true)
    })

    const tabs = wrapper.findAll('.page-tabs button')
    expect(tabs).toHaveLength(1)
    expect(tabs[0].text()).toBe('成绩')
    const bodyText = wrapper.find('.academic-body').text()
    expect(bodyText).not.toContain('错题本')
    expect(bodyText).not.toContain('学习路径')
    const exportAction = wrapper
      .findAll('.page-head-actions button')
      .find((node) => node.text().includes('导出成绩'))
    expect(exportAction, '学业档案不应再有导出成绩动作').toBeUndefined()
    wrapper.unmount()
  })
})

const openApi = vi.hoisted(() => {
  const stub = () =>
    new Proxy({} as Record<string, unknown>, {
      get: () => async () => ({ code: 200, message: 'ok', data: { list: [], records: [] } }),
    })
  return { teacher: stub(), exam: stub() }
})
vi.mock('@/api/teacher', () => ({ default: openApi.teacher }))
vi.mock('@/api/exam', () => ({ default: openApi.exam }))

describe('v2 全局页壳层：收件箱不留空控件', () => {
  beforeEach(() => {
    uiState.role.value = 'teacher'
    route.meta.v2page = 'inbox'
    const extras = taskApi as unknown as Record<string, unknown>
    extras.getPendingGrading = vi.fn(async () => envelope([]))
    const userExtras = userApi as unknown as Record<string, unknown>
    userExtras.getTeacherStudents = vi.fn(async () => envelope([]))
  })

  it('收件箱不挂页签，也不挂只会 toast 的演示动作', async () => {
    const wrapper = mountPage()
    await settle()
    await vi.waitFor(() => {
      expect(wrapper.find('.inbox-body').exists(), '收件箱正文未渲染').toBe(true)
    })
    expect(wrapper.findAll('.page-tabs button')).toHaveLength(0)
    const actionTexts = wrapper.findAll('.page-head-actions button').map((node) => node.text())
    expect(actionTexts).not.toContain('批量确认')
    expect(actionTexts).not.toContain('导出')
    expect(sourceChipText(wrapper)).toBe('接口数据')
    wrapper.unmount()
  })
})
