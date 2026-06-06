import { ref, watch } from 'vue'

const theme = ref(localStorage.getItem('theme') || 'system')

function applyTheme(val) {
  if (val === 'system') {
    const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
    document.documentElement.setAttribute('data-theme', prefersDark ? 'dark' : 'light')
  } else {
    document.documentElement.setAttribute('data-theme', val)
  }
  localStorage.setItem('theme', val)
}

watch(theme, applyTheme, { immediate: true })

export function useTheme() {
  function cycleTheme() {
    const order = ['light', 'dark', 'system']
    const idx = order.indexOf(theme.value)
    theme.value = order[(idx + 1) % order.length]
  }
  function setTheme(val) { theme.value = val }
  return { theme, cycleTheme, setTheme }
}