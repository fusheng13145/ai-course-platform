import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CourseProgressTab.vue + useCourseProgressTab —— 加载失败可见（2026-10-14，批次 17）
 *
 * <p>原实现 loadData 失败仅 logger.warn 静默：失败时统计卡与环形图照常显示 0，
 * 空态谎称「暂无章节数据」。现补独立 loadError、页面级持久 el-alert，
 * 统计卡失败时读数改显 '--'，空态文案诚实化。</p>
 */

const mocks = vi.hoisted(() => ({
  getChapterTree: vi.fn(),
}))

vi.mock('@/api/teacher', () => ({
  default: { getChapterTree: mocks.getChapterTree },
}))

import CourseProgressTab from '@/components/CourseProgressTab.vue'
import CptStatsRow from '@/components/course/CptStatsRow.vue'

const HONEST_EMPTY = '章节进度未能加载，请查看上方提示后重试'
const DEFAULT_EMPTY = '暂无章节数据'

function mountTab() {
  return mount(CourseProgressTab, {
    props: { courseId: 5 },
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        CptStatsRow: {
          props: [
            'overallProgress',
            'completedChapters',
            'totalChapters',
            'totalTasks',
            'avgSubmissionRate',
            'loadFailed',
          ],
          template: '<div class="stats-stub" :data-failed="loadFailed ? \'true\' : \'false\'" />',
        },
        CptChapterTable: {
          props: ['loading', 'chapterList', 'emptyText'],
          template: '<div class="table-stub" :data-len="(chapterList || []).length">{{ emptyText }}</div>',
        },
        CptRingChart: true,
        CptCompareCards: true,
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

describe('CourseProgressTab — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载失败：给出持久提示，统计卡标失败，空态不谎称「暂无章节数据」', async () => {
    mocks.getChapterTree.mockRejectedValue(new Error('500'))
    const wrapper = mountTab()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('课程进度数据加载失败，请检查网络后重试')
    expect(wrapper.get('.stats-stub').attributes('data-failed')).toBe('true')
    expect(wrapper.get('.table-stub').text()).toContain(HONEST_EMPTY)
    expect(wrapper.text()).not.toContain(DEFAULT_EMPTY)
  })

  it('阴性对照：加载成功（空章节）时无提示，统计卡正常，空态保留原语义', async () => {
    mocks.getChapterTree.mockResolvedValue({ data: [] })
    const wrapper = mountTab()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.get('.stats-stub').attributes('data-failed')).toBe('false')
    expect(wrapper.get('.table-stub').text()).toContain(DEFAULT_EMPTY)
    expect(wrapper.text()).not.toContain('未能加载')
  })

  it('加载成功：章节数据按真实列表渲染', async () => {
    mocks.getChapterTree.mockResolvedValue({
      data: [{ id: 1, title: '第一章', isPublished: true, taskCount: 3 }],
    })
    const wrapper = mountTab()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.get('.table-stub').attributes('data-len')).toBe('1')
    expect(wrapper.get('.table-stub').text()).toContain(DEFAULT_EMPTY)
  })

  it('失败后再成功：提示与失败标记必须被清空（每次 load 复位）', async () => {
    mocks.getChapterTree.mockRejectedValueOnce(new Error('500')).mockResolvedValue({ data: [] })
    const first = mountTab()
    await flushPromises()
    expect(first.find('.alert-stub').exists()).toBe(true)
    expect(first.get('.stats-stub').attributes('data-failed')).toBe('true')

    const second = mountTab()
    await flushPromises()

    expect(second.find('.alert-stub').exists()).toBe(false)
    expect(second.get('.stats-stub').attributes('data-failed')).toBe('false')
    expect(second.text()).not.toContain('课程进度数据加载失败')
  })
})

describe('CptStatsRow — 统计卡失败显 --', () => {
  it('loadFailed 时四个读数均显 --', () => {
    const wrapper = mount(CptStatsRow, {
      props: {
        loadFailed: true,
        overallProgress: 30,
        completedChapters: 1,
        totalChapters: 2,
        totalTasks: 5,
        avgSubmissionRate: 40,
      },
    })
    expect((wrapper.text().match(/--/g) || []).length).toBe(4)
    expect(wrapper.text()).not.toContain('30%')
    expect(wrapper.text()).not.toContain('40%')
  })

  it('阴性对照：未失败时按真实读数渲染', () => {
    const wrapper = mount(CptStatsRow, {
      props: {
        overallProgress: 30,
        completedChapters: 1,
        totalChapters: 2,
        totalTasks: 5,
        avgSubmissionRate: 40,
      },
    })
    expect(wrapper.text()).toContain('30%')
    expect(wrapper.text()).toContain('1/2')
    expect(wrapper.text()).toContain('5')
    expect(wrapper.text()).toContain('40%')
    expect(wrapper.text()).not.toContain('--')
  })
})
