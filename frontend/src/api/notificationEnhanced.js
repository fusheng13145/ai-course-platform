import request from './request'

// 通知增强 API
export const notificationEnhancedApi = {
  getUnreadCount() { return request.get('/notifications/unread-count') },
  sendNotification(data) { return request.post('/notifications/send', data) },
}

export default notificationEnhancedApi
