import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'

// 创建提醒
export const createReminder = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/reminders', data) as Promise<ApiEnvelope<ApiData>>
// 待办列表
export const getTodos = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/reminders/todos') as Promise<ApiEnvelope<ApiData>>
// 更新提醒（局部更新，对应后端 PUT /reminders/{id}）
export const updateReminder = (
  id: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/reminders/${id}`, data) as Promise<ApiEnvelope<ApiData>>
// 取消提醒（对应后端 PATCH /reminders/{id}/cancel）
export const cancelReminder = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.patch(`/reminders/${id}/cancel`) as Promise<ApiEnvelope<ApiData>>
// 发送到期提醒
export const sendDue = (): Promise<ApiEnvelope<ApiData>> =>
  request.post('/reminders/send-due') as Promise<ApiEnvelope<ApiData>>

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default { createReminder, getTodos, updateReminder, cancelReminder, sendDue }
