import request from './request'
export const getExamPaperList = params => request.get('/exam-papers', { params })
export const getExamPaperById = id => request.get(`/exam-papers/${id}`)
export const createExamPaper = data => request.post('/exam-papers', data)
export const updateExamPaper = (id, data) => request.put(`/exam-papers/${id}`, data)
export const deleteExamPaper = id => request.delete(`/exam-papers/${id}`)
export const getExamQuestions = id => request.get(`/exam-papers/${id}/questions`)
export const startExam = examPaperId => request.post('/exam-sessions/start', { examPaperId })
export const submitExam = (sessionId, data) => request.post(`/exam-sessions/${sessionId}/submit`, data)
export const getExamResult = sessionId => request.get(`/exam-sessions/${sessionId}/result`)
export const getMyExamSession = examPaperId => request.get(`/exam-sessions/paper/${examPaperId}`)
export const getMyExamSessions = () => request.get('/exam-sessions/my')

// 考试增强 API
export const getExamDetail = id => request.get(`/exam-papers/${id}/detail`)
export const publishExam = id => request.post(`/exam-papers/${id}/publish`)
export const copyExam = (id, data) => request.post(`/exam-papers/${id}/copy`, data)
export const getExamStatistics = id => request.get(`/exam-papers/${id}/statistics`)
export const autoSaveAnswers = (sessionId, data) => request.post(`/exam-sessions/${sessionId}/auto-save`, data)
export const forceSubmit = sessionId => request.post(`/exam-sessions/${sessionId}/force-submit`)
export const getRemainingTime = sessionId => request.get(`/exam-sessions/${sessionId}/time-remaining`)
export const autoGrade = sessionId => request.post(`/exam-sessions/${sessionId}/auto-grade`)
export const getScoreReport = sessionId => request.get(`/exam-sessions/${sessionId}/report`)
export const getActiveExams = () => request.get('/exam-sessions/active')
export const manualGrade = (sessionId, data) => request.post(`/exam-sessions/${sessionId}/manual-grade`, data)

export default { getExamPaperList, getExamPaperById, createExamPaper, updateExamPaper, deleteExamPaper, getExamQuestions, startExam, submitExam, getExamResult, getMyExamSession, getMyExamSessions, getExamDetail, publishExam, copyExam, getExamStatistics, autoSaveAnswers, forceSubmit, getRemainingTime, autoGrade, getScoreReport, getActiveExams, manualGrade }