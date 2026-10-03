import request from './request'

// 错题本 API（后端: WrongQuestionController /wrong-questions）
/**
 * 错题列表。
 *
 * <p><b>修复（2026-09-23，P2-6）</b>：后端 `/wrong-questions` 返回 `PagedResponse`
 * （`{ list, total, page, pageSize, ... }`），而唯一调用方 {@code useWrongQuestions}
 * 按<b>数组</b>消费（`Array.isArray(list) ? list.map(...) : []`）——结构不匹配使列表恒被置空，
 * 表现为错题本「统计卡有数字（6 条）但列表显示『暂无错题记录』」。</p>
 *
 * <p>在 API 层统一归一化为数组，与 {@code api/exam.ts} 的 {@code normalizeExamQuestions}
 * 同一模式：不改后端分页契约，也不改各调用方。</p>
 */
export const getWrongQuestions = (params = {}) =>
  request.get('/wrong-questions', { params }).then((res) => {
    const payload = res && res.data
    const list = Array.isArray(payload)
      ? payload
      : (payload && (payload.list || payload.records)) || []
    return { ...res, data: list }
  })
/**
 * 导出函数 addWrongQuestion：创建/新增数据
 */
export const addWrongQuestion = (data) => request.post('/wrong-questions', data)
/**
 * 导出函数 deleteWrongQuestion：删除数据
 */
export const deleteWrongQuestion = (id) => request.delete(`/wrong-questions/${id}`)
/**
 * 导出函数 markMastered：业务逻辑处理
 */
export const markMastered = (id) => request.patch(`/wrong-questions/${id}/mastered`)
/**
 * 导出函数 unmarkMastered：业务逻辑处理
 */
export const unmarkMastered = (id) => request.patch(`/wrong-questions/${id}/unmaster`)
/**
 * 导出函数 getWrongQuestionStats：获取/查询数据
 */
export const getWrongQuestionStats = () => request.get('/wrong-questions/stats')
/**
 * 导出函数 getUnmastered：获取/查询数据
 */
export const getUnmastered = (studentId?, courseId?) =>
  request.get('/wrong-questions/unmastered', { params: { studentId, courseId } })
/**
 * 导出函数 getByKnowledgePoints：获取/查询数据
 */
export const getByKnowledgePoints = (studentId?, courseId?) =>
  request.get('/wrong-questions/by-kp', { params: { studentId, courseId } })
/**
 * 导出函数 generatePracticePaper：业务逻辑处理
 */
export const generatePracticePaper = (data) => request.post('/wrong-questions/practice-paper', data)
/**
 * 导出函数 getRelatedQuestions：获取/查询数据
 */
export const getRelatedQuestions = (questionId) =>
  request.get(`/wrong-questions/${questionId}/related`)
// 兼容别名：与 getRelatedQuestions 指向同一端点
export const getRelated = (questionId) => getRelatedQuestions(questionId)

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getWrongQuestions,
  addWrongQuestion,
  deleteWrongQuestion,
  markMastered,
  unmarkMastered,
  getWrongQuestionStats,
  getUnmastered,
  getByKnowledgePoints,
  generatePracticePaper,
  getRelatedQuestions,
  getRelated,
}
