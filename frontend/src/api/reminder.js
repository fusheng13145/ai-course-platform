import request from './request'

// 创建提醒
export const createReminder = data => request.post('/reminders', data)
// 待办列表
export const getTodos = () => request.get('/reminders/todos')
// 更新提醒
export const updateReminder = (id, data) => request.put(`/reminders/${id}`, data)
// 批量触发
export const batchTrigger = data => request.post('/reminders/batch-trigger', data)
// 发送到期提醒
export const sendDue = () => request.post('/reminders/send-due')

export default { createReminder, getTodos, updateReminder, batchTrigger, sendDue }
