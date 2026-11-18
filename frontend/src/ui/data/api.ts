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
  completedAt?: string | null
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
      completedAt: raw.completedAt == null ? null : String(raw.completedAt),
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
  /** 后端事件码的中文展示名；界面只显示这个，不直接渲染原始枚举。 */
  kind: string
  type: string
  category: string
  actionUrl: string
  /** 公告类通知对应的公告 id；用于课程通知页与公告列表去重。 */
  announcementId?: number
}

const TYPE_LABELS: Record<string, string> = {
  SYSTEM: '系统通知',
  ANNOUNCEMENT: '课程公告',
  TASK: '作业通知',
  TASK_CREATED: '作业发布',
  ASSIGNMENT: '作业通知',
  EXAM: '考试通知',
  EXAM_PUBLISHED: '考试发布',
  SUBMISSION_GRADED: '批阅完成',
  DISCUSSION: '讨论通知',
  DISCUSSION_REPLIED: '讨论回复',
  DISCUSSION_MENTIONED: '讨论提及',
  REMINDER: '学习提醒',
}

const CATEGORY_LABELS: Record<string, string> = {
  SYSTEM: '系统通知',
  COURSE: '课程通知',
  ASSIGNMENT: '作业通知',
  EXAM: '考试通知',
  DISCUSSION: '讨论通知',
  ANNOUNCEMENT: '课程公告',
}

/** 把后端事件码翻成中文；已是中文的旧数据原样保留，未知英文码退回分类名。 */
function notificationKind(item: Record<string, unknown>): string {
  const raw = String(item.type ?? '').trim()
  if (!raw) return CATEGORY_LABELS[String(item.category ?? '').toUpperCase()] || '通知'
  if (/[\u4e00-\u9fa5]/.test(raw)) return raw
  return (
    TYPE_LABELS[raw.toUpperCase()] ||
    CATEGORY_LABELS[String(item.category ?? '').toUpperCase()] ||
    '通知'
  )
}

function normalizeNotifications(rows: unknown[]): Notification[] {
  return rows.map((row, index) => {
    const item = row as Record<string, unknown>
    let data: Record<string, unknown> = {}
    if (typeof item.data === 'string') {
      try {
        data = JSON.parse(item.data) as Record<string, unknown>
      } catch {
        data = {}
      }
    } else if (item.data && typeof item.data === 'object') {
      data = item.data as Record<string, unknown>
    }
    const announcementId = Number(data.announcementId)
    return {
      id: (item.id as number) ?? `demo-${index}`,
      title: String(item.title ?? '通知'),
      content: String(item.content ?? item.message ?? ''),
      time: String(item.createdAt ?? item.time ?? ''),
      unread: item.isRead === false || item.read === false || item.unread === true,
      kind: notificationKind(item),
      type: String(item.type ?? ''),
      category: String(item.category ?? ''),
      actionUrl: String(item.actionUrl ?? ''),
      ...(Number.isFinite(announcementId) && announcementId > 0 ? { announcementId } : {}),
    }
  })
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
    return normalizeNotifications(pickList(payload))
  } catch (error) {
    if (throwOnError) throw error
    return []
  }
}

/** 课程页「通知」数据源：只取当前用户在该课程下的通知。 */
export async function loadCourseNotifications(
  courseId: number | string,
  throwOnError = false,
): Promise<Notification[]> {
  try {
    const payload = unwrap<unknown>(
      await notificationApi.getCourseNotificationList(courseId, { page: 1, size: 20 }),
    )
    return normalizeNotifications(pickList(payload))
  } catch (error) {
    if (throwOnError) throw error
    return []
  }
}
