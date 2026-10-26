import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import CeExamQuestionPicker from '@/views/teacher/cockpit/components/CeExamQuestionPicker.vue'
import type { BankQuestion, SelectedQuestion } from '@/composables/useExamCompose'

/**
 * CeExamQuestionPicker 选题区核心行为（T03/T04）：
 * 勾选 emit、已选分值输入联动、题型筛选/翻页 emit、已选合计行展示。
 */

const stubs = {
  'el-select': {
    props: ['modelValue'],
    emits: ['change'],
    template:
      '<div class="select-stub" @click="$emit(\'change\', \'JUDGE\')">filter</div>',
  },
  'el-option': true,
  'el-checkbox': {
    props: ['modelValue'],
    emits: ['change'],
    template:
      '<label class="cb" :data-checked="modelValue ? \'1\' : \'0\'" @click="$emit(\'change\', true)"><slot /></label>',
  },
  'el-input-number': {
    props: ['modelValue'],
    emits: ['change'],
    template:
      '<input class="pts-input" :value="modelValue" @change="$emit(\'change\', 6)" />',
  },
  'el-tag': { template: '<span class="tag-stub"><slot /></span>' },
  'el-pagination': {
    props: ['total', 'pageSize', 'currentPage'],
    emits: ['current-change'],
    template:
      '<div class="pager-stub" :data-total="total" @click="$emit(\'current-change\', 2)">pager</div>',
  },
}

function makeQuestions(n: number): BankQuestion[] {
  return Array.from({ length: n }, (_, i) => ({
    id: i + 1,
    type: i % 2 === 0 ? 'SINGLE_CHOICE' : 'ESSAY',
    content: `题干${i + 1}`,
    score: i + 1,
    difficulty: 'EASY',
  }))
}

function mountPicker(
  questions: BankQuestion[],
  selected: SelectedQuestion[],
  total = questions.length,
  page = 1,
) {
  return mount(CeExamQuestionPicker, {
    props: { questions, selected, total, page, type: '', loading: false },
    global: { stubs, directives: { loading: { mounted() {}, updated() {} } } },
  })
}

describe('CeExamQuestionPicker — 勾选与合计', () => {
  it('已选题行勾选态点亮，未选题行不点亮', () => {
    const questions = makeQuestions(3)
    const selected: SelectedQuestion[] = [
      { questionId: 2, type: 'ESSAY', content: '题干2', score: 2 },
    ]
    const wrapper = mountPicker(questions, selected)
    const checks = wrapper.findAll('.cb')
    expect(checks).toHaveLength(3)
    expect(checks[0].attributes('data-checked')).toBe('0')
    expect(checks[1].attributes('data-checked')).toBe('1')
    expect(checks[2].attributes('data-checked')).toBe('0')
  })

  it('点击勾选框 emit toggle（携带整行题目）', async () => {
    const wrapper = mountPicker(makeQuestions(2), [])
    await wrapper.findAll('.cb')[1].trigger('click')
    expect(wrapper.emitted('toggle')).toHaveLength(1)
    expect((wrapper.emitted('toggle')![0] as unknown[])[0]).toMatchObject({ id: 2 })
  })

  it('已选题行渲染分值输入并 emit update-points；未选题行只读展示默认分', async () => {
    const questions = makeQuestions(2)
    const selected: SelectedQuestion[] = [
      { questionId: 1, type: 'SINGLE_CHOICE', content: '题干1', score: 3 },
    ]
    const wrapper = mountPicker(questions, selected)

    expect(wrapper.find('.pts-hint').text()).toBe('2 分') // 未选题（score=2）
    const inputs = wrapper.findAll('.pts-input')
    expect(inputs).toHaveLength(1)
    await inputs[0].trigger('change')
    expect(wrapper.emitted('update-points')).toEqual([[1, 6]])
  })

  it('合计行实时汇总已选分值与题数', () => {
    const selected: SelectedQuestion[] = [
      { questionId: 1, type: 'SINGLE_CHOICE', content: '题干1', score: 3 },
      { questionId: 2, type: 'ESSAY', content: '题干2', score: 2.5 },
    ]
    const wrapper = mountPicker(makeQuestions(3), selected)
    expect(wrapper.get('.picked-summary').text()).toContain('已选 2 题')
    expect(wrapper.get('.picked-summary').text()).toContain('5.5 分')
  })
})

describe('CeExamQuestionPicker — 筛选与分页', () => {
  it('题型筛选变更 emit type-change（携带题型实际值）', async () => {
    const wrapper = mountPicker(makeQuestions(2), [])
    await wrapper.get('.select-stub').trigger('click')
    expect(wrapper.emitted('type-change')).toEqual([['JUDGE']])
  })

  it('总数超页容量时渲染分页器并 emit page-change', async () => {
    const wrapper = mountPicker(makeQuestions(10), [], 24, 1)
    expect(wrapper.get('.pager-stub').attributes('data-total')).toBe('24')
    await wrapper.get('.pager-stub').trigger('click')
    expect(wrapper.emitted('page-change')).toEqual([[2]])
  })

  it('总数不超页容量时不渲染分页器', () => {
    const wrapper = mountPicker(makeQuestions(5), [])
    expect(wrapper.find('.pager-stub').exists()).toBe(false)
  })

  it('题库为空且非加载中：显示空态文案', () => {
    const wrapper = mountPicker([], [])
    expect(wrapper.get('.picker-empty').text()).toBe('题库中暂无匹配题目')
  })
})
