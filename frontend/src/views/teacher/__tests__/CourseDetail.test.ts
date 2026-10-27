import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CourseDetail.vue（教师课程详情）— 分路加载失败可见（2026-10-14，批次 16）
 *
 * <p>原实现 initData 虽已用 Promise.allSettled，但各 loader 内部 catch 仅 logger.warn 静默：
 * 失败时页面照常显示「暂无章节/暂无学生/暂无通知」，教师会误判课程确实无内容。
 * 现补四路独立错误 ref + computed loadError 聚合 + 页面级持久 el-alert，
 * 三个 Tab 子组件经 emptyText prop 透传诚实空态文案。</p>
 */

const mocks = vi.hoisted(() => ({
  getCourseById: vi.fn(),
  getAllStudents: vi.fn(),
  getStats: vi.fn(),
  getChapters: vi.fn(),
  getNotificationList: vi.fn(),
  getResourceList: vi.fn(),
  getAllTasks: vi.fn(),
  getAllExamPapers: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '1' } }),
  useRouter: () => ({ push: vi.fn() }),
}))
// 依赖链：api 模块 → request.js → import '@/router'，直接 mock 避免求值真实路由
vi.mock('@/router', () => ({
  default: { beforeEach: vi.fn(), afterEach: vi.fn(), push: vi.fn() },
}))
vi.mock('@/api/course', () => ({
  default: {
    getCourseById: mocks.getCourseById,
    getAllStudents: mocks.getAllStudents,
    getStats: mocks.getStats,
  },
}))
vi.mock('@/api/chapter', () => ({ default: { getChapters: mocks.getChapters } }))
vi.mock('@/api/notification', () => ({
  default: { getNotificationList: mocks.getNotificationList },
}))
vi.mock('@/api/resource', () => ({ default: { getResourceList: mocks.getResourceList } }))
vi.mock('@/api/task', () => ({ default: { getAllTasks: mocks.getAllTasks } }))
vi.mock('@/api/exam', () => ({ default: { getAllExamPapers: mocks.getAllExamPapers } }))
vi.mock('@/api/enrollment', () => ({ default: {} }))
vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  }),
  ElMessageBox: { confirm: vi.fn() },
}))

import CourseDetail from '@/views/teacher/CourseDetail.vue'

/** 三个 Tab 子组件桩：渲染 emptyText（空数据时）或真实计数（有数据时），供串味锁定断言 */
function tabStub(props: string[], cls: string, listKey: string) {
  return {
    props,
    template: `<div class="${cls}"><span v-if="!${listKey} || !${listKey}.length" class="empty">{{ emptyText }}</span><span v-else class="count">{{ ${listKey}.length }}</span></div>`,
  }
}

function mountPage() {
  return mount(CourseDetail, {
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        CdChaptersTab: tabStub(
          ['chapters', 'chaptersLoading', 'dragChanged', 'savingOrder', 'emptyText'],
          'chapters-stub',
          'chapters',
        ),
        CdStudentsTab: tabStub(
          ['students', 'studentsLoading', 'emptyText'],
          'students-stub',
          'students',
        ),
        CdNotificationsTab: tabStub(
          ['notifications', 'notificationsLoading', 'formatDateTime', 'emptyText'],
          'notifications-stub',
          'notifications',
        ),
        StatCard: {
          props: ['label', 'value'],
          template: '<div class="sc-stub">{{ label }}|{{ value }}</div>',
        },
        'el-icon': true,
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

/** 四路主接口全失败 */
function mockAllRejected() {
  mocks.getCourseById.mockRejectedValue(new Error('500'))
  mocks.getChapters.mockRejectedValue(new Error('500'))
  mocks.getAllStudents.mockRejectedValue(new Error('500'))
  mocks.getNotificationList.mockRejectedValue(new Error('500'))
}
/** 四路主接口全成功（空数据）；count 类恒成功 */
function mockAllResolved() {
  mocks.getCourseById.mockResolvedValue({ data: { id: 1, name: '测试课程' } })
  mocks.getChapters.mockResolvedValue({ data: { list: [] } })
  mocks.getAllStudents.mockResolvedValue([])
  mocks.getNotificationList.mockResolvedValue({ data: { list: [] } })
}

beforeEach(() => {
  vi.clearAllMocks()
  // count 类加载器静默容错，恒给空成功，与本组断言无关
  mocks.getStats.mockResolvedValue({ data: {} })
  mocks.getResourceList.mockResolvedValue({ data: { list: [] } })
  mocks.getAllTasks.mockResolvedValue([])
  mocks.getAllExamPapers.mockResolvedValue([])
})

describe('CourseDetail（教师）— 分路加载失败可见', () => {
  it('全部失败：给出持久提示，三个 Tab 空态不谎称「暂无」', async () => {
    mockAllRejected()
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('课程信息加载失败，请检查网络后重试')
    expect(wrapper.get('.chapters-stub .empty').text()).toContain(
      '章节列表未能加载，请查看上方提示后重试',
    )
    expect(wrapper.get('.students-stub .empty').text()).toContain(
      '学生列表未能加载，请查看上方提示后重试',
    )
    expect(wrapper.get('.notifications-stub .empty').text()).toContain(
      '通知列表未能加载，请查看上方提示后重试',
    )
    expect(wrapper.text()).not.toContain('暂无章节')
    expect(wrapper.text()).not.toContain('暂无学生')
    expect(wrapper.text()).not.toContain('暂无通知')
  })

  it('阴性对照：全部成功（空数据）时无提示，空态保留原语义', async () => {
    mockAllResolved()
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.get('.chapters-stub .empty').text()).toContain('暂无章节，点击上方添加')
    expect(wrapper.get('.students-stub .empty').text()).toContain('暂无学生')
    expect(wrapper.get('.notifications-stub .empty').text()).toContain('暂无通知')
    expect(wrapper.text()).not.toContain('未能加载')
  })

  it('串味锁定：仅章节失败时学生/通知两路正常渲染，提示只归因章节', async () => {
    mocks.getCourseById.mockResolvedValue({ data: { id: 1, name: '测试课程' } })
    mocks.getChapters.mockRejectedValue(new Error('500'))
    mocks.getAllStudents.mockResolvedValue([{ id: 1, studentName: '张三' }])
    mocks.getNotificationList.mockResolvedValue({ data: { list: [{ id: 1, title: '公告' }] } })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.get('.alert-stub').text()).toContain('章节列表加载失败，请检查网络后重试')
    expect(wrapper.get('.chapters-stub .empty').text()).toContain(
      '章节列表未能加载，请查看上方提示后重试',
    )
    // 他路不受牵连：学生/通知按真实计数渲染
    expect(wrapper.get('.students-stub .count').text()).toBe('1')
    expect(wrapper.get('.notifications-stub .count').text()).toBe('1')
  })

  it('失败后再成功：提示必须被清空（initData 复位四路错误）', async () => {
    mockAllRejected()
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
