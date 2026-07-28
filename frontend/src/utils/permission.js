export function getDefaultRouteByRole(role) {
  switch (role) {
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
 * 路由访问判定。
 *
 * 注意：本函数当前未被业务代码直接调用（路由守卫 router/index.js
 * 的 beforeEach 以 meta.role 严格相等作为唯一权威判定）。保留此函数
 * 仅作语义参考，且已与守卫对齐为「严格角色相等」模型（最小权限默认），
 * 避免后续误用时与守卫产生矛盾。如需「角色层级穿透」（ADMIN 可访问
 * TEACHER/STUDENT 区）请同时修改此处与 router/index.js 的判定逻辑。
 */
export function canAccessRoute(role, path) {
  if (!role) return false
  if (path.startsWith('/admin')) return role === 'ADMIN'
  if (path.startsWith('/teacher')) return role === 'TEACHER'
  if (path.startsWith('/student')) return role === 'STUDENT'
  return true
}
