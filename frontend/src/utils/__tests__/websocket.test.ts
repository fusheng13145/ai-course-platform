import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * src/utils/websocket.ts 真实行为测试。
 *
 * 模块持有 socket / listeners / reconnectAttempts / manuallyClosed 等「模块级可变状态」，
 * 所以每个用例都用 vi.resetModules() + 动态 import 取一份全新实例，用例之间互不污染。
 * 传输层完全由 FakeWebSocket 接管，不产生任何真实网络连接。
 */

const { loggerMock } = vi.hoisted(() => ({
  loggerMock: { log: vi.fn(), warn: vi.fn(), error: vi.fn(), info: vi.fn(), debug: vi.fn() },
}))

vi.mock('@/utils/logger', () => ({ default: loggerMock, logger: loggerMock }))

type WsModule = typeof import('@/utils/websocket')

/** 状态码与浏览器 WebSocket 对齐：源码用 WebSocket.OPEN / CONNECTING 做判断 */
class FakeWebSocket {
  static readonly CONNECTING = 0
  static readonly OPEN = 1
  static readonly CLOSING = 2
  static readonly CLOSED = 3
  /** 所有被构造过的实例：用于断言「是否重复建连」「重连了几次」 */
  static instances: FakeWebSocket[] = []

  static latest(): FakeWebSocket {
    return FakeWebSocket.instances[FakeWebSocket.instances.length - 1]
  }

  url: string
  readyState: number
  sent: string[] = []
  /** send 被调用次数（含抛错的调用），用于判断异常是否中断后续心跳 */
  sendAttempts = 0
  sendShouldThrow = false
  closeShouldThrow = false

  onopen: ((event?: unknown) => void) | null = null
  onmessage: ((event: { data: string }) => void) | null = null
  onerror: ((event: unknown) => void) | null = null
  onclose: ((event: { code: number; reason?: string }) => void) | null = null

  constructor(url: string) {
    this.url = url
    this.readyState = FakeWebSocket.CONNECTING
    FakeWebSocket.instances.push(this)
  }

  /** 握手成功 */
  open(): void {
    this.readyState = FakeWebSocket.OPEN
    this.onopen?.()
  }

  /** 握手失败：真实浏览器是 error 之后再 close，这里拆开便于单独覆盖每个回调 */
  fail(err: unknown): void {
    this.onerror?.(err)
  }

  /** 对端/网络断开（非主动 disconnect），会触发自动重连 */
  closeByPeer(code = 1006): void {
    this.readyState = FakeWebSocket.CLOSED
    this.onclose?.({ code })
  }

  emitMessage(payload: unknown): void {
    this.onmessage?.({ data: JSON.stringify(payload) })
  }

  /** 直接投递原始字符串，用于构造非法 JSON */
  emitRaw(data: string): void {
    this.onmessage?.({ data })
  }

  send(data: string): void {
    this.sendAttempts++
    if (this.sendShouldThrow) throw new Error('send 失败')
    this.sent.push(data)
  }

  close(code?: number, reason?: string): void {
    if (this.closeShouldThrow) throw new Error('close 失败')
    this.sent.push(`close:${code}:${reason}`)
    this.readyState = FakeWebSocket.CLOSED
  }
}

let ws: WsModule

/** 建连并握手成功，返回当前 socket */
async function connectAndOpen(): Promise<FakeWebSocket> {
  const pending = ws.connect()
  const inst = FakeWebSocket.latest()
  inst.open()
  await pending
  return inst
}

beforeEach(async () => {
  vi.useFakeTimers()
  vi.clearAllMocks()
  FakeWebSocket.instances = []
  vi.stubGlobal('WebSocket', FakeWebSocket as unknown as typeof globalThis.WebSocket)
  // 每个用例一份干净模块实例，避免模块级状态串味
  vi.resetModules()
  ws = await import('@/utils/websocket')
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

describe('websocket 实时通信服务（未连接状态）', () => {
  it('send 在连接未建立时返回 false 并告警', () => {
    expect(ws.send({ type: 'test' })).toBe(false)
    expect(loggerMock.warn).toHaveBeenCalledWith('[WS] Not connected, message not sent')
    expect(FakeWebSocket.instances).toHaveLength(0)
  })

  it('on 注册回调返回取消订阅函数，off 清除该类型全部监听', () => {
    const cb = vi.fn()
    const unsubscribe = ws.on('notice', cb)
    expect(unsubscribe).toBeTypeOf('function')
    // 取消订阅后再 off，不应抛错
    unsubscribe()
    ws.off('notice')
    expect(ws.listenerCount()).toBe(0)
  })

  it('disconnect 在未连接时安全退出且不抛错', () => {
    expect(() => ws.disconnect()).not.toThrow()
    expect(ws.isWebSocketConnected).toBe(false)
  })
})

describe('建连与地址拼接', () => {
  it('首次 connect 成功后状态为已连接，并建立 30s 周期心跳', async () => {
    const inst = await connectAndOpen()

    expect(ws.isWebSocketConnected).toBe(true)
    expect(inst.url).toBe('ws://localhost:3000/api/ws')
    // 心跳未到点前不发 ping
    expect(inst.sent).toHaveLength(0)

    vi.advanceTimersByTime(29999)
    expect(inst.sent).toHaveLength(0)
    vi.advanceTimersByTime(1)
    expect(inst.sent).toHaveLength(1)

    const ping = JSON.parse(inst.sent[0])
    expect(ping.type).toBe('ping')
    expect(typeof ping.timestamp).toBe('number')

    // setInterval 周期触发第二次心跳
    vi.advanceTimersByTime(30000)
    expect(inst.sent).toHaveLength(2)
    // 心跳定时器只有一个（startHeartbeat 前先 stopHeartbeat）
    expect(vi.getTimerCount()).toBe(1)
  })

  it('地址不含 token：凭据由后端 HttpOnly Cookie 在握手时携带', async () => {
    const pending = ws.connect()
    const inst = FakeWebSocket.latest()
    expect(inst.url).not.toMatch(/token/i)
    expect(inst.url).not.toContain('?')
    // http 页面对应 ws:（源码只按 protocol 切换 ws/wss，其余原样拼接 host）
    expect(inst.url).toBe(`ws://${window.location.host}/api/ws`)
    pending.catch(() => {})
  })

  it('https 页面下拼接为 wss 协议', async () => {
    vi.stubGlobal('location', { protocol: 'https:', host: 'school.example:8443' })
    vi.resetModules()
    const secure = await import('@/utils/websocket')
    const pending = secure.connect()
    pending.catch(() => {})
    expect(FakeWebSocket.latest().url).toBe('wss://school.example:8443/api/ws')
  })

  it('CONNECTING 阶段重复 connect 直接返回，不重复建连', async () => {
    const pending = ws.connect()
    await expect(ws.connect()).resolves.toBeUndefined()
    expect(FakeWebSocket.instances).toHaveLength(1)
    pending.catch(() => {})
  })

  it('OPEN 阶段重复 connect 同样短路', async () => {
    await connectAndOpen()
    await expect(ws.connect()).resolves.toBeUndefined()
    expect(FakeWebSocket.instances).toHaveLength(1)
  })

  it('socket 已 CLOSED 时 connect 会新建连接且不复用旧实例', async () => {
    const inst = await connectAndOpen()
    inst.readyState = FakeWebSocket.CLOSED

    const pending = ws.connect()
    expect(FakeWebSocket.instances).toHaveLength(2)
    expect(FakeWebSocket.latest()).not.toBe(inst)
    FakeWebSocket.latest().open()
    await pending

    // 没有遗留的自动重连：推进时间不会再冒出新实例
    vi.advanceTimersByTime(60000)
    expect(FakeWebSocket.instances).toHaveLength(2)
  })

  it('环境缺失 WebSocket 构造器时 connect() 以异常 reject（源码无降级守卫）', async () => {
    vi.stubGlobal('WebSocket', undefined)
    await expect(ws.connect()).rejects.toBeInstanceOf(TypeError)
  })
})

describe('消息分发', () => {
  it('onmessage 按事件名分发给对应监听器，* 通配符收到完整消息', async () => {
    const inst = await connectAndOpen()
    const onChat = vi.fn()
    const onAll = vi.fn()
    ws.on('chat', onChat)
    ws.on('*', onAll)

    const msg = { type: 'chat', data: { id: 7, text: 'hi' } }
    inst.emitMessage(msg)

    expect(onChat).toHaveBeenCalledTimes(1)
    expect(onChat).toHaveBeenCalledWith(msg.data)
    expect(onAll).toHaveBeenCalledTimes(1)
    expect(onAll).toHaveBeenCalledWith(msg)
  })

  it('未注册的类型不会误投递给其它监听器', async () => {
    const inst = await connectAndOpen()
    const onNotice = vi.fn()
    ws.on('notice', onNotice)
    inst.emitMessage({ type: 'chat', data: 1 })
    expect(onNotice).not.toHaveBeenCalled()
  })

  it('单个监听器抛错被隔离，同类型其余监听器仍收到消息', async () => {
    const inst = await connectAndOpen()
    const bad = vi.fn(() => {
      throw new Error('订阅者炸了')
    })
    const good = vi.fn()
    ws.on('chat', bad)
    ws.on('chat', good)

    expect(() => inst.emitMessage({ type: 'chat', data: 'x' })).not.toThrow()
    expect(bad).toHaveBeenCalledTimes(1)
    expect(good).toHaveBeenCalledWith('x')
    expect(loggerMock.error).toHaveBeenCalledWith('[WS] Handler error:', expect.any(Error))
  })

  it('* 通配符监听器抛错同样被隔离，不影响其它通配订阅者', async () => {
    const inst = await connectAndOpen()
    const bad = vi.fn(() => {
      throw new Error('通配订阅者炸了')
    })
    const good = vi.fn()
    ws.on('*', bad)
    ws.on('*', good)

    expect(() => inst.emitMessage({ type: 'chat', data: 'x' })).not.toThrow()
    expect(good).toHaveBeenCalledTimes(1)
  })

  it('非法 JSON 只告警丢弃，不影响后续正常消息', async () => {
    const inst = await connectAndOpen()
    const onChat = vi.fn()
    ws.on('chat', onChat)

    expect(() => inst.emitRaw('{ 这不是合法 JSON')).not.toThrow()
    expect(loggerMock.warn).toHaveBeenCalledWith('[WS] Parse error:', expect.any(Error))
    expect(onChat).not.toHaveBeenCalled()

    inst.emitMessage({ type: 'chat', data: 'ok' })
    expect(onChat).toHaveBeenCalledWith('ok')
  })

  it('取消订阅后不再收消息；off 清除该类型全部监听；clearListeners 清空所有', async () => {
    const inst = await connectAndOpen()
    const a = vi.fn()
    const b = vi.fn()
    const unsubscribe = ws.on('chat', a)
    ws.on('chat', b)
    expect(ws.listenerCount()).toBe(2)

    unsubscribe()
    inst.emitMessage({ type: 'chat', data: 1 })
    expect(a).not.toHaveBeenCalled()
    expect(b).toHaveBeenCalledTimes(1)

    ws.off('chat')
    inst.emitMessage({ type: 'chat', data: 2 })
    expect(b).toHaveBeenCalledTimes(1)

    ws.on('notice', a)
    ws.clearListeners()
    expect(ws.listenerCount()).toBe(0)
  })
})

describe('发送', () => {
  it('OPEN 时 send 序列化 payload 并返回 true', async () => {
    const inst = await connectAndOpen()
    expect(ws.send({ type: 'cmd', payload: { a: 1 } })).toBe(true)
    expect(inst.sent[0]).toBe(JSON.stringify({ type: 'cmd', payload: { a: 1 } }))
  })

  it('socket 存在但非 OPEN 时 send 返回 false 且不调用底层 send', async () => {
    const inst = await connectAndOpen()
    inst.readyState = FakeWebSocket.CLOSING
    expect(ws.send({ type: 'cmd' })).toBe(false)
    expect(inst.sendAttempts).toBe(0)
    expect(loggerMock.warn).toHaveBeenCalledWith('[WS] Not connected, message not sent')
  })

  it('底层 send 抛错时 send() 捕获并返回 false，异常不再逸出心跳定时器', async () => {
    // 缺陷回归（2026-10-04）：旧实现没有 try/catch，socket.send 抛错既把异常直接甩给
    // 业务调用方（一次点击即中断当前逻辑），又让每 30s 的心跳回调抛出未处理异常。
    const inst = await connectAndOpen()
    inst.sendShouldThrow = true

    expect(ws.send({ type: 'cmd' })).toBe(false)
    expect(loggerMock.warn).toHaveBeenCalledWith('[WS] send 失败:', expect.any(Error))
    // 心跳回调同样被保护：定时器回调里不再有异常逸出
    expect(() => vi.advanceTimersByTime(30000)).not.toThrow()
    expect(inst.sendAttempts).toBe(2)

    // 底层恢复后同一连接继续正常发 ping（说明定时器没有被摧毁）
    inst.sendShouldThrow = false
    expect(() => vi.advanceTimersByTime(30000)).not.toThrow()
    expect(inst.sent).toHaveLength(1)
    expect(JSON.parse(inst.sent[0]).type).toBe('ping')
  })
})

describe('重连与退避', () => {
  it('onclose 后按指数退避调度重连，延迟逐次递增', async () => {
    const inst1 = await connectAndOpen()
    inst1.closeByPeer(1006)
    expect(ws.isWebSocketConnected).toBe(false)

    const delays = [1000, 2000, 4000, 8000, 16000]
    for (const delay of delays) {
      const before = FakeWebSocket.instances.length
      // 退避未到期前不应提前重连
      vi.advanceTimersByTime(delay - 1)
      expect(FakeWebSocket.instances).toHaveLength(before)
      vi.advanceTimersByTime(1)
      expect(FakeWebSocket.instances).toHaveLength(before + 1)
      FakeWebSocket.latest().closeByPeer(1006)
    }
  })

  it('重连达到 5 次上限后不再调度', async () => {
    const inst1 = await connectAndOpen()
    for (let i = 0; i < 5; i++) {
      FakeWebSocket.latest().closeByPeer(1006)
      vi.advanceTimersByTime(30000)
    }
    expect(FakeWebSocket.instances).toHaveLength(6)

    loggerMock.log.mockClear()
    FakeWebSocket.latest().closeByPeer(1006)
    expect(loggerMock.log).toHaveBeenCalledWith('[WS] Max reconnect attempts reached')
    vi.advanceTimersByTime(120000)
    expect(FakeWebSocket.instances).toHaveLength(6)
    expect(inst1.readyState).toBe(FakeWebSocket.CLOSED)
  })

  it('重连成功后重试计数归零，再次断开仍从最短延迟开始', async () => {
    const inst1 = await connectAndOpen()
    inst1.closeByPeer(1006)
    vi.advanceTimersByTime(1000)
    const inst2 = FakeWebSocket.latest()
    expect(inst2).not.toBe(inst1)

    inst2.open()
    expect(ws.isWebSocketConnected).toBe(true)
    // 计数已重置：下一次退避回到 1000ms 而不是 2000ms
    inst2.closeByPeer(1006)
    vi.advanceTimersByTime(999)
    expect(FakeWebSocket.instances).toHaveLength(2)
    vi.advanceTimersByTime(1)
    expect(FakeWebSocket.instances).toHaveLength(3)
  })

  it('首次 connect 握手失败时返回的 promise 以 onerror 载荷 reject', async () => {
    const pending = ws.connect()
    const err = new Error('握手失败')
    FakeWebSocket.latest().fail(err)
    await expect(pending).rejects.toBe(err)
    expect(loggerMock.error).toHaveBeenCalledWith('[WS] Error:', err)
    // onerror 本身不驱动重连，须等 close 事件
    vi.advanceTimersByTime(60000)
    expect(FakeWebSocket.instances).toHaveLength(1)
  })

  it('回归 P1-11①：重连时 connect reject 不产生 unhandledRejection，且重连链继续', async () => {
    const rejections: unknown[] = []
    const onRejection = (reason: unknown) => rejections.push(reason)
    process.on('unhandledRejection', onRejection)

    try {
      const inst1 = await connectAndOpen()
      inst1.closeByPeer(1006)
      await vi.advanceTimersByTimeAsync(1000)

      const inst2 = FakeWebSocket.latest()
      expect(inst2).not.toBe(inst1)
      // 重连握手失败：connect() 内部 promise reject，源码用 .catch 兜住
      const err = new Error('重连握手失败')
      inst2.fail(err)
      await vi.advanceTimersByTimeAsync(0)

      expect(loggerMock.warn).toHaveBeenCalledWith('[WS] Reconnect failed:', err)
      expect(rejections).toHaveLength(0)

      // 重连链没有因为 reject 而断裂：下一次退避照常调度
      inst2.closeByPeer(1006)
      await vi.advanceTimersByTimeAsync(2000)
      expect(FakeWebSocket.instances).toHaveLength(3)
      expect(rejections).toHaveLength(0)
    } finally {
      process.off('unhandledRejection', onRejection)
    }
  })

  it('主动 disconnect 后不再调度任何重连', async () => {
    const inst1 = await connectAndOpen()
    inst1.closeByPeer(1006)
    ws.disconnect()
    await vi.advanceTimersByTimeAsync(120000)
    expect(FakeWebSocket.instances).toHaveLength(1)
    expect(vi.getTimerCount()).toBe(0)
  })
})

describe('disconnect 与订阅清理', () => {
  it('回归 P1-11②：disconnect 清空 listeners，重新登录后旧页面回调不再被触发', async () => {
    const inst1 = await connectAndOpen()
    const legacyCb = vi.fn()
    ws.on('notice', legacyCb)
    ws.on('*', legacyCb)
    expect(ws.listenerCount()).toBe(2)

    ws.disconnect()
    expect(ws.listenerCount()).toBe(0)

    // 下一个用户登录，复用同一模块实例
    const inst2 = await connectAndOpen()
    expect(inst2).not.toBe(inst1)
    inst2.emitMessage({ type: 'notice', data: '新用户消息' })
    expect(legacyCb).not.toHaveBeenCalled()

    // 新会话的订阅正常工作
    const freshCb = vi.fn()
    ws.on('notice', freshCb)
    inst2.emitMessage({ type: 'notice', data: '再次推送' })
    expect(freshCb).toHaveBeenCalledWith('再次推送')
  })

  it('disconnect 摘除 socket 全部回调并主动 close', async () => {
    const inst = await connectAndOpen()
    const staleOnClose = inst.onclose

    ws.disconnect()

    expect(inst.onopen).toBeNull()
    expect(inst.onmessage).toBeNull()
    expect(inst.onerror).toBeNull()
    expect(inst.onclose).toBeNull()
    expect(inst.sent).toContain('close:1000:Client disconnect')
    expect(ws.isWebSocketConnected).toBe(false)

    // 悬挂回调（例如被页面持有的旧引用）被触发时，manuallyClosed 抑制重连
    staleOnClose?.({ code: 1006 })
    vi.advanceTimersByTime(120000)
    expect(FakeWebSocket.instances).toHaveLength(1)
  })

  it('disconnect 幂等：连续调用不抛错且不残留心跳/重连定时器', async () => {
    const inst = await connectAndOpen()
    expect(vi.getTimerCount()).toBe(1)

    ws.disconnect()
    expect(() => ws.disconnect()).not.toThrow()
    expect(vi.getTimerCount()).toBe(0)

    const attemptsBefore = inst.sendAttempts
    vi.advanceTimersByTime(120000)
    expect(inst.sendAttempts).toBe(attemptsBefore)
    expect(FakeWebSocket.instances).toHaveLength(1)
    expect(ws.isWebSocketConnected).toBe(false)
  })

  it('disconnect 复位重连计数：触顶后的新会话重新拿到完整重连额度', async () => {
    // 缺陷回归（2026-10-04）：reconnectAttempts 原先只在 onopen 成功时归零，
    // 登出（disconnect）后计数残留，重新登录若首跳失败就直接落在「额度已用完」状态，
    // 整个会话再也不会自动重连。
    await connectAndOpen()
    // 把 5 次重试额度全部耗尽
    for (let i = 0; i < 5; i++) {
      FakeWebSocket.latest().closeByPeer(1006)
      vi.advanceTimersByTime(30000)
    }
    expect(FakeWebSocket.instances).toHaveLength(6)
    FakeWebSocket.latest().closeByPeer(1006)
    expect(loggerMock.log).toHaveBeenCalledWith('[WS] Max reconnect attempts reached')

    // 登出后重新登录：disconnect 已把计数归零，新会话首跳失败仍会退避重试
    ws.disconnect()
    const pending = ws.connect()
    pending.catch(() => {})
    const fresh = FakeWebSocket.latest()
    expect(fresh).not.toBe(FakeWebSocket.instances[0])
    fresh.fail(new Error('握手失败'))
    fresh.closeByPeer(1006)
    await vi.advanceTimersByTimeAsync(120000)

    // 出现第 8 个实例 = 新会话成功调度了重连（旧行为下这里永远停在 7）
    expect(FakeWebSocket.instances).toHaveLength(8)
  })

  it('底层 close 抛错时 disconnect 仍完成全部清理（防御分支）', async () => {
    const inst = await connectAndOpen()
    inst.closeShouldThrow = true
    ws.on('notice', vi.fn())

    expect(() => ws.disconnect()).not.toThrow()
    expect(loggerMock.warn).toHaveBeenCalledWith('[WS] close 异常:', expect.any(Error))
    expect(ws.listenerCount()).toBe(0)
    expect(vi.getTimerCount()).toBe(0)

    // socket 引用已释放：再次 connect 会新建实例而不是被旧实例短路
    const repending = ws.connect()
    expect(FakeWebSocket.instances).toHaveLength(2)
    expect(FakeWebSocket.latest()).not.toBe(inst)
    FakeWebSocket.latest().open()
    await repending
    expect(ws.isWebSocketConnected).toBe(true)
  })
})
