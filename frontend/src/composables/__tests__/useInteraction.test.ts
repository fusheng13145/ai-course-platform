import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * useInteraction 单元测试
 * <p>本轮（2026-10-13）为教师课堂互动补「加载失败可见」契约。本页有**签到/投票两路**列表，
 * 失败信号必须**各自独立**再聚合暴露，否则「签到成功、投票失败」时会被成功方复位而串味。
 * 用例 ① 专门锁死这一点。</p>
 */

const mocks = vi.hoisted(() => ({
  getSignInList: vi.fn(),
  getVoteList: vi.fn(),
  createSignIn: vi.fn(),
  publishSignIn: vi.fn(),
  closeSignIn: vi.fn(),
  getSignInRecords: vi.fn(),
  markSignIn: vi.fn(),
  createVote: vi.fn(),
  closeVote: vi.fn(),
  getVoteResult: vi.fn(),
}))

vi.mock('@/api/interaction', () => ({
  default: {
    getSignInList: mocks.getSignInList,
    getVoteList: mocks.getVoteList,
    createSignIn: mocks.createSignIn,
    publishSignIn: mocks.publishSignIn,
    closeSignIn: mocks.closeSignIn,
    getSignInRecords: mocks.getSignInRecords,
    markSignIn: mocks.markSignIn,
    createVote: mocks.createVote,
    closeVote: mocks.closeVote,
    getVoteResult: mocks.getVoteResult,
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: Object.assign(vi.fn(), {
    success: vi.fn(),
    error: vi.fn(),
    info: vi.fn(),
    warning: vi.fn(),
    closeAll: vi.fn(),
  }),
  ElMessageBox: { confirm: vi.fn().mockResolvedValue(undefined) },
}))

import { useInteraction } from '@/composables/useInteraction'

/** 组合式函数只注册 watch（无 onMounted），须挂宿主组件并显式触发加载 */
function withSetup(courseId: number | string = 7) {
  const courseIdRef = ref<number | string>(courseId)
  let state!: ReturnType<typeof useInteraction>
  mount(
    defineComponent({
      setup() {
        state = useInteraction(courseIdRef)
        return () => h('div')
      },
    }),
  )
  return { state, courseIdRef }
}

describe('useInteraction — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('签到失败但投票成功时：聚合提示仍必须报出签到失败（两路信号不得互相串味）', async () => {
    mocks.getSignInList.mockRejectedValue(new Error('500'))
    mocks.getVoteList.mockResolvedValue({ data: [{ id: 1, title: '投票一' }] })
    const { state } = withSetup()

    await Promise.all([state.loadSignInList(), state.loadVoteList()])
    await flushPromises()

    expect(state.loadError.value).toBe('签到列表加载失败，请检查网络后重试')
    expect(state.signList.value).toEqual([])
    expect(state.voteList.value).toHaveLength(1)
  })

  it('投票失败但签到成功时：聚合提示必须报出投票失败', async () => {
    mocks.getSignInList.mockResolvedValue({ data: [{ id: 1, title: '签到一' }] })
    mocks.getVoteList.mockRejectedValue(new Error('500'))
    const { state } = withSetup()

    await Promise.all([state.loadSignInList(), state.loadVoteList()])
    await flushPromises()

    expect(state.loadError.value).toBe('投票列表加载失败，请检查网络后重试')
    expect(state.signList.value).toHaveLength(1)
    expect(state.voteList.value).toEqual([])
  })

  it('阴性对照：两路都成功时不得出现失败提示', async () => {
    mocks.getSignInList.mockResolvedValue({ data: [{ id: 1, title: '签到一' }] })
    mocks.getVoteList.mockResolvedValue({ data: [{ id: 2, title: '投票一' }] })
    const { state } = withSetup()

    await Promise.all([state.loadSignInList(), state.loadVoteList()])
    await flushPromises()

    expect(state.loadError.value).toBe('')
    expect(state.signList.value).toHaveLength(1)
    expect(state.voteList.value).toHaveLength(1)
  })

  it('未选课程时不置错，也不发起请求（未选课 ≠ 加载失败）', async () => {
    const { state } = withSetup('')

    await Promise.all([state.loadSignInList(), state.loadVoteList()])
    await flushPromises()

    expect(state.loadError.value).toBe('')
    expect(mocks.getSignInList).not.toHaveBeenCalled()
    expect(mocks.getVoteList).not.toHaveBeenCalled()
  })
})
