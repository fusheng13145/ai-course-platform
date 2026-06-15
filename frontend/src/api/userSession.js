import request from './request'

/**
 * 用户会话管理 API
 * 后端: UserSessionController @RequestMapping("/user-sessions")
 */
export const userSessionApi = {
  /** 获取在线用户列表 */
  getOnlineUsers() { return request.get('/user-sessions/online') },
  /** 获取某用户的会话信息 */
  getUserSessions(userId) { return request.get(`/user-sessions/user/${userId}`) },
  /** 强制指定会话下线 */
  forceLogout(sessionId) { return request.delete(`/user-sessions/${sessionId}/force-logout`) },
  /** 获取会话统计数据 */
  getStats() { return request.get('/user-sessions/stats') },
  /** 异常会话检测 */
  getAbnormal(userId) { return request.get(`/user-sessions/abnormal/${userId}`) },
}

export default userSessionApi
