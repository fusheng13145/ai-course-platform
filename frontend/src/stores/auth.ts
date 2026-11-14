import logger from '@/utils/logger'
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { isAxiosError } from 'axios'
import authApi from '@/api/auth'
import { connect as wsConnect, disconnect as wsDisconnect } from '@/utils/websocket'
import { unwrap } from '@/utils/response'

/**
 * 安全读取并解析本地存储中的 JSON。
 *
 * 缺陷修复（P0-2）：原实现 `JSON.parse(localStorage.getItem('user') || … || 'null')`
 * 无任何容错——一旦存储里残留脏数据（人工改写、旧版本写入的非 JSON 字符串、
 * 被截断的 JSON），Pinia store 工厂在**初始化阶段**就抛出，导致整个应用白屏。
 * 这里解析失败时清掉该脏键并回退 null（即"未登录/无权限"），保证 store 永远能初始化。
 *
 * @param {Storage} storage 存储介质
 * @param {string} key 键名
 * @returns {{value: unknown, dirty: boolean}} 解析结果与是否为脏数据
 */
function readJson(storage: Storage, key: string) {
  const raw = storage.getItem(key)
  if (raw === null || raw === '') return { value: null, dirty: false }
  try {
    return { value: JSON.parse(raw), dirty: false }
  } catch {
    return { value: null, dirty: true }
  }
}

/**
 * 组合式函数 useAuthStore：封装可复用的响应式状态与逻辑
 */
export const useAuthStore = defineStore('auth', () => {
  // 访问令牌存于后端下发的 HttpOnly Cookie，前端不持有、不持久化、不可读。
  // token 仅作为内存态占位，用于兼容既有引用；绝不写入 localStorage/sessionStorage。
  const token = ref('')

  // user / permissions 的读取采用「localStorage 优先、sessionStorage 兜底」，
  // 任一介质脏数据都单独清理，绝不因解析失败而抛错（见 readJson）。
  const rawUserLocal = readJson(localStorage, 'user')
  const rawUserSession = readJson(sessionStorage, 'user')
  if (rawUserLocal.dirty) localStorage.removeItem('user')
  if (rawUserSession.dirty) sessionStorage.removeItem('user')

  const rawPermLocal = readJson(localStorage, 'permissions')
  const rawPermSession = readJson(sessionStorage, 'permissions')
  if (rawPermLocal.dirty) localStorage.removeItem('permissions')
  if (rawPermSession.dirty) sessionStorage.removeItem('permissions')

  const user = ref(rawUserLocal.value ?? rawUserSession.value ?? null)
  const permissions = ref(rawPermLocal.value ?? rawPermSession.value ?? null)
  // 权限是否已成功加载。
  //
  // 安全收紧（P0 配套）：原实现用 `localStorage.getItem('permissions')` 的**存在性**
  // 作为「已加载」——攻击者只要在控制台写入一份 permissions 字符串，就能凭空点亮
  // permissionsLoaded，绕过 fail-closed 判定。现在该标记**只由本会话内一次真实的
  // /auth/permissions 成功响应置位**，本地缓存只用于渲染、不作为授权凭据。
  const permissionsLoaded = ref(false)
  const isLoading = ref(false)
  // 是否"记住我"：决定非敏感 user/permissions 的持久化介质（localStorage/sessionStorage）。
  // 令牌本身由 HttpOnly Cookie 管理，与此无关。
  const persistent = ref(false)

  // 登录态以非敏感的 user 对象是否存在为准（真实鉴权由后端 Cookie + 对象级校验兜底）
  const isLoggedIn = computed(() => !!user.value)
  const userId = computed(() => user.value?.id || null)
  const userRole = computed(() => user.value?.role || '')
  const userName = computed(() => user.value?.fullName || user.value?.username || '')
  const userAvatar = computed(() => user.value?.avatarUrl || '')

  /**
   * 服务端在 /auth/permissions 响应中回传的角色（存在即优先于本地 user.role）。
   *
   * 安全收紧：`user` 对象明文存在 localStorage，任何人在控制台把 role 改成
   * 'ADMIN' 就能改到本地视图 —— 这条路径不能用来判定「能不能进管理端」。
   */
  const serverRole = ref('')

  /**
   * 路由鉴权使用的角色。
   *
   * 规则：
   * - 服务端回传了角色 → 以服务端为准（本地篡改无效）；
   * - 否则退回本地 user.role，但 **ADMIN 额外要求本会话内已成功拉取过服务端权限**
   *   （permissionsLoaded 只由真实响应置位，localStorage 无法伪造）。
   *   即：手工把 localStorage 的 user.role 改成 ADMIN 时，本值为 ''，
   *   管理端路由依然进不去；真正的提权闸门在服务端 Cookie + 对象级鉴权。
   */
  const effectiveRole = computed(() => {
    if (serverRole.value) return serverRole.value
    if (userRole.value === 'ADMIN') return permissionsLoaded.value ? 'ADMIN' : ''
    return userRole.value
  })

  function saveToStorage(persistent: boolean) {
    // 仅持久化非敏感的 user / permissions；令牌（HttpOnly Cookie）绝不落前端存储
    const storage = persistent ? localStorage : sessionStorage
    if (user.value) storage.setItem('user', JSON.stringify(user.value))
    if (permissions.value) storage.setItem('permissions', JSON.stringify(permissions.value))
  }

  function clearAllStorage() {
    // 清理可能残留的旧令牌键，避免遗留 localStorage 中的令牌被误用
    localStorage.removeItem('token')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('user')
    localStorage.removeItem('permissions')
    sessionStorage.removeItem('token')
    sessionStorage.removeItem('refreshToken')
    sessionStorage.removeItem('user')
    sessionStorage.removeItem('permissions')
  }

  /**
   * 清空前端登录态（内存 + 存储）。
   * 仅清理客户端状态；后端会话 Cookie 由 /auth/logout 在后端清除。
   */
  function resetAuthState() {
    token.value = ''
    user.value = null
    permissions.value = null
    permissionsLoaded.value = false
    serverRole.value = ''
    clearAllStorage()
  }

  /**
   * 登录后的默认落地路由。
   *
   * 缺陷修复（P0-3）：原实现对「已登录但角色未知」返回 `/login`，而路由守卫在
   * 已登录访问 `/login` 时又 `next(getDefaultRoute())`，形成 **`/login → /login`
   * 自指向重定向**。`/403` 的 `meta.requiresAuth === false`，是无条件可达且不会再
   * 触发的跳转的安全落点，故未知角色改落 `/403`。
   * 仅当完全没有登录态（无 user 对象）时才回落 `/login`——此时守卫不会调用本函数。
   *
   * 修复（2026-11-14 审计 D1）：三端返回值原为 `/xx/dashboard`，而路由表只有
   * `/xx/home`（`ui/types.ts` 的 `ROLE_HOME` 是单一来源）——守卫据此
   * `next(target)` 会落进 catch-all 404。现与 ROLE_HOME 逐字对齐。
   */
  const getDefaultRoute = () => {
    switch (userRole.value) {
      case 'ADMIN':
        return '/admin/home'
      case 'TEACHER':
        return '/teacher/home'
      case 'STUDENT':
        return '/student/home'
      default:
        // 已登录但角色缺失/未知 → 安全落点 /403（可达、无守卫、不成环）
        return user.value ? '/403' : '/login'
    }
  }

  /**
   * 获取当前用户权限列表，并回传**服务端的会话结论**。
   *
   * 缺陷修复（DR-45）：原实现无论成功失败都返回 undefined，失败只在控制台留一行日志。
   * 路由守卫因此处在「刚 await 了这次请求、却读不到它的结果」的位置，只能无条件 next()
   * 放行 —— 本地 localStorage 里残留的 user 让 `isLoggedIn` 恒为真，于是登录态早已过期的
   * 用户刷新页面后仍看到已登录的界面；与此同时 401 收尾逻辑又在拦截器里各自
   * `router.push('/login')`，两条导航互相取消，最终表现为「页面显示已登录、点什么都没反应，
   * 再点一次才跳登录页」。
   *
   * 结论分三种，守卫据此决定唯一的一次导航：
   * - `'ok'`：服务端确认了本次会话，正常放行；
   * - `'expired'`：服务端明确 401（含静默刷新也失败后的会话失效），就地清空本地登录态，
   *   由守卫带 `redirect` 跳登录页；
   * - `'unavailable'`：网络异常/5xx 等**无法判定**的情形，保留本地登录态继续渲染
   *   （拦截器另有重试），后端仍是真正的准入闸门。
   *
   * @returns {Promise<'ok'|'expired'|'unavailable'>} 服务端会话结论
   */
  const fetchPermissions = async () => {
    try {
      const response = await authApi.getPermissions()
      const data = unwrap(response)
      if (data) {
        permissions.value = data
        // 只由这一次**真实服务端成功响应**置位；本地缓存不参与（见上方注释）
        permissionsLoaded.value = true
        // 服务端若回传角色，作为路由鉴权的权威来源，压过可篡改的本地 user.role
        if (typeof data === 'object' && (data.role || data.userRole)) {
          serverRole.value = String(data.role || data.userRole)
        }
        const storage = persistent.value ? localStorage : sessionStorage
        storage.setItem('permissions', JSON.stringify(data))
        // 实时通道在此建立，而不是只在 login() 里建立。
        // 缺陷修复（Q-3 验证 2026-10-06）：刷新页面时 Cookie 会话仍然有效、但 login() 不会重跑，
        // 原实现只在 login/register 里 connect → 「按 F5 之后 WebSocket 永久不再连接」，
        // 考试实时与通知功能静默失效，前端 5 次退避重连的预算也救不回来（没有新的 connect 入口）。
        // fetchPermissions 由路由守卫在每次冷启动时调用，且 connect() 对 OPEN/CONNECTING
        // 幂等早退，因此放这里既能覆盖刷新场景，也不会叠出多余连接。
        wsConnect().catch(() => {
          logger.warn('WebSocket 连接失败，实时功能已禁用')
        })
        return 'ok'
      }
      // 200 但无权限载荷：无法据此确认会话，按「不可判定」处理，不把用户误踢出登录。
      logger.warn('权限响应为空，按会话不可判定处理')
      return 'unavailable'
    } catch (error) {
      // fail-closed：加载失败时保持 permissionsLoaded=false，使权限判定统一拒绝，
      // 而非错误地放开全部按钮/菜单。
      logger.error('Failed to fetch permissions:', error)
      if (isAxiosError(error) && error.response?.status === 401) {
        // 服务端明确拒绝本次会话（拦截器的静默刷新也已失败）：清空本地登录态并回传结论，
        // 跳转动作交给路由守卫这唯一出口。
        resetAuthState()
        return 'expired'
      }
      return 'unavailable'
    }
  }

  // 安全约定：以下前端权限仅用于界面展示优化（隐藏无权限的按钮/菜单）。
  // 真正的访问控制由后端对象级鉴权兜底，前端被绕过也不影响数据安全。

  const hasMenuPermission = (menuKey: string) => {
    // 未加载或无权限数据时拒绝（fail-closed）
    if (!permissionsLoaded.value || !permissions.value) return false
    const menus = permissions.value.menus || []
    return menus.includes(menuKey)
  }

  const hasButtonPermission = (buttonKey: string) => {
    // 未加载或无权限数据时拒绝（fail-closed）
    if (!permissionsLoaded.value || !permissions.value) return false
    const buttons = permissions.value.buttons || {}
    if (Object.keys(buttons).length === 0) return false
    return !!buttons[buttonKey]
  }

  /**
   * 用户登录
   * @param {Object} credentials - 登录凭据
   * @param {string} credentials.username - 用户名
   * @param {string} credentials.password - 密码
   * @param {boolean} [credentials.remember=true] - 是否记住登录状态
   * @returns {Promise<{success: boolean, user: Object}>} 登录结果，包含用户信息
   * @throws {Error} 登录失败时抛出错误
   */
  const login = async (credentials: {
    username: string
    password: string
    remember?: boolean
    rememberMe?: boolean
  }) => {
    try {
      isLoading.value = true
      const remember = credentials.remember !== false
      const response = await authApi.login({
        username: credentials.username,
        password: credentials.password,
        rememberMe: remember,
      })
      const data = unwrap(response)
      if (data && data.user) {
        // 令牌已写入 HttpOnly Cookie，前端仅持有内存态占位，不持久化
        token.value = String(data.token || '')
        user.value = data.user
        persistent.value = remember
        clearAllStorage()
        saveToStorage(remember)
        await fetchPermissions()
        return { success: true, user: user.value }
      }
      throw new Error('登录失败')
    } catch (error) {
      logger.error('Login error:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 用户注册
   * 注册接口后端已写入认证 Cookie（HttpOnly）并返回最小用户信息（userId/username/role），
   * 此处直接建立前端登录态并拉取权限，避免「注册成功跳 dashboard 被守卫弹回登录页」。
   * @param {Object} userData - 注册信息
   * @param {string} userData.username - 用户名
   * @param {string} userData.password - 密码
   * @param {string} userData.email - 邮箱
   * @param {string} userData.fullName - 真实姓名
   * @returns {Promise<{success: boolean, data: Object}>} 注册结果
   */
  const register = async (userData: Record<string, unknown>) => {
    try {
      isLoading.value = true
      const response = await authApi.register(userData)
      const data = unwrap(response)
      // 缺陷修复：原实现无论后端是否回传身份，一律 `return { success: true }`，
      // 于是「注册接口 200 但 data 里没有 userId」时前端仍提示注册成功，
      // 而登录态并未建立 —— 用户被扔到需要登录的页面再被守卫弹回，全程无报错。
      // 现在只有拿到服务端身份才算成功。
      if (!data?.userId) {
        logger.warn('注册响应缺少 userId，未建立登录态', data)
        return { success: false, data }
      }
      user.value = { id: data.userId, username: data.username, role: data.role }
      persistent.value = false
      clearAllStorage()
      saveToStorage(false)
      await fetchPermissions()
      return { success: true, data }
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 发送找回密码验证码（类型 FORGOT_PASSWORD，与后端 AuthServiceImpl 校验口径一致）
   * @param {string} email - 注册邮箱
   * @returns {Promise<void>}
   */
  const sendResetCode = async (email: string) => {
    await authApi.sendVerificationCode({ email, type: 'FORGOT_PASSWORD' })
  }

  /**
   * 校验找回密码验证码（仅校验不消费，供第二步「验证邮箱」使用）
   * @param {string} email - 注册邮箱
   * @param {string} code - 验证码
   * @returns {Promise<void>}
   */
  const verifyResetCode = async (email: string, code: string) => {
    await authApi.verifyResetCode({ email, code })
  }

  /**
   * 邮箱验证码重置密码
   * @param {string} email - 注册邮箱
   * @param {string} code - 验证码
   * @param {string} newPassword - 新密码
   * @returns {Promise<void>}
   */
  const resetPassword = async (email: string, code: string, newPassword: string) => {
    await authApi.resetPassword({ email, code, newPassword })
  }

  /**
   * 退出登录
   * 调用登出 API 后清空前端登录态（内存 + 存储），后端 Cookie 由 /auth/logout 清除
   * @returns {Promise<void>}
   */
  const logout = async () => {
    try {
      if (token.value || user.value) await authApi.logout()
    } catch {
      /* ignore */
    } finally {
      wsDisconnect()
      resetAuthState()
    }
  }

  return {
    token,
    user,
    permissions,
    permissionsLoaded,
    isLoading,
    persistent,
    isLoggedIn,
    userId,
    userRole,
    effectiveRole,
    serverRole,
    userName,
    userAvatar,
    login,
    register,
    sendResetCode,
    verifyResetCode,
    resetPassword,
    logout,
    resetAuthState,
    getDefaultRoute,
    fetchPermissions,
    hasMenuPermission,
    hasButtonPermission,
  }
})
