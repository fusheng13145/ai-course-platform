import teacherApi from '@/api/teacher'
import studentApi from '@/api/student'
import courseApi from '@/api/course'
import adminApi from '@/api/admin'
import analyticsApi from '@/api/analytics'
import notificationApi from '@/api/notification'
import { pickList, unwrap } from '@/utils/response'
import type { Role } from '../nav'

export interface CourseCard {
  id: number
  name: string
  code: string
  teacher: string
  semester: string
  progress: number
  students: number
  pending: number
  status: string
  short: string
  tone: number
}

export interface CourseDetail {
  id: number
  name: string
  code: string
  teacher: string
  semester: string
  credits: number | null
  status: string
  approvalStatus: string
  category: string
  description: string
}

const tone = (id: number) => (Math.abs(Number(id) || 1) % 5) + 1

const shortOf = (name: string, code: string) => {
  const ascii = String(code || '')
    .replace(/[^A-Za-z0-9]/g, '')
    .slice(0, 4)
  return ascii || String(name || '课程').slice(0, 2)
}

function asNumber(value: unknown, fallback = 0): number {
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

function normalizeCard(raw: Record<string, unknown>): CourseCard {
  const id = asNumber(raw.courseId ?? raw.id ?? 0)
  const name = String(raw.name ?? raw.courseName ?? `课程 #${id}`)
  const code = String(raw.code ?? '')
  return {
    id,
    name,
    code,
    teacher: String(raw.teacherName ?? raw.teacher ?? ''),
    semester: String(raw.semester ?? ''),
    progress: asNumber(raw.avgProgress ?? raw.progress ?? raw.completionRate ?? 0),
    students: asNumber(raw.studentCount ?? raw.students ?? 0),
    pending: asNumber(raw.pendingGrading ?? 0),
    status: String(raw.status ?? ''),
    short: shortOf(name, code),
    tone: tone(id),
  }
}

export async function loadCourses(role: Role): Promise<CourseCard[]> {
  try {
    if (role === 'teacher') {
      const payload = unwrap<unknown>(await teacherApi.getMyCourseSummaries())
      const rows = Array.isArray(payload) ? payload : pickList(payload)
      return rows.map((row) => normalizeCard(row as Record<string, unknown>))
    }
    if (role === 'student') {
      const payload = unwrap<{ courses?: Record<string, unknown>[] }>(
        await studentApi.getStudentDashboard(),
      )
      return (payload?.courses || []).map(normalizeCard)
    }
    const payload = unwrap<unknown>(await courseApi.getAllCourses())
    return pickList(payload).map((row) => normalizeCard(row as Record<string, unknown>))
  } catch {
    return []
  }
}

const courseCache = new Map<Role, CourseCard[]>()

export function clearCourses() {
  courseCache.clear()
}

export async function getCourses(role: Role, force = false): Promise<CourseCard[]> {
  if (!force && courseCache.has(role)) return courseCache.get(role) || []
  const rows = await loadCourses(role)
  courseCache.set(role, rows)
  return rows
}

/**
 * 课程目录（全量 GET /courses，任何已登录角色可读，CourseController.list 无 @PreAuthorize）。
 * 学生「加入课程」用它在本地剔除已选课程，得到「可加入」候选；
 * 真正的加入走 studentApi.enrollCourse（POST /student/courses/{courseId}/enroll）。
 */
export async function getCourseCatalog(): Promise<CourseCard[]> {
  try {
    const payload = unwrap<unknown>(await courseApi.getAllCourses())
    return pickList(payload).map((row) => normalizeCard(row as Record<string, unknown>))
  } catch {
    return []
  }
}

export async function loadCourse(role: Role, id: number): Promise<CourseDetail | null> {
  try {
    const response =
      role === 'teacher' ? await teacherApi.getCourseDetail(id) : await courseApi.getCourseById(id)
    const raw = (unwrap<Record<string, unknown>>(response) || {}) as Record<string, unknown>
    if (!raw || !Object.keys(raw).length) return null
    return {
      id: asNumber(raw.id ?? id),
      name: String(raw.name ?? ''),
      code: String(raw.code ?? ''),
      teacher: String(raw.teacherName ?? raw.teacher ?? ''),
      semester: String(raw.semester ?? ''),
      credits: raw.credits == null ? null : asNumber(raw.credits),
      status: String(raw.status ?? ''),
      approvalStatus: String(raw.approvalStatus ?? ''),
      category: String(raw.category ?? ''),
      description: String(raw.description ?? ''),
    }
  } catch {
    return null
  }
}

export async function loadStudentDashboard(): Promise<Record<string, unknown> | null> {
  try {
    return unwrap<Record<string, unknown>>(await studentApi.getStudentDashboard()) || null
  } catch {
    return null
  }
}

export async function loadAdminOverview(): Promise<{
  overview: Record<string, unknown>
  dashboard: Record<string, unknown>
}> {
  // 分路归因：统计路失败不得把总览路一起清成空（与学情页 4c 批同判据）
  const [overviewResult, statsResult] = await Promise.allSettled([
    adminApi.getOverview(),
    analyticsApi.getDashboardStats(),
  ])
  return {
    overview:
      overviewResult.status === 'fulfilled'
        ? unwrap<Record<string, unknown>>(overviewResult.value) || {}
        : {},
    dashboard:
      statsResult.status === 'fulfilled'
        ? unwrap<Record<string, unknown>>(statsResult.value) || {}
        : {},
  }
}

export interface Notification {
  id: number | string
  title: string
  content: string
  time: string
  unread: boolean
  kind: string
}

/**
 * 通知列表归一。
 * 默认把失败吞成 []（抽屉等降级场景沿用），页面需要区分空态与失败态时传 throwOnError。
 */
export async function loadNotifications(throwOnError = false): Promise<Notification[]> {
  try {
    const payload = unwrap<unknown>(
      await notificationApi.getNotificationList({ page: 1, size: 20 }),
    )
    return pickList(payload).map((row, index) => {
      const item = row as Record<string, unknown>
      return {
        id: (item.id as number) ?? `demo-${index}`,
        title: String(item.title ?? '通知'),
        content: String(item.content ?? item.message ?? ''),
        time: String(item.createdAt ?? item.time ?? ''),
        unread: item.isRead === false || item.read === false || item.unread === true,
        kind: String(item.type ?? '系统'),
      }
    })
  } catch (error) {
    if (throwOnError) throw error
    return []
  }
}
