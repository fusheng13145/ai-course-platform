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

describe('导航锚点键必须真的来自 menus.ts', () => {
  for (const role of ['student', 'teacher', 'admin']) {
    it(`${role} 端每个 nav: 锚点都能在 menus.ts 找到对应 key`, () => {
      const used = loadSteps(role)
        .map((s) => s.anchor)
        .filter((a) => a?.startsWith('nav:'))
        .map((a) => a.slice(4))
      expect(used.length).toBeGreaterThanOrEqual(2)
      const ghost = used.filter((k) => !BY_ROLE[role].has(k))
      expect(
        ghost,
        `menus.ts 里没有这些 key：${ghost.join(', ')}——改 steps 或补菜单，不要留幽灵锚点`,
      ).toEqual([])
    })
  }
})
