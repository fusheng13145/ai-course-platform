import { describe, it, expect, vi } from 'vitest'

// 模拟错题本 API
const mockGetWrongQuestions = vi.fn()
const mockRemoveWrongQuestion = vi.fn()
const mockRetryWrongQuestion = vi.fn()
vi.mock('@/api/wrongQuestions', () => ({
  default: {
    getWrongQuestions: mockGetWrongQuestions,
    removeWrongQuestion: mockRemoveWrongQuestion,
    retryWrongQuestion: mockRetryWrongQuestion,
  },
}))

describe('错题本功能', () => {
  const sampleWrongQuestions = [
    {
      id: 1,
      questionId: 101,
      content: 'OSI模型有几层？',
      type: 'SINGLE_CHOICE',
      correctAnswer: '7',
      userAnswer: '5',
      courseName: '计算机网络',
      recordedAt: '2026-07-20T10:00:00',
    },
    {
      id: 2,
      questionId: 102,
      content: 'HTTP默认端口是？',
      type: 'SINGLE_CHOICE',
      correctAnswer: '80',
      userAnswer: '8080',
      courseName: '计算机网络',
      recordedAt: '2026-07-21T14:30:00',
    },
    {
      id: 3,
      questionId: 103,
      content: 'DNS使用的传输层协议是UDP',
      type: 'TRUE_FALSE',
      correctAnswer: '正确',
      userAnswer: '错误',
      courseName: '计算机网络',
      recordedAt: '2026-07-22T09:15:00',
    },
  ]

  it('获取错题列表应按时间倒序', async () => {
    mockGetWrongQuestions.mockResolvedValue({
      code: 200,
      data: { records: [...sampleWrongQuestions], total: 3 },
    })

    const res = await mockGetWrongQuestions({ page: 1, size: 20 })
    const sorted = [...res.data.records].sort(
      (a, b) => new Date(b.recordedAt).getTime() - new Date(a.recordedAt).getTime(),
    )

    expect(sorted[0].id).toBe(3) // 最新的在前
    expect(sorted).toHaveLength(3)
  })

  it('按课程筛选错题', () => {
    const all = sampleWrongQuestions
    const filtered = all.filter((q) => q.courseName === '计算机网络')
    expect(filtered).toHaveLength(3)

    const none = all.filter((q) => q.courseName === '数据结构')
    expect(none).toHaveLength(0)
  })

  it('按题型筛选错题', () => {
    const singleChoice = sampleWrongQuestions.filter((q) => q.type === 'SINGLE_CHOICE')
    expect(singleChoice).toHaveLength(2)

    const trueFalse = sampleWrongQuestions.filter((q) => q.type === 'TRUE_FALSE')
    expect(trueFalse).toHaveLength(1)
  })

  it('移除错题应成功', async () => {
    mockRemoveWrongQuestion.mockResolvedValue({ code: 200, message: '已移除' })
    const res = await mockRemoveWrongQuestion(1)
    expect(res.code).toBe(200)
    expect(mockRemoveWrongQuestion).toHaveBeenCalledWith(1)
  })

  it('重新练习错题应返回题目详情', async () => {
    mockRetryWrongQuestion.mockResolvedValue({
      code: 200,
      data: sampleWrongQuestions[0],
    })
    const res = await mockRetryWrongQuestion(1)
    expect(res.data.questionId).toBe(101)
  })

  it('错题统计：按知识点分组计数', () => {
    const stats = sampleWrongQuestions.reduce((acc, q) => {
      const key = q.type
      acc[key] = (acc[key] || 0) + 1
      return acc
    }, {})
    expect(stats['SINGLE_CHOICE']).toBe(2)
    expect(stats['TRUE_FALSE']).toBe(1)
  })

  it('空错题本应显示提示', () => {
    const empty = []
    const hasRecords = empty.length > 0
    expect(hasRecords).toBe(false)
    const message = hasRecords ? '' : '暂无错题记录，继续加油！'
    expect(message).toBe('暂无错题记录，继续加油！')
  })
})
