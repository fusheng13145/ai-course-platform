import request from './request'
export const getNotificationList = params => request.get('/notifications', { params })
export const getUnreadCount = () => request.get('/notifications/unread-count')
export const markAsRead = id => request.patch(`/notifications/${id}/read`)
export const markAllAsRead = () => request.patch('/notifications/read-all')
export const deleteNotification = id => request.delete(`/notifications/${id}`)
// 以下导出由原 notificationEnhanced.js 合并而来
export const sendNotification = data => request.post('/notifications/send', data)
export default { getNotificationList, getUnreadCount, markAsRead, markAllAsRead, deleteNotification, sendNotification }