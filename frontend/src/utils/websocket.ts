import logger from '@/utils/logger'
import { type ApiData } from '@/utils/response'
/**
 * WebSocket 实时通信服务
 * 用于：实时通知、AI助手流式输出、在线状态同步
 */

let socket: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null
let heartbeatTimer: ReturnType<typeof setInterval> | null = null
// 回调入参沿用 ApiData 口径：消息体尚未 per-type 建模，回调侧按需收窄
const listeners = new Map<string, Set<(data: ApiData) => void>>()
let isConnected = false
/**
 * 是否处于「客户端主动断开」状态（P1-11）。
 * 原实现 disconnect() 后旧 socket 的 onclose 仍会触发 scheduleReconnect()，
 * 于是登出后若干秒又自动重连回来 —— 加上这里的全局抑制标记才能真正断开。
 */
let manuallyClosed = false

const WS_URL = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/api/ws`

/**
 * 连接 WebSocket。
 * 令牌经后端下发的 HttpOnly Cookie 在握手时由浏览器自动携带（同源/同站），
 * 无需（也不应）在前端传入令牌参数，避免令牌暴露给 JS 上下文。
 * @returns {Promise<void>} 连接成功 resolve，连接失败 reject
 */
export function connect() {
  if (
    socket &&
    (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)
  ) {
    return Promise.resolve()
  }

  // 客户端主动断开后，新的显式 connect() 视为重新开启一个会话
  manuallyClosed = false

  return new Promise<void>((resolve, reject) => {
    socket = new WebSocket(WS_URL)

    socket.onopen = () => {
      logger.log('[WS] Connected')
      isConnected = true
      reconnectAttempts = 0 // 重置重连计数
      startHeartbeat()
      resolve()
    }

    socket.onmessage = (event) => {
      let msg
      try {
        msg = JSON.parse(event.data)
      } catch (e) {
        // 非法 JSON 只记录，不抛出：单条坏消息不得影响后续消息与其它订阅者
        logger.warn('[WS] Parse error:', e)
        return
      }
      handleMessage(msg)
    }

    socket.onerror = (err) => {
      logger.error('[WS] Error:', err)
      reject(err)
    }

    socket.onclose = (event) => {
      logger.log(`[WS] Closed: code=${event.code}`)
      isConnected = false
      stopHeartbeat()
      // 主动断开（登出/卸载）后绝不自动重连，否则 disconnect 会被自己的
      // close 回调撤销，登出后仍持有新连接 → 跨用户串数据
      if (manuallyClosed) return
      // 自动重连（最多5次）
      scheduleReconnect()
    }
  })
}

/**
 * 断开 WebSocket 连接
 *
 * 缺陷修复（P1-11，2026-10-04）：
 * ① 原实现**不清空 listeners** —— 登出后各页面注册的回调仍留在全局 Map 中，
 *    下一个用户登录复用同一模块实例时，旧用户的回调会继续被投递（跨用户串数据）。
 * ② 原实现 close 后 `onclose` 仍会触发 `scheduleReconnect()`，把「主动断开」
 *    又重连回来，等于 disconnect() 完全失效。现在以 manuallyClosed 标记阻断，
 *    并摘除 socket 上的全部事件回调，避免悬挂引用继续驱动状态机。
 */
export function disconnect() {
  manuallyClosed = true
  clearReconnect()
  stopHeartbeat()
  // 缺陷修复（2026-10-04）：重连计数原先只在 onopen 成功时归零，主动断开（登出）后
  // 计数仍停在 MAX_RECONNECT_ATTEMPTS 附近；下次 connect() 若首跳失败，scheduleReconnect
  // 会立即耗尽预算而不再重试。断开即结束一个会话，计数应随之复位。
  reconnectAttempts = 0
  if (socket) {
    socket.onopen = null
    socket.onmessage = null
    socket.onerror = null
    socket.onclose = null
    try {
      socket.close(1000, 'Client disconnect')
    } catch (e) {
      logger.warn('[WS] close 异常:', e)
    }
    socket = null
  }
  isConnected = false
  // 登出即彻底丢弃订阅，杜绝回调残留
  listeners.clear()
}

/** 当前注册的监听器总数（供测试与调试断言订阅已清空） */
export function listenerCount() {
  let n = 0
  listeners.forEach((set) => {
    n += set.size
  })
  return n
}

/** 主动清空全部订阅（不等断开） */
export function clearListeners() {
  listeners.clear()
}

/**
 * 发送消息
 * @param {object} message - 要发送的消息对象（会自动 JSON 序列化）
 * @returns {boolean} 是否发送成功（false 表示连接未建立）
 */
export function send(message: ApiData) {
  if (socket && socket.readyState === WebSocket.OPEN) {
    // 修复（2026-10-04）：readyState 为 OPEN 不代表 send 一定成功——缓冲区溢出、
    // 页面卸载竞态下浏览器会直接抛 InvalidStateError。原实现不 catch，
    // 异常会顺着心跳定时器（setInterval 回调）逸出成未捕获异常，
    // 并被业务调用方的 await 链当成"发送逻辑失败"以外的错误。
    try {
      socket.send(JSON.stringify(message))
      return true
    } catch (e) {
      logger.warn('[WS] send 失败:', e)
      return false
    }
  }
  logger.warn('[WS] Not connected, message not sent')
  return false
}

/**
 * 订阅指定类型的消息
 * @param {string} type - 消息类型，监听对应 type 的消息；使用 '*' 可监听所有消息
 * @param {Function} callback - 收到消息时的回调函数，接收消息 data 参数
 * @returns {Function} 取消订阅函数 — 调用后移除本次注册的回调
 */
export function on(type: string, callback: (data: ApiData) => void) {
  let set = listeners.get(type)
  if (!set) {
    set = new Set()
    listeners.set(type, set)
  }
  set.add(callback)
  // 返回取消订阅函数
  return () => {
    const set = listeners.get(type)
    if (set) {
      set.delete(callback)
    }
  }
}

/**
 * 取消订阅某类型的所有监听
 * @param {string} type 消息类型
 */
export function off(type: string) {
  listeners.delete(type)
}

// ===== 内部方法 =====

function handleMessage(msg: ApiData) {
  const { type, data } = msg

  // 广播给对应类型的所有监听者
  const set = listeners.get(type)
  if (set) {
    set.forEach((cb) => {
      try {
        cb(data)
      } catch (e) {
        // 单个订阅者抛错不得中断广播：其余订阅者仍需收到消息
        logger.error('[WS] Handler error:', e)
      }
    })
  }

  // 同时广播给 '*' 通配符监听者
  const allListeners = listeners.get('*')
  if (allListeners) {
    allListeners.forEach((cb) => {
      try {
        cb(msg)
      } catch (e) {
        logger.error('[WS] Handler error:', e)
      }
    })
  }
}

function startHeartbeat() {
  stopHeartbeat()
  heartbeatTimer = setInterval(() => {
    send({ type: 'ping', timestamp: Date.now() })
  }, 30000)
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer)
    heartbeatTimer = null
  }
}

let reconnectAttempts = 0
const MAX_RECONNECT_ATTEMPTS = 5

function scheduleReconnect() {
  if (manuallyClosed) return
  if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
    logger.log('[WS] Max reconnect attempts reached')
    return
  }

  clearReconnect()
  const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000)
  reconnectAttempts++

  logger.log(`[WS] Reconnecting in ${delay}ms (attempt ${reconnectAttempts})`)
  // 缺陷修复（P1-11①）：connect() 在握手失败时会 reject（onerror），
  // 原实现 `setTimeout(() => connect(), delay)` 从不 catch
  // → 每次重连失败都产生一个 **Unhandled Promise Rejection**，
  // 在浏览器控制台/监控里刷屏，且可能触发全局错误处理把用户踢下线。
  reconnectTimer = setTimeout(() => {
    if (manuallyClosed) return
    connect().catch((err) => {
      logger.warn('[WS] Reconnect failed:', err)
    })
  }, delay)
}

function clearReconnect() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

export { isConnected as isWebSocketConnected }
