import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as chapterApi from '@/api/chapter'

/**
 * 章节 API 契约测试（2026-10-04）
 * <p>此前 0% 覆盖。除路径参数插值外，两处需专门锁定：
 * ① `importChaptersExcel` 手工拼装 FormData 并把 courseId 拼进 query；
 * ② `batchReorder` 当前打到 `/batch-reorder`（无 /chapters 前缀），锁定真实行为防漂移。</p>
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
  [keyof typeof chapterApi, 'get' | 'post' | 'put' | 'delete', string, unknown[], unknown[]]
> = [
  ['getChapters', 'get', '/chapters/course/3', [3], []],
  ['getChapterById', 'get', '/chapters/7', [7], []],
  ['createChapter', 'post', '/chapters', [{ title: '第1章' }], [{ title: '第1章' }]],
  ['updateChapter', 'put', '/chapters/7', [7, { title: '改后' }], [{ title: '改后' }]],
  ['deleteChapter', 'delete', '/chapters/7', [7], []],
  ['batchReorder', 'put', '/batch-reorder', [{ order: [1, 2] }], [{ order: [1, 2] }]],
]

describe('chapter 契约（方法 / URL / 路径参数插值）', () => {
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
      const fn = (chapterApi as unknown as Record<string, Function>)[name as string]
      expect(typeof fn).toBe('function')

      await fn(...callArgs)

      expect(spy).toHaveBeenCalledTimes(1)
      expect(spy).toHaveBeenCalledWith(url, ...requestArgs)
    })
  }

  it('路径参数插值：数字/字符串 id 均正确内联进 URL', async () => {
    await chapterApi.getChapterById('abc-123')
    expect(mocks.get).toHaveBeenCalledWith('/chapters/abc-123')

    await chapterApi.getChapters(0)
    expect(mocks.get).toHaveBeenCalledWith('/chapters/course/0')
  })
})

describe('chapter.importChaptersExcel - 文件上传', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.post.mockResolvedValue({ code: 200, data: {} })
  })

  it('把 file 装进 FormData 的 file 字段，courseId 拼进 URL query', async () => {
    const file = new File(['xlsx-bytes'], 'chapters.xlsx', {
      type: 'application/vnd.ms-excel',
    })

    await chapterApi.importChaptersExcel(5, file)

    expect(mocks.post).toHaveBeenCalledTimes(1)
    const [url, body] = mocks.post.mock.calls[0] as [string, FormData]
    expect(url).toBe('/teacher/chapters/import-excel?courseId=5')
    expect(body).toBeInstanceOf(FormData)
    expect(body.get('file')).toBe(file)
  })

  it('URL 拼接使用调用方传入的 courseId，不做额外编码', async () => {
    const file = new Blob(['x'])
    await chapterApi.importChaptersExcel(12, file)
    expect(mocks.post.mock.calls[0][0]).toBe('/teacher/chapters/import-excel?courseId=12')
  })
})

describe('chapter 模块导出完整性', () => {
  it('默认导出应包含全部具名接口', () => {
    const named = Object.keys(chapterApi).filter((k) => k !== 'default')
    const defaultKeys = Object.keys(chapterApi.default as object)
    const missing = named.filter((k) => !defaultKeys.includes(k))
    expect(missing).toEqual([])
  })
})
