import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { defineComponent, h } from 'vue'

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
        'el-icon': true,
        'el-button': true,
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

/**
 * D2-7 主从表键盘选择回归（2026-10-16）
 *
 * <p>两张主从表此前仅 @row-click（鼠标是唯一入口，键盘用户无法选择行来驱动
 * 风险/事件下钻）。现补行内操作按钮作为键盘替代（Tab 可达 + Enter 激活），
 * 与成绩表 CgtGradeTable/TgGradeTable 同款口径——至此全仓 row-click 表格
 * 键盘替代 100% 覆盖。</p>
 *
 * <p>列桩注入固定行 FAKE_ROW：断言只关心「按钮点击 → 同一 handler → API 调用」
 * 这条接线；行数据链路（真实 row 的传递）已由上方既有用例覆盖。</p>
 */
const FAKE_ROW = {
  id: 1,
  examPaperId: 9,
  studentName: '甲',
  riskType: 'FACE_MISSING',
  proctorSessionId: 77,
}

const richTableStub = {
  props: ['data'],
  emits: ['row-click'],
  template: '<div class="table-stub"><slot /></div>',
}

const columnStub = defineComponent({
  props: { label: { type: String, default: '' } },
  setup(props, { slots }) {
    return () =>
      h(
        'div',
        { class: 'col-stub', 'data-label': props.label },
        slots.default?.({ row: FAKE_ROW }) ?? [],
      )
  },
})

/** el-button 桩：不声明 emits，@click 作为原生监听落到根 button（与真实单根组件同姿态） */
const buttonStub = { template: '<button type="button"><slot /></button>' }

function mountPageWithColumns() {
  return mount(CourseProctor, {
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        'el-table': richTableStub,
        'el-table-column': columnStub,
        'el-tag': true,
        'el-switch': true,
        'el-icon': true,
        'el-button': buttonStub,
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

function findButton(wrapper, text: string) {
  const btn = wrapper.findAll('button').find((b) => b.text().includes(text))
  if (!btn) throw new Error(`未找到按钮：${text}`)
  return btn
}

describe('CourseProctor — D2-7 主从表键盘选择（行内按钮）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getExamPaperById.mockResolvedValue({ data: { id: 9, title: '期中考试' } })
  })

  it('会话表行内「查看风险」按钮：点击等价行选择（加载风险 + 上下文 aria-label）', async () => {
    mocks.getActiveExams.mockResolvedValue({ data: [{ id: 1, examPaperId: 9 }] })
    mocks.getExamRisk.mockResolvedValue({ data: [] })
    const wrapper = mountPageWithColumns()
    await flushPromises()

    const btn = findButton(wrapper, '查看风险')
    // aria-label 带上下文（读屏下多个同名按钮须可区分）
    expect(btn.attributes('aria-label')).toContain('期中考试')

    await btn.trigger('click')
    await flushPromises()

    expect(mocks.getExamRisk).toHaveBeenCalledWith(FAKE_ROW.id)
  })

  it('风险表行内「查看事件」按钮：点击等价风险行选择（加载监考事件）', async () => {
    mocks.getActiveExams.mockResolvedValue({ data: [{ id: 1, examPaperId: 9 }] })
    mocks.getExamRisk.mockResolvedValue({ data: [{ id: 5, studentName: '甲' }] })
    mocks.getSessionEvents.mockResolvedValue({ data: [] })
    const wrapper = mountPageWithColumns()
    await flushPromises()

    // 风险表 v-if="selected"：先经会话表行内按钮进入选择态
    await findButton(wrapper, '查看风险').trigger('click')
    await flushPromises()

    const evtBtn = findButton(wrapper, '查看事件')
    expect(evtBtn.attributes('aria-label')).toContain('查看监考事件')

    await evtBtn.trigger('click')
    await flushPromises()

    expect(mocks.getSessionEvents).toHaveBeenCalledWith(FAKE_ROW.proctorSessionId)
  })
})
