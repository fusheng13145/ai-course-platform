import { describe, expect, it, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'

const userApi = vi.hoisted(() => ({ getProfile: vi.fn() }))
vi.mock('@/api/user', () => ({ default: userApi }))

const authApi = vi.hoisted(() => ({ updateCurrentUser: vi.fn(), changePassword: vi.fn() }))
vi.mock('@/api/auth', () => ({ default: authApi }))

const commonApi = vi.hoisted(() => ({ uploadFile: vi.fn() }))
vi.mock('@/api/common', () => ({ default: commonApi }))

const router = vi.hoisted(() => ({ push: vi.fn() }))
vi.mock('vue-router', () => ({ useRouter: () => router }))

const ui = vi.hoisted(() => ({
  toast: vi.fn(),
  auth: {
    user: { role: 'STUDENT', fullName: '测试生', username: 'stu' },
    userId: 1,
    resetAuthState: vi.fn(),
  },
}))
vi.mock('../composables/ui', () => ({ useUi: () => ui }))

import ProfileBody from '../views/specials/ProfileBody.vue'

const studentProfile = {
  id: 100011,
  username: 'linzhiyuan',
  fullName: '林知远',
  email: 'lin@example.com',
  phone: '13800138000',
  gender: 'MALE',
  avatarUrl: '/uploads/avatars/1.png',
  studentId: '2023010101',
  major: '计算机科学与技术',
  majorName: '计算机科学与技术',
  grade: '2023',
  department: '计算机学院',
  collegeName: '计算机学院',
  schoolName: '江苏科技大学',
  adminClassName: '计科 2301',
  role: 'STUDENT',
}

beforeEach(() => {
  vi.clearAllMocks()
  ui.auth.user = { role: 'STUDENT', fullName: '测试生', username: 'stu' }
  ui.auth.userId = 100011
  userApi.getProfile.mockResolvedValue({ data: studentProfile })
  authApi.updateCurrentUser.mockResolvedValue({ data: studentProfile })
  authApi.changePassword.mockResolvedValue({ data: null })
  commonApi.uploadFile.mockResolvedValue({ data: { url: '/uploads/avatars/2.png' } })
})

function mountBody() {
  return mount(ProfileBody, {
    global: {
      stubs: {
        Modal: {
          props: ['open', 'title'],
          template: '<div v-if="open" class="modal-stub"><slot /><slot name="footer" /></div>',
        },
      },
    },
  })
}

function findButton(wrapper: ReturnType<typeof mountBody>, label: string) {
  return wrapper.findAll('button').find((node) => node.text().includes(label))
}

describe('个人中心 · 单张资料卡', () => {
  it('只渲染一张资料卡，展示本人关键字段，不再保留数据概览/我的课程/学习动态', async () => {
    const wrapper = mountBody()
    await flushPromises()

    expect(userApi.getProfile).toHaveBeenCalledWith(100011)
    expect(wrapper.findAll('.profile-card')).toHaveLength(1)
    const text = wrapper.text()
    for (const expected of [
      '林知远',
      '学生',
      'UID 100011',
      '性别',
      '男',
      '手机号',
      '13800138000',
      '邮箱',
      'lin@example.com',
      '用户名',
      'linzhiyuan',
      '所属学校',
      '江苏科技大学',
      '学院',
      '计算机学院',
      '学号',
      '2023010101',
      '专业',
      '计算机科学与技术',
      '年级',
      '2023',
      '行政班',
      '计科 2301',
    ]) {
      expect(text).toContain(expected)
    }
    expect(text).not.toContain('数据概览')
    expect(text).not.toContain('我的课程')
    expect(text).not.toContain('学习动态')
    expect(wrapper.find('.profile-avatar img').attributes('src')).toBe('/api/uploads/avatars/1.png')
    wrapper.unmount()
  })

  it('教师卡展示工号与职称，不出现学生专属字段', async () => {
    userApi.getProfile.mockResolvedValueOnce({
      data: {
        id: 100021,
        username: 'wangyanqiu',
        fullName: '王砚秋',
        role: 'TEACHER',
        teacherId: 'T2019007',
        title: '副教授',
        department: '计算机学院',
        collegeName: '计算机学院',
        schoolName: '江苏科技大学',
        gender: 'FEMALE',
        phone: '13900139000',
        email: 'wang@example.com',
      },
    })

    const wrapper = mountBody()
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('教师')
    expect(text).toContain('工号')
    expect(text).toContain('T2019007')
    expect(text).toContain('职称')
    expect(text).toContain('副教授')
    expect(text).not.toContain('学号')
    expect(text).not.toContain('专业')
    wrapper.unmount()
  })

  it('管理员按作用域显示管理范围', async () => {
    userApi.getProfile.mockResolvedValueOnce({
      data: {
        id: 100001,
        username: 'zhouming',
        fullName: '周明',
        role: 'ADMIN',
        adminScope: 'SCHOOL',
        teacherId: 'A1002',
        department: '教务处',
        collegeName: '教务处',
        schoolName: '江苏科技大学',
      },
    })

    const wrapper = mountBody()
    await flushPromises()
    expect(wrapper.text()).toContain('校级管理员')
    expect(wrapper.text()).toContain('管理范围')
    expect(wrapper.text()).toContain('校级')
    expect(wrapper.text()).toContain('A1002')
    wrapper.unmount()
  })

  it('编辑资料按白名单字段提交，性别可改且不回传未变化字段', async () => {
    const wrapper = mountBody()
    await flushPromises()

    await findButton(wrapper, '编辑资料')?.trigger('click')
    await flushPromises()

    const fullName = wrapper.find('[data-field="fullName"]').element as HTMLInputElement
    const email = wrapper.find('[data-field="email"]').element as HTMLInputElement
    const phone = wrapper.find('[data-field="phone"]').element as HTMLInputElement
    expect(fullName.value).toBe('林知远')
    expect(email.value).toBe('lin@example.com')
    expect(phone.value).toBe('13800138000')

    await wrapper.find('[data-gender="FEMALE"]').trigger('click')
    await wrapper.find('[data-field="email"]').setValue('new@example.com')
    await findButton(wrapper, '保存')?.trigger('click')
    await flushPromises()

    expect(authApi.updateCurrentUser).toHaveBeenCalledWith({
      fullName: '林知远',
      email: 'new@example.com',
      phone: '13800138000',
      gender: 'FEMALE',
    })
    expect(ui.toast).toHaveBeenCalledWith('资料已更新', 'ok')
    wrapper.unmount()
  })

  it('留空邮箱不下发空字符串，避免被后端当成清空', async () => {
    const wrapper = mountBody()
    await flushPromises()

    await findButton(wrapper, '编辑资料')?.trigger('click')
    await flushPromises()

    await wrapper.find('[data-field="email"]').setValue('')
    await findButton(wrapper, '保存')?.trigger('click')
    await flushPromises()

    expect(authApi.updateCurrentUser.mock.calls[0][0]).not.toHaveProperty('email', '')
    wrapper.unmount()
  })

  it('改密先做本地校验，再按 ChangePasswordDTO 三字段提交', async () => {
    const wrapper = mountBody()
    await flushPromises()

    await findButton(wrapper, '修改密码')?.trigger('click')
    await flushPromises()

    await wrapper.find('[data-field="oldPassword"]').setValue('oldpass1')
    await wrapper.find('[data-field="newPassword"]').setValue('Abc12345!')
    await wrapper.find('[data-field="confirmPassword"]').setValue('Abc12345?')
    await findButton(wrapper, '确认修改')?.trigger('click')
    await flushPromises()

    expect(authApi.changePassword).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('两次输入的新密码不一致')

    await wrapper.find('[data-field="confirmPassword"]').setValue('Abc12345!')
    await findButton(wrapper, '确认修改')?.trigger('click')
    await flushPromises()

    expect(authApi.changePassword).toHaveBeenCalledWith({
      oldPassword: 'oldpass1',
      newPassword: 'Abc12345!',
      confirmPassword: 'Abc12345!',
    })
    expect(ui.toast).toHaveBeenCalledWith('密码已修改，请重新登录', 'ok')
    expect(ui.auth.resetAuthState).toHaveBeenCalledTimes(1)
    expect(router.push).toHaveBeenCalledWith('/login')
    wrapper.unmount()
  })

  it('保存失败显式报错，不静默吞掉', async () => {
    authApi.updateCurrentUser.mockRejectedValueOnce(new Error('500'))
    const wrapper = mountBody()
    await flushPromises()

    await findButton(wrapper, '编辑资料')?.trigger('click')
    await flushPromises()
    await findButton(wrapper, '保存')?.trigger('click')
    await flushPromises()

    expect(wrapper.find('.form-error').text()).toContain('保存失败')
    expect(ui.toast).not.toHaveBeenCalledWith('资料已更新', 'ok')
    wrapper.unmount()
  })

  it('资料接口失败时保留登录缓存并显示危险提示', async () => {
    userApi.getProfile.mockRejectedValueOnce(new Error('500'))
    const wrapper = mountBody()
    await flushPromises()

    const callout = wrapper.find('.callout.danger')
    expect(callout.exists()).toBe(true)
    expect(callout.text()).toContain('详细资料未能加载')
    expect(wrapper.text()).toContain('测试生')
    wrapper.unmount()
  })

  it('可选资料为空时以占位符呈现，不虚构字段', async () => {
    userApi.getProfile.mockResolvedValueOnce({
      data: { id: 100011, username: 'linzhiyuan', role: 'STUDENT' },
    })
    const wrapper = mountBody()
    await flushPromises()

    expect(wrapper.text()).toContain('用户名')
    expect(wrapper.text()).toContain('学号')
    expect(wrapper.findAll('.profile-field dd.is-empty').length).toBeGreaterThan(0)
    await findButton(wrapper, '编辑资料')?.trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-field="fullName"]').element).toHaveProperty('value', '')
    wrapper.unmount()
  })
})
