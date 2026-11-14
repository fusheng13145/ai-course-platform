<template>
  <header class="topbar">
    <button
      class="icon-btn sidebar-toggle"
      :aria-label="sidebarCollapsed ? '展开导航' : '收起导航'"
      :aria-expanded="sidebarCollapsed ? 'false' : 'true'"
      @click="sidebarCollapsed = !sidebarCollapsed"
    >
      <Icon name="chevron" />
    </button>
    <div class="topbar-context">
      <nav class="breadcrumbs" aria-label="面包屑">
        <template v-for="(crumb, index) in crumbs" :key="index">
          <button v-if="crumb.path" class="crumb" @click="go(crumb.path)">{{ crumb.label }}</button>
          <span v-else class="crumb">{{ crumb.label }}</span>
          <span v-if="index < crumbs.length - 1" class="crumb-sep">/</span>
        </template>
      </nav>
    </div>
    <div class="topbar-actions">
      <button
        class="search-trigger"
        aria-label="全局搜索：搜索页面、课程、教学班 Ctrl K"
        @click="paletteOpen = true"
      >
        <Icon name="search" />
        <span class="search-label">搜索页面、课程、教学班</span>
        <kbd>Ctrl K</kbd>
      </button>
      <button
        class="icon-btn theme-toggle"
        :aria-label="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
        :aria-pressed="theme === 'dark'"
        :title="theme === 'dark' ? '切换到浅色模式' : '切换到深色模式'"
        @click="toggleTheme"
      >
        <Icon :name="theme === 'dark' ? 'sun' : 'moon'" />
      </button>
      <button
        v-if="role === 'teacher'"
        class="icon-btn ai-trigger"
        aria-label="AI 助手"
        @click="openAssistant"
      >
        <Icon name="sparkle" />
      </button>
      <button class="icon-btn" aria-label="通知" @click="notifOpen = true">
        <Icon name="bell" />
        <span v-if="unread > 0" class="notif-dot"></span>
      </button>
      <button
        class="user-chip"
        :aria-label="`打开个人中心：${avatar} ${displayName} ${roleLabel}`"
        @click="go(profilePath)"
      >
        <span class="user-avatar">{{ avatar }}</span>
        <span class="user-text">
          <b>{{ displayName }}</b>
          <small>{{ roleLabel }}</small>
        </span>
      </button>
      <button class="nav-toggle" aria-label="打开导航" @click="mobileNavOpen = true">
        <Icon name="menu" />
      </button>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from './Icon.vue'
import { useUi } from '../composables/ui'
import { ROLE_HOME, courseTabs, pageDef, type Role } from '../nav'

const route = useRoute()
const router = useRouter()
const {
  role,
  roleLabel,
  displayName,
  avatar,
  theme,
  toggleTheme,
  sidebarCollapsed,
  mobileNavOpen,
  paletteOpen,
  notifOpen,
  unread,
  courseContext,
  toast,
} = useUi()

const roleValue = computed(() => role.value as Role)
const profilePath = computed(() => `/${role.value}/profile`)
const aiPath = computed(() => `/${role.value}/assistant`)

const isCourse = computed(() => Boolean(route.meta.courseSpace))
const courseId = computed(() => Number(route.params.id) || courseContext.id || 0)
const tabs = computed(() => courseTabs(roleValue.value))
const tabId = computed(() => String(route.params.tab || 'overview'))
const tabLabel = computed(() => tabs.value.find((tab) => tab.id === tabId.value)?.label || '')
const courseLabel = computed(
  () => courseContext.name || (courseId.value ? `课程 #${courseId.value}` : '课程'),
)

const crumbs = computed<Array<{ label: string; path?: string }>>(() => {
  if (isCourse.value) {
    const base = `/${role.value}/courses`
    return [
      { label: role.value === 'admin' ? '课程目录' : '我的课程', path: base },
      { label: courseLabel.value, path: `${base}/${courseId.value}/overview` },
      ...(tabId.value === 'overview' ? [] : [{ label: tabLabel.value }]),
    ]
  }
  const page = String(route.meta.pageKey || '')
  const def = pageDef(roleValue.value, page)
  return [{ label: def.title }]
})

function go(path: string) {
  router.push(path)
}

function openAssistant() {
  if (role.value === 'teacher') {
    if (isCourse.value && courseId.value) {
      router.push(`/teacher/courses/${courseId.value}/ai`)
      return
    }
    toast('AI 教学助手在课程上下文中打开', 'info')
    router.push('/teacher/courses')
    return
  }
  if (isCourse.value && courseId.value) {
    router.push(`/${role.value}/courses/${courseId.value}/ai`)
    return
  }
  router.push(aiPath.value || ROLE_HOME[roleValue.value])
}
</script>
