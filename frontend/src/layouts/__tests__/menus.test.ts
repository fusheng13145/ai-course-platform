import { describe, it, expect } from 'vitest'
import router from '@/router'
import {
  ADMIN_SECTIONS,
  TEACHER_SECTIONS,
  STUDENT_SECTIONS,
  COURSE_TABS,
  buildCourseCatalogSection,
} from '../menus'

/**
 * 侧边栏导航与路由表的一致性守护
 * <p>导航配置（menus.ts）与路由表（router/index.ts）是两处独立声明，历史上出现过
 * 「教师端静态区清空后 /teacher/dashboard 成为无入口落点」「学生端跨课程路由缺入口」
 * 「管理端 /admin/exams、/admin/grades 收敛后漏掉入口」三类漂移。本套件把两侧对齐关系
 * 固化为断言：每条导航路径必须命中真实路由，管理端每条治理叶子路由也必须真有入口，
 * 且三端的收敛口径（三端统一为「动态课程目录 + 静态分区」）不回退。</p>
 */

const DEMO_COURSES = [{ id: 1, name: '示例课程' }]

function collectPaths(sections) {
  const paths = []
  const walk = (items) => {
    for (const item of items) {
      if (item.path) paths.push(item.path)
      if (item.children) walk(item.children)
    }
  }
  for (const sec of sections) walk(sec.items)
  return paths
}

/** 某角色侧边栏实际会出现的全部路径：静态区 + 动态课程目录（含三级页签） */
function navPathsOf(role) {
  const staticSections =
    role === 'ADMIN' ? ADMIN_SECTIONS : role === 'TEACHER' ? TEACHER_SECTIONS : STUDENT_SECTIONS
  return [
    ...collectPaths(buildCourseCatalogSection(role, DEMO_COURSES)),
    ...collectPaths(staticSections),
  ]
}

/** 命中 404 兜底路由（catch-all）等同于死链 */
function isLiveRoute(path) {
  const matched = router.resolve(path).matched
  return matched.length > 0 && matched[matched.length - 1].path !== '/:pathMatch(.*)*'
}

describe('导航路径必须命中真实路由', () => {
  for (const role of ['ADMIN', 'TEACHER', 'STUDENT']) {
    it(`${role} 端 ${navPathsOf(role).length} 条导航路径全部可达`, () => {
      const dead = navPathsOf(role).filter((p) => !isLiveRoute(p))
      expect(dead).toEqual([])
    })
  }

  it('课程目录三级页签与 /{role}/courses/:id/* 子路由一一对应', () => {
    for (const role of ['TEACHER', 'STUDENT', 'ADMIN']) {
      for (const tab of COURSE_TABS[role]) {
        expect(isLiveRoute(`/${role.toLowerCase()}/courses/1/${tab.suffix}`)).toBe(true)
      }
    }
  })
})

describe('三端收敛口径不回退', () => {
  it('教师端保留登录落点「教学概览」，避免进入后无入口可回', () => {
    expect(navPathsOf('TEACHER')).toContain('/teacher/dashboard')
  })

  it('学生端静态区补齐三个跨课程入口，课程级功能仍走三级页签', () => {
    const paths = navPathsOf('STUDENT')
    for (const p of [
      '/student/knowledge-graph',
      '/student/exams',
      '/student/grades',
      '/student/dashboard',
      '/student/wrong-questions',
      '/student/learning-path',
      '/student/learning-calendar',
      '/student/discussion',
      '/student/notifications',
    ]) {
      expect(paths).toContain(p)
    }
    // 跨课程入口与课程级页签共存：同一功能两种归属都要在
    expect(paths).toContain('/student/courses/1/exams')
    expect(paths).toContain('/student/courses/1/grades')
    expect(paths).toContain('/student/courses/1/knowledge')
  })

  it('管理员端治理大盘为静态区首节，课程维度经动态目录进入', () => {
    // 配置层不锁组装顺序（那由 AppSidebar 决定，见 AppSidebar.test 的分区标签顺序断言），
    // 这里只锁：静态区第一节必须是落点，且课程目录能为 ADMIN 构建出真实页签路径。
    expect(ADMIN_SECTIONS[0].items[0].path).toBe('/admin/dashboard')
    const paths = navPathsOf('ADMIN')
    expect(paths).toContain('/admin/dashboard')
    expect(paths).toContain('/admin/courses/1/overview')
    expect(paths).toContain('/admin/courses/1/ai')
    expect(paths).toContain('/admin/courses')
  })

  it('管理员端课程页签集合是治理子集，不含教师运营页签', () => {
    const suffixes = COURSE_TABS.ADMIN.map((t) => t.suffix)
    expect(suffixes).toEqual([
      'overview',
      'chapters',
      'bank',
      'exams',
      'grades',
      'analytics',
      'knowledge',
      'ai',
    ])
    for (const excluded of ['submissions', 'proctor', 'interaction', 'tasks', 'discussion']) {
      expect(suffixes).not.toContain(excluded)
    }
  })

  it('静态 ADMIN_SECTIONS 不出现「课程目录」，禁止静态/动态双写', () => {
    expect(ADMIN_SECTIONS.some((sec) => sec.label === '课程目录')).toBe(false)
    expect(ADMIN_SECTIONS).toHaveLength(6)
  })

  it('管理端每条治理叶子路由都有导航入口（孤儿收编后不得回退）', () => {
    const covered = new Set([
      ...navPathsOf('ADMIN'),
      '/admin/profile',
      '/admin/message-center',
      '/admin',
      '/admin/courses/:id',
    ])
    const adminLeaves = router
      .getRoutes()
      .filter((r) => r.path.startsWith('/admin/'))
      .filter((r) => !r.children.length)
      .map((r) => r.path)
    const orphans = adminLeaves.filter(
      (p) => !covered.has(p) && !/^\/admin\/courses\/:id\//.test(p),
    )
    expect(orphans, `侧边栏无入口的管理端路由：${orphans.join(', ')}`).toEqual([])
  })
})
