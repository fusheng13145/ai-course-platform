import { describe, it, expect, beforeEach } from 'vitest'
import { ALL_ANCHOR_KEYS, isRegisteredAnchor, resolveAnchor } from '../anchors'
import { loadSteps } from '../steps/loader'

// 反例喂合成 DOM，不读活文件：断言不许钉在会被改的活文件上
describe('anchors 注册表', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('合成 DOM 里 data-onboard 命中即返回该元素', () => {
    document.body.innerHTML = '<div data-onboard="join-course" id="x"></div>'
    expect(resolveAnchor('join-course')?.id).toBe('x')
  })

  it('nav: 前缀锚点同样按 data-onboard 解析', () => {
    document.body.innerHTML = '<div data-onboard="nav:ai-assistant" id="y"></div>'
    expect(resolveAnchor('nav:ai-assistant')?.id).toBe('y')
  })

  it('未注册的 key 直接抛，不返回 null（静默 null 会让锚点漂移无人知）', () => {
    expect(() => resolveAnchor('nope')).toThrow(/未注册锚点/)
  })

  it('isRegisteredAnchor 认非前缀键与 nav: 前缀，不认野键', () => {
    expect(isRegisteredAnchor('side-user')).toBe(true)
    expect(isRegisteredAnchor('nav:whatever')).toBe(true)
    expect(isRegisteredAnchor('topbar')).toBe(false)
  })

  it('三端 steps 用到的每个锚点都已注册', () => {
    const used = new Set()
    for (const role of ['student', 'teacher', 'admin']) {
      loadSteps(role).forEach((s) => s.anchor && used.add(s.anchor))
    }
    const missing = [...used].filter((k) => !isRegisteredAnchor(k))
    expect(missing, `steps 引用了未注册锚点：${missing.join(', ')}`).toEqual([])
  })

  it('注册了但没人用的裸键必红（要么删、要么有步骤用它）', () => {
    const used = new Set()
    for (const role of ['student', 'teacher', 'admin']) {
      loadSteps(role).forEach((s) => s.anchor && used.add(s.anchor))
    }
    const unused = ALL_ANCHOR_KEYS.filter((k) => !used.has(k))
    expect(unused, `未使用的锚点：${unused.join(', ')}`).toEqual([])
  })
})
