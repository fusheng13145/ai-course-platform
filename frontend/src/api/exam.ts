import request from './request'
/** 获取考试试卷列表 */
export const getExamPaperList = (params = {}) => request.get('/exam-papers', { params })
/** 根据ID获取试卷 */
export const getExamPaperById = (id) => request.get(`/exam-papers/${id}`)
/** 创建考试试卷 */
export const createExamPaper = (data) => request.post('/exam-papers', data)
/** 更新考试试卷 */
export const updateExamPaper = (id, data) => request.put(`/exam-papers/${id}`, data)
/** 删除考试试卷 */
export const deleteExamPaper = (id) => request.delete(`/exam-papers/${id}`)
/**
 * 试卷题目解包归一化。
 *
 * 后端 `/exam-papers/{id}/questions` 返回的是「试卷-题目关联」包装结构
 * （`{ id, orderIndex, points, question: {…} }`），而调用方（学生答题页、教师题目预览）
 * 一律按**扁平题目对象**读取 `type / content / options / score`。
 *
 * 历史缺陷（2026-09-21 修复）：此前未做归一化，导致
 * ① 答题页题干空白、题型显示「未知题型」、分值回落到默认值；
 * ② 提交答案时误用关联行 id 而非真实题目 id，服务端判卷取不到作答。
 * 统一在 API 层收敛，一次覆盖全部调用方；已是扁平结构的返回项原样透传。
 *
 * 导出供单测直接验证契约（`src/api/__tests__/exam.test.ts`）——这是 P0-2 的护栏，
 * 若归一化被移除或改写，测试会立刻失败。
 */
export function normalizeExamQuestions(payload) {
  const list = Array.isArray(payload) ? payload : []
  return list.map((item) => {
    const nested = item && item.question
    if (!nested || typeof nested !== 'object') return item
    return {
      ...nested,
      // 分值以关联行上的 points 为准；乱序序号保留供展示
      score: item.points ?? nested.score,
      orderIndex: item.orderIndex ?? nested.orderIndex,
    }
  })
}

/** 获取试卷题目（返回已归一化的扁平题目对象） */
export const getExamQuestions = (id) =>
  request.get(`/exam-papers/${id}/questions`).then((res) => ({
    ...res,
    data: normalizeExamQuestions(res && res.data),
  }))
/** 开始考试 */
export const startExam = (examPaperId) => request.post('/exam-sessions/start', { examPaperId })
/** 提交考试答案 */
export const submitExam = (sessionId, data) =>
  request.post(`/exam-sessions/${sessionId}/submit`, data)
/** 获取考试结果 */
export const getExamResult = (sessionId) => request.get(`/exam-sessions/${sessionId}/result`)
/** 获取我的考试会话 */
export const getMyExamSession = (examPaperId) => request.get(`/exam-sessions/paper/${examPaperId}`)
/** 获取我的所有考试会话 */
export const getMyExamSessions = () => request.get('/exam-sessions/my')

// 考试增强 API
/** 获取考试详情 */
export const getExamDetail = (id) => request.get(`/exam-papers/${id}/detail`)
/** 发布考试 */
export const publishExam = (id) => request.post(`/exam-papers/${id}/publish`)
/** 复制考试 */
export const copyExam = (id, data) => request.post(`/exam-papers/${id}/copy`, data)
/** 获取考试统计数据 */
export const getExamStatistics = (id) => request.get(`/exam-papers/${id}/statistics`)
/** 自动保存答题记录 */
export const autoSaveAnswers = (sessionId, data) =>
  request.post(`/exam-sessions/${sessionId}/auto-save`, data)
/** 强制交卷 */
export const forceSubmit = (sessionId) => request.post(`/exam-sessions/${sessionId}/force-submit`)
/** 获取剩余时间 */
export const getRemainingTime = (sessionId) =>
  request.get(`/exam-sessions/${sessionId}/time-remaining`)
/** 自动批改 */
export const autoGrade = (sessionId) => request.post(`/exam-sessions/${sessionId}/auto-grade`)
/** 获取成绩报告 */
export const getScoreReport = (sessionId) => request.get(`/exam-sessions/${sessionId}/report`)
/** 获取活跃考试 */
export const getActiveExams = () => request.get('/exam-sessions/active')
/** 手动批阅 */
export const manualGrade = (sessionId, data) =>
  request.post(`/exam-sessions/${sessionId}/manual-grade`, data)
/** 获取作弊检测信息 */
export const getCheatingDetect = (sessionId) =>
  request.get(`/exam-sessions/${sessionId}/cheating-detect`)

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getExamPaperList,
  getExamPaperById,
  createExamPaper,
  updateExamPaper,
  deleteExamPaper,
  getExamQuestions,
  startExam,
  submitExam,
  getExamResult,
  getMyExamSession,
  getMyExamSessions,
  getExamDetail,
  publishExam,
  copyExam,
  getExamStatistics,
  autoSaveAnswers,
  forceSubmit,
  getRemainingTime,
  autoGrade,
  getScoreReport,
  getActiveExams,
  manualGrade,
  getCheatingDetect,
}
