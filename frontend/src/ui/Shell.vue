<template>
  <Sprite />
  <a class="skip-link" href="#main">跳到主要内容</a>
  <div class="shell">
    <Topbar />
    <Sidebar />
    <main id="main" class="main" tabindex="-1">
      <div class="main-inner">
        <router-view />
      </div>
    </main>
  </div>
  <div class="nav-scrim" aria-hidden="true" @click="mobileNavOpen = false"></div>
  <Palette />
  <NotifDrawer />
  <Toasts />
  <ConfirmHost />
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import './styles/record.css'
import './styles/shell-fix.css'
import './styles/typography.css'
import './styles/form.css'
import './styles/consistency.css'
import Sprite from './components/Sprite.vue'
import Topbar from './components/Topbar.vue'
import Sidebar from './components/Sidebar.vue'
import Palette from './components/Palette.vue'
import NotifDrawer from './components/NotifDrawer.vue'
import Toasts from './components/Toasts.vue'
import ConfirmHost from './components/ConfirmHost.vue'
import { useUi } from './composables/ui'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const { theme, sidebarCollapsed, mobileNavOpen, paletteOpen, notifOpen, setCourseContext } = useUi()
const auth = useAuthStore()

const BODY_CLASSES = ['is-collapsed', 'nav-open', 'course-mode', 'overlay-open']

function syncChrome() {
  const body = document.body
  const html = document.documentElement
  const courseMode = Boolean(route.meta.courseSpace)
  html.dataset.ui = '1'
  html.dataset.uiTheme = theme.value
  body.dataset.app = 'ui'
  body.dataset.style = 'record'
  body.dataset.theme = theme.value
  body.dataset.context = courseMode ? 'course' : 'global'
  body.dataset.role = String(auth.effectiveRole || auth.userRole || '').toLowerCase()
  body.classList.toggle('is-collapsed', sidebarCollapsed.value)
  body.classList.toggle('nav-open', mobileNavOpen.value)
  body.classList.toggle('course-mode', courseMode)
  body.classList.toggle('overlay-open', paletteOpen.value || notifOpen.value)
}

function cleanupChrome() {
  const body = document.body
  const html = document.documentElement
  delete html.dataset.ui
  delete html.dataset.uiTheme
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
    if (!route.meta.courseSpace) setCourseContext(null)
    window.scrollTo({ top: 0 })
    syncChrome()
  },
)
</script>
