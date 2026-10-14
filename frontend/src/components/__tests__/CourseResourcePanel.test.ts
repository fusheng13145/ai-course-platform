import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CourseResourcePanel.vue（资源面板）— 加载失败可见（2026-10-14，批次 17）
 *
 * <p>原实现 loadResources 失败仅 logger.error 静默：失败时空态谎称「暂无资源」，
 * 教师会误判课程确实没有资源。现补 loadError + 页面级持久 el-alert，空态文案诚实化。</p>
 */

const mocks = vi.hoisted(() => ({
  getResourceList: vi.fn(),
}))

vi.mock('@/api/resource', () => ({
  default: {
    getResourceList: mocks.getResourceList,
    uploadResource: vi.fn(),
    toggleVisibility: vi.fn(),
    deleteResource: vi.fn(),
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

import CourseResourcePanel from '@/components/CourseResourcePanel.vue'

function mountPanel() {
  return mount(CourseResourcePanel, {
    props: { courseId: 5 },
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        'el-empty': {
          props: ['description'],
          template: '<div class="empty-stub">{{ description }}</div>',
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

describe('CourseResourcePanel — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载失败：给出持久提示，空态不谎称「暂无资源」', async () => {
    mocks.getResourceList.mockRejectedValue(new Error('500'))
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('资源列表加载失败，请检查网络后重试')
    expect(wrapper.get('.empty-stub').text()).toContain('资源列表未能加载，请查看上方提示后重试')
    expect(wrapper.text()).not.toContain('暂无资源')
  })

  it('阴性对照：加载成功（空列表）时无提示，空态保留原语义', async () => {
    mocks.getResourceList.mockResolvedValue({ data: [] })
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.get('.empty-stub').text()).toContain('暂无资源')
    expect(wrapper.text()).not.toContain('未能加载')
  })

  it('加载成功：资源列表按真实数据渲染表格', async () => {
    mocks.getResourceList.mockResolvedValue({
      data: [
        { id: 1, fileName: '课件.pdf' },
        { id: 2, fileName: '视频.mp4' },
      ],
    })
    const wrapper = mountPanel()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.find('.empty-stub').exists()).toBe(false)
    expect(wrapper.get('.tbl-stub').text()).toBe('2')
  })

  it('失败后再成功：提示必须被清空（每次 load 复位）', async () => {
    mocks.getResourceList.mockRejectedValueOnce(new Error('500')).mockResolvedValue({ data: [] })
    const first = mountPanel()
    await flushPromises()
    expect(first.find('.alert-stub').exists()).toBe(true)

    const second = mountPanel()
    await flushPromises()

    expect(second.find('.alert-stub').exists()).toBe(false)
    expect(second.text()).not.toContain('资源列表加载失败')
    expect(second.get('.empty-stub').text()).toContain('暂无资源')
  })
})
