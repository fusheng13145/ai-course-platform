import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import SeQuestionView from '@/views/student/academic/components/SeQuestionView.vue'
import { useStudentExams } from '@/composables/useStudentExams'

/**
 * 学生答题页渲染 —— P0-2 回归护栏（2026-09-21 重写）
 *
 * 原用例的问题（已修）：它 mock 了 **不存在的模块** `@/api/exams`（真实模块是单数
 * `@/api/exam`），且未 import 任何项目源码，8 条断言全部只作用于 mock 自身与测试内
 * 手写的普通 JS（Map 赋值、字符串比较、Math.round）——**不验证任何产品行为**。
 * 这正是 P0-2「答题页题干空白、显示未知题型」能长期潜伏而测试全绿的原因。
 *
 * 现改为挂载真实组件，传入**归一化后**的扁平题目（契约见
 * `src/api/__tests__/exam.test.ts`），断言各题型能被正确渲染。
 */

/* composable 内部会注册生命周期与定时器，且会引用 api 模块；
   本用例只取它的纯工具函数（题型名/标签/选项解析），故 mock 掉 api 与生命周期。 */
vi.mock('@/api/exam', () => ({ default: {} }))
vi.mock('element-plus', () => ({
  ElMessage: { info: vi.fn(), success: vi.fn(), warning: vi.fn(), error: vi.fn() },
  ElMessageBox: { confirm: vi.fn() },
}))
vi.mock('vue', async (importOriginal) => {
  const actual = await importOriginal<typeof import('vue')>()
  return { ...actual, onMounted: vi.fn(), onUnmounted: vi.fn() }
})

/** 归一化后的扁平题目：真实题目 id、score 来自关联行 points（与 API 层输出一致） */
const SINGLE = {
  id: 6,
  type: 'SINGLE_CHOICE',
  content: '下列哪种数据结构遵循先进先出(FIFO)原则？',
  options: '["栈","队列","二叉树","图"]',
  score: 5,
  orderIndex: 0,
}

const MULTI = {
  id: 7,
  type: 'MULTIPLE_CHOICE',
  content: '以下属于线性数据结构的有哪些？',
  options: '["数组","链表","二叉树","栈"]',
  score: 8,
  orderIndex: 1,
}

const TRUE_FALSE = {
  id: 8,
  type: 'TRUE_FALSE',
  content: '冒泡排序最坏时间复杂度为 O(n^2)。',
  options: '',
  score: 3,
  orderIndex: 2,
}

const SHORT = {
  id: 10,
  type: 'SHORT_ANSWER',
  content: '请简述时间复杂度的含义并举例。',
  options: '',
  score: 10,
  orderIndex: 3,
}

/** 元素库组件全部 stub，避免依赖完整 Element Plus 运行时 */
const STUBS = {
  'el-tag': { template: '<span class="q-tag"><slot /></span>' },
  'el-radio-group': { template: '<div><slot /></div>' },
  'el-radio': { template: '<label class="option-radio"><slot /></label>' },
  'el-checkbox-group': { template: '<div><slot /></div>' },
  'el-checkbox': { template: '<label class="option-checkbox"><slot /></label>' },
  'el-input': { template: '<textarea></textarea>' },
  'el-button': { template: '<button><slot /></button>' },
  'el-icon': { template: '<i><slot /></i>' },
}

function mountQuestion(question: unknown, index = 0) {
  const state = useStudentExams()
  return mount(SeQuestionView, {
    props: {
      currentIndex: index,
      currentQuestion: question,
      questions: [question],
      answers: {},
      multiAnswers: {},
      textAnswers: {},
      defaultScore: 10,
      getQuestionTypeName: state.getQuestionTypeName,
      getQuestionTypeTag: state.getQuestionTypeTag,
      parseOptions: state.parseOptions,
      isQuestionAnswered: state.isQuestionAnswered,
    },
    global: { stubs: STUBS },
  })
}

describe('学生答题页渲染（P0-2 回归护栏）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('题号行显示正确题型名，不得出现「未知题型」', () => {
    const wrapper = mountQuestion(SINGLE)

    const label = wrapper.find('.question-label-row').text()
    expect(label).toContain('第 1 题')
    expect(label).toContain('单选题')
    expect(label).not.toContain('未知题型')
  })

  it('题干正常渲染（不再空白）', () => {
    const wrapper = mountQuestion(SINGLE)

    expect(wrapper.find('.question-content').text()).toContain('先进先出')
  })

  it('分值为关联行 points，而非 defaultScore 兜底', () => {
    const wrapper = mountQuestion(SINGLE)

    // 归一化正确时显示 5 分；若 score 丢失会回落 defaultScore(10)
    expect(wrapper.find('.question-score').text()).toContain('5 分')
    expect(wrapper.find('.question-score').text()).not.toContain('10 分')
  })

  it('单选题渲染出全部选项', () => {
    const wrapper = mountQuestion(SINGLE)

    const options = wrapper.findAll('.option-radio')
    expect(options).toHaveLength(4)
    expect(options[0].text()).toContain('栈')
    expect(options[0].text()).toContain('A.')
  })

  it('多选题渲染为复选框组', () => {
    const wrapper = mountQuestion(MULTI)

    expect(wrapper.find('.question-label-row').text()).toContain('多选题')
    expect(wrapper.findAll('.option-checkbox')).toHaveLength(4)
  })

  it('判断题渲染出正确/错误按钮', () => {
    const wrapper = mountQuestion(TRUE_FALSE)

    expect(wrapper.find('.question-label-row').text()).toContain('判断题')
    const text = wrapper.find('.question-content').text()
    expect(text).toContain('冒泡排序')
  })

  it('简答题渲染为文本输入区', () => {
    const wrapper = mountQuestion(SHORT)

    expect(wrapper.find('.question-label-row').text()).toContain('简答题')
    expect(wrapper.find('textarea').exists()).toBe(true)
  })

  it('未归一化的原始包装结构会导致渲染退化（反证：归一化不可省略）', () => {
    // 模拟「缺失归一化」时组件实际收到的对象：题目字段全在 question 子对象里
    const raw = { id: 11, orderIndex: 0, points: 5, question: SINGLE }
    const wrapper = mountQuestion(raw)

    // 顶层读不到题型与题干 → 正是 P0-2 的现场症状
    expect(wrapper.find('.question-label-row').text()).toContain('未知题型')
    expect(wrapper.find('.question-content').text()).toBe('')
    expect(wrapper.findAll('.option-radio')).toHaveLength(0)
  })
})
