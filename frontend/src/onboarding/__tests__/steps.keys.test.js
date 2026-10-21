import { describe, it, expect } from 'vitest'
import { STUDENT_SECTIONS, TEACHER_SECTIONS, ADMIN_SECTIONS } from '@/layouts/menus'
import { loadSteps } from '../steps/loader'

const flatten = (sections) => {
  const keys = new Set()
  const walk = (items) => items.forEach((i) => (keys.add(i.key), i.children && walk(i.children)))
  sections.forEach((s) => walk(s.items))
  return keys
}

const BY_ROLE = {
  student: flatten(STUDENT_SECTIONS),
  teacher: flatten(TEACHER_SECTIONS),
  admin: flatten(ADMIN_SECTIONS),
}

// 期望值钉死定档内容，不从 steps 文件反抄
const EXPECTED_NAV = {
  student: ['ai-assistant', 'graph'],
  teacher: ['t-assess', 't-grades', 't-ai'],
  admin: ['dashboard', 'users', 'audit'],
}

describe('导航锚点键必须真的来自 menus.ts', () => {
  for (const role of ['student', 'teacher', 'admin']) {
    it(`${role} 端每个 nav: 锚点都能在 menus.ts 找到对应 key`, () => {
      const used = loadSteps(role)
        .map((s) => s.anchor)
        .filter((a) => a?.startsWith('nav:'))
        .map((a) => a.slice(4))
      const ghost = used.filter((k) => !BY_ROLE[role].has(k))
      expect(
        ghost,
        `menus.ts 里没有这些 key：${ghost.join(', ')}——改 steps 或补菜单，不要留幽灵锚点`,
      ).toEqual([])
      const actual = [...new Set(used)].sort()
      const expected = [...EXPECTED_NAV[role]].sort()
      expect(
        actual,
        `${role} 端 nav 锚点集合与三端定档表不符：实际 ${actual.join(', ')}，期望 ${expected.join(', ')}`,
      ).toEqual(expected)
    })
  }
})
