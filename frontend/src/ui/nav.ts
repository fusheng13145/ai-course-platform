import navData from './data/nav.json'
import { ROLE_HOME, type Role } from './types'
import { courseTabHidden, pageDisposition } from './notProvided'

export { ROLE_HOME, ROLE_KICKER, ROLE_LABEL, type Role } from './types'

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
  { id: 'bank', label: '课程题库', icon: 'doc' },
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
  kicker: string
  desc: string
  special?: string
}

const STUDENT_PAGES: Record<string, PageDef> = {
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
    special: 'tasks',
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
  // 全局 AI 助手页已判下线（M-30）：导航不挂入口，深链接落到「待扩展」空态。
  // 原 special: 'assistant' 指向的 AiTab 是硬编码示例面板（自带「示例回答」章），
  // 随本次死码清理删除；页面定义保留，只为让空态的标题/描述仍取自本表。
  assistant: {
    title: 'AI 助手',
    kicker: 'STUDENT / ASSISTANT',
    desc: '跨课程的全局 AI 问答；课程内精准问答仍走课程空间的「AI 助手」页签。',
    special: 'assistant',
  },
}

const TEACHER_PAGES: Record<string, PageDef> = {
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
    desc: '跨课程的资源汇总：上传、下载、可见性与删除；课程内挂载仍在课程空间。',
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

const ADMIN_PAGES: Record<string, PageDef> = {
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
    special: 'users',
  },
  roles: {
    title: '角色与权限',
    kicker: 'ADMIN / ROLES',
    desc: '角色矩阵与权限边界；院系级只读。',
    special: 'roles',
  },
  semesters: {
    title: '学期管理',
    kicker: 'ADMIN / SEMESTERS',
    desc: '学期注册表与归档；课程编号按学期唯一。',
    special: 'semesters',
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
    desc: '按课程/按日/按能力聚合的调用量、成功率与耗时（近 N 天，数据源 ai_call_logs）与教师出题治理。',
    special: 'aiUsage',
  },
  adminAi: {
    title: 'AI 供应商',
    kicker: 'ADMIN / AI PROVIDER',
    desc: 'LLM 供应商运行时热切换与连通性探测；密钥由环境变量注入，不在此页管理。',
    special: 'adminAi',
  },
  moderation: {
    title: '内容审核',
    kicker: 'ADMIN / MODERATION',
    desc: '讨论与资源审核；处置需留痕。',
    special: 'moderation',
  },
  notices: {
    title: '通知公告',
    kicker: 'ADMIN / NOTICES',
    desc: '按课程 / 教学班 / 行政班三类受众发布公告；扇出到站内通知，非受众不可见。',
    special: 'notices',
  },
  audit: {
    title: '操作与登录日志',
    kicker: 'ADMIN / AUDIT',
    desc: '关键写操作与每一次登录尝试的查询面（只读，不提供删除或改写日志）。',
    special: 'audit',
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
    desc: '平台参数与依赖服务状态；改值只影响单个配置键。',
    special: 'system',
  },
  profile: {
    title: '个人',
    kicker: 'ADMIN / PROFILE',
    desc: '账号、安全与偏好设置。',
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
