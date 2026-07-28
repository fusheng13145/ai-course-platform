import logger from '@/utils/logger'
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import authApi from '@/api/auth'
import { connect as wsConnect } from '@/utils/websocket'
import { unwrap } from '@/utils/response'

export const useAuthStore = defineStore('auth', () => {
  // 访问令牌存于后端下发的 HttpOnly Cookie，前端不持有、不持久化、不可读。
  // token 仅作为内存态占位，用于兼容既有引用；绝不写入 localStorage/sessionStorage。
  const token = ref('')
  const user = ref(
    JSON.parse(localStorage.getItem('user') || sessionStorage.getItem('user') || 'null'),
  )
  const permissions = ref(
    JSON.parse(
      localStorage.getItem('permissions') || sessionStorage.getItem('permissions') || 'null',
    ),
  )
  // 权限是否已成功加载。未加载或加载失败时一律按"无权限"处理（fail-closed），
  // 杜绝 fetchPermissions 异常被静默吞掉后所有按钮/菜单被错误放开的安全漏洞。
  const permissionsLoaded = ref(
    !!(localStorage.getItem('permissions') || sessionStorage.getItem('permissions')),
  )
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

  function saveToStorage(persistent) {
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
    clearAllStorage()
  }

  const getDefaultRoute = () => {
    switch (userRole.value) {
      case 'ADMIN':
        return '/admin/dashboard'
      case 'TEACHER':
        return '/teacher/dashboard'
      case 'STUDENT':
        return '/student/dashboard'
      default:
        return '/login'
    }
  }

  /**
   * 获取当前用户权限列表
   * 请求后端接口获取菜单和按钮权限数据，存储到 Pinia state 和 localStorage/sessionStorage 中
   * 加载失败时保持 permissionsLoaded=false 以 fail-closed 方式拒绝所有权限判定
   * @returns {Promise<void>}
   */
  const fetchPermissions = async () => {
    try {
      const response = await authApi.getPermissions()
      const data = unwrap(response)
      if (data) {
        permissions.value = data
        permissionsLoaded.value = true
        const storage = persistent.value ? localStorage : sessionStorage
        storage.setItem('permissions', JSON.stringify(data))
      }
    } catch (error) {
      // fail-closed：加载失败时保持 permissionsLoaded=false，使权限判定统一拒绝，
      // 而非错误地放开全部按钮/菜单。仅记录日志，不静默放行。
      logger.error('Failed to fetch permissions:', error)
    }
  }

  // 安全约定：以下前端权限仅用于界面展示优化（隐藏无权限的按钮/菜单）。
  // 真正的访问控制由后端对象级鉴权兜底，前端被绕过也不影响数据安全。

  const hasMenuPermission = (menuKey) => {
    // 未加载或无权限数据时拒绝（fail-closed）
    if (!permissionsLoaded.value || !permissions.value) return false
    const menus = permissions.value.menus || []
    return menus.includes(menuKey)
  }

  const hasButtonPermission = (buttonKey) => {
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
  const login = async (credentials) => {
    try {
      isLoading.value = true
      const remember = credentials.remember !== false
      const response = await authApi.login({
        account: credentials.username,
        password: credentials.password,
        rememberMe: remember,
      })
      const data = unwrap(response)
      if (data.user) {
        // 令牌已写入 HttpOnly Cookie，前端仅持有内存态占位，不持久化
        token.value = data.token || ''
        user.value = data.user
        persistent.value = remember
        clearAllStorage()
        saveToStorage(remember)
        await fetchPermissions()
        // 登录成功后自动连接 WebSocket：握手经同源/同站 Cookie 自动携带令牌，无需传参
        wsConnect().catch(() => {
          logger.warn('WebSocket 连接失败，实时功能已禁用')
        })
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
   * @param {Object} userData - 注册信息
   * @param {string} userData.username - 用户名
   * @param {string} userData.password - 密码
   * @param {string} userData.email - 邮箱
   * @param {string} userData.fullName - 真实姓名
   * @returns {Promise<{success: boolean, data: Object}>} 注册结果
   */
  const register = async (userData) => {
    try {
      isLoading.value = true
      const response = await authApi.register(userData)
      return { success: true, data: unwrap(response) }
    } finally {
      isLoading.value = false
    }
  }

  /**
   * 退出登录
   * 调用登出 API 后清空前端登录态（内存 + 存储），后端 Cookie 由 /auth/logout 清除
   * @returns {Promise<void>}
   */
  const logout = async () => {
    try {
      if (token.value || user.value) await authApi.logout()
    } catch (e) {
      /* ignore */
    } finally {
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
    userName,
    userAvatar,
    login,
    register,
    logout,
    resetAuthState,
    getDefaultRoute,
    fetchPermissions,
    hasMenuPermission,
    hasButtonPermission,
  }
})
