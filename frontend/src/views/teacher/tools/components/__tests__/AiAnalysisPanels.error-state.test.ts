/**
 * AI 分析面板失败反馈双态测试（D2-1）
 *
 * <p>背景：AcAdvicePanel / AcWeakPanel 原实现 catch 内只 `logger.error`，
 * 失败后页面照常显示「暂无学习建议」「暂无薄弱点数据」——把「加载失败」
 * 伪装成「无数据」。本测试锁定：失败必须有持久 alert + 空态双态文案，
 * 且不得渲染成纯「无数据」文案。</p>
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AcAdvicePanel from '@/views/teacher/tools/components/AcAdvicePanel.vue'
import AcWeakPanel from '@/views/teacher/tools/components/AcWeakPanel.vue'

const learningAdvice = vi.fn()
const weakPointsAnalysis = vi.fn()

vi.mock('@/api/ai', () => ({
  default: {
    learningAdvice: (...a) => learningAdvice(...a),
    weakPointsAnalysis: (...a) => weakPointsAnalysis(...a),
  },
}))

vi.mock('@/utils/logger', () => ({
  default: { error: vi.fn(), warn: vi.fn(), info: vi.fn() },
}))

/** 统一桩：alert / EmptyState / 图标 / 表格 */
const stubs = {
  'el-alert': {
    props: ['title', 'type'],
    template: '<div class="alert-stub" :data-type="type">{{ title }}</div>',
  },
  'el-icon': { template: '<i><slot /></i>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-table': { props: ['data'], template: '<div class="table-stub"><slot /></div>' },
  'el-table-column': true,
  'el-progress': true,
  EmptyState: {
    props: ['title', 'desc', 'icon', 'size'],
    template: '<div class="empty-stub">{{ title }}</div>',
  },
  Lightning: true,
  Warning: true,
}

function mountPanel(component) {
  return mount(component, {
    props: { courseId: 7 },
    global: { stubs, directives: { loading: { mounted() {}, updated() {} } } },
  })
}

describe('AcAdvicePanel 失败反馈双态（D2-1）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载失败：渲染持久 error alert，不静默', async () => {
    learningAdvice.mockRejectedValue(new Error('boom'))
    const wrapper = mountPanel(AcAdvicePanel)
    await flushPromises()

    const alert = wrapper.find('.alert-stub')
    expect(alert.exists()).toBe(true)
    expect(alert.attributes('data-type')).toBe('error')
    expect(alert.text()).toContain('学习建议加载失败')
    expect(wrapper.find('.empty-stub').text()).toContain('请查看上方提示后重试')
  })

  it('加载成功但无数据：纯「暂无学习建议」，无 alert', async () => {
    learningAdvice.mockResolvedValue({ data: { data: { summary: '', items: [] } } })
    const wrapper = mountPanel(AcAdvicePanel)
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.find('.empty-stub').text()).toBe('暂无学习建议')
  })

  it('加载成功且有数据：渲染建议条目，不显示空态', async () => {
    learningAdvice.mockResolvedValue({
      data: { data: { summary: '总体良好', items: [{ level: 'HIGH', title: '矩阵运算', detail: '偏弱' }] } },
    })
    const wrapper = mountPanel(AcAdvicePanel)
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.find('.empty-stub').exists()).toBe(false)
    expect(wrapper.text()).toContain('矩阵运算')
  })
})

describe('AcWeakPanel 失败反馈双态（D2-1）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('加载失败：渲染 error alert 且空态带失败提示', async () => {
    weakPointsAnalysis.mockRejectedValue(new Error('boom'))
    const wrapper = mountPanel(AcWeakPanel)
    await flushPromises()

    const alert = wrapper.find('.alert-stub')
    expect(alert.exists()).toBe(true)
    expect(alert.attributes('data-type')).toBe('error')
    expect(alert.text()).toContain('薄弱点分析加载失败')
    expect(wrapper.find('.empty-stub').text()).toContain('请查看上方提示后重试')
  })

  it('加载成功但无数据：纯「暂无薄弱点数据」，无 alert', async () => {
    weakPointsAnalysis.mockResolvedValue({ data: { data: { weakPoints: [] } } })
    const wrapper = mountPanel(AcWeakPanel)
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.find('.empty-stub').text()).toBe('暂无薄弱点数据')
  })
})