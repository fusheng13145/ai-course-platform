/**
 * 驾驶舱壳层（CourseCockpit）头部读数测试（DR-46）。
 *
 * 钉住两类曾经出现的伪造读数：
 *   1. 学期/学分/班级数/学生数写死在模板里（`2025-2026-2 · 4 学分 …`），
 *      与后端 /courses/{id}、/courses/{id}/stats 的真实字段无关；
 *   2. 课程未绑定行政班时伪造出一行「全部班级 · 0 人」，
 *      把 15 名真实学员显示成 0。
 */
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { COURSE_TABS } from '@/layouts/menus'

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

vi.mock('@/api/teacher', () => ({
  getCourseClasses: mocks.getCourseClasses,
}))

const CourseCockpit = (await import('../CourseCockpit.vue')).default

/** 后端实测形状：课程 4001（Python 程序设计 A 班）——2026春、3 学分、15 名学员、未绑定行政班 */
const COURSE_DETAIL = {
  code: 200,
  data: {
    id: 4001,
    name: 'Python程序设计（A班）',
    code: 'PY-2026SA',
    credits: 3,
    semester: '2026春',
    status: 'INACTIVE',
  },
}

function mountCockpit() {
  return mount(CourseCockpit, {
    global: { stubs: { 'router-view': true, 'router-link': true } },
  })
}

/** 需要断言返回链接 to 时用的挂载器：把 router-link 渲染成带 data-to 的 a */
function mountCockpitAt(path: string, id: string) {
  mocks.route.path = path
  mocks.route.params = { id }
  return mount(CourseCockpit, {
    global: {
      stubs: {
        'router-view': true,
        'router-link': { props: ['to'], template: '<a class="rl" :data-to="to"><slot /></a>' },
      },
    },
  })
}

describe('CourseCockpit 头部读数', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getCourseById.mockResolvedValue(COURSE_DETAIL)
    mocks.getStats.mockResolvedValue({ code: 200, data: { totalStudents: 15 } })
    mocks.getCourseClasses.mockResolvedValue([])
  })

  it('副标题用后端真实字段，不再残留硬编码学期/学分', async () => {
    const wrapper = await mountCockpit()
    await flushPromises()

    const meta = wrapper.get('.small.muted').text()
    expect(meta).toContain('2026春')
    expect(meta).toContain('3 学分')
    expect(meta).toContain('15 名学生')
    expect(meta).not.toContain('2025-2026-2')
    expect(meta).not.toContain('4 学分')
  })

  it('课程名/编码取真实数据，不再显示原型稿的「计算机网络 CS3101」', async () => {
    const wrapper = await mountCockpit()
    await flushPromises()

    expect(wrapper.text()).toContain('Python程序设计（A班）')
    expect(wrapper.text()).toContain('PY-2026SA')
    expect(wrapper.text()).not.toContain('计算机网络')
    expect(wrapper.text()).not.toContain('CS3101')
  })

  it('状态标签跟随真实 status，已结束课程不再谎报「进行中」', async () => {
    const wrapper = await mountCockpit()
    await flushPromises()

    expect(wrapper.text()).toContain('已停用')
    expect(wrapper.text()).not.toContain('进行中')
  })

  it('未绑定行政班时不伪造「全部班级 0 人」页签，如实说明未绑定', async () => {
    const wrapper = await mountCockpit()
    await flushPromises()

    expect(wrapper.find('.tabs-line').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('全部班级')
    expect(wrapper.text()).toContain('未绑定行政班')
    expect(wrapper.text()).toContain('15 名学生')
  })

  it('绑定行政班时按班级渲染页签与班级数', async () => {
    mocks.getCourseClasses.mockResolvedValue({
      data: [
        { key: 11, name: '计科2301', students: 8, pending: 2, schedule: '' },
        { key: 12, name: '计科2302', students: 7, pending: 0, schedule: '' },
      ],
    })

    const wrapper = await mountCockpit()
    await flushPromises()

    const tabs = wrapper.findAll('.tabs-line .tab-i')
    expect(tabs).toHaveLength(2)
    expect(tabs[0].text()).toContain('计科2301')
    expect(wrapper.text()).toContain('共 2 个班级')
    expect(wrapper.text()).toContain('当前班级：计科2301')
  })

  it('报名统计接口不可用时回退班级求和，而不是报 0', async () => {
    mocks.getStats.mockRejectedValue(new Error('network'))
    mocks.getCourseClasses.mockResolvedValue({
      data: [{ key: 11, name: '计科2301', students: 8, pending: 0, schedule: '' }],
    })

    const wrapper = await mountCockpit()
    await flushPromises()

    expect(wrapper.text()).toContain('8 名学生')
  })

  it('详情接口失败时头部进入未同步态，不显示原型稿假数据', async () => {
    mocks.getCourseById.mockRejectedValue(new Error('network'))

    const wrapper = await mountCockpit()
    await flushPromises()

    expect(wrapper.text()).toContain('加载中…')
    expect(wrapper.text()).toContain('未同步')
    expect(wrapper.text()).not.toContain('计算机网络')
  })

  describe('模块页签（DR-47：与侧边栏三级页签同源，且点击必跳转）', () => {
    it('页签文案直接取 COURSE_TABS.TEACHER，不再各写一份', async () => {
      const wrapper = await mountCockpit()
      await flushPromises()

      const labels = wrapper.findAll('.tabs-pill .tab-i').map((n) => n.text())
      expect(labels).toEqual(COURSE_TABS.TEACHER.map((t) => t.text))
      // submissions 页曾被误标成「成员管理」，实际指向作业批改页
      expect(labels).not.toContain('成员管理')
    })

    it('点击每个页签都 push 到对应子路由', async () => {
      const wrapper = await mountCockpit()
      await flushPromises()

      const pills = wrapper.findAll('.tabs-pill .tab-i')
      expect(pills).toHaveLength(COURSE_TABS.TEACHER.length)
      for (const [i, tab] of COURSE_TABS.TEACHER.entries()) {
        await pills[i].trigger('click')
        expect(mocks.push, `页签 ${tab.suffix} 应跳转`).toHaveBeenCalledWith(
          `/teacher/courses/4001/${tab.suffix}`,
        )
      }
    })
  })
})

describe('CourseCockpit 角色前缀推导（DR-48：同一壳服务 TEACHER / ADMIN）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.getCourseById.mockResolvedValue(COURSE_DETAIL)
    mocks.getStats.mockResolvedValue({ code: 200, data: { totalStudents: 15 } })
    mocks.getCourseClasses.mockResolvedValue([])
  })

  it('教师端前缀下返回链接指向 /teacher/courses', async () => {
    const wrapper = await mountCockpitAt('/teacher/courses/4001/overview', '4001')
    await flushPromises()
    expect(wrapper.get('a.rl').attributes('data-to')).toBe('/teacher/courses')
  })

  it('管理端前缀下返回链接与页签跳转都走 /admin，不串回教师端', async () => {
    const wrapper = await mountCockpitAt('/admin/courses/4007/chapters', '4007')
    await flushPromises()

    expect(wrapper.get('a.rl').attributes('data-to')).toBe('/admin/courses')

    const pills = wrapper.findAll('.tabs-pill .tab-i')
    expect(pills.map((n) => n.text())).toEqual(COURSE_TABS.TEACHER.map((t) => t.text))
    await pills[0].trigger('click')
    expect(mocks.push).toHaveBeenCalledWith('/admin/courses/4007/overview')
  })
})
