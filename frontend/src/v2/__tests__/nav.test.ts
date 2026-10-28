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

  it('v2 登录/注册是公开路由，且不会被兜底 404 拦截', () => {
    for (const path of ['/v2/login', '/v2/register']) {
      const matched = router.resolve(path).matched
      const leaf = matched[matched.length - 1]
      expect(leaf, `${path} 未命中路由`).toBeTruthy()
      expect(leaf.path, `${path} 落到了兜底`).not.toBe(CATCH_ALL)
      expect(
        matched.some((record) => record.meta?.requiresAuth === false),
        `${path} 不是公开路由`,
      ).toBe(true)
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

  it('课程页签数量与原型 IA 对齐', () => {
    expect(COURSE_TABS.student).toHaveLength(8)
    expect(COURSE_TABS.teacher).toHaveLength(11)
    expect(ADMIN_COURSE_TABS).toHaveLength(10)
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
