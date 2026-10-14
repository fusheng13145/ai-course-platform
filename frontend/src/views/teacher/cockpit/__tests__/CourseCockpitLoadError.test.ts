import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

/**
 * CourseCockpit.vue（课程壳头部读数）— 分路加载失败可见（2026-10-14，批次 18）
 *
 * <p>原实现三路读取（详情 / 统计 / 班级维度）各自静默：详情失败静默留白、统计失败用
 * 班级求和兜底但不提示、班级维度失败直接当作「未绑定行政班」。现改为三路独立归因
 * （detailError / statsError / classesError + computed loadError 聚合）+ 头部卡持久 el-alert，
 * 读数失败显 '--'，同时保留 DR-46 约定的「统计不可用但班级可用则按班级求和」。</p>
 */

const mocks = vi.hoisted(() => ({
  route: { params: { id: '4001' }, path: '/teacher/courses/4001/overview' },
  push: vi.fn(),
  getCourseById: vi.fn(),
  getStats: vi.fn(),
  getCourseClasses: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push: mocks.push }),
  useRoute: () => mocks.route,
}))
vi.mock('@/api/course', () => ({
  default: { getCourseById: mocks.getCourseById, getStats: mocks.getStats },
}))
vi.mock('@/api/teacher', () => ({ getCourseClasses: mocks.getCourseClasses }))

import CourseCockpit from '@/views/teacher/cockpit/CourseCockpit.vue'

/** 后端实测形状：课程 4001 —— 2026春、3 学分、15 名学员 */
const DETAIL = {
  data: {
    id: 4001,
    name: 'Python程序设计（A班）',
    code: 'PY-2026SA',
    credits: 3,
    semester: '2026春',
    status: 'ACTIVE',
  },
}

function mountPage() {
  return mount(CourseCockpit, {
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
      },
    },
  })
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.route.path = '/teacher/courses/4001/overview'
  mocks.route.params = { id: '4001' }
  mocks.getCourseById.mockResolvedValue(DETAIL)
  mocks.getStats.mockResolvedValue({ data: { totalStudents: 15 } })
  mocks.getCourseClasses.mockResolvedValue([])
})

describe('CourseCockpit 头部读数 — 分路加载失败可见', () => {
  it('三路全失败：给出持久提示，学生数/班级维度不谎报（显 --）', async () => {
    mocks.getCourseById.mockRejectedValue(new Error('500'))
    mocks.getStats.mockRejectedValue(new Error('500'))
    mocks.getCourseClasses.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(true)
    expect(wrapper.get('.alert-stub').text()).toContain('课程基本信息加载失败，请检查网络后重试')

    const meta = wrapper.get('.small.muted').text()
    expect(meta).toContain('-- 名学生')
    expect(meta).not.toContain('未绑定行政班')
    expect(meta).not.toContain('0 名学生')
    // 班级维度失败：当前班级标签显 '--'
    expect(wrapper.text()).toContain('--')
  })

  it('阴性对照：三路成功时无提示，读数取真实字段（未绑定行政班如实说明）', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.alert-stub').exists()).toBe(false)
    const meta = wrapper.get('.small.muted').text()
    expect(meta).toContain('2026春')
    expect(meta).toContain('3 学分')
    expect(meta).toContain('15 名学生')
    expect(meta).toContain('未绑定行政班')
    expect(wrapper.text()).not.toContain('加载失败')
  })

  it('串味锁定：仅班级维度失败时，提示只归因班级，他路读数照常', async () => {
    mocks.getCourseClasses.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    const alert = wrapper.get('.alert-stub').text()
    expect(alert).toContain('课程班级维度加载失败，请检查网络后重试')
    expect(alert).not.toContain('课程基本信息加载失败')
    expect(alert).not.toContain('课程统计加载失败')

    const meta = wrapper.get('.small.muted').text()
    expect(meta).toContain('2026春')
    expect(meta).toContain('15 名学生')
    expect(meta).not.toContain('未绑定行政班')
  })

  it('串味锁定：仅统计失败但班级维度可用时，按班级求和（DR-46，不显 --、不串味班级提示）', async () => {
    mocks.getStats.mockRejectedValue(new Error('500'))
    mocks.getCourseClasses.mockResolvedValue({
      data: [{ key: 11, name: '计科2301', students: 8, pending: 0, schedule: '' }],
    })
    const wrapper = mountPage()
    await flushPromises()

    const alert = wrapper.get('.alert-stub').text()
    expect(alert).toContain('课程统计加载失败，请检查网络后重试')
    expect(alert).not.toContain('课程基本信息加载失败')
    expect(alert).not.toContain('课程班级维度加载失败')

    const meta = wrapper.get('.small.muted').text()
    expect(meta).toContain('8 名学生')
    expect(meta).not.toContain('-- 名学生')
    expect(wrapper.text()).toContain('共 1 个班级')
    expect(wrapper.text()).toContain('当前班级：计科2301')
  })

  it('串味锁定：仅详情失败时提示只归因详情，统计与班级读数照常', async () => {
    mocks.getCourseById.mockRejectedValue(new Error('500'))
    const wrapper = mountPage()
    await flushPromises()

    const alert = wrapper.get('.alert-stub').text()
    expect(alert).toContain('课程基本信息加载失败，请检查网络后重试')
    expect(alert).not.toContain('课程统计加载失败')
    expect(alert).not.toContain('课程班级维度加载失败')

    expect(wrapper.text()).toContain('加载中…')
    expect(wrapper.text()).toContain('未同步')
    const meta = wrapper.get('.small.muted').text()
    expect(meta).toContain('--')
    expect(meta).toContain('15 名学生')
    expect(meta).toContain('未绑定行政班')
  })

  it('失败后再成功：提示必须被清空（每次挂载复位三路错误）', async () => {
    mocks.getCourseById.mockRejectedValueOnce(new Error('500'))
    mocks.getStats.mockRejectedValueOnce(new Error('500'))
    mocks.getCourseClasses.mockRejectedValueOnce(new Error('500'))

    const first = mountPage()
    await flushPromises()
    expect(first.find('.alert-stub').exists()).toBe(true)

    const second = mountPage()
    await flushPromises()

    expect(second.find('.alert-stub').exists()).toBe(false)
    expect(second.get('.small.muted').text()).toContain('15 名学生')
  })
})
