import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as resourceApi from '@/api/resource'

/**
 * 课程资源 API 契约 + 上传归一化测试（2026-10-04）
 * <p>本模块此前 0% 覆盖，并承载 P0-5 缺陷修复：
 * `/resources/upload` 声明 `multipart/form-data`，但仓库里存在两套调用——
 * `useResourceManagement` 传 FormData（正确）、`CourseResourcePanel` 传普通对象
 * （后端 MultipartFile 取不到部件，必然 400）；
 * 且 `fd.append('courseId', null)` 会把 null 变成字符串 "null" 上报。
 * 现在由 `buildUploadForm` 在 API 层统一收敛并拒发非法请求。</p>
 */

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  del: vi.fn(),
}))

vi.mock('@/api/request', () => ({
  default: {
    get: mocks.get,
    post: mocks.post,
    put: mocks.put,
    patch: mocks.patch,
    delete: mocks.del,
  },
}))

const spyOf = {
  get: mocks.get,
  post: mocks.post,
  put: mocks.put,
  patch: mocks.patch,
  delete: mocks.del,
}

/** 契约表：[导出名, HTTP 方法, 期望 URL, 调用实参, 期望传给 request 的完整实参] */
const CONTRACT: Array<
  [string, 'get' | 'post' | 'put' | 'patch' | 'delete', string, unknown[], unknown[]]
> = [
  ['getResourceList', 'get', '/resources/course/3', [3], []],
  ['getResourceById', 'get', '/resources/7', [7], []],
  ['deleteResource', 'delete', '/resources/7', [7], []],
  ['getResourceTree', 'get', '/resources/tree/3', [3], []],
  [
    'searchResources',
    'get',
    '/resources/search',
    [{ keyword: '课件' }],
    [{ params: { keyword: '课件' } }],
  ],
  ['getResourceStats', 'get', '/resources/stats/course/3', [3], []],
  ['getChapterResources', 'get', '/resources/chapter/12', [12], []],
]

describe('resource API 契约（方法 / URL / 参数）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.get.mockResolvedValue({ code: 200, data: [] })
    mocks.del.mockResolvedValue({ code: 200, data: null })
  })

  for (const [name, method, url, callArgs, requestArgs] of CONTRACT) {
    it(`${name} 走 ${method.toUpperCase()} ${url}`, async () => {
      const fn = (resourceApi as unknown as Record<string, Function>)[name]
      expect(typeof fn).toBe('function')
      await fn(...callArgs)
      const spy = spyOf[method]
      expect(spy).toHaveBeenCalledTimes(1)
      expect(spy).toHaveBeenCalledWith(url, ...requestArgs)
    })
  }

  it('downloadResource 必须以 responseType=blob 请求（否则二进制被当文本解析成乱码）', async () => {
    await resourceApi.downloadResource(7)
    expect(mocks.get).toHaveBeenCalledWith('/resources/download/7', { responseType: 'blob' })
  })

  it('toggleVisibility 用 PATCH + JSON 体 {visibleToAll}（后端 @RequestBody ResourceVisibilityDTO）', async () => {
    await resourceApi.toggleVisibility(7, true)
    expect(mocks.patch).toHaveBeenCalledWith('/resources/7/visibility', { visibleToAll: true })
  })

  it('toggleVisibility 把假值归一为 false（后端字段是 Boolean，不能上报 "0"/"" 之类）', async () => {
    await resourceApi.toggleVisibility(7, 0)
    expect(mocks.patch).toHaveBeenCalledWith('/resources/7/visibility', { visibleToAll: false })
  })
})

describe('resource.buildUploadForm - 上传入参归一化（P0-5）', () => {
  const file = () => new File(['data'], 'note.pdf', { type: 'application/pdf' })

  it('普通对象入参也要产出合法 FormData（原实现直接把对象发给 multipart 接口 → 400）', () => {
    const f = file()
    const fd = resourceApi.buildUploadForm({ file: f, courseId: 3, description: '第一章课件' })

    expect(fd).toBeInstanceOf(FormData)
    expect(fd.get('file')).toBe(f)
    expect(fd.get('courseId')).toBe('3')
    expect(fd.get('description')).toBe('第一章课件')
  })

  it('已经是 FormData 的入参同样归一化，字段不丢失', () => {
    const f = file()
    const raw = new FormData()
    raw.append('file', f)
    raw.append('courseId', 3 as unknown as string)
    raw.append('description', 'x')

    const fd = resourceApi.buildUploadForm(raw)

    expect(fd.get('file')).toBe(f)
    expect(fd.get('courseId')).toBe('3')
    expect(fd.get('description')).toBe('x')
  })

  it('数字 courseId 转字符串上报（FormData 不允许保留 number 语义差异）', () => {
    const fd = resourceApi.buildUploadForm({ file: file(), courseId: 42 })
    expect(fd.get('courseId')).toBe('42')
  })

  it.each([
    ['null', null],
    ['undefined', undefined],
    ['空串', ''],
    ['字符串 null', 'null'],
    ['字符串 undefined', 'undefined'],
    ['空白串', '   '],
  ])('courseId 为 %s 时拒发并抛出可读错误（避免后端 Long 解析 400）', (_label, courseId) => {
    expect(() => resourceApi.buildUploadForm({ file: file(), courseId })).toThrow(
      '请先选择所属课程后再上传',
    )
  })

  it('未选文件时抛出「请选择要上传的文件」', () => {
    expect(() => resourceApi.buildUploadForm({ courseId: 3 })).toThrow('请选择要上传的文件')
    expect(() => resourceApi.buildUploadForm({ file: null, courseId: 3 })).toThrow(
      '请选择要上传的文件',
    )
  })

  it('visibleToAll 仅在显式给出时附加（缺省不写键，交由后端默认值）', () => {
    const without = resourceApi.buildUploadForm({ file: file(), courseId: 3 })
    expect(without.has('visibleToAll')).toBe(false)

    const withFalse = resourceApi.buildUploadForm({
      file: file(),
      courseId: 3,
      visibleToAll: false,
    })
    expect(withFalse.get('visibleToAll')).toBe('false')
  })

  it('description 为 null 时上报空串而不是字符串 "null"', () => {
    const fd = resourceApi.buildUploadForm({ file: file(), courseId: 3, description: null })
    expect(fd.get('description')).toBe('')
  })

  it('非法入参（null / 非对象）也走「未选文件」分支，不抛类型错误', () => {
    expect(() => resourceApi.buildUploadForm(null)).toThrow('请选择要上传的文件')
    expect(() => resourceApi.buildUploadForm('x' as unknown as object)).toThrow(
      '请选择要上传的文件',
    )
  })
})

describe('resource.uploadResource - 请求层收口', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.post.mockResolvedValue({ code: 200, data: { id: 88 } })
  })

  it('无论传 FormData 还是普通对象，实际发出的都是 FormData 且带 multipart 头', async () => {
    const f = new File(['a'], 'a.pdf')

    await resourceApi.uploadResource({ file: f, courseId: 3 })
    const [url, body, cfg] = mocks.post.mock.calls[0]
    expect(url).toBe('/resources/upload')
    expect(body).toBeInstanceOf(FormData)
    expect(body.get('courseId')).toBe('3')
    expect(cfg).toEqual({ headers: { 'Content-Type': 'multipart/form-data' } })

    const raw = new FormData()
    raw.append('file', f)
    raw.append('courseId', '4')
    await resourceApi.uploadResource(raw)
    expect(mocks.post.mock.calls[1][1]).toBeInstanceOf(FormData)
    expect(mocks.post.mock.calls[1][1].get('courseId')).toBe('4')
  })

  it('courseId 缺失时 Promise 直接 reject，且**一个请求都不发**（P0-5 拒发语义）', async () => {
    await expect(
      resourceApi.uploadResource({ file: new File(['a'], 'a.pdf'), courseId: null }),
    ).rejects.toThrow('请先选择所属课程后再上传')
    expect(mocks.post).not.toHaveBeenCalled()
  })
})

describe('resource 模块导出完整性', () => {
  it('默认导出覆盖全部具名接口（含 buildUploadForm）', () => {
    const named = Object.keys(resourceApi).filter((k) => k !== 'default')
    const defaultKeys = Object.keys(resourceApi.default as object)
    expect(named.filter((k) => !defaultKeys.includes(k))).toEqual([])
  })
})
