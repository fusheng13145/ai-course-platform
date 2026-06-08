import request from './request'
export const createLearningRecord = data => request.post('/learning-records', data)
export const getMyLearningRecords = () => request.get('/learning-records')
export default { createLearningRecord, getMyLearningRecords }