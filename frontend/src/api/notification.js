import request from './request'
/**
 * 导出函数 getNotificationList：获取/查询数据
 */
export const getNotificationList = params => request.get('/notifications', { params })
/**
 * 导出函数 getUnreadCount：获取/查询数据
 */
export const getUnreadCount = () => request.get('/notifications/unread-count')
/**
 * 导出函数 markAsRead：业务逻辑处理
 */
export const markAsRead = id => request.patch(`/notifications/${id}/read`)
/**
 * 导出函数 markAllAsRead：业务逻辑处理
 */
export const markAllAsRead = () => request.patch('/notifications/read-all')
/**
 * 导出函数 deleteNotification：删除数据
 */
export const deleteNotification = id => request.delete(`/notifications/${id}`)
// 以下导出由原 notificationEnhanced.js 合并而来
export const sendNotification = data => request.post('/notifications/send', data)
/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default { getNotificationList, getUnreadCount, markAsRead, markAllAsRead, deleteNotification, sendNotification }