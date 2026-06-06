import request from './request'
export const submitTask = (taskId, data) => request.post(`/tasks/${taskId}/submissions`, data)
export const getMySubmissions = params => request.get('/submissions/my', { params })
export const getTaskSubmissions = (taskId, params) => request.get(`/tasks/${taskId}/submissions`, { params })
export const gradeSubmission = (id, data) => request.post(`/submissions/${id}/grade`, data)
export default { submitTask, getMySubmissions, getTaskSubmissions, gradeSubmission }