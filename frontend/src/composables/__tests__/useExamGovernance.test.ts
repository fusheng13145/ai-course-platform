import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount } from '@vue/test-utils'

/**
 * useExamGovernance（防作弊治理看板逻辑）单元测试
 *
 * <p>本文件此前 0% 覆盖，且承载 P1-9 缺陷修复：旧实现用 `Number(t.high ?? 6)` 直接
 * 写进 reactive 状态，后端返回 "abc" 时 NaN 污染界面；返回 high=2/medium=8（阈值倒置）
 * 时照样 PUT 回后端，风险分级完全反向；非管理员调用 saveThresholds 的返回值也不确定。
 * 这里把「校验/归一化纯函数 + 加载回退 + 保存短路」三段全部钉成回归用例。</p>
 *
 * <p>另锁定监考会话的并行 enrich 降级：单个会话风险接口失败只能让该会话降级为
 * LOW/0，不能把整块看板清空。</p>
 */

const mocks = vi.hoisted(() => ({
  getActiveExams: vi.fn(),
  getExamRisk: vi.fn(),
  getStudentSession: vi.fn(),
  getSessionEvents: vi.fn(),
  getThresholds: vi.fn(),
  updateThresholds: vi.fn(),
  reviewEvent: vi.fn(),
  // ElMessage 在源码里既被当函数调用又被当对象使用，用「可调用函数 + 挂载方法」建模
  message: Object.assign(vi.fn(), {
    info: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn(),
    closeAll: vi.fn(),
  }),
  // 角色可控：useAuthStore 的 mock 以 getter 暴露，挂载时求值
  role: 'ADMIN',
}))

vi.mock('@/api/exam', () => ({ default: { getActiveExams: mocks.getActiveExams } }))
vi.mock('@/api/proctor', () => ({
  default: {
    getExamRisk: mocks.getExamRisk,
    getStudentSession: mocks.getStudentSession,
    getSessionEvents: mocks.getSessionEvents,
    getThresholds: mocks.getThresholds,
    updateThresholds: mocks.updateThresholds,
    reviewEvent: mocks.reviewEvent,
  },
}))
vi.mock('element-plus', () => ({ ElMessage: mocks.message }))
vi.mock('@/stores/auth', () => ({
  useAuthStore: () => ({
    get userRole() {
      return mocks.role
    },
  }),
}))

import {
  useExamGovernance,
  validateThresholds,
  normalizeThresholds,
} from '@/composables/useExamGovernance'

type Session = {
  id: number
  studentId?: number
  riskLevel?: string
  status?: string
  eventCount?: number
  riskScore?: number
}

function withSetup() {
  let state!: ReturnType<typeof useExamGovernance>
  mount(
    defineComponent({
      setup() {
        state = useExamGovernance()
        return () => h('div')
      },
    }),
  )
  return { state }
}

/** sessions/list 等 ref 由 `ref([])` 推导为 never[]，测试内按声明类型读取 */
const rows = <T>(v: unknown) => v as unknown as T[]

const okThresholds = { high: 6, medium: 3, weightLow: 1, weightMedium: 2, weightHigh: 3 }

describe('useExamGovernance — validateThresholds 纯函数（P1-9）', () => {
  it('合法阈值通过校验且不带错误文案', () => {
    expect(validateThresholds(okThresholds)).toEqual({ valid: true, message: '' })
    // 字符串数字会被 Number 接受，属于后端常见返回形态
    expect(
      validateThresholds({
        high: '8',
        medium: '4',
        weightLow: '1',
        weightMedium: '2',
        weightHigh: '3',
      }).valid,
    ).toBe(true)
  })

  it('high 与 medium 相等或倒置一律拒绝（旧实现可原样 PUT 回去）', () => {
    expect(validateThresholds({ ...okThresholds, high: 3, medium: 3 }).message).toBe(
      '高风险阈值必须严格大于中风险阈值',
    )
    expect(validateThresholds({ ...okThresholds, high: 2, medium: 8 }).valid).toBe(false)
  })

  it('非有限数值（NaN/Infinity/null/字符串/undefined）逐项拒绝并报出字段名', () => {
    expect(validateThresholds({ ...okThresholds, high: 'abc' })).toEqual({
      valid: false,
      message: '阈值 high 必须为有效数字',
    })
    expect(validateThresholds({ ...okThresholds, medium: NaN }).message).toBe(
      '阈值 medium 必须为有效数字',
    )
    expect(validateThresholds({ ...okThresholds, weightHigh: undefined }).message).toBe(
      '阈值 weightHigh 必须为有效数字',
    )
    expect(validateThresholds({ ...okThresholds, high: Infinity }).message).toBe(
      '阈值 high 必须为有效数字',
    )
  })

  it('权重必须为正数（0 与负数都拒绝）', () => {
    expect(validateThresholds({ ...okThresholds, weightLow: 0 }).message).toBe(
      '权重 weightLow 必须大于 0',
    )
    expect(validateThresholds({ ...okThresholds, weightMedium: -2 }).message).toBe(
      '权重 weightMedium 必须大于 0',
    )
  })

  it('入参非对象时给出格式错误而不是抛异常', () => {
    expect(validateThresholds(null)).toEqual({ valid: false, message: '阈值数据格式不正确' })
    expect(validateThresholds('abc')).toEqual({ valid: false, message: '阈值数据格式不正确' })
  })
})

describe('useExamGovernance — normalizeThresholds 纯函数（P1-9）', () => {
  it('合法字段保留、非法字段逐项回退默认值，绝不产出 NaN', () => {
    expect(
      normalizeThresholds({
        high: '9',
        medium: 4,
        weightLow: 'abc',
        weightMedium: undefined,
        weightHigh: {},
      }),
    ).toEqual({ high: 9, medium: 4, weightLow: 1, weightMedium: 2, weightHigh: 3 })
    expect(normalizeThresholds({ high: NaN, medium: Infinity })).toMatchObject({
      high: 6,
      medium: 3,
    })
  })

  it('null / 空串字段视为缺失并回退默认值（不再被 Number 折成合法的 0）', () => {
    // 缺陷回归（2026-10-04）：`Number(null)` 与 `Number('')` 都等于 0 且是有限数，
    // 旧实现让「后端字段没给」变成 high=0/medium=0 —— 所有会话一律判为高风险，
    // 且 validateThresholds 还会因为 0<=0 之外的原因通不过而看不出问题。
    expect(normalizeThresholds({ high: null, medium: '', weightLow: '  ' })).toEqual({
      high: 6,
      medium: 3,
      weightLow: 1,
      weightMedium: 2,
      weightHigh: 3,
    })
    // 显式的 0 仍是用户主动配置的值，不该被当成缺失
    expect(normalizeThresholds({ high: 0 })).toMatchObject({ high: 0 })
  })

  it('入参缺失（undefined/null/非对象）时整套回退默认值', () => {
    const fallback = { high: 6, medium: 3, weightLow: 1, weightMedium: 2, weightHigh: 3 }
    expect(normalizeThresholds(undefined)).toEqual(fallback)
    expect(normalizeThresholds(null)).toEqual(fallback)
    expect(normalizeThresholds('x')).toEqual(fallback)
  })
})

describe('useExamGovernance — 阈值加载与保存（P1-9）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.role = 'ADMIN'
    mocks.getActiveExams.mockResolvedValue({ data: [] })
    mocks.getThresholds.mockResolvedValue({ data: { ...okThresholds } })
    mocks.getExamRisk.mockResolvedValue({ data: [] })
  })

  it('挂载即并行拉取会话与阈值', async () => {
    withSetup()
    await vi.waitFor(() => expect(mocks.getActiveExams).toHaveBeenCalled())
    expect(mocks.getThresholds).toHaveBeenCalled()
  })

  it('后端阈值含非法值时逐字段回退，状态里不出现 NaN', async () => {
    mocks.getThresholds.mockResolvedValue({
      data: { high: 'abc', medium: 2, weightLow: undefined, weightHigh: Infinity },
    })
    const { state } = withSetup()
    await state.loadThresholds()

    Object.values(state.thresholds).forEach((v) => {
      expect(Number.isFinite(v as number)).toBe(true)
    })
    // 非法项回退默认，合法项（medium=2）保留后端值
    expect(state.thresholds.high).toBe(6)
    expect(state.thresholds.medium).toBe(2)
    expect(state.thresholds.weightLow).toBe(1)
    expect(state.thresholds.weightHigh).toBe(3)
  })

  it('后端返回倒置阈值时先如实入状态，再在保存环节被拦截（加载/保存双闸门）', async () => {
    mocks.getThresholds.mockResolvedValue({ data: { ...okThresholds, high: 2, medium: 8 } })
    const { state } = withSetup()
    await state.loadThresholds()
    expect(state.thresholds.high).toBe(2)

    await expect(state.saveThresholds()).resolves.toBe(false)
    expect(mocks.updateThresholds).not.toHaveBeenCalled()
  })

  it('阈值接口失败时静默保持默认值（catch 转 null，不抛错）', async () => {
    mocks.getThresholds.mockRejectedValue(new Error('500'))
    const { state } = withSetup()

    await expect(state.loadThresholds()).resolves.toBeUndefined()
    expect(state.thresholds).toEqual(okThresholds)
  })

  it('后端返回非对象信封时不覆盖已有阈值', async () => {
    mocks.getThresholds.mockResolvedValue({ data: 'oops' })
    const { state } = withSetup()
    state.thresholds.high = 10
    await state.loadThresholds()
    expect(state.thresholds.high).toBe(10)
  })

  it('非管理员保存：返回 false 且零 PUT 请求', async () => {
    mocks.role = 'TEACHER'
    const { state } = withSetup()

    await expect(state.saveThresholds()).resolves.toBe(false)
    expect(mocks.updateThresholds).not.toHaveBeenCalled()
    expect(mocks.message.error).not.toHaveBeenCalled()
  })

  it('倒置阈值保存：不发 PUT，直接把校验文案提示给用户', async () => {
    const { state } = withSetup()
    Object.assign(state.thresholds, { high: 2, medium: 8 })

    await expect(state.saveThresholds()).resolves.toBe(false)
    expect(mocks.updateThresholds).not.toHaveBeenCalled()
    expect(mocks.message.error).toHaveBeenCalledWith('高风险阈值必须严格大于中风险阈值')
  })

  it('NaN 注入后保存同样被短路拦截（旧实现会 PUT 出 NaN）', async () => {
    const { state } = withSetup()
    Object.assign(state.thresholds, { weightMedium: Number('abc') })

    await expect(state.saveThresholds()).resolves.toBe(false)
    expect(mocks.updateThresholds).not.toHaveBeenCalled()
    expect(mocks.message.error).toHaveBeenCalledWith('阈值 weightMedium 必须为有效数字')

    // 权重为 0/负数属于「数值合法但语义非法」，也必须拦下
    Object.assign(state.thresholds, { weightMedium: -1 })
    await expect(state.saveThresholds()).resolves.toBe(false)
    expect(mocks.message.error).toHaveBeenLastCalledWith('权重 weightMedium 必须大于 0')
  })

  it('合法阈值：PUT 载荷恰好是五个字段并返回 true', async () => {
    mocks.updateThresholds.mockResolvedValue({ code: 200 })
    const { state } = withSetup()

    await expect(state.saveThresholds()).resolves.toBe(true)
    expect(mocks.updateThresholds).toHaveBeenCalledWith(okThresholds)
    expect(mocks.message.success).toHaveBeenCalledWith('风险判定阈值已保存')
    expect(state.thrSaving.value).toBe(false)
  })

  it('PUT 失败：返回 false、提示带后端 message、结束保存态', async () => {
    mocks.updateThresholds.mockRejectedValue({ response: { data: { message: '仅管理员可配置' } } })
    const { state } = withSetup()

    await expect(state.saveThresholds()).resolves.toBe(false)
    expect(mocks.message.error).toHaveBeenCalledWith('保存阈值失败：仅管理员可配置')
    expect(state.thrSaving.value).toBe(false)
  })
})

describe('useExamGovernance — 监考会话看板', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.role = 'ADMIN'
    mocks.getThresholds.mockResolvedValue({ data: { ...okThresholds } })
    mocks.getActiveExams.mockResolvedValue({
      data: [
        { id: 1, studentId: 11, status: 'IN_PROGRESS' },
        { id: 2, studentId: 12, status: 'IN_PROGRESS' },
      ],
    })
  })

  it('并行 enrich：单个会话风险接口失败只降级该会话，不清空看板', async () => {
    mocks.getExamRisk.mockImplementation((id: number) =>
      id === 1
        ? Promise.resolve({
            data: [{ riskScore: 12, riskLevel: 'HIGH', eventCount: 4, status: 'FLAGGED' }],
          })
        : Promise.reject(new Error('500')),
    )
    const { state } = withSetup()
    await state.loadSessions()

    const list = rows<Session>(state.sessions.value)
    expect(list).toHaveLength(2)
    expect(list[0]).toMatchObject({
      riskScore: 12,
      riskLevel: 'HIGH',
      eventCount: 4,
      status: 'FLAGGED',
    })
    // 失败项必须补齐安全默认值，否则 highRiskCount 等派生统计整体失真
    expect(list[1]).toMatchObject({ riskScore: 0, riskLevel: 'LOW', eventCount: 0, typeCounts: {} })
    expect(state.highRiskCount.value).toBe(1)
    expect(state.flaggedCount.value).toBe(1)
    expect(state.totalEvents.value).toBe(4)
  })

  it('风险接口返回非数组信封时按空风险处理', async () => {
    mocks.getActiveExams.mockResolvedValue({ data: [{ id: 7 }] })
    mocks.getExamRisk.mockResolvedValue({ data: { unexpected: 'shape' } })
    const { state } = withSetup()
    await state.loadSessions()

    expect(rows<Session>(state.sessions.value)[0]).toMatchObject({
      riskLevel: 'LOW',
      riskScore: 0,
      eventCount: 0,
    })
  })

  it('会话接口返回非数组时看板置空而不是抛错', async () => {
    mocks.getActiveExams.mockResolvedValue({ data: { records: [] } })
    const { state } = withSetup()
    await state.loadSessions()

    expect(state.sessions.value).toEqual([])
    expect(state.selectedId.value).toBeNull()
  })

  it('会话接口失败：报错提示 + 清空 + 结束 loading', async () => {
    mocks.getActiveExams.mockRejectedValue(new Error('500'))
    const { state } = withSetup()
    await state.loadSessions()

    expect(state.sessions.value).toEqual([])
    expect(mocks.message.error).toHaveBeenCalledWith('加载监考会话失败')
    expect(state.loading.value).toBe(false)
  })

  it('首次加载自动选中首个会话，selectSession 切换后 current 跟随', async () => {
    mocks.getExamRisk.mockResolvedValue({ data: [{ riskLevel: 'MEDIUM', eventCount: 2 }] })
    const { state } = withSetup()
    await state.loadSessions()

    expect(state.selectedId.value).toBe(1)
    expect(state.current.value).toMatchObject({ id: 1 })

    state.selectSession({ id: 2 } as never)
    expect(state.current.value).toMatchObject({ id: 2 })
  })

  it('openEvents 先用学生映射监考会话 id，再按该 id 拉事件', async () => {
    mocks.getStudentSession.mockResolvedValue({ data: { id: 900 } })
    mocks.getSessionEvents.mockResolvedValue({ data: [{ id: 1, eventType: 'TAB_SWITCH' }] })
    const { state } = withSetup()
    await state.openEvents({ id: 5, studentId: 11 })

    expect(mocks.getStudentSession).toHaveBeenCalledWith(5, 11)
    // 事件接口必须拿监考会话主键（900），而不是考试会话 id（5）
    expect(mocks.getSessionEvents).toHaveBeenCalledWith(900)
    expect(state.events.value).toHaveLength(1)
    expect(state.drawer.value).toBe(true)
    expect(state.eventsLoading.value).toBe(false)
  })

  it('openEvents：学生映射失败时事件置空且不发事件请求', async () => {
    mocks.getStudentSession.mockRejectedValue(new Error('404'))
    const { state } = withSetup()
    await state.openEvents({ id: 5, studentId: 11 })

    expect(state.events.value).toEqual([])
    expect(mocks.getSessionEvents).not.toHaveBeenCalled()
    expect(state.eventsLoading.value).toBe(false)
  })

  it('openEvents：事件接口返回非数组时降级为空列表', async () => {
    mocks.getStudentSession.mockResolvedValue({ id: 3 })
    mocks.getSessionEvents.mockResolvedValue({ data: { list: [] } })
    const { state } = withSetup()
    await state.openEvents({ id: 5, studentId: 11 })

    expect(state.events.value).toEqual([])
  })

  it('submitReview 成功：合并后端返回并把按钮状态复位', async () => {
    mocks.reviewEvent.mockResolvedValue({
      data: { status: 'REVIEWED', reviewNote: '教师确认该违规事件成立' },
    })
    const { state } = withSetup()
    const e: Record<string, unknown> = { id: 42, status: 'PENDING' }

    await state.submitReview(e as never, 'REVIEWED')

    expect(mocks.reviewEvent).toHaveBeenCalledWith(42, {
      status: 'REVIEWED',
      reviewNote: '教师确认该违规事件成立',
    })
    expect(e.status).toBe('REVIEWED')
    expect(mocks.message.success).toHaveBeenCalledWith('已确认违规')
    expect(state.reviewingId.value).toBeNull()
    expect(state.reviewAction.value).toBe('')
  })

  it('submitReview 失败：提示后端 message 且不清空原状态', async () => {
    mocks.reviewEvent.mockRejectedValue({ response: { data: { message: '无复核权限' } } })
    const { state } = withSetup()
    const e: Record<string, unknown> = { id: 43, status: 'PENDING' }

    await state.submitReview(e as never, 'EXEMPTED')

    expect(e.status).toBe('PENDING')
    expect(mocks.message.error).toHaveBeenCalledWith('复核提交失败：无复核权限')
    expect(state.reviewingId.value).toBeNull()
  })
})

describe('useExamGovernance — 展示辅助', () => {
  it('状态/类型/风险等级文案与标签映射覆盖未知值', () => {
    const { state } = withSetup()

    expect(state.statusText('PENDING')).toBe('待复核')
    expect(state.statusText('EXEMPTED')).toBe('已豁免')
    expect(state.statusText('WHATEVER')).toBe('WHATEVER')
    expect(state.statusText('')).toBe('—')
    expect(state.statusTag('REVIEWED')).toBe('danger')
    expect(state.statusTag('EXEMPTED')).toBe('info')
    expect(state.statusTag('PENDING')).toBe('warning')

    expect(state.typeText('TAB_SWITCH')).toBe('切屏')
    expect(state.typeText('NEW_TYPE')).toBe('NEW_TYPE')
    expect(state.levelText('HIGH')).toBe('高风险')
    expect(state.levelText(null)).toBe('—')
    expect(state.riskTag('MEDIUM')).toBe('warning')
    expect(state.riskTag('LOW')).toBe('info')
  })

  it('canReview 只允许待复核（或无状态）事件进入复核', () => {
    const { state } = withSetup()
    expect(state.canReview({} as never)).toBe(true)
    expect(state.canReview({ status: 'PENDING' } as never)).toBe(true)
    expect(state.canReview({ status: 'REVIEWED' } as never)).toBe(false)
    expect(state.canReview({ status: 'EXEMPTED' } as never)).toBe(false)
  })
})
