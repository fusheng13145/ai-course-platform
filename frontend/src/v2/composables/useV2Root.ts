import { onBeforeUnmount, onMounted, watch } from 'vue'
import type { Ref } from 'vue'

const BODY_CLASSES = ['is-collapsed', 'nav-open', 'course-mode', 'overlay-open']

/**
 * 认证页等 v2 顶层页面用它把主题属性挂到 body/html；
 * 与 V2Shell 的 syncChrome 共用同一组属性名，离开时一起清理。
 */
export function useV2Root(theme: Ref<'light' | 'dark'>, context = 'auth') {
  function apply() {
    const html = document.documentElement
    const body = document.body
    html.dataset.v2 = '1'
    html.dataset.v2Theme = theme.value
    body.dataset.app = 'v2'
    body.dataset.style = 'record'
    body.dataset.theme = theme.value
    body.dataset.context = context
  }

  function clear() {
    const html = document.documentElement
    const body = document.body
    delete html.dataset.v2
    delete html.dataset.v2Theme
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
