import { useAuthStore } from '@/stores/auth'

/**
 * 前端权限检查组合式函数
 *
 * 提供菜单权限与按钮权限的便捷检查方法，所有方法遵循 **fail-closed** 策略：
 * 权限数据未加载或加载失败时一律返回 `false`，默认拒绝访问，确保安全边界。
 *
 * @returns {{ hasMenuPermission: Function, hasButtonPermission: Function, hasAnyPermission: Function, hasAllPermissions: Function }}
 */
export function usePermission() {
  const authStore = useAuthStore()
  /**
   * 检查当前用户是否拥有指定菜单权限
   * @param {string} menuKey - 菜单权限标识（如 "course-management"）
   * @returns {boolean} 是否拥有权限（fail-closed：权限数据未加载或 Key 不存在时返回 false）
   */
  const hasMenuPermission = (menuKey) => authStore.hasMenuPermission(menuKey)
  /**
   * 检查当前用户是否拥有指定按钮权限
   * @param {string} buttonKey - 按钮权限标识（如 "course:create"）
   * @returns {boolean} 是否拥有权限（fail-closed：权限数据未加载或 Key 不存在时返回 false）
   */
  const hasButtonPermission = (buttonKey) => authStore.hasButtonPermission(buttonKey)
  const hasAnyPermission = (keys, type = 'button') => {
    if (!keys || !Array.isArray(keys)) return false
    return keys.some((key) =>
      type === 'button' ? hasButtonPermission(key) : hasMenuPermission(key),
    )
  }
  const hasAllPermissions = (keys, type = 'button') => {
    if (!keys || !Array.isArray(keys)) return false
    return keys.every((key) =>
      type === 'button' ? hasButtonPermission(key) : hasMenuPermission(key),
    )
  }
  return { hasMenuPermission, hasButtonPermission, hasAnyPermission, hasAllPermissions }
}
