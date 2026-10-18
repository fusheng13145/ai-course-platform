import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * useDiscussion 单元测试
 * <p>本轮（2026-10-13）为学生课程讨论补「加载失败可见」契约：失败后话题列表被置空
 * → 页面只剩「暂无话题」，学生会误判该课程无讨论。故暴露 `loadError` 供页面渲染持久提示。</p>
 */

const mocks = vi.hoisted(() => ({
  getAllCourses: vi.fn(),
  getTopics: vi.fn(),
  createTopic: vi.fn(),
  getTopicDetail: vi.fn(),
  replyTopic: vi.fn(),
  likePost: vi.fn(),
  deletePost: vi.fn(),
}))

vi.mock('@/api/course', () => ({ default: { getAllCourses: mocks.getAllCourses } }))

vi.mock('@/api/discussion', () => ({
  default: {
    getTopics: mocks.getTopics,
    createTopic: mocks.createTopic,
    getTopicDetail: mocks.getTopicDetail,
    replyTopic: mocks.replyTopic,
    likePost: mocks.likePost,
    deletePost: mocks.deletePost,
  },
  normalizeDiscussionPost: (p: unknown) => p,
  normalizeDiscussionTopic: (t: unknown) => t,
}))

vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ userId: 1 }) }))

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

import { useDiscussion } from '@/composables/useDiscussion'

/** 组合式函数 onMounted 只加载课程列表，话题列表需在课程就绪后显式触发 */
function withSetup() {
  let state!: ReturnType<typeof useDiscussion>
  mount(
    defineComponent({
      setup() {
        state = useDiscussion()
        return () => h('div')
      },
    }),
  )
  return state
}

describe('useDiscussion — 加载失败可见', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getAllCourses.mockResolvedValue([{ id: 9, name: '数据结构' }])
  })

  it('话题列表请求失败时必须给出持久提示，且不得表现为「暂无话题」', async () => {
    mocks.getTopics.mockRejectedValue(new Error('500'))
    const state = withSetup()
    await flushPromises()

    await state.loadTopics()
    await flushPromises()

    expect(state.loadError.value).toBe('话题列表加载失败，请检查网络后重试')
    expect(state.topics.value).toEqual([])
    expect(state.topicLoading.value).toBe(false)
  })

  it('阴性对照：加载成功时不得出现失败提示', async () => {
    mocks.getTopics.mockResolvedValue({ data: [{ id: 1, title: '话题一' }] })
    const state = withSetup()
    await flushPromises()

    await state.loadTopics()
    await flushPromises()

    expect(state.loadError.value).toBe('')
    expect(state.topics.value).toHaveLength(1)
  })

  it('切换课程时清空上一门课的失败提示（避免串味）', async () => {
    mocks.getTopics.mockRejectedValueOnce(new Error('500')).mockResolvedValue({ data: [] })
    const state = withSetup()
    await flushPromises()
    await state.loadTopics()
    expect(state.loadError.value).not.toBe('')

    state.courseId.value = 10
    state.onCourseChange()
    await flushPromises()

    expect(state.loadError.value).toBe('')
  })
})
