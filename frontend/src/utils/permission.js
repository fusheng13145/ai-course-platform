export function getDefaultRouteByRole(role) {
  switch (role) {
    case "ADMIN": return "/admin/dashboard"
    case "TEACHER": return "/teacher/dashboard"
    case "STUDENT": return "/student/dashboard"
    default: return "/login"
  }
}
export function canAccessRoute(role, path) {
  if (!role) return false
  if (path.startsWith("/admin")) return role === "ADMIN"
  if (path.startsWith("/teacher")) return role === "TEACHER" || role === "ADMIN"
  if (path.startsWith("/student")) return role === "STUDENT" || role === "TEACHER" || role === "ADMIN"
  return true
}
