import request from './request'

// 学习记录基础 API
export const createLearningRecord = data => request.post('/learning-records', data)
export const getMyLearningRecords = () => request.get('/learning-records')

// 学习记录增强 API
export const updateProgress = data => request.put('/learning-records/progress', data)
export const getCourseProgress = courseId => request.get(`/learning-records/course/${courseId}`)
export const getLearningStats = () => request.get('/learning-records/stats')
export const getHeatmap = params => request.get('/learning-records/heatmap', { params })
export const getChapterProgress = courseId => request.get(`/learning-records/course/${courseId}/chapters`)

export default { createLearningRecord, getMyLearningRecords, updateProgress, getCourseProgress, getLearningStats, getHeatmap, getChapterProgress }