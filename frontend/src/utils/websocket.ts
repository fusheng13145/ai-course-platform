import logger from '@/utils/logger'
/**
 * WebSocket 实时通信服务
 * 用于：实时通知、AI助手流式输出、在线状态同步
 */

let socket = null
let reconnectTimer = null
let heartbeatTimer = null
const listeners = new Map()
let isConnected = false

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
      try {
        const msg = JSON.parse(event.data)
        handleMessage(msg)
      } catch (e) {
        console.error('[websocket.js] catch:', e)
        logger.warn('[WS] Parse error:', e)
      }
    }

    socket.onerror = (err) => {
      logger.error('[WS] Error:', err)
      reject(err)
    }

    socket.onclose = (event) => {
      logger.log(`[WS] Closed: code=${event.code}`)
      isConnected = false
      stopHeartbeat()
      // 自动重连（最多5次）
      scheduleReconnect()
    }
  })
}

/**
 * 断开 WebSocket 连接
 * 清除重连定时器和心跳检测，主动关闭 socket 连接
 */
export function disconnect() {
  clearReconnect()
  stopHeartbeat()
  if (socket) {
    socket.close(1000, 'Client disconnect')
    socket = null
  }
  isConnected = false
}

/**
 * 发送消息
 * @param {object} message - 要发送的消息对象（会自动 JSON 序列化）
 * @returns {boolean} 是否发送成功（false 表示连接未建立）
 */
export function send(message) {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify(message))
    return true
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
export function on(type, callback) {
  if (!listeners.has(type)) {
    listeners.set(type, new Set())
  }
  listeners.get(type).add(callback)
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
export function off(type) {
  listeners.delete(type)
}

// ===== 内部方法 =====

function handleMessage(msg) {
  const { type, data } = msg

  // 广播给对应类型的所有监听者
  const set = listeners.get(type)
  if (set) {
    set.forEach((cb) => {
      try {
        cb(data)
      } catch (e) {
        console.error('[websocket.js] catch:', e)
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
        console.error('[websocket.js] catch:', e)
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
  if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
    logger.log('[WS] Max reconnect attempts reached')
    return
  }

  clearReconnect()
  const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000)
  reconnectAttempts++

  logger.log(`[WS] Reconnecting in ${delay}ms (attempt ${reconnectAttempts})`)
  reconnectTimer = setTimeout(() => connect(), delay)
}

function clearReconnect() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

export { isConnected as isWebSocketConnected }
