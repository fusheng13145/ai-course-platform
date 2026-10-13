import request from './request'

// 课程讨论（DiscussionController，前缀 /discussion）

export interface DiscussionTopicQuery {
  keyword?: string
  chapterId?: number | string
  essence?: boolean | string
  page?: number
  size?: number
}

export interface CreateDiscussionTopicPayload {
  courseId: number | string
  chapterId?: number | string
  title: string
  content: string
}

export interface ReplyDiscussionTopicPayload {
  content: string
  mentionUserIds?: Array<number | string>
}

export interface DiscussionLikeResult {
  likeCount: number
  liked: boolean
}

function roleLabel(role?: string) {
  switch (role?.toUpperCase()) {
    case 'TEACHER':
      return '教师'
    case 'STUDENT':
      return '同学'
    case 'ADMIN':
      return '管理员'
    default:
      return null
  }
}

/** 将后端实体字段映射为讨论组件共享的展示字段。 */
export function normalizeDiscussionTopic(topic: DiscussionTopic): DiscussionTopic {
  return {
    ...topic,
    authorId: topic.authorId ?? topic.creatorId,
    authorName: topic.authorName?.trim() || roleLabel(topic.creatorRole),
    pinned: topic.pinned ?? topic.isPinned ?? false,
    closed: topic.closed ?? topic.status === 'CLOSED',
    postCount: topic.postCount ?? topic.replyCount ?? 0,
  }
}

/** 归一化回复点赞数与后端只提供的作者角色。 */
export function normalizeDiscussionPost(post: DiscussionPost): DiscussionPost {
  return {
    ...post,
    authorName: post.authorName?.trim() || roleLabel(post.authorRole) || '匿名',
    likes: post.likes ?? post.likeCount ?? 0,
    likeCount: post.likeCount ?? post.likes ?? 0,
  }
}

// 发布话题 body: { courseId, chapterId?, title, content } → DiscussionTopic
export const createTopic = (data: CreateDiscussionTopicPayload) =>
  request.post('/discussion/topic', data)
// 话题列表（含 chapterId/keyword 可选查询）
export const getTopics = (courseId: number | string, params: DiscussionTopicQuery = {}) =>
  request.get(`/discussion/topic/course/${courseId}`, { params })
// 话题详情 → Map（getTopicDetail，含帖子列表）
export const getTopicDetail = (id: number | string) => request.get(`/discussion/topic/${id}`)
// 回复 body: DiscussionPost
export const replyTopic = (id: number | string, data: ReplyDiscussionTopicPayload) =>
  request.post(`/discussion/topic/${id}/reply`, data)
// 点赞帖子
export const likePost = (postId: number | string) => request.post(`/discussion/post/${postId}/like`)
// 关闭话题
export const closeTopic = (id: number | string) => request.post(`/discussion/topic/${id}/close`)
// 置顶/取消 body 放查询 ?pinned=true|false
export const pinTopic = (id: number | string, pinned: boolean) =>
  request.post(`/discussion/topic/${id}/pin`, null, { params: { pinned } })
// 删除帖子
export const deletePost = (postId: number | string) => request.delete(`/discussion/post/${postId}`)

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
  // normalize 助手同为模块对外接口：默认导出须与具名导出保持一致
  // （passthrough.test.ts 的「默认导出完整性」用例钉住该口径）。
  normalizeDiscussionTopic,
  normalizeDiscussionPost,
}
