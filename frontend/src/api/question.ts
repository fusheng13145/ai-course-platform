import request from './request'
import { fetchAllPages, type ApiEnvelope, type ApiData } from '@/utils/response'
/** 获取题目列表 */
export const getQuestionList = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/questions', { params }) as Promise<ApiEnvelope<ApiData>>
/** 读取题目列表所有分页。 */
export const getAllQuestions = (filters: Record<string, unknown> = {}): Promise<ApiData[]> =>
  fetchAllPages((page: number, size: number) =>
    getQuestionList({ ...filters, page, size }),
  ) as Promise<ApiData[]>
/** 根据ID获取题目 */
export const getQuestionById = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/questions/${id}`) as Promise<ApiEnvelope<ApiData>>
/** 创建题目 */
export const createQuestion = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/questions', data) as Promise<ApiEnvelope<ApiData>>
/** 更新题目 */
export const updateQuestion = (
  id: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/questions/${id}`, data) as Promise<ApiEnvelope<ApiData>>
/** 删除题目 */
export const deleteQuestion = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/questions/${id}`) as Promise<ApiEnvelope<ApiData>>
/** AI 生成题目 */
export const generateByAI = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/questions/ai-generate', data) as Promise<ApiEnvelope<ApiData>>

/** 审核题目 */
export const reviewQuestion = (
  id: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/questions/${id}/review`, data) as Promise<ApiEnvelope<ApiData>>
/** 复制题目 */
export const copyQuestion = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/questions/${id}/copy`) as Promise<ApiEnvelope<ApiData>>
/** 搜索题目 */
export const searchQuestions = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/questions/search', { params }) as Promise<ApiEnvelope<ApiData>>
/** 读取搜索结果所有分页，避免题库选择器只展示第一页。 */
export const searchAllQuestions = (filters: Record<string, unknown> = {}): Promise<ApiData[]> =>
  fetchAllPages((page: number, size: number) =>
    searchQuestions({ ...filters, page, size }),
  ) as Promise<ApiData[]>
/** 随机选题 */
export const randomSelect = (params: Record<string, unknown> = {}): Promise<ApiEnvelope<ApiData>> =>
  request.get('/questions/random', { params }) as Promise<ApiEnvelope<ApiData>>
/** 根据知识点获取题目 */
export const getByKnowledgePoint = (kpId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/questions/kp/${kpId}`) as Promise<ApiEnvelope<ApiData>>
/** 获取题目统计数据 */
export const getQuestionStats = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/questions/${id}/stats`) as Promise<ApiEnvelope<ApiData>>
/** 获取题库总览统计（总题数 + 今日新增） */
export const getBankStatistics = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/questions/statistics') as Promise<ApiEnvelope<ApiData>>
/** 获取易错题目 */
export const getErrorProne = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/questions/error-prone', { params }) as Promise<ApiEnvelope<ApiData>>
/** 导出题库为 Excel 文件（blob 下载，可按课程筛选） */
export const exportQuestionsExcel = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/teacher/questions/export', {
    params,
    responseType: 'blob',
  }) as Promise<ApiEnvelope<ApiData>>
/** Excel 批量导入题目（.xlsx，可选指定归属课程） */
export const importQuestionsExcel = (data: FormData): Promise<ApiEnvelope<ApiData>> =>
  request.post('/teacher/questions/import-excel', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }) as Promise<ApiEnvelope<ApiData>>

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  getQuestionList,
  getAllQuestions,
  getQuestionById,
  createQuestion,
  updateQuestion,
  deleteQuestion,
  generateByAI,
  reviewQuestion,
  copyQuestion,
  searchQuestions,
  searchAllQuestions,
  randomSelect,
  getByKnowledgePoint,
  getQuestionStats,
  getBankStatistics,
  getErrorProne,
  exportQuestionsExcel,
  importQuestionsExcel,
}
