import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'

/**
 * A 条结局①（接真）钉桩：学生「加入课程」不再是演示 toast，而是真选课写口。
 *
 * 后端契约实测（StudentController.enrollCourse）：
 *   POST /student/courses/{courseId}/enroll —— courseId 走【路径参数】、【无请求体】；
 *   服务层 enrollStudent 落库 is_active=true，而 GET /student/courses 只按 is_active=true 过滤，
 *   因此点完按钮后该读数里真的出现这门课。
 * 本用例用 mock 复刻这一写→读语义，并断言前端确实按契约调用（不照旧页面猜字段）：
 *   - enrollCourse 收到的必须是「数字 courseId」，而不是 { courseId } 这类猜出来的请求体；
 *   - 报名成功后 GET /student/courses 读数含该课；页面课程卡刷新出现该课。
 */

// 单一共享「后端态」：enrollments 等价于 course_enrollments 里 is_active=true 的行
const store = vi.hoisted(() => ({ enrollments: [] as number[] }))

const studentApi = vi.hoisted(() => ({
  getStudentDashboard: vi.fn(),
  getMyCourses: vi.fn(),
  enrollCourse: vi.fn(),
  unenrollCourse: vi.fn(),
}))
// 只接管视图真正用到的两个数据面，避免把整条 @/api 依赖图拉进单测
const dataApi = vi.hoisted(() => ({
  getCourses: vi.fn(),
  getCourseCatalog: vi.fn(),
}))
const router = vi.hoisted(() => ({ push: vi.fn() }))

const uiState = vi.hoisted(() => ({
  toast: vi.fn(),
  role: null as unknown as { value: string },
}))

vi.mock('@/api/student', () => ({ default: studentApi }))
vi.mock('../data/api', () => dataApi)
vi.mock('vue-router', () => ({ useRouter: () => ({ push: router.push }) }))
vi.mock('../composables/ui', async () => {
  const { ref } = await import('vue')
  uiState.role = ref('student')
  return { useUi: () => ({ toast: uiState.toast, role: uiState.role }) }
})

import CoursesView from '../views/CoursesView.vue'
import type { CourseCard } from '../data/api'

// 课程目录（GET /courses 的可加入候选源）
const CATALOG = [
  { id: 4007, name: '计算机网络' },
  { id: 4008, name: '数据结构' },
  { id: 4009, name: '操作系统' },
]

function nameOf(id: number): string {
  return CATALOG.find((course) => course.id === id)?.name || `课程 #${id}`
}

function card(id: number): CourseCard {
  return {
    id,
    name: nameOf(id),
    code: `CS-${id}`,
    teacher: '张老师',
    semester: '2026 秋',
    progress: 0,
    students: 0,
    pending: 0,
    status: 'ACTIVE',
    short: nameOf(id).slice(0, 2),
    tone: 1,
  }
}

const envelope = <T>(data: T) => ({ code: 200, message: 'ok', data })

async function settle() {
  await flushPromises()
  await flushPromises()
  await flushPromises()
}

function mountCourses(): VueWrapper {
  return mount(CoursesView)
}

function primaryButton(wrapper: VueWrapper) {
  return wrapper.findAll('.page-head-actions button').find((node) => node.text() === '加入课程')
}

function joinSelect(wrapper: VueWrapper) {
  return wrapper.find('select.join-course-select')
}

beforeEach(() => {
  vi.clearAllMocks()
  store.enrollments = [4008] // 已选「数据结构」，可加入候选应排除它

  uiState.role.value = 'student'

  // GET /student/courses：只按 is_active=true 过滤（等价于读 store.enrollments）
  studentApi.getMyCourses.mockImplementation(() =>
    Promise.resolve(
      envelope({
        records: store.enrollments.map((id) => ({ id, name: nameOf(id) })),
        total: store.enrollments.length,
      }),
    ),
  )
  // POST /student/courses/{courseId}/enroll：服务层落库 is_active=true
  studentApi.enrollCourse.mockImplementation((courseId: number) => {
    store.enrollments = [...store.enrollments, courseId]
    return Promise.resolve(envelope(null))
  })
  // DELETE /student/courses/{courseId}/enroll：服务层置 is_active=false
  studentApi.unenrollCourse.mockImplementation((courseId: number) => {
    store.enrollments = store.enrollments.filter((id) => id !== courseId)
    return Promise.resolve(envelope(null))
  })
  studentApi.getStudentDashboard.mockResolvedValue(envelope({ courses: [] }))

  dataApi.getCourses.mockImplementation((role: string) =>
    Promise.resolve(role === 'student' ? store.enrollments.map(card) : []),
  )
  dataApi.getCourseCatalog.mockImplementation(() => Promise.resolve(CATALOG.map((c) => card(c.id))))
})

describe('学生「加入课程」= 真实选课写口（非演示动作）', () => {
  it('点完按钮：按路径参数契约报名，且 GET /student/courses 读数出现这门课', async () => {
    const wrapper = mountCourses()
    await settle()

    // 已选的 4008 不在可加入候选里，4007/4009 在
    const options = joinSelect(wrapper)
      .findAll('option')
      .map((node) => node.text())
    expect(options).toContain('计算机网络')
    expect(options).toContain('操作系统')
    expect(options).not.toContain('数据结构')

    await joinSelect(wrapper).setValue(4007)
    await primaryButton(wrapper)!.trigger('click')
    await settle()

    // 契约钉子：enrollCourse 收到的必须是数字 courseId（走路径参数），而非猜出来的请求体
    expect(studentApi.enrollCourse).toHaveBeenCalledTimes(1)
    const arg = studentApi.enrollCourse.mock.calls[0][0]
    expect(arg).toBe(4007)
    expect(typeof arg, 'courseId 必须是路径参数用的数字，而不是 { courseId } 对象').toBe('number')

    // 写→读语义：GET /student/courses 读数里真的出现这门课
    const read = await studentApi.getMyCourses()
    const ids = (read.data.records as Array<{ id: number }>).map((row) => row.id)
    expect(ids).toContain(4007)

    // 页面课程卡刷新出现该课，且给的是成功提示而不是演示 toast
    expect(wrapper.text()).toContain('计算机网络')
    expect(uiState.toast).toHaveBeenCalledWith('已加入课程', 'ok')
    expect(
      uiState.toast.mock.calls.some(([msg]) => /演示数据|尚未接入/.test(String(msg))),
      '学生「加入课程」不得再走演示 toast',
    ).toBe(false)

    wrapper.unmount()
  })

  it('未选择课程时不发起任何请求，只给真实校验提示（不是演示动作）', async () => {
    const wrapper = mountCourses()
    await settle()

    await primaryButton(wrapper)!.trigger('click')
    await settle()

    expect(studentApi.enrollCourse).not.toHaveBeenCalled()
    expect(uiState.toast).toHaveBeenCalledWith('请先选择要加入的课程', 'warn')
    expect(uiState.toast.mock.calls.some(([msg]) => /演示数据|尚未接入/.test(String(msg)))).toBe(
      false,
    )

    wrapper.unmount()
  })

  it('后端拒绝时如实报错：不谎报成功、读数不变、更不回落演示 toast', async () => {
    studentApi.enrollCourse.mockImplementationOnce(() =>
      Promise.reject(new Error('403 课程未通过审核')),
    )

    const wrapper = mountCourses()
    await settle()

    await joinSelect(wrapper).setValue(4009)
    await primaryButton(wrapper)!.trigger('click')
    await settle()

    expect(uiState.toast).toHaveBeenCalledWith(expect.stringContaining('加入课程失败'), 'warn')
    expect(uiState.toast).not.toHaveBeenCalledWith('已加入课程', 'ok')
    expect(uiState.toast.mock.calls.some(([msg]) => /演示数据|尚未接入/.test(String(msg)))).toBe(
      false,
    )

    const read = await studentApi.getMyCourses()
    const ids = (read.data.records as Array<{ id: number }>).map((row) => row.id)
    expect(ids).not.toContain(4009)

    wrapper.unmount()
  })

  it('课程列表不再保留只弹演示 toast 的筛选/导出动作', async () => {
    uiState.role.value = 'admin'
    const wrapper = mountCourses()
    await settle()

    const labels = wrapper.findAll('.page-head-actions button').map((node) => node.text())
    expect(labels).not.toContain('筛选')
    expect(labels).not.toContain('导出课程')
    wrapper.unmount()
  })

  it('教师端主按钮仍跳创建课程，学生选课写口不会被误调', async () => {
    uiState.role.value = 'teacher'
    const wrapper = mountCourses()
    await settle()

    const create = wrapper
      .findAll('.page-head-actions button')
      .find((node) => node.text() === '新建课程')
    await create!.trigger('click')

    expect(router.push).toHaveBeenCalledWith('/teacher/courses/create')
    expect(studentApi.enrollCourse).not.toHaveBeenCalled()
    expect(joinSelect(wrapper).exists(), '教师端不应出现学生选课控件').toBe(false)

    wrapper.unmount()
  })
})

/** 弹层经 Teleport 挂到 body，需从 document 查询确认钮。 */
function modalButton(text: string): HTMLButtonElement | undefined {
  return Array.from(document.querySelectorAll('.modal-panel button')).find(
    (node) => node.textContent?.trim() === text,
  ) as HTMLButtonElement | undefined
}

function dropButton(wrapper: VueWrapper) {
  return wrapper.findAll('.course-tile-foot button').find((node) => node.text() === '退选')
}

function tileNames(wrapper: VueWrapper): string[] {
  return wrapper.findAll('.course-tile h3').map((node) => node.text())
}

describe('学生「退选课程」= 真实退选写口（二次确认）', () => {
  it('二次确认后按路径参数退选，GET /student/courses 读数移除该课', async () => {
    const wrapper = mountCourses()
    await settle()

    expect(tileNames(wrapper)).toContain('数据结构')
    const btn = dropButton(wrapper)
    expect(btn, '学生课程卡应有「退选」入口').toBeTruthy()
    await btn!.trigger('click')
    await settle()

    // 未确认前不发写请求
    expect(studentApi.unenrollCourse).not.toHaveBeenCalled()
    expect(modalButton('确定退选'), '应弹出二次确认').toBeTruthy()

    modalButton('确定退选')!.click()
    await settle()

    expect(studentApi.unenrollCourse).toHaveBeenCalledTimes(1)
    // 契约钉子：路径参数用的是数字 courseId，而非请求体
    expect(studentApi.unenrollCourse.mock.calls[0][0]).toBe(4008)
    expect(uiState.toast).toHaveBeenCalledWith('已退选课程', 'ok')

    // 写→读语义：GET /student/courses 读数不再含该课，课程卡消失
    const read = await studentApi.getMyCourses()
    const ids = (read.data.records as Array<{ id: number }>).map((row) => row.id)
    expect(ids).not.toContain(4008)
    expect(tileNames(wrapper)).not.toContain('数据结构')

    wrapper.unmount()
  })

  it('取消后不发请求，课程仍在「我的课程」', async () => {
    const wrapper = mountCourses()
    await settle()

    await dropButton(wrapper)!.trigger('click')
    await settle()

    modalButton('取消')!.click()
    await settle()

    expect(studentApi.unenrollCourse).not.toHaveBeenCalled()
    expect(tileNames(wrapper)).toContain('数据结构')

    wrapper.unmount()
  })

  it('后端拒绝时如实报错，不谎报成功、读数不变', async () => {
    studentApi.unenrollCourse.mockImplementationOnce(() =>
      Promise.reject(new Error('403 课程不可退选')),
    )

    const wrapper = mountCourses()
    await settle()

    await dropButton(wrapper)!.trigger('click')
    await settle()
    modalButton('确定退选')!.click()
    await settle()

    expect(uiState.toast).toHaveBeenCalledWith('退选失败，请稍后重试', 'warn')
    expect(uiState.toast).not.toHaveBeenCalledWith('已退选课程', 'ok')
    expect(tileNames(wrapper)).toContain('数据结构')

    wrapper.unmount()
  })
})
