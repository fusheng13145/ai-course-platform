import request from './request'
export const getQuestionList = params => request.get('/questions', { params })
export const getQuestionById = id => request.get(`/questions/${id}`)
export const createQuestion = data => request.post('/questions', data)
export const updateQuestion = (id, data) => request.put(`/questions/${id}`, data)
export const deleteQuestion = id => request.delete(`/questions/${id}`)
export const generateByAI = data => request.post('/questions/ai-generate', data)
export default { getQuestionList, getQuestionById, createQuestion, updateQuestion, deleteQuestion, generateByAI }