import request from './request'
// ==================== 已废弃的旧版接口（请迁移到 v2 接口） ====================
// 计划于 2026.09 移除。替代方案：
//   aiChat       → qaAsk()
//   generateQuestion → questionRecommendations() + essayCorrect()
//   generateSummary  → essayCorrect({ type: 'summary' })
//   checkPlagiarism  → 独立查重 POST /submissions/{id}/check-plagiarism

/** @deprecated 请使用 qaAsk() 替代。将在 2026.09 移除。 */
export const aiChat = (message, context, courseId) =>
  request.post('/ai/chat', { message, context, courseId })
/** @deprecated 请使用 questionRecommendations() 替代。将在 2026.09 移除。 */
export const generateQuestion = (data) => request.post('/ai/generate-question', data)
/** @deprecated 请使用 essayCorrect() 替代。将在 2026.09 移除。 */
export const generateSummary = (content) => request.post('/ai/generate-summary', { content })
/** @deprecated 请使用独立查重 POST /submissions/{id}/check-plagiarism。将在 2026.09 移除。 */
export const checkPlagiarism = (text1, text2) =>
  request.post('/ai/check-plagiarism', { text1, text2 })

// ==================== v2（当前推荐接口） ====================
export const qaAsk = (data) => request.post('/ai/qa/ask', data)
export const qaContinue = (data) => request.post('/ai/qa/continue', data)
export const getSessions = () => request.get('/ai/qa/sessions')
export const essayCorrect = (data) => request.post('/ai/essay/correct', data)
export const essayPolish = (data) => request.post('/ai/essay/polish', data)
export const learningRecommendations = () => request.get('/ai/recommendations/learning')
export const questionRecommendations = (params) =>
  request.get('/ai/recommendations/questions', { params })
export const weakPointsAnalysis = (params) => request.get('/ai/analysis/weak-points', { params })
export const learningAdvice = (params) => request.get('/ai/advice/learning', { params })
export const generateLearningPath = (data) => request.post('/ai/learning-path/generate', data)

export default {
  aiChat,
  generateQuestion,
  generateSummary,
  checkPlagiarism,
  qaAsk,
  qaContinue,
  getSessions,
  essayCorrect,
  essayPolish,
  learningRecommendations,
  questionRecommendations,
  weakPointsAnalysis,
  learningAdvice,
  generateLearningPath,
}
