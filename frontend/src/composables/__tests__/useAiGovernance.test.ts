import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * useAiGovernance 单元测试（角色矩阵「限制教师出题权限」的管理端开关）
 * <p>钉两条契约：① **缺省 = 允许**——配置行不存在时必须判为开启，否则"没配过"会被当成"被禁用"，
 * 让既有教师的出题能力在无人操作的情况下静默失效；② 保存失败必须回滚本地状态并给出提示，
 * 不能让界面显示"已关闭"而实际没落库。</p>
 */

const mocks = vi.hoisted(() => ({
  getConfigs: vi.fn(),
  updateConfig: vi.fn(),
  getAiRestrictions: vi.fn(),
  restrictAiGeneration: vi.fn(),
  unrestrictAiGeneration: vi.fn(),
  getAllUsers: vi.fn(),
}))

vi.mock('@/api/admin', () => ({
  default: {
    getConfigs: mocks.getConfigs,
    updateConfig: mocks.updateConfig,
    getAiRestrictions: mocks.getAiRestrictions,
    restrictAiGeneration: mocks.restrictAiGeneration,
    unrestrictAiGeneration: mocks.unrestrictAiGeneration,
    getAllUsers: mocks.getAllUsers,
  },
}))

import { useAiGovernance, TEACHER_QUESTION_GENERATION_KEY } from '@/composables/useAiGovernance'

function withSetup() {
  let state!: ReturnType<typeof useAiGovernance>
  mount(
    defineComponent({
      setup() {
        state = useAiGovernance()
        return () => h('div')
      },
    }),
  )
  return state
}

describe('useAiGovernance — 出题权限总闸', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('配置行不存在时缺省为「允许」，且不报错（缺省必须等于现状）', async () => {
    mocks.getConfigs.mockResolvedValue({ data: [] })
    const state = withSetup()

    await state.load()
    await flushPromises()

    expect(state.questionGenEnabled.value).toBe(true)
    expect(state.loadError.value).toBe('')
  })

  it('配置值为 false / FALSE / 带空白 时判为禁用（与后端口径一致）', async () => {
    mocks.getConfigs.mockResolvedValue({
      data: [{ key: TEACHER_QUESTION_GENERATION_KEY, value: ' FALSE ' }],
    })
    const state = withSetup()

    await state.load()
    await flushPromises()

    expect(state.questionGenEnabled.value).toBe(false)
  })

  it('关闭开关：按同一配置键写入 false（group=ai），本地状态随之更新', async () => {
    mocks.getConfigs.mockResolvedValue({ data: [] })
    mocks.updateConfig.mockResolvedValue(undefined)
    const state = withSetup()
    // 先让 onMounted 触发的首次读取落地，使本用例只测"保存"这一条路径（不混入挂载期读取的时序）
    await state.load()
    await flushPromises()

    await state.setQuestionGenEnabled(false)
    await flushPromises()

    expect(mocks.updateConfig).toHaveBeenCalledWith(TEACHER_QUESTION_GENERATION_KEY, {
      value: 'false',
      group: 'ai',
    })
    expect(state.questionGenEnabled.value).toBe(false)
    expect(state.saveError.value).toBe('')
  })

  it('保存失败必须回滚到原状态并给出提示（不得显示"已关闭"而实际未落库）', async () => {
    mocks.getConfigs.mockResolvedValue({ data: [] })
    mocks.updateConfig.mockRejectedValue(new Error('500'))
    const state = withSetup()
    // 先确认初始为允许
    await state.load()
    expect(state.questionGenEnabled.value).toBe(true)

    await state.setQuestionGenEnabled(false)
    await flushPromises()

    expect(state.questionGenEnabled.value).toBe(true)
    expect(state.saveError.value).not.toBe('')
    expect(state.saving.value).toBe(false)
  })

  it('读取失败：给出"状态不可信"的提示，不静默吞掉', async () => {
    mocks.getConfigs.mockRejectedValue(new Error('500'))
    const state = withSetup()

    await state.load()
    await flushPromises()

    expect(state.loadError.value).toContain('不可信')
    expect(state.loading.value).toBe(false)
  })
})

describe('useAiGovernance — 逐人限制名单（V10）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('名单加载成功：填充名单行（含被限制教师身份信息）', async () => {
    mocks.getAiRestrictions.mockResolvedValue({
      data: [{ userId: 7, username: 't001', fullName: '张老师', reason: '重复出题' }],
    })
    const state = withSetup()

    await state.loadRestrictions()
    await flushPromises()

    expect(state.restrictions.value).toHaveLength(1)
    expect(state.restrictions.value[0].fullName).toBe('张老师')
    expect(state.restrictionLoadError.value).toBe('')
  })

  it('名单加载失败：给出提示并清空名单（不得表现为"没人被限制"）', async () => {
    mocks.getAiRestrictions.mockRejectedValue(new Error('500'))
    const state = withSetup()

    await state.loadRestrictions()
    await flushPromises()

    expect(state.restrictionLoadError.value).toBe('限制名单加载失败，请检查网络后重试')
    expect(state.restrictions.value).toEqual([])
    expect(state.restrictionLoading.value).toBe(false)
  })

  it('加入名单成功：按 userId + 原因调用接口，并刷新名单', async () => {
    mocks.getAiRestrictions.mockResolvedValue({ data: [] })
    mocks.restrictAiGeneration.mockResolvedValue(undefined)
    const state = withSetup()
    await state.loadRestrictions()
    mocks.getAiRestrictions.mockClear()

    expect(await state.restrict(7, '重复出题')).toBe(true)
    await flushPromises()

    expect(mocks.restrictAiGeneration).toHaveBeenCalledWith(7, { reason: '重复出题' })
    // 成功后必须刷新，否则界面停留在旧名单
    expect(mocks.getAiRestrictions).toHaveBeenCalled()
  })

  it('加入名单失败：透传后端原因（如非教师 400），返回 false 且不刷新', async () => {
    mocks.getAiRestrictions.mockResolvedValue({ data: [] })
    mocks.restrictAiGeneration.mockRejectedValue({
      response: { data: { message: '只能限制教师角色的账号出题（该账号当前角色：ADMIN）' } },
    })
    const state = withSetup()
    await state.loadRestrictions()
    mocks.getAiRestrictions.mockClear()

    expect(await state.restrict(3, '')).toBe(false)
    expect(state.restrictionActionError.value).toContain('只能限制教师')
    expect(mocks.getAiRestrictions).not.toHaveBeenCalled()
    expect(state.restrictionBusy.value).toBe(false)
  })

  it('解除限制：调用接口并刷新名单（404 也走失败分支，不静默成功）', async () => {
    mocks.getAiRestrictions.mockResolvedValue({ data: [] })
    mocks.unrestrictAiGeneration.mockResolvedValue(undefined)
    const state = withSetup()

    expect(await state.unrestrict(7)).toBe(true)
    await flushPromises()
    expect(mocks.unrestrictAiGeneration).toHaveBeenCalledWith(7)
    expect(mocks.getAiRestrictions).toHaveBeenCalled()

    mocks.unrestrictAiGeneration.mockRejectedValue({ response: { data: { message: '该教师不在限制名单内：7' } } })
    expect(await state.unrestrict(7)).toBe(false)
    expect(state.restrictionActionError.value).toContain('不在限制名单内')
  })

  it('教师候选加载失败：只置空选择器，不阻断名单本身的展示（不写 error）', async () => {
    mocks.getAllUsers.mockRejectedValue(new Error('500'))
    mocks.getAiRestrictions.mockResolvedValue({ data: [{ userId: 7, reason: 'r' }] })
    const state = withSetup()

    await state.loadTeachers()
    await state.loadRestrictions()
    await flushPromises()

    expect(state.teachers.value).toEqual([])
    expect(state.restrictions.value).toHaveLength(1)
    expect(state.restrictionLoadError.value).toBe('')
  })
})
