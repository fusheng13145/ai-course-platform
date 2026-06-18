import request from './request'
export const aiChat = (message, context, courseId) => request.post('/ai/chat', { message, context, courseId })
export const generateQuestion = data => request.post('/ai/generate-question', data)
export const generateSummary = content => request.post('/ai/generate-summary', { content })
export const checkPlagiarism = (text1, text2) => request.post('/ai/check-plagiarism', { text1, text2 })
export const ragAsk = (question, courseId) => request.get('/ai/rag/ask', { params: { question, courseId } })

// AI增强接口（v2，需求文档定义）
export const qaAsk = data => request.post('/ai/qa/ask', data)
export const qaContinue = data => request.post('/ai/qa/continue', data)
export const getSessions = () => request.get('/ai/qa/sessions')
export const essayCorrect = data => request.post('/ai/essay/correct', data)
export const essayPolish = data => request.post('/ai/essay/polish', data)
export const learningRecommendations = () => request.get('/ai/recommendations/learning')
export const questionRecommendations = params => request.get('/ai/recommendations/questions', { params })
export const weakPointsAnalysis = params => request.get('/ai/analysis/weak-points', { params })
export const generateLearningPath = data => request.post('/ai/learning-path/generate', data)

export default {
  aiChat, generateQuestion, generateSummary, checkPlagiarism, ragAsk,
  qaAsk, qaContinue, getSessions, essayCorrect, essayPolish,
  learningRecommendations, questionRecommendations, weakPointsAnalysis, generateLearningPath
}