import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CourseDetail.vue（学生课程详情）— 分路加载失败可见（2026-10-14，批次 16）
 *
 * <p>原实现三路加载用 Promise.all 一把抓：任一接口失败 → catch 仅瞬时 ElMessage，
 * 且三路数据全部丢失，章节列表谎称「暂无章节内容」。现改 Promise.allSettled
 * 三路独立归因（courseError/chaptersError/enrollError + computed loadError 聚合）
 * + 页面级持久 el-alert，CduChapterList 经 emptyText prop 透传诚实空态文案。</p>
 */

const mocks = vi.hoisted(() => ({
  getCourseById: vi.fn(),
  getChapters: vi.fn(),
  getMyEnrollments: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '1' }, query: {} }),
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('@/router', () => ({
  default: { beforeEach: vi.fn(), afterEach: vi.fn(), push: vi.fn() },
}))
vi.mock('@/api/course', () => ({ default: { getCourseById: mocks.getCourseById } }))
vi.mock('@/api/chapter', () => ({ default: { getChapters: mocks.getChapters } }))
vi.mock('@/api/enrollment', () => ({
  default: { getMyEnrollments: mocks.getMyEnrollments, enrollCourse: vi.fn() },
}))
vi.mock('@/api/learningRecord', () => ({ default: { createLearningRecord: vi.fn() } }))
vi.mock('@/api/resource', () => ({ default: { getChapterResources: vi.fn() } }))
vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  }),
}))
vi.mock('@element-plus/icons-vue', () => ({
  Plus: { name: 'Plus' },
  ArrowRight: { name: 'ArrowRight' },
  Select: { name: 'Select' },
  Timer: { name: 'Timer' },
  ArrowLeft: { name: 'ArrowLeft' },
  VideoPlay: { name: 'VideoPlay' },
}))

import CourseDetail from '@/views/student/course/CourseDetail.vue'

function mountPage() {
  return mount(CourseDetail, {
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        CduHeader: {
          props: ['course', 'enrollment', 'getProgressColor'],
          template: '<div class="header-stub">{{ course.name }}</div>',
        },
        CduChapterList: {
          props: ['chapters', 'emptyText'],
          template:
            '<div class="chapters-stub"><span v-if="!chapters.length" class="empty">{{ emptyText }}</span><span v-else class="count">{{ chapters.length }}</span></div>',
        },
        'el-icon': true,
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

/** 三路全成功（章节空列表） */
function mockAllResolved() {
  mocks.getCourseById.mockResolvedValue({ data: { id: 1, name: '测试课程' } })
  mocks.getChapters.mockResolvedValue({ data: [] })
  mocks.getMyEnrollments.mockResolvedValue({
    data: { list: [{ id: 9, courseId: 1, isActive: true, progress: 0.5 }] },
  })
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('CourseDetail（学生）— 分路加载失败可见', () => {
  it('全部失败：给出持久提示，章节空态不谎称「暂无章节内容」', async () => {
    mocks.getCourseById.mockRejectedValue(new Error('500'))
    mocks.getChapters.mockRejectedValue(new Error('500'))
    mocks.getMyEnrollments.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('课程信息加载失败，请检查网络后重试')
    expect(wrapper.get('.chapters-stub .empty').text()).toContain(
      '章节列表未能加载，请查看上方提示后重试',
    )
    expect(wrapper.text()).not.toContain('暂无章节内容')
    // 课程路失败：头部不得谎报课程名（保持初始占位）
    expect(wrapper.get('.header-stub').text()).not.toContain('测试课程')
  })

  it('阴性对照：全部成功（章节为空）时无提示，空态保留原语义', async () => {
    mockAllResolved()
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.get('.chapters-stub .empty').text()).toContain('暂无章节内容')
    expect(wrapper.text()).not.toContain('未能加载')
    expect(wrapper.get('.header-stub').text()).toContain('测试课程')
  })

  it('串味锁定：仅章节失败时课程/选课两路正常渲染，提示只归因章节', async () => {
    mocks.getCourseById.mockResolvedValue({ data: { id: 1, name: '测试课程' } })
    mocks.getChapters.mockRejectedValue(new Error('500'))
    mocks.getMyEnrollments.mockResolvedValue({
      data: { list: [{ id: 9, courseId: 1, isActive: true, progress: 0.5 }] },
    })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('.alert-stub').text()).toContain('章节列表加载失败，请检查网络后重试')
    expect(wrapper.get('.chapters-stub .empty').text()).toContain(
      '章节列表未能加载，请查看上方提示后重试',
    )
    // 他路不受牵连：课程名正常填充
    expect(wrapper.get('.header-stub').text()).toContain('测试课程')
  })

  it('失败后再成功：提示必须被清空（每次加载复位三路错误）', async () => {
    mocks.getCourseById.mockRejectedValue(new Error('500'))
    mocks.getChapters.mockRejectedValue(new Error('500'))
    mocks.getMyEnrollments.mockRejectedValue(new Error('500'))
    const first = mountPage()
    await flushPromises()
    expect(first.find('.alert-stub').exists()).toBe(true)

    mockAllResolved()
    const second = mountPage()
    await flushPromises()

    expect(second.find('.alert-stub').exists()).toBe(false)
    expect(second.text()).not.toContain('加载失败')
  })
})
