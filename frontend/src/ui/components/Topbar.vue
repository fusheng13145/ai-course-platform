<template>
  <header class="topbar">
    <button
      class="topbar-brand brand"
      aria-label="云枢 AI 助教：返回首页"
      @click="go(ROLE_HOME[roleValue])"
    >
      <span class="brand-mark">枢</span>
      <span class="brand-text">
        <b>云枢 AI 助教</b>
      </span>
    </button>
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
      <button class="nav-toggle" aria-label="打开导航" @click="mobileNavOpen = true">
        <Icon name="menu" />
      </button>
      <div class="user-menu">
        <button
          class="user-chip"
          aria-haspopup="menu"
          :aria-expanded="userMenuOpen ? 'true' : 'false'"
          :aria-label="`账号菜单：${avatar} ${displayName} ${roleLabel}`"
          @click="userMenuOpen = !userMenuOpen"
          @keydown.esc="userMenuOpen = false"
        >
          <span class="user-avatar">{{ avatar }}</span>
          <span class="user-text">
            <b>{{ displayName }}</b>
            <small>{{ roleLabel }}</small>
          </span>
          <Icon class="user-caret" name="chevron" />
        </button>
        <!-- 透明遮罩：点面板外任意处收起菜单；面板自身 z 更高不受影响 -->
        <div
          v-if="userMenuOpen"
          class="user-menu-scrim"
          aria-hidden="true"
          @click="userMenuOpen = false"
        ></div>
        <div
          v-if="userMenuOpen"
          class="user-dropdown"
          role="menu"
          :aria-label="`${displayName} 的账号菜单`"
          @keydown.esc="userMenuOpen = false"
        >
          <button class="user-menu-item" role="menuitem" @click="goProfile">
            <Icon name="user" />
            <span>个人中心</span>
          </button>
          <div class="user-menu-sep" role="separator"></div>
          <button class="user-menu-item user-menu-item--danger" role="menuitem" @click="onLogout">
            <Icon name="logout" />
            <span>退出登录</span>
          </button>
        </div>
      </div>
    </div>
  </header>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Icon from './Icon.vue'
import { useUi } from '../composables/ui'
import { ROLE_HOME, type Role } from '../nav'
import { clearCourses } from '../data/api'

const route = useRoute()
const router = useRouter()
const {
  auth,
  role,
  roleLabel,
  displayName,
  avatar,
  theme,
  toggleTheme,
  mobileNavOpen,
  paletteOpen,
  notifOpen,
  userMenuOpen,
  unread,
  courseContext,
  toast,
} = useUi()

const roleValue = computed(() => role.value as Role)
const profilePath = computed(() => `/${role.value}/profile`)
const aiPath = computed(() => `/${role.value}/assistant`)

// AI 助手入口仍依赖课程上下文（课程内直达课程 AI 页签）
const isCourse = computed(() => Boolean(route.meta.courseSpace))
const courseId = computed(() => Number(route.params.id) || courseContext.id || 0)

function go(path: string) {
  router.push(path)
}

function goProfile() {
  userMenuOpen.value = false
  router.push(profilePath.value)
}

// 与原侧栏退出按钮同口径：清课程缓存 → 登出 → 回登录页
async function onLogout() {
  userMenuOpen.value = false
  clearCourses()
  await auth.logout()
  router.push('/login')
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
