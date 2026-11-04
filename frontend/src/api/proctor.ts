import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'

// 在线监考（ProctorController，前缀 /proctor，教师/管理员只读视角）

// 监考事件列表
export const getSessionEvents = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/proctor/session/${id}/events`) as Promise<ApiEnvelope<ApiData>>
// 学生监考会话
export const getStudentSession = (
  examSessionId: number | string,
  studentId: number | string,
): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/proctor/exam/${examSessionId}/student/${studentId}/session`) as Promise<
    ApiEnvelope<ApiData>
  >
// 考试风险汇总 List<Map>
export const getExamRisk = (examSessionId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/proctor/exam/${examSessionId}/risk`) as Promise<ApiEnvelope<ApiData>>

// 风险判定阈值（教师/管理员可读）
export const getThresholds = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/proctor/thresholds') as Promise<ApiEnvelope<ApiData>>
// 管理员更新阈值
export const updateThresholds = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.put('/proctor/thresholds', data) as Promise<ApiEnvelope<ApiData>>
// 单条违规事件（含复核状态）
export const getEvent = (eventId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/proctor/events/${eventId}`) as Promise<ApiEnvelope<ApiData>>
// 教师/管理员复核：确认违规(REVIEWED) / 豁免(EXEMPTED)
export const reviewEvent = (
  eventId: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/proctor/events/${eventId}/review`, data) as Promise<ApiEnvelope<ApiData>>

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getSessionEvents,
  getStudentSession,
  getExamRisk,
  getThresholds,
  updateThresholds,
  getEvent,
  reviewEvent,
}
