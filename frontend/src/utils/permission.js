export function canAccessRoute(userRole, path) {
  const adminPaths = ['/admin']
  const teacherPaths = ['/teacher']
  const studentPaths = ['/student', '/common']

  if (userRole === 'ADMIN' && adminPaths.some(p => path.startsWith(p))) return true
  if (userRole === 'TEACHER' && teacherPaths.some(p => path.startsWith(p))) return true
  if (userRole === 'STUDENT' && studentPaths.some(p => path.startsWith(p))) return true
  return false
}

export function getDefaultRouteByRole(role) {
  switch (role) {
    case 'ADMIN': return '/admin/dashboard'
    case 'TEACHER': return '/teacher/dashboard'
    case 'STUDENT': return '/student/dashboard'
    default: return '/login'
  }
}