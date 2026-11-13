import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'
/**
 * 导出函数 getNotificationList：获取/查询数据
 */
export const getNotificationList = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/notifications', { params }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getUnreadCount：获取/查询数据
 */
export const getUnreadCount = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/notifications/unread-count') as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 markAsRead：业务逻辑处理
 */
export const markAsRead = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.patch(`/notifications/${id}/read`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 markAllAsRead：业务逻辑处理
 */
export const markAllAsRead = (): Promise<ApiEnvelope<ApiData>> =>
  request.patch('/notifications/read-all') as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 deleteNotification：删除数据
 */
export const deleteNotification = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/notifications/${id}`) as Promise<ApiEnvelope<ApiData>>
// 以下导出由原 notificationEnhanced.js 合并而来
export const sendNotification = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/notifications/send', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getNotificationList,
  getUnreadCount,
  markAsRead,
  markAllAsRead,
  deleteNotification,
  sendNotification,
}
