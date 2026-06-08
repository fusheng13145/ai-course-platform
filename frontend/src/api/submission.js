import request from './request'
export const submitTask = (taskId, data) => request.post('/submissions', { ...data, taskId })
export const getMySubmissions = params => request.get('/submissions', { params })
export const getTaskSubmissions = (taskId, params) => request.get(`/submissions/task/${taskId}`, { params })
export const gradeSubmission = (id, data) => request.post(`/submissions/${id}/grade`, data)

// 作业提交增强 API
export const resubmit = (id, data) => request.post(`/submissions/${id}/resubmit`, data)
export const returnSubmission = (id, data) => request.post(`/submissions/${id}/return`, data)
export const aiGrade = id => request.post(`/submissions/${id}/ai-grade`)
export const getPlagiarismResult = id => request.get(`/submissions/${id}/plagiarism`)
export const checkPlagiarism = id => request.post(`/submissions/${id}/check-plagiarism`)
export const recordView = id => request.post(`/submissions/${id}/view`)

export default { submitTask, getMySubmissions, getTaskSubmissions, gradeSubmission, resubmit, returnSubmission, aiGrade, getPlagiarismResult, checkPlagiarism, recordView }
