import request from './request'

/**
 * 定时提醒 API
 * 后端: ReminderController @RequestMapping("/reminders")
 */
export const reminderApi = {
  /** 获取提醒列表 */
  getList(params) { return request.get('/reminders', { params }) },
  /** 创建提醒 */
  create(data) { return request.post('/reminders', data) },
  /** 更新提醒 */
  update(id, data) { return request.put(`/reminders/${id}`, data) },
  /** 删除提醒 */
  delete(id) { return request.delete(`/reminders/${id}`) },
  /** 标记已读 */
  markRead(id) { return request.patch(`/reminders/${id}/read`) },
  /** 全部标已读 */
  markAllRead() { return request.patch('/reminders/read-all') },
  /** 忽略/关闭提醒 */
  dismiss(id) { return request.post(`/reminders/${id}/dismiss`) },
  /** 获取即将到来的提醒 */
  getUpcoming() { return request.get('/reminders/upcoming') },
  /** 获取过期未处理的提醒 */
  getOverdue() { return request.get('/reminders/overdue') },
}

export default reminderApi
