import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CourseChapters.vue（章节结构）— 加载失败可见（2026-10-13）
 * <p>原实现章节树加载失败仅 logger.error 后静默 → 页面显示「暂无章节 / 点击右上角新增章节」，
 * 教师会误判该课程确实没有章节。现补页面级 `loadError` + 持久 el-alert + 空态文案诚实化。</p>
 */

const mocks = vi.hoisted(() => ({
  getChapterTree: vi.fn(),
  createChapter: vi.fn(),
  updateChapter: vi.fn(),
  deleteChapter: vi.fn(),
  importChaptersExcel: vi.fn(),
  route: { params: { id: '4007' } },
}))

vi.mock('@/api/teacher', () => ({ default: { getChapterTree: mocks.getChapterTree } }))
vi.mock('@/api/chapter', () => ({
  default: {
    createChapter: mocks.createChapter,
    updateChapter: mocks.updateChapter,
    deleteChapter: mocks.deleteChapter,
    importChaptersExcel: mocks.importChaptersExcel,
  },
}))
vi.mock('vue-router', () => ({ useRoute: () => mocks.route }))
vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    closeAll: vi.fn(),
  }),
  ElMessageBox: { confirm: vi.fn().mockResolvedValue('confirm') },
}))

import CourseChapters from '@/views/teacher/cockpit/CourseChapters.vue'

function mountPage() {
  return mount(CourseChapters, {
    shallow: true,
    global: {
      stubs: {
        // 自定义 el-alert 桩：渲染 title，使 wrapper.text() 可断言持久提示文案
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        'el-icon': true,
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

describe('CourseChapters — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('章节树加载失败时必须给出持久提示，且不得表现为「暂无章节」', async () => {
    mocks.getChapterTree.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    expect(mocks.getChapterTree).toHaveBeenCalledWith('4007')
    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('章节加载失败，请检查网络后重试')
    // 空态须诚实：显示「章节未能加载」而非「暂无章节」
    expect(wrapper.text()).toContain('章节未能加载')
    expect(wrapper.text()).not.toContain('暂无章节')
  })

  it('阴性对照：加载成功（空数组）时显示「暂无章节」且无失败提示', async () => {
    mocks.getChapterTree.mockResolvedValue({ data: [] })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.text()).toContain('暂无章节')
    expect(wrapper.text()).not.toContain('章节未能加载')
  })

  it('失败后再成功：提示必须被清空（每次 load 都复位，不是一次失败永久残留）', async () => {
    mocks.getChapterTree.mockRejectedValueOnce(new Error('500')).mockResolvedValue({ data: [] })
    const first = mountPage()
    await flushPromises()
    expect(first.find('.alert-stub').exists()).toBe(true)

    const second = mountPage()
    await flushPromises()

    expect(second.find('.alert-stub').exists()).toBe(false)
    expect(second.text()).toContain('暂无章节')
  })
})
