import navData from './data/nav.json'
import { ROLE_HOME, type Role } from './types'
import { courseTabHidden, pageDisposition } from './notProvided'

export { ROLE_HOME, ROLE_LABEL, type Role } from './types'

const PATHS: Record<Role, Record<string, string>> = {
  student: {
    home: '/student/home',
    courses: '/student/courses',
    tasks: '/student/tasks',
    academic: '/student/academic',
    exams: '/student/exams',
    messages: '/student/messages',
    profile: '/student/profile',
  },
  teacher: {
    home: '/teacher/home',
    courses: '/teacher/courses',
    bank: '/teacher/bank',
    exams: '/teacher/exams',
    grades: '/teacher/grades',
    interaction: '/teacher/interaction',
    knowledge: '/teacher/knowledge-management',
    inbox: '/teacher/inbox',
    analytics: '/teacher/analytics',
    resources: '/teacher/resources',
    advising: '/teacher/advising',
    advisingProfile: '/teacher/advising/profile',
    advisingAlerts: '/teacher/advising/alerts',
    advisingNotices: '/teacher/advising/notices',
    messages: '/teacher/messages',
    profile: '/teacher/profile',
  },
  admin: {
    home: '/admin/home',
    colleges: '/admin/colleges',
    adminClasses: '/admin/classes',
    users: '/admin/users',
    roles: '/admin/roles',
    semesters: '/admin/semesters',
    approvals: '/admin/approvals',
    courses: '/admin/courses',
    teachingClasses: '/admin/teaching-classes',
    enrollments: '/admin/enrollments',
    supervision: '/admin/supervision',
    examGovernance: '/admin/exam-governance',
    grades: '/admin/grades',
    aiUsage: '/admin/ai-usage',
    adminAi: '/admin/ai-provider',
    moderation: '/admin/moderation',
    adminQuestions: '/admin/questions',
    adminUploads: '/admin/uploads',
    notices: '/admin/notices',
    audit: '/admin/audit',
    sessions: '/admin/sessions',
    system: '/admin/system',
    profile: '/admin/profile',
  },
}

export interface NavItem {
  id: string
  label: string
  icon: string
  badge?: string
  path: string
}

export interface NavGroup {
  label: string
  advisorOnly?: boolean
  items: NavItem[]
}

const protoNav = navData.nav as Record<
  Role,
  Array<{
    label: string
    advisorOnly?: boolean
    items: Array<{ id: string; label: string; icon: string; badge?: string }>
  }>
>

export function navGroups(role: Role): NavGroup[] {
  const groups: NavGroup[] = (protoNav[role] || []).map((group) => ({
    label: group.label,
    advisorOnly: group.advisorOnly,
    items: group.items.map((item) => ({
      ...item,
      path: PATHS[role][item.id] || ROLE_HOME[role],
    })),
  }))
  // 原型 的学生侧栏「学业」组只有学业档案，旧端另有跨课程的考试入口；
  // 迁移期按旧端功能面补齐，避免删旧前端时丢入口（与 M-02 课程空间那一路同源）。
  if (role === 'student') {
    const academic = groups.find((group) => group.items.some((item) => item.id === 'academic'))
    const item = { id: 'exams', label: '我的考试', icon: 'clipboard', path: PATHS.student.exams }
    if (academic) academic.items.push(item)
    else
      groups.splice(1, 0, {
        label: '学业',
        items: [item],
      })
  }
  // 原型 的教师侧栏只有「资源中心」，旧端另有题库／试卷／成绩与课堂互动等真实能力；
  // 迁移期按旧端功能面补齐，避免删旧前端时丢入口。
  if (role === 'teacher') {
    const insertAt = groups.findIndex((group) => group.label === '平台')
    const at = insertAt >= 0 ? insertAt : groups.length
    groups.splice(at, 0, {
      label: '试题与学情',
      items: [
        { id: 'bank', label: '题库管理', icon: 'file', path: PATHS.teacher.bank },
        { id: 'exams', label: '试卷库', icon: 'clipboard', path: PATHS.teacher.exams },
        { id: 'grades', label: '成绩管理', icon: 'award', path: PATHS.teacher.grades },
      ],
    })
    groups.splice(at + 1, 0, {
      label: '课堂与知识',
      items: [
        { id: 'interaction', label: '课堂互动', icon: 'message', path: PATHS.teacher.interaction },
        { id: 'knowledge', label: '知识点管理', icon: 'path', path: PATHS.teacher.knowledge },
      ],
    })
  }
  // 管理员侧栏同理：原型只有治理类入口，旧端另有题库/试卷/资源等内容面
  if (role === 'admin') {
    const insertAt = groups.findIndex((group) => group.label === '平台')
    groups.splice(insertAt >= 0 ? insertAt : groups.length, 0, {
      label: '内容与资源',
      items: [
        {
          id: 'adminQuestions',
          label: '题库管理',
          icon: 'file',
          path: PATHS.admin.adminQuestions,
        },
        {
          id: 'adminUploads',
          label: '上传会话',
          icon: 'upload',
          path: PATHS.admin.adminUploads,
        },
      ],
    })
  }
  // 本轮判为不提供的页面：导航不留入口，避免点进去只有示例读数（判据见前端待办项 §11.8.3）。
  for (const group of groups) {
    group.items = group.items.filter((item) => pageDisposition(role, item.id) !== 'hidden')
  }
  return groups.filter((group) => group.items.length > 0)
}

export interface CourseTab {
  id: string
  label: string
  icon?: string
  badge?: string
}

export const COURSE_TABS = navData.course_tabs as Record<Role, CourseTab[]>
export const ADMIN_COURSE_TABS = navData.admin_course_tabs as CourseTab[]

const TEACHER_EXTRA_TABS: CourseTab[] = [
  { id: 'bank', label: '课程题库', icon: 'file' },
  { id: 'proctor', label: '在线监考', icon: 'shield' },
]

export function courseTabs(role: Role): CourseTab[] {
  if (role === 'admin') return ADMIN_COURSE_TABS
  const tabs = [...(COURSE_TABS[role] || [])]
  // 旧端教师课程页签另有「课程题库」「在线监考」，原型 漏了；迁移期补回。
  if (role === 'teacher') {
    const examsIndex = tabs.findIndex((tab) => tab.id === 'exams')
    tabs.splice(examsIndex >= 0 ? examsIndex : 1, 0, ...TEACHER_EXTRA_TABS)
  }
  return tabs.filter((tab) => !courseTabHidden(role, tab.id))
}

export function courseTabPath(role: Role, courseId: number | string, tab: string): string {
  return `/${role}/courses/${courseId}/${tab}`
}

export interface Metric {
  label: string
  value: number | string
  unit?: string
  note?: string
  tone?: string
  icon?: string
}

export type Cell = string | { text?: string; chip?: string } | null

export interface Table {
  title: string
  columns: string[]
  rows: Cell[][]
}

export interface PageDef {
  title: string
  special?: string
}

const STUDENT_PAGES: Record<string, PageDef> = {
  home: { title: '工作台' },
  courses: {
    title: '我的课程',
    special: 'courses',
  },
  tasks: {
    title: '学习任务',
    special: 'tasks',
  },
  academic: {
    title: '学业档案',
    special: 'academic',
  },
  exams: {
    title: '我的考试',
    special: 'exams',
  },
  messages: {
    title: '消息',
    special: 'messages',
  },
  profile: {
    title: '个人中心',
    special: 'profile',
  },
  // 全局 AI 助手页已判下线（M-30）：导航不挂入口，深链接落到「待扩展」空态。
  // 原 special: 'assistant' 指向的 AiTab 是硬编码示例面板（自带「示例回答」章），
  // 随本次死码清理删除；页面定义保留，只为让空态的标题/描述仍取自本表。
  assistant: {
    title: 'AI 助手',
    special: 'assistant',
  },
}

const TEACHER_PAGES: Record<string, PageDef> = {
  home: {
    title: '教学工作台',
  },
  courses: {
    title: '我的课程',
    special: 'courses',
  },
  createCourse: {
    title: '创建课程',
  },
  bank: {
    title: '题库管理',
  },
  exams: {
    title: '试卷库',
  },
  grades: {
    title: '成绩管理',
  },
  interaction: {
    title: '课堂互动',
  },
  knowledge: {
    title: '知识点管理',
  },
  inbox: {
    title: '教学任务',
    special: 'inbox',
  },
  analytics: {
    title: '学情分析',
    special: 'analytics',
  },
  resources: {
    title: '资源中心',
    special: 'resources',
  },
  advising: {
    title: '我的班级',
    special: 'advising',
  },
  advisingProfile: {
    title: '班级画像',
    special: 'advising',
  },
  advisingAlerts: {
    title: '学生预警',
    special: 'advising',
  },
  advisingNotices: {
    title: '班级通知',
    special: 'advising',
  },
  messages: {
    title: '消息',
    special: 'messages',
  },
  profile: {
    title: '个人中心',
    special: 'profile',
  },
}

const ADMIN_PAGES: Record<string, PageDef> = {
  home: { title: '治理总览' },
  colleges: {
    title: '学院与专业',
    special: 'colleges',
  },
  adminClasses: {
    title: '行政班',
    special: 'adminClasses',
  },
  users: {
    title: '用户与账号',
    special: 'users',
  },
  roles: {
    title: '角色与权限',
    special: 'roles',
  },
  semesters: {
    title: '学期管理',
    special: 'semesters',
  },
  approvals: {
    title: '课程审批',
    special: 'approvals',
  },
  courses: {
    title: '课程目录',
    special: 'courses',
  },
  teachingClasses: {
    title: '教学班监管',
    special: 'teachingClasses',
  },
  enrollments: {
    title: '选课与名单',
    special: 'enrollments',
  },
  supervision: {
    title: '教学监督',
    special: 'supervision',
  },
  examGovernance: {
    title: '考试治理',
    special: 'examGovernance',
  },
  grades: {
    title: '成绩与预警',
    special: 'grades',
  },
  adminQuestions: {
    title: '题库管理',
  },
  adminUploads: {
    title: '上传会话监控',
  },
  aiUsage: {
    title: 'AI 使用与治理',
    special: 'aiUsage',
  },
  adminAi: {
    title: 'AI 供应商',
    special: 'adminAi',
  },
  moderation: {
    title: '内容审核',
    special: 'moderation',
  },
  notices: {
    title: '通知公告',
    special: 'notices',
  },
  audit: {
    title: '操作与登录日志',
    special: 'audit',
  },
  sessions: {
    title: '会话管理',
    special: 'sessions',
  },
  system: {
    title: '系统设置',
    special: 'system',
  },
  profile: {
    title: '个人中心',
    special: 'profile',
  },
}

export const PAGE_DEFS: Record<Role, Record<string, PageDef>> = {
  student: STUDENT_PAGES,
  teacher: TEACHER_PAGES,
  admin: ADMIN_PAGES,
}

export function pageDef(role: Role, page: string): PageDef {
  return PAGE_DEFS[role][page] || PAGE_DEFS[role].home
}

export const STYLE = navData.style as { name: string; en: string }
