import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'
/**
 * 导出函数 enrollCourse：业务逻辑处理
 */
export const enrollCourse = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/enrollments', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 unenrollCourse：业务逻辑处理
 */
export const unenrollCourse = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/enrollments/${id}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getMyEnrollments：获取/查询数据
 */
export const getMyEnrollments = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/enrollments/my') as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 approveEnrollment：业务逻辑处理
 */
export const approveEnrollment = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/enrollments/${id}/approve`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 rejectEnrollment：业务逻辑处理
 */
export const rejectEnrollment = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/enrollments/${id}/reject`) as Promise<ApiEnvelope<ApiData>>
/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  enrollCourse,
  unenrollCourse,
  getMyEnrollments,
  approveEnrollment,
  rejectEnrollment,
}
