import request from './request'
export const getLearningRecords = params => request.get('/learning-records', { params })
export const createLearningRecord = data => request.post('/learning-records', data)
export default { getLearningRecords, createLearningRecord }