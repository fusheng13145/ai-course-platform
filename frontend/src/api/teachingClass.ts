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

/** 教学班已导入的行政班来源 */
export const getRosterSources = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/teaching-classes/${id}/admin-classes`) as Promise<ApiEnvelope<ApiData>>

/** 教学班名单（含学生姓名/学号/状态） */
export const getTeachingClassMembers = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/teaching-classes/${id}/members/detail`) as Promise<ApiEnvelope<ApiData>>

/** 可导入本教学班的行政班候选 */
export const getRosterCandidates = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/teaching-classes/${id}/roster/candidates`) as Promise<ApiEnvelope<ApiData>>

/** 按行政班导入名单 */
export const importRoster = (
  id: number | string,
  adminClassId: number | string,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/teaching-classes/${id}/roster/import`, null, {
    params: { adminClassId },
  }) as Promise<ApiEnvelope<ApiData>>

/** 重同步名单；adminClassId 省略时同步全部来源 */
export const resyncRoster = (
  id: number | string,
  adminClassId?: number | string | null,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/teaching-classes/${id}/roster/resync`, null, {
    params: adminClassId ? { adminClassId } : undefined,
  }) as Promise<ApiEnvelope<ApiData>>
