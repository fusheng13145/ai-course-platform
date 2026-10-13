import { describe, it, expect, beforeEach, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * useSemesters 单元测试（角色矩阵 ① 创建学期 / ② 归档过期学期）
 * <p>钉住三件事：① 列表加载失败必须"失败可见"（loadError + 列表清空，不得表现为"没有学期"）；
 * ② 写操作失败要给出**后端原因**（如重名 409 的 message），而不是笼统文案；
 * ③ 归档/批量归档后必须重新拉取列表（不能让界面停留在旧状态）。</p>
 */

const mocks = vi.hoisted(() => ({
  getSemesters: vi.fn(),
  createSemester: vi.fn(),
  archiveSemester: vi.fn(),
  archiveExpiredSemesters: vi.fn(),
}))

vi.mock('@/api/admin', () => ({
  default: {
    getSemesters: mocks.getSemesters,
    createSemester: mocks.createSemester,
    archiveSemester: mocks.archiveSemester,
    archiveExpiredSemesters: mocks.archiveExpiredSemesters,
  },
}))

import { useSemesters } from '@/composables/useSemesters'

function withSetup() {
  let state!: ReturnType<typeof useSemesters>
  mount(
    defineComponent({
      setup() {
        state = useSemesters()
        return () => h('div')
      },
    }),
  )
  return state
}

describe('useSemesters — 学期管理', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('列表加载失败：给出持久提示并清空列表（不得表现为"没有学期"）', async () => {
    mocks.getSemesters.mockRejectedValue(new Error('500'))
    const state = withSetup()

    await state.list()
    await flushPromises()

    expect(state.loadError.value).toBe('学期列表加载失败，请检查网络后重试')
    expect(state.semesters.value).toEqual([])
    expect(state.loading.value).toBe(false)
  })

  it('列表加载成功：按状态统计在册/已归档数量', async () => {
    mocks.getSemesters.mockResolvedValue({
      data: [
        { id: 1, name: '2026-2027-1', status: 'ACTIVE' },
        { id: 2, name: '2025-2026-2', status: 'ARCHIVED' },
        { id: 3, name: '2025-2026-1', status: 'ARCHIVED' },
      ],
    })
    const state = withSetup()

    await state.list()
    await flushPromises()

    expect(state.activeCount.value).toBe(1)
    expect(state.archivedCount.value).toBe(2)
    expect(state.loadError.value).toBe('')
  })

  it('创建学期成功：调用创建接口并刷新列表', async () => {
    mocks.getSemesters.mockResolvedValue({ data: [] })
    mocks.createSemester.mockResolvedValue(undefined)
    const state = withSetup()
    await state.list()
    mocks.getSemesters.mockClear()

    const ok = await state.create({ name: '2026-2027-1', startDate: '2026-09-01', endDate: '2027-01-15' })
    await flushPromises()

    expect(ok).toBe(true)
    expect(mocks.createSemester).toHaveBeenCalledWith({
      name: '2026-2027-1',
      startDate: '2026-09-01',
      endDate: '2027-01-15',
    })
    // 成功后必须重新拉取，否则界面停留在旧列表
    expect(mocks.getSemesters).toHaveBeenCalled()
  })

  it('创建失败：透传后端原因（如重名 409），返回 false 且不刷新列表', async () => {
    mocks.getSemesters.mockResolvedValue({ data: [] })
    mocks.createSemester.mockRejectedValue({ response: { data: { message: '学期名称已存在：2026-2027-1' } } })
    const state = withSetup()
    await state.list()
    mocks.getSemesters.mockClear()

    const ok = await state.create({ name: '2026-2027-1', startDate: '2026-09-01', endDate: '2027-01-15' })
    await flushPromises()

    expect(ok).toBe(false)
    expect(state.actionError.value).toBe('学期名称已存在：2026-2027-1')
    expect(mocks.getSemesters).not.toHaveBeenCalled()
  })

  it('归档单个学期：调用接口并刷新列表', async () => {
    mocks.getSemesters.mockResolvedValue({ data: [] })
    mocks.archiveSemester.mockResolvedValue(undefined)
    const state = withSetup()
    await state.list()
    mocks.getSemesters.mockClear()

    expect(await state.archive(7)).toBe(true)
    await flushPromises()

    expect(mocks.archiveSemester).toHaveBeenCalledWith(7)
    expect(mocks.getSemesters).toHaveBeenCalled()
  })

  it('批量归档过期学期：返回本次条数；失败时返回 -1 并给出原因', async () => {
    mocks.getSemesters.mockResolvedValue({ data: [] })
    mocks.archiveExpiredSemesters.mockResolvedValue({ data: { archivedCount: 3 } })
    const state = withSetup()

    expect(await state.archiveExpired()).toBe(3)
    await flushPromises()

    mocks.archiveExpiredSemesters.mockRejectedValue(new Error('500'))
    expect(await state.archiveExpired()).toBe(-1)
    expect(state.actionError.value).not.toBe('')
  })
})
