import request from './request'
import { type ApiEnvelope, type ApiData } from '@/utils/response'

// 课堂互动：签到 / 投票（InteractionController，前缀 /interaction，需教师/管理员权限）

/* ===== 签到 ===== */
// 发起签到 body: { courseId, title, duration?, type? } → SignIn
export const createSignIn = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/interaction/sign-in', data) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 publishSignIn：业务逻辑处理
 */
export const publishSignIn = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/interaction/sign-in/${id}/publish`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 closeSignIn：业务逻辑处理
 */
export const closeSignIn = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/interaction/sign-in/${id}/close`) as Promise<ApiEnvelope<ApiData>>
// 课程签到列表
export const getSignInList = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/interaction/sign-in/course/${courseId}`) as Promise<ApiEnvelope<ApiData>>
// 签到记录
export const getSignInRecords = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/interaction/sign-in/${id}/records`) as Promise<ApiEnvelope<ApiData>>
// 手动标记 body: { studentId, status }
export const markSignIn = (
  id: number | string,
  data: Record<string, unknown>,
): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/interaction/sign-in/${id}/manual`, data) as Promise<ApiEnvelope<ApiData>>

/* ===== 投票 ===== */
// 发起投票 body: { courseId, title, options(JSON 字符串), voteMode } → Vote
export const createVote = (data: Record<string, unknown>): Promise<ApiEnvelope<ApiData>> =>
  request.post('/interaction/vote', data) as Promise<ApiEnvelope<ApiData>>
/** 发布投票，开始接受学生投票 */
export const publishVote = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/interaction/vote/${id}/publish`) as Promise<ApiEnvelope<ApiData>>
/**
 * 导出函数 closeVote：业务逻辑处理
 */
export const closeVote = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.post(`/interaction/vote/${id}/close`) as Promise<ApiEnvelope<ApiData>>
// 课程投票列表
export const getVoteList = (courseId: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/interaction/vote/course/${courseId}`) as Promise<ApiEnvelope<ApiData>>
// 投票详情
export const getVoteDetail = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/interaction/vote/${id}`) as Promise<ApiEnvelope<ApiData>>
// 投票结果
export const getVoteResult = (id: number | string): Promise<ApiEnvelope<ApiData>> =>
  request.get(`/interaction/vote/${id}/result`) as Promise<ApiEnvelope<ApiData>>

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  createSignIn,
  publishSignIn,
  closeSignIn,
  getSignInList,
  getSignInRecords,
  markSignIn,
  createVote,
  publishVote,
  closeVote,
  getVoteList,
  getVoteDetail,
  getVoteResult,
}
