import request from './request'
export const getWrongQuestions = params => request.get('/wrong-questions', { params })
export const markAsReviewed = id => request.put(`/wrong-questions/${id}/reviewed`)
export const getWrongQuestionStats = () => request.get('/wrong-questions/stats')
export default { getWrongQuestions, markAsReviewed, getWrongQuestionStats }