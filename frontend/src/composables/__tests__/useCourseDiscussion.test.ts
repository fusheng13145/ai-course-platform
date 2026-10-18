import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * useCourseDiscussion 单元测试
 * <p>本轮（2026-10-13）为课程讨论（教师驾驶舱）补「加载失败可见」契约：失败后话题列表被置空
 * → 页面只剩「暂无讨论话题」，教师会误判该课程零讨论。故暴露 `loadError` 供页面渲染持久提示。</p>
 */

const mocks = vi.hoisted(() => ({
  getTopics: vi.fn(),
  createTopic: vi.fn(),
  getTopicDetail: vi.fn(),
  replyTopic: vi.fn(),
  likePost: vi.fn(),
  deletePost: vi.fn(),
  pinTopic: vi.fn(),
  closeTopic: vi.fn(),
  route: { params: { id: 7 } },
}))

vi.mock('@/api/discussion', () => ({
  default: {
    getTopics: mocks.getTopics,
    createTopic: mocks.createTopic,
    getTopicDetail: mocks.getTopicDetail,
    replyTopic: mocks.replyTopic,
    likePost: mocks.likePost,
    deletePost: mocks.deletePost,
    pinTopic: mocks.pinTopic,
    closeTopic: mocks.closeTopic,
  },
  normalizeDiscussionPost: (p: unknown) => p,
  normalizeDiscussionTopic: (t: unknown) => t,
}))

vi.mock('vue-router', () => ({ useRoute: () => mocks.route }))

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

import { useCourseDiscussion } from '@/composables/useCourseDiscussion'

/** 组合式函数含 onMounted(loadTopics)，挂宿主组件即可驱动首次加载 */
function withSetup() {
  let state!: ReturnType<typeof useCourseDiscussion>
  mount(
    defineComponent({
      setup() {
        state = useCourseDiscussion()
        return () => h('div')
      },
    }),
  )
  return state
}

describe('useCourseDiscussion — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.route.params.id = 7
  })

  it('话题列表请求失败时必须给出持久提示，且不得表现为「暂无讨论话题」', async () => {
    mocks.getTopics.mockRejectedValue(new Error('500'))
    const state = withSetup()

    await flushPromises()

    expect(state.loadError.value).toBe('话题列表加载失败，请检查网络后重试')
    expect(state.topics.value).toEqual([])
    expect(state.loading.value).toBe(false)
  })

  it('阴性对照：加载成功时不得出现失败提示', async () => {
    mocks.getTopics.mockResolvedValue({ data: [{ id: 1, title: '话题一' }] })
    const state = withSetup()

    await flushPromises()

    expect(state.loadError.value).toBe('')
    expect(state.topics.value).toHaveLength(1)
  })

  it('路由无课程 ID 时不置错，也不发起请求（缺课程 ≠ 加载失败）', async () => {
    mocks.route.params.id = undefined as unknown as number
    mocks.getTopics.mockResolvedValue({ data: [] })
    const state = withSetup()

    await flushPromises()

    expect(state.loadError.value).toBe('')
    expect(mocks.getTopics).not.toHaveBeenCalled()
  })
})
