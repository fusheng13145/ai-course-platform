import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useAuthStore } from '@/stores/auth'

// 模拟所有依赖
vi.mock('@/utils/logger', () => ({ default: { log: vi.fn(), warn: vi.fn(), error: vi.fn() } }))
vi.mock('@/utils/response', () => ({ unwrap: vi.fn((r) => (r && r.data !== undefined ? r.data : r)) }))
vi.mock('@/utils/websocket', () => ({ connect: vi.fn(() => Promise.resolve()) }))

const mockLogin = vi.fn()
const mockLogout = vi.fn()
const mockGetPermissions = vi.fn()
vi.mock('@/api/auth', () => ({ default: { login: mockLogin, logout: mockLogout, getPermissions: mockGetPermissions } }))

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
    expect(store.userRole).toBeNull()
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

    expect(result).toBe(true)
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
    expect(store.token).toBeNull()
  })

  it('角色判断辅助函数正确', async () => {
    mockLogin.mockResolvedValue({
      data: { token: 't', user: { id: 1, username: 't', role: 'TEACHER' } },
    })
    mockGetPermissions.mockResolvedValue({ data: [] })

    const store = useAuthStore()
    await store.login({ username: 'teacher', password: 'pass' })
    expect(store.isTeacher).toBe(true)
    expect(store.isStudent).toBe(false)
    expect(store.isAdmin).toBe(false)
  })

  it('默认路由根据角色返回正确路径', async () => {
    const store = useAuthStore()

    // 管理员
    store.userRole = 'ADMIN'
    expect(store.getDefaultRoute()).toBe('/admin/dashboard')

    // 教师
    store.userRole = 'TEACHER'
    expect(store.getDefaultRoute()).toBe('/teacher/dashboard')

    // 学生
    store.userRole = 'STUDENT'
    expect(store.getDefaultRoute()).toBe('/student/dashboard')
  })

  it('resetAuthState 清理所有状态', () => {
    const store = useAuthStore()
    store.token = 'some-token'
    store.user = { id: 1 }
    store.userRole = 'STUDENT'

    store.resetAuthState()
    expect(store.token).toBeNull()
    expect(store.user).toBeNull()
    expect(store.userRole).toBeNull()
    expect(store.isLoggedIn).toBe(false)
  })
})
