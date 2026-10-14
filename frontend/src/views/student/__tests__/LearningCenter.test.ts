import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * LearningCenter.vue（学生章节任务点学习中心）— 加载失败可见（2026-10-14，批次 16）
 *
 * <p>原实现 load 失败仅 logger + 瞬时 ElMessage：失败时进度读数谎报 0/0 0%，
 * 空态谎称「本课程暂无章节任务点」。现补 loadError + 页面级持久 el-alert，
 * 失败时三处读数改显 '--'，空态文案诚实化。</p>
 */

const mocks = vi.hoisted(() => ({
  getLearningCenter: vi.fn(),
  push: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '7' } }),
  useRouter: () => ({ push: mocks.push }),
}))
vi.mock('@/router', () => ({
  default: { beforeEach: vi.fn(), afterEach: vi.fn(), push: vi.fn() },
}))
vi.mock('@/api/student', () => ({ default: { getLearningCenter: mocks.getLearningCenter } }))
vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
  }),
}))

import LearningCenter from '@/views/student/course/LearningCenter.vue'

function mountPage() {
  return mount(LearningCenter, {
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        'el-empty': {
          props: ['description', 'imageSize'],
          template: '<div class="empty-stub">{{ description }}</div>',
        },
        'el-page-header': {
          template: '<div class="ph-stub"><slot name="content" /></div>',
          props: ['title'],
        },
        'el-progress': {
          props: ['percentage', 'strokeWidth', 'color'],
          template: '<div class="prog-stub">{{ percentage }}</div>',
        },
        'el-tag': { template: '<span><slot /></span>', props: ['size', 'type', 'effect'] },
        ChapterTree: { props: ['nodes'], template: '<div class="tree-stub" />' },
        TaskDrawer: { props: ['task', 'courseId'], template: '<div class="drawer-stub" />' },
        'el-icon': true,
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

describe('LearningCenter — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载失败：给出持久提示，三处读数改显 --，空态不谎称「暂无章节任务点」', async () => {
    mocks.getLearningCenter.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('学习中心加载失败，请检查网络后重试')
    expect(wrapper.get('.lc-progress-num .done').text()).toBe('--')
    expect(wrapper.get('.lc-progress-num .total').text()).toBe('--')
    expect(wrapper.get('.lc-percent').text()).toBe('--')
    expect(wrapper.get('.empty-stub').text()).toContain(
      '学习中心内容未能加载，请查看上方提示后重试',
    )
    expect(wrapper.text()).not.toContain('本课程暂无章节任务点')
  })

  it('阴性对照：加载成功（空章节）时无提示，空态保留原语义且读数为 0', async () => {
    mocks.getLearningCenter.mockResolvedValue({
      data: { courseName: '测试课程', chapters: [], totalTasks: 0, completedTasks: 0, completionPercent: 0 },
    })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.get('.empty-stub').text()).toContain('本课程暂无章节任务点')
    expect(wrapper.text()).not.toContain('未能加载')
    expect(wrapper.get('.lc-progress-num .done').text()).toBe('0')
    expect(wrapper.get('.lc-percent').text()).toBe('0%')
  })

  it('加载成功：三处读数按真实进度渲染', async () => {
    mocks.getLearningCenter.mockResolvedValue({
      data: {
        courseName: '测试课程',
        chapters: [{ id: 1, title: '第一章', tasks: [] }],
        totalTasks: 4,
        completedTasks: 2,
        completionPercent: 50,
      },
    })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.get('.lc-progress-num .done').text()).toBe('2')
    expect(wrapper.get('.lc-progress-num .total').text()).toBe('4')
    expect(wrapper.get('.lc-percent').text()).toBe('50%')
    expect(wrapper.find('.alert-stub').exists()).toBe(false)
  })

  it('失败后再成功：提示必须被清空（每次 load 复位）', async () => {
    mocks.getLearningCenter.mockRejectedValueOnce(new Error('500')).mockResolvedValue({
      data: { courseName: '测试课程', chapters: [], totalTasks: 0, completedTasks: 0, completionPercent: 0 },
    })
    const first = mountPage()
    await flushPromises()
    expect(first.find('.alert-stub').exists()).toBe(true)

    const second = mountPage()
    await flushPromises()

    expect(second.find('.alert-stub').exists()).toBe(false)
    expect(second.text()).not.toContain('学习中心加载失败')
    expect(second.get('.lc-progress-num .done').text()).toBe('0')
  })
})
