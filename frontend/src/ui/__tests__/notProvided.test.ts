import { describe, expect, it } from 'vitest'
import { courseTabs, navGroups } from '../nav'
import { courseTabHidden, notProvidedCopy, pageDisposition } from '../notProvided'

function navIds(role: 'student' | 'teacher' | 'admin'): string[] {
  return navGroups(role).flatMap((group) => group.items.map((item) => item.id))
}

describe('不提供清单的判定', () => {
  it('判为待扩展的页面按角色落到 hidden / unavailable', () => {
    expect(pageDisposition('student', 'assistant')).toBe('hidden')
    expect(pageDisposition('teacher', 'resources')).toBe('hidden')
    expect(pageDisposition('admin', 'audit')).toBe('hidden')
    expect(pageDisposition('admin', 'aiUsage')).toBe('unavailable')
    expect(pageDisposition('teacher', 'advising')).toBe('unavailable')
    expect(pageDisposition('admin', 'users')).toBe('provided')
    expect(pageDisposition('student', 'tasks')).toBe('provided')
  })

  it('每条不提供都有具体说明，不用同一句话糊过去', () => {
    for (const [role, page] of [
      ['student', 'assistant'],
      ['teacher', 'resources'],
      ['admin', 'audit'],
      ['admin', 'system'],
      ['admin', 'enrollments'],
    ] as const) {
      const copy = notProvidedCopy(role, page)
      expect(copy, `${role}/${page} 缺少说明`).not.toBeNull()
      expect(copy!.head.length).toBeGreaterThan(4)
      expect(copy!.detail.length, `${role}/${page} 说明未写原因`).toBeGreaterThan(10)
    }
  })

  it('学生侧栏不再出现 AI 助手入口', () => {
    expect(navIds('student')).not.toContain('assistant')
    expect(navIds('student')).toContain('exams')
  })

  it('管理端侧栏摘掉本轮不迁入的治理页，保留已转真的五页', () => {
    const ids = navIds('admin')
    for (const gone of ['audit', 'notices', 'sessions', 'system', 'supervision', 'moderation']) {
      expect(ids, `admin 侧栏仍留有 ${gone}`).not.toContain(gone)
    }
    for (const kept of ['users', 'roles', 'semesters', 'adminClasses', 'approvals']) {
      expect(ids, `admin 侧栏缺了 ${kept}`).toContain(kept)
    }
  })

  it('教师班主任那组入口保留但标为待扩展，不静默消失', () => {
    const ids = navIds('teacher')
    expect(ids).toContain('advising')
    expect(pageDisposition('teacher', 'advising')).toBe('unavailable')
    expect(ids).not.toContain('resources')
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
