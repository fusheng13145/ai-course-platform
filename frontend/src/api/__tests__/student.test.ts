import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as studentApi from '@/api/student'

/**
 * 学生端 API 契约测试（2026-10-04）
 * <p>本模块此前 0% 覆盖，共 26 个接口。学生端路径大量使用
 * `/student/courses/{courseId}/tasks/{taskId}/...` 的多段拼接，
 * 参数顺序写反（courseId/taskId）时后端直接 404 或命中他人任务点，
 * 因此在 mock 层锁定完整 URL。</p>
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

/** 契约表：[导出名, HTTP 方法, 期望 URL, 调用实参, 期望传给 request 的完整实参] */
const CONTRACT: Array<[string, 'get' | 'post' | 'put' | 'delete', string, unknown[], unknown[]]> = [
  ['getMyCourses', 'get', '/student/courses', [{ page: 1 }], [{ params: { page: 1 } }]],
  ['getCourseDetail', 'get', '/student/courses/3', [3], []],
  ['enrollCourse', 'post', '/student/courses/3/enroll', [3], []],
  ['unenrollCourse', 'delete', '/student/courses/3/enroll', [3], []],
  ['getPendingAssignments', 'get', '/student/assignments/pending', [], []],
  ['getCompletedAssignments', 'get', '/student/assignments/completed', [], []],
  ['getAssignmentDetail', 'get', '/student/assignments/5', [5], []],
  [
    'submitAssignment',
    'post',
    '/student/assignments/5/submit',
    [5, { content: '答案' }],
    [{ content: '答案' }],
  ],
  ['getAssignmentResult', 'get', '/student/assignments/5/result', [5], []],
  [
    'getWrongQuestions',
    'get',
    '/student/wrong-questions',
    [{ courseId: 3 }],
    [{ params: { courseId: 3 } }],
  ],
  [
    'addWrongQuestion',
    'post',
    '/student/wrong-questions',
    [{ questionId: 9 }],
    [{ questionId: 9 }],
  ],
  ['markMastered', 'put', '/student/wrong-questions/9/mastered', [9], []],
  ['removeWrongQuestion', 'delete', '/student/wrong-questions/9', [9], []],
  ['generatePractice', 'post', '/student/wrong-questions/practice', [{ count: 5 }], [{ count: 5 }]],
  ['getProgressOverview', 'get', '/student/progress/overview', [], []],
  [
    'getProgressCalendar',
    'get',
    '/student/progress/calendar',
    [{ month: '2026-10' }],
    [{ params: { month: '2026-10' } }],
  ],
  [
    'getProgressStatistics',
    'get',
    '/student/progress/statistics',
    [{ days: 30 }],
    [{ params: { days: 30 } }],
  ],
  ['getStudentDashboard', 'get', '/student/dashboard', [], []],
  ['getLearningCenter', 'get', '/student/courses/3/learning-center', [3], []],
  ['getProgressSummary', 'get', '/student/courses/3/progress-summary', [3], []],
  ['getVideoState', 'get', '/student/courses/3/tasks/7/video-state', [3, 7], []],
  [
    'reportVideoHeartbeat',
    'post',
    '/student/courses/3/tasks/7/video-heartbeat',
    [3, 7, { watched: 120, position: 88 }],
    [{ watched: 120, position: 88 }],
  ],
  [
    'submitQuiz',
    'post',
    '/student/courses/3/tasks/7/quiz-submit',
    [3, 7, { 11: 'A' }],
    [{ 11: 'A' }],
  ],
  ['getGrades', 'get', '/student/grades', [], []],
  ['getVisualizationData', 'get', '/student/analytics/visualization', [], []],
]

describe('student API 契约（方法 / URL / 参数）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.get.mockResolvedValue({ code: 200, data: [] })
    mocks.post.mockResolvedValue({ code: 200, data: {} })
    mocks.put.mockResolvedValue({ code: 200, data: {} })
    mocks.del.mockResolvedValue({ code: 200, data: {} })
  })

  for (const [name, method, url, callArgs, requestArgs] of CONTRACT) {
    it(`${name} 走 ${method.toUpperCase()} ${url}`, async () => {
      const fn = (studentApi as unknown as Record<string, Function>)[name]
      expect(typeof fn).toBe('function')
      await fn(...callArgs)
      const spy = spyOf[method]
      expect(spy).toHaveBeenCalledTimes(1)
      expect(spy).toHaveBeenCalledWith(url, ...requestArgs)
    })
  }
})

describe('student.markTaskComplete - completionType 可选体', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.post.mockResolvedValue({ code: 200, data: {} })
  })

  it('传 completionType 时以 JSON 体上报（后端 DTO 字段 completionType）', async () => {
    await studentApi.markTaskComplete(3, 7, 'VIDEO')
    expect(mocks.post).toHaveBeenCalledWith('/student/courses/3/tasks/7/complete', {
      completionType: 'VIDEO',
    })
  })

  it('不传 completionType 时发送空对象而非 undefined（避免 axios 丢掉 body 导致后端解析异常）', async () => {
    await studentApi.markTaskComplete(3, 7)
    expect(mocks.post).toHaveBeenCalledWith('/student/courses/3/tasks/7/complete', {})
  })
})

describe('student.getAllMyCourses - 完整课程目录', () => {
  it('按分页总数读取后续课程，而不是只显示默认第一页', async () => {
    vi.clearAllMocks()
    mocks.get
      .mockResolvedValueOnce({
        code: 200,
        data: { list: [{ id: 1, name: '课程一' }], total: 101 },
      })
      .mockResolvedValueOnce({
        code: 200,
        data: { list: [{ id: 2, name: '课程二' }], total: 101 },
      })

    await expect(studentApi.getAllMyCourses()).resolves.toEqual([
      { id: 1, name: '课程一' },
      { id: 2, name: '课程二' },
    ])
    expect(mocks.get).toHaveBeenNthCalledWith(1, '/student/courses', {
      params: { page: 1, size: 100 },
    })
    expect(mocks.get).toHaveBeenNthCalledWith(2, '/student/courses', {
      params: { page: 2, size: 100 },
    })
  })
})

describe('student 模块导出完整性', () => {
  it('默认导出覆盖全部具名接口', () => {
    const named = Object.keys(studentApi).filter((k) => k !== 'default')
    const defaultKeys = Object.keys(studentApi.default as object)
    expect(named.filter((k) => !defaultKeys.includes(k))).toEqual([])
  })
})
