import request from './request'
export const aiChat = (message, context, courseId) => request.post('/ai/chat', { message, context, courseId })
export const generateQuestion = data => request.post('/ai/generate/question', data)
export const generateContent = (type, params) => request.post('/ai/generate/content', { type, params })
export const generateSummary = content => request.post('/ai/summary', { content })
export const checkPlagiarism = (text1, text2) => request.post('/ai/plagiarism', { text1, text2 })
export default { aiChat, generateQuestion, generateContent, generateSummary, checkPlagiarism }