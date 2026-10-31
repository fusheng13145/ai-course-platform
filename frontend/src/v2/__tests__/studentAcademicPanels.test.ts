import { afterEach, describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const learningApi = vi.hoisted(() => ({
  getLearningStats: vi.fn(),
  getHeatmap: vi.fn(),
  getMyLearningRecords: vi.fn(),
  createLearningRecord: vi.fn(),
}))
const studentApi = vi.hoisted(() => ({ getAllMyCourses: vi.fn() }))
const wrongApi = vi.hoisted(() => ({
  getMyWrongQuestions: vi.fn(),
  markMyWrongQuestionMastered: vi.fn(),
  removeMyWrongQuestion: vi.fn(),
  getMyWrongQuestionStats: vi.fn(),
  generateMyWrongPractice: vi.fn(),
  getMyWrongGroups: vi.fn(),
}))
const toast = vi.hoisted(() => vi.fn())
const confirm = vi.hoisted(() => vi.fn())

vi.mock('@/api/learningRecord', () => learningApi)
vi.mock('@/api/student', () => studentApi)
vi.mock('@/api/wrongQuestion', () => wrongApi)
vi.mock('../composables/ui', () => ({ useV2Ui: () => ({ toast }) }))
vi.mock('../composables/v2Confirm', () => ({ confirmWithV2: confirm }))
vi.mock('@/utils/logger', () => ({ default: { error: vi.fn(), warn: vi.fn(), info: vi.fn() } }))

import LearningCenterPanel from '../views/specials/academic/LearningCenterPanel.vue'
import WrongBookPanel from '../views/specials/academic/WrongBookPanel.vue'

const modalText = () =>
  (document.body.querySelector('.v2-modal-panel') as HTMLElement | null)?.textContent || ''
const modalButton = (label: string) =>
  Array.from(document.body.querySelectorAll('.v2-modal-panel button')).find((node) =>
    (node.textContent || '').includes(label),
  ) as HTMLButtonElement | undefined

const COURSES = [{ id: 1, name: 'Python程序设计' }]
const day = (date: string, duration: number, count = 1) => ({ date, duration, count })

afterEach(() => {
  document.body.innerHTML = ''
})

beforeEach(() => {
  vi.clearAllMocks()
  studentApi.getAllMyCourses.mockResolvedValue(COURSES)
  learningApi.getLearningStats.mockResolvedValue({
    data: {
      totalDuration: 120,
      learningDays: 3,
      completedCourses: 1,
      currentCourses: 2,
      todayDuration: 20,
    },
  })
  learningApi.getHeatmap.mockResolvedValue({
    data: [day('2026-10-01', 30), day('2026-10-02', 0, 0)],
  })
  learningApi.getMyLearningRecords.mockResolvedValue({
    data: [
      {
        id: 2,
        courseId: 1,
        chapterId: 11,
        actionType: 'COMPLETE',
        duration: 15,
        createdAt: '2026-10-02 10:00:00',
      },
      {
        id: 1,
        courseId: 1,
        chapterId: 10,
        actionType: 'VIEW',
        duration: 5,
        createdAt: '2026-10-01 09:00:00',
      },
    ],
  })
  wrongApi.getMyWrongQuestions.mockResolvedValue({
    data: {
      list: [
        {
          id: 9001,
          questionContent: 'TCP 工作在哪一层？',
          wrongAnswer: 'A',
          correctAnswer: '"B"',
          questionOptions: '["网络层","传输层"]',
          mastered: false,
          sourceType: 'EXAM',
          practiceCount: 2,
          createdAt: '2026-10-29 16:51:00',
        },
      ],
      total: 1,
      totalPages: 1,
    },
  })
  wrongApi.markMyWrongQuestionMastered.mockResolvedValue({ data: null })
  wrongApi.removeMyWrongQuestion.mockResolvedValue({ data: null })
  wrongApi.getMyWrongGroups.mockResolvedValue({
    data: [
      {
        knowledgePointId: 701,
        knowledgePointName: 'TCP 与传输层',
        count: 2,
        questions: [
          {
            id: 9001,
            questionContent: 'TCP 工作在哪一层？',
            wrongAnswer: 'A',
            correctAnswer: '"B"',
            mastered: false,
          },
          {
            id: 9002,
            questionContent: '三次握手的第三次做了什么？',
            wrongAnswer: 'B',
            correctAnswer: '"C"',
            mastered: true,
          },
        ],
      },
      {
        knowledgePointId: null,
        knowledgePointName: null,
        count: 1,
        questions: [{ id: 9003, questionContent: '未归类题', mastered: false }],
      },
    ],
  })
  wrongApi.getMyWrongQuestionStats.mockResolvedValue({
    data: { total: 4, mastered: 1, unmastered: 3, masteryRate: 0.75 },
  })
  wrongApi.generateMyWrongPractice.mockResolvedValue({
    data: [
      {
        id: 501,
        content: 'TCP 建立连接几次握手？',
        options: '["一次","两次","三次","四次"]',
        correctAnswers: '"C"',
        explanation: '三次握手',
        type: 'SINGLE',
        difficulty: 'MEDIUM',
      },
    ],
  })
  confirm.mockResolvedValue(true)
})

describe('学习中心页签：只用 /learning-records 真接口', () => {
  it('挂载即拉统计、日历（带年月）与记录，并把真实读数渲染成 KPI', async () => {
    const wrapper = mount(LearningCenterPanel)
    await flushPromises()
    expect(learningApi.getLearningStats).toHaveBeenCalledTimes(1)
    expect(learningApi.getHeatmap).toHaveBeenCalledWith({
      year: new Date().getFullYear(),
      month: new Date().getMonth() + 1,
    })
    expect(learningApi.getMyLearningRecords).toHaveBeenCalledTimes(1)
    const text = wrapper.text()
    expect(text).toContain('累计时长')
    expect(text).toContain('120 分钟')
    expect(text).toContain('学习天数')
    expect(text).toContain('3 天')
    expect(text).toContain('章节完成')
    expect(text).toContain('Python程序设计')
  })

  it('翻月会按新年月重新拉日历', async () => {
    const wrapper = mount(LearningCenterPanel)
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((node) => node.text().includes('上月'))!
      .trigger('click')
    await flushPromises()
    expect(learningApi.getHeatmap).toHaveBeenCalledTimes(2)
    const last = learningApi.getHeatmap.mock.calls[1][0]
    expect(last).toHaveProperty('year')
    expect(last).toHaveProperty('month')
  })

  it('统计失败时给出可区分的失败态与重试，不渲染假数值', async () => {
    learningApi.getLearningStats.mockRejectedValueOnce(new Error('boom'))
    const wrapper = mount(LearningCenterPanel)
    await flushPromises()
    expect(wrapper.text()).toContain('学习统计加载失败')
    expect(wrapper.text()).not.toContain('累计时长')
  })

  it('无记录时给空态文案，而不是空白表', async () => {
    learningApi.getMyLearningRecords.mockResolvedValue({ data: [] })
    const wrapper = mount(LearningCenterPanel)
    await flushPromises()
    expect(wrapper.text()).toContain('还没有学习记录')
  })
})

describe('错题本页签：/student/wrong-questions 真接口', () => {
  it('挂载即按分页拉取，并渲染题面、来源与待掌握状态', async () => {
    const wrapper = mount(WrongBookPanel)
    await flushPromises()
    expect(wrongApi.getMyWrongQuestions).toHaveBeenCalledWith({ page: 1, size: 10 })
    const text = wrapper.text()
    expect(text).toContain('TCP 工作在哪一层？')
    expect(text).toContain('考试')
    expect(text).toContain('待掌握')
    expect(text).toContain('共 1 条')
  })

  it('展开行显示你的答案、正确答案与选项（字母序号由选项顺序生成）', async () => {
    const wrapper = mount(WrongBookPanel)
    await flushPromises()
    await wrapper.find('.wb-stem').trigger('click')
    const text = wrapper.text()
    expect(text).toContain('你的答案')
    expect(text).toContain('B')
    expect(text).toContain('A. 网络层')
  })

  it('标记掌握走 PUT 端点并即时更新状态', async () => {
    const wrapper = mount(WrongBookPanel)
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((node) => node.text().includes('标记掌握'))!
      .trigger('click')
    await flushPromises()
    expect(wrongApi.markMyWrongQuestionMastered).toHaveBeenCalledWith(9001)
    expect(wrapper.text()).toContain('已掌握')
    expect(toast).toHaveBeenCalledWith('已标记为掌握', 'ok')
  })

  it('移除需二次确认；取消则不发请求，确认才删并刷新列表', async () => {
    const wrapper = mount(WrongBookPanel)
    await flushPromises()
    confirm.mockResolvedValueOnce(false)
    await wrapper
      .findAll('button')
      .find((node) => node.text().includes('移除'))!
      .trigger('click')
    await flushPromises()
    expect(wrongApi.removeMyWrongQuestion).not.toHaveBeenCalled()

    await wrapper
      .findAll('button')
      .find((node) => node.text().includes('移除'))!
      .trigger('click')
    await flushPromises()
    expect(confirm).toHaveBeenCalledTimes(2)
    expect(wrongApi.removeMyWrongQuestion).toHaveBeenCalledWith(9001)
    expect(wrongApi.getMyWrongQuestions).toHaveBeenCalledTimes(2)
  })

  it('按课程筛选时把 courseId 带进请求，并重置到第一页', async () => {
    const wrapper = mount(WrongBookPanel)
    await flushPromises()
    await wrapper.find('.wb-filter').setValue('1')
    await flushPromises()
    expect(wrongApi.getMyWrongQuestions).toHaveBeenLastCalledWith({
      page: 1,
      size: 10,
      courseId: 1,
    })
  })

  it('空列表给明确空态', async () => {
    wrongApi.getMyWrongQuestions.mockResolvedValue({ data: { list: [], total: 0, totalPages: 1 } })
    const wrapper = mount(WrongBookPanel)
    await flushPromises()
    expect(wrapper.text()).toContain('还没有错题')
  })
})

describe('错题统计卡与生成练习（批次 6）', () => {
  it('挂载即拉统计并渲染四项读数（掌握率按百分比显示）', async () => {
    const wrapper = mount(WrongBookPanel)
    await flushPromises()
    expect(wrongApi.getMyWrongQuestionStats).toHaveBeenCalledWith({})
    const text = wrapper.text()
    expect(text).toContain('错题总数')
    expect(text).toContain('待掌握')
    expect(text).toContain('75.0%')
  })

  it('统计失败不影响列表：不渲染统计条，列表照常', async () => {
    wrongApi.getMyWrongQuestionStats.mockRejectedValueOnce(new Error('boom'))
    const wrapper = mount(WrongBookPanel)
    await flushPromises()
    expect(wrapper.find('.wb-stats').exists()).toBe(false)
    expect(wrapper.text()).toContain('TCP 工作在哪一层？')
  })

  it('点「生成练习」打开弹层，生成时按无筛选发 {count}，并可显示答案', async () => {
    const wrapper = mount(WrongBookPanel)
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((n) => n.text().includes('生成练习'))!
      .trigger('click')
    await flushPromises()
    expect(modalText()).toContain('错题练习')
    modalButton('开始生成')!.click()
    await flushPromises()
    expect(wrongApi.generateMyWrongPractice).toHaveBeenCalledWith({ count: 10 })
    expect(modalText()).toContain('TCP 建立连接几次握手？')
    modalButton('显示答案')!.click()
    await flushPromises()
    expect(modalText()).toContain('参考答案')
    expect(modalText()).toContain('三次握手')
  })

  it('有课程筛选时练习请求带 courseId', async () => {
    const wrapper = mount(WrongBookPanel)
    await flushPromises()
    await wrapper.find('.wb-filter').setValue('1')
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((n) => n.text().includes('生成练习'))!
      .trigger('click')
    await flushPromises()
    modalButton('开始生成')!.click()
    await flushPromises()
    expect(wrongApi.generateMyWrongPractice).toHaveBeenCalledWith({ count: 10, courseId: 1 })
  })

  it('生成结果为空时给明确提示，不展示空列表', async () => {
    wrongApi.generateMyWrongPractice.mockResolvedValue({ data: [] })
    const wrapper = mount(WrongBookPanel)
    await flushPromises()
    await wrapper
      .findAll('button')
      .find((n) => n.text().includes('生成练习'))!
      .trigger('click')
    await flushPromises()
    modalButton('开始生成')!.click()
    await flushPromises()
    expect(modalText()).toContain('当前没有可练习的错题')
  })

  it('标记掌握后刷新统计（统计请求次数 +1）', async () => {
    const wrapper = mount(WrongBookPanel)
    await flushPromises()
    expect(wrongApi.getMyWrongQuestionStats).toHaveBeenCalledTimes(1)
    await wrapper
      .findAll('button')
      .find((n) => n.text().includes('标记掌握'))!
      .trigger('click')
    await flushPromises()
    expect(wrongApi.getMyWrongQuestionStats).toHaveBeenCalledTimes(2)
  })
})

describe('错题按知识点分组（批次 7）', () => {
  const openGrouped = async (wrapper: ReturnType<typeof mount>) => {
    await wrapper
      .findAll('.tabs button')
      .find((n) => n.text() === '按知识点')!
      .trigger('click')
    await flushPromises()
  }

  it('切到「按知识点」才请求分组端点，并渲染知识点名、数量与题目', async () => {
    const wrapper = mount(WrongBookPanel)
    await flushPromises()
    expect(wrongApi.getMyWrongGroups).not.toHaveBeenCalled()
    await openGrouped(wrapper)
    expect(wrongApi.getMyWrongGroups).toHaveBeenCalledWith({})
    const text = wrapper.text()
    expect(text).toContain('TCP 与传输层')
    expect(text).toContain('2 道')
    expect(text).toContain('TCP 工作在哪一层？')
    expect(text).toContain('未归类知识点')
    expect(text).toContain('共 2 个知识点、3 道错题')
  })

  it('分组视图下展开题目可看答案与解析；课程筛选会把 courseId 带进请求', async () => {
    const wrapper = mount(WrongBookPanel)
    await flushPromises()
    await wrapper.find('.wb-filter').setValue('1')
    await flushPromises()
    await openGrouped(wrapper)
    expect(wrongApi.getMyWrongGroups).toHaveBeenLastCalledWith({ courseId: 1 })
    const stems = wrapper.findAll('.wb-group-list .wb-stem')
    await stems[0].trigger('click')
    expect(wrapper.text()).toContain('参考答案')
  })

  it('分组为空与请求失败两态可区分', async () => {
    wrongApi.getMyWrongGroups.mockResolvedValueOnce({ data: [] })
    const empty = mount(WrongBookPanel)
    await flushPromises()
    await openGrouped(empty)
    expect(empty.text()).toContain('还没有可归类的错题')

    wrongApi.getMyWrongGroups.mockRejectedValueOnce(new Error('boom'))
    const failed = mount(WrongBookPanel)
    await flushPromises()
    await openGrouped(failed)
    expect(failed.text()).toContain('按知识点汇总失败')
  })

  it('只剩「未知知识点」单桶时给出数据面提示（题目未打标签）', async () => {
    wrongApi.getMyWrongGroups.mockResolvedValueOnce({
      data: [
        {
          knowledgePointId: null,
          knowledgePointName: '未知知识点',
          count: 3,
          questions: [{ id: 1, questionContent: 'Q1', mastered: false }],
        },
      ],
    })
    const wrapper = mount(WrongBookPanel)
    await flushPromises()
    await openGrouped(wrapper)
    expect(wrapper.text()).toContain('全部归入「未知知识点」')
  })

  it('切回列表不重复请求分组端点', async () => {
    const wrapper = mount(WrongBookPanel)
    await flushPromises()
    await openGrouped(wrapper)
    expect(wrongApi.getMyWrongGroups).toHaveBeenCalledTimes(1)
    await wrapper
      .findAll('.tabs button')
      .find((n) => n.text() === '列表')!
      .trigger('click')
    await flushPromises()
    expect(wrongApi.getMyWrongGroups).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('共 1 条')
  })
})
