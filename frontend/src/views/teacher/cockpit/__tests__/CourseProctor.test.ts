import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CourseProctor.vue（在线监考）— 加载失败可见（2026-10-13）
 * <p>会话 / 风险 / 事件三路加载失败原先仅 logger.error 后静默置空 →
 * 页面显示「暂无进行中的考试 / 该会话暂无风险记录 / 该风险暂无监考事件」，属误导。</p>
 *
 * <p>三路失败信号各自独立（sessionsError / riskError / eventsError）再聚合为 loadError；
 * 本用例分别锁定「主列表失败」与「会话成功但风险失败」两路。</p>
 */

const mocks = vi.hoisted(() => ({
  getActiveExams: vi.fn(),
  getExamPaperById: vi.fn(),
  getExamRisk: vi.fn(),
  getSessionEvents: vi.fn(),
  getStudentSession: vi.fn(),
  route: { params: { id: '4007' } },
}))

vi.mock('@/api/exam', () => ({
  default: {
    getActiveExams: mocks.getActiveExams,
    getExamPaperById: mocks.getExamPaperById,
  },
}))
vi.mock('@/api/proctor', () => ({
  default: {
    getExamRisk: mocks.getExamRisk,
    getSessionEvents: mocks.getSessionEvents,
    getStudentSession: mocks.getStudentSession,
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
}))

import CourseProctor from '@/views/teacher/cockpit/CourseProctor.vue'

/** 可点击的 el-table 桩：点击按钮即 emit row-click，用于驱动「选会话 → 加载风险」链路 */
const tableStub = {
  props: ['data', 'emptyText'],
  emits: ['row-click'],
  template: `<div class="table-stub"><button class="row-click" @click="$emit('row-click', (data && data[0]) || {})"></button></div>`,
}

function mountPage() {
  return mount(CourseProctor, {
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        'el-table': tableStub,
        'el-table-column': true,
        'el-tag': true,
        'el-switch': true,
        'el-empty': true,
        'el-icon': true,
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

describe('CourseProctor — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getExamPaperById.mockResolvedValue({ data: { id: 9, title: '期中考试' } })
  })

  it('进行中考试会话加载失败时必须给出持久提示，且不得表现为「暂无进行中的考试」', async () => {
    mocks.getActiveExams.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    // courseId 非空 → 不会渲染「未关联到具体课程」的 info alert，唯一 alert 即失败提示
    expect(wrapper.find('.alert-stub').text()).toBe('进行中考试会话加载失败，请检查网络后重试')
  })

  it('会话成功但风险加载失败时：仍必须报出风险失败（两路信号不得互相串味）', async () => {
    mocks.getActiveExams.mockResolvedValue({ data: [{ id: 1, examPaperId: 9 }] })
    mocks.getExamRisk.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()
    expect(wrapper.find('.alert-stub').exists()).toBe(false)

    await wrapper.find('.row-click').trigger('click')
    await flushPromises()

    expect(wrapper.find('.alert-stub').text()).toBe('风险记录加载失败，请检查网络后重试')
  })

  it('阴性对照：会话加载成功时不得出现失败提示', async () => {
    mocks.getActiveExams.mockResolvedValue({ data: [] })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
  })
})
