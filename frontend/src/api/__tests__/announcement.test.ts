import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * 课程公告 API 契约（2026-11-07 P2 批次：课程内「通知」页签数据面）。
 * 钉住两条契约：列表走 GET /announcements（三维度并集分页）、发布走 POST /announcements
 * 且必须带 courseId（三目标互斥——courseId/teachingClassId/adminClassId 只能选一）。
 */
const requestMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
}))
vi.mock('@/api/request', () => ({ default: requestMock }))

import announcementApi, { listMyAnnouncements, publishCourseAnnouncement } from '@/api/announcement'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('课程公告 API 契约', () => {
  it('listMyAnnouncements → GET /announcements 分页参数透传', async () => {
    requestMock.get.mockResolvedValue({ code: 200, message: 'ok', data: { records: [] } })
    await listMyAnnouncements({ page: 1, size: 50 })
    expect(requestMock.get).toHaveBeenCalledWith('/announcements', {
      params: { page: 1, size: 50 },
    })
  })

  it('publishCourseAnnouncement → POST /announcements 且 courseId 在 body 内（三目标互斥）', async () => {
    requestMock.post.mockResolvedValue({ code: 200, message: 'ok', data: { id: 1 } })
    await publishCourseAnnouncement(4047, { title: '明天停课', content: '详见群公告' })
    expect(requestMock.post).toHaveBeenCalledWith('/announcements', {
      courseId: 4047,
      title: '明天停课',
      content: '详见群公告',
    })
    // 不得携带其余目标维度，避免触发后端「三目标互斥必须选一」校验失败
    const body = requestMock.post.mock.calls[0][1] as Record<string, unknown>
    expect(body).not.toHaveProperty('teachingClassId')
    expect(body).not.toHaveProperty('adminClassId')
  })

  it('默认导出与具名导出一致（模块双形态可用）', () => {
    expect(announcementApi.listMyAnnouncements).toBe(listMyAnnouncements)
    expect(announcementApi.publishCourseAnnouncement).toBe(publishCourseAnnouncement)
  })
})
