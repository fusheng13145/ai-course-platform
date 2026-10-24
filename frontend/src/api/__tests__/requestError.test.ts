import { describe, it, expect } from 'vitest'
import {
  getStatusMessage,
  resolveNetworkErrorMessage,
  isIdempotentMethod,
  extractFieldErrorSummary,
  STATUS_MESSAGES,
  IDEMPOTENT_METHODS,
} from '../requestError'

describe('requestError 纯函数层（FE-07）', () => {
  describe('getStatusMessage', () => {
    it('已知状态码返回映射文案', () => {
      expect(getStatusMessage(401)).toBe('登录已过期，请重新登录')
      expect(getStatusMessage(403)).toBe('抱歉，您没有权限执行此操作')
      expect(getStatusMessage(429)).toBe('操作过于频繁，请稍后再试')
      expect(getStatusMessage(503)).toBe('服务维护中，请稍后访问')
    })

    it('未知状态码回退为「请求失败 (status)」', () => {
      expect(getStatusMessage(599)).toBe('请求失败 (599)')
    })

    it('缺失状态码返回空串（由调用方兜底）', () => {
      expect(getStatusMessage(undefined)).toBe('')
      expect(getStatusMessage(null as unknown as undefined)).toBe('')
    })

    it('文案表覆盖原 request.ts 全部 18 个状态码且无空值', () => {
      const expected = [
        400, 401, 403, 404, 405, 408, 409, 410, 413, 415, 422, 423, 429, 500, 501, 502, 503, 504,
      ]
      expect(
        Object.keys(STATUS_MESSAGES)
          .map(Number)
          .sort((a, b) => a - b),
      ).toEqual(expected)
      for (const v of Object.values(STATUS_MESSAGES)) expect(v).toBeTruthy()
    })
  })

  describe('resolveNetworkErrorMessage', () => {
    it('timeout / Timeout → 请求超时', () => {
      expect(resolveNetworkErrorMessage('timeout of 30000ms exceeded')).toBe(
        '请求超时，请检查网络连接',
      )
      expect(resolveNetworkErrorMessage('TimeoutError')).toBe('请求超时，请检查网络连接')
    })

    it('Network Error → 网络不可用', () => {
      expect(resolveNetworkErrorMessage('Network Error')).toBe('网络不可用，请检查网络设置')
    })

    it('断网错误码 → 网络已断开', () => {
      expect(resolveNetworkErrorMessage('net::ERR_INTERNET_DISCONNECTED')).toBe(
        '网络已断开，请连接后重试',
      )
    })

    it('其余消息回退「网络异常」', () => {
      expect(resolveNetworkErrorMessage('fetch failed')).toBe('网络异常')
      expect(resolveNetworkErrorMessage('')).toBe('网络异常')
    })
  })

  describe('isIdempotentMethod（P1-7 重试白名单）', () => {
    it('GET/HEAD/OPTIONS 幂等', () => {
      expect(IDEMPOTENT_METHODS).toEqual(['get', 'head', 'options'])
      expect(isIdempotentMethod('get')).toBe(true)
      expect(isIdempotentMethod('GET')).toBe(true)
      expect(isIdempotentMethod('head')).toBe(true)
      expect(isIdempotentMethod('options')).toBe(true)
    })

    it('写方法与缺失方法不重试（缺失按 get 处理保持原默认）', () => {
      expect(isIdempotentMethod('post')).toBe(false)
      expect(isIdempotentMethod('delete')).toBe(false)
      expect(isIdempotentMethod('put')).toBe(false)
      expect(isIdempotentMethod(undefined)).toBe(true)
    })
  })

  describe('extractFieldErrorSummary（422 字段级摘要）', () => {
    it('提取 msg / message 并分号连接', () => {
      expect(extractFieldErrorSummary([{ msg: '标题不能为空' }, { message: '分值必须为正' }])).toBe(
        '标题不能为空; 分值必须为正',
      )
    })

    it('过滤空条目', () => {
      expect(extractFieldErrorSummary([{ msg: '' }, {}, { message: '有效' }])).toBe('有效')
    })

    it('非数组或空数组返回空串（由调用方回退通用文案）', () => {
      expect(extractFieldErrorSummary(undefined)).toBe('')
      expect(extractFieldErrorSummary([])).toBe('')
      expect(extractFieldErrorSummary('oops' as unknown as unknown[])).toBe('')
    })
  })
})
