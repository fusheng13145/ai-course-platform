import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const state = vi.hoisted(() => ({ role: 'admin', tab: 'grades', roleRef: null as unknown }))

vi.mock('vue-router', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue-router')>()
  return {
    ...actual,
    useRoute: () => ({ params: { id: '7', tab: state.tab }, query: {} }),
    useRouter: () => ({ push: vi.fn(), replace: vi.fn() }),
  }
})

vi.mock('../composables/ui', async () => {
  const { ref } = await import('vue')
  // 模板里比对的是解包后的 role，必须给真 ref
  state.roleRef = ref(state.role)
  return {
    useUi: () => ({
      role: state.roleRef,
      courseContext: { id: 7, name: '计算机网络' },
      setCourseContext: vi.fn(),
      toast: vi.fn(),
      paletteOpen: ref(false),
      paletteQuery: ref(''),
    }),
  }
})

vi.mock('../data/api', () => ({
  loadCourse: vi.fn(async () => null),
  getCourses: vi.fn(async () => []),
}))

const mocks = vi.hoisted(() => {
  const empty = async () => ({ data: { list: [], records: [] } })
  const apiStub = (target: Record<string, unknown>) =>
    new Proxy(target, {
      get: (obj, key: string) => (key in obj ? obj[key] : empty),
    })
  const chapter: Record<string, unknown> = { getChapters: empty, getChapterTree: empty }
  const teacher: Record<string, unknown> = { getMyCourseSummaries: empty }
  const resource: Record<string, unknown> = { getResourceList: empty }
  const student: Record<string, unknown> = { getGrades: empty }
  return {
    empty,
    chapter,
    teacher,
    resource,
    student,
    stubs: {
      chapter: apiStub(chapter),
      teacher: apiStub(teacher),
      resource: apiStub(resource),
      student: apiStub(student),
    },
  }
})

vi.mock('@/api/chapter', () => ({ default: mocks.stubs.chapter }))
vi.mock('@/api/teacher', () => ({ default: mocks.stubs.teacher }))
vi.mock('@/api/resource', () => ({ default: mocks.stubs.resource }))
vi.mock('@/api/student', () => ({ default: mocks.stubs.student }))

import CourseView from '../views/course/CourseView.vue'
import AdminReadOnlyTab from '../views/course/tabs/AdminReadOnlyTab.vue'
import ChaptersTab from '../views/course/tabs/ChaptersTab.vue'
import GradesTab from '../views/course/tabs/GradesTab.vue'
import CourseOverviewTab from '../views/course/tabs/CourseOverviewTab.vue'

function mountCourseView() {
  return mount(CourseView)
}

function setRole(role: string) {
  state.role = role
  ;(state.roleRef as { value: string }).value = role
}

beforeEach(() => {
  setRole('admin')
  state.tab = 'grades'
  mocks.chapter.getChapters = vi.fn(async () => ({ data: { list: [] } }))
  mocks.chapter.getChapterTree = vi.fn(async () => ({ data: [] }))
  mocks.teacher.getMyCourseSummaries = vi.fn(async () => ({ data: [] }))
  mocks.resource.getResourceList = vi.fn(async () => ({ data: [] }))
  mocks.student.getGrades = vi.fn(async () => ({ data: { courses: [] } }))
})

describe('课程空间 · 管理员只读页签', () => {
  it.each(['content', 'bank', 'exams', 'grades', 'graph', 'aiUsage'])(
    '管理员 %s 渲染只读明示而非示例读数',
    (tab) => {
      state.tab = tab
      const wrapper = mountCourseView()
      expect(wrapper.findComponent(AdminReadOnlyTab).exists()).toBe(true)
      expect(wrapper.text()).toContain('管理员只读，无数据面')
      wrapper.unmount()
    },
  )

  it('教师与学生同页签仍走各自组件，不被只读明示替换', async () => {
    setRole('teacher')
    state.tab = 'chapters'
    const teacherWrapper = mountCourseView()
    expect(teacherWrapper.findComponent(AdminReadOnlyTab).exists()).toBe(false)
    expect(teacherWrapper.findComponent(ChaptersTab).exists()).toBe(true)
    teacherWrapper.unmount()

    setRole('student')
    state.tab = 'grades'
    const studentWrapper = mountCourseView()
    expect(studentWrapper.findComponent(AdminReadOnlyTab).exists()).toBe(false)
    expect(studentWrapper.findComponent(GradesTab).exists()).toBe(true)
    studentWrapper.unmount()
    await flushPromises()
  })
})

describe('课程空间 · 总览三态与去示例兜底', () => {
  function mountOverview(role: 'admin' | 'teacher' | 'student') {
    return mount(CourseOverviewTab, {
      props: { courseId: 7, role, course: { name: '计算机网络', code: 'CS7', credits: 4 } },
    })
  }

  it('管理员总览明示只读，且不渲染原型班级表', async () => {
    const wrapper = mountOverview('admin')
    await flushPromises()
    expect(wrapper.find('[data-testid="overview-admin-readonly"]').exists()).toBe(true)
    expect(wrapper.find('.mini-class').exists()).toBe(false)
    wrapper.unmount()
  })

  it('接口失败显示可重试的失败态，而不是空态', async () => {
    mocks.chapter.getChapters = vi.fn(async () => {
      throw new Error('500')
    })
    mocks.teacher.getMyCourseSummaries = vi.fn(async () => {
      throw new Error('500')
    })
    const wrapper = mountOverview('teacher')
    await flushPromises()
    const error = wrapper.find('[data-testid="overview-error"]')
    expect(error.exists()).toBe(true)
    expect(error.text()).toContain('重试')
    expect(wrapper.find('[data-testid="overview-empty"]').exists()).toBe(false)
    wrapper.unmount()
  })

  it('教学班无数据面时明示不可用，不写示例班级与假进度', async () => {
    const wrapper = mountOverview('teacher')
    await flushPromises()
    expect(wrapper.text()).toContain('教学班数据暂不可用')
    expect(wrapper.html()).not.toContain('示例数据：教学班')
    wrapper.unmount()
  })

  it('课程头部不再保留只弹演示 toast 的课程通知按钮', async () => {
    setRole('teacher')
    state.tab = 'overview'
    const wrapper = mountCourseView()
    await flushPromises()
    const labels = wrapper.findAll('button').map((node) => node.text())
    expect(labels).not.toContain('课程通知')
    wrapper.unmount()
  })
})
