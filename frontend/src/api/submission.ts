import request from './request'
import { fetchAllPages, type ApiEnvelope, type ApiData } from '@/utils/response'
/**
 * 导出函数 submitTask：发起请求/调用
 */
export const submitTask = (
  taskId: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post('/submissions', { ...data, taskId }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getMySubmissions：获取/查询数据
 */
export const getMySubmissions = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/submissions', { params }) as Promise<ApiEnvelope<ApiData>>
/** 读取当前学生的全部提交（跨任务分组展示成绩/评语用，避免分页截断漏行）。 */
export const getAllMySubmissions = (filters: Record<string, unknown> = {}): Promise<ApiData[]> =>
  fetchAllPages((page: number, size: number) =>
    getMySubmissions({ ...filters, page, size }),
  ) as Promise<ApiData[]>
/**
 * 导出函数 getTaskSubmissions：获取/查询数据
 */
export const getTaskSubmissions = (
  taskId: number | string,
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/submissions/task/${taskId}`, { params }) as Promise<ApiEnvelope<ApiData>>
/** 获取任务的全部提交，供教师批改列表与课程完成度统计使用。 */
export const getAllTaskSubmissions = (
  taskId: number | string,
  filters: Record<string, unknown> = {},
): Promise<ApiData[]> =>
  fetchAllPages((page: number, size: number) =>
    getTaskSubmissions(taskId, { ...filters, page, size }),
  ) as Promise<ApiData[]>
/**
 * 导出函数 gradeSubmission：业务逻辑处理
 */
export const gradeSubmission = (
  id: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/submissions/${id}/grade`, data) as Promise<ApiEnvelope<ApiData>>

// 作业提交增强 API
export const resubmit = (
  id: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/submissions/${id}/resubmit`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 returnSubmission：业务逻辑处理
 */
export const returnSubmission = (
  id: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/submissions/${id}/return`, data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 aiGrade：业务逻辑处理
 */
export const aiGrade = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/submissions/${id}/ai-grade`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getPlagiarismResult：获取/查询数据
 */
export const getPlagiarismResult = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/submissions/${id}/plagiarism`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 checkPlagiarism：业务逻辑处理
 */
export const checkPlagiarism = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/submissions/${id}/check-plagiarism`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 recordView：业务逻辑处理
 */
export const recordView = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/submissions/${id}/view`) as Promise<ApiEnvelope<ApiData>>

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  submitTask,
  getMySubmissions,
  getAllMySubmissions,
  getTaskSubmissions,
  getAllTaskSubmissions,
  gradeSubmission,
  resubmit,
  returnSubmission,
  aiGrade,
  getPlagiarismResult,
  checkPlagiarism,
  recordView,
}
