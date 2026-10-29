import { describe, expect, it } from 'vitest'
import { PAGE_DEFS, courseTabs, type V2Role } from '../nav'
import { demoBlock } from '../data/pageData'
import { courseTabBlock } from '../data/courseTabData'

const ROLES: V2Role[] = ['student', 'teacher', 'admin']

/**
 * 示例块必须自带「示例」字样。
 * 判据来自本仓「降级必须可区分」：页面读数分不清真假时，演示与验收都会被问穿。
 */
const hasCaveat = (note?: string) => Boolean(note && note.includes('示例'))

describe('v2 示例块警示守卫', () => {
  it('阴性对照：合成块缺「示例」字样时必须判红、带上时判绿', () => {
    expect(
      hasCaveat('账号与安全的后端接口已存在，本版仅展示会话信息。'),
      '缺警示的块应被判红',
    ).toBe(false)
    expect(hasCaveat('示例数据：该页签的后端聚合端点尚未接入。'), '带警示的块应被判绿').toBe(true)
    expect(hasCaveat(undefined), '没有 note 的块应被判红').toBe(false)
  })

  it('全局页：demoBlock 返回的每一块都自带「示例」警示', () => {
    const offenders: string[] = []
    for (const role of ROLES) {
      for (const page of Object.keys(PAGE_DEFS[role])) {
        const block = demoBlock(role, page)
        if (block && !hasCaveat(block.note)) offenders.push(`${role}/${page}`)
      }
    }
    expect(offenders, `这些示例块的文案读起来像真数据：${offenders.join('、')}`).toEqual([])
  })

  it('课程页签：courseTabBlock 返回的每一块都自带「示例」警示', () => {
    const offenders: string[] = []
    for (const role of ROLES) {
      for (const tab of courseTabs(role)) {
        const block = courseTabBlock(role, tab.id)
        if (block && !hasCaveat(block.note)) offenders.push(`${role}/${tab.id}`)
      }
    }
    expect(offenders, `这些课程页签的示例块没有警示：${offenders.join('、')}`).toEqual([])
  })

  it('课程页签警示要带上这一页的口径句，不能只剩「示例数据」四个字', () => {
    const block = courseTabBlock('admin', 'bank')
    expect(block, '管理员课程题库应返回示例块').toBeTruthy()
    expect(block?.note, `实际文案：${block?.note}`).toContain('管理员只读')
  })

  it('教学班页签不得返回假班级读数：按已拍口径走明确空态（B-13① 未开工）', () => {
    expect(courseTabBlock('teacher', 'classes'), '教师端教学班页签仍有假数据').toBeNull()
    expect(courseTabBlock('admin', 'classes'), '管理员端教学班页签仍有假数据').toBeNull()
  })

  it('教师端页签已全部接真组件，示例层不再供数（M-20）', () => {
    for (const tab of courseTabs('teacher')) {
      expect(courseTabBlock('teacher', tab.id), `教师端 ${tab.id} 仍返回示例块`).toBeNull()
    }
  })

  it('管理员端 exams／grades 由演示组件渲染，示例层的对应块是死数据（M-20）', () => {
    expect(courseTabBlock('admin', 'exams'), 'admin/exams 仍返回示例块').toBeNull()
    expect(courseTabBlock('admin', 'grades'), 'admin/grades 仍返回示例块').toBeNull()
    // 反面：admin/content 与 admin/bank 确实走 ModuleTab，必须仍有块且带警示
    expect(hasCaveat(courseTabBlock('admin', 'content')?.note)).toBe(true)
    expect(hasCaveat(courseTabBlock('admin', 'bank')?.note)).toBe(true)
  })
})
