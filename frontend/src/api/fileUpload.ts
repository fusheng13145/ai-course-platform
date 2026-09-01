import request from './request'

// 上传会话列表（管理员监控）
export const listSessions = () => request.get('/upload-sessions')
// 创建上传会话
export const createSession = data => request.post('/upload-sessions', data)
// 上传分片
export const uploadChunk = (sessionId, index, formData) =>
  request.post(`/upload-sessions/${sessionId}/chunks/${index}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
// 合并分片
export const mergeChunks = sessionId => request.post(`/upload-sessions/${sessionId}/merge`)
// 上传进度
export const getProgress = sessionId => request.get(`/upload-sessions/${sessionId}/progress`)
// 取消上传
export const cancelUpload = sessionId => request.delete(`/upload-sessions/${sessionId}/cancel`)

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default { listSessions, createSession, uploadChunk, mergeChunks, getProgress, cancelUpload }
