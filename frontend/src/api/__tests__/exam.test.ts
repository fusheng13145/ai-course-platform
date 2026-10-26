import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  autoComposeExamPaper,
  getExamQuestions,
  listByCourse,
  normalizeExamQuestions,
  saveExamQuestions,
} from '@/api/exam'

/**
 * P0-2 契约护栏（2026-09-21）
 *
 * 背景：`/exam-papers/{id}/questions` 返回的是「试卷-题目关联」包装结构，
 * 顶层 `id` 是 **exam_questions 关联行 id**，真实题目 id 在 `question.id`。
 * 前端调用方一律按扁平题目对象读取，若缺少归一化则会出现：
 *   ① 答题页题干空白、题型显示「未知题型」、分值回落到 defaultScore；
 *   ② 提交答案时用关联行 id 当题目 id，服务端判卷取不到作答。
 *
 * 该缺陷曾在两个版本中长期存在而无人发现，原因是单测 mock 用了与实际接口
 * 不符的**扁平**结构。因此这里刻意使用**从运行中实例真实抓取**的响应形态，
 * 让契约漂移在单测阶段就暴露。
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
    delete: vi.fn(),
  },
}))

/** 后端真实响应：顶层 id(11) 为关联行 id，真实题目 id 在 question.id(6) */
const BACKEND_PAYLOAD = {
  code: 200,
  message: 'success',
  data: [
    {
      id: 11,
      orderIndex: 0,
      points: 5.0,
      question: {
        id: 6,
        type: 'SINGLE_CHOICE',
        content: '下列哪种数据结构遵循先进先出(FIFO)原则？',
        options: '["栈","队列","二叉树","图"]',
        difficulty: 1,
        score: null,
        usageCount: 0,
        courseId: 1,
      },
    },
    {
      id: 12,
      orderIndex: 1,
      points: 8.0,
      question: {
        id: 7,
        type: 'MULTIPLE_CHOICE',
        content: '以下属于线性数据结构的有哪些？',
        options: '["数组","链表","二叉树","栈"]',
        difficulty: 2,
        score: null,
        usageCount: 0,
        courseId: 1,
      },
    },
    {
      id: 13,
      orderIndex: 2,
      points: 3.0,
      question: {
        id: 8,
        type: 'TRUE_FALSE',
        content: '冒泡排序最坏时间复杂度为 O(n^2)。',
        options: '',
        score: null,
        usageCount: 0,
        courseId: 1,
      },
    },
  ],
}

describe('normalizeExamQuestions - 关联包装结构解包', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('解包 question 后，题目字段提升到顶层可直接读取', () => {
    const list = normalizeExamQuestions(BACKEND_PAYLOAD.data)

    expect(list).toHaveLength(3)
    expect(list[0].type).toBe('SINGLE_CHOICE')
    expect(list[0].content).toBe('下列哪种数据结构遵循先进先出(FIFO)原则？')
    expect(list[0].options).toBe('["栈","队列","二叉树","图"]')
  })

  it('id 必须取真实题目 id，而不是关联行 id（否则判卷取不到作答）', () => {
    const list = normalizeExamQuestions(BACKEND_PAYLOAD.data)

    // 关联行 id 为 11/12/13，真实题目 id 为 6/7/8
    expect(list.map((q) => q.id)).toEqual([6, 7, 8])
    expect(list.map((q) => q.id)).not.toContain(11)
  })

  it('score 取关联行 points（question.score 为 null 时不能回落）', () => {
    const list = normalizeExamQuestions(BACKEND_PAYLOAD.data)

    expect(list[0].score).toBe(5.0)
    expect(list[1].score).toBe(8.0)
    expect(list[2].score).toBe(3.0)
  })

  it('保留 orderIndex 供展示与排序', () => {
    const list = normalizeExamQuestions(BACKEND_PAYLOAD.data)

    expect(list.map((q) => q.orderIndex)).toEqual([0, 1, 2])
  })

  it('解包后不应再残留 question 包装层', () => {
    const list = normalizeExamQuestions(BACKEND_PAYLOAD.data)

    expect(list[0]).not.toHaveProperty('question')
  })
})

describe('normalizeExamQuestions - 兼容与边界', () => {
  it('已是扁平结构的返回项原样透传（兼容非包装接口）', () => {
    const flat = [{ id: 6, type: 'SINGLE_CHOICE', content: '题干', options: '["A"]', score: 5 }]
    const list = normalizeExamQuestions(flat)

    expect(list).toEqual(flat)
  })

  it('payload 为空/null/非数组时返回空数组，不抛异常', () => {
    expect(normalizeExamQuestions(null)).toEqual([])
    expect(normalizeExamQuestions(undefined)).toEqual([])
    expect(normalizeExamQuestions({})).toEqual([])
    expect(normalizeExamQuestions([])).toEqual([])
  })

  it('question 字段非对象时按扁平项透传，不误伤', () => {
    const weird = [{ id: 6, type: 'SINGLE_CHOICE', question: 'not-an-object' }]
    const list = normalizeExamQuestions(weird)

    expect(list[0].type).toBe('SINGLE_CHOICE')
    expect(list[0].id).toBe(6)
  })

  it('question.score 有值而关联行无 points 时，回落 question.score', () => {
    const list = normalizeExamQuestions([{ id: 11, question: { id: 6, score: 7 } }])

    expect(list[0].score).toBe(7)
  })
})

describe('getExamQuestions - API 层接入', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('请求路径正确，且返回的 data 已归一化为扁平题目', async () => {
    mocks.get.mockResolvedValue(BACKEND_PAYLOAD)

    const res = await getExamQuestions(4)

    expect(mocks.get).toHaveBeenCalledWith('/exam-papers/4/questions')
    expect(res.data[0].id).toBe(6)
    expect(res.data[0].type).toBe('SINGLE_CHOICE')
    expect(res.data[0].score).toBe(5.0)
    // 归一化不应破坏响应外层封装（调用方仍按 ApiResponse 读取 code/message）。
    // request 模块的类型签名是 AxiosResponse，这里按运行时实际封装结构断言。
    const envelope = res as unknown as { code: number; message: string }
    expect(envelope.code).toBe(200)
    expect(envelope.message).toBe('success')
  })

  it('响应缺少 data 时返回空数组，不抛异常', async () => {
    mocks.get.mockResolvedValue({ code: 200, message: 'success' })

    const res = await getExamQuestions(4)

    expect(res.data).toEqual([])
  })
})

/**
 * listByCourse 契约（2026-10-04）
 *
 * CourseExamPanel 一直调用 `examApi.listByCourse(courseId)`，但 api/exam.ts 里从来没有这个导出
 * —— 教师课程详情页的考试面板因此每次加载都抛 TypeError（列表恒空 + 控制台报错）。
 * 后端真实端点为 `GET /exam-papers/course/{courseId}`（分页参数 page/size）。
 */
describe('exam 模块：listByCourse', () => {
  // 本文件前面的 describe 会真实调用 getExamQuestions(4)，若不清零，
  // mock.calls 里残留 `/exam-papers/4/questions`，使 toHaveBeenNthCalledWith(1, …) 错位。
  beforeEach(() => {
    mocks.get.mockReset()
  })

  it('拼出 /exam-papers/course/{courseId}，分页参数走 params', async () => {
    mocks.get.mockResolvedValue({ data: { records: [], total: 0 } })

    await listByCourse(7, { page: 2, size: 20 })

    expect(mocks.get).toHaveBeenCalledWith('/exam-papers/course/7', {
      params: { page: 2, size: 20 },
    })
  })

  it('省略分页参数时发送空 params 对象（而非 undefined，避免 axios 序列化差异）', async () => {
    mocks.get.mockResolvedValue({ data: { records: [] } })

    await listByCourse(3)

    expect(mocks.get).toHaveBeenCalledWith('/exam-papers/course/3', { params: {} })
  })

  it('已在默认导出对象中登记（组件按 examApi.listByCourse 取用）', async () => {
    const examApi = (await import('@/api/exam')).default
    expect(typeof examApi.listByCourse).toBe('function')
  })

  it('getAllExamPapersByCourse follows every page rather than capping at the first 100', async () => {
    mocks.get
      .mockResolvedValueOnce({ code: 200, data: { records: [{ id: 1 }], total: 101 } })
      .mockResolvedValueOnce({ code: 200, data: { records: [{ id: 2 }], total: 101 } })

    const examApi = (await import('@/api/exam')).default
    await expect(examApi.getAllExamPapersByCourse(7)).resolves.toEqual([{ id: 1 }, { id: 2 }])
    expect(mocks.get).toHaveBeenNthCalledWith(1, '/exam-papers/course/7', {
      params: { page: 1, size: 100 },
    })
    expect(mocks.get).toHaveBeenNthCalledWith(2, '/exam-papers/course/7', {
      params: { page: 2, size: 100 },
    })
  })
})

/**
 * autoComposeExamPaper / saveExamQuestions 契约（2026-10-26 批阅闭环批）
 *
 * - autoCompose：后端 POST /exam-papers/auto-compose 直接建卷并写入题目，返回 DRAFT 试卷；
 *   typeRules 的 key 必须是题库 Question.type 实际值（F3），封装只做透传不加工。
 * - saveExamQuestions：批量替换语义，body 形状与后端 ExamQuestion 实体对齐
 *   （{ questions: [{ questionId, points, orderIndex }] }）。
 */
describe('exam 模块：autoComposeExamPaper / saveExamQuestions', () => {
  beforeEach(() => {
    mocks.post.mockReset()
  })

  it('autoComposeExamPaper POST /exam-papers/auto-compose，payload 原样透传', async () => {
    mocks.post.mockResolvedValue({ code: 200, message: '自动组卷成功', data: { id: 9 } })

    const dto = {
      courseId: 7,
      title: '期中测验',
      totalScore: 100,
      duration: 60,
      typeRules: { SINGLE: 5, ESSAY: 2 },
      difficultyRatio: { EASY: 30, MEDIUM: 50, HARD: 20 },
    }
    await autoComposeExamPaper(dto)

    expect(mocks.post).toHaveBeenCalledTimes(1)
    expect(mocks.post).toHaveBeenCalledWith('/exam-papers/auto-compose', dto)
  })

  it('saveExamQuestions POST /exam-papers/{id}/questions，题目清单包在 questions 字段内', async () => {
    mocks.post.mockResolvedValue({ code: 200, message: '保存成功', data: [] })

    const items = [
      { questionId: 6, points: 5, orderIndex: 0 },
      { questionId: 7, points: 8, orderIndex: 1 },
    ]
    await saveExamQuestions(9, items)

    expect(mocks.post).toHaveBeenCalledWith('/exam-papers/9/questions', { questions: items })
  })

  it('两个封装均已在默认导出对象中登记', async () => {
    const examApi = (await import('@/api/exam')).default
    expect(typeof examApi.autoComposeExamPaper).toBe('function')
    expect(typeof examApi.saveExamQuestions).toBe('function')
  })
})
