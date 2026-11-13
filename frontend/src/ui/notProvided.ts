import type { Role } from './types'

export type PageDisposition = 'provided' | 'hidden' | 'unavailable'

/**
 * 本轮切换不迁入、且旧端下线后不再提供的全局页（§11.8.3）。
 * 这些条目从 导航摘除；深链接进来看到的必须是「待扩展」空态，不能是示例读数。
 */
// 2026-11-07 全量实现批：student:assistant（全局 AI 问答）、teacher:resources（跨课程资源库）、
// admin:supervision（教学运行总览）、admin:grades（成绩与预警）四页全部接真，HIDDEN 清空。
// 待扩展空态机制保留：teacher:advising 四页签仍因「班级治理面后端未落地」处于 unavailable。
const HIDDEN: Record<Role, string[]> = {
  student: [],
  teacher: [],
  admin: [],
}

/**
 * 后端缺口阻断的页面：入口保留，但正文只能是明确的「待扩展」空态（口径 3）。
 */
// 2026-11-07 全量实现批三：教师班级治理四页签接真（/advisor/classes[/profile|/alerts] +
// 公告受众扇出），UNAVAILABLE 清空；该机制保留给未来的后端缺口页。
const UNAVAILABLE: Record<Role, string[]> = {
  student: [],
  teacher: [],
  admin: [],
}

const COPY: Record<string, { head: string; detail: string }> = {}

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
