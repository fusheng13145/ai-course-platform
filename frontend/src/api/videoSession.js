import request from './request'

// 视频观看会话 API — 对接后端 LearningRecordController (/learning-records)
export const videoSessionApi = {
  // 开始学习会话（对应 learning-record 创建）
  startSession(data) { return request.post('/learning-records', data) },
  // 结束会话（更新学习时长和完成状态）
  endSession(id, data) { return request.put(`/learning-records/${id}`, data) },
  // 历史学习会话列表
  getHistory(params) { return request.get('/learning-records', { params }) },
}

export default videoSessionApi
