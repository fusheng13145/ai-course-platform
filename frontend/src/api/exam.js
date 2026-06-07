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
export default { getExamPaperList, getExamPaperById, createExamPaper, updateExamPaper, deleteExamPaper, getExamQuestions, startExam, submitExam, getExamResult, getMyExamSession }