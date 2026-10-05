import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

/**
 * 认证流转编排测试（真实 store + 真实跳转校验工具）
 *
 * <p>本文件此前用 `createRouter` 造了个**没有守卫的玩具路由**，断言的是测试里
 * 手写的字面量对象（`roleRoutes.ADMIN === '/admin/dashboard'`），产品代码 0 覆盖。
 * 现改为把「登录成功 → 决定去哪」这条真实编排链钉住：
 * ① 角色落地页取自真实 `useAuthStore().getDefaultRoute()`；
 * ② URL 上的 redirect 参数必须经真实 `resolvePostAuthRedirect` 校验（P0-6）；
 * ③ 已登录但角色未知时落 `/403`，绝不落 `/login`（P0-3 自指向重定向的编排侧证据）；
 * ④ 登出后登录态与存储一并清空。</p>
 */

const mocks = vi.hoisted(() => ({
  login: vi.fn(),
  logout: vi.fn(),
  getPermissions: vi.fn(),
  wsConnect: vi.fn(),
  wsDisconnect: vi.fn(),
}))

vi.mock('@/api/auth', () => ({
  default: { login: mocks.login, logout: mocks.logout, getPermissions: mocks.getPermissions },
}))
vi.mock('@/utils/websocket', () => ({
  connect: mocks.wsConnect,
  disconnect: mocks.wsDisconnect,
}))
vi.mock('@/utils/logger', () => ({
  default: { log: vi.fn(), warn: vi.fn(), error: vi.fn() },
}))

import { useAuthStore } from '@/stores/auth'
import { resolvePostAuthRedirect } from '@/utils/redirect'

/**
 * 复刻 Login.vue / Register.vue 登录成功后的那一行决策逻辑，
 * 使「store 落地页 + URL redirect 参数」的组合行为可被断言。
 */
function decidePostAuthTarget(queryRedirect: unknown) {
  const auth = useAuthStore()
  return resolvePostAuthRedirect(queryRedirect, auth.getDefaultRoute())
}

function userWith(role?: string) {
  return role === undefined ? { id: 1, username: 'u' } : { id: 1, username: 'u', role }
}

async function loginAs(role?: string) {
  mocks.login.mockResolvedValue({ data: { user: userWith(role), token: 'x' } })
  mocks.getPermissions.mockResolvedValue({ data: { menus: [], buttons: {} } })
  const auth = useAuthStore()
  await auth.login({ username: 'u', password: 'p', remember: false })
  return auth
}

describe('认证流转 — 角色落地页', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
    sessionStorage.clear()
    mocks.wsConnect.mockResolvedValue(undefined)
  })

  it.each([
    ['ADMIN', '/admin/dashboard'],
    ['TEACHER', '/teacher/dashboard'],
    ['STUDENT', '/student/dashboard'],
  ])('%s 登录后落到 %s', async (role, expected) => {
    await loginAs(role)
    expect(decidePostAuthTarget(undefined)).toBe(expected)
  })

  it('已登录但角色缺失时落 /403，绝不落 /login（P0-3 回归）', async () => {
    await loginAs()

    const target = decidePostAuthTarget(undefined)
    expect(target).toBe('/403')
    // 落在 /login 会与「已登录访问 /login 跳默认落地页」的守卫形成成环
    expect(target).not.toBe('/login')
  })

  it('登出后登录态清空，默认落点回到 /login 且存储不留 user', async () => {
    const auth = await loginAs('STUDENT')
    expect(sessionStorage.getItem('user')).not.toBeNull()

    mocks.logout.mockResolvedValue({ data: null })
    await auth.logout()

    expect(auth.isLoggedIn).toBe(false)
    expect(auth.getDefaultRoute()).toBe('/login')
    expect(sessionStorage.getItem('user')).toBeNull()
    expect(localStorage.getItem('user')).toBeNull()
  })
})

describe('认证流转 — redirect 参数与落地页的组合', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
    sessionStorage.clear()
    mocks.wsConnect.mockResolvedValue(undefined)
  })

  it('合法站内 redirect 优先于角色默认落地页', async () => {
    await loginAs('STUDENT')
    expect(decidePostAuthTarget('/student/courses?tab=enrolled')).toBe(
      '/student/courses?tab=enrolled',
    )
  })

  it.each(['//evil.example', 'https://evil.example', 'javascript:alert(1)', '/\\evil.example'])(
    '攻击者可控的 %s 必须回落到角色落地页，不得外跳',
    async (attack) => {
      await loginAs('STUDENT')
      expect(decidePostAuthTarget(attack)).toBe('/student/dashboard')
    },
  )

  it('角色未知 + 攻击者 redirect 的组合也只会落到安全落点', async () => {
    await loginAs()
    const target = decidePostAuthTarget('//evil.example')
    expect(target).toBe('/403')
  })
})

describe('认证流转 — 登录失败不产生跳转凭据', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
    sessionStorage.clear()
  })

  it('后端拒绝时 store 抛错、登录态为空，默认落点仍是 /login', async () => {
    mocks.login.mockRejectedValue({ response: { data: { message: '用户名或密码错误' } } })
    const auth = useAuthStore()

    await expect(auth.login({ username: 'u', password: 'bad' })).rejects.toBeTruthy()
    expect(auth.isLoggedIn).toBe(false)
    expect(auth.getDefaultRoute()).toBe('/login')
    expect(mocks.wsConnect).not.toHaveBeenCalled()
  })

  it('响应缺少 user 时不得置为已登录（防假成功）', async () => {
    mocks.login.mockResolvedValue({ data: { token: 'x' } })
    const auth = useAuthStore()

    await expect(auth.login({ username: 'u', password: 'p' })).rejects.toThrow('登录失败')
    expect(auth.isLoggedIn).toBe(false)
  })
})
