import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const api = vi.hoisted(() => ({
  getExamPaperById: vi.fn(),
  getExamQuestions: vi.fn(),
  getPaperSessions: vi.fn(),
  getExamResult: vi.fn(),
  autoGrade: vi.fn(),
  manualGrade: vi.fn(),
}))
vi.mock('@/api/exam', () => ({ default: api }))

const toast = vi.hoisted(() => vi.fn())
vi.mock('../composables/ui', () => ({ useUi: () => ({ toast }) }))

const push = vi.hoisted(() => vi.fn())
vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { paperId: '7007004' } }),
  useRouter: () => ({ push }),
}))

import ExamReviewView from '../views/teacher/ExamReviewView.vue'

const stubs = {
  Toasts: { template: '<div />' },
}

function mountReview() {
  return mount(ExamReviewView, { global: { stubs } })
}

beforeEach(() => {
  vi.clearAllMocks()
  api.getExamPaperById.mockResolvedValue({ data: { id: 7007004, title: 'Python 期中测验' } })
  api.getExamQuestions.mockResolvedValue({
    data: [
      {
        id: 7006068,
        score: 10,
        orderIndex: 1,
        type: 'ESSAY',
        content: '请论述进程与线程的区别',
        correctAnswers: '地址空间、调度、通信三点',
      },
      {
        id: 7006057,
        score: 10,
        orderIndex: 2,
        type: 'SINGLE_CHOICE',
        content: 'TCP 属于哪层',
        correctAnswers: 'B',
      },
    ],
  })
  api.getPaperSessions.mockResolvedValue({
    data: [
      {
        sessionId: 7008371,
        studentId: 4,
        username: 'student',
        fullName: '王志远',
        studentNo: '2024001',
        attemptNumber: 2,
        status: 'GRADED',
        totalScore: 50,
        percentage: 50,
        submitTime: '2026-11-22T12:55:45',
      },
    ],
  })
  api.getExamResult.mockResolvedValue({
    data: {
      id: 7008371,
      status: 'GRADED',
      totalScore: 50,
      answers: '{"7006068":"进程有独立地址空间，线程共享","7006057":"A"}',
      questionScores: '{"7006068":0,"7006057":10}',
      questionComments: '{}',
    },
  })
  api.manualGrade.mockResolvedValue({ data: null })
  api.autoGrade.mockResolvedValue({ data: {} })
})

describe('教师考试阅卷台', () => {
  it('自动选中首份答卷，展示学生信息、题干与作答', async () => {
    const wrapper = mountReview()
    await flushPromises()

    expect(api.getPaperSessions).toHaveBeenCalledWith(7007004)
    const text = wrapper.text()
    expect(text).toContain('王志远')
    expect(text).toContain('2024001')
    expect(text).toContain('请论述进程与线程的区别')
    expect(text).toContain('进程有独立地址空间，线程共享')
    expect(text).toContain('地址空间、调度、通信三点')
    // 论述题必须标记为需人工评分
    expect(text).toContain('需人工评分')
    wrapper.unmount()
  })

  it('给论述题打分后按题提交人工评分', async () => {
    const wrapper = mountReview()
    await flushPromises()

    const essayCard = wrapper.findAll('.question-card')[0]
    const scoreInput = essayCard.find('.score-field input')
    await scoreInput.setValue('8')
    await essayCard.find('.comment-field input').setValue('要点基本完整')

    const saveBtn = essayCard.findAll('button').find((b) => b.text().includes('保存本题'))
    expect(saveBtn).toBeTruthy()
    await saveBtn?.trigger('click')
    await flushPromises()

    expect(api.manualGrade).toHaveBeenCalledWith(7008371, {
      questionId: 7006068,
      score: 8,
      comment: '要点基本完整',
    })
    expect(toast).toHaveBeenCalledWith('本题评分已保存', 'ok')
    wrapper.unmount()
  })

  it('得分超过本题满分时拒绝提交', async () => {
    const wrapper = mountReview()
    await flushPromises()

    const essayCard = wrapper.findAll('.question-card')[0]
    await essayCard.find('.score-field input').setValue('99')
    await essayCard.findAll('button').find((b) => b.text().includes('保存本题'))?.trigger('click')
    await flushPromises()

    expect(api.manualGrade).not.toHaveBeenCalled()
    expect(toast).toHaveBeenCalledWith('得分不能超过本题满分 10', 'warn')
    wrapper.unmount()
  })

  it('会话未自动阅卷时先触发自动判分再加载', async () => {
    api.getExamResult
      .mockResolvedValueOnce({ data: { id: 7008371, status: 'SUBMITTED', totalScore: null } })
      .mockResolvedValueOnce({
        data: { id: 7008371, status: 'GRADED', totalScore: 10, answers: '{}', questionScores: '{}' },
      })

    const wrapper = mountReview()
    await flushPromises()

    expect(api.autoGrade).toHaveBeenCalledWith(7008371)
    wrapper.unmount()
  })
})
