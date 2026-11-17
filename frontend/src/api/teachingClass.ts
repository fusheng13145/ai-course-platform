import request from './request'
import type { ApiData, ApiEnvelope } from '@/utils/response'

/** 课程下教学班（教师/管理员可读） */
export const getTeachingClasses = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/teaching-classes/course/${courseId}`) as Promise<ApiEnvelope<ApiData>>

/** 发布课程教学班：DRAFT → ACTIVE */
export const publishCourseClasses = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/teaching-classes/course/${courseId}/publish`) as Promise<ApiEnvelope<ApiData>>

/** 创建教学班 */
export const createTeachingClass = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/teaching-classes', data) as Promise<ApiEnvelope<ApiData>>

/** 指定主讲 */
export const assignLeadTeacher = (
  id: number | string,
  teacherId: number | string,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/teaching-classes/${id}/lead-teacher`, null, {
    params: { teacherId },
  }) as Promise<ApiEnvelope<ApiData>>

/** 修改教学班基础信息 */
export const updateTeachingClass = (
  id: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.patch(`/teaching-classes/${id}`, data) as Promise<ApiEnvelope<ApiData>>

/** 删除教学班 */
export const deleteTeachingClass = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/teaching-classes/${id}`) as Promise<ApiEnvelope<ApiData>>
