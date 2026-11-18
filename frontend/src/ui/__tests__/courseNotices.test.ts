import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const api = vi.hoisted(() => ({
  listMyAnnouncements: vi.fn(),
  listCourseAnnouncementsForAdmin: vi.fn(),
  publishCourseAnnouncement: vi.fn(),
  markAsRead: vi.fn(),
  loadCourseNotifications: vi.fn(),
}))

vi.mock('@/api/announcement', () => ({
  listMyAnnouncements: api.listMyAnnouncements,
  listCourseAnnouncementsForAdmin: api.listCourseAnnouncementsForAdmin,
  publishCourseAnnouncement: api.publishCourseAnnouncement,
}))
vi.mock('@/api/notification', () => ({ markAsRead: api.markAsRead }))
const toast = vi.hoisted(() => vi.fn())
vi.mock('../composables/ui', () => ({ useUi: () => ({ toast }) }))
vi.mock('../data/api', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../data/api')>()
  return { ...actual, loadCourseNotifications: api.loadCourseNotifications }
})

import CourseNoticesTab from '../views/course/tabs/CourseNoticesTab.vue'

const envelope = (records: Record<string, unknown>[]) => ({
  code: 200,
  message: 'ok',
  data: { records },
})

const notice = {
  id: 1,
  title: '第三章测验安排',
  content: '本周五随堂测验，范围第三章。',
  time: '2026-11-17T09:00:00',
  unread: true,
  kind: '讨论回复',
  type: 'DISCUSSION_REPLIED',
  category: 'DISCUSSION',
  actionUrl: '',
}

const announcement = {
  id: 7,
  courseId: 4178,
  title: '期中考试说明',
  content: '闭卷，覆盖前五章。',
  createdAt: '2026-11-16T08:00:00',
}

async function mountBody(role: 'teacher' | 'student' | 'admin' = 'student') {
  const wrapper = mount(CourseNoticesTab, {
    props: { courseId: 4178, role },
    global: { stubs: { teleport: true } },
  })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  api.listMyAnnouncements.mockResolvedValue(envelope([announcement]))
  api.listCourseAnnouncementsForAdmin.mockResolvedValue(envelope([announcement]))
  api.loadCourseNotifications.mockResolvedValue([])
  api.markAsRead.mockResolvedValue({ data: {} })
})

describe('课程通知公告', () => {
  it('合并公告与通知，点击打开详情且不展示原始事件码', async () => {
    api.loadCourseNotifications.mockResolvedValue([notice])
    const wrapper = await mountBody()

    expect(wrapper.text()).toContain('第三章测验安排')
    expect(wrapper.text()).toContain('期中考试说明')
    expect(wrapper.text()).not.toContain('DISCUSSION_REPLIED')

    await wrapper.find('.notice-item').trigger('click')
    await flushPromises()

    expect(api.markAsRead).toHaveBeenCalledWith(1)
    expect(wrapper.find('.notice-detail').exists()).toBe(true)
    expect(wrapper.find('.notice-detail').text()).toContain('讨论回复')
    expect(wrapper.find('.notice-detail').text()).toContain('本周五随堂测验，范围第三章。')
  })

  it('管理员课程页读取课程公告，不依赖个人通知', async () => {
    api.loadCourseNotifications.mockResolvedValue([])
    const wrapper = await mountBody('admin')

    expect(api.listCourseAnnouncementsForAdmin).toHaveBeenCalledWith(4178)
    expect(api.listMyAnnouncements).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('期中考试说明')
    expect(wrapper.find('.notice-item').exists()).toBe(true)
  })

  it('通知与公告重复时按公告 id 去重，保留带已读状态的通知行', async () => {
    api.loadCourseNotifications.mockResolvedValue([
      {
        ...notice,
        id: 2,
        title: announcement.title,
        content: announcement.content,
        createdAt: announcement.createdAt,
        type: 'ANNOUNCEMENT',
        category: 'ANNOUNCEMENT',
        announcementId: 7,
      },
    ])
    const wrapper = await mountBody()

    expect(wrapper.findAll('.notice-item')).toHaveLength(1)
  })
})
