import { onBeforeUnmount, onMounted, watch } from 'vue'
import type { Ref } from 'vue'

const BODY_CLASSES = ['is-collapsed', 'nav-open', 'course-mode', 'overlay-open']

/**
 * 认证页等 顶层页面用它把主题属性挂到 body/html；
 * 与 Shell 的 syncChrome 共用同一组属性名，离开时一起清理。
 */
export function useRoot(theme: Ref<'light' | 'dark'>, context = 'auth') {
  function apply() {
    const html = document.documentElement
    const body = document.body
    html.dataset.ui = '1'
    html.dataset.uiTheme = theme.value
    body.dataset.app = 'ui'
    body.dataset.style = 'record'
    body.dataset.theme = theme.value
    body.dataset.context = context
  }

  function clear() {
    const html = document.documentElement
    const body = document.body
    delete html.dataset.ui
    delete html.dataset.uiTheme
    delete body.dataset.app
    delete body.dataset.style
    delete body.dataset.theme
    delete body.dataset.context
    BODY_CLASSES.forEach((name) => body.classList.remove(name))
  }

  onMounted(apply)
  watch(theme, apply)
  onBeforeUnmount(clear)
}
