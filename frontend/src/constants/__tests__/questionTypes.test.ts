import { describe, expect, it } from 'vitest'
import {
  QUESTION_TYPE_OPTIONS,
  questionTypeLabel,
  questionTypeTag,
} from '@/constants/questionTypes'

/**
 * 题型契约常量测试（2026-10-26 F3 契约更正护栏）。
 *
 * 背景：曾误按设计 F3 用 SINGLE/JUDGE/FILL/CODE 风格，与实库脱靶——
 * 后端 typeRules 按原值精确分组，错 key 直接 400「没有匹配到符合条件的题目」，
 * 且筛选/标签恒失效。本测试钉死 key 与实库值严格一致，防止回归。
 *
 * 实库证据（QA 实查 courseId=4007 全部 68 题 + 后端源码）：
 * SINGLE_CHOICE(38)/MULTIPLE_CHOICE(10)/TRUE_FALSE(8)/FILL_BLANK(7)/ESSAY(5)；
 * V2 列注释与 V5 seed 同口径；QuestionVO.allowableValues 无 CODE。
 */
const REAL_LIBRARY_KEYS = ['SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE', 'FILL_BLANK', 'ESSAY']

describe('questionTypes — 实库题型契约', () => {
  it('选项 key 与实库 Question.type 实际值严格一致（顺序无关）', () => {
    expect([...QUESTION_TYPE_OPTIONS.map((o) => o.value)].sort()).toEqual(
      [...REAL_LIBRARY_KEYS].sort(),
    )
  })

  it('不含已被实库淘汰的旧风格 key（SINGLE/JUDGE/FILL/CODE 等）', () => {
    const keys = QUESTION_TYPE_OPTIONS.map((o) => o.value)
    for (const stale of ['SINGLE', 'MULTIPLE', 'JUDGE', 'FILL', 'CODE']) {
      expect(keys).not.toContain(stale)
    }
  })

  it('questionTypeLabel：实库值 → 中文标签；未知/空值降级', () => {
    expect(questionTypeLabel('SINGLE_CHOICE')).toBe('单选题')
    expect(questionTypeLabel('MULTIPLE_CHOICE')).toBe('多选题')
    expect(questionTypeLabel('TRUE_FALSE')).toBe('判断题')
    expect(questionTypeLabel('FILL_BLANK')).toBe('填空题')
    expect(questionTypeLabel('ESSAY')).toBe('简答题')
    // 大小写不敏感 + 未知值透传原始串（便于发现新题型）而非吞掉
    expect(questionTypeLabel('single_choice')).toBe('单选题')
    expect(questionTypeLabel('SHORT_ANSWER')).toBe('SHORT_ANSWER')
    expect(questionTypeLabel('')).toBe('未知')
    expect(questionTypeLabel(null)).toBe('未知')
  })

  it('questionTypeTag：实库值 → 色板；未知/空值降级 info', () => {
    expect(questionTypeTag('SINGLE_CHOICE')).toBe('primary')
    expect(questionTypeTag('TRUE_FALSE')).toBe('warning')
    expect(questionTypeTag('ESSAY')).toBe('')
    expect(questionTypeTag('UNKNOWN_TYPE')).toBe('info')
    expect(questionTypeTag(null)).toBe('info')
  })
})
