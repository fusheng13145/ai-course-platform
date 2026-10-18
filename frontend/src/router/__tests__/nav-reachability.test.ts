import { describe, it, expect } from 'vitest'
import { readdirSync, statSync, readFileSync } from 'node:fs'
import { join, relative } from 'node:path'
import router from '../index'
import { ADMIN_SECTIONS, TEACHER_SECTIONS, STUDENT_SECTIONS, COURSE_TABS } from '@/layouts/menus'

// 导航目标可达性门禁：任何侧边栏条目、页签、以及源码里写死的
// router.push/replace/next('/…') 字面量，都必须命中真实路由记录，
// 而不是落到根兜底 `/:pathMatch(.*)*`（那就是用户看到的 404）。
const CATCH_ALL = '/:pathMatch(.*)*'
const SRC_ROOT = join(process.cwd(), 'src')
/** 模板字符串插值段的占位符，比对时按「任意一段」处理 */
const DYNAMIC = '\u0001'

function collectLiteralTargets(dir: string, found: Map<string, string[]>) {
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry)
    if (statSync(full).isDirectory()) {
      if (entry === '__tests__') continue
      collectLiteralTargets(full, found)
      continue
    }
    if (!/\.(ts|vue|js)$/.test(entry)) continue
    const text = readFileSync(full, 'utf8')
    const hits: string[] = []
    const re = /(?:\$?router\.(?:push|replace)|next)\(\s*[{]?\s*(?:path\s*:\s*)?['`]([^'`]+)['`]/g
    let m: RegExpExecArray | null
    while ((m = re.exec(text))) {
      const raw = m[1]
      if (!raw.startsWith('/')) continue
      hits.push(raw.replace(/\$\{[^}]*\}/g, DYNAMIC))
    }
    if (hits.length) found.set(relative(SRC_ROOT, full).replace(/\\/g, '/'), hits)
  }
}

function resolveBad(target: string) {
  const matched = router.resolve(target).matched
  const leaf = matched[matched.length - 1]
  return !leaf || leaf.path === CATCH_ALL
}

/**
 * 含插值段的目标按段比对路由记录：
 * 插值段与路由的 `:param` 都是「任意一段」，其余段必须字面相等。
 * （`\`/teacher/courses/${id}/${tab}\`` 这种写法里 tab 是运行期页签键，
 * 用固定样例值去 resolve 会把合法跳转误判成死链。）
 */
function matchesRoutePattern(target: string) {
  const segs = target.split('/').filter(Boolean)
  return router
    .getRoutes()
    .filter((r) => r.path !== CATCH_ALL)
    .some((r) => {
      const rs = r.path.split('/').filter(Boolean)
      if (rs.length !== segs.length) return false
      return segs.every((s, i) => s === DYNAMIC || rs[i].startsWith(':') || rs[i] === s)
    })
}

function isDead(target: string) {
  return target.includes(DYNAMIC) ? !matchesRoutePattern(target) : resolveBad(target)
}

describe('导航目标可达性', () => {
  it('三端侧边栏静态条目全部命中真实路由', () => {
    const paths: string[] = []
    for (const sections of [ADMIN_SECTIONS, TEACHER_SECTIONS, STUDENT_SECTIONS]) {
      for (const section of sections) {
        for (const item of section.items) {
          if (item.path) paths.push(item.path)
          for (const child of item.children ?? []) paths.push(child.path)
        }
      }
    }
    const bad = paths.filter(isDead)
    expect(bad, `侧边栏死链：${bad.join(', ')}`).toEqual([])
  })

  it('课程目录三级页签全部命中真实路由', () => {
    const paths: string[] = []
    for (const [role, tabs] of Object.entries(COURSE_TABS)) {
      for (const tab of tabs as { suffix: string }[]) {
        paths.push(`/${role.toLowerCase()}/courses/1/${tab.suffix}`)
      }
    }
    const bad = paths.filter(isDead)
    expect(bad, `课程页签死链：${bad.join(', ')}`).toEqual([])
  })

  it('admin 课程壳页签标题与 COURSE_TABS.ADMIN 单一来源一致', () => {
    // 按 suffix 精确匹配 path 尾段取壳叶子（空路径 redirect 子记录 path 无尾段、也无 meta.title，
    // 不依赖数组顺序）
    const adminShellLeaf = (suffix: string) =>
      router.getRoutes().find((r) => r.path === `/admin/courses/:id/${suffix}`)
    const shellLeaves = router
      .getRoutes()
      .filter((r) => /^\/admin\/courses\/:id\/[^/]+$/.test(r.path))

    expect(shellLeaves).toHaveLength(8)
    // 与页签数对齐：防止只加页签忘了加路由
    expect(shellLeaves.length).toBe(COURSE_TABS.ADMIN.length)

    for (const tab of COURSE_TABS.ADMIN) {
      const leaf = adminShellLeaf(tab.suffix)
      expect(leaf, `admin 课程壳缺少页签路由：${tab.suffix}`).toBeDefined()
      expect(leaf?.meta?.title, `${tab.suffix} 标题与侧边栏文案分叉`).toBe(tab.text)
    }

    // 阴性对照：discussion 是教师端页签、admin 壳下不存在。
    // 同一查法若能命中它，上面的断言就是恒真的；这里必须查不到。
    expect(COURSE_TABS.TEACHER.some((t) => t.suffix === 'discussion')).toBe(true)
    expect(adminShellLeaf('discussion')).toBeUndefined()
  })

  it('源码里写死的跳转目标全部命中真实路由', () => {
    const targets = new Map<string, string[]>()
    collectLiteralTargets(SRC_ROOT, targets)
    const bad: string[] = []
    for (const [file, list] of targets) {
      for (const target of new Set(list)) {
        if (isDead(target)) bad.push(`${file} → ${target}`)
      }
    }
    expect(bad, `写死跳转的死链：\n${bad.join('\n')}`).toEqual([])
  })

  it('扫描确实取到了跳转字面量（防止正则退化后静默通过）', () => {
    const targets = new Map<string, string[]>()
    collectLiteralTargets(SRC_ROOT, targets)
    const all = [...targets.values()].flat()
    const files = [...targets.keys()]
    expect(all.length).toBeGreaterThan(30)
    // 逐文件核对若干已知写死点，确保扫描真的覆盖到导航热区
    expect(files).toContain('layouts/components/AppTopbar.vue')
    expect(files).toContain('views/teacher/Courses.vue')
    expect(all).toContain('/teacher/courses')
    expect(all).toContain('/login')

    // 反向自检：判定逻辑本身要能认出死链，否则前三条用例会因规则过松而空转
    expect(isDead('/teacher/courses/1/no-such-tab')).toBe(true)
    expect(isDead('/no/such/route/at/all')).toBe(true)
    expect(isDead('/teacher/dashboard')).toBe(false)
    // 插值段按「任意一段」处理，但段数与其余字面段仍需真实路由可对上
    expect(isDead(`/no/${DYNAMIC}/route`)).toBe(true)
  })
})
