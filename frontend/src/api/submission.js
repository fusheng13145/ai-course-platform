import request from './request'
/**
 * 导出函数 submitTask：发起请求/调用
 */
export const submitTask = (taskId, data) => request.post('/submissions', { ...data, taskId })
/**
 * 导出函数 getMySubmissions：获取/查询数据
 */
export const getMySubmissions = params => request.get('/submissions', { params })
/**
 * 导出函数 getTaskSubmissions：获取/查询数据
 */
export const getTaskSubmissions = (taskId, params) => request.get(`/submissions/task/${taskId}`, { params })
/**
 * 导出函数 gradeSubmission：业务逻辑处理
 */
export const gradeSubmission = (id, data) => request.post(`/submissions/${id}/grade`, data)

// 作业提交增强 API
export const resubmit = (id, data) => request.post(`/submissions/${id}/resubmit`, data)
/**
 * 导出函数 returnSubmission：业务逻辑处理
 */
export const returnSubmission = (id, data) => request.post(`/submissions/${id}/return`, data)
/**
 * 导出函数 aiGrade：业务逻辑处理
 */
export const aiGrade = id => request.post(`/submissions/${id}/ai-grade`)
/**
 * 导出函数 getPlagiarismResult：获取/查询数据
 */
export const getPlagiarismResult = id => request.get(`/submissions/${id}/plagiarism`)
/**
 * 导出函数 checkPlagiarism：业务逻辑处理
 */
export const checkPlagiarism = id => request.post(`/submissions/${id}/check-plagiarism`)
/**
 * 导出函数 recordView：业务逻辑处理
 */
export const recordView = id => request.post(`/submissions/${id}/view`)

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default { submitTask, getMySubmissions, getTaskSubmissions, gradeSubmission, resubmit, returnSubmission, aiGrade, getPlagiarismResult, checkPlagiarism, recordView }
