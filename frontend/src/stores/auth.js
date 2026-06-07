import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import authApi from '@/api/auth'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || sessionStorage.getItem('token') || '')
  const user = ref(JSON.parse(localStorage.getItem('user') || sessionStorage.getItem('user') || 'null'))
  const permissions = ref(JSON.parse(localStorage.getItem('permissions') || sessionStorage.getItem('permissions') || 'null'))
  const isLoading = ref(false)

  const isLoggedIn = computed(() => !!token.value && !!user.value)
  const userId = computed(() => user.value?.id || null)
  const userRole = computed(() => user.value?.role || '')
  const userName = computed(() => user.value?.fullName || user.value?.username || '')
  const userAvatar = computed(() => user.value?.avatarUrl || '')

  function saveToStorage(persistent) {
    const storage = persistent ? localStorage : sessionStorage
    storage.setItem('token', token.value)
    storage.setItem('user', JSON.stringify(user.value))
    if (permissions.value) storage.setItem('permissions', JSON.stringify(permissions.value))
  }

  function clearAllStorage() {
    localStorage.removeItem('token'); localStorage.removeItem('user'); localStorage.removeItem('permissions')
    sessionStorage.removeItem('token'); sessionStorage.removeItem('user'); sessionStorage.removeItem('permissions')
  }

  const getDefaultRoute = () => {
    switch (userRole.value) {
      case 'ADMIN': return '/admin/dashboard'
      case 'TEACHER': return '/teacher/dashboard'
      case 'STUDENT': return '/student/dashboard'
      default: return '/login'
    }
  }

  const fetchPermissions = async () => {
    try {
      const response = await authApi.getPermissions()
      const data = response.data || response
      if (data) {
        permissions.value = data
        const storage = localStorage.getItem('token') ? localStorage : sessionStorage
        storage.setItem('permissions', JSON.stringify(data))
      }
    } catch (error) {
      console.error('Failed to fetch permissions:', error)
    }
  }

  const hasMenuPermission = (menuKey) => {
    if (!permissions.value) return true
    const menus = permissions.value.menus || []
    return menus.includes(menuKey)
  }

  const hasButtonPermission = (buttonKey) => {
    if (!permissions.value) return true
    const buttons = permissions.value.buttons || {}
    if (Object.keys(buttons).length === 0) return true
    return !!buttons[buttonKey]
  }

  const login = async (credentials) => {
    try {
      isLoading.value = true
      const remember = credentials.remember !== false
      const response = await authApi.login({
        account: credentials.username,
        password: credentials.password,
        rememberMe: remember
      })
      const data = response.data || response
      if (data.token) {
        token.value = data.token
        user.value = data.user
        clearAllStorage()
        saveToStorage(remember)
        await fetchPermissions()
        return { success: true, user: user.value }
      }
      throw new Error('登录失败')
    } catch (error) {
      console.error('Login error:', error)
      throw error
    } finally {
      isLoading.value = false
    }
  }

  const register = async (userData) => {
    try {
      isLoading.value = true
      const response = await authApi.register(userData)
      return { success: true, data: response.data }
    } catch (error) {
      throw error
    } finally {
      isLoading.value = false
    }
  }

  const logout = async () => {
    try {
      if (token.value) await authApi.logout()
    } catch (e) { /* ignore */ }
    finally {
      token.value = ''; user.value = null; permissions.value = null
      clearAllStorage()
    }
  }

  return {
    token, user, permissions, isLoading,
    isLoggedIn, userId, userRole, userName, userAvatar,
    login, register, logout, getDefaultRoute,
    fetchPermissions, hasMenuPermission, hasButtonPermission
  }
})