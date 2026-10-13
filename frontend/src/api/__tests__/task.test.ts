import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as taskApi from '@/api/task'

/**
 * 任务 API 契约测试（2026-10-04）
 * <p>本模块此前 0% 覆盖。`listByCourse` 是 CourseTaskPanel 一直在调、但 api/task.ts 里
 * 根本不存在的方法（面板取任务列表必然 TypeError），补上后逐条锁定 URL 形态：
 * 按课程查询走 path 段 `/tasks/course/{id}`，而条件分页走 query `/tasks?courseId=`。</p>
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

const spyOf: Record<string, ReturnType<typeof vi.fn>> = {
  get: mocks.get,
  post: mocks.post,
  put: mocks.put,
  delete: mocks.del,
}

beforeEach(() => {
  Object.values(spyOf).forEach((fn) => fn.mockReset())
})

const TASK_BODY = { title: '第一章作业', courseId: 3, deadline: '2026-10-20 23:59:59' }

/** 契约表：[导出名, HTTP 方法, 期望 URL, 调用实参, 期望传给 request 的其余实参] */
const CONTRACT: Array<[string, 'get' | 'post' | 'put' | 'delete', string, unknown[], unknown[]]> = [
  ['getTaskList', 'get', '/tasks', [{ page: 1, size: 10 }], [{ params: { page: 1, size: 10 } }]],
  [
    'listByCourse',
    'get',
    '/tasks/course/3',
    [3, { page: 2, size: 20 }],
    [{ params: { page: 2, size: 20 } }],
  ],
  ['getTaskById', 'get', '/tasks/11', [11], []],
  ['createTask', 'post', '/tasks', [TASK_BODY], [TASK_BODY]],
  ['updateTask', 'put', '/tasks/11', [11, TASK_BODY], [TASK_BODY]],
  ['deleteTask', 'delete', '/tasks/11', [11], []],
  ['publishTask', 'post', '/tasks/11/publish', [11], []],
  [
    'extendDeadline',
    'post',
    '/tasks/11/extend-deadline',
    [11, { deadline: '2026-10-31 23:59:59' }],
    [{ deadline: '2026-10-31 23:59:59' }],
  ],
  ['copyTask', 'post', '/tasks/11/copy', [11, { count: 2 }], [{ count: 2 }]],
  ['getPendingGrading', 'get', '/tasks/pending-grading', [7], [{ params: { teacherId: 7 } }]],
  ['getCompletionStats', 'get', '/tasks/11/completion-stats', [11], []],
]

describe('task API 契约（方法 / URL / 参数）', () => {
  it.each(CONTRACT)('%s → %s %s', async (name, method, url, callArgs, requestArgs) => {
    const fn = (taskApi as unknown as Record<string, Function>)[name]
    await fn(...callArgs)
    expect(spyOf[method]).toHaveBeenCalledTimes(1)
    expect(spyOf[method]).toHaveBeenCalledWith(url, ...requestArgs)
  })

  it('getAllTasks follows all course-filtered pages', async () => {
    mocks.get
      .mockResolvedValueOnce({ code: 200, data: { list: [{ id: 1 }], total: 101 } })
      .mockResolvedValueOnce({ code: 200, data: { list: [{ id: 2 }], total: 101 } })

    await expect(taskApi.getAllTasks({ courseId: 3 })).resolves.toEqual([{ id: 1 }, { id: 2 }])
    expect(mocks.get).toHaveBeenNthCalledWith(1, '/tasks', {
      params: { courseId: 3, page: 1, size: 100 },
    })
    expect(mocks.get).toHaveBeenNthCalledWith(2, '/tasks', {
      params: { courseId: 3, page: 2, size: 100 },
    })
  })

  it('getTaskList 省略参数时退化为 { params: undefined }，不伪造空对象改变请求形状', async () => {
    await taskApi.getTaskList()
    expect(mocks.get).toHaveBeenCalledWith('/tasks', { params: undefined })
  })

  it('getPendingGrading 不带 teacherId 时 params 中键仍存在（后端按缺省口径处理）', async () => {
    await taskApi.getPendingGrading()
    expect(mocks.get).toHaveBeenCalledWith('/tasks/pending-grading', {
      params: { teacherId: undefined },
    })
  })

  it('默认导出覆盖全部具名接口', async () => {
    const named = Object.keys(taskApi).filter((k) => k !== 'default')
    const defaultKeys = Object.keys(taskApi.default as object)
    expect(named.filter((k) => !defaultKeys.includes(k))).toEqual([])
  })
})
