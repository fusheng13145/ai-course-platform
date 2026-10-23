import { describe, it, expect, beforeEach, vi } from 'vitest'
import {
  readState,
  writeStatus,
  migrateV1IfPresent,
  KEY_V1_PREFIX,
  KEY_V2_PREFIX,
} from '../state'

const k1 = (id) => `${KEY_V1_PREFIX}:${id ?? 'anon'}`
const k2 = (id) => `${KEY_V2_PREFIX}:${id ?? 'anon'}`

describe('onboarding state 契约 v2', () => {
  beforeEach(() => localStorage.clear())

  it('空存储 ⇒ idle@0', () => expect(readState(7)).toEqual({ status: 'idle', atStep: 0 }))

  it('三态互不覆盖：paused@2 之后再写 skipped@2，读到的是 skipped', () => {
    writeStatus(7, 'paused', 2)
    writeStatus(7, 'skipped', 2)
    expect(readState(7)).toEqual({ status: 'skipped', atStep: 2 })
  })

  it('v1 有标记且无 v2 ⇒ 迁移成 done，且 v1 键不删（回滚证据）', () => {
    localStorage.setItem(k1(7), '1')
    expect(migrateV1IfPresent(7)).toBe(true)
    expect(readState(7).status).toBe('done')
    expect(localStorage.getItem(k1(7))).toBe('1')
  })

  it('已有 v2 ⇒ 不迁移、不覆盖', () => {
    localStorage.setItem(k1(7), '1')
    writeStatus(7, 'paused', 3)
    expect(migrateV1IfPresent(7)).toBe(false)
    expect(readState(7).atStep).toBe(3)
  })

  it('按账号隔离（DR-52／D2-9 定案口径不变）', () => {
    writeStatus(1, 'done', 5)
    expect(readState(2).status).toBe('idle')
  })

  it('userId 缺失退化为 anon，不压别的账号', () => {
    writeStatus(null, 'done', 5)
    expect(readState(undefined).status).toBe('done')
    expect(readState(1).status).toBe('idle')
  })

  // 两处偏离 brief 原文（本仓规矩：红话必须只红这一条）：
  // ① 匹配器用 stringContaining——toHaveBeenCalledWith 对裸正则是按"参数等于那个正则对象"比，
  //    永不成立（brief 原文的假红）；同文件 Step 5 的 anchor-miss 用的就是 stringContaining。
  // ② 还原放进 finally——断言转红时也要把真 localStorage 换回来，否则它后面每条都红在
  //    `localStorage.clear is not a function`，把一条真红放大成三条假红。
  it('写失败不静默（改前 catch{} 把写失败与读失败一样吞掉）', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const real = window.localStorage
    Object.defineProperty(window, 'localStorage', {
      configurable: true,
      value: {
        getItem: () => null,
        setItem: () => {
          throw new Error('quota')
        },
      },
    })
    try {
      writeStatus(7, 'paused', 2)
      expect(warn).toHaveBeenCalledWith(expect.stringContaining('写入失败'))
    } finally {
      Object.defineProperty(window, 'localStorage', { configurable: true, value: real })
      warn.mockRestore()
    }
  })

  it('脏值（status 不在枚举内）读成 idle，不把坏 JSON 抛给调用方', () => {
    localStorage.setItem(k2(3), JSON.stringify({ status: 'wat', atStep: 9 }))
    expect(readState(3)).toEqual({ status: 'idle', atStep: 0 })
  })

  it('v2 记录带 role：writeStatus 把 role 落进 JSON，readState 仍不泄漏进契约返回值', () => {
    writeStatus(7, 'paused', 2, 'teacher')
    // 直接读落盘原文钉"形状里有 role"——不复用 readState 的读法，否则实现漏写会一起绿
    expect(localStorage.getItem(k2(7))).toContain('"role":"teacher"')
    expect(readState(7)).toEqual({ status: 'paused', atStep: 2 })
  })
})
