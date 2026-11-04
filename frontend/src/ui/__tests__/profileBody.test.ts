import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const studentApi = vi.hoisted(() => ({
  getPendingAssignments: vi.fn(),
  getCompletedAssignments: vi.fn(),
  getAllMyCourses: vi.fn(),
}))
vi.mock('@/api/student', () => ({ default: studentApi }))

const userApi = vi.hoisted(() => ({ getProfile: vi.fn() }))
vi.mock('@/api/user', () => ({ default: userApi }))

const authApi = vi.hoisted(() => ({ updateCurrentUser: vi.fn(), changePassword: vi.fn() }))
vi.mock('@/api/auth', () => ({ default: authApi }))

const dataApi = vi.hoisted(() => ({
  loadCourses: vi.fn(),
  loadNotifications: vi.fn(),
}))
vi.mock('../data/api', () => ({ ...dataApi }))

const router = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => router }))

const ui = vi.hoisted(() => ({
  toast: vi.fn(),
  theme: 'light',
  setTheme: vi.fn(),
  auth: {
    user: { role: 'STUDENT', fullName: '测试生', username: 'stu' },
    userId: 1,
    resetAuthState: vi.fn(),
  },
}))
vi.mock('../composables/ui', () => ({ useUi: () => ui }))

import ProfileBody from '../views/specials/ProfileBody.vue'

const courses = [
  { id: 101, name: '高等数学', code: 'MATH101', short: 'MA10', progress: 73, tone: 1 },
  { id: 102, name: '大学英语', code: 'ENG102', short: 'EN10', progress: 45, tone: 2 },
]
const notes = [
  {
    id: 1,
    title: '系统维护通知',
    content: '',
    time: '2026-11-01 10:00',
    unread: true,
    kind: '系统',
  },
  {
    id: 2,
    title: '期中考试成绩发布',
    content: '',
    time: '2026-10-30 09:00',
    unread: false,
    kind: '成绩',
  },
]
const pending = [{ id: 5001, title: '第一章作业', courseId: 101, dueDate: '2026-11-10T23:59:00' }]
const completed = [{ id: 5002, title: '预习测验', courseId: 102, dueDate: '2026-10-20T23:59:00' }]

beforeEach(() => {
  vi.clearAllMocks()
  userApi.getProfile.mockResolvedValue({
    data: { fullName: '测试生', username: 'stu', role: 'STUDENT' },
  })
  dataApi.loadCourses.mockResolvedValue(courses)
  dataApi.loadNotifications.mockResolvedValue(notes)
  studentApi.getPendingAssignments.mockResolvedValue({ data: pending })
  studentApi.getCompletedAssignments.mockResolvedValue({ data: completed })
})

function mountBody() {
  return mount(ProfileBody)
}

describe('M-06 个人中心扩展三面', () => {
  it('数据概览按真实数据聚合：在学 / 待办 / 未读 / 已完成', async () => {
    const wrapper = mountBody()
    await flushPromises()
    const nums = wrapper.findAll('.profile-stat-num').map((n) => n.text())
    expect(nums).toEqual(['2', '1', '1', '1'])
    wrapper.unmount()
  })

  it('我的课程渲染封面 + 进度，点击跳转到课程空间', async () => {
    const wrapper = mountBody()
    await flushPromises()
    const items = wrapper.findAll('.profile-course-item')
    expect(items.length).toBe(2)
    expect(items[0].find('.course-name').text()).toBe('高等数学')
    expect(items[0].find('.course-cover').text()).toBe('MA10')
    // 进度条宽度随进度（73%）
    expect(items[0].find('.course-bar i').attributes('style')).toContain('width: 73%')
    await items[0].trigger('click')
    expect(router.push).toHaveBeenCalledWith('/student/courses/101')
    wrapper.unmount()
  })

  it('学习动态聚合待办作业与通知，按类型着色，可点击的课程项跳转', async () => {
    const wrapper = mountBody()
    await flushPromises()
    const feed = wrapper.findAll('.profile-feed-item')
    // 1 条待办 + 2 条通知
    expect(feed.length).toBe(3)
    // 第一条是待办作业（warning），带 chip
    expect(feed[0].find('.chip').text()).toBe('待办作业')
    expect(feed[0].find('.profile-feed-name').text()).toBe('第一章作业')
    expect(feed[0].classes()).toContain('clickable')
    await feed[0].trigger('click')
    expect(router.push).toHaveBeenCalledWith('/student/courses/101')
    wrapper.unmount()
  })

  it('空数据呈现可读空态，而非报错', async () => {
    dataApi.loadCourses.mockResolvedValue([])
    dataApi.loadNotifications.mockResolvedValue([])
    studentApi.getPendingAssignments.mockResolvedValue({ data: [] })
    studentApi.getCompletedAssignments.mockResolvedValue({ data: [] })

    const wrapper = mountBody()
    await flushPromises()
    expect(wrapper.text()).toContain('还没有在学课程')
    expect(wrapper.text()).toContain('暂无学习动态')
    wrapper.unmount()
  })

  it('单路接口失败不连坐其它路（分路归因）', async () => {
    studentApi.getPendingAssignments.mockRejectedValue(new Error('500'))
    dataApi.loadNotifications.mockResolvedValue(notes)

    const wrapper = mountBody()
    await flushPromises()
    // 课程与通知仍正常；待办缺省为 0（已完成路未被注入失败，仍为 1）
    expect(wrapper.findAll('.profile-course-item').length).toBe(2)
    expect(wrapper.findAll('.profile-feed-item').length).toBe(2) // 仅 2 条通知
    expect(wrapper.findAll('.profile-stat-num').map((n) => n.text())).toEqual(['2', '0', '1', '1'])
    wrapper.unmount()
  })
})
