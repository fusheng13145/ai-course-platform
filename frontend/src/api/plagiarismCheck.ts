import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'

// 创建查重任务
export const createCheck = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/plagiarism-checks', data) as Promise<ApiEnvelope<ApiData>>
// 执行查重
export const executeCheck = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/plagiarism-checks/${id}/execute`) as Promise<ApiEnvelope<ApiData>>
// 查重详情
export const getCheckDetail = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/plagiarism-checks/${id}`) as Promise<ApiEnvelope<ApiData>>
// 匹配列表
export const getCheckMatches = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/plagiarism-checks/${id}/matches`) as Promise<ApiEnvelope<ApiData>>
// 查重历史（传 studentId 查该生历史；不传则返回全部查重任务列表，供管理端使用）
export const getCheckHistory = (studentId: number | string): Promise<ApiEnvelope<ApiData>> =>
  (studentId
    ? request.get(`/plagiarism-checks/student/${studentId}`)
    : request.get('/plagiarism-checks')) as Promise<ApiEnvelope<ApiData>>

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default { createCheck, executeCheck, getCheckDetail, getCheckMatches, getCheckHistory }
