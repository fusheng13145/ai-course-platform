import request from './request'

// 视频观看会话 API
export const videoSessionApi = {
  startSession(data) { return request.post('/video-sessions/start', data) },
  endSession(id, data) { return request.post(`/video-sessions/${id}/end`, data) },
  getHistory() { return request.get('/video-sessions/history') },
}
