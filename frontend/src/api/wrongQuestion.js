import request from './request'

// 错题本 API（后端: WrongQuestionController /wrong-questions）
export const getWrongQuestions = (params) => request.get('/wrong-questions', { params })
export const addWrongQuestion = (data) => request.post('/wrong-questions', data)
export const deleteWrongQuestion = (id) => request.delete(`/wrong-questions/${id}`)
export const markMastered = (id) => request.patch(`/wrong-questions/${id}/mastered`)
export const unmarkMastered = (id) => request.patch(`/wrong-questions/${id}/unmaster`)
export const getWrongQuestionStats = () => request.get('/wrong-questions/stats')
export const getUnmastered = (studentId, courseId) =>
  request.get('/wrong-questions/unmastered', { params: { studentId, courseId } })
export const getByKnowledgePoints = (studentId, courseId) =>
  request.get('/wrong-questions/by-kp', { params: { studentId, courseId } })
export const generatePracticePaper = (data) => request.post('/wrong-questions/practice-paper', data)
export const getRelatedQuestions = (questionId) =>
  request.get(`/wrong-questions/${questionId}/related`)
// 兼容别名：与 getRelatedQuestions 指向同一端点
export const getRelated = (questionId) => getRelatedQuestions(questionId)

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
