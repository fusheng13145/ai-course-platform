import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import * as aiApi from '@/api/ai'

/**
 * AI 接口契约测试（2026-10-04）
 * <p>此前 0% 覆盖。除薄封装的 10 个 REST 接口外，`qaStream` 是模块内唯一的真实逻辑：
 * 原生 fetch + ReadableStream 逐块解析 SSE，涉及行缓冲拼接、`[DONE]` 提前返回、
 * JSON/纯文本回落、注释行跳过与 AbortError 静默。这里用桩 fetch 覆盖全部分支。</p>
 */

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}))

vi.mock('@/api/request', () => ({
  default: {
    get: mocks.get,
    post: mocks.post,
    put: vi.fn(),
    patch: vi.fn(),
    delete: vi.fn(),
  },
}))

/** 契约表：[导出名, HTTP 方法, 期望 URL, 调用实参, 期望传给 request 的完整实参] */
const CONTRACT: Array<[keyof typeof aiApi, 'get' | 'post', string, unknown[], unknown[]]> = [
  ['qaAsk', 'post', '/ai/qa/ask', [{ q: '什么是栈' }], [{ q: '什么是栈' }]],
  ['qaContinue', 'post', '/ai/qa/continue', [{ sessionId: 1 }], [{ sessionId: 1 }]],
  ['getSessions', 'get', '/ai/qa/sessions', [], []],
  ['essayCorrect', 'post', '/ai/essay/correct', [{ text: '作文' }], [{ text: '作文' }]],
  ['essayPolish', 'post', '/ai/essay/polish', [{ text: '作文' }], [{ text: '作文' }]],
  ['learningRecommendations', 'get', '/ai/recommendations/learning', [], []],
  [
    'questionRecommendations',
    'get',
    '/ai/recommendations/questions',
    [{ courseId: 3 }],
    [{ params: { courseId: 3 } }],
  ],
  [
    'weakPointsAnalysis',
    'get',
    '/ai/analysis/weak-points',
    [{ courseId: 3 }],
    [{ params: { courseId: 3 } }],
  ],
  [
    'learningAdvice',
    'get',
    '/ai/advice/learning',
    [{ scope: 'week' }],
    [{ params: { scope: 'week' } }],
  ],
  [
    'generateLearningPath',
    'post',
    '/ai/learning-path/generate',
    [{ courseId: 3 }],
    [{ courseId: 3 }],
  ],
]

describe('ai REST 契约（方法 / URL / 参数）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.get.mockResolvedValue({ code: 200, data: [] })
    mocks.post.mockResolvedValue({ code: 200, data: {} })
  })

  for (const [name, method, url, callArgs, requestArgs] of CONTRACT) {
    it(`${String(name)} 走 ${method.toUpperCase()} ${url}`, async () => {
      const spy = method === 'get' ? mocks.get : mocks.post
      const fn = (aiApi as unknown as Record<string, Function>)[name as string]
      expect(typeof fn).toBe('function')

      await fn(...callArgs)

      expect(spy).toHaveBeenCalledTimes(1)
      expect(spy).toHaveBeenCalledWith(url, ...requestArgs)
    })
  }

  it('推荐/分析类接口未传参时以空 params 发起', async () => {
    await aiApi.questionRecommendations()
    expect(mocks.get).toHaveBeenCalledWith('/ai/recommendations/questions', { params: {} })

    await aiApi.learningAdvice()
    expect(mocks.get).toHaveBeenCalledWith('/ai/advice/learning', { params: {} })
  })
})

// ================= qaStream：SSE 流式解析 =================

/** 由字符串片段构造一个桩 Response，其 body 逐块吐给 reader */
function makeSSEResponse(chunks: string[], init: Record<string, unknown> = {}) {
  const encoder = new TextEncoder()
  let cursor = 0
  return {
    ok: true,
    status: 200,
    ...init,
    body: {
      getReader() {
        return {
          read() {
            if (cursor < chunks.length) {
              return Promise.resolve({ done: false, value: encoder.encode(chunks[cursor++]) })
            }
            return Promise.resolve({ done: true, value: undefined })
          },
        }
      },
    },
  }
}

// fetch().then 内部只 await 已 resolve 的 Promise，故一次宏任务边界即可让整条链跑完
const flush = async () => {
  for (let i = 0; i < 5; i++) await new Promise((r) => setTimeout(r, 0))
}

describe('ai.qaStream - SSE 流式解析', () => {
  let errSpy: ReturnType<typeof vi.spyOn>
  beforeEach(() => {
    vi.clearAllMocks()
    errSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    errSpy.mockRestore()
  })

  it('逐条 data 事件解析 JSON token 并回调 onToken，收尾 onDone', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          makeSSEResponse(['data: {"token":"你"}\n\n', 'data: {"token":"好"}\n\n']),
        ),
    )
    const onToken = vi.fn()
    const onDone = vi.fn()

    aiApi.qaStream({ question: 'q' }, { onToken, onDone })
    await flush()

    expect(onToken.mock.calls.map((c) => c[0])).toEqual(['你', '好'])
    expect(onDone).toHaveBeenCalledTimes(1)
  })

  it('[DONE] 终止流：后续 token 不再投递', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          makeSSEResponse(['data: {"token":"A"}\n\n', 'data: [DONE]\n\ndata: {"token":"B"}\n\n']),
        ),
    )
    const onToken = vi.fn()
    const onDone = vi.fn()

    aiApi.qaStream({ question: 'q' }, { onToken, onDone })
    await flush()

    expect(onToken).toHaveBeenCalledTimes(1)
    expect(onToken).toHaveBeenCalledWith('A')
    expect(onDone).toHaveBeenCalledTimes(1)
  })

  it('跨 chunk 断行拼接：半行数据在下一块补全后才解析', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(makeSSEResponse(['data: {"token":"A"}\ndata: {"tok', 'en":"B"}\n\n'])),
    )
    const onToken = vi.fn()
    const onDone = vi.fn()

    aiApi.qaStream({ question: 'q' }, { onToken, onDone })
    await flush()

    expect(onToken.mock.calls.map((c) => c[0])).toEqual(['A', 'B'])
    expect(onDone).toHaveBeenCalledTimes(1)
  })

  it('sources 末帧：回调 onSources 且不触发 onToken，随后正常 onDone', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          makeSSEResponse([
            'data: {"token":"答"}\n\n',
            'data: {"sources":[{"documentId":3,"documentName":"第1章","relevanceScore":0.8,"snippet":"片段"}]}\n\n',
          ]),
        ),
    )
    const onToken = vi.fn()
    const onSources = vi.fn()
    const onDone = vi.fn()

    aiApi.qaStream({ question: 'q', courseId: 7 }, { onToken, onSources, onDone })
    await flush()

    expect(onToken).toHaveBeenCalledTimes(1)
    expect(onToken).toHaveBeenCalledWith('答')
    expect(onSources).toHaveBeenCalledTimes(1)
    expect(onSources.mock.calls[0][0]).toEqual([
      { documentId: 3, documentName: '第1章', relevanceScore: 0.8, snippet: '片段' },
    ])
    expect(onDone).toHaveBeenCalledTimes(1)
  })

  it('空 sources 末帧：仍回调 onSources([]) 以区分「检索为空」与「缺帧」', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          makeSSEResponse(['data: {"token":"A"}\n\n', 'data: {"sources":[]}\n\n']),
        ),
    )
    const onSources = vi.fn()

    aiApi.qaStream({ question: 'q' }, { onSources })
    await flush()

    expect(onSources).toHaveBeenCalledTimes(1)
    expect(onSources).toHaveBeenCalledWith([])
  })

  it('非 JSON 的 data 走纯文本回落，仍回调 onToken', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(makeSSEResponse(['data: 纯文本片段\n\n'])))
    const onToken = vi.fn()

    aiApi.qaStream({ question: 'q' }, { onToken })
    await flush()

    expect(onToken).toHaveBeenCalledWith('纯文本片段')
  })

  it('跳过注释行与空行；token 为空的 JSON 不触发 onToken', async () => {
    vi.stubGlobal(
      'fetch',
      vi
        .fn()
        .mockResolvedValue(
          makeSSEResponse([': keep-alive\n\n', 'data: {}\n\n', 'data: {"token":""}\n\n']),
        ),
    )
    const onToken = vi.fn()
    const onDone = vi.fn()

    aiApi.qaStream({ question: 'q' }, { onToken, onDone })
    await flush()

    expect(onToken).not.toHaveBeenCalled()
    expect(onDone).toHaveBeenCalledTimes(1)
  })

  it('HTTP 非 2xx：抛错后走 onError，并记录日志', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 503 }))
    const onError = vi.fn()

    aiApi.qaStream({ question: 'q' }, { onError })
    await flush()

    expect(onError).toHaveBeenCalledTimes(1)
    expect(String(onError.mock.calls[0][0])).toContain('HTTP 503')
    expect(errSpy).toHaveBeenCalled()
  })

  it('主动中止（AbortError）静默：不触发 onError', async () => {
    const abortErr = Object.assign(new Error('aborted'), { name: 'AbortError' })
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(abortErr))
    const onError = vi.fn()

    aiApi.qaStream({ question: 'q' }, { onError })
    await flush()

    expect(onError).not.toHaveBeenCalled()
    expect(errSpy).toHaveBeenCalled()
  })

  it('请求走 GET /api/ai/qa/stream，携带 SSE 头与取消信号', async () => {
    const fetchMock = vi.fn().mockResolvedValue(makeSSEResponse([]))
    vi.stubGlobal('fetch', fetchMock)

    const controller = aiApi.qaStream({ question: 'hi' }, {})
    await flush()

    expect(controller).toBeInstanceOf(AbortController)
    const [url, options] = fetchMock.mock.calls[0]
    expect(options.method).toBe('GET')
    expect(options.credentials).toBe('include')
    expect(options.headers.Accept).toBe('text/event-stream')
    expect(options.signal).toBe(controller.signal)
    expect(url.pathname).toBe('/api/ai/qa/stream')
  })

  it('query 参数拼接：跳过 undefined/null，其余转字符串', async () => {
    const fetchMock = vi.fn().mockResolvedValue(makeSSEResponse([]))
    vi.stubGlobal('fetch', fetchMock)

    aiApi.qaStream({ question: '搜索', sessionId: 12, context: undefined, flag: true }, {})
    await flush()

    const url = fetchMock.mock.calls[0][0]
    expect(url.searchParams.get('question')).toBe('搜索')
    expect(url.searchParams.get('sessionId')).toBe('12')
    expect(url.searchParams.get('flag')).toBe('true')
    expect(url.searchParams.has('context')).toBe(false)
  })
})

describe('ai 模块导出完整性', () => {
  it('默认导出应包含全部具名接口', () => {
    const named = Object.keys(aiApi).filter((k) => k !== 'default')
    const defaultKeys = Object.keys(aiApi.default as object)
    const missing = named.filter((k) => !defaultKeys.includes(k))
    expect(missing).toEqual([])
  })
})
