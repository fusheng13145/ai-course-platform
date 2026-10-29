import navData from './data/nav.json'
import { ROLE_HOME, type V2Role } from './types'

export { ROLE_HOME, ROLE_KICKER, ROLE_LABEL, LEGACY_HOME, type V2Role } from './types'

const PATHS: Record<V2Role, Record<string, string>> = {
  student: {
    home: '/v2/student/home',
    courses: '/v2/student/courses',
    tasks: '/v2/student/tasks',
    academic: '/v2/student/academic',
    exams: '/v2/student/exams',
    messages: '/v2/student/messages',
    profile: '/v2/student/profile',
  },
  teacher: {
    home: '/v2/teacher/home',
    courses: '/v2/teacher/courses',
    bank: '/v2/teacher/bank',
    exams: '/v2/teacher/exams',
    grades: '/v2/teacher/grades',
    interaction: '/v2/teacher/interaction',
    knowledge: '/v2/teacher/knowledge-management',
    inbox: '/v2/teacher/inbox',
    analytics: '/v2/teacher/analytics',
    resources: '/v2/teacher/resources',
    advising: '/v2/teacher/advising',
    advisingProfile: '/v2/teacher/advising/profile',
    advisingAlerts: '/v2/teacher/advising/alerts',
    advisingNotices: '/v2/teacher/advising/notices',
    messages: '/v2/teacher/messages',
    profile: '/v2/teacher/profile',
  },
  admin: {
    home: '/v2/admin/home',
    colleges: '/v2/admin/colleges',
    adminClasses: '/v2/admin/classes',
    users: '/v2/admin/users',
    roles: '/v2/admin/roles',
    semesters: '/v2/admin/semesters',
    approvals: '/v2/admin/approvals',
    courses: '/v2/admin/courses',
    teachingClasses: '/v2/admin/teaching-classes',
    enrollments: '/v2/admin/enrollments',
    supervision: '/v2/admin/supervision',
    examGovernance: '/v2/admin/exam-governance',
    grades: '/v2/admin/grades',
    aiUsage: '/v2/admin/ai-usage',
    moderation: '/v2/admin/moderation',
    adminQuestions: '/v2/admin/questions',
    adminUploads: '/v2/admin/uploads',
    notices: '/v2/admin/notices',
    audit: '/v2/admin/audit',
    sessions: '/v2/admin/sessions',
    system: '/v2/admin/system',
    profile: '/v2/admin/profile',
  },
}

export interface V2NavItem {
  id: string
  label: string
  icon: string
  badge?: string
  path: string
}

export interface V2NavGroup {
  label: string
  advisorOnly?: boolean
  items: V2NavItem[]
}

const protoNav = navData.nav as Record<
  V2Role,
  Array<{
    label: string
    advisorOnly?: boolean
    items: Array<{ id: string; label: string; icon: string; badge?: string }>
  }>
>

export function navGroups(role: V2Role): V2NavGroup[] {
  const groups: V2NavGroup[] = (protoNav[role] || []).map((group) => ({
    label: group.label,
    advisorOnly: group.advisorOnly,
    items: group.items.map((item) => ({
      ...item,
      path: PATHS[role][item.id] || ROLE_HOME[role],
    })),
  }))
  // 原型 v2 的学生侧栏「学业」组只有学业档案，旧端另有跨课程的考试入口；
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
  // 原型 v2 的教师侧栏只有「资源中心」，旧端另有题库／试卷／成绩与课堂互动等真实能力；
  // 迁移期按旧端功能面补齐，避免删旧前端时丢入口。
  if (role === 'teacher') {
    const insertAt = groups.findIndex((group) => group.label === '平台')
    const at = insertAt >= 0 ? insertAt : groups.length
    groups.splice(at, 0, {
      label: '试题与学情',
      items: [
        { id: 'bank', label: '题库管理', icon: 'doc', path: PATHS.teacher.bank },
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
          icon: 'doc',
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
  return groups
}

export interface V2CourseTab {
  id: string
  label: string
  icon?: string
  badge?: string
}

export const COURSE_TABS = navData.course_tabs as Record<V2Role, V2CourseTab[]>
export const ADMIN_COURSE_TABS = navData.admin_course_tabs as V2CourseTab[]

const TEACHER_EXTRA_TABS: V2CourseTab[] = [
  { id: 'bank', label: '课程题库', icon: 'doc' },
  { id: 'proctor', label: '在线监考', icon: 'shield' },
]

export function courseTabs(role: V2Role): V2CourseTab[] {
  if (role === 'admin') return ADMIN_COURSE_TABS
  const tabs = [...(COURSE_TABS[role] || [])]
  // 旧端教师课程页签另有「课程题库」「在线监考」，原型 v2 漏了；迁移期补回。
  if (role === 'teacher') {
    const examsIndex = tabs.findIndex((tab) => tab.id === 'exams')
    tabs.splice(examsIndex >= 0 ? examsIndex : 1, 0, ...TEACHER_EXTRA_TABS)
  }
  return tabs
}

export function courseTabPath(role: V2Role, courseId: number | string, tab: string): string {
  return `/v2/${role}/courses/${courseId}/${tab}`
}

export interface V2Metric {
  label: string
  value: number | string
  unit?: string
  note?: string
  tone?: string
  icon?: string
}

export type V2Cell = string | { text?: string; chip?: string } | null

export interface V2Table {
  title: string
  columns: string[]
  rows: V2Cell[][]
}

export interface V2Side {
  title: string
  body: string
  items: string[]
  action?: { label: string; module: string }
}

export interface V2Module {
  id: string
  label: string
  en: string
  group: string
  icon?: string
  desc: string
  metrics: V2Metric[]
  table?: V2Table
  side?: V2Side
  special?: string
  hideMetrics?: boolean
}

export interface V2PageDef {
  title: string
  kicker: string
  desc: string
  moduleId?: string
  special?: string
}

const STUDENT_PAGES: Record<string, V2PageDef> = {
  home: { title: '工作台', kicker: 'STUDENT / HOME', desc: '课程进度、待办任务与未读通知概览。' },
  courses: {
    title: '我的课程',
    kicker: 'STUDENT / COURSES',
    desc: '在修课程、教学班与进度；课程卡片可进入课程空间。',
    special: 'courses',
  },
  tasks: {
    title: '学习任务',
    kicker: 'STUDENT / TASKS',
    desc: '跨课程作业、考试与提醒；按截止时间排序。',
    moduleId: 'tasks',
  },
  academic: {
    title: '学业档案',
    kicker: 'STUDENT / ACADEMIC',
    desc: '成绩、错题与学习路径；成绩以教师提交批阅为准。',
    special: 'academic',
  },
  exams: {
    title: '我的考试',
    kicker: 'STUDENT / EXAMS',
    desc: '跨课程的考试：开考、作答、倒计时与断线续考；交卷以服务端判分为准。',
    special: 'exams',
  },
  messages: {
    title: '消息',
    kicker: 'STUDENT / MESSAGES',
    desc: '课程通知、考试安排、AI 分析与批阅结果。',
    special: 'messages',
  },
  profile: {
    title: '个人',
    kicker: 'STUDENT / PROFILE',
    desc: '账号、安全与偏好设置。',
    special: 'profile',
  },
  assistant: {
    title: 'AI 助手',
    kicker: 'STUDENT / ASSISTANT',
    desc: '基于课程资料与个人学习数据的问答与复习建议。',
    special: 'assistant',
  },
}

const TEACHER_PAGES: Record<string, V2PageDef> = {
  home: {
    title: '教学工作台',
    kicker: 'TEACHER / HOME',
    desc: '待批阅、课程进度与班级风险的单一入口。',
  },
  courses: {
    title: '我的课程',
    kicker: 'TEACHER / COURSES',
    desc: '我负责的课程、教学班与待批阅；进入课程空间完成教学动作。',
    special: 'courses',
  },
  createCourse: {
    title: '创建课程',
    kicker: 'TEACHER / NEW COURSE',
    desc: '填写课程基本信息，提交后进入审核流程。',
  },
  bank: {
    title: '题库管理',
    kicker: 'TEACHER / QUESTION BANK',
    desc: '课程题库的筛选、创建、审核、复制与 AI 出题。',
  },
  exams: {
    title: '试卷库',
    kicker: 'TEACHER / EXAM PAPERS',
    desc: '试卷的创建、编辑、题目明细、统计与发布。',
  },
  grades: {
    title: '成绩管理',
    kicker: 'TEACHER / GRADES',
    desc: '按课程查看学情与正式总评，导出成绩并进入待批阅任务。',
  },
  interaction: {
    title: '课堂互动',
    kicker: 'TEACHER / INTERACTION',
    desc: '发起签到与投票，查看签到记录、补签与投票结果。',
  },
  knowledge: {
    title: '知识点管理',
    kicker: 'TEACHER / KNOWLEDGE',
    desc: '维护知识点结构、层级、前置依赖与批量导入。',
  },
  inbox: {
    title: '教学任务',
    kicker: 'TEACHER / INBOX',
    desc: '跨课程的批阅、发布、监考与提醒聚合；每条带课程深链。',
    special: 'inbox',
  },
  analytics: {
    title: '学情分析',
    kicker: 'TEACHER / ANALYTICS',
    desc: '跨课程的成绩分布、薄弱点与可执行建议。',
    special: 'analytics',
  },
  resources: {
    title: '资源中心',
    kicker: 'TEACHER / RESOURCES',
    desc: '课程级资源共享；题库、讲义与试卷统一检索。',
    special: 'resources',
  },
  advising: {
    title: '我的班级',
    kicker: 'TEACHER / ADVISING',
    desc: '班主任视角：行政班人数、课程进度与预警；与教学班分离。',
    special: 'advising',
  },
  advisingProfile: {
    title: '班级画像',
    kicker: 'TEACHER / ADVISING',
    desc: '行政班的课程进度、均分与风险结构；只读。',
    special: 'advising',
  },
  advisingAlerts: {
    title: '学生预警',
    kicker: 'TEACHER / ADVISING',
    desc: '学业、进度与活跃度预警；作为线索，由班主任核实。',
    special: 'advising',
  },
  advisingNotices: {
    title: '班级通知',
    kicker: 'TEACHER / ADVISING',
    desc: '面向行政班学生的通知；与课程通知、系统公告分属不同受众。',
    special: 'advising',
  },
  messages: {
    title: '消息',
    kicker: 'TEACHER / MESSAGES',
    desc: '课程通知、批阅结果与监考风险事件。',
    special: 'messages',
  },
  profile: {
    title: '个人',
    kicker: 'TEACHER / PROFILE',
    desc: '账号、安全与偏好设置。',
    special: 'profile',
  },
}

const ADMIN_PAGES: Record<string, V2PageDef> = {
  home: { title: '治理总览', kicker: 'ADMIN / HOME', desc: '平台规模、审批队列与运行风险。' },
  colleges: {
    title: '学院与专业',
    kicker: 'ADMIN / COLLEGES',
    desc: '学校 → 学院 → 专业；行政班只负责学生归属，不绑定课程。',
    special: 'colleges',
  },
  adminClasses: {
    title: '行政班',
    kicker: 'ADMIN / ADMIN CLASSES',
    desc: '行政班、班主任与学生归属；行政班与教学班分离。',
    special: 'adminClasses',
  },
  users: {
    title: '用户与账号',
    kicker: 'ADMIN / USERS',
    desc: '全校用户、角色与账号状态；管理员只做治理。',
    moduleId: 'users',
  },
  roles: {
    title: '角色与权限',
    kicker: 'ADMIN / ROLES',
    desc: '角色矩阵与权限边界；院系级只读。',
    moduleId: 'roles',
  },
  semesters: {
    title: '学期管理',
    kicker: 'ADMIN / SEMESTERS',
    desc: '学期注册表与归档；课程编号按学期唯一。',
    moduleId: 'semesters',
  },
  approvals: {
    title: '课程审批',
    kicker: 'ADMIN / APPROVALS',
    desc: '待审批课程、风险提示与审批流水；审批后才可发布教学班。',
    special: 'approvals',
  },
  courses: {
    title: '课程目录',
    kicker: 'ADMIN / COURSES',
    desc: '全校课程、审批状态与教学班规模；进入课程治理只读。',
    special: 'courses',
  },
  teachingClasses: {
    title: '教学班监管',
    kicker: 'ADMIN / TEACHING CLASSES',
    desc: '课程下的开课单元；学生来源与主讲教师。',
    special: 'teachingClasses',
  },
  enrollments: {
    title: '选课与名单',
    kicker: 'ADMIN / ENROLLMENTS',
    desc: '教学班名单与自助选课审批；名单可回溯来源。',
    special: 'enrollments',
  },
  supervision: {
    title: '教学监督',
    kicker: 'ADMIN / SUPERVISION',
    desc: '课程运行、完成度与异常；只读。',
    special: 'supervision',
  },
  examGovernance: {
    title: '考试治理',
    kicker: 'ADMIN / EXAMS',
    desc: '全校试卷与进行中考试会话：筛选、题目与统计、强制交卷与作弊检测；不替代教师批阅。',
    special: 'examGovernance',
  },
  grades: {
    title: '成绩与预警',
    kicker: 'ADMIN / GRADES',
    desc: '课程与班级维度的成绩概览；管理员只读，不改分。',
    special: 'grades',
  },
  adminQuestions: {
    title: '题库管理',
    kicker: 'ADMIN / QUESTION BANK',
    desc: '全校题目维护：筛选、编辑、AI 出题、统计与易错题榜单。',
  },
  adminUploads: {
    title: '上传会话监控',
    kicker: 'ADMIN / UPLOADS',
    desc: '上传会话的状态与磁盘分片进度：本地分片上传、合并已齐片的会话、取消仍在进行的会话。',
  },
  aiUsage: {
    title: 'AI 使用与治理',
    kicker: 'ADMIN / AI USAGE',
    desc: '按课程聚合调用量、教师配额与限制出题；管理员只做治理。',
    moduleId: 'aiUsage',
    special: 'aiUsage',
  },
  moderation: {
    title: '内容审核',
    kicker: 'ADMIN / MODERATION',
    desc: '讨论与资源审核；处置需留痕。',
    moduleId: 'moderation',
  },
  notices: {
    title: '通知公告',
    kicker: 'ADMIN / NOTICES',
    desc: '全校或学院范围的公告；与课程通知、班级通知分属不同 audience。',
    special: 'notices',
  },
  audit: {
    title: '操作与登录日志',
    kicker: 'ADMIN / AUDIT',
    desc: '审计日志、登录记录与会话；管理员操作必须留痕。',
    moduleId: 'audit',
  },
  sessions: {
    title: '会话管理',
    kicker: 'ADMIN / SESSIONS',
    desc: '在线会话、设备与最近活动；强制下线会立即失效刷新令牌。',
    special: 'sessions',
  },
  system: {
    title: '系统设置',
    kicker: 'ADMIN / SYSTEM',
    desc: '平台参数、服务健康与公告发布。',
    moduleId: 'system',
  },
  profile: {
    title: '个人',
    kicker: 'ADMIN / PROFILE',
    desc: '账号、安全与偏好设置。',
    special: 'profile',
  },
}

export const PAGE_DEFS: Record<V2Role, Record<string, V2PageDef>> = {
  student: STUDENT_PAGES,
  teacher: TEACHER_PAGES,
  admin: ADMIN_PAGES,
}

export function pageDef(role: V2Role, page: string): V2PageDef {
  return PAGE_DEFS[role][page] || PAGE_DEFS[role].home
}

export const STYLE = navData.style as { name: string; en: string }
