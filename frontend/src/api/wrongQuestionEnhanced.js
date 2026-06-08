import request from './request'

// 错题本增强 API
export const wrongQuestionEnhancedApi = {
  getUnmastered(studentId, courseId) { return request.get('/wrong-questions/unmastered', { params: { studentId, courseId } }) },
  getByKnowledgePoints(studentId, courseId) { return request.get('/wrong-questions/by-kp', { params: { studentId, courseId } }) },
  generatePracticePaper(data) { return request.post('/wrong-questions/practice-paper', data) },
  getRelated(questionId) { return request.get(`/wrong-questions/${questionId}/related`) },
}
