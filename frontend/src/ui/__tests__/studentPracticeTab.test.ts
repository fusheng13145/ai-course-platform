import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const courseApi = vi.hoisted(() => ({ getCourseById: vi.fn() }))
const questionApi = vi.hoisted(() => ({
  getPracticeQuestions: vi.fn(),
  checkPracticeAnswer: vi.fn(),
}))

vi.mock('@/api/course', () => courseApi)
vi.mock('@/api/question', () => questionApi)

import StudentPracticeTab from '../views/course/tabs/StudentPracticeTab.vue'

const COURSE_ID = 4007

function mountTab() {
  return mount(StudentPracticeTab, { props: { courseId: COURSE_ID } })
}

beforeEach(() => {
  vi.clearAllMocks()
  courseApi.getCourseById.mockResolvedValue({ code: 200, data: { questionPracticeEnabled: true } })
  questionApi.getPracticeQuestions.mockResolvedValue({
    code: 200,
    data: { list: [], total: 0 },
  })
})

describe('StudentPracticeTab', () => {
  it('教师未开放时显示空态且不请求题库', async () => {
    courseApi.getCourseById.mockResolvedValue({
      code: 200,
      data: { questionPracticeEnabled: false },
    })
    const wrapper = mountTab()
    await flushPromises()

    expect(wrapper.text()).toContain('教师尚未开放本课程题库刷题')
    expect(questionApi.getPracticeQuestions).not.toHaveBeenCalled()
  })

  it('开放后加载题目、不含答案，提交后才显示判分与解析', async () => {
    questionApi.getPracticeQuestions.mockResolvedValue({
      code: 200,
      data: {
        list: [
          {
            id: 1,
            courseId: COURSE_ID,
            type: 'SINGLE_CHOICE',
            content: 'TCP 属于哪一层？',
            options: ['应用层', '传输层'],
            knowledgePoints: ['TCP/IP'],
          },
        ],
        total: 1,
      },
    })
    questionApi.checkPracticeAnswer.mockResolvedValue({
      code: 200,
      data: { questionId: 1, correct: true, correctAnswer: 'B', explanation: 'TCP 是传输层协议。' },
    })

    const wrapper = mountTab()
    await flushPromises()

    expect(wrapper.text()).toContain('TCP 属于哪一层？')
    expect(wrapper.text()).not.toContain('正确答案')

    const options = wrapper.findAll('.practice-option')
    await options[1].trigger('click')
    const submit = wrapper.findAll('button').find((node) => node.text().includes('提交答案'))
    expect(submit).toBeTruthy()
    await submit!.trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('回答正确')
    expect(wrapper.text()).toContain('正确答案：B')
    expect(wrapper.text()).toContain('TCP 是传输层协议。')
  })
})
