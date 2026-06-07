import request from './request'
export const aiChat = (message, context, courseId) => request.post('/ai/chat', { message, context, courseId })
export const generateQuestion = data => request.post('/ai/generate-question', data)
export const generateSummary = content => request.post('/ai/generate-summary', { content })
export const checkPlagiarism = (text1, text2) => request.post('/ai/check-plagiarism', { text1, text2 })
export const ragAsk = (question, courseId) => request.get('/ai/rag/ask', { params: { question, courseId } })
export default { aiChat, generateQuestion, generateSummary, checkPlagiarism, ragAsk }