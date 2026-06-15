/**
 * WebSocket 实时通信服务
 * 用于：实时通知、AI助手流式输出、在线状态同步
 */

let socket = null
let reconnectTimer = null
let heartbeatTimer = null
let listeners = new Map()
let isConnected = false

const WS_URL = `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}/ws`

/**
 * 连接 WebSocket
 * @param {string} token JWT Token
 */
export function connect(token) {
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
    return Promise.resolve()
  }

  const url = `${WS_URL}?token=${token}`

  return new Promise((resolve, reject) => {
    socket = new WebSocket(url)

    socket.onopen = () => {
      console.log('[WS] Connected')
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
        console.warn('[WS] Parse error:', e)
      }
    }

    socket.onerror = (err) => {
      console.error('[WS] Error:', err)
      reject(err)
    }

    socket.onclose = (event) => {
      console.log(`[WS] Closed: code=${event.code}`)
      isConnected = false
      stopHeartbeat()
      // 自动重连（最多5次）
      scheduleReconnect(token)
    }
  })
}

/**
 * 断开连接
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
 * @param {object} message 消息对象
 */
export function send(message) {
  if (socket && socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify(message))
    return true
  }
  console.warn('[WS] Not connected, message not sent')
  return false
}

/**
 * 订阅消息类型
 * @param {string} type 消息类型
 * @param {Function} callback 回调函数
 * @returns {Function} 取消订阅函数
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
    set.forEach(cb => {
      try { cb(data) } catch (e) { console.error('[WS] Handler error:', e) }
    })
  }

  // 同时广播给 '*' 通配符监听者
  const allListeners = listeners.get('*')
  if (allListeners) {
    allListeners.forEach(cb => {
      try { cb(msg) } catch (e) { console.error('[WS] Handler error:', e) }
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

function scheduleReconnect(token) {
  if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
    console.log('[WS] Max reconnect attempts reached')
    return
  }

  clearReconnect()
  const delay = Math.min(1000 * Math.pow(2, reconnectAttempts), 30000)
  reconnectAttempts++

  console.log(`[WS] Reconnecting in ${delay}ms (attempt ${reconnectAttempts})`)
  reconnectTimer = setTimeout(() => connect(token), delay)
}

function clearReconnect() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
}

export { isConnected as isWebSocketConnected }
