import type { Role } from './types'

export type PageDisposition = 'provided' | 'hidden' | 'unavailable'

/**
 * 本轮切换不迁入、且旧端下线后不再提供的全局页（§11.8.3）。
 * 这些条目从 导航摘除；深链接进来看到的必须是「待扩展」空态，不能是示例读数。
 */
const HIDDEN: Record<Role, string[]> = {
  student: ['assistant'],
  teacher: ['resources'],
  admin: [
    'supervision',
    'moderation',
    'notices',
    'grades',
    'colleges',
    'teachingClasses',
    'enrollments',
  ],
}

/**
 * 后端缺口阻断的页面：入口保留，但正文只能是明确的「待扩展」空态（口径 3）。
 */
const UNAVAILABLE: Record<Role, string[]> = {
  student: [],
  teacher: ['advising', 'advisingProfile', 'advisingAlerts', 'advisingNotices'],
  admin: [],
}

const COPY: Record<string, { head: string; detail: string }> = {
  'student:assistant': {
    head: '待扩展：全局 AI 助手页',
    detail:
      'AI 能力只挂课程空间那一路（教师端「AI 教学助手」已接真实接口）；跨课程的全局助手与语音输入判为后续迭代项。',
  },
  'teacher:resources': {
    head: '待扩展：教师资源中心独立页',
    detail:
      '课程资源的上传、列表与下载已在「课程空间 → 章节与资源」内提供；独立资源库页判为后续迭代项。',
  },
  'admin:supervision': {
    head: '待扩展：教学监督',
    detail:
      '旧端该页依赖多源聚合与人工巡查流程，本轮未迁入；管理员可从「考试治理」与「题库管理」进入相应治理动作。',
  },
  'admin:moderation': {
    head: '待扩展：内容审核',
    detail: '讨论与资源审核需要留痕链路配套，本轮未迁入；讨论治理目前在课程空间内由教师维护。',
  },
  'admin:notices': {
    head: '待扩展：通知公告管理',
    detail:
      '全校公告发布与受众选择在旧端为独立能力，本轮未迁入；通知的消费侧（学生／教师消息中心）已接真实接口。',
  },
  'admin:grades': {
    head: '待扩展：管理员成绩管理',
    detail:
      '成绩改分只发生在教师端与批阅流；管理员跨课程成绩视图依赖成绩方案配置（后端缺口），本轮不迁入。',
  },
  'admin:colleges': {
    head: '待扩展：学院管理',
    detail: '学院与组织架构的后端模型尚未落地（B-13①），因此不提供管理界面。',
  },
  'admin:teachingClasses': {
    head: '待扩展：教学班管理',
    detail: '教学班模型尚未在后端实现（B-13①），课程内的教学班页签同样只给明确空态。',
  },
  'admin:enrollments': {
    head: '待扩展：选课审批',
    detail:
      '选课与审批链路依赖教学班模型（B-13①），本轮不提供独立审批界面；课程审批在「审批记录」页。',
  },
}

const GENERIC_UNAVAILABLE = {
  head: '待扩展：该页签无可用数据面',
  detail:
    '依赖的后端接口尚未落地（内部编号见后端待办项 B-13），本页只给明确空态、不展示示例读数。待扩展方向见产品规划 §4.1 的能力矩阵。',
}

function inList(list: Record<Role, string[]>, role: Role, page: string): boolean {
  return (list[role] || []).includes(page)
}

/**
 * 课程空间内不提供的页签（学生侧知识图谱与 AI）：入口直接摘掉，
 * 因为课程内的 AI 与图谱能力只对教师端那一路成立（后端已实测）。
 */
// 2026-10-30 起学生侧「知识图谱／AI 助手」两个课程页签**重新开放**：二者已接真接口
// （KnowledgeGraphTab 只读＋我的掌握度、AiCenterTab 学生模式），不再是示例壳。
const COURSE_TAB_HIDDEN: Record<Role, string[]> = {
  student: [],
  teacher: [],
  admin: [],
}

export function courseTabHidden(role: Role, tabId: string): boolean {
  return inList(COURSE_TAB_HIDDEN, role, tabId)
}

export function pageDisposition(role: Role, page: string): PageDisposition {
  if (inList(HIDDEN, role, page)) return 'hidden'
  if (inList(UNAVAILABLE, role, page)) return 'unavailable'
  return 'provided'
}

export function notProvidedCopy(role: Role, page: string) {
  return COPY[`${role}:${page}`] || (role && page ? GENERIC_UNAVAILABLE : null)
}

export function isNotProvided(role: Role, page: string): boolean {
  return pageDisposition(role, page) !== 'provided'
}
