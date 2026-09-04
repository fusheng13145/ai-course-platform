import request from './request'

// 创建提醒
export const createReminder = (data) => request.post('/reminders', data)
// 待办列表
export const getTodos = () => request.get('/reminders/todos')
// 更新提醒（局部更新，对应后端 PUT /reminders/{id}）
export const updateReminder = (id, data) => request.put(`/reminders/${id}`, data)
// 取消提醒（对应后端 PATCH /reminders/{id}/cancel）
export const cancelReminder = (id) => request.patch(`/reminders/${id}/cancel`)
// 发送到期提醒
export const sendDue = () => request.post('/reminders/send-due')

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default { createReminder, getTodos, updateReminder, cancelReminder, sendDue }
