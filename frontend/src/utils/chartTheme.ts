// 图表主题工具 · 云枢教学平台
// ECharts 不读取 CSS 变量，故按主题返回协调的系列配色。
// 色板对齐校园赭红设计体系。

function resolveTheme() {
  const t = document.documentElement.getAttribute('data-theme')
  if (t === 'dark') return 'dark'
  if (t === 'light') return 'light'
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

/**
 * 导出函数 getChartTheme：获取/查询数据
 */
export function getChartTheme() {
  const dark = resolveTheme() === 'dark'
  return {
    text: dark ? '#eef1f6' : '#1f2733',
    subText: dark ? '#b7bec9' : '#8b94a3',
    axis: dark ? 'rgba(255,255,255,0.10)' : '#e3e6ec',
    split: dark ? 'rgba(255,255,255,0.06)' : 'rgba(24,32,48,0.05)',
    tooltipBg: dark ? '#1c2330' : '#ffffff',
    tooltipBorder: dark ? 'rgba(255,255,255,0.12)' : '#e3e6ec',
    tooltipText: dark ? '#eef1f6' : '#1f2733',
    sliceBorder: dark ? '#0f1419' : '#ffffff',
  }
}

const PALETTE_LIGHT = [
  '#c13b30',
  '#2563a8',
  '#2e7d4f',
  '#b57908',
  '#6d4fa1',
  '#0f766e',
  '#c2602e',
  '#4d6a8a',
]
const PALETTE_DARK = [
  '#d45b50',
  '#4d8ad6',
  '#4a9e6e',
  '#d59a2a',
  '#8b6cc0',
  '#2fb5a8',
  '#d97a4c',
  '#6d8a9a',
]
/**
 * 导出函数 getMonoSeries：获取/查询数据
 */
export function getMonoSeries() {
  return resolveTheme() === 'dark' ? PALETTE_DARK : PALETTE_LIGHT
}

/**
 * 导出函数 getMonoStatus：获取/查询数据
 */
export function getMonoStatus() {
  const d = resolveTheme() === 'dark'
  return {
    EASY: d ? '#4a9e6e' : '#2e7d4f',
    MEDIUM: d ? '#4d8ad6' : '#2563a8',
    HARD: d ? '#d45b50' : '#c13b30',
    SUCCESS: d ? '#4a9e6e' : '#2e7d4f',
    WARNING: d ? '#d59a2a' : '#b57908',
    DANGER: d ? '#d45b50' : '#c13b30',
    INFO: d ? '#4d8ad6' : '#2563a8',
  }
}

/**
 * 导出函数 monoScore：业务逻辑处理
 * @param {*} v - 参数
 * @returns {*} - 返回值
 */
export function monoScore(v) {
  const d = resolveTheme() === 'dark'
  if (v >= 90) return d ? '#4a9e6e' : '#2e7d4f'
  if (v >= 80) return d ? '#4d8ad6' : '#2563a8'
  if (v >= 70) return d ? '#8b6cc0' : '#6d4fa1'
  if (v >= 60) return d ? '#d59a2a' : '#b57908'
  return d ? '#d45b50' : '#c13b30'
}

export const THEME_CHANGE_EVENT = 'ac-theme-change'
const listeners = []
/**
 * 导出函数 onThemeChange：业务逻辑处理
 * @param {*} cb - 参数
 * @returns {*} - 返回值
 */
export function onThemeChange(cb) {
  if (!listeners.includes(cb)) listeners.push(cb)
}
/**
 * 导出函数 emitThemeChange：业务逻辑处理
 */
export function emitThemeChange() {
  window.dispatchEvent(new CustomEvent(THEME_CHANGE_EVENT))
  listeners.forEach((cb) => cb())
}
