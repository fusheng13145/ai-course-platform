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
}))

vi.mock('@/api/admin', () => ({
  default: { getConfigs: mocks.getConfigs, updateConfig: mocks.updateConfig },
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

describe('useAiGovernance — 出题权限开关', () => {
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
