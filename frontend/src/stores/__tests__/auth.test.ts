import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { AxiosError, type AxiosResponse } from 'axios'

// 使用 vi.hoisted 确保 mock 工厂中的变量在 hoisting 时可用
const { mockLogin, mockLogout, mockGetPermissions } = vi.hoisted(() => ({
  mockLogin: vi.fn(),
  mockLogout: vi.fn(),
  mockGetPermissions: vi.fn(),
}))

// 模拟所有依赖
vi.mock('@/utils/logger', () => ({ default: { log: vi.fn(), warn: vi.fn(), error: vi.fn() } }))
vi.mock('@/utils/response', () => ({
  unwrap: vi.fn((r) => (r && r.data !== undefined ? r.data : r)),
}))
vi.mock('@/utils/websocket', () => ({
  connect: vi.fn(() => Promise.resolve()),
  disconnect: vi.fn(),
}))
vi.mock('@/api/auth', () => ({
  default: { login: mockLogin, logout: mockLogout, getPermissions: mockGetPermissions },
}))

// 动态导入 auth store（在 mock 之后）
const { useAuthStore } = await import('@/stores/auth')

const localStorageMock = (() => {
  let store: Record<string, unknown> = {}
  return {
    getItem: vi.fn((key: string) => store[key] ?? null),
    setItem: vi.fn((key: string, value: unknown) => {
      store[key] = value
    }),
    removeItem: vi.fn((key: string) => {
      delete store[key]
    }),
    clear: vi.fn(() => {
      store = {}
    }),
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
    expect(store.getDefaultRoute()).toBe('/admin/home')

    // 教师
    store.user = { role: 'TEACHER', id: 2, username: 'teacher' }
    expect(store.getDefaultRoute()).toBe('/teacher/home')

    // 学生
    store.user = { role: 'STUDENT', id: 3, username: 'student' }
    expect(store.getDefaultRoute()).toBe('/student/home')
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
    it('ADMIN 角色返回 /admin/home', () => {
      const store = useAuthStore()
      store.user = { role: 'ADMIN', id: 1, username: 'admin' }
      expect(store.getDefaultRoute()).toBe('/admin/home')
    })

    it('TEACHER 角色返回 /teacher/home', () => {
      const store = useAuthStore()
      store.user = { role: 'TEACHER', id: 2, username: 'teacher' }
      expect(store.getDefaultRoute()).toBe('/teacher/home')
    })

    it('STUDENT 角色返回 /student/home', () => {
      const store = useAuthStore()
      store.user = { role: 'STUDENT', id: 3, username: 'student' }
      expect(store.getDefaultRoute()).toBe('/student/home')
    })

    it('未知角色（已登录）返回安全落点 /403，不再返回 /login', () => {
      const store = useAuthStore()
      // 回归（P0-3）：原实现对未知角色返回 '/login'，
      // 而守卫在「已登录访问 /login」时又 next(getDefaultRoute()) → '/login' 自指向重定向。
      store.user = { role: 'UNKNOWN', id: 9, username: 'x' }
      expect(store.getDefaultRoute()).toBe('/403')

      store.user = { role: '', id: 10, username: 'y' }
      expect(store.getDefaultRoute()).toBe('/403')

      // 完全没有登录态时才回落 /login（此时守卫不会调用本函数）
      store.user = null
      expect(store.getDefaultRoute()).toBe('/login')
    })

    it('未知角色的落点必须不等于 /login，否则 /login 页会无限重定向', () => {
      const store = useAuthStore()
      store.user = { role: 'GUEST', id: 11, username: 'g' }
      const target = store.getDefaultRoute()
      // 这是 P0-3 的核心不变式：已登录用户的默认落点不能是登录页本身
      expect(target).not.toBe('/login')
      expect(target).not.toBe('/register')
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

    // DR-45：fetchPermissions 必须把「服务端的会话结论」回传给调用方（路由守卫），
    // 否则守卫会一边等待这次请求、一边无视它的结果照常 next() 放行受保护页 ——
    // 表现即「登录态已过期，页面仍显示已登录且点不动，再点一次才跳登录页」。
    describe('fetchPermissions 的会话结论（DR-45）', () => {
      /** 预置一份「本地缓存里有登录态」的状态，模拟刷新页面时 localStorage 仍留着 user */
      function seedCachedSession() {
        const store = useAuthStore()
        store.user = { id: 1001, username: 'ts01', role: 'TEACHER', fullName: '孙立群' }
        store.permissions = { menus: ['teacher/dashboard'], buttons: {} }
        return store
      }

      it('服务端回 401 → 结论为 expired，并清空本地登录态', async () => {
        // 拦截器 reject 的是真实 AxiosError（store 侧用 isAxiosError 判定），不是普通对象
        mockGetPermissions.mockRejectedValue(
          new AxiosError(
            'Request failed with status code 401',
            AxiosError.ERR_BAD_REQUEST,
            undefined,
            undefined,
            {
              status: 401,
            } as AxiosResponse,
          ),
        )
        const store = seedCachedSession()

        await expect(store.fetchPermissions()).resolves.toBe('expired')
        expect(store.isLoggedIn).toBe(false)
        expect(store.user).toBeNull()
      })

      it('非 401 的失败（网络异常/5xx）→ 结论为 unavailable，且不得清空登录态', async () => {
        // 后端重启、超时这类瞬时故障不应把用户踢出登录；真正拦住他的是后端本身。
        mockGetPermissions.mockRejectedValue(new Error('Network Error'))
        const store = seedCachedSession()

        await expect(store.fetchPermissions()).resolves.toBe('unavailable')
        expect(store.isLoggedIn).toBe(true)
        expect(store.user).not.toBeNull()
      })

      it('成功 → 结论为 ok', async () => {
        mockGetPermissions.mockResolvedValue({
          data: { menus: ['teacher/dashboard'], buttons: {} },
        })
        const store = seedCachedSession()

        await expect(store.fetchPermissions()).resolves.toBe('ok')
        expect(store.permissionsLoaded).toBe(true)
      })

      it('服务端角色与本地缓存矛盾 → 结论为 expired 并清空脏缓存（2026-11-14 用户实测回归）', async () => {
        // 本地残留 user.role=TEACHER，而服务端 Cookie 会话确认的角色是 STUDENT
        // （旧版本/换分支残留的 localStorage user，或 localhost 多端口共享 Cookie
        // 被另一账号会话覆盖）。原实现拿着脏缓存去过角色门禁 → 困在 /403，且错误页
        // 在 Shell 之外没有退出入口、/login 又被「已登录跳默认落地页」拦走，
        // 用户永远到不了登录页。现就地清空并按会话失效处理。
        mockGetPermissions.mockResolvedValue({
          data: { role: 'STUDENT', menus: ['student/home'], buttons: {} },
        })
        const store = seedCachedSession()

        await expect(store.fetchPermissions()).resolves.toBe('expired')
        expect(store.isLoggedIn).toBe(false)
        expect(store.user).toBeNull()
        expect(store.serverRole).toBe('')
      })

      it('服务端角色与本地缓存一致 → 结论为 ok，登录态不受影响', async () => {
        mockGetPermissions.mockResolvedValue({
          data: { role: 'TEACHER', menus: ['teacher/home'], buttons: {} },
        })
        const store = seedCachedSession()

        await expect(store.fetchPermissions()).resolves.toBe('ok')
        expect(store.isLoggedIn).toBe(true)
        expect(store.serverRole).toBe('TEACHER')
        expect(store.effectiveRole).toBe('TEACHER')
      })
    })
  })
})
