// 图表主题工具 - 高级感 (Premium)
// ECharts 不读取 CSS 变量，故按主题返回协调的系列配色。
// 中性基底之上使用克制、协调的多彩系列（indigo/blue/cyan/violet/teal/amber/rose），
// 既高级又不花哨。

function resolveTheme() {
  const t = document.documentElement.getAttribute('data-theme')
  if (t === 'dark') return 'dark'
  if (t === 'light') return 'light'
  return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
}

// 图表文字 / 坐标轴 / 提示框（随主题切换）
export function getChartTheme() {
  const dark = resolveTheme() === 'dark'
  return {
    text: dark ? '#E8EDF6' : '#0F172A',
    subText: dark ? '#A9B4C6' : '#64748B',
    axis: dark ? 'rgba(255,255,255,0.10)' : '#E2E8F0',
    split: dark ? 'rgba(255,255,255,0.06)' : 'rgba(15,23,42,0.05)',
    tooltipBg: dark ? '#1B212E' : '#FFFFFF',
    tooltipBorder: dark ? 'rgba(255,255,255,0.12)' : '#E2E8F0',
    tooltipText: dark ? '#E8EDF6' : '#0F172A',
    sliceBorder: dark ? '#141922' : '#FFFFFF', // 饼图描边 = 卡片底色
  }
}

// 协调多彩系列色板（深浅主题各一套，饱和度克制、明度分层）
const SERIES_LIGHT = [
  '#3B6EF6',
  '#6366F1',
  '#06B6D4',
  '#8B5CF6',
  '#10B981',
  '#F59E0B',
  '#F43F5E',
  '#64748B',
]
const SERIES_DARK = [
  '#6B8EF8',
  '#8B9CFB',
  '#34D0E8',
  '#A78BFA',
  '#34D399',
  '#FBBF24',
  '#FB7185',
  '#94A3B8',
]
export function getMonoSeries() {
  return resolveTheme() === 'dark' ? SERIES_DARK : SERIES_LIGHT
}

// 语义色（协调的蓝/青/琥珀/红/紫系）
export function getMonoStatus() {
  const d = resolveTheme() === 'dark'
  return {
    EASY: d ? '#34D399' : '#10B981',
    MEDIUM: d ? '#6B8EF8' : '#3B6EF6',
    HARD: d ? '#FB7185' : '#F43F5E',
    SUCCESS: d ? '#34D399' : '#10B981',
    WARNING: d ? '#FBBF24' : '#F59E0B',
    DANGER: d ? '#FB7185' : '#F43F5E',
    INFO: d ? '#34D0E8' : '#06B6D4',
  }
}

// 按分值返回协调色（越高越"好"——青绿；越低越"警"——玫红）
export function monoScore(v) {
  const d = resolveTheme() === 'dark'
  if (v >= 90) return d ? '#34D399' : '#10B981'
  if (v >= 80) return d ? '#6B8EF8' : '#3B6EF6'
  if (v >= 70) return d ? '#8B9CFB' : '#6366F1'
  if (v >= 60) return d ? '#FBBF24' : '#F59E0B'
  return d ? '#FB7185' : '#F43F5E'
}

// 主题变更事件
export const THEME_CHANGE_EVENT = 'ac-theme-change'
const listeners = []
export function onThemeChange(cb) {
  if (!listeners.includes(cb)) listeners.push(cb)
}
export function emitThemeChange() {
  window.dispatchEvent(new CustomEvent(THEME_CHANGE_EVENT))
  listeners.forEach((cb) => cb())
}
