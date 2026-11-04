import { describe, it, expect, vi, beforeEach } from 'vitest'
import * as teacherApi from '@/api/teacher'

/**
 * 教师端 API 契约测试（2026-10-04）
 * <p>本模块此前 0% 覆盖。教师端 34 个接口全部是「方法 + URL + 参数」的薄封装，
 * 一旦路径写错（如漏掉复数 s、把 courseId 拼进 query 而非 path），
 * 页面只会显示空数据，很难在 UI 层发现。这里以契约表逐项锁定
 * HTTP 方法 / URL / 请求体 / query，全部走 mock，不发真实网络请求。</p>
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

const COURSE_BODY = { name: '数据结构', code: 'DS101' }
const ASSIGNMENT_BODY = { title: '第一次作业', fullScore: 100 }
const QUESTION_BODY = { content: '题干', type: 'SINGLE_CHOICE' }

/** 契约表：[导出名, HTTP 方法, 期望 URL, 调用实参, 期望传给 request 的完整实参] */
const CONTRACT: Array<
  [keyof typeof teacherApi, 'get' | 'post' | 'put' | 'delete', string, unknown[], unknown[]]
> = [
  ['createCourse', 'post', '/teacher/courses', [COURSE_BODY], [COURSE_BODY]],
  [
    'getMyCourses',
    'get',
    '/teacher/courses',
    [{ page: 2, size: 20 }],
    [{ params: { page: 2, size: 20 } }],
  ],
  ['getCourseDetail', 'get', '/teacher/courses/3', [3], []],
  ['updateCourse', 'put', '/teacher/courses/3', [3, COURSE_BODY], [COURSE_BODY]],
  ['deleteCourse', 'delete', '/teacher/courses/3', [3], []],
  [
    'addChapter',
    'post',
    '/teacher/courses/3/chapters',
    [3, { title: '第1章' }],
    [{ title: '第1章' }],
  ],
  [
    'updateChapter',
    'put',
    '/teacher/courses/3/chapters/7',
    [3, 7, { title: '改后' }],
    [{ title: '改后' }],
  ],
  ['deleteChapter', 'delete', '/teacher/courses/3/chapters/7', [3, 7], []],
  ['createAssignment', 'post', '/teacher/assignments', [ASSIGNMENT_BODY], [ASSIGNMENT_BODY]],
  [
    'getAssignments',
    'get',
    '/teacher/assignments',
    [{ courseId: 3 }],
    [{ params: { courseId: 3 } }],
  ],
  ['getAssignmentDetail', 'get', '/teacher/assignments/5', [5], []],
  ['updateAssignment', 'put', '/teacher/assignments/5', [5, ASSIGNMENT_BODY], [ASSIGNMENT_BODY]],
  ['deleteAssignment', 'delete', '/teacher/assignments/5', [5], []],
  [
    'getSubmissions',
    'get',
    '/teacher/assignments/5/submissions',
    [5, { page: 1 }],
    [{ params: { page: 1 } }],
  ],
  [
    'gradeSubmission',
    'put',
    '/teacher/assignments/5/submissions/9',
    [5, 9, { score: 88 }],
    [{ score: 88 }],
  ],
  ['publishAssignment', 'put', '/teacher/assignments/5/publish', [5], []],
  ['createQuestion', 'post', '/teacher/questions', [QUESTION_BODY], [QUESTION_BODY]],
  [
    'getQuestions',
    'get',
    '/teacher/questions',
    [{ difficulty: 2 }],
    [{ params: { difficulty: 2 } }],
  ],
  ['getQuestionDetail', 'get', '/teacher/questions/2', [2], []],
  ['updateQuestion', 'put', '/teacher/questions/2', [2, QUESTION_BODY], [QUESTION_BODY]],
  ['deleteQuestion', 'delete', '/teacher/questions/2', [2], []],
  ['importQuestions', 'post', '/teacher/questions/import', [{ rows: [] }], [{ rows: [] }]],
  [
    'exportQuestions',
    'get',
    '/teacher/questions/export',
    [{ courseId: 3 }],
    [{ params: { courseId: 3 } }],
  ],
  ['getClassAnalytics', 'get', '/teacher/analytics/class/3', [3], []],
  ['getStudentAnalytics', 'get', '/teacher/analytics/students/8', [8], []],
  ['getAssignmentAnalytics', 'get', '/teacher/analytics/assignments/5', [5], []],
  [
    'getKnowledgePointAnalytics',
    'get',
    '/teacher/analytics/knowledge-points',
    [{ courseId: 3 }],
    [{ params: { courseId: 3 } }],
  ],
  ['exportReport', 'get', '/teacher/analytics/report/export?courseId=3', [3], []],
  ['getTeacherDashboard', 'get', '/teacher/dashboard/overview', [], []],
  ['getMyCourseSummaries', 'get', '/teacher/courses/summaries', [], []],
  ['getCourseDashboard', 'get', '/teacher/courses/3/dashboard', [3], []],
  ['getChapterTree', 'get', '/teacher/courses/3/chapters/tree', [3], []],
  [
    'generateOutline',
    'post',
    '/teacher/courses/ai/generate-outline',
    [{ topic: '图论', chapterCount: 8 }],
    [null, { params: { topic: '图论', chapterCount: 8 } }],
  ],
  [
    'cloneCourse',
    'post',
    '/teacher/courses/3/clone',
    [3, '数据结构-副本'],
    [null, { params: { newName: '数据结构-副本' } }],
  ],
]

describe('teacher API 契约（方法 / URL / 参数）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.get.mockResolvedValue({ code: 200, data: [] })
    mocks.post.mockResolvedValue({ code: 200, data: {} })
    mocks.put.mockResolvedValue({ code: 200, data: {} })
    mocks.del.mockResolvedValue({ code: 200, data: {} })
  })

  for (const [name, method, url, callArgs, requestArgs] of CONTRACT) {
    it(`${String(name)} 走 ${method.toUpperCase()} ${url}`, async () => {
      const spy = spyOf[method]
      // 契约表按「当前实现」锁定；这里统一转成 any 以避开动态索引的类型噪声
      const fn = (teacherApi as unknown as Record<string, Function>)[name as string]
      expect(typeof fn).toBe('function')

      await fn(...callArgs)

      expect(spy).toHaveBeenCalledTimes(1)
      expect(spy).toHaveBeenCalledWith(url, ...requestArgs)
    })
  }

  it('默认参数：查询类接口不传 params 时仍发送空对象（URL 不带 query）', async () => {
    await teacherApi.getMyCourses()
    expect(mocks.get).toHaveBeenCalledWith('/teacher/courses', { params: {} })

    await teacherApi.getTeacherDashboard()
    expect(mocks.get).toHaveBeenLastCalledWith('/teacher/dashboard/overview')
  })
})

describe('teacher.getCourseClasses - 班级维度聚合', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('把 pending 数按 classroomId 映射进班级卡片（真实数据来源，非占位）', async () => {
    mocks.get.mockImplementation((url: string) => {
      if (url.endsWith('/pending')) {
        return Promise.resolve({ data: [{ classroomId: 11, pendingCount: 4 }] })
      }
      return Promise.resolve({
        data: [
          { id: 11, name: '计科2101', studentCount: 38 },
          { id: 12, name: '计科2102' },
        ],
      })
    })

    const list = await teacherApi.getCourseClasses(3)

    expect(mocks.get).toHaveBeenCalledWith('/classrooms/by-course/3')
    expect(list).toEqual([
      { key: 11, name: '计科2101', students: 38, pending: 4, schedule: '' },
      { key: 12, name: '计科2102', students: 0, pending: 0, schedule: '' },
    ])
  })

  it('pending 端点失败时回退 0，不影响班级列表主流程', async () => {
    mocks.get.mockImplementation((url: string) => {
      if (url.endsWith('/pending')) return Promise.reject(new Error('503'))
      return Promise.resolve({ data: [{ id: 11, name: '计科2101', studentCount: 5 }] })
    })

    const list = await teacherApi.getCourseClasses(3)

    expect(list).toEqual([{ key: 11, name: '计科2101', students: 5, pending: 0, schedule: '' }])
  })

  it('班级/待批接口返回非数组时返回空列表，不抛异常', async () => {
    mocks.get.mockResolvedValue({ data: null })

    await expect(teacherApi.getCourseClasses(3)).resolves.toEqual([])
  })
})

describe('teacher 模块导出完整性', () => {
  it('默认导出应包含全部具名接口，避免调用方 `(await import(...)).default.xxx` 取到 undefined', () => {
    const named = Object.keys(teacherApi).filter((k) => k !== 'default')
    const defaultKeys = Object.keys(teacherApi.default as object)
    const missing = named.filter((k) => !defaultKeys.includes(k))
    expect(missing).toEqual([])
  })
})
