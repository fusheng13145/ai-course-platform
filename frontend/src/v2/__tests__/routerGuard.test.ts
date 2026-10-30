import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createMemoryHistory } from 'vue-router'
import type { Router } from 'vue-router'

// 可控的 auth store 假实现：守卫只读这些字段，禁止任何真实网络请求。
// 形状与 stores/auth 的返回值一致（见 stores/auth.ts 的 return）。
const store = vi.hoisted(() => ({
  isLoggedIn: false,
  userRole: '',
  effectiveRole: '',
  permissions: null as unknown,
  permissionsLoaded: false,
  getDefaultRoute: vi.fn((): string => '/login'),
  fetchPermissions: vi.fn(async (): Promise<'ok' | 'expired' | 'unavailable'> => 'ok'),
}))

vi.mock('@/stores/auth', () => ({
  useAuthStore: () => store,
}))

// 进度条只关心被调用，不引入真实 NProgress 的 DOM 副作用
vi.mock('nprogress', () => ({
  default: { start: vi.fn(), done: vi.fn(), configure: vi.fn() },
}))

// 懒加载视图在导航时会被真实 import，这里逐个存根，测试因此不触碰任何视图/接口
vi.mock('../V2Shell.vue', () => ({ default: { name: 'V2Shell' } }))
vi.mock('../views/V2HomeView.vue', () => ({ default: { name: 'V2HomeView' } }))
vi.mock('../views/auth/V2LoginView.vue', () => ({ default: { name: 'V2LoginView' } }))
vi.mock('../views/error/V2ErrorView.vue', () => ({ default: { name: 'V2ErrorView' } }))
vi.mock('../views/admin/V2AdminQuestionsView.vue', () => ({
  default: { name: 'V2AdminQuestionsView' },
}))

import { createV2Router } from '../router'

describe('v2 自有 router 的鉴权守卫', () => {
  let router: Router

  beforeEach(() => {
    vi.clearAllMocks()
    store.isLoggedIn = false
    store.userRole = ''
    store.effectiveRole = ''
    store.permissions = null
    store.permissionsLoaded = false
    store.getDefaultRoute.mockReturnValue('/login')
    store.fetchPermissions.mockResolvedValue('ok')
    router = createV2Router(createMemoryHistory())
  })

  it('未登录访问 /admin/questions：跳 /login 且带 redirect，不发权限探针', async () => {
    await router.push('/admin/questions')

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/admin/questions')
    // 未登录时不该先探权限（原有顺序：登录态先于 permissionsLoaded）
    expect(store.fetchPermissions).not.toHaveBeenCalled()
  })

  it('学生角色访问管理页：落 /403，不渲染目标页', async () => {
    store.isLoggedIn = true
    store.userRole = 'STUDENT'
    store.effectiveRole = 'STUDENT'
    store.permissions = { menus: [], buttons: {} }
    store.permissionsLoaded = true

    await router.push('/admin/questions')

    expect(router.currentRoute.value.path).toBe('/403')
    expect(router.currentRoute.value.path.startsWith('/admin')).toBe(false)
  })

  it('权限探针返回 expired：不渲染受保护页，带 redirect 跳 /login', async () => {
    store.isLoggedIn = true
    store.userRole = 'ADMIN'
    store.effectiveRole = 'ADMIN'
    store.permissions = null
    store.permissionsLoaded = false
    // 真实 store 在 401 分支会清空本地登录态
    store.fetchPermissions.mockImplementation(async () => {
      store.isLoggedIn = false
      return 'expired'
    })

    await router.push('/admin/questions')

    expect(router.currentRoute.value.path).toBe('/login')
    expect(router.currentRoute.value.query.redirect).toBe('/admin/questions')
  })

  it('权限探针返回 unavailable：落 /503 并保留登录态（不可达 ≠ 无权限）', async () => {
    store.isLoggedIn = true
    store.userRole = 'TEACHER'
    store.effectiveRole = 'TEACHER'
    store.permissions = null
    store.permissionsLoaded = false
    store.fetchPermissions.mockResolvedValue('unavailable')

    await router.push('/admin/questions')

    expect(router.currentRoute.value.path).toBe('/503')
    expect(router.currentRoute.value.query.redirect).toBe('/admin/questions')
    expect(store.isLoggedIn).toBe(true)
  })

  it('角色匹配：放行到目标页', async () => {
    store.isLoggedIn = true
    store.userRole = 'STUDENT'
    store.effectiveRole = 'STUDENT'
    store.permissions = { menus: [], buttons: {} }
    store.permissionsLoaded = true

    await router.push('/student/home')

    expect(router.currentRoute.value.path).toBe('/student/home')
  })

  it('/503 自身不再触发权限探针，避免不可达时形成轮询环', async () => {
    store.isLoggedIn = true
    store.effectiveRole = 'TEACHER'
    store.permissionsLoaded = false

    await router.push('/503')

    expect(router.currentRoute.value.path).toBe('/503')
    expect(store.fetchPermissions).not.toHaveBeenCalled()
  })
})
