import request from './request'
// 教师端题库管理（需求文档定义: /api/teacher/questions）
export const getQuestionList = params => request.get('/teacher/questions', { params })
export const getQuestionById = id => request.get(`/teacher/questions/${id}`)
export const createQuestion = data => request.post('/teacher/questions', data)
export const updateQuestion = (id, data) => request.put(`/teacher/questions/${id}`, data)
export const deleteQuestion = id => request.delete(`/teacher/questions/${id}`)
export const importQuestions = data => request.post('/teacher/questions/import', data)
export const exportQuestions = params => request.get('/teacher/questions/export', { params })

// 题目增强 API（/questions 路径，属于公共题库增强功能）
export const generateByAI = data => request.post('/questions/ai-generate', data)
export const createQuestionEnhanced = data => request.post('/questions/create', data)
export const generateByAIEnhanced = data => request.post('/questions/generate-by-ai', data)
export const reviewQuestion = (id, data) => request.post(`/questions/${id}/review`, data)
export const copyQuestion = id => request.post(`/questions/${id}/copy`)
export const searchQuestions = params => request.get('/questions/search', { params })
export const randomSelect = params => request.get('/questions/random', { params })
export const getByKnowledgePoint = kpId => request.get(`/questions/kp/${kpId}`)
export const getQuestionStats = id => request.get(`/questions/${id}/stats`)
export const getErrorProne = params => request.get('/questions/error-prone', { params })

export default { getQuestionList, getQuestionById, createQuestion, updateQuestion, deleteQuestion, importQuestions, exportQuestions, generateByAI, createQuestionEnhanced, generateByAIEnhanced, reviewQuestion, copyQuestion, searchQuestions, randomSelect, getByKnowledgePoint, getQuestionStats, getErrorProne }