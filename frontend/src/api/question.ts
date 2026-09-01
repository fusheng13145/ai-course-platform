import request from './request'
/** 获取题目列表 */
export const getQuestionList = (params = {}) => request.get('/questions', { params })
/** 根据ID获取题目 */
export const getQuestionById = id => request.get(`/questions/${id}`)
/** 创建题目 */
export const createQuestion = data => request.post('/questions', data)
/** 更新题目 */
export const updateQuestion = (id, data) => request.put(`/questions/${id}`, data)
/** 删除题目 */
export const deleteQuestion = id => request.delete(`/questions/${id}`)
/** AI 生成题目 */
export const generateByAI = data => request.post('/questions/ai-generate', data)

/** 审核题目 */
export const reviewQuestion = (id, data) => request.post(`/questions/${id}/review`, data)
/** 复制题目 */
export const copyQuestion = id => request.post(`/questions/${id}/copy`)
/** 搜索题目 */
export const searchQuestions = (params = {}) => request.get('/questions/search', { params })
/** 随机选题 */
export const randomSelect = (params = {}) => request.get('/questions/random', { params })
/** 根据知识点获取题目 */
export const getByKnowledgePoint = kpId => request.get(`/questions/kp/${kpId}`)
/** 获取题目统计数据 */
export const getQuestionStats = id => request.get(`/questions/${id}/stats`)
/** 获取易错题目 */
export const getErrorProne = (params = {}) => request.get('/questions/error-prone', { params })
/** 导出题库为 Excel 文件（blob 下载，可按课程筛选） */
export const exportQuestionsExcel = (params = {}) =>
  request.get('/teacher/questions/export', {
    params,
    responseType: 'blob',
  })
/** Excel 批量导入题目（.xlsx，可选指定归属课程） */
export const importQuestionsExcel = data =>
  request.post('/teacher/questions/import-excel', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default { getQuestionList, getQuestionById, createQuestion, updateQuestion, deleteQuestion, generateByAI, reviewQuestion, copyQuestion, searchQuestions, randomSelect, getByKnowledgePoint, getQuestionStats, getErrorProne, exportQuestionsExcel, importQuestionsExcel }