import request from './request'

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

export default { createSession, uploadChunk, mergeChunks, getProgress, cancelUpload }
