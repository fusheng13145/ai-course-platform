import request from './request'

// 创建查重任务
export const createCheck = (data) => request.post('/plagiarism-checks', data)
// 执行查重
export const executeCheck = (id) => request.post(`/plagiarism-checks/${id}/execute`)
// 查重详情
export const getCheckDetail = (id) => request.get(`/plagiarism-checks/${id}`)
// 匹配列表
export const getCheckMatches = (id) => request.get(`/plagiarism-checks/${id}/matches`)
// 查重历史（传 studentId 查该生历史；不传则返回全部查重任务列表，供管理端使用）
export const getCheckHistory = (studentId) =>
  studentId
    ? request.get(`/plagiarism-checks/student/${studentId}`)
    : request.get('/plagiarism-checks')

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default { createCheck, executeCheck, getCheckDetail, getCheckMatches, getCheckHistory }
