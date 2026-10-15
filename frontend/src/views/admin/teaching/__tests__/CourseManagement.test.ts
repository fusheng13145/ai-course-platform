import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CourseManagement.vue（admin 课程管理）— 加载失败可见（D2-1）
 *
 * <p>原实现 onMounted 加载失败仅弹一次性 toast：统计卡谎报 0、列表空态谎称
 * 「暂无课程数据」，管理员无法区分「平台确无课程」与「接口失败」。
 * 现接入 useListFeedback 原语：失败 → 持久 el-alert + 双态空文案 + 读数显 '--'，
 * 且响应格式无效（非数组）同样进入失败态而非渲染成空列表。</p>
 */

const mocks = vi.hoisted(() => ({
  getAllCourses: vi.fn(),
}))

vi.mock('@/api/course', () => ({
  default: {
    getAllCourses: mocks.getAllCourses,
    approveCourse: vi.fn(),
    rejectCourse: vi.fn(),
    toggleCourseStatus: vi.fn(),
    deleteCourse: vi.fn(),
  },
}))

vi.mock('element-plus', () => ({
  ElMessage: { error: vi.fn(), success: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn(), prompt: vi.fn() },
}))

import CourseManagement from '@/views/admin/teaching/CourseManagement.vue'

function mountPage() {
  return mount(CourseManagement, {
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        // D2-3：空态统一为 EmptyState；shallow 下显式桩，断言 title 双态文案
        EmptyState: {
          props: ['title', 'desc', 'icon', 'size'],
          template: '<div class="empty-stub">{{ title }}</div>',
        },
        // el-table 桩：只渲染 #empty 插槽（默认插槽内的列 scoped-slot 无作用域会炸）
        'el-table': {
          props: ['data'],
          template: '<div class="table-stub"><slot name="empty" /></div>',
        },
        'el-dialog': true,
        PageHeader: {
          props: ['title', 'subtitle'],
          template: '<div class="ph-stub"><slot /></div>',
        },
        StatCard: {
          props: ['label', 'value', 'icon'],
          template: '<div class="sc-stub" :data-label="label">{{ label }}|{{ value }}</div>',
        },
      },
      directives: { loading: { mounted() {}, updated() {} } },
    },
  })
}

const COURSES = [
  { id: 1, name: '计算机网络', approvalStatus: 'PENDING', status: 'ACTIVE', teacherName: '张老师' },
  { id: 2, name: '数据结构', approvalStatus: 'APPROVED', status: 'INACTIVE', teacherName: '李老师' },
]

describe('CourseManagement 加载失败反馈（D2-1）', () => {
  beforeEach(() => {
    mocks.getAllCourses.mockReset()
  })

  it('加载成功：统计卡显示真实读数，无失败 alert', async () => {
    mocks.getAllCourses.mockResolvedValue(COURSES)
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    const stats = wrapper.findAll('.sc-stub')
    expect(stats[0].text()).toBe('课程总数|2')
    expect(stats[1].text()).toBe('待审核|1')
    expect(stats[2].text()).toBe('已启用|1')
    expect(stats[3].text()).toBe('已停用|1')
  })

  it('接口失败：持久 alert + 统计读数显 -- + 空态文案诚实化', async () => {
    mocks.getAllCourses.mockRejectedValue(new Error('network down'))
    const wrapper = mountPage()
    await flushPromises()

    const alert = wrapper.find('.alert-stub')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toBe('课程列表加载失败，请稍后刷新；已保留上次显示的数据。')

    const stats = wrapper.findAll('.sc-stub')
    expect(stats[0].text()).toBe('课程总数|--')
    expect(stats[1].text()).toBe('待审核|--')

    const empty = wrapper.find('.empty-stub')
    expect(empty.exists()).toBe(true)
    expect(empty.text()).toBe('课程数据未能加载，请查看上方提示后重试')
  })

  it('响应格式无效（非数组）：同样进入失败态，不渲染成空列表', async () => {
    mocks.getAllCourses.mockResolvedValue({ unexpected: true })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.find('.empty-stub').text()).toBe('课程数据未能加载，请查看上方提示后重试')
  })
})
