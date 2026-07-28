import request from './request'

// 过程性成绩（GradeController，前缀 /grade）

// 保存成绩方案 body: GradePolicy { courseId, name, components:[{key,name,weight,source}] }
export const saveGradePolicy = (data) => request.post('/grade/policy', data)
// 获取成绩方案
export const getGradePolicy = (courseId) => request.get(`/grade/policy/course/${courseId}`)
// 录入/更新成绩项 body: GradeItem { policyId, studentId, componentKey, score, rawScore?, sourceId?, remark? }
export const saveGradeItem = (data) => request.post('/grade/item', data)
// 计算总评 Map（computeFinal）
export const computeFinal = (policyId, studentId) =>
  request.get(`/grade/final/${policyId}/student/${studentId}`)
// 课程成绩报告 List<Map>（courseReport）
export const getCourseReport = (courseId) => request.get(`/grade/report/course/${courseId}`)

export default {
  saveGradePolicy,
  getGradePolicy,
  saveGradeItem,
  computeFinal,
  getCourseReport,
}
