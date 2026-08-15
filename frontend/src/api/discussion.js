import request from './request'

// 课程讨论（DiscussionController，前缀 /discussion）

// 发布话题 body: { courseId, chapterId?, title, content } → DiscussionTopic
export const createTopic = (data) => request.post('/discussion/topic', data)
// 话题列表（含 chapterId/keyword 可选查询）
export const getTopics = (courseId, params) =>
  request.get(`/discussion/topic/course/${courseId}`, { params })
// 话题详情 → Map（getTopicDetail，含帖子列表）
export const getTopicDetail = (id) => request.get(`/discussion/topic/${id}`)
// 回复 body: DiscussionPost
export const replyTopic = (id, data) => request.post(`/discussion/topic/${id}/reply`, data)
// 点赞帖子
export const likePost = (postId) => request.post(`/discussion/post/${postId}/like`)
// 关闭话题
export const closeTopic = (id) => request.post(`/discussion/topic/${id}/close`)
// 置顶/取消 body 放查询 ?pinned=true|false
export const pinTopic = (id, pinned) =>
  request.post(`/discussion/topic/${id}/pin`, null, { params: { pinned } })
// 删除帖子
export const deletePost = (postId) => request.delete(`/discussion/post/${postId}`)

/**
 * 默认导出对象：模块/组件配置与导出内容
 */
export default {
  createTopic,
  getTopics,
  getTopicDetail,
  replyTopic,
  likePost,
  closeTopic,
  pinTopic,
  deletePost,
}
