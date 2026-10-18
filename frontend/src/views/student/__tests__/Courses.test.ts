import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * student/Courses.vue（课程大厅）— 加载失败可见（2026-10-14，批次 18）
 *
 * <p>原实现 load 失败仅弹瞬时 toast 且清空 courses：列表空态谎称「暂无课程」，
 * 学生误判自己没有任何课程。现补 loadError + 页面级持久 el-alert，空态文案诚实化，
 * 移除被持久提示取代的瞬时 toast。</p>
 */

const mocks = vi.hoisted(() => ({
  getStudentDashboard: vi.fn(),
  getCourseDetail: vi.fn(),
  enrollCourse: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: vi.fn() }),
  useRoute: () => ({ params: {}, path: '/' }),
}))
vi.mock('@/router', () => ({
  default: { beforeEach: vi.fn(), afterEach: vi.fn(), push: vi.fn() },
}))
vi.mock('@/api/student', () => ({
  default: {
    getStudentDashboard: mocks.getStudentDashboard,
    getCourseDetail: mocks.getCourseDetail,
    enrollCourse: mocks.enrollCourse,
  },
}))
vi.mock('@/composables/useToast', () => ({ toast: { show: vi.fn() } }))

import StudentCourses from '@/views/student/Courses.vue'

const EMPTY_DASHBOARD = {
  data: { data: { courses: [], todos: { assignments: [], exams: [], signIns: [] } } },
}

function mountPage() {
  return mount(StudentCourses, {
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        // D2-3：空态统一为 EmptyState；shallow 下显式桩，断言 title 双态文案
        EmptyState: {
          props: ['title', 'desc', 'icon', 'size'],
          template: '<div class="empty-stub">{{ title }}</div>',
        },
        'el-skeleton': true,
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.getStudentDashboard.mockResolvedValue(EMPTY_DASHBOARD)
  mocks.getCourseDetail.mockResolvedValue({ data: { data: { course: {} } } })
  mocks.enrollCourse.mockResolvedValue({})
})

describe('student/Courses 课程大厅 — 加载失败可见', () => {
  it('加载失败：给出持久提示，空态不谎称「暂无课程」', async () => {
    mocks.getStudentDashboard.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.get('.alert-stub').text()).toContain('课程数据加载失败，请检查网络后重试')
    expect(wrapper.get('.empty-stub').text()).toContain('课程列表未能加载，请查看上方提示后重试')
    expect(wrapper.text()).not.toContain('暂无课程')
  })

  it('阴性对照：加载成功（空列表）时无提示，空态保留原语义', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.get('.empty-stub').text()).toContain('暂无课程')
    expect(wrapper.text()).not.toContain('未能加载')
  })

  it('加载成功：课程按真实数据渲染左侧列表与详情卡', async () => {
    mocks.getStudentDashboard.mockResolvedValue({
      data: {
        data: {
          courses: [{ courseId: 1, courseName: '数据结构', progress: 40, cover: '#c13b30' }],
          todos: { assignments: [], exams: [], signIns: [] },
        },
      },
    })
    mocks.getCourseDetail.mockResolvedValue({
      data: { data: { course: { semester: '2026春', credits: 3, code: 'DS101' } } },
    })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.findAll('.tree-row')).toHaveLength(1)
    expect(wrapper.text()).toContain('数据结构')
    expect(wrapper.text()).toContain('2026春')
  })

  it('失败后再成功：提示必须被清空（每次 load 复位）', async () => {
    mocks.getStudentDashboard.mockRejectedValueOnce(new Error('500'))
    const first = mountPage()
    await flushPromises()
    expect(first.find('.alert-stub').exists()).toBe(true)

    const second = mountPage()
    await flushPromises()

    expect(second.find('.alert-stub').exists()).toBe(false)
    expect(second.get('.empty-stub').text()).toContain('暂无课程')
  })
})
