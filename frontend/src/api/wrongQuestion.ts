import request from './request'
import { firstNonEmptyArray, type ApiEnvelope, type ApiData } from '@/utils/response'

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
export const getWrongQuestions = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/wrong-questions', { params }).then((res: ApiData) => {
    const payload = res && res.data
    // 修复（2026-10-04，P1-10 同类）：原写法 `payload.list || payload.records` 用 `||` 择一，
    // 而空数组是 truthy —— 后端把 `list: []` 与真实数据 `records` 一起返回时，
    // 这里短路取到空的 list，列表仍被置空（正是 P2-6 想修掉的那个"统计有数、列表为空"现象的另一半）。
    // 改用 firstNonEmptyArray：谁真正有元素取谁，都为空才回落到 []。
    const list = Array.isArray(payload)
      ? payload
      : firstNonEmptyArray(payload && payload.list, payload && payload.records)
    return { ...res, data: list }
  }) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 addWrongQuestion：创建/新增数据
 */
export const addWrongQuestion = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/wrong-questions', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 deleteWrongQuestion：删除数据
 */
export const deleteWrongQuestion = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/wrong-questions/${id}`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 markMastered：业务逻辑处理
 */
export const markMastered = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.patch(`/wrong-questions/${id}/mastered`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 unmarkMastered：业务逻辑处理
 */
export const unmarkMastered = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.patch(`/wrong-questions/${id}/unmaster`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getWrongQuestionStats：获取/查询数据
 */
export const getWrongQuestionStats = (): Promise<ApiEnvelope<ApiData>> =>
  request.get('/wrong-questions/stats') as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getUnmastered：获取/查询数据
 */
export const getUnmastered = (
  studentId?: number | string,
  courseId?: number | string,
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/wrong-questions/unmastered', { params: { studentId, courseId } }) as Promise<
    ApiEnvelope<ApiData>
  >
/**
 * 导出函数 getByKnowledgePoints：获取/查询数据
 */
export const getByKnowledgePoints = (
  studentId?: number | string,
  courseId?: number | string,
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/wrong-questions/by-kp', { params: { studentId, courseId } }) as Promise<
    ApiEnvelope<ApiData>
  >
/**
 * 导出函数 generatePracticePaper：业务逻辑处理
 */
export const generatePracticePaper = (
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post('/wrong-questions/practice-paper', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 getRelatedQuestions：获取/查询数据
 */
export const getRelatedQuestions = (questionId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/wrong-questions/${questionId}/related`) as Promise<ApiEnvelope<ApiData>>
// 兼容别名：与 getRelatedQuestions 指向同一端点
export const getRelated = (questionId: number | string): Promise<ApiEnvelope<ApiData>> =>
  getRelatedQuestions(questionId)

/**
 * 学生本人的错题本（StudentController，`/student/wrong-questions`）。
 * 与上面 `/wrong-questions` 那组是全量/教师面向的端点区分开：这组按登录学生作用域、
 * 返回分页信封，标记掌握用 PUT、移除用 DELETE。
 */
export const getMyWrongQuestions = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/student/wrong-questions', { params }) as Promise<ApiEnvelope<ApiData>>

export const markMyWrongQuestionMastered = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.put(`/student/wrong-questions/${id}/mastered`) as Promise<ApiEnvelope<ApiData>>

export const removeMyWrongQuestion = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.delete(`/student/wrong-questions/${id}`) as Promise<ApiEnvelope<ApiData>>

/** 错题统计（服务端按 auth 主体作用域，学生调用只得到自己的读数） */
export const getMyWrongQuestionStats = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/wrong-questions/stats', { params }) as Promise<ApiEnvelope<ApiData>>

/** 生成错题练习卷（随机取未掌握错题对应的题目；无判分端点，前端只展示） */
export const generateMyWrongPractice = (
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post('/student/wrong-questions/practice', data) as Promise<ApiEnvelope<ApiData>>

/** 错题按知识点分组（服务端按 auth 主体作用域；按题目数量倒序） */
export const getMyWrongGroups = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/wrong-questions/by-kp', { params }) as Promise<ApiEnvelope<ApiData>>

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
  getMyWrongQuestions,
  markMyWrongQuestionMastered,
  removeMyWrongQuestion,
  getMyWrongQuestionStats,
  generateMyWrongPractice,
  getMyWrongGroups,
}
