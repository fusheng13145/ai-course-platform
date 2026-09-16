import request from './request'

// 上传会话列表（管理员监控）
export const listSessions = () => request.get('/upload-sessions')
// 创建上传会话
export const createSession = (data) => request.post('/upload-sessions', data)
// 上传进度
export const getProgress = (sessionId) => request.get(`/upload-sessions/${sessionId}/progress`)
// 取消上传
export const cancelUpload = (sessionId) => request.delete(`/upload-sessions/${sessionId}/cancel`)

/**
 * 分片上传与合并端点（uploadChunk / mergeChunks）已按上线前全检结论（N-03）下线：
 * 后端从未实现分片字节落盘与合并逻辑，属空壳，调用将返回 501。
 * 前端同步移除对应导出，避免向已禁用端点发起请求。
 *
 * 默认导出对象：模块/组件配置与导出内容
 */
export default { listSessions, createSession, getProgress, cancelUpload }
