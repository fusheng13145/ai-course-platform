import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

/** 五个治理页共享的接口 mock：每个模块只暴露页面真实调用的具名导出。 */
const adminApi = vi.hoisted(() => ({
  getUsers: vi.fn(),
  getSemesters: vi.fn(),
  createSemester: vi.fn(),
  archiveSemester: vi.fn(),
}))

vi.mock('@/api/admin', () => ({ ...adminApi, default: adminApi }))

const userApi = vi.hoisted(() => ({ updateUserStatus: vi.fn() }))
vi.mock('@/api/user', () => ({ ...userApi, default: userApi }))

const permissionApi = vi.hoisted(() => ({
  getAllPermissions: vi.fn(),
  createPermission: vi.fn(),
}))
vi.mock('@/api/permission', () => ({ ...permissionApi, default: permissionApi }))

const classroomApi = vi.hoisted(() => ({
  getClassroomList: vi.fn(),
  createClassroom: vi.fn(),
  updateClassroom: vi.fn(),
  deleteClassroom: vi.fn(),
}))
vi.mock('@/api/classroom', () => ({ ...classroomApi, default: classroomApi }))

const courseApi = vi.hoisted(() => ({
  getAllCourses: vi.fn(),
  approveCourse: vi.fn(),
  rejectCourse: vi.fn(),
}))
vi.mock('@/api/course', () => ({ ...courseApi, default: courseApi }))

const toast = vi.hoisted(() => vi.fn())
vi.mock('../composables/ui', () => ({
  useUi: () => ({ toast }),
}))

import UsersBody from '../views/specials/UsersBody.vue'
import RolesBody from '../views/specials/RolesBody.vue'
import SemestersBody from '../views/specials/SemestersBody.vue'
import AdminClassesBody from '../views/specials/AdminClassesBody.vue'
import ApprovalsBody from '../views/specials/ApprovalsBody.vue'

/** Modal 走 Teleport，测试里换成同 slot 结构的存根，确认按钮仍能被点到。 */
const stubs = {
  Modal: {
    props: ['open', 'title'],
    template: '<div v-if="open" class="modal-stub"><slot /><slot name="footer" /></div>',
  },
}

const envelope = (data: unknown) => ({ code: 200, message: 'ok', data })
const pageOf = (records: Record<string, unknown>[], total = records.length) =>
  envelope({ records, total })

const USERS = [
  {
    id: 11,
    username: 'zhangsan',
    fullName: '张三',
    email: 'zhangsan@example.com',
    phone: '13800000000',
    role: 'TEACHER',
    isActive: true,
  },
  {
    id: 12,
    username: 'lisi',
    fullName: '李四',
    email: 'lisi@example.com',
    phone: '13900000000',
    role: 'STUDENT',
    isActive: false,
  },
]

const PERMISSIONS = [
  {
    id: 1,
    role: 'ADMIN',
    permissionKey: 'user:manage',
    permissionName: '用户管理',
    permissionType: 'MENU',
    resourcePath: '/users',
    parentId: 0,
    sortOrder: 1,
    isActive: true,
  },
  {
    id: 2,
    role: 'ADMIN',
    permissionKey: 'course:approve',
    permissionName: '课程审批',
    permissionType: 'MENU',
    parentId: 0,
    sortOrder: 2,
    isActive: true,
  },
  {
    id: 3,
    role: 'TEACHER',
    permissionKey: 'course:own',
    permissionName: '我的课程',
    permissionType: 'MENU',
    parentId: 0,
    sortOrder: 1,
    isActive: true,
  },
]

const SEMESTERS = [
  {
    id: 1,
    name: '2026-2027-1',
    startDate: '2026-09-01',
    endDate: '2027-01-15',
    status: 'ACTIVE',
    archivedAt: null,
  },
  {
    id: 2,
    name: '2025-2026-2',
    startDate: '2026-02-20',
    endDate: '2026-07-01',
    status: 'ARCHIVED',
    archivedAt: '2026-07-02T10:30:00',
  },
]

const CLASSES = [
  {
    id: 1,
    name: '计算机2201班',
    code: 'CS2201',
    major: '计算机科学与技术',
    gradeLevel: '2022',
    advisorId: 9,
    studentCount: 32,
    maxStudents: 50,
    isActive: true,
    description: '行政班',
  },
]

const COURSES = [
  {
    id: 101,
    name: '数据结构',
    code: 'CS201',
    category: '专业必修',
    semester: '2026-2027-1',
    teacherId: 7,
    credits: 4,
    approvalStatus: 'PENDING',
    createdAt: '2026-10-01T09:00:00',
  },
  {
    id: 102,
    name: '操作系统',
    approvalStatus: 'APPROVED',
    teacherId: 7,
  },
  {
    id: 103,
    name: '编译原理',
    approvalStatus: 'REJECTED',
    teacherId: 8,
  },
]

const mountOptions = { global: { stubs } }

beforeEach(() => {
  vi.clearAllMocks()
  adminApi.getUsers.mockResolvedValue(pageOf(USERS, 2))
  adminApi.getSemesters.mockResolvedValue(envelope(SEMESTERS))
  adminApi.createSemester.mockResolvedValue({ code: 200, data: {} })
  adminApi.archiveSemester.mockResolvedValue({ code: 200, data: {} })
  userApi.updateUserStatus.mockResolvedValue({ code: 200, data: null })
  permissionApi.getAllPermissions.mockResolvedValue(envelope(PERMISSIONS))
  permissionApi.createPermission.mockResolvedValue({ code: 200, data: {} })
  classroomApi.getClassroomList.mockResolvedValue(envelope(CLASSES))
  classroomApi.createClassroom.mockResolvedValue({ code: 200, data: {} })
  classroomApi.updateClassroom.mockResolvedValue({ code: 200, data: {} })
  classroomApi.deleteClassroom.mockResolvedValue({ code: 200, data: {} })
  courseApi.getAllCourses.mockResolvedValue(COURSES)
  courseApi.approveCourse.mockResolvedValue({ code: 200, data: {} })
  courseApi.rejectCourse.mockResolvedValue({ code: 200, data: {} })
})

function mountUsers() {
  return mount(UsersBody, mountOptions)
}

function mountRoles() {
  return mount(RolesBody, mountOptions)
}

function mountSemesters() {
  return mount(SemestersBody, mountOptions)
}

function mountClasses() {
  return mount(AdminClassesBody, mountOptions)
}

function mountApprovals() {
  return mount(ApprovalsBody, mountOptions)
}

function footerButtons(wrapper: ReturnType<typeof mount>) {
  return wrapper.find('.modal-stub').findAll('button')
}

describe('管理员 / 用户与账号', () => {
  it('列表渲染接口数据，并支持角色筛选与关键词查询', async () => {
    const wrapper = mountUsers()
    await flushPromises()

    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('张三')
    expect(rows[0].text()).toContain('教师')
    expect(rows[0].text()).toContain('启用')
    expect(rows[1].text()).toContain('停用')
    expect(wrapper.text()).not.toContain('示例数据')
    expect(adminApi.getUsers).toHaveBeenCalledWith(expect.objectContaining({ page: 1, size: 10 }))

    await wrapper.find('select.role-select').setValue('TEACHER')
    await flushPromises()
    expect(adminApi.getUsers).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1, role: 'TEACHER' }),
    )

    await wrapper.find('input.search-input').setValue('张')
    await wrapper.find('form.search-form').trigger('submit')
    await flushPromises()
    expect(adminApi.getUsers).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1, role: 'TEACHER', keyword: '张' }),
    )
    wrapper.unmount()
  })

  it('停用账号先二次确认，确认后才调用 PATCH /users/{id}/status 并刷新', async () => {
    const wrapper = mountUsers()
    await flushPromises()

    await wrapper.findAll('tbody tr')[0].find('.row-actions button').trigger('click')
    await flushPromises()

    expect(wrapper.find('.modal-stub').text()).toContain('张三')
    expect(userApi.updateUserStatus).not.toHaveBeenCalled()

    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(userApi.updateUserStatus).toHaveBeenCalledWith(11, false)
    expect(adminApi.getUsers).toHaveBeenCalledTimes(2)
    expect(toast).toHaveBeenCalledWith('账号已停用', 'ok')
    wrapper.unmount()
  })

  it('启用停用账号无需确认，直接调用状态端点', async () => {
    const wrapper = mountUsers()
    await flushPromises()

    await wrapper.findAll('tbody tr')[1].find('.row-actions button').trigger('click')
    await flushPromises()

    expect(userApi.updateUserStatus).toHaveBeenCalledWith(12, true)
    expect(wrapper.find('.modal-stub').exists()).toBe(false)
    expect(adminApi.getUsers).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('加载失败与空态可区分，重试成功后落到空态', async () => {
    adminApi.getUsers.mockRejectedValueOnce(new Error('500'))
    const wrapper = mountUsers()
    await flushPromises()

    expect(wrapper.find('.callout').text()).toContain('用户列表加载失败')
    expect(wrapper.text()).toContain('无法确认用户数据')
    expect(wrapper.text()).not.toContain('暂无用户')
    expect(toast).toHaveBeenCalledWith('用户列表加载失败', 'danger')

    adminApi.getUsers.mockResolvedValueOnce(pageOf([], 0))
    await wrapper.find('.callout button').trigger('click')
    await flushPromises()

    expect(wrapper.find('.callout').exists()).toBe(false)
    expect(wrapper.text()).toContain('暂无用户')
    wrapper.unmount()
  })
})

describe('管理员 / 角色与权限', () => {
  it('角色列表按 role_permissions 统计，切换角色只显示该角色的真实权限字段', async () => {
    const wrapper = mountRoles()
    await flushPromises()

    const roles = wrapper.findAll('.role-item')
    expect(roles).toHaveLength(3)
    expect(roles[0].text()).toContain('管理员')
    expect(roles[0].text()).toContain('2 项')

    let rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('用户管理')
    expect(rows[0].text()).toContain('user:manage')
    expect(rows[0].text()).toContain('/users')
    expect(wrapper.text()).not.toContain('menuKey')
    expect(wrapper.text()).not.toContain('buttonKey')

    await roles[1].trigger('click')
    rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(1)
    expect(rows[0].text()).toContain('我的课程')
    expect(permissionApi.getAllPermissions).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('分配权限调用 POST /admin/permissions/create，成功后重新拉取权限', async () => {
    const wrapper = mountRoles()
    await flushPromises()

    await wrapper.find('.card-head button.btn-sm').trigger('click')
    await wrapper.find('[data-testid="role-assign-name"]').setValue('考试审计')
    await wrapper.find('[data-testid="role-assign-key"]').setValue('exam:audit')

    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(permissionApi.createPermission).toHaveBeenCalledWith(
      expect.objectContaining({
        role: 'ADMIN',
        permissionName: '考试审计',
        permissionKey: 'exam:audit',
        permissionType: 'MENU',
        isActive: true,
      }),
    )
    expect(permissionApi.getAllPermissions).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.modal-stub').exists()).toBe(false)
    expect(toast).toHaveBeenCalledWith('权限已分配', 'ok')
    wrapper.unmount()
  })

  it('分配失败保留弹窗并展示后端原因', async () => {
    permissionApi.createPermission.mockRejectedValueOnce({
      response: { data: { message: '该角色已存在相同权限 Key' } },
    })
    const wrapper = mountRoles()
    await flushPromises()

    await wrapper.find('.card-head button.btn-sm').trigger('click')
    await wrapper.find('[data-testid="role-assign-name"]').setValue('考试审计')
    await wrapper.find('[data-testid="role-assign-key"]').setValue('exam:audit')
    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(wrapper.find('.modal-stub').exists()).toBe(true)
    expect(wrapper.find('.modal-stub').text()).toContain('该角色已存在相同权限 Key')
    expect(permissionApi.getAllPermissions).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('权限列表加载失败时显示失败态而非空角色权限', async () => {
    permissionApi.getAllPermissions.mockRejectedValueOnce(new Error('500'))
    const wrapper = mountRoles()
    await flushPromises()

    expect(wrapper.find('.callout').text()).toContain('权限列表加载失败')
    expect(wrapper.text()).toContain('无法确认权限数据')
    expect(wrapper.text()).not.toContain('该角色暂无权限配置')
    expect(toast).toHaveBeenCalledWith('权限列表加载失败', 'danger')
    wrapper.unmount()
  })
})

describe('管理员 / 学期管理', () => {
  it('列表渲染学期、状态与归档时间', async () => {
    const wrapper = mountSemesters()
    await flushPromises()

    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('2026-2027-1')
    expect(rows[0].text()).toContain('在册')
    expect(rows[1].text()).toContain('已归档')
    expect(rows[1].text()).toContain('2026-07-02 10:30')
    expect(wrapper.text()).not.toContain('示例数据')
    expect(adminApi.getSemesters).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('新建学期调用真实创建端点，成功后关闭弹窗并刷新', async () => {
    const wrapper = mountSemesters()
    await flushPromises()

    await wrapper.find('button.btn-primary').trigger('click')
    await wrapper.find('[data-testid="semester-name"]').setValue('2027-2028-1')
    await wrapper.find('[data-testid="semester-start"]').setValue('2027-09-01')
    await wrapper.find('[data-testid="semester-end"]').setValue('2028-01-10')
    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(adminApi.createSemester).toHaveBeenCalledWith({
      name: '2027-2028-1',
      startDate: '2027-09-01',
      endDate: '2028-01-10',
    })
    expect(adminApi.getSemesters).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.modal-stub').exists()).toBe(false)
    expect(toast).toHaveBeenCalledWith('学期创建成功', 'ok')
    wrapper.unmount()
  })

  it('创建失败保留弹窗与输入，并展示后端原因', async () => {
    adminApi.createSemester.mockRejectedValueOnce({
      response: { data: { message: '学期名称已存在' } },
    })
    const wrapper = mountSemesters()
    await flushPromises()

    await wrapper.find('button.btn-primary').trigger('click')
    await wrapper.find('[data-testid="semester-name"]').setValue('2026-2027-1')
    await wrapper.find('[data-testid="semester-start"]').setValue('2026-09-01')
    await wrapper.find('[data-testid="semester-end"]').setValue('2027-01-15')
    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(adminApi.createSemester).toHaveBeenCalledTimes(1)
    expect(wrapper.find('.modal-stub').exists()).toBe(true)
    expect(wrapper.find('.modal-stub').text()).toContain('学期名称已存在')
    expect((wrapper.find('[data-testid="semester-name"]').element as HTMLInputElement).value).toBe(
      '2026-2027-1',
    )
    wrapper.unmount()
  })

  it('归档前二次确认，确认后调用归档端点并刷新', async () => {
    const wrapper = mountSemesters()
    await flushPromises()

    await wrapper.findAll('tbody tr')[0].find('.row-actions button').trigger('click')
    await flushPromises()
    expect(wrapper.find('.modal-stub').text()).toContain('2026-2027-1')
    expect(adminApi.archiveSemester).not.toHaveBeenCalled()

    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(adminApi.archiveSemester).toHaveBeenCalledWith(1)
    expect(adminApi.getSemesters).toHaveBeenCalledTimes(2)
    expect(toast).toHaveBeenCalledWith('学期已归档', 'ok')
    wrapper.unmount()
  })

  it('列表加载失败与真正无学期可区分', async () => {
    adminApi.getSemesters.mockRejectedValueOnce(new Error('500'))
    const wrapper = mountSemesters()
    await flushPromises()

    expect(wrapper.find('.callout').text()).toContain('学期列表加载失败')
    expect(wrapper.text()).toContain('无法确认学期数据')
    expect(wrapper.text()).not.toContain('尚未注册任何学期')
    expect(toast).toHaveBeenCalledWith('学期列表加载失败', 'danger')
    wrapper.unmount()
  })
})

describe('管理员 / 行政班', () => {
  it('列表按 classrooms 真实字段渲染，不混入学院或教学班列', async () => {
    const wrapper = mountClasses()
    await flushPromises()

    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(1)
    expect(rows[0].text()).toContain('计算机2201班')
    expect(rows[0].text()).toContain('计算机科学与技术')
    expect(rows[0].text()).toContain('2022')
    expect(rows[0].text()).toContain('32 / 50')
    expect(rows[0].text()).toContain('教师 #9')

    const headers = wrapper.find('thead').text()
    expect(headers).toContain('专业')
    expect(headers).toContain('年级')
    expect(headers).toContain('班主任')
    expect(headers).not.toContain('学院')
    expect(headers).not.toContain('教学班')
    expect(classroomApi.getClassroomList).toHaveBeenCalledWith({ keyword: undefined })
    wrapper.unmount()
  })

  it('新建班级调用 POST /classrooms 并刷新', async () => {
    const wrapper = mountClasses()
    await flushPromises()

    await wrapper.find('button.btn-primary').trigger('click')
    await wrapper.find('[data-testid="class-name"]').setValue('计算机2202班')
    await wrapper.find('[data-testid="class-code"]').setValue('CS2202')
    await wrapper.find('[data-testid="class-major"]').setValue('计算机科学与技术')
    await wrapper.find('[data-testid="class-grade"]').setValue('2022')
    await wrapper.find('[data-testid="class-max"]').setValue(45)
    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(classroomApi.createClassroom).toHaveBeenCalledWith(
      expect.objectContaining({
        name: '计算机2202班',
        code: 'CS2202',
        major: '计算机科学与技术',
        gradeLevel: '2022',
        maxStudents: 45,
        isActive: true,
      }),
    )
    expect(classroomApi.getClassroomList).toHaveBeenCalledTimes(2)
    expect(toast).toHaveBeenCalledWith('班级已创建', 'ok')
    wrapper.unmount()
  })

  it('编辑班级调用 PUT /classrooms/{id}', async () => {
    const wrapper = mountClasses()
    await flushPromises()

    await wrapper.findAll('tbody tr')[0].findAll('.row-actions button')[0].trigger('click')
    await wrapper.find('[data-testid="class-name"]').setValue('计算机2201班（改）')
    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(classroomApi.updateClassroom).toHaveBeenCalledWith(
      1,
      expect.objectContaining({ name: '计算机2201班（改）', code: 'CS2201' }),
    )
    expect(toast).toHaveBeenCalledWith('班级已更新', 'ok')
    wrapper.unmount()
  })

  it('删除班级先二次确认，确认后才调用 DELETE /classrooms/{id}', async () => {
    const wrapper = mountClasses()
    await flushPromises()

    await wrapper.findAll('tbody tr')[0].findAll('.row-actions button')[1].trigger('click')
    await flushPromises()
    expect(wrapper.find('.modal-stub').text()).toContain('计算机2201班')
    expect(classroomApi.deleteClassroom).not.toHaveBeenCalled()

    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(classroomApi.deleteClassroom).toHaveBeenCalledWith(1)
    expect(classroomApi.getClassroomList).toHaveBeenCalledTimes(2)
    expect(toast).toHaveBeenCalledWith('班级已删除', 'ok')
    wrapper.unmount()
  })

  it('保存失败保留弹窗与输入，并展示后端原因', async () => {
    classroomApi.createClassroom.mockRejectedValueOnce({
      response: { data: { message: '班级编号已存在' } },
    })
    const wrapper = mountClasses()
    await flushPromises()

    await wrapper.find('button.btn-primary').trigger('click')
    await wrapper.find('[data-testid="class-name"]').setValue('计算机2202班')
    await wrapper.find('[data-testid="class-code"]').setValue('CS2202')
    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(wrapper.find('.modal-stub').exists()).toBe(true)
    expect(wrapper.find('.modal-stub').text()).toContain('班级编号已存在')
    expect((wrapper.find('[data-testid="class-code"]').element as HTMLInputElement).value).toBe(
      'CS2202',
    )
    expect(classroomApi.getClassroomList).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('列表加载失败与真正无班级可区分', async () => {
    classroomApi.getClassroomList.mockRejectedValueOnce(new Error('500'))
    const wrapper = mountClasses()
    await flushPromises()

    expect(wrapper.find('.callout').text()).toContain('班级列表加载失败')
    expect(wrapper.text()).toContain('无法确认班级数据')
    expect(wrapper.text()).not.toContain('暂无行政班')
    expect(toast).toHaveBeenCalledWith('班级列表加载失败', 'danger')
    wrapper.unmount()
  })
})

describe('管理员 / 课程审批', () => {
  it('只列 approvalStatus=PENDING 的课程', async () => {
    const wrapper = mountApprovals()
    await flushPromises()

    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(1)
    expect(rows[0].text()).toContain('数据结构')
    expect(rows[0].text()).toContain('专业必修')
    expect(rows[0].text()).toContain('教师 #7')
    expect(wrapper.text()).not.toContain('操作系统')
    expect(wrapper.text()).not.toContain('编译原理')
    expect(wrapper.text()).not.toContain('示例数据')
    expect(courseApi.getAllCourses).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('通过课程先确认，成功后调用 approve 并刷新队列', async () => {
    const wrapper = mountApprovals()
    await flushPromises()

    await wrapper.findAll('tbody tr')[0].findAll('.row-actions button')[0].trigger('click')
    await flushPromises()
    expect(wrapper.find('.modal-stub').text()).toContain('数据结构')
    expect(courseApi.approveCourse).not.toHaveBeenCalled()

    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(courseApi.approveCourse).toHaveBeenCalledWith(101)
    expect(courseApi.getAllCourses).toHaveBeenCalledTimes(2)
    expect(toast).toHaveBeenCalledWith('课程审批已通过', 'ok')
    wrapper.unmount()
  })

  it('驳回可填理由，成功后调用 reject 并刷新队列', async () => {
    const wrapper = mountApprovals()
    await flushPromises()

    await wrapper.findAll('tbody tr')[0].findAll('.row-actions button')[1].trigger('click')
    await wrapper.find('[data-testid="reject-reason"]').setValue('内容不完整')
    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(courseApi.rejectCourse).toHaveBeenCalledWith(101, '内容不完整')
    expect(courseApi.getAllCourses).toHaveBeenCalledTimes(2)
    expect(toast).toHaveBeenCalledWith('课程已驳回', 'ok')
    wrapper.unmount()
  })

  it('驳回理由留空时按无理由提交', async () => {
    const wrapper = mountApprovals()
    await flushPromises()

    await wrapper.findAll('tbody tr')[0].findAll('.row-actions button')[1].trigger('click')
    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(courseApi.rejectCourse).toHaveBeenCalledWith(101, undefined)
    wrapper.unmount()
  })

  it('通过失败保留确认弹窗，不刷新队列', async () => {
    courseApi.approveCourse.mockRejectedValueOnce({
      response: { data: { message: '课程已处于审批完成状态' } },
    })
    const wrapper = mountApprovals()
    await flushPromises()

    await wrapper.findAll('tbody tr')[0].findAll('.row-actions button')[0].trigger('click')
    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(wrapper.find('.modal-stub').exists()).toBe(true)
    expect(wrapper.find('.modal-stub').text()).toContain('课程已处于审批完成状态')
    expect(courseApi.getAllCourses).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('队列加载失败与真正无待审批可区分', async () => {
    courseApi.getAllCourses.mockRejectedValueOnce(new Error('500'))
    const wrapper = mountApprovals()
    await flushPromises()

    expect(wrapper.find('.callout').text()).toContain('课程审批队列加载失败')
    expect(wrapper.text()).toContain('无法确认审批队列')
    expect(wrapper.text()).not.toContain('当前没有待审批课程')
    expect(toast).toHaveBeenCalledWith('课程审批队列加载失败', 'danger')
    wrapper.unmount()
  })
})
