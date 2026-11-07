import { describe, expect, it } from 'vitest'
import { courseTabs, navGroups } from '../nav'
import { courseTabHidden, pageDisposition } from '../notProvided'

function navIds(role: 'student' | 'teacher' | 'admin'): string[] {
  return navGroups(role).flatMap((group) => group.items.map((item) => item.id))
}

describe('不提供清单的判定', () => {
  it('判为待扩展的页面按角色落到 hidden / unavailable', () => {
    // 2026-11-07 全量实现批二：assistant（全局 AI）/ resources（跨课程资源库）/ supervision /
    // grades 四页接真，HIDDEN 全部清空；teacher:advising 仍 unavailable（班级治理面未落地）
    expect(pageDisposition('student', 'assistant')).toBe('provided')
    expect(pageDisposition('teacher', 'resources')).toBe('provided')
    expect(pageDisposition('admin', 'supervision')).toBe('provided')
    expect(pageDisposition('admin', 'grades')).toBe('provided')
    // 2026-11-04 批次 23：操作/登录日志与会话管理接真（端点早已存在），
    // 两页从「待扩展」回到 provided，由 AuditBody / SessionsBody 承接。
    expect(pageDisposition('admin', 'audit')).toBe('provided')
    expect(pageDisposition('admin', 'sessions')).toBe('provided')
    // 2026-11-07 全量实现批：notices/colleges/teachingClasses/enrollments/moderation
    // 五页接真（后端 WP3-WP8 + 两端点补齐），从「待扩展」回到 provided。
    expect(pageDisposition('admin', 'notices')).toBe('provided')
    expect(pageDisposition('admin', 'colleges')).toBe('provided')
    expect(pageDisposition('admin', 'teachingClasses')).toBe('provided')
    expect(pageDisposition('admin', 'enrollments')).toBe('provided')
    expect(pageDisposition('admin', 'moderation')).toBe('provided')
    // 2026-11-04 批次 24：系统设置接真（`/admin/configs` GET＋PUT、`/admin/health`）
    expect(pageDisposition('admin', 'system')).toBe('provided')
    // aiUsage 已于 2026-11-04（M-14）按课程维度接真，不再属于「待扩展」：
    // 它现在由 AiUsageBody 走 special 约定加载，处置必须回到 provided。
    expect(pageDisposition('admin', 'aiUsage')).toBe('provided')
    expect(pageDisposition('teacher', 'advising')).toBe('provided')
    expect(pageDisposition('admin', 'users')).toBe('provided')
    expect(pageDisposition('student', 'tasks')).toBe('provided')
  })

  it('每条不提供都有具体说明，不用同一句话糊过去', () => {
    // HIDDEN / UNAVAILABLE 均已清空：四个 advising 页签全部 provided
    for (const page of [
      'advising',
      'advisingProfile',
      'advisingAlerts',
      'advisingNotices',
    ] as const) {
      expect(pageDisposition('teacher', page)).toBe('provided')
    }
  })

  it('学生侧栏不再出现 AI 助手入口', () => {
    expect(navIds('student')).not.toContain('assistant')
    expect(navIds('student')).toContain('exams')
  })

  it('管理端侧栏摘掉本轮不迁入的治理页，保留已转真的页面', () => {
    const ids = navIds('admin')
    // 2026-11-07 全量实现批二：治理页全部接真，侧栏不再摘除任何 admin 页
    expect(ids.length).toBeGreaterThan(10)
    for (const kept of [
      'users',
      'roles',
      'semesters',
      'adminClasses',
      'approvals',
      // 批次 23 接真后重新出现在侧栏
      'audit',
      'sessions',
      // 批次 24 接真后重新出现在侧栏
      'system',
      // 2026-11-07 全量实现批接真后重新出现在侧栏
      'colleges',
      'teachingClasses',
      'enrollments',
      'moderation',
      'notices',
      // 全量实现批二接真
      'supervision',
      'grades',
    ]) {
      expect(ids, `admin 侧栏缺了 ${kept}`).toContain(kept)
    }
  })

  it('教师班主任那组入口保留但标为待扩展，不静默消失', () => {
    const ids = navIds('teacher')
    expect(ids).toContain('advising')
    expect(pageDisposition('teacher', 'advising')).toBe('provided')
    // 2026-11-07 全量实现批二：resources 已接真，入口恢复
    expect(ids).toContain('resources')
  })

  it('空掉的分组不再渲染标题', () => {
    for (const role of ['student', 'teacher', 'admin'] as const) {
      for (const group of navGroups(role)) {
        expect(group.items.length, `${role} 分组「${group.label}」为空`).toBeGreaterThan(0)
      }
    }
  })

  it('学生课程页签开放图谱与 AI（已接真），教师侧不受影响', () => {
    expect(courseTabHidden('student', 'graph')).toBe(false)
    const studentTabs = courseTabs('student').map((tab) => tab.id)
    expect(studentTabs).toContain('graph')
    expect(studentTabs).toContain('ai')
    expect(studentTabs).toContain('chapters')
    const teacherTabs = courseTabs('teacher').map((tab) => tab.id)
    expect(teacherTabs).toContain('graph')
    expect(teacherTabs).toContain('ai')
  })
})
