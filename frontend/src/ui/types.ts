export type Role = 'student' | 'teacher' | 'admin'

export const ROLE_LABEL: Record<Role, string> = {
  student: '学生',
  teacher: '教师',
  admin: '管理员',
}

export const ROLE_HOME: Record<Role, string> = {
  student: '/student/home',
  teacher: '/teacher/home',
  admin: '/admin/home',
}

export function roleOf(value: unknown): Role {
  const raw = String(value || '')
    .trim()
    .toLowerCase()
  if (raw === 'teacher') return 'teacher'
  if (raw === 'admin') return 'admin'
  return 'student'
}
