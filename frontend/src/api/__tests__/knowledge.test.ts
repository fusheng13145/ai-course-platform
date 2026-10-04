import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as knowledgeApi from '@/api/knowledge'

/**
 * 知识点 API 契约测试（2026-10-04）
 * <p>本模块此前 0% 覆盖。注意两处易错点：
 * ① `getKnowledgePoints` / `getKnowledgeGraph` 把 courseId 放在 **query**，
 *    而 `getTree` / `getRootPoints` / `getRelationGraphData` 放在 **path**，
 *    混用会 404；② `getSubsequent` 的后端路径是 `successors`（复数语义），
 *    与请求函数名不同字面，最易在重构时被"顺手改名"改坏。</p>
 */

const mocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  del: vi.fn(),
}))

vi.mock('@/api/request', () => ({
  default: {
    get: mocks.get,
    post: mocks.post,
    put: mocks.put,
    delete: mocks.del,
  },
}))

const spyOf = { get: mocks.get, post: mocks.post, put: mocks.put, delete: mocks.del }

const POINT_BODY = { name: '二叉树', courseId: 3, difficulty: 'MEDIUM' }

/** 契约表：[导出名, HTTP 方法, 期望 URL, 调用实参, 期望传给 request 的完整实参] */
const CONTRACT: Array<[string, 'get' | 'post' | 'put' | 'delete', string, unknown[], unknown[]]> = [
  ['getKnowledgePoints', 'get', '/knowledge/points', [3], [{ params: { courseId: 3 } }]],
  ['createKnowledgePoint', 'post', '/knowledge/points', [POINT_BODY], [POINT_BODY]],
  ['updateKnowledgePoint', 'put', '/knowledge/points/4', [4, POINT_BODY], [POINT_BODY]],
  ['deleteKnowledgePoint', 'delete', '/knowledge/points/4', [4], []],
  ['getKnowledgeGraph', 'get', '/knowledge/points/graph', [3], [{ params: { courseId: 3 } }]],
  [
    'addRelation',
    'post',
    '/knowledge/relations',
    [{ sourceId: 1, targetId: 2 }],
    [{ sourceId: 1, targetId: 2 }],
  ],
  ['getTree', 'get', '/knowledge/points/tree/3', [3], []],
  ['getChildren', 'get', '/knowledge/points/4/children', [4], []],
  ['getPrerequisites', 'get', '/knowledge/points/4/prerequisites', [4], []],
  ['getSubsequent', 'get', '/knowledge/points/4/successors', [4], []],
  ['getMastery', 'get', '/knowledge/points/4/mastery/8', [4, 8], []],
  ['getRootPoints', 'get', '/knowledge/points/root/3', [3], []],
  ['getKnowledgeOverview', 'get', '/knowledge/overview', [3], [{ params: { courseId: 3 } }]],
  [
    'extractKnowledgePoints',
    'post',
    '/knowledge/extract',
    [{ courseId: 3, content: '文本' }],
    [{ courseId: 3, content: '文本' }],
  ],
  ['getRelationGraphData', 'get', '/knowledge/relations/graph/3', [3], []],
  [
    'planLearningPath',
    'post',
    '/knowledge/path/plan',
    [1, 2, 6],
    [{ sourceId: 1, targetId: 2, maxHops: 6 }],
  ],
]

describe('knowledge API 契约（方法 / URL / 参数）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.get.mockResolvedValue({ code: 200, data: [] })
    mocks.post.mockResolvedValue({ code: 200, data: {} })
    mocks.put.mockResolvedValue({ code: 200, data: {} })
    mocks.del.mockResolvedValue({ code: 200, data: {} })
  })

  for (const [name, method, url, callArgs, requestArgs] of CONTRACT) {
    it(`${name} 走 ${method.toUpperCase()} ${url}`, async () => {
      const fn = (knowledgeApi as unknown as Record<string, Function>)[name]
      expect(typeof fn).toBe('function')
      await fn(...callArgs)
      const spy = spyOf[method]
      expect(spy).toHaveBeenCalledTimes(1)
      expect(spy).toHaveBeenCalledWith(url, ...requestArgs)
    })
  }

  it('planLearningPath 省略 maxHops 时字段仍存在（后端 DTO 按缺省处理，不能变成 undefined 丢键）', async () => {
    await knowledgeApi.planLearningPath(1, 2)
    expect(mocks.post).toHaveBeenCalledWith('/knowledge/path/plan', {
      sourceId: 1,
      targetId: 2,
      maxHops: undefined,
    })
  })
})

describe('knowledge.importKnowledgePointsExcel - multipart 上传', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.post.mockResolvedValue({ code: 200, data: { success: 3, skipped: 1 } })
  })

  it('courseId 走 query，文件以 FormData 的 file 部件上报', async () => {
    await knowledgeApi.importKnowledgePointsExcel(3, new File(['x'], 'kp.xlsx'))

    expect(mocks.post).toHaveBeenCalledTimes(1)
    const [url, body] = mocks.post.mock.calls[0]
    expect(url).toBe('/teacher/knowledge-points/import-excel?courseId=3')
    expect(body).toBeInstanceOf(FormData)
    expect(body.get('file')).toBeInstanceOf(File)
  })
})

describe('knowledge 模块导出完整性', () => {
  it('默认导出覆盖全部具名接口', () => {
    const named = Object.keys(knowledgeApi).filter((k) => k !== 'default')
    const defaultKeys = Object.keys(knowledgeApi.default as object)
    expect(named.filter((k) => !defaultKeys.includes(k))).toEqual([])
  })
})
