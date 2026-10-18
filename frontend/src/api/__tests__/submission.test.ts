import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as submissionApi from '@/api/submission'

const mocks = vi.hoisted(() => ({ get: vi.fn() }))

vi.mock('@/api/request', () => ({
  default: { get: mocks.get },
}))

describe('submission API pagination helpers', () => {
  beforeEach(() => vi.clearAllMocks())

  it('loads every submission page for the selected task and preserves filters', async () => {
    mocks.get
      .mockResolvedValueOnce({ code: 200, data: { records: [{ id: 1 }], total: 101 } })
      .mockResolvedValueOnce({ code: 200, data: { records: [{ id: 2 }], total: 101 } })

    await expect(
      submissionApi.getAllTaskSubmissions(31, { status: 'GRADED' }),
    ).resolves.toEqual([{ id: 1 }, { id: 2 }])
    expect(mocks.get).toHaveBeenNthCalledWith(1, '/submissions/task/31', {
      params: { status: 'GRADED', page: 1, size: 100 },
    })
    expect(mocks.get).toHaveBeenNthCalledWith(2, '/submissions/task/31', {
      params: { status: 'GRADED', page: 2, size: 100 },
    })
  })

  it('exposes the helper in the default API object', () => {
    expect(submissionApi.default.getAllTaskSubmissions).toBe(submissionApi.getAllTaskSubmissions)
  })
})
