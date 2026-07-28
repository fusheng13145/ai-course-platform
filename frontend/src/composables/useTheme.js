import { ref, watch, onMounted } from 'vue'
import { emitThemeChange } from '@/utils/chartTheme'

const THEME_KEY = 'fz-theme'

const theme = ref(localStorage.getItem(THEME_KEY) || 'dark')

function applyTheme() {
  const effective =
    theme.value === 'system'
      ? window.matchMedia('(prefers-color-scheme: dark)').matches
        ? 'dark'
        : 'light'
      : theme.value
  document.documentElement.setAttribute('data-theme', effective)
  emitThemeChange()
}

export function useTheme() {
  /**
   * 循环切换主题
   * 按 light → dark → system 顺序循环切换，并持久化到 localStorage
   */
  const cycleTheme = () => {
    const order = ['light', 'dark', 'system']
    const idx = order.indexOf(theme.value)
    theme.value = order[(idx + 1) % order.length]
    localStorage.setItem(THEME_KEY, theme.value)
    applyTheme()
  }

  /**
   * 设置指定主题
   * @param {string} val - 主题值：'light' | 'dark' | 'system'
   */
  const setTheme = (val) => {
    theme.value = val
    localStorage.setItem(THEME_KEY, val)
    applyTheme()
  }

  onMounted(() => {
    applyTheme()
  })

  watch(theme, applyTheme)

  return {
    theme,
    cycleTheme,
    setTheme,
  }
}
