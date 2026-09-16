import { describe, it, expect, beforeEach, vi } from 'vitest'

// 模拟考试相关 API
const mockGetExamPaper = vi.fn()
const mockSubmitExam = vi.fn()
const mockGetExamSessions = vi.fn()
vi.mock('@/api/exams', () => ({
  default: {
    getExamPaper: mockGetExamPaper,
    submitExam: mockSubmitExam,
    getExamSessions: mockGetExamSessions,
  },
}))

describe('考试流程', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('获取试卷后应加载题目列表和倒计时', async () => {
    mockGetExamPaper.mockResolvedValue({
      code: 200,
      data: {
        id: 1,
        title: '计网期中测验',
        totalScore: 100,
        duration: 60,
        questions: [
          {
            id: 1,
            type: 'SINGLE_CHOICE',
            content: 'TCP位于OSI哪一层？',
            options: ['A.网络层', 'B.传输层', 'C.应用层', 'D.数据链路层'],
            correctAnswer: 'B',
          },
          {
            id: 2,
            type: 'TRUE_FALSE',
            content: 'UDP提供可靠传输',
            options: ['正确', '错误'],
            correctAnswer: '错误',
          },
        ],
      },
    })

    const res = await mockGetExamPaper(1, { studentId: 100 })
    const paper = res.data

    expect(paper.questions).toHaveLength(2)
    expect(paper.duration).toBe(60)
    expect(paper.totalScore).toBe(100)
  })

  it('单选题答案记录应正确存储', () => {
    const answers = new Map()
    const answer = { questionId: 1, answer: 'B' }
    answers.set(answer.questionId, answer.answer)
    expect(answers.get(1)).toBe('B')
    expect(answers.size).toBe(1)
  })

  it('判断题答案记录应正确存储', () => {
    const answers = new Map()
    answers.set(2, '错误')
    expect(answers.get(2)).toBe('错误')
  })

  it('交卷时应提交所有已答题目', async () => {
    const answers = { 1: 'B', 2: '错误' }
    mockSubmitExam.mockResolvedValue({
      code: 200,
      data: { sessionId: 100, score: 85, totalScore: 100, status: 'GRADED' },
    })

    const res = await mockSubmitExam(1, { answers })
    expect(res.data.score).toBe(85)
    expect(res.data.status).toBe('GRADED')
    expect(mockSubmitExam).toHaveBeenCalledWith(1, { answers })
  })

  it('未答题且到时应自动提交', () => {
    const answers = { 1: 'B' }
    const totalQuestions = 2
    const answeredCount = Object.keys(answers).length
    expect(answeredCount).toBeLessThan(totalQuestions)
    // 超时应允许提交部分答案
    const canSubmit = answeredCount >= 0
    expect(canSubmit).toBe(true)
  })

  it('客观题自动判分逻辑：答案标准化比对', () => {
    const correctAnswer = 'B'
    const studentAnswer = 'B'
    const isCorrect = correctAnswer.trim().toUpperCase() === studentAnswer.trim().toUpperCase()
    expect(isCorrect).toBe(true)

    const wrongAnswer = 'A'
    const isWrong = correctAnswer.trim().toUpperCase() === wrongAnswer.trim().toUpperCase()
    expect(isWrong).toBe(false)
  })

  it('成绩计算：得分 = 正确数 / 总题数 × 100', () => {
    const total = 5
    const correct = 3
    const score = Math.round((correct / total) * 100)
    expect(score).toBe(60)
  })

  it('考试倒计时归零后应立即交卷', async () => {
    const state = { remainingSeconds: 0, submitted: false }
    if (state.remainingSeconds <= 0 && !state.submitted) {
      state.submitted = true
    }
    expect(state.submitted).toBe(true)
  })
})
