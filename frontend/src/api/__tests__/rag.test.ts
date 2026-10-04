import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as ragApi from '@/api/rag'

/**
 * RAG 知识库接口契约测试（2026-10-04）
 * <p>此前 0% 覆盖。本模块每个函数都串了 `.then(unwrap)`：既锁「方法 + URL + 参数落点」，
 * 也要锁解包语义——content 走请求体、courseId/documentName 走 query，以及 unwrap
 * 对「有 data / 无 data / data 为 null」三种信封的不同返回。</p>
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
  [keyof typeof ragApi, 'get' | 'post' | 'put' | 'delete', string, unknown[], unknown[]]
> = [
  [
    'ragAsk',
    'post',
    '/rag/ask',
    [{ question: 'q', topK: 5 }],
    [null, { params: { question: 'q', topK: 5 } }],
  ],
  [
    'ragRetrieve',
    'post',
    '/rag/retrieve',
    [{ query: '召回' }],
    [null, { params: { query: '召回' } }],
  ],
  ['ragListDocuments', 'get', '/rag/documents', [3], [{ params: { courseId: 3 } }]],
  [
    'ragAddDocument',
    'post',
    '/rag/documents',
    [3, '第一章.md', '正文内容'],
    ['正文内容', { params: { courseId: 3, documentName: '第一章.md' } }],
  ],
  ['ragUpdateDocument', 'put', '/rag/documents/9', [9, '新正文'], ['新正文']],
  ['ragDeleteDocument', 'delete', '/rag/documents/9', [9], []],
  ['ragBuildIndex', 'post', '/rag/index/build/3', [3], []],
  ['ragClearIndex', 'delete', '/rag/index/3', [3], []],
]

describe('rag 契约（方法 / URL / 参数落点）', () => {
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
      const fn = (ragApi as unknown as Record<string, Function>)[name as string]
      expect(typeof fn).toBe('function')

      await fn(...callArgs)

      expect(spy).toHaveBeenCalledTimes(1)
      expect(spy).toHaveBeenCalledWith(url, ...requestArgs)
    })
  }

  it('问答类接口默认参数：未传时以空 params 发起，正文体固定为 null', async () => {
    await ragApi.ragAsk()
    expect(mocks.post).toHaveBeenCalledWith('/rag/ask', null, { params: {} })

    await ragApi.ragRetrieve()
    expect(mocks.post).toHaveBeenCalledWith('/rag/retrieve', null, { params: {} })
  })

  it('ragAddDocument 把 content 放请求体、courseId/documentName 放 query（勿放反）', async () => {
    await ragApi.ragAddDocument(3, 'doc.md', 'BODY')
    const [url, body, config] = mocks.post.mock.calls.at(-1) as [
      string,
      unknown,
      { params: unknown },
    ]
    expect(url).toBe('/rag/documents')
    expect(body).toBe('BODY')
    expect(config.params).toEqual({ courseId: 3, documentName: 'doc.md' })
  })
})

describe('rag - unwrap 解包语义', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('信封含 data 时返回内层业务数据', async () => {
    mocks.get.mockResolvedValue({ code: 200, data: [{ id: 1 }] })
    await expect(ragApi.ragListDocuments(3)).resolves.toEqual([{ id: 1 }])
  })

  it('信封不含 data 字段时原样返回整个响应（不误判为 undefined）', async () => {
    mocks.delete.mockResolvedValue({ code: 200, message: 'ok' })
    await expect(ragApi.ragDeleteDocument(9)).resolves.toEqual({ code: 200, message: 'ok' })
  })

  it('data 为 null 时返回 null（区别于缺失 data 的情况）', async () => {
    mocks.put.mockResolvedValue({ code: 200, data: null })
    await expect(ragApi.ragUpdateDocument(9, 'x')).resolves.toBeNull()
  })
})

describe('rag 模块导出完整性', () => {
  it('默认导出应包含全部具名接口', () => {
    const named = Object.keys(ragApi).filter((k) => k !== 'default')
    const defaultKeys = Object.keys(ragApi.default as object)
    const missing = named.filter((k) => !defaultKeys.includes(k))
    expect(missing).toEqual([])
  })
})
