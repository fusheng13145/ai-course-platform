import { describe, it, expect } from 'vitest'
import { validateSteps, loadSteps } from '../steps/loader'

const step = (over) => ({ id: 'x', title: 't', desc: 'd', scope: 'shell', ...over })

describe('steps 排序不变式（spec §3.4）', () => {
  it('page 锚点只能是最后一步', () => {
    expect(() => validateSteps([step({ anchor: 'join-course', scope: 'page' }), step({})])).toThrow(
      /page 锚点只能是最后一步/,
    )
  })

  it('page 步骤在末位时通过', () => {
    expect(() =>
      validateSteps([step({}), step({ anchor: 'join-course', scope: 'page' })]),
    ).not.toThrow()
  })

  it('两个 page 步骤必红（哪怕第二个在末位）', () => {
    expect(() =>
      validateSteps([
        step({ anchor: 'join-course', scope: 'page' }),
        step({ anchor: 'side-user', scope: 'page' }),
      ]),
    ).toThrow(/page 锚点只能是最后一步/)
  })

  it('未注册锚点红', () => {
    expect(() =>
      // 两枚：单枚会先触发长度不变式，掩盖这里要测的锚点校验
      validateSteps([step({ anchor: 'nav:nope-not-registered' }), step({})]),
    ).not.toThrow()
    // nav: 前缀天然"已注册"，真正的漂移由 steps.keys.test.js 按 menus.ts 兜住
    expect(() => validateSteps([step({ anchor: 'bogus' }), step({})])).toThrow(/未注册锚点/)
  })

  it('少于两步红（欢迎＋激活动作是下限）', () => {
    expect(() => validateSteps([step({})])).toThrow(/至少两步/)
  })

  it('三端真实 steps 全部通过，且每端恰好一个激活动作步、且它在末位', () => {
    for (const role of ['student', 'teacher', 'admin']) {
      const steps = loadSteps(role)
      expect(() => validateSteps(steps)).not.toThrow()
      const acts = steps.filter((s) => s.action)
      expect(acts.length, `${role} 端激活动作步数`).toBe(1)
      expect(steps[steps.length - 1].action, `${role} 端激活动作必须在最后一步`).toBeTruthy()
    }
  })
})
