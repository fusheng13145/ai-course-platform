import { useAuthStore } from '@/stores/auth'

export function usePermission() {
  const authStore = useAuthStore()
  const hasMenuPermission = (menuKey) => authStore.hasMenuPermission(menuKey)
  const hasButtonPermission = (buttonKey) => authStore.hasButtonPermission(buttonKey)
  const hasAnyPermission = (keys, type = 'button') => {
    if (!keys || !Array.isArray(keys)) return false
    return keys.some(key => type === 'button' ? hasButtonPermission(key) : hasMenuPermission(key))
  }
  const hasAllPermissions = (keys, type = 'button') => {
    if (!keys || !Array.isArray(keys)) return false
    return keys.every(key => type === 'button' ? hasButtonPermission(key) : hasMenuPermission(key))
  }
  return { hasMenuPermission, hasButtonPermission, hasAnyPermission, hasAllPermissions }
}