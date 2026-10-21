import { describe, it, expect } from 'vitest'
import fs from 'node:fs'

// 剥注释后再匹配：否则"注释里提了一句"也能把守卫喂绿（本仓已犯过四次同型错）
const strip = (s) =>
  s
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '')

const read = (p) => strip(fs.readFileSync(new URL(p, import.meta.url), 'utf8'))

describe('data-onboard 绑定必须还在壳层里', () => {
  it('侧栏三处循环各自绑定 nav:key，用户区绑定 side-user', () => {
    const src = read('../../layouts/components/AppSidebar.vue')
    expect(
      (src.match(/data-onboard=/g) || []).length,
      'AppSidebar 里的 data-onboard 绑定数',
    ).toBeGreaterThanOrEqual(4)
    expect(src).toMatch(/:data-onboard="`nav:\$\{item\.key\}`"/)
    expect(src).toMatch(/:data-onboard="`nav:\$\{child\.key\}`"/)
    expect(src).toMatch(/:data-onboard="`nav:\$\{leaf\.key\}`"/)
    expect(src).toMatch(/data-onboard="side-user"/)
  })

  it('学生端加入课程卡绑定 join-course', () => {
    expect(read('../../views/student/components/SdCourseGrid.vue')).toMatch(
      /data-onboard="join-course"/,
    )
  })
})
