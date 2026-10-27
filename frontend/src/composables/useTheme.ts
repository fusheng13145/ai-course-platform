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

/**
 * 把**已存偏好**应用到 `<html data-theme>` 上：供入口在 `app.mount()` 之前调一次。
 *
 * 【为什么需要它（缺陷 D-1）】`applyTheme()` 原先只在 `useTheme()` 的 `onMounted` 里跑，
 * 而全仓调用 `useTheme()` 的只有 `ThemeSwitcher` 与 `Profile` 两个组件；`/403`、`/404`、
 * `/503` 三个错误页既无 AppShell 也无 ThemeSwitcher，**从不调用它** ⇒ `<html>` 上永远
 * 没有 `data-theme`，`styles/theme.css` 的 `[data-theme='dark']` 整块不生效，暗色用户在这
 * 三页上看到的是 `:root` 的浅色令牌。症状在视觉基线面表现为 `/404` 的明暗两张**逐像素
 * 相同**，被 `assertComparableRender` 判红（历史缺陷 D-1，修复见 commit `ea48fe1`）。
 *
 * 【为什么不复制一份解析逻辑】这里只把 `applyTheme()` 摆到入口够得着的位置：
 * "偏好 → 生效值"（含 `system` 走 `matchMedia`）的解析全仓只有 `applyTheme()` 一处，
 * `useTheme()` 的 `onMounted` 与 `watch` 照旧调它，两条路径是同一份实现。
 *
 * 【幂等】重复调用只是再写一次同一个属性值；`useTheme()` 挂载时那次 `applyTheme()`
 * 不受影响（它本来就要处理"挂载后用户再切主题"这条路径）。
 */
export function applyStoredTheme(): void {
  applyTheme()
}

/**
 * 组合式函数 useTheme：封装可复用的响应式状态与逻辑
 */
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
