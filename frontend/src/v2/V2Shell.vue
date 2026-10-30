<template>
  <V2Sprite />
  <a class="skip-link" href="#v2-main">跳到主要内容</a>
  <div class="shell">
    <V2Topbar />
    <V2Sidebar />
    <main id="v2-main" class="main" tabindex="-1">
      <div class="main-inner">
        <router-view />
      </div>
    </main>
  </div>
  <div class="nav-scrim" aria-hidden="true" @click="mobileNavOpen = false"></div>
  <V2Palette />
  <V2NotifDrawer />
  <V2Toasts />
  <V2ConfirmHost />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import './styles/record.css'
import './styles/shell-fix.css'
import './styles/typography.css'
import './styles/form.css'
import V2Sprite from './components/V2Sprite.vue'
import V2Topbar from './components/V2Topbar.vue'
import V2Sidebar from './components/V2Sidebar.vue'
import V2Palette from './components/V2Palette.vue'
import V2NotifDrawer from './components/V2NotifDrawer.vue'
import V2Toasts from './components/V2Toasts.vue'
import V2ConfirmHost from './components/V2ConfirmHost.vue'
import { useV2Ui } from './composables/ui'

const route = useRoute()
const { theme, sidebarCollapsed, mobileNavOpen, paletteOpen, notifOpen, setCourseContext } =
  useV2Ui()

const BODY_CLASSES = ['is-collapsed', 'nav-open', 'course-mode', 'overlay-open']

function syncChrome() {
  const body = document.body
  const html = document.documentElement
  const courseMode = Boolean(route.meta.v2course)
  html.dataset.v2 = '1'
  html.dataset.v2Theme = theme.value
  body.dataset.app = 'v2'
  body.dataset.style = 'record'
  body.dataset.theme = theme.value
  body.dataset.context = courseMode ? 'course' : 'global'
  body.classList.toggle('is-collapsed', sidebarCollapsed.value)
  body.classList.toggle('nav-open', mobileNavOpen.value)
  body.classList.toggle('course-mode', courseMode)
  body.classList.toggle('overlay-open', paletteOpen.value || notifOpen.value)
}

function cleanupChrome() {
  const body = document.body
  const html = document.documentElement
  delete html.dataset.v2
  delete html.dataset.v2Theme
  delete body.dataset.app
  delete body.dataset.style
  delete body.dataset.theme
  delete body.dataset.context
  BODY_CLASSES.forEach((name) => body.classList.remove(name))
}

onMounted(syncChrome)
onBeforeUnmount(cleanupChrome)

watch([theme, sidebarCollapsed, mobileNavOpen, paletteOpen, notifOpen], syncChrome)
watch(
  () => route.fullPath,
  () => {
    mobileNavOpen.value = false
    paletteOpen.value = false
    notifOpen.value = false
    if (!route.meta.v2course) setCourseContext(null)
    window.scrollTo({ top: 0 })
    syncChrome()
  },
)
</script>
