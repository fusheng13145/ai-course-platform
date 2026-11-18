import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

/** 五个治理页共享的接口 mock：每个模块只暴露页面真实调用的具名导出。 */
const adminApi = vi.hoisted(() => ({
  getUsers: vi.fn(),
  getAllUsers: vi.fn(),
  createUser: vi.fn(),
  updateUser: vi.fn(),
  resetUserPassword: vi.fn(),
  getSemesters: vi.fn(),
  createSemester: vi.fn(),
  archiveSemester: vi.fn(),
  archiveExpiredSemesters: vi.fn(),
  getOrgAdminClasses: vi.fn(),
  createOrgAdminClass: vi.fn(),
  updateOrgAdminClass: vi.fn(),
  archiveOrgAdminClass: vi.fn(),
  getColleges: vi.fn(),
  getMajors: vi.fn(),
}))

vi.mock('@/api/admin', () => ({ ...adminApi, default: adminApi }))

const userApi = vi.hoisted(() => ({ updateUserStatus: vi.fn() }))
vi.mock('@/api/user', () => ({ ...userApi, default: userApi }))

const permissionApi = vi.hoisted(() => ({
  getAllPermissions: vi.fn(),
  createPermission: vi.fn(),
  updatePermission: vi.fn(),
  deletePermission: vi.fn(),
  togglePermission: vi.fn(),
}))
vi.mock('@/api/permission', () => ({ ...permissionApi, default: permissionApi }))

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
    adminClassId: 1,
    studentNo: '20220001',
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

const COLLEGES = [{ id: 1, name: '计算机学院' }]
const MAJORS = [{ id: 11, collegeId: 1, name: '计算机科学与技术' }]

const ADMIN_CLASSES = [
  {
    id: 1,
    schoolId: 1,
    collegeId: 1,
    majorId: 11,
    name: '计算机2201班',
    code: 'CS2201',
    gradeYear: '2022',
    advisorTeacherId: 11,
    studentCount: 1,
    maxStudents: 50,
    status: 'ACTIVE',
  },
  {
    id: 2,
    schoolId: 1,
    collegeId: 1,
    majorId: 11,
    name: '计算机2202班',
    code: 'CS2202',
    gradeYear: '2022',
    advisorTeacherId: 11,
    studentCount: 0,
    maxStudents: 45,
    status: 'ACTIVE',
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
  adminApi.getAllUsers.mockResolvedValue(USERS)
  adminApi.getOrgAdminClasses.mockResolvedValue(envelope(ADMIN_CLASSES))
  adminApi.createOrgAdminClass.mockResolvedValue({ code: 200, data: {} })
  adminApi.updateOrgAdminClass.mockResolvedValue({ code: 200, data: {} })
  adminApi.archiveOrgAdminClass.mockResolvedValue({ code: 200, data: null })
  adminApi.getColleges.mockResolvedValue(envelope(COLLEGES))
  adminApi.getMajors.mockResolvedValue(envelope(MAJORS))
  adminApi.getSemesters.mockResolvedValue(envelope(SEMESTERS))
  adminApi.createSemester.mockResolvedValue({ code: 200, data: {} })
  adminApi.archiveSemester.mockResolvedValue({ code: 200, data: {} })
  adminApi.archiveExpiredSemesters.mockResolvedValue({ code: 200, data: {} })
  adminApi.createUser.mockResolvedValue({ code: 200, data: {} })
  adminApi.updateUser.mockResolvedValue({ code: 200, data: {} })
  adminApi.resetUserPassword.mockResolvedValue({ code: 200, data: {} })
  userApi.updateUserStatus.mockResolvedValue({ code: 200, data: null })
  permissionApi.getAllPermissions.mockResolvedValue(envelope(PERMISSIONS))
  permissionApi.createPermission.mockResolvedValue({ code: 200, data: {} })
  permissionApi.updatePermission.mockResolvedValue({ code: 200, data: {} })
  permissionApi.deletePermission.mockResolvedValue({ code: 200, data: {} })
  permissionApi.togglePermission.mockResolvedValue({ code: 200, data: {} })
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

    await wrapper.findAll('tbody tr')[0].find('[data-testid="user-toggle"]').trigger('click')
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

    await wrapper.findAll('tbody tr')[1].find('[data-testid="user-toggle"]').trigger('click')
    await flushPromises()

    expect(userApi.updateUserStatus).toHaveBeenCalledWith(12, true)
    expect(wrapper.find('.modal-stub').exists()).toBe(false)
    expect(adminApi.getUsers).toHaveBeenCalledTimes(2)
    wrapper.unmount()
  })

  it('新建用户提交完整资料，成功后刷新列表', async () => {
    const wrapper = mountUsers()
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((item) => item.text() === '新建用户')!
      .trigger('click')
    await wrapper.find('[data-testid="user-username"]').setValue('wangwu')
    await wrapper.find('[data-testid="user-name"]').setValue('王五')
    await wrapper.find('[data-testid="user-role"]').setValue('TEACHER')
    await wrapper.find('[data-testid="user-password"]').setValue('Strong@123')
    await wrapper.find('[data-testid="user-email"]').setValue('wangwu@example.com')
    await wrapper.find('[data-testid="user-phone"]').setValue('13700000000')

    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(adminApi.createUser).toHaveBeenCalledWith({
      username: 'wangwu',
      fullName: '王五',
      role: 'TEACHER',
      email: 'wangwu@example.com',
      phone: '13700000000',
      password: 'Strong@123',
      isActive: true,
      adminClassId: 0,
    })
    expect(adminApi.getUsers).toHaveBeenCalledTimes(2)
    expect(toast).toHaveBeenCalledWith('用户已创建', 'ok')
    wrapper.unmount()
  })

  it('弱密码在提交前被拦截，不发起创建请求', async () => {
    const wrapper = mountUsers()
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((item) => item.text() === '新建用户')!
      .trigger('click')
    await wrapper.find('[data-testid="user-username"]').setValue('wangwu')
    await wrapper.find('[data-testid="user-password"]').setValue('weakpass')

    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(wrapper.find('.form-error').text()).toContain('大写字母')
    expect(adminApi.createUser).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('学生创建可直接分进行政班', async () => {
    const wrapper = mountUsers()
    await flushPromises()

    await wrapper
      .findAll('button')
      .find((item) => item.text() === '新建用户')!
      .trigger('click')
    await wrapper.find('[data-testid="user-username"]').setValue('zhaoliu')
    await wrapper.find('[data-testid="user-password"]').setValue('Strong@123')
    await wrapper.find('[data-testid="user-role"]').setValue('STUDENT')
    await wrapper.find('[data-testid="user-admin-class"]').setValue('1')
    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(adminApi.createUser).toHaveBeenCalledWith(
      expect.objectContaining({ role: 'STUDENT', adminClassId: 1 }),
    )
    wrapper.unmount()
  })

  it('编辑用户调用更新端点，重置密码调用专用端点', async () => {
    const wrapper = mountUsers()
    await flushPromises()

    await wrapper.findAll('tbody tr')[0].find('[data-testid="user-edit"]').trigger('click')
    await wrapper.find('[data-testid="user-name"]').setValue('张三丰')
    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(adminApi.updateUser).toHaveBeenCalledWith(11, {
      username: 'zhangsan',
      fullName: '张三丰',
      role: 'TEACHER',
      email: 'zhangsan@example.com',
      phone: '13800000000',
      adminClassId: 0,
    })
    expect(wrapper.find('.modal-stub').exists()).toBe(false)

    await wrapper.findAll('tbody tr')[0].find('[data-testid="user-reset"]').trigger('click')
    await wrapper.find('[data-testid="user-new-password"]').setValue('Reset@123')
    const resetButtons = footerButtons(wrapper)
    await resetButtons[resetButtons.length - 1].trigger('click')
    await flushPromises()

    expect(adminApi.resetUserPassword).toHaveBeenCalledWith(11, { newPassword: 'Reset@123' })
    expect(toast).toHaveBeenCalledWith('密码已重置', 'ok')
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
    expect(toast).toHaveBeenCalledWith('权限项已新增', 'ok')
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

  it('编辑权限项调用更新端点并保留角色', async () => {
    const wrapper = mountRoles()
    await flushPromises()

    await wrapper.findAll('tbody tr')[0].find('[data-testid="permission-edit"]').trigger('click')
    await wrapper.find('[data-testid="role-assign-name"]').setValue('账号治理')
    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(permissionApi.updatePermission).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        role: 'ADMIN',
        permissionName: '账号治理',
        permissionKey: 'user:manage',
        isActive: true,
      }),
    )
    expect(toast).toHaveBeenCalledWith('权限项已更新', 'ok')
    wrapper.unmount()
  })

  it('权限项可停用与删除，删除先二次确认', async () => {
    const wrapper = mountRoles()
    await flushPromises()

    await wrapper.findAll('tbody tr')[0].find('[data-testid="permission-toggle"]').trigger('click')
    await flushPromises()
    expect(permissionApi.togglePermission).toHaveBeenCalledWith(1)

    await wrapper.findAll('tbody tr')[0].find('[data-testid="permission-delete"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('.modal-stub').text()).toContain('用户管理')
    expect(permissionApi.deletePermission).not.toHaveBeenCalled()

    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()
    expect(permissionApi.deletePermission).toHaveBeenCalledWith(1)
    expect(toast).toHaveBeenCalledWith('权限项已删除', 'ok')
    wrapper.unmount()
  })

  it('权限列表加载失败时显示失败态而非空角色权限', async () => {
    permissionApi.getAllPermissions.mockRejectedValueOnce(new Error('500'))
    const wrapper = mountRoles()
    await flushPromises()

    expect(wrapper.find('.callout').text()).toContain('权限列表加载失败')
    expect(wrapper.text()).not.toContain('暂无权限项')
    expect(wrapper.find('.empty-state').exists()).toBe(false)
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

  it('批量归档只处理已结束且仍在册的学期，确认后调用批量端点', async () => {
    adminApi.getSemesters.mockResolvedValueOnce(
      envelope([
        ...SEMESTERS,
        {
          id: 3,
          name: '2025-2026-1',
          startDate: '2025-09-01',
          endDate: '2026-01-10',
          status: 'ACTIVE',
          archivedAt: null,
        },
      ]),
    )
    const wrapper = mountSemesters()
    await flushPromises()

    const batchButton = wrapper.findAll('button').find((item) => item.text().includes('归档过期'))!
    expect(batchButton.text()).toContain('1')
    await batchButton.trigger('click')
    expect(wrapper.find('.modal-stub').text()).toContain('1')
    expect(adminApi.archiveExpiredSemesters).not.toHaveBeenCalled()

    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(adminApi.archiveExpiredSemesters).toHaveBeenCalledTimes(1)
    expect(adminApi.getSemesters).toHaveBeenCalledTimes(2)
    expect(toast).toHaveBeenCalledWith('过期学期已归档', 'ok')
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
  it('列表来自 admin_classes，并显示真实成员数、专业名与班主任姓名', async () => {
    const wrapper = mountClasses()
    await flushPromises()

    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('计算机2201班')
    expect(rows[0].text()).toContain('计算机科学与技术')
    expect(rows[0].text()).toContain('2022')
    expect(rows[0].text()).toContain('1 / 50')
    expect(rows[0].text()).toContain('张三')

    const headers = wrapper.find('thead').text()
    expect(headers).toContain('专业')
    expect(headers).toContain('年级')
    expect(headers).toContain('班主任')
    expect(headers).not.toContain('教学班')
    expect(adminApi.getOrgAdminClasses).toHaveBeenCalled()
    expect(adminApi.getAllUsers).toHaveBeenCalledWith({})
    wrapper.unmount()
  })

  it('新建行政班调用 POST /admin/org/admin-classes 并刷新', async () => {
    const wrapper = mountClasses()
    await flushPromises()

    await wrapper.find('button.btn-primary').trigger('click')
    await wrapper.find('[data-testid="class-name"]').setValue('计算机2203班')
    await wrapper.find('[data-testid="class-code"]').setValue('CS2203')
    await wrapper.find('[data-testid="class-max"]').setValue(45)
    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(adminApi.createOrgAdminClass).toHaveBeenCalledWith(
      expect.objectContaining({
        name: '计算机2203班',
        code: 'CS2203',
        collegeId: 1,
        majorId: 11,
        maxStudents: 45,
        advisorTeacherId: null,
        status: 'ACTIVE',
      }),
    )
    expect(adminApi.getOrgAdminClasses).toHaveBeenCalledTimes(2)
    expect(toast).toHaveBeenCalledWith('行政班已创建', 'ok')
    wrapper.unmount()
  })

  it('编辑行政班调用 PUT /admin/org/admin-classes/{id}', async () => {
    const wrapper = mountClasses()
    await flushPromises()

    await wrapper.findAll('tbody tr')[0].findAll('.row-actions button')[1].trigger('click')
    await wrapper.find('[data-testid="class-name"]').setValue('计算机2201班（改）')
    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(adminApi.updateOrgAdminClass).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        name: '计算机2201班（改）',
        code: 'CS2201',
        collegeId: 1,
        majorId: 11,
      }),
    )
    expect(toast).toHaveBeenCalledWith('行政班已更新', 'ok')
    wrapper.unmount()
  })

  it('成员弹窗列出本班学生，移出需确认并清空行政班归属', async () => {
    const wrapper = mountClasses()
    await flushPromises()

    await wrapper.findAll('tbody tr')[0].findAll('.row-actions button')[0].trigger('click')
    await flushPromises()
    expect(wrapper.find('.modal-stub').text()).toContain('李四')

    const removeButton = wrapper
      .findAll('.modal-stub button')
      .find((item) => item.text() === '移出')!
    await removeButton.trigger('click')
    await flushPromises()
    const modals = wrapper.findAll('.modal-stub')
    expect(modals[modals.length - 1].text()).toContain('确定将')
    expect(modals[modals.length - 1].text()).toContain('移出行政班吗')

    const buttons = modals[modals.length - 1].findAll('button')
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(adminApi.updateUser).toHaveBeenCalledWith(12, { adminClassId: 0 })
    expect(toast).toHaveBeenCalledWith('已移出行政班', 'ok')
    wrapper.unmount()
  })

  it('归档行政班先二次确认，确认后才调用 DELETE /admin/org/admin-classes/{id}', async () => {
    const wrapper = mountClasses()
    await flushPromises()

    await wrapper.findAll('tbody tr')[1].findAll('.row-actions button')[2].trigger('click')
    await flushPromises()
    expect(wrapper.find('.modal-stub').text()).toContain('计算机2202班')
    expect(adminApi.archiveOrgAdminClass).not.toHaveBeenCalled()

    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(adminApi.archiveOrgAdminClass).toHaveBeenCalledWith(2)
    expect(adminApi.getOrgAdminClasses).toHaveBeenCalledTimes(2)
    expect(toast).toHaveBeenCalledWith('行政班已归档', 'ok')
    wrapper.unmount()
  })

  it('保存失败保留弹窗与输入，并展示后端原因', async () => {
    adminApi.createOrgAdminClass.mockRejectedValueOnce({
      response: { data: { message: '行政班编码已存在' } },
    })
    const wrapper = mountClasses()
    await flushPromises()

    await wrapper.find('button.btn-primary').trigger('click')
    await wrapper.find('[data-testid="class-name"]').setValue('计算机2203班')
    await wrapper.find('[data-testid="class-code"]').setValue('CS2203')
    const buttons = footerButtons(wrapper)
    await buttons[buttons.length - 1].trigger('click')
    await flushPromises()

    expect(wrapper.find('.modal-stub').exists()).toBe(true)
    expect(wrapper.find('.modal-stub').text()).toContain('行政班编码已存在')
    expect((wrapper.find('[data-testid="class-code"]').element as HTMLInputElement).value).toBe(
      'CS2203',
    )
    expect(adminApi.getOrgAdminClasses).toHaveBeenCalledTimes(1)
    wrapper.unmount()
  })

  it('列表加载失败与真正无行政班可区分', async () => {
    adminApi.getOrgAdminClasses.mockRejectedValueOnce(new Error('500'))
    const wrapper = mountClasses()
    await flushPromises()

    expect(wrapper.find('.callout').text()).toContain('行政班列表加载失败')
    expect(wrapper.text()).toContain('无法确认行政班数据')
    expect(wrapper.text()).not.toContain('暂无行政班')
    expect(toast).toHaveBeenCalledWith('行政班列表加载失败', 'danger')
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
