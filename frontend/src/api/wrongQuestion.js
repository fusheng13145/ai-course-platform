import request from './request'
export const getWrongQuestions = params => request.get('/wrong-questions', { params })
export const addWrongQuestion = data => request.post('/wrong-questions', data)
export const deleteWrongQuestion = id => request.delete(`/wrong-questions/${id}`)
export const markAsReviewed = id => request.put(`/wrong-questions/${id}/reviewed`)
export const getWrongQuestionStats = () => request.get('/wrong-questions/stats')
export default { getWrongQuestions, addWrongQuestion, deleteWrongQuestion, markAsReviewed, getWrongQuestionStats }