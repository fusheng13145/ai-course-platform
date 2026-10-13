import { describe, expect, it, vi } from 'vitest'

const requestMocks = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('@/api/request', () => ({ default: requestMocks }))

import { normalizeDiscussionPost, normalizeDiscussionTopic } from '@/api/discussion'

describe('discussion API response normalization', () => {
  it('maps backend topic fields to the shared discussion view model', () => {
    const topic = normalizeDiscussionTopic({
      id: 28,
      courseId: 4007,
      title: 'TCP 握手',
      content: '三次握手的作用是什么？',
      creatorId: 12,
      creatorRole: 'TEACHER',
      status: 'OPEN',
      isPinned: true,
      isEssence: true,
      replyCount: 4,
    })

    expect(topic).toMatchObject({
      authorId: 12,
      authorName: '教师',
      pinned: true,
      closed: false,
      postCount: 4,
      isEssence: true,
    })
  })

  it('maps backend reply fields without losing zero counts or mention data', () => {
    const post = normalizeDiscussionPost({
      id: 91,
      topicId: 28,
      authorId: 33,
      authorRole: 'STUDENT',
      content: '用于同步双方的初始序号。',
      likeCount: 0,
      mentions: '12,15',
    })

    expect(post).toMatchObject({
      authorName: '同学',
      likes: 0,
      likeCount: 0,
      mentions: '12,15',
    })
  })

  it('preserves an explicit author name and recognizes closed topics', () => {
    const topic = normalizeDiscussionTopic({
      id: 29,
      title: '已结束话题',
      content: '内容',
      authorName: '李老师',
      status: 'CLOSED',
      isPinned: false,
    })

    expect(topic.authorName).toBe('李老师')
    expect(topic.closed).toBe(true)
    expect(topic.pinned).toBe(false)
  })
})
