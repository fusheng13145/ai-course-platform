import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CourseTaskPanel.vue（作业面板）— 加载失败可见（2026-10-14，批次 17）
 *
 * <p>原实现 loadTasks 失败仅 logger.error 静默：失败时空态谎称「暂无作业」，
 * 教师会误判课程确实没有作业。现补 loadError + 页面级持久 el-alert，空态文案诚实化。</p>
 */

const mocks = vi.hoisted(() => ({
  listByCourse: vi.fn(),
}))

vi.mock('@/api/task', () => ({
  default: {
    listByCourse: mocks.listByCourse,
    createTask: vi.fn(),
    updateTask: vi.fn(),
    publishTask: vi.fn(),
    deleteTask: vi.fn(),
  },
}))
vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
  }),
  ElMessageBox: { confirm: vi.fn() },
}))

import CourseTaskPanel from '@/components/CourseTaskPanel.vue'

function mountPanel() {
  return mount(CourseTaskPanel, {
    props: { courseId: 5 },
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
          props: ['data'],
          template: '<div class="tbl-stub">{{ (data || []).length }}</div>',
        },
        'el-icon': true,
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

describe('CourseTaskPanel — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载失败：给出持久提示，空态不谎称「暂无作业」', async () => {
    mocks.listByCourse.mockRejectedValue(new Error('500'))
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('作业列表加载失败，请检查网络后重试')
    expect(wrapper.get('.empty-stub').text()).toContain('作业列表未能加载，请查看上方提示后重试')
    expect(wrapper.text()).not.toContain('暂无作业')
  })

  it('阴性对照：加载成功（空列表）时无提示，空态保留原语义', async () => {
    mocks.listByCourse.mockResolvedValue({ data: [] })
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.get('.empty-stub').text()).toContain('暂无作业')
    expect(wrapper.text()).not.toContain('未能加载')
  })

  it('加载成功：作业列表按真实数据渲染表格', async () => {
    mocks.listByCourse.mockResolvedValue({
      data: [
        { id: 1, title: '作业一' },
        { id: 2, title: '测验一' },
      ],
    })
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.find('.empty-stub').exists()).toBe(false)
    expect(wrapper.get('.tbl-stub').text()).toBe('2')
  })

  it('失败后再成功：提示必须被清空（每次 load 复位）', async () => {
    mocks.listByCourse.mockRejectedValueOnce(new Error('500')).mockResolvedValue({ data: [] })
    const first = mountPanel()
    await flushPromises()
    expect(first.find('.alert-stub').exists()).toBe(true)

    const second = mountPanel()
    await flushPromises()

    expect(second.find('.alert-stub').exists()).toBe(false)
    expect(second.text()).not.toContain('作业列表加载失败')
    expect(second.get('.empty-stub').text()).toContain('暂无作业')
  })
})
