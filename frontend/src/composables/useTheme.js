import { ref, watch, onMounted } from "vue"

const THEME_KEY = "fz-theme"

const theme = ref(localStorage.getItem(THEME_KEY) || "system")

function applyTheme() {
  const effective = theme.value === "system"
    ? (window.matchMedia("(prefers-color-scheme: dark)").matches ? "dark" : "light")
    : theme.value
  document.documentElement.setAttribute("data-theme", effective)
}

export function useTheme() {
  const cycleTheme = () => {
    const order = ["light", "dark", "system"]
    const idx = order.indexOf(theme.value)
    theme.value = order[(idx + 1) % order.length]
    localStorage.setItem(THEME_KEY, theme.value)
    applyTheme()
  }
  
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
    setTheme
  }
}
