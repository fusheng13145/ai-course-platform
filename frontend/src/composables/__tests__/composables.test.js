import { describe, it, expect, vi, beforeEach } from 'vitest'
import { nextTick } from 'vue'

vi.mock('@/utils/logger', () => ({ default: { log: vi.fn(), warn: vi.fn(), error: vi.fn() } }))

describe('useTheme composable', () => {
  beforeEach(() => {
    localStorage.clear()
    document.documentElement.className = ''
    vi.restoreAllMocks()
  })

  it('默认主题为 light', async () => {
    const { useTheme } = await import('@/composables/useTheme')
    const { theme, isDark, toggleTheme } = useTheme()

    expect(theme.value).toBe('light')
    expect(isDark.value).toBe(false)
  })

  it('toggleTheme 切换主题', async () => {
    const { useTheme } = await import('@/composables/useTheme')
    const { theme, isDark, toggleTheme } = useTheme()

    toggleTheme()
    await nextTick()
    expect(theme.value).toBe('dark')
    expect(isDark.value).toBe(true)
    expect(localStorage.getItem('theme')).toBe('dark')

    toggleTheme()
    await nextTick()
    expect(theme.value).toBe('light')
    expect(isDark.value).toBe(false)
  })

  it('从 localStorage 恢复主题', async () => {
    localStorage.setItem('theme', 'dark')

    const { useTheme } = await import('@/composables/useTheme')
    const { theme, isDark } = useTheme()

    expect(theme.value).toBe('dark')
    expect(isDark.value).toBe(true)
  })
})

describe('ErrorBoundary 组件行为', () => {
  it('渲染默认插槽内容', async () => {
    const { mount } = await import('@vue/test-utils')
    const ErrorBoundary = (await import('@/views/common/ErrorBoundary.vue')).default

    const wrapper = mount(ErrorBoundary, {
      slots: { default: '<div class="content">正常内容</div>' },
    })

    expect(wrapper.find('.content').exists()).toBe(true)
    expect(wrapper.text()).toContain('正常内容')
    wrapper.unmount()
  })
})
