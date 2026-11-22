import { describe, expect, it } from 'vitest'
import {
  gradeOrderLabel,
  gradeTimeValue,
  sortGradeItems,
  toggleGradeOrder,
} from '../gradeOrder'

type Row = { key: string; submittedAt: string; title?: string }

const rows: Row[] = [
  { key: 'mid', submittedAt: '2026-11-19 18:06', title: '期中' },
  { key: 'old', submittedAt: '2026-11-08 15:06', title: '第一周作业' },
  { key: 'new', submittedAt: '2026-11-22 12:55', title: '重考' },
]

describe('gradeTimeValue', () => {
  it('解析空格分隔与 ISO 两种写法，缺失/占位符返回 NaN', () => {
    expect(gradeTimeValue('2026-11-22 12:55')).toBe(Date.parse('2026-11-22T12:55'))
    expect(gradeTimeValue('2026-11-22T12:55:45')).toBe(Date.parse('2026-11-22T12:55:45'))
    expect(Number.isNaN(gradeTimeValue(''))).toBe(true)
    expect(Number.isNaN(gradeTimeValue('—'))).toBe(true)
    expect(Number.isNaN(gradeTimeValue(null))).toBe(true)
  })
})

describe('sortGradeItems', () => {
  it('倒序：最新提交在前', () => {
    expect(sortGradeItems(rows, 'desc').map((r) => r.key)).toEqual(['new', 'mid', 'old'])
  })

  it('正序：最早提交在前', () => {
    expect(sortGradeItems(rows, 'asc').map((r) => r.key)).toEqual(['old', 'mid', 'new'])
  })

  it('无提交时间的一律沉底，不随正/倒序翻转', () => {
    const withMissing: Row[] = [
      { key: 'a', submittedAt: '2026-11-20 10:00' },
      { key: 'empty', submittedAt: '' },
      { key: 'b', submittedAt: '2026-11-21 10:00' },
      { key: 'dash', submittedAt: '—' },
    ]
    expect(sortGradeItems(withMissing, 'desc').map((r) => r.key)).toEqual([
      'b',
      'a',
      'dash',
      'empty',
    ])
    expect(sortGradeItems(withMissing, 'asc').map((r) => r.key)).toEqual([
      'a',
      'b',
      'dash',
      'empty',
    ])
  })

  it('同一时间戳固定按 key 升序，不随方向翻转，且不改动入参', () => {
    const same: Row[] = [
      { key: 'k2', submittedAt: '2026-11-20 17:06' },
      { key: 'k1', submittedAt: '2026-11-20 17:06' },
    ]
    const before = same.map((r) => r.key)
    expect(sortGradeItems(same, 'desc').map((r) => r.key)).toEqual(['k1', 'k2'])
    expect(sortGradeItems(same, 'asc').map((r) => r.key)).toEqual(['k1', 'k2'])
    expect(same.map((r) => r.key)).toEqual(before)
  })
})

describe('排序切换文案', () => {
  it('倒序显示最新优先，正序显示最早优先，可来回切换', () => {
    expect(gradeOrderLabel('desc')).toContain('最新优先')
    expect(gradeOrderLabel('asc')).toContain('最早优先')
    expect(toggleGradeOrder('desc')).toBe('asc')
    expect(toggleGradeOrder('asc')).toBe('desc')
  })
})
