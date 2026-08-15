import request from './request'

// 错题本 API（后端: WrongQuestionController /wrong-questions）
export const getWrongQuestions = (params) => request.get('/wrong-questions', { params })
/**
 * 导出函数 addWrongQuestion：创建/新增数据
 */
export const addWrongQuestion = (data) => request.post('/wrong-questions', data)
/**
 * 导出函数 deleteWrongQuestion：删除数据
 */
export const deleteWrongQuestion = (id) => request.delete(`/wrong-questions/${id}`)
/**
 * 导出函数 markMastered：业务逻辑处理
 */
export const markMastered = (id) => request.patch(`/wrong-questions/${id}/mastered`)
/**
 * 导出函数 unmarkMastered：业务逻辑处理
 */
export const unmarkMastered = (id) => request.patch(`/wrong-questions/${id}/unmaster`)
/**
 * 导出函数 getWrongQuestionStats：获取/查询数据
 */
export const getWrongQuestionStats = () => request.get('/wrong-questions/stats')
/**
 * 导出函数 getUnmastered：获取/查询数据
 */
export const getUnmastered = (studentId, courseId) =>
  request.get('/wrong-questions/unmastered', { params: { studentId, courseId } })
/**
 * 导出函数 getByKnowledgePoints：获取/查询数据
 */
export const getByKnowledgePoints = (studentId, courseId) =>
  request.get('/wrong-questions/by-kp', { params: { studentId, courseId } })
/**
 * 导出函数 generatePracticePaper：业务逻辑处理
 */
export const generatePracticePaper = (data) => request.post('/wrong-questions/practice-paper', data)
/**
 * 导出函数 getRelatedQuestions：获取/查询数据
 */
export const getRelatedQuestions = (questionId) =>
  request.get(`/wrong-questions/${questionId}/related`)
// 兼容别名：与 getRelatedQuestions 指向同一端点
export const getRelated = (questionId) => getRelatedQuestions(questionId)

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getWrongQuestions,
  addWrongQuestion,
  deleteWrongQuestion,
  markMastered,
  unmarkMastered,
  getWrongQuestionStats,
  getUnmastered,
  getByKnowledgePoints,
  generatePracticePaper,
  getRelatedQuestions,
  getRelated,
}
