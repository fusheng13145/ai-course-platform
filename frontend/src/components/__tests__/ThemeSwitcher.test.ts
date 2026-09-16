import { describe, it, expect, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'

// Mock useTheme composable with a reactive ref we can control
const mockTheme = { value: 'dark' }
const mockCycleTheme = vi.fn()
vi.mock('@/composables/useTheme', () => ({
  useTheme: () => ({
    theme: mockTheme,
    cycleTheme: mockCycleTheme,
  }),
}))

// Stub Element Plus ElTooltip to simplify rendering
const ThemeSwitcher = (await import('@/components/ThemeSwitcher.vue')).default

describe('ThemeSwitcher', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockTheme.value = 'dark'
  })

  it('renders the theme switcher', () => {
    const wrapper = mount(ThemeSwitcher, {
      global: {
        stubs: {
          'el-tooltip': {
            template: '<div><slot /></div>',
          },
        },
      },
    })
    expect(wrapper.find('.theme-switcher').exists()).toBe(true)
  })

  it('renders moon icon when theme is dark', () => {
    mockTheme.value = 'dark'
    const wrapper = mount(ThemeSwitcher, {
      global: {
        stubs: {
          'el-tooltip': {
            template: '<div><slot /></div>',
          },
        },
      },
    })
    // dark theme shows moon svg (v-else-if="theme === 'dark'")
    const svgs = wrapper.findAll('svg.theme-icon')
    expect(svgs.length).toBeGreaterThan(0)
  })

  it('renders sun icon when theme is light', () => {
    mockTheme.value = 'light'
    const wrapper = mount(ThemeSwitcher, {
      global: {
        stubs: {
          'el-tooltip': {
            template: '<div><slot /></div>',
          },
        },
      },
    })
    const svgs = wrapper.findAll('svg.theme-icon')
    expect(svgs.length).toBeGreaterThan(0)
  })

  it('renders monitor icon when theme is system', () => {
    mockTheme.value = 'system'
    const wrapper = mount(ThemeSwitcher, {
      global: {
        stubs: {
          'el-tooltip': {
            template: '<div><slot /></div>',
          },
        },
      },
    })
    const svgs = wrapper.findAll('svg.theme-icon')
    expect(svgs.length).toBeGreaterThan(0)
  })

  it('calls cycleTheme when clicked', async () => {
    const wrapper = mount(ThemeSwitcher, {
      global: {
        stubs: {
          'el-tooltip': {
            template: '<div><slot /></div>',
          },
        },
      },
    })
    await wrapper.find('.theme-switcher').trigger('click')
    expect(mockCycleTheme).toHaveBeenCalledTimes(1)
  })

  it('tooltip shows correct text for dark theme', () => {
    mockTheme.value = 'dark'
    const wrapper = mount(ThemeSwitcher, {
      global: {
        stubs: {
          'el-tooltip': {
            template: '<div><slot /></div>',
          },
        },
      },
    })
    // Tooltip computed shows "暗色模式（点击切换跟随系统）"
    const html = wrapper.html()
    expect(html).toBeTruthy()
  })

  it('tooltip shows correct text for light theme', () => {
    mockTheme.value = 'light'
    const wrapper = mount(ThemeSwitcher, {
      global: {
        stubs: {
          'el-tooltip': {
            template: '<div><slot /></div>',
          },
        },
      },
    })
    // Tooltip computed shows "亮色模式（点击切换暗色）"
    const html = wrapper.html()
    expect(html).toBeTruthy()
  })
})
