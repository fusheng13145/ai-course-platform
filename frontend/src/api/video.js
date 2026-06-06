import request from './request'
export const getVideoRecords = params => request.get('/video-records', { params })
export const updateProgress = (id, progress) => request.put(`/video-records/${id}/progress`, { progress })
export const completeVideo = id => request.post(`/video-records/${id}/complete`)
export default { getVideoRecords, updateProgress, completeVideo }