import request from './request'
/** 获取考试试卷列表 */
export const getExamPaperList = (params = {}) => request.get('/exam-papers', { params })
/** 根据ID获取试卷 */
export const getExamPaperById = id => request.get(`/exam-papers/${id}`)
/** 创建考试试卷 */
export const createExamPaper = data => request.post('/exam-papers', data)
/** 更新考试试卷 */
export const updateExamPaper = (id, data) => request.put(`/exam-papers/${id}`, data)
/** 删除考试试卷 */
export const deleteExamPaper = id => request.delete(`/exam-papers/${id}`)
/** 获取试卷题目 */
export const getExamQuestions = id => request.get(`/exam-papers/${id}/questions`)
/** 开始考试 */
export const startExam = examPaperId => request.post('/exam-sessions/start', { examPaperId })
/** 提交考试答案 */
export const submitExam = (sessionId, data) => request.post(`/exam-sessions/${sessionId}/submit`, data)
/** 获取考试结果 */
export const getExamResult = sessionId => request.get(`/exam-sessions/${sessionId}/result`)
/** 获取我的考试会话 */
export const getMyExamSession = examPaperId => request.get(`/exam-sessions/paper/${examPaperId}`)
/** 获取我的所有考试会话 */
export const getMyExamSessions = () => request.get('/exam-sessions/my')

// 考试增强 API
/** 获取考试详情 */
export const getExamDetail = id => request.get(`/exam-papers/${id}/detail`)
/** 发布考试 */
export const publishExam = id => request.post(`/exam-papers/${id}/publish`)
/** 复制考试 */
export const copyExam = (id, data) => request.post(`/exam-papers/${id}/copy`, data)
/** 获取考试统计数据 */
export const getExamStatistics = id => request.get(`/exam-papers/${id}/statistics`)
/** 自动保存答题记录 */
export const autoSaveAnswers = (sessionId, data) => request.post(`/exam-sessions/${sessionId}/auto-save`, data)
/** 强制交卷 */
export const forceSubmit = sessionId => request.post(`/exam-sessions/${sessionId}/force-submit`)
/** 获取剩余时间 */
export const getRemainingTime = sessionId => request.get(`/exam-sessions/${sessionId}/time-remaining`)
/** 自动批改 */
export const autoGrade = sessionId => request.post(`/exam-sessions/${sessionId}/auto-grade`)
/** 获取成绩报告 */
export const getScoreReport = sessionId => request.get(`/exam-sessions/${sessionId}/report`)
/** 获取活跃考试 */
export const getActiveExams = () => request.get('/exam-sessions/active')
/** 手动批阅 */
export const manualGrade = (sessionId, data) => request.post(`/exam-sessions/${sessionId}/manual-grade`, data)
/** 获取作弊检测信息 */
export const getCheatingDetect = sessionId => request.get(`/exam-sessions/${sessionId}/cheating-detect`)

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default { getExamPaperList, getExamPaperById, createExamPaper, updateExamPaper, deleteExamPaper, getExamQuestions, startExam, submitExam, getExamResult, getMyExamSession, getMyExamSessions, getExamDetail, publishExam, copyExam, getExamStatistics, autoSaveAnswers, forceSubmit, getRemainingTime, autoGrade, getScoreReport, getActiveExams, manualGrade, getCheatingDetect }