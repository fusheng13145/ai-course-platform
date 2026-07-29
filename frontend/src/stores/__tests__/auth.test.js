import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'

// 使用 vi.hoisted 确保 mock 工厂中的变量在 hoisting 时可用
const { mockLogin, mockLogout, mockGetPermissions } = vi.hoisted(() => ({
  mockLogin: vi.fn(),
  mockLogout: vi.fn(),
  mockGetPermissions: vi.fn(),
}))

// 模拟所有依赖
vi.mock('@/utils/logger', () => ({ default: { log: vi.fn(), warn: vi.fn(), error: vi.fn() } }))
vi.mock('@/utils/response', () => ({ unwrap: vi.fn((r) => (r && r.data !== undefined ? r.data : r)) }))
vi.mock('@/utils/websocket', () => ({ connect: vi.fn(() => Promise.resolve()) }))
vi.mock('@/api/auth', () => ({ default: { login: mockLogin, logout: mockLogout, getPermissions: mockGetPermissions } }))

// 动态导入 auth store（在 mock 之后）
const { useAuthStore } = await import('@/stores/auth')

const localStorageMock = (() => {
  let store = {}
  return {
    getItem: vi.fn((key) => store[key] ?? null),
    setItem: vi.fn((key, value) => { store[key] = value }),
    removeItem: vi.fn((key) => { delete store[key] }),
    clear: vi.fn(() => { store = {} }),
  }
})()

Object.defineProperty(globalThis, 'localStorage', { value: localStorageMock })
Object.defineProperty(globalThis, 'sessionStorage', {
  value: { getItem: vi.fn(), setItem: vi.fn(), removeItem: vi.fn(), clear: vi.fn() },
})

describe('AuthStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorageMock.clear()
  })

  it('初始化时未登录', () => {
    const store = useAuthStore()
    expect(store.isLoggedIn).toBe(false)
    expect(store.user).toBeNull()
    // userRole 是 computed，无用户时返回空字符串
    expect(store.userRole).toBe('')
  })

  it('login 成功后设置用户信息和 token', async () => {
    const mockResponse = {
      data: {
        token: 'jwt-token-123',
        user: { id: 1, username: 'testuser', role: 'STUDENT', realName: '测试' },
      },
    }
    mockLogin.mockResolvedValue(mockResponse)

    const store = useAuthStore()
    const result = await store.login({ username: 'testuser', password: 'password' })

    // login 返回 { success: true, user: ... }
    expect(result.success).toBe(true)
    expect(result.user).toEqual(mockResponse.data.user)
    expect(store.isLoggedIn).toBe(true)
    expect(store.user).toEqual(mockResponse.data.user)
    expect(store.userRole).toBe('STUDENT')
    expect(store.token).toBe('jwt-token-123')
  })

  it('login 失败不清除已有状态', async () => {
    mockLogin.mockRejectedValue(new Error('登录失败'))

    const store = useAuthStore()
    await expect(store.login({ username: 'bad', password: 'wrong' })).rejects.toThrow('登录失败')
    expect(store.isLoggedIn).toBe(false)
  })

  it('logout 后重置状态', async () => {
    const mockResponse = { data: { token: 't', user: { id: 1, username: 'test', role: 'ADMIN' } } }
    mockLogin.mockResolvedValue(mockResponse)
    const store = useAuthStore()
    await store.login({ username: 'admin', password: 'admin' })
    expect(store.isLoggedIn).toBe(true)

    mockLogout.mockResolvedValue({})
    await store.logout()
    expect(store.isLoggedIn).toBe(false)
    expect(store.user).toBeNull()
    // resetAuthState 设置 token = ''，不是 null
    expect(store.token).toBe('')
  })

  it('角色判断辅助函数正确', async () => {
    mockLogin.mockResolvedValue({
      data: { token: 't', user: { id: 1, username: 't', role: 'TEACHER' } },
    })
    mockGetPermissions.mockResolvedValue({ data: [] })

    const store = useAuthStore()
    await store.login({ username: 'teacher', password: 'pass' })
    // userRole 是 computed，值为 'TEACHER'
    expect(store.userRole).toBe('TEACHER')
    expect(store.user).toBeTruthy()
    expect(store.isLoggedIn).toBe(true)
  })

  it('默认路由根据角色返回正确路径', async () => {
    const store = useAuthStore()

    // 设置 user 对象来影响 userRole computed
    store.user = { role: 'ADMIN', id: 1, username: 'admin' }
    expect(store.getDefaultRoute()).toBe('/admin/dashboard')

    // 教师
    store.user = { role: 'TEACHER', id: 2, username: 'teacher' }
    expect(store.getDefaultRoute()).toBe('/teacher/dashboard')

    // 学生
    store.user = { role: 'STUDENT', id: 3, username: 'student' }
    expect(store.getDefaultRoute()).toBe('/student/dashboard')
  })

  it('resetAuthState 清理所有状态', () => {
    const store = useAuthStore()
    store.token = 'some-token'
    store.user = { id: 1, role: 'STUDENT' }

    store.resetAuthState()
    // resetAuthState 设置 token = ''，不是 null
    expect(store.token).toBe('')
    expect(store.user).toBeNull()
    // userRole 是 computed，无用户时返回 ''
    expect(store.userRole).toBe('')
    expect(store.isLoggedIn).toBe(false)
  })

  describe('getDefaultRoute', () => {
    it('ADMIN 角色返回 /admin/dashboard', () => {
      const store = useAuthStore()
      store.user = { role: 'ADMIN', id: 1, username: 'admin' }
      expect(store.getDefaultRoute()).toBe('/admin/dashboard')
    })

    it('TEACHER 角色返回 /teacher/dashboard', () => {
      const store = useAuthStore()
      store.user = { role: 'TEACHER', id: 2, username: 'teacher' }
      expect(store.getDefaultRoute()).toBe('/teacher/dashboard')
    })

    it('STUDENT 角色返回 /student/dashboard', () => {
      const store = useAuthStore()
      store.user = { role: 'STUDENT', id: 3, username: 'student' }
      expect(store.getDefaultRoute()).toBe('/student/dashboard')
    })

    it('未设置角色或未知角色返回 /login', () => {
      const store = useAuthStore()
      // 无用户或空角色
      store.user = null
      expect(store.getDefaultRoute()).toBe('/login')

      store.user = { role: 'UNKNOWN' }
      expect(store.getDefaultRoute()).toBe('/login')
    })
  })

  describe('logout 增强测试', () => {
    it('logout 调用 API 成功后清空状态', async () => {
      const mockResponse = {
        data: { token: 't', user: { id: 1, username: 'test', role: 'STUDENT' } },
      }
      mockLogin.mockResolvedValue(mockResponse)
      mockLogout.mockResolvedValue({})
      const store = useAuthStore()
      await store.login({ username: 'test', password: 'pass' })
      expect(store.isLoggedIn).toBe(true)
      expect(store.userRole).toBe('STUDENT')

      await store.logout()
      expect(store.isLoggedIn).toBe(false)
      expect(store.user).toBeNull()
      // resetAuthState 设置 token = ''
      expect(store.token).toBe('')
      // userRole 是 computed，无用户时返回 ''
      expect(store.userRole).toBe('')
      expect(mockLogout).toHaveBeenCalled()
    })

    it('logout API 失败时仍然清空前端状态', async () => {
      const store = useAuthStore()
      store.token = 'stale-token'
      store.user = { id: 1, role: 'STUDENT' }
      mockLogout.mockRejectedValue(new Error('网络错误'))

      await store.logout()
      // finally 中 resetAuthState 仍然执行
      expect(store.isLoggedIn).toBe(false)
      expect(store.token).toBe('')
      expect(store.user).toBeNull()
    })

    it('未登录时 logout 不调用 API', async () => {
      const store = useAuthStore()
      // token 和 user 都为空
      store.token = ''
      store.user = null
      await store.logout()
      expect(mockLogout).not.toHaveBeenCalled()
    })
  })

  describe('hasMenuPermission', () => {
    it('权限未加载时返回 false（fail-closed）', () => {
      const store = useAuthStore()
      store.permissions = null
      expect(store.hasMenuPermission('admin/dashboard')).toBe(false)
    })

    it('权限已加载但无权限数据时返回 false', () => {
      const store = useAuthStore()
      store.permissions = { menus: [] }
      // 需要设置 permissionsLoaded
      store.permissionsLoaded = true
      expect(store.hasMenuPermission('admin/dashboard')).toBe(false)
    })

    it('菜单权限列表包含指定 key 返回 true', () => {
      const store = useAuthStore()
      store.permissions = { menus: ['admin/dashboard', 'admin/users'] }
      store.permissionsLoaded = true
      expect(store.hasMenuPermission('admin/dashboard')).toBe(true)
      expect(store.hasMenuPermission('admin/users')).toBe(true)
    })

    it('菜单权限列表不包含指定 key 返回 false', () => {
      const store = useAuthStore()
      store.permissions = { menus: ['admin/dashboard'] }
      store.permissionsLoaded = true
      expect(store.hasMenuPermission('admin/courses')).toBe(false)
    })

    it('permissions 为 null 时 hasMenuPermission 返回 false', () => {
      const store = useAuthStore()
      store.permissions = null
      store.permissionsLoaded = true
      expect(store.hasMenuPermission('any-menu')).toBe(false)
    })

    it('permissionsLoaded 为 false 时拒绝所有权限', () => {
      const store = useAuthStore()
      store.permissions = { menus: ['admin/dashboard'] }
      store.permissionsLoaded = false
      expect(store.hasMenuPermission('admin/dashboard')).toBe(false)
    })
  })

  describe('hasButtonPermission', () => {
    it('权限未加载时返回 false（fail-closed）', () => {
      const store = useAuthStore()
      store.permissions = null
      expect(store.hasButtonPermission('user:create')).toBe(false)
    })

    it('permissionsLoaded 为 false 时拒绝所有按钮权限', () => {
      const store = useAuthStore()
      store.permissions = { buttons: { 'user:create': true } }
      store.permissionsLoaded = false
      expect(store.hasButtonPermission('user:create')).toBe(false)
    })

    it('按钮权限为 true 时返回 true', () => {
      const store = useAuthStore()
      store.permissions = { buttons: { 'user:create': true, 'user:delete': false } }
      store.permissionsLoaded = true
      expect(store.hasButtonPermission('user:create')).toBe(true)
    })

    it('按钮权限为 false 时返回 false', () => {
      const store = useAuthStore()
      store.permissions = { buttons: { 'user:delete': false } }
      store.permissionsLoaded = true
      expect(store.hasButtonPermission('user:delete')).toBe(false)
    })

    it('不存在的按钮权限返回 false', () => {
      const store = useAuthStore()
      store.permissions = { buttons: { 'user:create': true } }
      store.permissionsLoaded = true
      expect(store.hasButtonPermission('user:export')).toBe(false)
    })

    it('buttons 为空对象时返回 false', () => {
      const store = useAuthStore()
      store.permissions = { buttons: {} }
      store.permissionsLoaded = true
      expect(store.hasButtonPermission('any-button')).toBe(false)
    })

    it('permissions 为 null 时返回 false', () => {
      const store = useAuthStore()
      store.permissions = null
      store.permissionsLoaded = true
      expect(store.hasButtonPermission('any-button')).toBe(false)
    })
  })

  describe('fetchPermissions', () => {
    it('fetchPermissions 成功时设置权限数据和标记', async () => {
      const mockPermData = {
        menus: ['student/dashboard', 'student/courses'],
        buttons: { 'course:view': true },
      }
      mockGetPermissions.mockResolvedValue({ data: mockPermData })

      const store = useAuthStore()
      await store.fetchPermissions()

      expect(store.permissions).toEqual(mockPermData)
      expect(store.permissionsLoaded).toBe(true)
    })

    it('fetchPermissions 失败时保持 fail-closed', async () => {
      mockGetPermissions.mockRejectedValue(new Error('权限加载失败'))

      const store = useAuthStore()
      store.permissions = null
      store.permissionsLoaded = false
      await store.fetchPermissions()

      expect(store.permissions).toBeNull()
      expect(store.permissionsLoaded).toBe(false)
    })
  })
})
