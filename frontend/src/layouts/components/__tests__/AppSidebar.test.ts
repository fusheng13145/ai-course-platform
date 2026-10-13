import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

// vi.hoisted 保证 mock 工厂中的变量在 hoisting 时可用
const mocks = vi.hoisted(() => ({
  mockPush: vi.fn(),
  mockRoute: { path: '/' },
  studentCourses: vi.fn(),
  allCourses: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.mockPush }),
  useRoute: () => mocks.mockRoute,
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({ userName: '孙立群', role: 'TEACHER' }),
}))

vi.mock('@/api/course', () => ({
  default: {
    getMyCourses: vi.fn(),
    getAllMyCourses: mocks.allCourses,
    getCourseList: vi.fn(),
    getAllCourses: mocks.allCourses,
  },
}))

vi.mock('@/api/student', () => ({
  default: { getMyCourses: vi.fn(), getAllMyCourses: mocks.studentCourses },
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
    mocks.studentCourses.mockResolvedValue([COURSE])
    mocks.allCourses.mockResolvedValue([COURSE])
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

  it('管理员端：拉全库课程列表构建课程目录，治理大盘仍是第一分区', async () => {
    const wrapper = await mountSidebar('ADMIN', '/admin/dashboard')

    expect(mocks.allCourses).toHaveBeenCalled()
    expect(mocks.studentCourses).not.toHaveBeenCalled()

    // 组装顺序锁死：治理大盘（/admin 空路径重定向落点）第一，动态课程目录紧随其后，其余静态分区依次
    const labels = wrapper.findAll('.nav-section-label').map((n) => n.text())
    expect(labels).toEqual([
      '治理总览',
      '课程目录',
      '用户与班级',
      '题库与试卷',
      '数据统计',
      '内容与资源治理',
      '权限·系统·审计',
    ])

    // 静态区一级项按新文案渲染，点击真实跳转
    await navItem(wrapper, '用户与账号').trigger('click')
    expect(mocks.mockPush).toHaveBeenCalledWith('/admin/users')
  })

  it('管理员端：课程目录三级页签跳到 /admin 前缀的子路由', async () => {
    const wrapper = await mountSidebar('ADMIN', '/admin/courses/1/grades')
    await navLeaf(wrapper, '成绩统计').trigger('click')
    expect(mocks.mockPush).toHaveBeenCalledWith('/admin/courses/1/grades')
  })

  it('管理员端：位于课程子页时课程目录与课程节点自动展开', async () => {
    const wrapper = await mountSidebar('ADMIN', '/admin/courses/1/bank')
    const catalog = navItem(wrapper, '课程目录')
    expect(catalog.classes()).toContain('active')
    expect(catalog.classes()).toContain('expanded')

    // 三级页签容器由 AppSidebar.vue 的 v-if="child.children" 决定是否存在于 DOM，
    // 「展开」只切换 .nav-sub-3 的 open class（对应 course-${id} 进入 expandedKeys）；
    // 页签是否存在与展开态无关，故断言 open 才真正锁住课程节点自动展开这一半行为。
    navLeaf(wrapper, '章节与任务点')
    expect(wrapper.find('.nav-sub-3').classes()).toContain('open')
  })

  it('管理员端：课程治理总览入口精确高亮，不被课程子页误亮', async () => {
    const wrapper = await mountSidebar('ADMIN', '/admin/courses/1/overview')
    const all = wrapper
      .findAll('.nav-sub-item')
      .find((n) => n.find('.nav-sub-txt').text().trim() === '课程治理总览')
    expect(all, '应渲染「课程治理总览」入口').toBeTruthy()
    expect(all!.classes()).not.toContain('active')
  })
})
