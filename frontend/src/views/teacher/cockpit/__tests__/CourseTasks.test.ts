import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CourseTasks.vue（课程任务）— 加载失败可见（2026-10-14，批次 16）
 *
 * <p>原实现 load 失败仅 logger.error 静默：失败时空态谎称「暂无任务，点击右上角新建」，
 * 教师会误判课程确实无任务。现补 loadError + 页面级持久 el-alert，空态文案诚实化。</p>
 */

const mocks = vi.hoisted(() => ({
  getAllTasks: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '5' } }),
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('@/router', () => ({
  default: { beforeEach: vi.fn(), afterEach: vi.fn(), push: vi.fn() },
}))
vi.mock('@/api/task', () => ({
  default: {
    getAllTasks: mocks.getAllTasks,
    createTask: vi.fn(),
    updateTask: vi.fn(),
    publishTask: vi.fn(),
    extendDeadline: vi.fn(),
    deleteTask: vi.fn(),
  },
}))
vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  }),
  ElMessageBox: { confirm: vi.fn() },
}))

import CourseTasks from '@/views/teacher/cockpit/CourseTasks.vue'

function mountPage() {
  return mount(CourseTasks, {
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
        'el-table': {
          props: ['data', 'emptyText'],
          template: '<div class="tbl-stub">{{ (data || []).length }}</div>',
        },
        PageHeader: {
          props: ['title', 'subtitle', 'icon', 'compact'],
          template: '<div><slot /></div>',
        },
        'el-icon': true,
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

describe('CourseTasks — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载失败：给出持久提示，空态不谎称「暂无任务」', async () => {
    mocks.getAllTasks.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('任务列表加载失败，请检查网络后重试')
    expect(wrapper.get('.empty-stub').text()).toContain(
      '任务列表未能加载，请查看上方提示后重试',
    )
    expect(wrapper.text()).not.toContain('暂无任务，点击右上角新建')
    expect(wrapper.find('.tbl-stub').exists()).toBe(false)
  })

  it('阴性对照：加载成功（空列表）时无提示，空态保留原语义', async () => {
    mocks.getAllTasks.mockResolvedValue([])
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.get('.empty-stub').text()).toContain('暂无任务，点击右上角新建')
    expect(wrapper.text()).not.toContain('未能加载')
  })

  it('加载成功：任务列表按真实数据渲染表格', async () => {
    mocks.getAllTasks.mockResolvedValue([
      { id: 1, title: '作业一', taskType: '作业', isPublished: true },
      { id: 2, title: '测验一', taskType: '测验', isPublished: false },
    ])
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.find('.empty-stub').exists()).toBe(false)
    expect(wrapper.get('.tbl-stub').text()).toBe('2')
  })

  it('失败后再成功：提示必须被清空（每次 load 复位）', async () => {
    mocks.getAllTasks.mockRejectedValueOnce(new Error('500')).mockResolvedValue([])
    const first = mountPage()
    await flushPromises()
    expect(first.find('.alert-stub').exists()).toBe(true)

    const second = mountPage()
    await flushPromises()

    expect(second.find('.alert-stub').exists()).toBe(false)
    expect(second.text()).not.toContain('任务列表加载失败')
    expect(second.get('.empty-stub').text()).toContain('暂无任务，点击右上角新建')
  })
})
