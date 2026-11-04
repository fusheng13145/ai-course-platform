import { findModule } from './modules'
import type { Metric } from '../nav'
import type { DemoBlock } from './pageData'

function fromModule(
  role: 'student' | 'teacher' | 'admin',
  id: string,
  fallback: Metric[] = [],
): DemoBlock | null {
  const module = findModule(role, id)
  if (!module) return null
  return {
    metrics: module.metrics?.length ? module.metrics : fallback,
    table: module.table,
    side: module.side,
  }
}

function rawCourseTabBlock(role: 'student' | 'teacher' | 'admin', tab: string): DemoBlock | null {
  // 学生「作业 / 讨论 / 成绩」已接真实接口（CourseView → TasksTab / DiscussionTab / GradesTab），
  // 示例层不再供数；教师端全部走真组件。
  if (role === 'student') return null
  if (role === 'teacher') return null
  if (tab === 'content') return fromModule('teacher', 'chapters')
  if (tab === 'bank') return fromModule('teacher', 'bank')
  return null
}

export const COURSE_TAB_NOTES: Record<string, string> = {
  classes: '教学班与组织接口尚未落地；本页签待扩展，暂不提供班级读数。',
  content: '课程内容由授课教师在课程上下文中维护；管理员只读。',
  bank: '课程题库由授课教师维护；管理员只读。',
  analytics: '学情统计按课程聚合；管理员只读。',
  graph: '知识图谱由课程知识点构建；管理员只读。',
  aiUsage: 'AI 用量按课程聚合，数据源 ai_call_logs。',
  approval: '审批记录只读，驳回理由不可编辑。',
}

const DEMO_TAB_SUFFIX = '该课程页签走的是原型样本数据，后端聚合端点尚未接入；旧版对应页面仍可使用。'

/** 示例块自带「示例数据」前缀，否则演示与验收会把假读数当真读数。 */
export function courseTabBlock(
  role: 'student' | 'teacher' | 'admin',
  tab: string,
): DemoBlock | null {
  const block = rawCourseTabBlock(role, tab)
  if (!block) return null
  return { ...block, note: `示例数据：${COURSE_TAB_NOTES[tab] || DEMO_TAB_SUFFIX}` }
}
