import { describe, it, expect } from 'vitest'
import { createRouter, createMemoryHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import router from '../index'

// 课程壳的「空子路径 → 默认 Tab」重定向只能用真实 push 验证：
// router.resolve() 不跟随 redirect，而相对重定向的解析基准正是本缺陷的根因。
const stub = { name: 'RouteStub', render: () => null }

// 探针路由表沿用真实结构，仅把组件替换为空壳，避免导航时加载真实视图
function strip(routes: readonly RouteRecordRaw[]): Record<string, unknown>[] {
  return routes.map((record) => {
    const node: Record<string, unknown> = { ...record }
    node.component = stub
    const children = node.children
    if (Array.isArray(children)) node.children = strip(children as RouteRecordRaw[])
    return node
  })
}

function probe() {
  return createRouter({
    history: createMemoryHistory(),
    routes: strip(router.options.routes) as unknown as RouteRecordRaw[],
  })
}

async function landedFrom(start: string, target: string) {
  const r = probe()
  await r.push(start)
  await r.push(target)
  return r.currentRoute.value.fullPath
}

describe('课程壳空路径重定向', () => {
  it('教师端从驾驶舱点裸课程 URL 落在课程概览，而非 404', async () => {
    expect(await landedFrom('/teacher/dashboard', '/teacher/courses/4001')).toBe(
      '/teacher/courses/4001/overview',
    )
  })

  it('教师端从「我的课程」列表点裸课程 URL 同样落在课程概览', async () => {
    expect(await landedFrom('/teacher/courses', '/teacher/courses/4001')).toBe(
      '/teacher/courses/4001/overview',
    )
  })

  it('教师端裸课程 URL 保留课程 id 参数', async () => {
    const r = probe()
    await r.push('/teacher/dashboard')
    await r.push('/teacher/courses/4023')
    expect(r.currentRoute.value.params.id).toBe('4023')
    expect(r.currentRoute.value.matched.map((m) => m.path)).toEqual([
      '/teacher',
      '/teacher/courses/:id',
      '/teacher/courses/:id/overview',
    ])
  })

  it('学生端从学习首页点裸课程 URL 落在课程详情壳的概览', async () => {
    expect(await landedFrom('/student/dashboard', '/student/courses/4001')).toBe(
      '/student/courses/4001/overview',
    )
  })

  it('两端裸课程 URL 直达（无历史起点）也不落入兜底 404', async () => {
    expect(await landedFrom('/', '/teacher/courses/4001')).toBe('/teacher/courses/4001/overview')
    expect(await landedFrom('/', '/student/courses/4001')).toBe('/student/courses/4001/overview')
  })

  it('具体 Tab 子路径不受重定向影响', async () => {
    expect(await landedFrom('/teacher/dashboard', '/teacher/courses/4001/analytics')).toBe(
      '/teacher/courses/4001/analytics',
    )
    expect(await landedFrom('/student/dashboard', '/student/courses/4001/learning-center')).toBe(
      '/student/courses/4001/learning-center',
    )
  })

  it('管理端从治理大盘点裸课程 URL 落在课程概览', async () => {
    expect(await landedFrom('/admin/dashboard', '/admin/courses/4007')).toBe(
      '/admin/courses/4007/overview',
    )
  })

  it('管理端裸课程 URL 保留课程 id，匹配链落到课程壳而非兜底 404', async () => {
    const r = probe()
    await r.push('/admin/users')
    await r.push('/admin/courses/4023')
    expect(r.currentRoute.value.params.id).toBe('4023')
    expect(r.currentRoute.value.matched.map((m) => m.path)).toEqual([
      '/admin',
      '/admin/courses/:id',
      '/admin/courses/:id/overview',
    ])
  })

  it('管理端裸课程 URL 冷启动直达（无历史起点）也不落兜底 404', async () => {
    expect(await landedFrom('/', '/admin/courses/4007')).toBe('/admin/courses/4007/overview')
  })

  it('课程列表页 /admin/courses 不被 courses/:id 壳吞掉', async () => {
    const r = probe()
    await r.push('/admin/dashboard')
    await r.push('/admin/courses')
    expect(r.currentRoute.value.fullPath).toBe('/admin/courses')
    expect(r.currentRoute.value.matched.map((m) => m.path)).toEqual(['/admin', '/admin/courses'])
    await r.push('/admin/courses/4007/bank')
    expect(r.currentRoute.value.matched.map((m) => m.path)).toEqual([
      '/admin',
      '/admin/courses/:id',
      '/admin/courses/:id/bank',
    ])
  })

  it('管理端课程壳 8 个页签表驱动命中壳记录，而非兜底 404', async () => {
    const adminTabSuffixes = [
      'overview',
      'chapters',
      'bank',
      'exams',
      'grades',
      'analytics',
      'knowledge',
      'ai',
    ]
    for (const suffix of adminTabSuffixes) {
      const r = probe()
      await r.push('/admin/courses')
      await r.push(`/admin/courses/4007/${suffix}`)
      expect(r.currentRoute.value.matched.map((m) => m.path)).toEqual([
        '/admin',
        '/admin/courses/:id',
        `/admin/courses/:id/${suffix}`,
      ])
      expect(r.currentRoute.value.fullPath).toBe(`/admin/courses/4007/${suffix}`)
      expect(r.currentRoute.value.params.id).toBe('4007')
    }
  })
})
