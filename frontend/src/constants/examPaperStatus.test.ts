import { describe, expect, it } from 'vitest'
import { EXAM_PAPER_STATUS, examPaperStatusView } from '@/constants/examPaperStatus'

/**
 * 试卷状态映射契约测试（设计 F1 修复护栏）。
 * CeExamTable 曾直接判 `row.status === '已发布'` 导致已发布卷恒显「草稿」；
 * 状态判定必须且只能走本映射，测试钉死三个枚举 name 的展示口径。
 */
describe('examPaperStatus 映射契约', () => {
  it('三个枚举 name 的 label 与后端持久化语义一一对应', () => {
    expect(EXAM_PAPER_STATUS.DRAFT.label).toBe('草稿')
    expect(EXAM_PAPER_STATUS.PUBLISHED.label).toBe('已发布')
    expect(EXAM_PAPER_STATUS.ARCHIVED.label).toBe('已归档')
  })

  it('仅草稿态可发布（防重复发布：后端对已发布卷会抛「试卷已发布」）', () => {
    expect(EXAM_PAPER_STATUS.DRAFT.canPublish).toBe(true)
    expect(EXAM_PAPER_STATUS.PUBLISHED.canPublish).toBe(false)
    expect(EXAM_PAPER_STATUS.ARCHIVED.canPublish).toBe(false)
  })

  it('examPaperStatusView 按枚举 name 命中，且大小写不敏感', () => {
    expect(examPaperStatusView('PUBLISHED')).toEqual({
      label: '已发布',
      tagType: 'success',
      canPublish: false,
    })
    expect(examPaperStatusView('draft').canPublish).toBe(true)
  })

  it('空值/未知状态静默降级为「状态未知」，canPublish 恒 false，不抛异常', () => {
    expect(examPaperStatusView(null)).toEqual({
      label: '状态未知',
      tagType: 'info',
      canPublish: false,
    })
    expect(examPaperStatusView('')).toEqual(examPaperStatusView(undefined))
    expect(examPaperStatusView('SOMETHING_ELSE').canPublish).toBe(false)
  })
})
