import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as questionApi from '@/api/question'

/**
 * 题库 API 契约测试（2026-10-04）
 * <p>此前 0% 覆盖。多数接口是薄封装，需重点锁：路径参数插值、查询参数落点，
 * 以及两个非默认形态——`exportQuestionsExcel` 附加 responseType:'blob'、
 * `importQuestionsExcel` 携带 multipart 头；`copyQuestion` 只发 URL 不发请求体。</p>
 */

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/api/request', () => ({
  default: {
    get: mocks.get,
    post: mocks.post,
    put: mocks.put,
    delete: mocks.delete,
  },
}))

const spyOf = { get: mocks.get, post: mocks.post, put: mocks.put, delete: mocks.delete }

/** [导出名, 方法, 期望 URL, 调用实参, 期望传给 request 的完整实参] */
const CONTRACT: Array<
  [keyof typeof questionApi, 'get' | 'post' | 'put' | 'delete', string, unknown[], unknown[]]
> = [
  ['getQuestionList', 'get', '/questions', [{ page: 1 }], [{ params: { page: 1 } }]],
  ['getQuestionById', 'get', '/questions/6', [6], []],
  [
    'createQuestion',
    'post',
    '/questions',
    [{ type: 'SINGLE_CHOICE' }],
    [{ type: 'SINGLE_CHOICE' }],
  ],
  ['updateQuestion', 'put', '/questions/6', [6, { type: 'X' }], [{ type: 'X' }]],
  ['deleteQuestion', 'delete', '/questions/6', [6], []],
  ['generateByAI', 'post', '/questions/ai-generate', [{ topic: '图' }], [{ topic: '图' }]],
  ['reviewQuestion', 'post', '/questions/6/review', [6, { pass: true }], [{ pass: true }]],
  ['copyQuestion', 'post', '/questions/6/copy', [6], []],
  ['searchQuestions', 'get', '/questions/search', [{ kw: 'a' }], [{ params: { kw: 'a' } }]],
  ['randomSelect', 'get', '/questions/random', [{ count: 5 }], [{ params: { count: 5 } }]],
  ['getByKnowledgePoint', 'get', '/questions/kp/11', [11], []],
  ['getQuestionStats', 'get', '/questions/6/stats', [6], []],
  ['getBankStatistics', 'get', '/questions/statistics', [], []],
  [
    'getErrorProne',
    'get',
    '/questions/error-prone',
    [{ courseId: 3 }],
    [{ params: { courseId: 3 } }],
  ],
  [
    'exportQuestionsExcel',
    'get',
    '/teacher/questions/export',
    [{ courseId: 3 }],
    [{ params: { courseId: 3 }, responseType: 'blob' }],
  ],
  [
    'importQuestionsExcel',
    'post',
    '/teacher/questions/import-excel',
    [{ file: 'x' }],
    [{ file: 'x' }, { headers: { 'Content-Type': 'multipart/form-data' } }],
  ],
]

describe('question 契约（方法 / URL / 参数落点）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.get.mockResolvedValue({ code: 200, data: [] })
    mocks.post.mockResolvedValue({ code: 200, data: {} })
    mocks.put.mockResolvedValue({ code: 200, data: {} })
    mocks.delete.mockResolvedValue({ code: 200, data: {} })
  })

  for (const [name, method, url, callArgs, requestArgs] of CONTRACT) {
    it(`${String(name)} 走 ${method.toUpperCase()} ${url}`, async () => {
      const spy = spyOf[method]
      const fn = (questionApi as unknown as Record<string, Function>)[name as string]
      expect(typeof fn).toBe('function')

      await fn(...callArgs)

      expect(spy).toHaveBeenCalledTimes(1)
      expect(spy).toHaveBeenCalledWith(url, ...requestArgs)
    })
  }
})

describe('question - 默认参数与特例分支', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.get.mockResolvedValue({ code: 200, data: [] })
    mocks.post.mockResolvedValue({ code: 200, data: {} })
  })

  it('查询类接口未传参时以空 params 发起（URL 不带 query）', async () => {
    await questionApi.getQuestionList()
    expect(mocks.get).toHaveBeenCalledWith('/questions', { params: {} })

    await questionApi.randomSelect()
    expect(mocks.get).toHaveBeenCalledWith('/questions/random', { params: {} })
  })

  it('copyQuestion 仅发送 URL，不附带请求体', async () => {
    await questionApi.copyQuestion(6)
    expect(mocks.post).toHaveBeenCalledTimes(1)
    expect(mocks.post).toHaveBeenCalledWith('/questions/6/copy')
    expect(mocks.post.mock.calls[0].length).toBe(1)
  })

  it('getAllQuestions 和 searchAllQuestions 汇总全部分页并保留筛选', async () => {
    mocks.get
      .mockResolvedValueOnce({ code: 200, data: { list: [{ id: 1 }], total: 101 } })
      .mockResolvedValueOnce({ code: 200, data: { list: [{ id: 2 }], total: 101 } })

    await expect(
      questionApi.searchAllQuestions({ keyword: '三次握手', type: 'ESSAY' }),
    ).resolves.toEqual([{ id: 1 }, { id: 2 }])
    expect(mocks.get).toHaveBeenNthCalledWith(1, '/questions/search', {
      params: { keyword: '三次握手', type: 'ESSAY', page: 1, size: 100 },
    })
    expect(mocks.get).toHaveBeenNthCalledWith(2, '/questions/search', {
      params: { keyword: '三次握手', type: 'ESSAY', page: 2, size: 100 },
    })
  })

  it('exportQuestionsExcel 始终合并 responseType，默认空 params', async () => {
    await questionApi.exportQuestionsExcel()
    expect(mocks.get).toHaveBeenCalledWith('/teacher/questions/export', {
      params: {},
      responseType: 'blob',
    })
  })

  it('importQuestionsExcel 固定携带 multipart 头', async () => {
    const fd = new FormData()
    await questionApi.importQuestionsExcel(fd)
    const [url, body, config] = mocks.post.mock.calls[0] as [string, FormData, { headers: unknown }]
    expect(url).toBe('/teacher/questions/import-excel')
    expect(body).toBe(fd)
    expect(config).toEqual({ headers: { 'Content-Type': 'multipart/form-data' } })
  })
})

describe('question 模块导出完整性', () => {
  it('默认导出应包含全部具名接口', () => {
    const named = Object.keys(questionApi).filter((k) => k !== 'default')
    const defaultKeys = Object.keys(questionApi.default as object)
    const missing = named.filter((k) => !defaultKeys.includes(k))
    expect(missing).toEqual([])
  })
})
