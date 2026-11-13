import { describe, it, expect, vi, beforeEach } from 'vitest'
import {
  getChartTheme,
  getMonoSeries,
  getMonoStatus,
  monoScore,
  onThemeChange,
  emitThemeChange,
  THEME_CHANGE_EVENT,
} from '@/utils/chartTheme'

/**
 * 模拟主题环境：设置 data-theme 与 window.matchMedia，
 * 覆盖 chartTheme 的 resolveTheme 内部逻辑（显式主题优先、系统偏好回退）。
 */
function mockTheme(theme: string) {
  document.documentElement.setAttribute('data-theme', theme)
  window.matchMedia = vi.fn().mockImplementation((query) => ({
    matches: theme === 'dark',
    media: query,
    onchange: null,
    addListener: vi.fn(),
    removeListener: vi.fn(),
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    dispatchEvent: vi.fn(),
  }))
}

describe('chartTheme 图表主题工具', () => {
  beforeEach(() => {
    document.documentElement.removeAttribute('data-theme')
  })

  it('getChartTheme 在 light 主题下返回浅色配色', () => {
    mockTheme('light')
    const t = getChartTheme()
    expect(t.text).toBe('#1f2733')
    expect(t.tooltipBg).toBe('#ffffff')
  })

  it('getChartTheme 在 dark 主题下返回深色配色', () => {
    mockTheme('dark')
    const t = getChartTheme()
    expect(t.text).toBe('#eef1f6')
    expect(t.tooltipBg).toBe('#1c2330')
  })

  it('getMonoSeries 返回 8 色系列色板', () => {
    mockTheme('light')
    const series = getMonoSeries()
    expect(series).toHaveLength(8)
    expect(series[0]).toBe('#c13b30')
  })

  it('getMonoStatus 返回状态语义配色', () => {
    mockTheme('light')
    const status = getMonoStatus()
    expect(status.EASY).toBe('#2e7d4f')
    expect(status.DANGER).toBe('#c13b30')
  })

  it('monoScore 按分数档位返回配色', () => {
    mockTheme('light')
    expect(monoScore(95)).toBe('#2e7d4f')
    expect(monoScore(85)).toBe('#2563a8')
    expect(monoScore(75)).toBe('#6d4fa1')
    expect(monoScore(65)).toBe('#b57908')
    expect(monoScore(55)).toBe('#c13b30')
  })

  it('onThemeChange 注册回调，emitThemeChange 触发回调并派发事件', () => {
    mockTheme('light')
    const dispatchSpy = vi.spyOn(window, 'dispatchEvent')
    const cb = vi.fn()
    onThemeChange(cb)
    emitThemeChange()
    expect(cb).toHaveBeenCalledTimes(1)
    expect(dispatchSpy).toHaveBeenCalledWith(expect.any(Event))
    expect(dispatchSpy.mock.calls[0][0].type).toBe(THEME_CHANGE_EVENT)
  })

  it('onThemeChange 对同一回调去重', () => {
    mockTheme('light')
    const cb = vi.fn()
    onThemeChange(cb)
    onThemeChange(cb)
    emitThemeChange()
    expect(cb).toHaveBeenCalledTimes(1)
  })
})
