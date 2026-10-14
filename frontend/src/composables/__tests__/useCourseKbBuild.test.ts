import { describe, it, expect, beforeEach, vi } from 'vitest'
import { ref } from 'vue'

/**
 * useCourseKbBuild（「从课程资料构建课程知识库」共享逻辑）单元测试。
 *
 * <p>该入口同时挂在管理员知识库管理页与教师课程详情页，行为必须一致，因此把「失败可见」契约锁在组合式函数层：
 * <ul>
 *   <li>引擎真正写入（mode=ENGINE）→ 返回 true 并提示成功，页面据此刷新文档列表；</li>
 *   <li>引擎未接入（mode=LOCAL）→ 属已知降级态：不提示成功、返回 false，用 warning 如实告知「只枚举未写入」；</li>
 *   <li>引擎不可用（mode=UNAVAILABLE / code=503）与网络异常 → 写入常驻 buildError，绝不粉饰为已入库；</li>
 *   <li>未选课程 → 不发起请求、不置错（未选课 ≠ 失败）。</li>
 * </ul>
 */

const mocks = vi.hoisted(() => ({
  ragBuildCourseKnowledgeBase: vi.fn(),
  success: vi.fn(),
  warning: vi.fn(),
  error: vi.fn(),
}))

vi.mock('@/api/rag', () => ({
  default: { ragBuildCourseKnowledgeBase: mocks.ragBuildCourseKnowledgeBase },
}))
vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), {
    success: mocks.success,
    warning: mocks.warning,
    error: mocks.error,
    info: vi.fn(),
    closeAll: vi.fn(),
  }),
}))

import { useCourseKbBuild } from '@/composables/useCourseKbBuild'

function setup(courseId: number | null = 7) {
  return useCourseKbBuild(ref(courseId))
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('useCourseKbBuild — 结果模式与提示口径', () => {
  it('ENGINE：真正写入 → 返回 true、提示成功、buildResult 落位、无错误', async () => {
    // rag 接口经 .then(unwrap) 已解包，桩直接给内层业务数据（勿再套 { data } 信封）
    mocks.ragBuildCourseKnowledgeBase.mockResolvedValue({
      mode: 'ENGINE',
      total: 3,
      added: 3,
      skipped: 0,
      indexedChunks: 8,
      note: '已写入 3 篇、跳过 0 篇（知识库中已存在），并重建课程索引',
    })
    const state = setup()

    await expect(state.build()).resolves.toBe(true)

    expect(mocks.ragBuildCourseKnowledgeBase).toHaveBeenCalledWith(7)
    expect(state.buildError.value).toBe('')
    expect(state.buildResult.value).toMatchObject({ mode: 'ENGINE', added: 3 })
    expect(mocks.success).toHaveBeenCalledTimes(1)
    expect(mocks.warning).not.toHaveBeenCalled()
  })

  it('LOCAL（引擎未接入）：不报成功、返回 false，用 warning 如实说明只枚举未写入', async () => {
    mocks.ragBuildCourseKnowledgeBase.mockResolvedValue({
      mode: 'LOCAL',
      total: 5,
      added: 0,
      skipped: 0,
      note: 'RAG 引擎未接入：已在消费侧枚举 5 条课程资料',
    })
    const state = setup()

    await expect(state.build()).resolves.toBe(false)

    expect(state.buildError.value).toBe('')
    expect(state.buildResult.value).toMatchObject({ mode: 'LOCAL', total: 5 })
    expect(mocks.success).not.toHaveBeenCalled()
    expect(mocks.warning).toHaveBeenCalledTimes(1)
    expect(String(mocks.warning.mock.calls[0][0])).toContain('未接入')
  })

  it('UNAVAILABLE：写入常驻错误、返回 false，且不提示成功', async () => {
    mocks.ragBuildCourseKnowledgeBase.mockResolvedValue({
      mode: 'UNAVAILABLE',
      total: 3,
      added: 0,
      skipped: 0,
      error: 'RAG 引擎由团队另一成员负责，本端仅预留接口；服务未接入或不可用',
      code: 503,
    })
    const state = setup()

    await expect(state.build()).resolves.toBe(false)

    expect(state.buildError.value).toContain('服务未接入或不可用')
    expect(state.buildResult.value).toMatchObject({ mode: 'UNAVAILABLE', code: 503 })
    expect(mocks.success).not.toHaveBeenCalled()
    expect(mocks.warning).not.toHaveBeenCalled()
  })

  it('code=503（即使 mode 缺失）同样判为失败可见', async () => {
    mocks.ragBuildCourseKnowledgeBase.mockResolvedValue({ code: 503 })
    const state = setup()

    await expect(state.build()).resolves.toBe(false)

    expect(state.buildError.value).not.toBe('')
  })

  it('网络/接口异常：写入常驻错误、返回 false（不得静默）', async () => {
    mocks.ragBuildCourseKnowledgeBase.mockRejectedValue(new Error('500'))
    const state = setup()

    await expect(state.build()).resolves.toBe(false)

    expect(state.buildError.value).toBe('课程知识库构建失败：请检查后端 RAG 服务后重试')
    expect(state.buildResult.value).toBeNull()
  })

  it('后端未返回有效结果（null / 非对象）：写入常驻错误', async () => {
    mocks.ragBuildCourseKnowledgeBase.mockResolvedValue(null)
    const state = setup()

    await expect(state.build()).resolves.toBe(false)

    expect(state.buildError.value).toBe('课程知识库构建失败：后端未返回有效结果')
  })

  it('未选课程：不发请求、不置错（未选课 ≠ 失败）', async () => {
    const state = setup(null)

    await expect(state.build()).resolves.toBe(false)

    expect(mocks.ragBuildCourseKnowledgeBase).not.toHaveBeenCalled()
    expect(state.buildError.value).toBe('')
    expect(mocks.warning).toHaveBeenCalledWith('请先选择课程')
  })

  it('失败后再成功可复位（buildError 清空）', async () => {
    const state = setup()
    mocks.ragBuildCourseKnowledgeBase.mockRejectedValueOnce(new Error('500'))
    await state.build()
    expect(state.buildError.value).not.toBe('')

    mocks.ragBuildCourseKnowledgeBase.mockResolvedValue({ mode: 'ENGINE', added: 1 })

    await expect(state.build()).resolves.toBe(true)
    expect(state.buildError.value).toBe('')
  })

  it('请求进行中 building=true，结束后复位（防重复提交）', async () => {
    let resolveFn: (v: unknown) => void = () => {}
    mocks.ragBuildCourseKnowledgeBase.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveFn = resolve
        }),
    )
    const state = setup()

    const pending = state.build()
    expect(state.building.value).toBe(true)

    resolveFn({ mode: 'ENGINE', added: 2 })
    await pending

    expect(state.building.value).toBe(false)
  })
})
