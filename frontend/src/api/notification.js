import request from './request'
export const getNotificationList = params => request.get('/notifications', { params })
export const markAsRead = id => request.patch(`/notifications/${id}/read`)
export const markAllAsRead = () => request.patch('/notifications/read-all')
export const deleteNotification = id => request.delete(`/notifications/${id}`)
export default { getNotificationList, markAsRead, markAllAsRead, deleteNotification }