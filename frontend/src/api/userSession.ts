import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'

// 在线用户列表
export const getOnlineUsers = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/user-sessions/online') as Promise<ApiEnvelope<ApiData>>
// 用户会话历史
export const getUserSessions = (userId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/user-sessions/user/${userId}`) as Promise<ApiEnvelope<ApiData>>
// 强制下线
export const forceLogout = (sessionId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/user-sessions/${sessionId}/force-logout`) as Promise<ApiEnvelope<ApiData>>
// 会话统计
export const getSessionStats = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/user-sessions/stats') as Promise<ApiEnvelope<ApiData>>
// 异常会话检测
export const getAbnormalSessions = (userId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/user-sessions/abnormal/${userId}`) as Promise<ApiEnvelope<ApiData>>

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getOnlineUsers,
  getUserSessions,
  forceLogout,
  getSessionStats,
  getAbnormalSessions,
}
