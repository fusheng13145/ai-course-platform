import { describe, it, expect, vi } from 'vitest'

vi.mock('@/utils/logger', () => ({
  default: { log: vi.fn(), warn: vi.fn(), error: vi.fn() },
}))

import { send, on, off, disconnect, isWebSocketConnected } from '@/utils/websocket'
import logger from '@/utils/logger'

describe('websocket 实时通信服务（未连接状态）', () => {
  it('send 在连接未建立时返回 false 并告警', () => {
    expect(send({ type: 'test' })).toBe(false)
    expect(logger.warn).toHaveBeenCalled()
  })

  it('on 注册回调返回取消订阅函数，off 清除该类型全部监听', () => {
    const cb = vi.fn()
    const unsubscribe = on('notice', cb)
    expect(unsubscribe).toBeTypeOf('function')
    // 取消订阅后再 off，不应抛错
    unsubscribe()
    off('notice')
  })

  it('disconnect 在未连接时安全退出且不抛错', () => {
    expect(() => disconnect()).not.toThrow()
    expect(isWebSocketConnected).toBe(false)
  })
})
