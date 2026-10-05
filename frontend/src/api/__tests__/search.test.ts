import { describe, it, expect, vi, beforeEach } from 'vitest'
import { searchAll } from '@/api/search'
import searchDefault from '@/api/search'

/**
 * 全站聚合搜索契约测试（2026-10-04）
 * <p>此前 0% 覆盖。`searchAll` 有默认 types（四类全搜）与默认 limit，且把 types 数组
 * 逗号拼接进 query；`.then` 做「命中数组才透传、否则回落空数组」的归一化——
 * 归一化写错会让搜索页在接口异常时抛错而非显示空态。</p>
 */

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
}))

vi.mock('@/api/request', () => ({
  default: {
    get: mocks.get,
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  },
}))

describe('search.searchAll - 请求契约', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.get.mockResolvedValue({ data: [] })
  })

  it('默认参数：四类全搜 + limit 8，types 逗号拼接进 query', async () => {
    await searchAll('数据结构')
    expect(mocks.get).toHaveBeenCalledWith('/search', {
      params: { keyword: '数据结构', types: 'knowledge,question,course,note', limit: 8 },
    })
  })

  it('自定义 types 子集与 limit 生效', async () => {
    await searchAll('栈', ['question', 'note'], 3)
    expect(mocks.get).toHaveBeenCalledWith('/search', {
      params: { keyword: '栈', types: 'question,note', limit: 3 },
    })
  })

  it('边界：空 types 数组拼接为空字符串', async () => {
    await searchAll('x', [], 1)
    expect(mocks.get).toHaveBeenCalledWith('/search', {
      params: { keyword: 'x', types: '', limit: 1 },
    })
  })
})

describe('search.searchAll - 结果归一化', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  const CASES: Array<[string, unknown, unknown[]]> = [
    ['data 为命中数组', { data: [{ id: 1 }] }, [{ id: 1 }]],
    ['data 为空数组', { data: [] }, []],
    ['data 为 null', { data: null }, []],
    ['data 为对象（非数组）', { data: { list: [] } }, []],
    ['响应缺 data 字段', { code: 200 }, []],
    ['响应为 null', null, []],
    ['响应为 undefined', undefined, []],
  ]

  for (const [label, payload, expected] of CASES) {
    it(`${label} → 归一化为数组（异常回落空列表）`, async () => {
      mocks.get.mockResolvedValue(payload)
      const result = await searchAll('q')
      expect(Array.isArray(result)).toBe(true)
      expect(result).toEqual(expected)
    })
  }
})

describe('search 模块导出完整性', () => {
  it('默认导出应包含具名接口 searchAll', () => {
    expect(typeof searchDefault.searchAll).toBe('function')
    expect(Object.keys(searchDefault)).toEqual(['searchAll'])
  })
})
