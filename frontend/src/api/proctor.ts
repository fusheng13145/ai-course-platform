import request from './request'

// 在线监考（ProctorController，前缀 /proctor，教师/管理员只读视角）

// 监考事件列表
export const getSessionEvents = (id) => request.get(`/proctor/session/${id}/events`)
// 学生监考会话
export const getStudentSession = (examSessionId, studentId) =>
  request.get(`/proctor/exam/${examSessionId}/student/${studentId}/session`)
// 考试风险汇总 List<Map>
export const getExamRisk = (examSessionId) => request.get(`/proctor/exam/${examSessionId}/risk`)

// 风险判定阈值（教师/管理员可读）
export const getThresholds = () => request.get('/proctor/thresholds')
// 管理员更新阈值
export const updateThresholds = (data) => request.put('/proctor/thresholds', data)
// 单条违规事件（含复核状态）
export const getEvent = (eventId) => request.get(`/proctor/events/${eventId}`)
// 教师/管理员复核：确认违规(REVIEWED) / 豁免(EXEMPTED)
export const reviewEvent = (eventId, data) =>
  request.post(`/proctor/events/${eventId}/review`, data)

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
