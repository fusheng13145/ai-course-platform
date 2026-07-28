import request from './request'

// 课堂互动：签到 / 投票（InteractionController，前缀 /interaction，需教师/管理员权限）

/* ===== 签到 ===== */
// 发起签到 body: { courseId, title, duration?, type? } → SignIn
export const createSignIn = (data) => request.post('/interaction/sign-in', data)
export const publishSignIn = (id) => request.post(`/interaction/sign-in/${id}/publish`)
export const closeSignIn = (id) => request.post(`/interaction/sign-in/${id}/close`)
// 课程签到列表
export const getSignInList = (courseId) => request.get(`/interaction/sign-in/course/${courseId}`)
// 签到记录
export const getSignInRecords = (id) => request.get(`/interaction/sign-in/${id}/records`)
// 手动标记 body: { studentId, status }
export const markSignIn = (id, data) => request.post(`/interaction/sign-in/${id}/manual`, data)

/* ===== 投票 ===== */
// 发起投票 body: { courseId, title, options[], multiple? } → Vote
export const createVote = (data) => request.post('/interaction/vote', data)
export const closeVote = (id) => request.post(`/interaction/vote/${id}/close`)
// 课程投票列表
export const getVoteList = (courseId) => request.get(`/interaction/vote/course/${courseId}`)
// 投票详情
export const getVoteDetail = (id) => request.get(`/interaction/vote/${id}`)
// 投票结果
export const getVoteResult = (id) => request.get(`/interaction/vote/${id}/result`)

export default {
  createSignIn,
  publishSignIn,
  closeSignIn,
  getSignInList,
  getSignInRecords,
  markSignIn,
  createVote,
  closeVote,
  getVoteList,
  getVoteDetail,
  getVoteResult,
}
