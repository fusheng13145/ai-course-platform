import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CourseQuestionBank.vue（课程题库）— 加载失败可见（2026-10-14，批次 16）
 *
 * <p>原实现 load 失败仅 logger.error 静默：失败时统计卡（题目总数/待审核/已审核）
 * 谎报 0，空态谎称「该课程暂无题目」。现补 loadError + 页面级持久 el-alert，
 * 失败时统计卡改显 '--'，CqbQuestionTable 经 emptyText prop 透传诚实空态文案。</p>
 */

const mocks = vi.hoisted(() => ({
  getQuestionList: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '3' } }),
  useRouter: () => ({ push: vi.fn() }),
}))
vi.mock('@/router', () => ({
  default: { beforeEach: vi.fn(), afterEach: vi.fn(), push: vi.fn() },
}))
vi.mock('@/api/question', () => ({
  default: {
    getQuestionList: mocks.getQuestionList,
    createQuestion: vi.fn(),
    updateQuestion: vi.fn(),
    reviewQuestion: vi.fn(),
    copyQuestion: vi.fn(),
    deleteQuestion: vi.fn(),
    generateByAI: vi.fn(),
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

import CourseQuestionBank from '@/views/teacher/cockpit/CourseQuestionBank.vue'
import StatCard from '@/components/ui/StatCard.vue'

function mountPage() {
  return mount(CourseQuestionBank, {
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        PageHeader: {
          props: ['title', 'subtitle', 'icon', 'iconBg'],
          template: '<div><slot /></div>',
        },
        StatCard: {
          props: ['label', 'value', 'icon', 'tone'],
          template: '<div class="sc-stub">{{ label }}|{{ value }}</div>',
        },
        CqbQuestionTable: {
          props: [
            'loading',
            'list',
            'filters',
            'typeOpts',
            'diffOpts',
            'total',
            'page',
            'size',
            'emptyText',
          ],
          template:
            '<div class="table-stub"><span v-if="!list.length" class="empty">{{ emptyText }}</span><span v-else class="count">{{ list.length }}</span></div>',
        },
        'el-icon': true,
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

function statValue(wrapper: Awaited<ReturnType<typeof mountPage>>, label: string) {
  return wrapper.findAllComponents(StatCard).find((c) => c.props('label') === label)?.props('value')
}

describe('CourseQuestionBank — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载失败：给出持久提示，统计卡改显 --，空态不谎称「该课程暂无题目」', async () => {
    mocks.getQuestionList.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.text()).toContain('题库列表加载失败，请检查网络后重试')
    expect(statValue(wrapper, '题目总数')).toBe('--')
    expect(statValue(wrapper, '待审核')).toBe('--')
    expect(statValue(wrapper, '已审核')).toBe('--')
    expect(wrapper.get('.table-stub .empty').text()).toContain(
      '题库列表未能加载，请查看上方提示后重试',
    )
    expect(wrapper.text()).not.toContain('该课程暂无题目')
    // 题型种类为前端常量（constants/questionTypes 实库契约，2026-10-26 起 5 种、
    // 已删实库不存在的 CODE），不受加载失败影响
    expect(statValue(wrapper, '题型种类')).toBe(5)
  })

  it('阴性对照：加载成功（空列表）时无提示，空态保留原引导语义且统计为 0', async () => {
    mocks.getQuestionList.mockResolvedValue({ data: { total: 0, list: [] } })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.get('.table-stub .empty').text()).toContain(
      '该课程暂无题目，点击右上角「新建题目」或「AI 生成」',
    )
    expect(wrapper.text()).not.toContain('未能加载')
    expect(statValue(wrapper, '题目总数')).toBe(0)
  })

  it('加载成功：统计卡按真实列表计数（审核态取自 isActive）', async () => {
    mocks.getQuestionList.mockResolvedValue({
      data: {
        total: 2,
        list: [
          { id: 1, type: 'SINGLE_CHOICE', difficulty: 'easy', isActive: true },
          { id: 2, type: 'TRUE_FALSE', difficulty: 'hard', isActive: false },
        ],
      },
    })
    const wrapper = mountPage()
    await flushPromises()

    expect(statValue(wrapper, '题目总数')).toBe(2)
    expect(statValue(wrapper, '待审核')).toBe(1)
    expect(statValue(wrapper, '已审核')).toBe(1)
    expect(wrapper.find('.alert-stub').exists()).toBe(false)
  })

  it('失败后再成功：提示必须被清空（每次 load 复位）', async () => {
    mocks.getQuestionList
      .mockRejectedValueOnce(new Error('500'))
      .mockResolvedValue({ data: { total: 0, list: [] } })
    const first = mountPage()
    await flushPromises()
    expect(first.find('.alert-stub').exists()).toBe(true)

    const second = mountPage()
    await flushPromises()

    expect(second.find('.alert-stub').exists()).toBe(false)
    expect(second.text()).not.toContain('题库列表加载失败')
    expect(statValue(second, '题目总数')).toBe(0)
  })
})
