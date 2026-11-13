import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'

// 过程性成绩（GradeController，前缀 /grade）

// 保存成绩方案 body: GradePolicy { courseId, name, components:[{key,name,weight,source}] }
export const saveGradePolicy = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/grade/policy', data) as Promise<ApiEnvelope<ApiData>>
// 获取成绩方案
export const getGradePolicy = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/grade/policy/course/${courseId}`) as Promise<ApiEnvelope<ApiData>>
// 录入/更新成绩项 body: GradeItem { policyId, studentId, componentKey, score, rawScore?, sourceId?, remark? }
export const saveGradeItem = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/grade/item', data) as Promise<ApiEnvelope<ApiData>>
// 计算总评 Map（computeFinal）
export const computeFinal = (
  policyId: number | string,
  studentId: number | string,
): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/grade/final/${policyId}/student/${studentId}`) as Promise<ApiEnvelope<ApiData>>
// 课程成绩报告 List<Map>（courseReport）
export const getCourseReport = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/grade/report/course/${courseId}`) as Promise<ApiEnvelope<ApiData>>

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  saveGradePolicy,
  getGradePolicy,
  saveGradeItem,
  computeFinal,
  getCourseReport,
}
