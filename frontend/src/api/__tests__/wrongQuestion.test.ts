import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as wrongQuestionApi from '@/api/wrongQuestion'

/**
 * 错题本 API 契约测试（2026-10-04）
 * <p>本模块此前 0% 覆盖。真实风险有两处：
 * ① `getWrongQuestions` 的 `.then` 归一化（P2-6 修复）——后端返回 PagedResponse，
 *    而调用方按数组消费，归一化写错会让错题列表恒空；
 * ② `unmarkMastered` 打到 `/unmaster`（URL 与方法名不一致，属既定契约，不得改）。</p>
 */

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/api/request', () => ({
  default: {
    get: mocks.get,
    post: mocks.post,
    put: mocks.put,
    patch: mocks.patch,
    delete: mocks.delete,
  },
}))

const spyOf = {
  get: mocks.get,
  post: mocks.post,
  put: mocks.put,
  patch: mocks.patch,
  delete: mocks.delete,
}

/** 契约表：[导出名, HTTP 方法, 期望 URL, 调用实参, 期望传给 request 的完整实参] */
const CONTRACT: Array<
  [
    keyof typeof wrongQuestionApi,
    'get' | 'post' | 'put' | 'patch' | 'delete',
    string,
    unknown[],
    unknown[],
  ]
> = [
  ['addWrongQuestion', 'post', '/wrong-questions', [{ id: 1 }], [{ id: 1 }]],
  ['deleteWrongQuestion', 'delete', '/wrong-questions/8', [8], []],
  ['markMastered', 'patch', '/wrong-questions/8/mastered', [8], []],
  // 注意：方法名 unmarkMastered，但真实端点是 /unmaster（不是 /unmastered）——锁定当前契约
  ['unmarkMastered', 'patch', '/wrong-questions/8/unmaster', [8], []],
  ['getWrongQuestionStats', 'get', '/wrong-questions/stats', [], []],
  [
    'generatePracticePaper',
    'post',
    '/wrong-questions/practice-paper',
    [{ ids: [1] }],
    [{ ids: [1] }],
  ],
  ['getRelatedQuestions', 'get', '/wrong-questions/6/related', [6], []],
  ['getRelated', 'get', '/wrong-questions/6/related', [6], []],
]

describe('wrongQuestion 契约（方法 / URL / 参数位置）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.get.mockResolvedValue({ code: 200, data: {} })
    mocks.post.mockResolvedValue({ code: 200, data: {} })
    mocks.patch.mockResolvedValue({ code: 200, data: {} })
    mocks.delete.mockResolvedValue({ code: 200, data: {} })
  })

  for (const [name, method, url, callArgs, requestArgs] of CONTRACT) {
    it(`${String(name)} 走 ${method.toUpperCase()} ${url}`, async () => {
      const spy = spyOf[method]
      const fn = (wrongQuestionApi as unknown as Record<string, Function>)[name as string]
      expect(typeof fn).toBe('function')

      await fn(...callArgs)

      expect(spy).toHaveBeenCalledTimes(1)
      expect(spy).toHaveBeenCalledWith(url, ...requestArgs)
    })
  }

  it('getUnmastered / getByKnowledgePoints 把 studentId/courseId 放进 query params', async () => {
    await wrongQuestionApi.getUnmastered(3, 7)
    expect(mocks.get).toHaveBeenCalledWith('/wrong-questions/unmastered', {
      params: { studentId: 3, courseId: 7 },
    })

    await wrongQuestionApi.getByKnowledgePoints(3)
    expect(mocks.get).toHaveBeenCalledWith('/wrong-questions/by-kp', {
      params: { studentId: 3, courseId: undefined },
    })
  })

  it('getRelated 与 getRelatedQuestions 指向同一端点', async () => {
    await wrongQuestionApi.getRelatedQuestions(6)
    const byFull = mocks.get.mock.calls.at(-1)
    await wrongQuestionApi.getRelated(6)
    const byAlias = mocks.get.mock.calls.at(-1)
    expect(byAlias).toEqual(byFull)
  })
})

describe('wrongQuestion.getWrongQuestions - PagedResponse 归一化为数组（P2-6）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // 核心不变式：无论后端把列表藏在哪个字段，返回的 res.data 一定是数组
  const CASES: Array<[string, unknown, unknown[]]> = [
    [
      'data.list 包装',
      { code: 200, data: { list: [{ id: 1 }, { id: 2 }] } },
      [{ id: 1 }, { id: 2 }],
    ],
    ['data.records 包装', { code: 200, data: { records: [{ id: 3 }] } }, [{ id: 3 }]],
    ['data 裸数组', { code: 200, data: [{ id: 4 }] }, [{ id: 4 }]],
    ['data 空对象', { code: 200, data: {} }, []],
    ['data 为 null', { code: 200, data: null }, []],
    ['data 缺失', { code: 200 }, []],
    ['list 为空数组', { code: 200, data: { list: [] } }, []],
  ]

  for (const [label, payload, expected] of CASES) {
    it(`${label} → res.data 归一化为数组`, async () => {
      mocks.get.mockResolvedValue(payload)
      const res = await wrongQuestionApi.getWrongQuestions()
      expect(Array.isArray(res.data)).toBe(true)
      expect(res.data).toEqual(expected)
    })
  }

  it('默认参数：未传 params 时以空对象发起 GET /wrong-questions', async () => {
    mocks.get.mockResolvedValue({ data: [] })
    await wrongQuestionApi.getWrongQuestions()
    expect(mocks.get).toHaveBeenCalledWith('/wrong-questions', { params: {} })
  })

  it('透传查询参数：params 落在请求配置里', async () => {
    mocks.get.mockResolvedValue({ data: [] })
    await wrongQuestionApi.getWrongQuestions({ page: 2, size: 10 })
    expect(mocks.get).toHaveBeenCalledWith('/wrong-questions', { params: { page: 2, size: 10 } })
  })

  it('保留信封顶层字段（code/message），并把 total/page 留在原 data 内不上浮', async () => {
    const inner = { list: [{ id: 1 }], total: 42, page: 3, pageSize: 5 }
    mocks.get.mockResolvedValue({ code: 200, message: 'ok', data: inner })

    const res = await wrongQuestionApi.getWrongQuestions()

    // 归一化策略：{...res, data: list} —— 顶层信封原样保留，分页元字段不被提升
    // request 的类型签名是 AxiosResponse，这里按运行时实际信封结构读取 code/message
    const envelope = res as unknown as { code: number; message: string; data: unknown }
    expect(envelope.code).toBe(200)
    expect(envelope.message).toBe('ok')
    expect('total' in res).toBe(false)
    expect(res.data).toBe(inner.list) // 直接复用后端数组引用
  })

  it('边界：list 与 records 均非空时取先出现的非空候选（list）', async () => {
    mocks.get.mockResolvedValue({ data: { list: [{ id: 'L' }], records: [{ id: 'R' }] } })
    const res = await wrongQuestionApi.getWrongQuestions()
    expect(res.data).toEqual([{ id: 'L' }])
  })

  it('回归（P1-10 同类）：list 为空数组时必须回落到 records', async () => {
    mocks.get.mockResolvedValue({ data: { list: [], records: [{ id: 'R' }] } })
    const res = await wrongQuestionApi.getWrongQuestions()
    // 旧写法 `payload.list || payload.records` 里空数组是 truthy，会把 records 的真实数据吞掉；
    // 现在走 firstNonEmptyArray，空数组不再算候选。
    expect(res.data).toEqual([{ id: 'R' }])
  })
})

describe('wrongQuestion 模块导出完整性', () => {
  it('默认导出应包含全部具名接口', () => {
    const named = Object.keys(wrongQuestionApi).filter((k) => k !== 'default')
    const defaultKeys = Object.keys(wrongQuestionApi.default as object)
    const missing = named.filter((k) => !defaultKeys.includes(k))
    expect(missing).toEqual([])
  })
})
