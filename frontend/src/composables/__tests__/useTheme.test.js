import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'

// Mock chartTheme 工具
const mockEmitThemeChange = vi.fn()
vi.mock('@/utils/chartTheme', () => ({
  emitThemeChange: mockEmitThemeChange,
}))

// jsdom 未实现 matchMedia，需模拟
const matchMediaMock = vi.fn((query) => ({
  matches: false,
  media: query,
  onchange: null,
  addListener: vi.fn(),
  removeListener: vi.fn(),
  addEventListener: vi.fn(),
  removeEventListener: vi.fn(),
  dispatchEvent: vi.fn(),
}))
Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: matchMediaMock,
})

const THEME_KEY = 'fz-theme'

/** 挂载使用 useTheme 的最小组件，触发 onMounted 生命周期 */
async function mountThemeHarness() {
  const { useTheme } = await import('@/composables/useTheme')
  const Harness = {
    template: '<div />',
    setup() {
      return useTheme()
    },
  }
  return mount(Harness)
}

describe('useTheme', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    // 系统主题默认偏好 light
    matchMediaMock.mockReturnValue({
      matches: false,
      media: '(prefers-color-scheme: dark)',
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })
    document.documentElement.setAttribute('data-theme', 'light')
    // theme ref 是模块级单例，每测重置模块注册表保证状态隔离
    vi.resetModules()
  })

  // ===================== 默认主题 =====================

  it('无存储偏好时默认主题为 dark', async () => {
    const { useTheme } = await import('@/composables/useTheme')
    const { theme } = useTheme()
    expect(theme.value).toBe('dark')
  })

  it('组件挂载时应用默认主题', async () => {
    await mountThemeHarness()
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })

  // ===================== 主题切换 =====================

  it('cycleTheme 按 light → dark → system 循环切换', async () => {
    const { useTheme } = await import('@/composables/useTheme')
    const { theme, cycleTheme, setTheme } = useTheme()

    setTheme('light')
    cycleTheme() // light -> dark
    expect(theme.value).toBe('dark')

    cycleTheme() // dark -> system
    expect(theme.value).toBe('system')

    cycleTheme() // system -> light
    expect(theme.value).toBe('light')
  })

  it('cycleTheme 循环三次回到起点', async () => {
    const { useTheme } = await import('@/composables/useTheme')
    const { theme, cycleTheme } = useTheme()

    cycleTheme() // dark -> system
    cycleTheme() // -> light
    cycleTheme() // -> dark
    expect(theme.value).toBe('dark')
  })

  // ===================== 持久化 =====================

  it('主题偏好持久化到 localStorage', async () => {
    const { useTheme } = await import('@/composables/useTheme')
    const { cycleTheme } = useTheme()

    cycleTheme() // dark -> system
    expect(localStorage.getItem(THEME_KEY)).toBe('system')

    cycleTheme() // -> light
    expect(localStorage.getItem(THEME_KEY)).toBe('light')

    cycleTheme() // -> dark
    expect(localStorage.getItem(THEME_KEY)).toBe('dark')
  })

  it('从 localStorage 读取已存储的主题', async () => {
    localStorage.setItem(THEME_KEY, 'system')

    const { useTheme } = await import('@/composables/useTheme')
    const { theme } = useTheme()
    expect(theme.value).toBe('system')
  })

  // ===================== setTheme =====================

  it('setTheme 直接设置主题值并持久化', async () => {
    const { useTheme } = await import('@/composables/useTheme')
    const { theme, setTheme } = useTheme()

    setTheme('light')
    expect(theme.value).toBe('light')
    expect(localStorage.getItem(THEME_KEY)).toBe('light')

    setTheme('dark')
    expect(theme.value).toBe('dark')
    expect(localStorage.getItem(THEME_KEY)).toBe('dark')

    setTheme('system')
    expect(theme.value).toBe('system')
    expect(localStorage.getItem(THEME_KEY)).toBe('system')
  })

  // ===================== 系统主题检测 =====================

  it('系统偏好 light 时 system 解析为 light', async () => {
    matchMediaMock.mockReturnValue({
      matches: false, // prefers-color-scheme: light
      media: '(prefers-color-scheme: dark)',
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })

    const { useTheme } = await import('@/composables/useTheme')
    const { setTheme } = useTheme()
    setTheme('system')

    expect(document.documentElement.getAttribute('data-theme')).toBe('light')
  })

  it('系统偏好 dark 时 system 解析为 dark', async () => {
    matchMediaMock.mockReturnValue({
      matches: true, // prefers-color-scheme: dark
      media: '(prefers-color-scheme: dark)',
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })

    const { useTheme } = await import('@/composables/useTheme')
    const { setTheme } = useTheme()
    setTheme('system')

    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })
})
