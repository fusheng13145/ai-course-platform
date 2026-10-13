import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h, ref } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * useTeacherDiscussion 单元测试
 * <p>本轮（2026-10-13）为教师课堂讨论补「加载失败可见」契约：接口层只记日志不弹窗，
 * 失败后话题列表被置空 → 页面只剩「暂无话题」，教师会据此误判班级零讨论。
 * 故本组合式函数暴露 `loadError`，由页面渲染持久提示并切换空态文案。</p>
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

import { useTeacherDiscussion } from '@/composables/useTeacherDiscussion'

/** 组合式函数只注册 watch（无 onMounted），须挂宿主组件并显式触发加载 */
function withSetup(courseId: number | string = 7) {
  const courseIdRef = ref<number | string>(courseId)
  let state!: ReturnType<typeof useTeacherDiscussion<number>>
  mount(
    defineComponent({
      setup() {
        state = useTeacherDiscussion(courseIdRef)
        return () => h('div')
      },
    }),
  )
  return { state, courseIdRef }
}

describe('useTeacherDiscussion — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('话题列表请求失败时必须给出持久提示，且不得表现为「暂无话题」', async () => {
    mocks.getTopics.mockRejectedValue(new Error('500'))
    const { state } = withSetup()

    await state.loadTopics()
    await flushPromises()

    expect(state.loadError.value).toBe('话题列表加载失败，请检查网络后重试')
    expect(state.topics.value).toEqual([])
    expect(state.topicLoading.value).toBe(false)
  })

  it('阴性对照：加载成功时不得出现失败提示', async () => {
    mocks.getTopics.mockResolvedValue({ data: [{ id: 1, title: '话题一' }] })
    const { state } = withSetup()

    await state.loadTopics()
    await flushPromises()

    expect(state.loadError.value).toBe('')
    expect(state.topics.value).toHaveLength(1)
  })

  it('未选课程时不置错，也不发起请求（未选课 ≠ 加载失败）', async () => {
    const { state } = withSetup('')

    await state.loadTopics()
    await flushPromises()

    expect(state.loadError.value).toBe('')
    expect(mocks.getTopics).not.toHaveBeenCalled()
  })

  it('失败后再成功：提示必须被清空（每次加载都复位，不是一次失败永久残留）', async () => {
    mocks.getTopics.mockRejectedValueOnce(new Error('500'))
    const { state } = withSetup()
    await state.loadTopics()
    expect(state.loadError.value).not.toBe('')

    mocks.getTopics.mockResolvedValue({ data: [{ id: 2, title: '话题二' }] })
    await state.loadTopics()

    expect(state.loadError.value).toBe('')
  })
})
