import request from './request'
export const getExamPaperList = params => request.get('/exam-papers', { params })
export const getExamPaperById = id => request.get(`/exam-papers/${id}`)
export const createExamPaper = data => request.post('/exam-papers', data)
export const updateExamPaper = (id, data) => request.put(`/exam-papers/${id}`, data)
export const deleteExamPaper = id => request.delete(`/exam-papers/${id}`)
export const startExam = (paperId, studentId) => request.post(`/exam-papers/${paperId}/start`, { studentId })
export const submitExam = (sessionId, data) => request.post(`/exam-sessions/${sessionId}/submit`, data)
export const getExamResult = sessionId => request.get(`/exam-sessions/${sessionId}`)
export default { getExamPaperList, getExamPaperById, createExamPaper, updateExamPaper, deleteExamPaper, startExam, submitExam, getExamResult }