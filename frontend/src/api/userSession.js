import request from './request'

// 在线用户列表
export const getOnlineUsers = () => request.get('/user-sessions/online')
// 用户会话历史
export const getUserSessions = userId => request.get(`/user-sessions/user/${userId}`)
// 强制下线
export const forceLogout = sessionId => request.delete(`/user-sessions/${sessionId}/force-logout`)
// 会话统计
export const getSessionStats = () => request.get('/user-sessions/stats')
// 异常会话检测
export const getAbnormalSessions = userId => request.get(`/user-sessions/abnormal/${userId}`)

export default { getOnlineUsers, getUserSessions, forceLogout, getSessionStats, getAbnormalSessions }
