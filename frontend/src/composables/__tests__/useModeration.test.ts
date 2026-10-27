import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * useModeration 单元测试（角色矩阵 ③ 内容审核与违规处理）
 * <p>钉四件事：① 加载失败必须"失败可见"（loadError + 清空当前页签列表，不得表现为"没有违规内容"）；
 * ② 三个页签各自取数、互不串味；③ 处置动作按 target+id+动作+原因+是否封禁 透传并刷新；
 * ④ 解封失败（404，非经审核封禁）不得静默成功。</p>
 */

const mocks = vi.hoisted(() => ({
  getModerationPosts: vi.fn(),
  getModerationResources: vi.fn(),
  getBannedAuthors: vi.fn(),
  applyModerationAction: vi.fn(),
  banAuthor: vi.fn(),
  unbanAuthor: vi.fn(),
}))

vi.mock('@/api/admin', () => ({
  default: {
    getModerationPosts: mocks.getModerationPosts,
    getModerationResources: mocks.getModerationResources,
    getBannedAuthors: mocks.getBannedAuthors,
    applyModerationAction: mocks.applyModerationAction,
    banAuthor: mocks.banAuthor,
    unbanAuthor: mocks.unbanAuthor,
  },
}))

import { useModeration } from '@/composables/useModeration'

function withSetup() {
  let state!: ReturnType<typeof useModeration>
  mount(
    defineComponent({
      setup() {
        state = useModeration()
        return () => h('div')
      },
    }),
  )
  return state
}

describe('useModeration — 内容审核', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('讨论回复：解包分页信封并填充列表与总数', async () => {
    mocks.getModerationPosts.mockResolvedValue({
      data: { list: [{ id: 1, authorName: '学生甲', content: '违规内容' }], total: 7 },
    })
    const state = withSetup()

    await state.load()
    await flushPromises()

    expect(state.posts.value).toHaveLength(1)
    expect(state.total.value).toBe(7)
    expect(state.loadError.value).toBe('')
  })

  it('加载失败：给出提示并清空当前页签列表（不得表现为"没有违规内容"）', async () => {
    mocks.getModerationPosts.mockRejectedValue(new Error('500'))
    const state = withSetup()

    await state.load()
    await flushPromises()

    expect(state.loadError.value).toBe('内容审核列表加载失败，请检查网络后重试')
    expect(state.posts.value).toEqual([])
    expect(state.loading.value).toBe(false)
  })

  it('切换页签：资源与被封禁作者各自取数，互不串味', async () => {
    mocks.getModerationResources.mockResolvedValue({
      data: { list: [{ id: 5, fileName: '课件.pdf' }], total: 1 },
    })
    mocks.getBannedAuthors.mockResolvedValue({ data: [{ userId: 7, banReason: '刷屏' }] })
    const state = withSetup()

    state.setTab('resources')
    await flushPromises()
    expect(state.resources.value).toHaveLength(1)
    expect(mocks.getModerationResources).toHaveBeenCalled()

    state.setTab('banned')
    await flushPromises()
    expect(state.bannedAuthors.value).toHaveLength(1)
    expect(mocks.getBannedAuthors).toHaveBeenCalled()
  })

  it('处置：按 target/id/动作/原因/是否封禁透传，并刷新列表', async () => {
    mocks.getModerationPosts.mockResolvedValue({ data: { list: [], total: 0 } })
    mocks.applyModerationAction.mockResolvedValue(undefined)
    const state = withSetup()
    await state.load()
    mocks.getModerationPosts.mockClear()

    expect(await state.applyAction('discussion-posts', 1, 'HIDE', ' 含广告 ', true)).toBe(true)
    await flushPromises()

    expect(mocks.applyModerationAction).toHaveBeenCalledWith('discussion-posts', 1, {
      action: 'HIDE',
      reason: '含广告',
      banAuthor: true,
    })
    expect(mocks.getModerationPosts).toHaveBeenCalled()
  })

  it('处置失败：透传后端原因（如 404 已不存在），返回 false 且不刷新', async () => {
    mocks.getModerationPosts.mockResolvedValue({ data: { list: [], total: 0 } })
    mocks.applyModerationAction.mockRejectedValue({
      response: { data: { message: '讨论回复不存在：1' } },
    })
    const state = withSetup()
    await state.load()
    mocks.getModerationPosts.mockClear()

    expect(await state.applyAction('discussion-posts', 1, 'DELETE', '', false)).toBe(false)
    expect(state.actionError.value).toBe('讨论回复不存在：1')
    expect(mocks.getModerationPosts).not.toHaveBeenCalled()
    expect(state.busy.value).toBe(false)
  })

  it('解除封禁：非经审核封禁（404）走失败分支，不静默成功', async () => {
    mocks.getBannedAuthors.mockResolvedValue({ data: [] })
    mocks.unbanAuthor.mockRejectedValue({
      response: {
        data: { message: '该账号不是经内容审核封禁的账号（或不存在），无法在此解除：7' },
      },
    })
    const state = withSetup()

    expect(await state.unban(7)).toBe(false)
    expect(state.actionError.value).toContain('不是经内容审核封禁')
  })

  it('查询：重置到第 1 页并带上关键词与状态过滤', async () => {
    mocks.getModerationPosts.mockResolvedValue({ data: { list: [], total: 0 } })
    const state = withSetup()
    state.pageNo.value = 5
    state.keyword.value = '  广告  '
    state.statusFilter.value = 'HIDDEN'

    state.search()
    await flushPromises()

    expect(state.pageNo.value).toBe(1)
    expect(mocks.getModerationPosts).toHaveBeenCalledWith(
      expect.objectContaining({ page: 1, status: 'HIDDEN', keyword: '广告' }),
    )
  })
})
