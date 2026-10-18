import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CourseShell.vue（学生课程壳）— 加载失败可见（2026-10-14，批次 18）
 *
 * <p>原实现 loadCourse 失败仅把 courseName 清空：页头静默退化成「课程详情」占位，
 * 学生看不出是接口失败还是课程确无名称。现补 loadError + 页头持久 el-alert。</p>
 */

const mocks = vi.hoisted(() => ({
  getCourseById: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { id: '5' }, path: '/student/courses/5/overview' }),
  useRouter: () => ({ push: vi.fn() }),
}))

vi.mock('@/api/course', () => ({ default: { getCourseById: mocks.getCourseById } }))

import CourseShell from '@/views/student/course/CourseShell.vue'

function mountPage() {
  return mount(CourseShell, {
    shallow: true,
    global: {
      stubs: {
        'el-alert': {
          props: ['title', 'type'],
          template: '<div class="alert-stub">{{ title }}</div>',
        },
        'router-link': true,
        'router-view': true,
        'el-icon': true,
        'el-button': true,
        'el-tag': { props: ['type', 'effect'], template: '<span class="tag-stub"><slot /></span>' },
      },
    },
  })
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.getCourseById.mockResolvedValue({
    data: { name: '数据结构', teacherName: '王老师', progress: 40 },
  })
})

describe('CourseShell 学生课程壳 — 加载失败可见', () => {
  it('加载失败：给出持久提示，页头退回占位而非谎报数据', async () => {
    mocks.getCourseById.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.get('.alert-stub').text()).toContain('课程信息加载失败，请检查网络后重试')
    expect(wrapper.get('.shell-title h2').text()).toBe('课程详情')
    expect(wrapper.find('.shell-sub').exists()).toBe(false)
    // 进度未知不进度假报：标签不渲染
    expect(wrapper.text()).not.toContain('已完成')
  })

  it('阴性对照：加载成功时无提示，页头取真实课程名/教师/进度', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    expect(wrapper.get('.shell-title h2').text()).toBe('数据结构')
    expect(wrapper.get('.shell-sub').text()).toBe('王老师')
    expect(wrapper.text()).toContain('40% 已完成')
  })

  it('失败后再成功：提示必须被清空（每次 loadCourse 复位）', async () => {
    mocks.getCourseById.mockRejectedValueOnce(new Error('500'))
    const first = mountPage()
    await flushPromises()
    expect(first.find('.alert-stub').exists()).toBe(true)

    const second = mountPage()
    await flushPromises()

    expect(second.find('.alert-stub').exists()).toBe(false)
    expect(second.get('.shell-title h2').text()).toBe('数据结构')
  })
})
