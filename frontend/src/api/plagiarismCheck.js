import request from './request'

// 创建查重任务
export const createCheck = data => request.post('/plagiarism-checks', data)
// 执行查重
export const executeCheck = id => request.post(`/plagiarism-checks/${id}/execute`)
// 查重详情
export const getCheckDetail = id => request.get(`/plagiarism-checks/${id}`)
// 匹配列表
export const getCheckMatches = id => request.get(`/plagiarism-checks/${id}/matches`)
// 查重历史
export const getCheckHistory = studentId => request.get(`/plagiarism-checks/student/${studentId}`)

export default { createCheck, executeCheck, getCheckDetail, getCheckMatches, getCheckHistory }
