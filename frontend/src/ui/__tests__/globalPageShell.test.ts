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
  getAIUsageByCourse: vi.fn(),
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
  meta: { pageKey: 'tasks' } as Record<string, unknown>,
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
    useUi: () => ({
      role: uiState.role,
      theme: uiState.theme,
      setTheme: uiState.setTheme,
      toast: uiState.toast,
      auth: uiState.auth,
    }),
  }
})

import GlobalPage from '../views/GlobalPage.vue'
import { pageDef } from '../nav'

const envelope = (data: unknown) => ({ code: 200, message: 'ok', data })

async function settle() {
  await flushPromises()
  await flushPromises()
}

function mountPage(): VueWrapper {
  return mount(GlobalPage, {
    global: { stubs: { RouterLink: true } },
  })
}

function sourceChipText(wrapper: VueWrapper): string {
  const chip = wrapper.find('.page-kicker .chip')
  return chip.exists() ? chip.text() : ''
}

beforeEach(() => {
  vi.clearAllMocks()
  route.meta.pageKey = 'tasks'
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
  adminApi.getAIUsageByCourse.mockResolvedValue(
    envelope({
      rangeDays: 30,
      totalCalls: 12,
      totalTokens: 3400,
      distinctCourses: 1,
      unboundCalls: 0,
      successRate: 1,
      collectionNote: '仅统计埋点上线后的调用',
      courses: [{ courseId: 1, courseName: '计算机网络', calls: 12, tokens: 3400, successRate: 1 }],
    }),
  )
  permissionApi.getAllPermissions.mockResolvedValue(envelope([]))
  classroomApi.getClassroomList.mockResolvedValue(envelope({ records: [], total: 0 }))
  courseApi.getAllCourses.mockResolvedValue(envelope({ records: [], total: 0 }))
})

describe('全局页壳层：真实页面不得再戴「示例数据」章', () => {
  it('学生任务 / 学业档案 / 个人中心都标「接口数据」', async () => {
    const bodies: Record<string, string> = {
      tasks: '.tasks-body',
      academic: '.academic-body',
      profile: '.profile-body',
    }
    for (const page of ['tasks', 'academic', 'profile']) {
      route.meta.pageKey = page
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
      route.meta.pageKey = page
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

  it('既无真实 Body 也非「不提供」的页面落兜底空态：不挂来源章、不出示例读数', async () => {
    // 示例层已于 batch 24 整体删除：兜底不再是「示例块」，而是一句明确的空态。
    // 这里断言的是「不出现任何读数来源声明」，而不是「标成示例数据」。
    uiState.role.value = 'student'
    route.meta.pageKey = 'courses'
    const wrapper = mountPage()
    await settle()
    expect(sourceChipText(wrapper)).toBe('')
    expect(wrapper.text()).not.toContain('示例数据')
    expect(wrapper.find('[data-testid="page-not-provided"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('管理端 AI 用量页走真实正文并标「接口数据」（M-14 接真后不再回落待扩展空态）', async () => {
    uiState.role.value = 'admin'
    route.meta.pageKey = 'aiUsage'
    const wrapper = mountPage()
    await settle()
    await vi.waitFor(() => {
      expect(wrapper.find('.ai-usage-body').exists(), 'AI 用量正文未渲染').toBe(true)
    })
    expect(wrapper.find('[data-testid="page-not-provided"]').exists()).toBe(false)
    expect(sourceChipText(wrapper)).toBe('接口数据')
    expect(wrapper.text()).not.toContain('示例数据')
    wrapper.unmount()
  })

  it('学业档案三个页签（成绩／学习中心／错题本），且不提供导出成绩动作', async () => {
    route.meta.pageKey = 'academic'
    const wrapper = mountPage()
    await settle()
    await vi.waitFor(() => {
      expect(wrapper.find('.academic-body').exists(), '学业档案正文未渲染').toBe(true)
    })

    const tabs = wrapper.findAll('.academic-body .tabs button')
    expect(tabs.map((node) => node.text())).toEqual(['成绩', '学习中心', '错题本'])
    const bodyText = wrapper.find('.academic-body').text()
    expect(bodyText).not.toContain('学习路径')
    const exportAction = wrapper
      .findAll('.page-head-actions button')
      .find((node) => node.text().includes('导出成绩'))
    expect(exportAction, '学业档案不应再有导出成绩动作').toBeUndefined()
    wrapper.unmount()
  })

  it('正文自带分区的页面不再由壳层叠一条重复页签条（学业档案）', async () => {
    // 壳层曾为 academic 保留一条只有一个「成绩」按钮的页签条，那是「本页只提供成绩」时期的遗留；
    // AcademicBody 自带三页签后，两条页签条同时渲染会让用户看到两个 tablist，且外层只剩一个选项。
    route.meta.pageKey = 'academic'
    const wrapper = mountPage()
    await settle()
    await vi.waitFor(() => {
      expect(wrapper.find('.academic-body').exists(), '学业档案正文未渲染').toBe(true)
    })
    expect(wrapper.findAll('.page-tabs'), '壳层仍渲染重复页签条').toHaveLength(0)
    expect(wrapper.findAll('[role="tablist"]'), '同页出现多个 tablist').toHaveLength(1)
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

describe('全局页壳层：收件箱不留空控件', () => {
  beforeEach(() => {
    uiState.role.value = 'teacher'
    route.meta.pageKey = 'inbox'
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

describe('全局页壳层：判为待扩展的页面不留示例读数', () => {
  it.each([
    ['admin', 'notices', '待扩展：'],
    ['teacher', 'advising', '待扩展：该页签无可用数据面'],
    ['student', 'assistant', '待扩展：'],
  ] as const)('%s/%s 渲染待扩展空态且不挂来源章', async (role, pageName, phrase) => {
    uiState.role.value = role
    route.meta.pageKey = pageName
    const wrapper = mountPage()
    await settle()
    const body = wrapper.find('[data-testid="page-not-provided"]')
    expect(body.exists(), `${role}/${pageName} 未渲染待扩展空态`).toBe(true)
    expect(body.text()).toContain(phrase)
    expect(wrapper.text(), `${role}/${pageName} 仍有示例读数`).not.toContain('示例数据')
    expect(wrapper.find('.page-kicker .chip').exists(), `${role}/${pageName} 仍挂来源章`).toBe(
      false,
    )
    wrapper.unmount()
  })

  it.each([
    ['teacher', 'advising'],
    ['teacher', 'resources'],
  ] as const)('%s/%s 判为待扩展时不挂只会 toast 的演示动作', async (role, pageName) => {
    // 判据与「收件箱不留空控件」同源：页体只有空态，页头却挂着按钮，点下去只弹
    // 「演示数据：…尚未接入」——这句话本身就是把不提供的能力说成"快要有了"。
    uiState.role.value = role
    route.meta.pageKey = pageName
    const wrapper = mountPage()
    await settle()
    expect(
      wrapper.findAll('.page-head-actions button'),
      `${role}/${pageName} 仍挂演示动作`,
    ).toHaveLength(0)
    wrapper.unmount()
  })
})
