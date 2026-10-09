import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

// vi.hoisted 保证 mock 工厂中的变量在 hoisting 时可用
const mocks = vi.hoisted(() => ({
  mockPush: vi.fn(),
  mockRoute: { path: '/' },
  teacherCourses: vi.fn(),
  studentCourses: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.mockPush }),
  useRoute: () => mocks.mockRoute,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ userName: '孙立群', role: 'TEACHER' }),
}))

vi.mock('@/api/course', () => ({
  default: { getMyCourses: mocks.teacherCourses },
}))

vi.mock('@/api/student', () => ({
  default: { getMyCourses: mocks.studentCourses },
}))

const AppSidebar = (await import('../AppSidebar.vue')).default

const COURSE = { id: 1, name: '数据结构' }

/** 侧边栏按角色挂载；path 用于模拟当前路由（决定高亮与自动展开） */
async function mountSidebar(role: string, path: string) {
  mocks.mockRoute.path = path
  const wrapper = mount(AppSidebar, { props: { role } })
  await flushPromises()
  return wrapper
}

function navItem(wrapper, text: string) {
  const node = wrapper.findAll('.nav-item').find((n) => n.find('.nav-txt').text().trim() === text)
  expect(node, `侧边栏应渲染一级菜单项「${text}」`).toBeTruthy()
  return node!
}

function navLeaf(wrapper, text: string) {
  const node = wrapper
    .findAll('.nav-leaf')
    .find((n) => n.find('.nav-sub-txt').text().trim() === text)
  expect(node, `侧边栏应渲染课程页签「${text}」`).toBeTruthy()
  return node!
}

describe('AppSidebar 导航跳转', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.teacherCourses.mockResolvedValue({ data: { records: [COURSE] } })
    mocks.studentCourses.mockResolvedValue({ data: { records: [COURSE] } })
  })

  it('教师端：位于课程子页时「教学概览」可见且可点回 /teacher/dashboard', async () => {
    const wrapper = await mountSidebar('TEACHER', '/teacher/courses/1/analytics')
    expect(navItem(wrapper, '课程目录').classes()).toContain('active')
    const dashboard = navItem(wrapper, '教学概览')
    expect(dashboard.classes()).not.toContain('active')

    await dashboard.trigger('click')
    expect(mocks.mockPush).toHaveBeenCalledTimes(1)
    expect(mocks.mockPush).toHaveBeenCalledWith('/teacher/dashboard')
  })

  it('教师端：课程目录下三级页签跳到该课程路由', async () => {
    const wrapper = await mountSidebar('TEACHER', '/teacher/courses/1/analytics')
    await navLeaf(wrapper, '学情统计').trigger('click')
    expect(mocks.mockPush).toHaveBeenCalledWith('/teacher/courses/1/analytics')
  })

  it('学生端：三条跨课程入口在静态区渲染并可点击跳转', async () => {
    const wrapper = await mountSidebar('STUDENT', '/student/dashboard')
    const cases = [
      ['知识图谱', '/student/knowledge-graph'],
      ['模拟考试', '/student/exams'],
      ['我的成绩', '/student/grades'],
    ] as const
    for (const [text, path] of cases) {
      mocks.mockPush.mockClear()
      await navItem(wrapper, text).trigger('click')
      expect(mocks.mockPush).toHaveBeenCalledWith(path)
    }
  })

  it('一级项含子菜单时点击只展开手风琴，不触发跳转', async () => {
    const wrapper = await mountSidebar('TEACHER', '/teacher/dashboard')
    const catalog = navItem(wrapper, '课程目录')
    await catalog.trigger('click')
    expect(mocks.mockPush).not.toHaveBeenCalled()
    expect(catalog.classes()).toContain('expanded')
  })

  it('管理员端：扁平治理视图，不构建课程目录也不拉取课程列表', async () => {
    const wrapper = await mountSidebar('ADMIN', '/admin/dashboard')
    expect(wrapper.text()).not.toContain('课程目录')
    expect(mocks.teacherCourses).not.toHaveBeenCalled()
    expect(mocks.studentCourses).not.toHaveBeenCalled()

    // 菜单文案的唯一来源是 menus.ts：本条只跟着「用户与账号」这个新分区标签改字，
    // 断言的跳转目标 /admin/users 未变。ADMIN 侧的组装改造与目录断言由 Task 4 承接。
    await navItem(wrapper, '用户与账号').trigger('click')
    expect(mocks.mockPush).toHaveBeenCalledWith('/admin/users')
  })
})
