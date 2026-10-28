export type V2Role = 'student' | 'teacher' | 'admin'

export const ROLE_LABEL: Record<V2Role, string> = {
  student: '学生',
  teacher: '教师',
  admin: '管理员',
}

export const ROLE_KICKER: Record<V2Role, string> = {
  student: 'STUDENT SPACE',
  teacher: 'TEACHER SPACE',
  admin: 'ADMIN SPACE',
}

export const ROLE_HOME: Record<V2Role, string> = {
  student: '/v2/student/home',
  teacher: '/v2/teacher/home',
  admin: '/v2/admin/home',
}

export const LEGACY_HOME: Record<V2Role, string> = {
  student: '/student/dashboard',
  teacher: '/teacher/dashboard',
  admin: '/admin/dashboard',
}

export function roleOf(value: unknown): V2Role {
  const raw = String(value || '')
    .trim()
    .toLowerCase()
  if (raw === 'teacher') return 'teacher'
  if (raw === 'admin') return 'admin'
  return 'student'
}
