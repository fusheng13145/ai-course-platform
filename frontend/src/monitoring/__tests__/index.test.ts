import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/utils/logger', () => ({
  default: { info: vi.fn(), error: vi.fn(), warn: vi.fn() },
}))

import { initMonitoring, captureFrontendError } from '@/monitoring'
import logger from '@/utils/logger'

describe('monitoring 前端错误监控', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.stubGlobal('fetch', vi.fn())
  })

  it('initMonitoring 可重复调用且不抛错（幂等）', () => {
    expect(() => {
      initMonitoring()
      initMonitoring()
    }).not.toThrow()
  })

  it('captureFrontendError 记录错误/信息/组件日志（endpoint 未配置时静默降级，不触发上报）', () => {
    const err = new Error('boom')
    captureFrontendError(err, 'render', 'LoginForm')

    expect(logger.error).toHaveBeenCalledWith('[Global Error]', err)
    expect(logger.error).toHaveBeenCalledWith('[Error Info]', 'render')
    expect(logger.error).toHaveBeenCalledWith('[Component]', 'LoginForm')
    // endpoint 为空，不应触发 sendBeacon / fetch
    expect(fetch).not.toHaveBeenCalled()
  })

  it('captureFrontendError 对非 Error 入参与缺省 component 容错', () => {
    captureFrontendError('string error', undefined, undefined)
    expect(logger.error).toHaveBeenCalledWith('[Component]', 'Unknown')
  })
})
