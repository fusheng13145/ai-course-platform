import request from './request'
export const submitTask = (taskId, data) => request.post('/submissions', { ...data, taskId })
export const getMySubmissions = params => request.get('/submissions', { params })
export const getTaskSubmissions = (taskId, params) => request.get(`/submissions/task/${taskId}`, { params })
export const gradeSubmission = (id, data) => request.post(`/submissions/${id}/grade`, data)
export default { submitTask, getMySubmissions, getTaskSubmissions, gradeSubmission }
