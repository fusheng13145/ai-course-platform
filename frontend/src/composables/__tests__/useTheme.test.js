import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'

// Mock chartTheme utility
const mockEmitThemeChange = vi.fn()
vi.mock('@/utils/chartTheme', () => ({
  emitThemeChange: mockEmitThemeChange,
}))

// Import after mocks are set up
const { useTheme } = await import('@/composables/useTheme')

// jsdom 提供 document.documentElement；matchMedia 需模拟
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

describe('useTheme', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.clearAllMocks()
    // 恢复默认：系统主题为 light
    matchMediaMock.mockReturnValue({
      matches: false,
      media: '(prefers-color-scheme: dark)',
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })
    document.documentElement.setAttribute('data-theme', 'light')
  })

  afterEach(() => {
    vi.clearAllMocks()
  })

  // ===================== 默认主题 =====================

  it('returns default theme when no preference is stored', () => {
    const { theme } = useTheme()
    expect(theme.value).toBe('dark')
  })

  it('applies default theme on mount', () => {
    useTheme()
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })

  // ===================== 主题切换 =====================

  it('can toggle theme (light -> dark -> system)', () => {
    const { theme, cycleTheme } = useTheme()

    cycleTheme() // dark -> light
    expect(theme.value).toBe('light')

    cycleTheme() // light -> dark
    expect(theme.value).toBe('dark')

    cycleTheme() // dark -> system
    expect(theme.value).toBe('system')
  })

  it('cycleTheme wraps around correctly', () => {
    const { theme, cycleTheme } = useTheme()

    // 从 dark 循环三次应回到 dark
    cycleTheme() // -> light
    cycleTheme() // -> system
    cycleTheme() // -> dark
    expect(theme.value).toBe('dark')
  })

  // ===================== 持久化主题偏好 =====================

  it('persists theme preference to localStorage', () => {
    const { cycleTheme } = useTheme()

    cycleTheme() // dark -> light
    expect(localStorage.getItem(THEME_KEY)).toBe('light')

    cycleTheme() // light -> dark
    expect(localStorage.getItem(THEME_KEY)).toBe('dark')

    cycleTheme() // dark -> system
    expect(localStorage.getItem(THEME_KEY)).toBe('system')
  })

  it('reads stored theme from localStorage', () => {
    localStorage.setItem(THEME_KEY, 'system')
    const { theme } = useTheme()
    expect(theme.value).toBe('system')
  })

  // ===================== setTheme =====================

  it('setTheme directly sets the theme value', () => {
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

  it('resolves system theme to light when system prefers light', () => {
    matchMediaMock.mockReturnValue({
      matches: false, // prefers-color-scheme: light
      media: '(prefers-color-scheme: dark)',
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })

    const { setTheme } = useTheme()
    setTheme('system')

    expect(document.documentElement.getAttribute('data-theme')).toBe('light')
  })

  it('resolves system theme to dark when system prefers dark', () => {
    matchMediaMock.mockReturnValue({
      matches: true, // prefers-color-scheme: dark
      media: '(prefers-color-scheme: dark)',
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
    })

    const { setTheme } = useTheme()
    setTheme('system')

    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })

  // ===================== DOM 属性 =====================

  it('sets data-theme attribute on document', () => {
    const { setTheme } = useTheme()

    setTheme('light')
    expect(document.documentElement.getAttribute('data-theme')).toBe('light')

    setTheme('dark')
    expect(document.documentElement.getAttribute('data-theme')).toBe('dark')
  })

  // ===================== emitThemeChange 回调 =====================

  it('calls emitThemeChange when theme changes', () => {
    const { cycleTheme } = useTheme()

    cycleTheme()
    expect(mockEmitThemeChange).toHaveBeenCalled()
  })

  it('calls emitThemeChange via setTheme', () => {
    const { setTheme } = useTheme()

    setTheme('light')
    expect(mockEmitThemeChange).toHaveBeenCalled()
  })
})
