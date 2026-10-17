import request from './request'

// 上传会话列表（管理员监控）
export const listSessions = () => request.get('/upload-sessions')
// 创建上传会话
export const createSession = (data) => request.post('/upload-sessions', data)
/**
 * 上传单个分片。index 为 0 基序号，须小于会话声明的 totalChunks；
 * formData 需含字段 file（分片字节），可另带 md5 字段由服务端实算比对。
 * 同一序号重传覆盖旧片，不会重复推进进度。
 */
export const uploadChunk = (sessionId, index, formData) =>
  request.post(`/upload-sessions/${sessionId}/chunks/${index}`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
/** 合并该会话已落盘的全部分片；缺片时后端返回 400 并列出缺失索引，不产出半成品 */
export const mergeChunks = (sessionId) => request.post(`/upload-sessions/${sessionId}/merge`)
// 上传进度（uploaded/total/percentage/status/missingIndexes，均取自服务端磁盘真值）
export const getProgress = (sessionId) => request.get(`/upload-sessions/${sessionId}/progress`)
// 取消上传
export const cancelUpload = (sessionId) => request.delete(`/upload-sessions/${sessionId}/cancel`)

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  listSessions,
  createSession,
  uploadChunk,
  mergeChunks,
  getProgress,
  cancelUpload,
}
