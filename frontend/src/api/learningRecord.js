import request from './request'

// 学习记录基础 API
export const createLearningRecord = data => request.post('/learning-records', data)
export const getMyLearningRecords = () => request.get('/learning-records')

// 学习记录增强 API
export const getLearningStats = () => request.get('/learning-records/stats')
export const getHeatmap = params => request.get('/learning-records/calendar', { params })

export default { createLearningRecord, getMyLearningRecords, getLearningStats, getHeatmap }