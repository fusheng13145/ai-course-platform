import request from './request'
export const getQuestionList = params => request.get('/questions', { params })
export const getQuestionById = id => request.get(`/questions/${id}`)
export const createQuestion = data => request.post('/questions', data)
export const updateQuestion = (id, data) => request.put(`/questions/${id}`, data)
export const deleteQuestion = id => request.delete(`/questions/${id}`)
export const generateByAI = data => request.post('/questions/ai-generate', data)

// 题目增强 API
export const createQuestionEnhanced = data => request.post('/questions/create', data)
export const generateByAIEnhanced = data => request.post('/questions/generate-by-ai', data)
export const reviewQuestion = (id, data) => request.post(`/questions/${id}/review`, data)
export const copyQuestion = id => request.post(`/questions/${id}/copy`)
export const searchQuestions = params => request.get('/questions/search', { params })
export const randomSelect = params => request.get('/questions/random', { params })
export const getByKnowledgePoint = kpId => request.get(`/questions/kp/${kpId}`)
export const getQuestionStats = id => request.get(`/questions/${id}/stats`)
export const getErrorProne = params => request.get('/questions/error-prone', { params })

export default { getQuestionList, getQuestionById, createQuestion, updateQuestion, deleteQuestion, generateByAI, createQuestionEnhanced, generateByAIEnhanced, reviewQuestion, copyQuestion, searchQuestions, randomSelect, getByKnowledgePoint, getQuestionStats, getErrorProne }