import { describe, expect, it } from 'vitest'
import { appRouter as router } from '../router'
import {
  ADMIN_COURSE_TABS,
  COURSE_TABS,
  PAGE_DEFS,
  courseTabs,
  navGroups,
  pageDef,
  type Role,
} from '../nav'

const ROLES: Role[] = ['student', 'teacher', 'admin']
const CATCH_ALL = '/:pathMatch(.*)*'

function resolves(path: string): boolean {
  const matched = router.resolve(path).matched
  const leaf = matched[matched.length - 1]
  return Boolean(leaf) && leaf.path !== CATCH_ALL
}

describe('导航与路由一致性', () => {
  it('三端侧栏条目都命中 /路由，而不是兜底 404', () => {
    for (const role of ROLES) {
      for (const group of navGroups(role)) {
        for (const item of group.items) {
          expect(item.path.startsWith(`/${role}/`), `${role}/${item.id} 不在本端命名空间`).toBe(
            true,
          )
          expect(resolves(item.path), `${role}/${item.id} 是死链：${item.path}`).toBe(true)
        }
      }
    }
  })

  it('登录/注册/找回密码/法务/错误页是公开路由，且不会被兜底 404 拦截', () => {
    for (const path of [
      '/login',
      '/register',
      '/forgot-password',
      '/agreement',
      '/privacy',
      '/403',
      '/404',
      '/503',
    ]) {
      const matched = router.resolve(path).matched
      const leaf = matched[matched.length - 1]
      expect(leaf, `${path} 未命中路由`).toBeTruthy()
      expect(leaf.path, `${path} 落到了兜底`).not.toBe(CATCH_ALL)
      expect(leaf.path, `${path} 落到了 兜底`).not.toContain(':pathMatch')
      expect(
        matched.some((record) => record.meta?.requiresAuth === false),
        `${path} 不是公开路由`,
      ).toBe(true)
    }
  })

  it('三端首页不被 兜底 404 拦截', () => {
    for (const path of ['/student/home', '/teacher/home', '/admin/home']) {
      const leaf = router.resolve(path).matched.at(-1)
      expect(leaf?.path, `${path} 被 兜底接走`).not.toContain(':pathMatch')
    }
  })

  it('课程页签路径全部命中课程壳路由', () => {
    for (const role of ROLES) {
      for (const tab of courseTabs(role)) {
        const path = `/${role}/courses/1/${tab.id}`
        expect(resolves(path), `${role}/${tab.id} 是死链：${path}`).toBe(true)
      }
    }
  })

  it('课程页签数量与原型 IA 对齐，并补回教师侧旧端的题库与监考页签', () => {
    expect(COURSE_TABS.student).toHaveLength(10)
    expect(COURSE_TABS.teacher).toHaveLength(16)
    expect(ADMIN_COURSE_TABS).toHaveLength(10)
    const teacherTabs = courseTabs('teacher').map((tab) => tab.id)
    expect(courseTabs('teacher')).toHaveLength(COURSE_TABS.teacher.length + 2)
    expect(teacherTabs).toContain('bank')
    expect(teacherTabs).toContain('proctor')
    expect(teacherTabs).toContain('grades')
    expect(teacherTabs).toContain('interaction')
    expect(teacherTabs).toContain('knowledge')
    expect(courseTabs('student').map((tab) => tab.id)).not.toContain('proctor')
    expect(courseTabs('student').map((tab) => tab.id)).toContain('practice')
  })

  it('教师侧栏只保留工作台/我的课程/系统消息，课程能力全部落到课程页签', () => {
    const ids = navGroups('teacher')
      .map((group) => group.items.map((item) => item.id))
      .flat()
    expect(ids).toEqual(expect.arrayContaining(['home', 'courses', 'messages']))
    for (const retired of [
      'bank',
      'exams',
      'grades',
      'interaction',
      'knowledge',
      'inbox',
      'analytics',
      'resources',
      'advising',
      'advisingProfile',
      'advisingAlerts',
      'advisingNotices',
    ]) {
      expect(ids, `教师侧栏仍保留课程/班级入口 ${retired}`).not.toContain(retired)
    }
  })

  it('新建课程不被课程空间的 :id 动态段吞掉', () => {
    const leaf = router.resolve('/teacher/courses/create').matched.slice(-1)[0]
    expect(leaf.meta.pageKey, '创建课程落到了课程空间路由').toBe('createCourse')
    expect(leaf.meta.courseSpace).toBeUndefined()
    expect(
      router.resolve('/teacher/courses/4060/overview').matched.slice(-1)[0].meta.courseSpace,
    ).toBe(true)
  })

  it('学生侧栏只保留学业档案等全局入口，考试归入课程页签', () => {
    const ids = navGroups('student')
      .map((group) => group.items.map((item) => item.id))
      .flat()
    expect(ids).toEqual(expect.arrayContaining(['home', 'courses', 'academic', 'messages']))
    expect(ids).not.toContain('tasks')
    expect(ids).not.toContain('exams')
    expect(courseTabs('student').map((tab) => tab.id)).toContain('exams')
    expect(pageDef('student', 'messages').title).toBe('系统消息')
  })

  it('每个全局页面定义都有标题', () => {
    for (const role of ROLES) {
      for (const [page, def] of Object.entries(PAGE_DEFS[role])) {
        expect(pageDef(role, page).title, `${role}/${page} 缺标题`).toBeTruthy()
        expect(def.title, `${role}/${page} 缺标题`).toBeTruthy()
      }
    }
  })

  it('页面定义不再挂示例模块编号（示例层已于 batch 24 整体删除）', () => {
    for (const role of ROLES) {
      for (const [page, def] of Object.entries(PAGE_DEFS[role])) {
        expect(
          (def as { moduleId?: string }).moduleId,
          `${role}/${page} 仍声明 moduleId`,
        ).toBeUndefined()
      }
    }
  })
})
