import request from './request'
import type { ApiEnvelope, ApiData } from '@/utils/response'

/**
 * 课程公告/通知（2026-11-07 P2 批次：课程内「通知」页签的数据面）。
 *
 * 复用 WP8 公告链路：教师发布课程公告 → 后端按受众（选课学生 ∪ 主讲）扇出站内通知；
 * 学生端列表来自 GET /announcements（当前用户三维度并集），本页按课程 ID 前端过滤。
 * 契约见 AnnouncementController（V16 迁移）；非受众 403、课程未选 403 由后端把守。
 */

/** 当前用户可见的公告分页（三维度并集；页内按课程过滤在调用方完成）。 */
export const listMyAnnouncements = (
  params: Record<string, unknown> = {},
): Promise<ApiEnvelope<ApiData>> =>
  request.get('/announcements', { params }) as Promise<ApiEnvelope<ApiData>>

/** 教师向课程发布公告（扇出为站内通知；权限：课程管理.assertCanManageCourse）。 */
export const publishCourseAnnouncement = (
  courseId: number | string,
  data: { title: string; content: string },
): Promise<ApiEnvelope<ApiData>> =>
  request.post('/announcements', { courseId, ...data }) as Promise<ApiEnvelope<ApiData>>

export default {
  listMyAnnouncements,
  publishCourseAnnouncement,
}
