import { describe, expect, it } from 'vitest'
import router from '@/router'
import {
  ADMIN_COURSE_TABS,
  COURSE_TABS,
  PAGE_DEFS,
  courseTabs,
  navGroups,
  pageDef,
  type V2Role,
} from '../nav'
import { findModule } from '../data/modules'

const ROLES: V2Role[] = ['student', 'teacher', 'admin']
const CATCH_ALL = '/:pathMatch(.*)*'

function resolves(path: string): boolean {
  const matched = router.resolve(path).matched
  const leaf = matched[matched.length - 1]
  return Boolean(leaf) && leaf.path !== CATCH_ALL
}

describe('v2 导航与路由一致性', () => {
  it('三端侧栏条目都命中 /v2 路由，而不是兜底 404', () => {
    for (const role of ROLES) {
      for (const group of navGroups(role)) {
        for (const item of group.items) {
          expect(item.path.startsWith(`/v2/${role}/`), `${role}/${item.id} 不在本端命名空间`).toBe(
            true,
          )
          expect(resolves(item.path), `${role}/${item.id} 是死链：${item.path}`).toBe(true)
        }
      }
    }
  })

  it('v2 登录/注册/找回密码/法务/错误页是公开路由，且不会被兜底 404 拦截', () => {
    for (const path of [
      '/v2/login',
      '/v2/register',
      '/v2/forgot-password',
      '/v2/agreement',
      '/v2/privacy',
      '/v2/403',
      '/v2/404',
      '/v2/503',
    ]) {
      const matched = router.resolve(path).matched
      const leaf = matched[matched.length - 1]
      expect(leaf, `${path} 未命中路由`).toBeTruthy()
      expect(leaf.path, `${path} 落到了兜底`).not.toBe(CATCH_ALL)
      expect(leaf.path, `${path} 落到了 v2 兜底`).not.toContain(':pathMatch')
      expect(
        matched.some((record) => record.meta?.requiresAuth === false),
        `${path} 不是公开路由`,
      ).toBe(true)
    }
  })

  it('v2 三端首页不被 v2 兜底 404 拦截', () => {
    for (const path of ['/v2/student/home', '/v2/teacher/home', '/v2/admin/home']) {
      const leaf = router.resolve(path).matched.at(-1)
      expect(leaf?.path, `${path} 被 v2 兜底接走`).not.toContain(':pathMatch')
    }
  })

  it('课程页签路径全部命中课程壳路由', () => {
    for (const role of ROLES) {
      for (const tab of courseTabs(role)) {
        const path = `/v2/${role}/courses/1/${tab.id}`
        expect(resolves(path), `${role}/${tab.id} 是死链：${path}`).toBe(true)
      }
    }
  })

  it('课程页签数量与原型 IA 对齐，并补回教师侧旧端的题库与监考页签', () => {
    expect(COURSE_TABS.student).toHaveLength(8)
    expect(COURSE_TABS.teacher).toHaveLength(11)
    expect(ADMIN_COURSE_TABS).toHaveLength(10)
    const teacherTabs = courseTabs('teacher').map((tab) => tab.id)
    expect(courseTabs('teacher')).toHaveLength(COURSE_TABS.teacher.length + 2)
    expect(teacherTabs).toContain('bank')
    expect(teacherTabs).toContain('proctor')
    expect(courseTabs('student').map((tab) => tab.id)).not.toContain('proctor')
  })

  it('教师侧栏补回题库/试卷/成绩/互动入口，且都命中真实路由', () => {
    const paths = navGroups('teacher').flatMap((group) => group.items.map((item) => item.path))
    for (const path of [
      '/v2/teacher/bank',
      '/v2/teacher/exams',
      '/v2/teacher/grades',
      '/v2/teacher/interaction',
    ]) {
      expect(paths, `教师侧栏缺少 ${path}`).toContain(path)
      expect(resolves(path), `${path} 是死链`).toBe(true)
    }
  })

  it('新建课程不被课程空间的 :id 动态段吞掉', () => {
    const leaf = router.resolve('/v2/teacher/courses/create').matched.slice(-1)[0]
    expect(leaf.meta.v2page, '创建课程落到了课程空间路由').toBe('createCourse')
    expect(leaf.meta.v2course).toBeUndefined()
    expect(
      router.resolve('/v2/teacher/courses/4060/overview').matched.slice(-1)[0].meta.v2course,
    ).toBe(true)
  })

  it('每个全局页面定义都有标题与描述', () => {
    for (const role of ROLES) {
      for (const [page, def] of Object.entries(PAGE_DEFS[role])) {
        expect(pageDef(role, page).title, `${role}/${page} 缺标题`).toBeTruthy()
        expect(def.desc, `${role}/${page} 缺描述`).toBeTruthy()
      }
    }
  })

  it('声明了 moduleId 的页面都能在模块注册表里找到模块（阴性对照：不存在的 id 必须查不到）', () => {
    for (const role of ROLES) {
      for (const [page, def] of Object.entries(PAGE_DEFS[role])) {
        if (!def.moduleId) continue
        expect(
          findModule(role, def.moduleId),
          `${role}/${page} 的模块 ${def.moduleId} 不存在`,
        ).toBeTruthy()
      }
    }
    expect(findModule('teacher', 'not-a-module')).toBeUndefined()
  })
})
