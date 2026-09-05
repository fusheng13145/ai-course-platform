import { describe, it, expect } from 'vitest'
import { formatTime, formatDate, formatDateTime } from '@/utils/datetime'

describe('datetime 日期格式化工具', () => {
  describe('formatTime (YYYY-MM-DD HH:mm)', () => {
    it('null / undefined 返回默认 fallback', () => {
      expect(formatTime(null)).toBe('-')
      expect(formatTime(undefined)).toBe('-')
    })

    it('支持自定义 fallback', () => {
      expect(formatTime(null, 'N/A')).toBe('N/A')
    })

    it('有效日期字符串格式化到分钟', () => {
      expect(formatTime('2026-09-05 08:30:59')).toBe('2026-09-05 08:30')
    })

    it('无效输入返回 fallback', () => {
      expect(formatTime('not-a-date')).toBe('-')
    })
  })

  describe('formatDate (YYYY-MM-DD)', () => {
    it('有效日期字符串只保留日期', () => {
      expect(formatDate('2026-09-05 08:30:00')).toBe('2026-09-05')
    })

    it('空值返回 fallback', () => {
      expect(formatDate('')).toBe('-')
    })
  })

  describe('formatDateTime (YYYY-MM-DD HH:mm:ss)', () => {
    it('有效日期时间保留到秒', () => {
      expect(formatDateTime('2026-09-05 08:30:00')).toBe('2026-09-05 08:30:00')
    })

    it('无效值返回 fallback', () => {
      expect(formatDateTime('invalid')).toBe('-')
    })
  })
})
