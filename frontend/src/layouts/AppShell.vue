<template>
  <div class="layout-container">
    <!-- 移动端遮罩层 -->
    <div v-show="sidebarOpen" class="sidebar-overlay" @click="toggleSidebar"></div>

    <!-- ============ 侧边栏 ============ -->
    <aside class="sidebar" :class="{ 'is-open': sidebarOpen }">
      <div class="logo">
        <span class="brand-mark">AI</span>
        <span class="logo-text">
          <span class="logo-title">AI 助教系统</span>
          <span class="logo-kicker">{{ roleMeta.brand }}</span>
        </span>
      </div>
      <div class="menu-scroll">
        <el-menu
          :default-active="activeMenu"
          :default-openeds="openedGroups"
          router
          background-color="transparent"
          class="sidebar-menu"
        >
          <template v-for="(item, idx) in menus" :key="item.path || item.group || idx">
            <!-- 可展开分组 -->
            <el-sub-menu v-if="item.children" :index="`group-${idx}`">
              <template #title>
                <el-icon><component :is="item.icon" /></el-icon>
                <span>{{ item.group }}</span>
              </template>
              <el-menu-item
                v-for="child in item.children"
                :key="child.path"
                :index="child.path"
              >
                <el-icon><component :is="child.icon" /></el-icon>
                <span>{{ child.title }}</span>
              </el-menu-item>
            </el-sub-menu>
            <!-- 顶层单项 -->
            <el-menu-item v-else-if="item.path" :index="item.path">
              <el-icon><component :is="item.icon" /></el-icon>
              <span>{{ item.title }}</span>
            </el-menu-item>
          </template>
        </el-menu>
      </div>
    </aside>

    <!-- ============ 主区域 ============ -->
    <div class="main-area">
      <header class="header">
        <div class="header-left">
          <el-button class="menu-toggle" text aria-label="打开导航菜单" @click="toggleSidebar">
            <el-icon :size="20"><Menu /></el-icon>
          </el-button>
          <!-- 面包屑导航 -->
          <el-breadcrumb separator="/" class="breadcrumb">
            <el-breadcrumb-item :to="{ path: defaultHome }">
              <el-icon style="vertical-align: middle"><HomeFilled /></el-icon>
            </el-breadcrumb-item>
            <el-breadcrumb-item
              v-for="(crumb, ci) in breadcrumbs"
              :key="ci"
              :to="ci < breadcrumbs.length - 1 ? crumb.path : undefined"
            >
              {{ crumb.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>

        <div class="header-right">
          <ThemeSwitcher />

          <!-- 通知中心 -->
          <el-popover
            placement="bottom-end"
            :width="360"
            trigger="click"
            @show="loadNotifications"
          >
            <template #reference>
              <el-badge
                :value="unreadCount"
                :hidden="unreadCount === 0"
                :max="99"
                class="notify-badge"
              >
                <el-button text circle aria-label="通知中心">
                  <el-icon :size="19"><Bell /></el-icon>
                </el-button>
              </el-badge>
            </template>
            <div class="notify-panel">
              <div class="notify-head">
                <span class="notify-title">通知</span>
                <el-button
                  text
                  type="primary"
                  size="small"
                  :disabled="unreadCount === 0"
                  @click="handleReadAll"
                  >全部已读</el-button
                >
              </div>
              <div v-loading="notifyLoading" class="notify-list">
                <el-empty
                  v-if="!notifyLoading && notifications.length === 0"
                  description="暂无通知"
                  :image-size="60"
                />
                <div
                  v-for="n in notifications"
                  :key="n.id"
                  class="notify-item"
                  :class="{ unread: !n.isRead }"
                  @click="handleRead(n)"
                >
                  <span v-if="!n.isRead" class="notify-dot"></span>
                  <div class="notify-body">
                    <div class="notify-item-title">{{ n.title }}</div>
                    <div class="notify-item-content">{{ n.content }}</div>
                    <div class="notify-item-time">{{ formatTime(n.createdAt) }}</div>
                  </div>
                </div>
              </div>
              <div class="notify-foot">
                <el-button text type="primary" size="small" @click="goNotificationCenter"
                  >查看全部通知</el-button
                >
              </div>
            </div>
          </el-popover>

          <!-- 用户菜单 -->
          <el-dropdown trigger="click" @command="handleUserCommand">
            <div class="user-entry">
              <span class="user-avatar" :class="`role-${role.toLowerCase()}`">{{
                avatarText
              }}</span>
              <span class="user-name">{{ auth.userName }}</span>
              <el-tag :type="roleMeta.tagType" size="small" effect="light">{{
                roleMeta.label
              }}</el-tag>
              <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>

      <main class="content"><router-view /></main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown, Bell, HomeFilled, Menu, SwitchButton, User } from '@element-plus/icons-vue'
import dayjs from 'dayjs'
import { useAuthStore } from '@/stores/auth'
import { unwrap } from '@/utils/response'
import notificationApi from '@/api/notification'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'
import { ROLE_META } from './menus'

const props = defineProps({
  /** 菜单配置：树形结构，group+children 或 path+icon 叶子节点 */
  menus: { type: Array, required: true },
  /** 角色标识：ADMIN / TEACHER / STUDENT */
  role: { type: String, required: true },
  /** 个人通知中心路由（管理员端与公告管理区分时使用独立路径） */
  notificationPath: { type: String, default: '' },
  /** 个人中心路由 */
  profilePath: { type: String, default: '' },
})

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const roleMeta = computed(() => ROLE_META[props.role] || ROLE_META.STUDENT)
const activeMenu = computed(() => route.path)
const avatarText = computed(() => (auth.userName || 'U').slice(0, 1).toUpperCase())
const defaultHome = computed(() => `/${props.role.toLowerCase()}/dashboard`)

/* ---------- 面包屑 ---------- */
const breadcrumbs = computed(() => {
  const crumbs = []
  const matched = route.matched.filter((r) => r.meta?.title)

  for (const record of matched) {
    const title = record.meta.breadcrumb || record.meta.title
    if (title) {
      crumbs.push({ title, path: record.path || route.path })
    }
  }

  // 如果当前路由在某个菜单分组下，追加分组名作为前置面包屑
  if (crumbs.length <= 1) {
    const group = findMenuGroup(route.path)
    if (group) {
      crumbs.unshift({ title: group.group, path: '' })
    }
  }

  return crumbs
})

function findMenuGroup(path) {
  for (const item of props.menus) {
    if (item.children) {
      for (const child of item.children) {
        if (child.path === path) return item
      }
    }
  }
  return null
}

/* ---------- 侧边栏展开分组 ---------- */
const openedGroups = computed(() => {
  const groups = []
  const currentPath = route.path
  // 展开包含当前激活菜单项的分组
  for (let i = 0; i < props.menus.length; i++) {
    const item = props.menus[i]
    if (item.children) {
      for (const child of item.children) {
        if (currentPath === child.path || currentPath.startsWith(child.path + '/')) {
          groups.push(`group-${i}`)
          break
        }
      }
    }
  }
  return groups
})

/* ---------- 移动端侧边栏 ---------- */
const sidebarOpen = ref(false)
function toggleSidebar() {
  sidebarOpen.value = !sidebarOpen.value
}

/* ---------- 通知中心 ---------- */
const unreadCount = ref(0)
const notifications = ref([])
const notifyLoading = ref(false)
let pollTimer = null

async function refreshUnreadCount() {
  try {
    const res = await notificationApi.getUnreadCount()
    unreadCount.value = Number(unwrap(res)) || 0
  } catch {
    /* 角标失败静默，不打断页面 */
  }
}

async function loadNotifications() {
  notifyLoading.value = true
  try {
    const res = await notificationApi.getNotificationList({ page: 1, size: 5 })
    const data = unwrap(res)
    notifications.value = data?.records || data?.list || []
  } catch {
    notifications.value = []
  } finally {
    notifyLoading.value = false
  }
}

async function handleRead(n) {
  if (!n.isRead) {
    try {
      await notificationApi.markAsRead(n.id)
      n.isRead = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch {
      /* 已读标记失败不阻断跳转 */
    }
  }
  goNotificationCenter()
}

async function handleReadAll() {
  try {
    await notificationApi.markAllAsRead()
    unreadCount.value = 0
    notifications.value.forEach((n) => (n.isRead = true))
    ElMessage.success('已全部标记为已读')
  } catch {
    ElMessage.error('操作失败，请稍后重试')
  }
}

function goNotificationCenter() {
  router.push(props.notificationPath || `/${props.role.toLowerCase()}/notifications`)
}

function formatTime(t) {
  return t ? dayjs(t).format('MM-DD HH:mm') : ''
}

/* ---------- 用户菜单 ---------- */
function handleUserCommand(cmd) {
  if (cmd === 'profile') {
    router.push(props.profilePath || `/${props.role.toLowerCase()}/profile`)
  } else if (cmd === 'logout') {
    auth.logout().then(() => router.push('/login'))
  }
}

onMounted(() => {
  refreshUnreadCount()
  pollTimer = setInterval(refreshUnreadCount, 60 * 1000)
})
onBeforeUnmount(() => clearInterval(pollTimer))
</script>

<style scoped>
.menu-scroll {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

.sidebar-menu {
  border-right: none !important;
}

.breadcrumb {
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.breadcrumb :deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: var(--ac-text-primary);
  font-weight: 600;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex: 1;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.menu-toggle {
  display: none;
}

.notify-badge :deep(.el-badge__content) {
  border: none;
}

.notify-panel {
  display: flex;
  flex-direction: column;
  max-height: 420px;
}

.notify-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--ac-border);
}

.notify-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
}

.notify-list {
  flex: 1;
  overflow-y: auto;
  min-height: 80px;
}

.notify-item {
  display: flex;
  gap: 8px;
  padding: 10px 6px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s ease;
}

.notify-item:hover {
  background: var(--ac-bg-hover);
}

.notify-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--ac-danger);
  margin-top: 7px;
  flex-shrink: 0;
}

.notify-body {
  min-width: 0;
}

.notify-item-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ac-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notify-item-content {
  font-size: 12px;
  color: var(--ac-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.notify-item-time {
  font-size: 11px;
  color: var(--ac-text-muted);
  margin-top: 2px;
}

.notify-foot {
  padding-top: 8px;
  border-top: 1px solid var(--ac-border);
  text-align: center;
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 6px;
  border-radius: 6px;
  outline: none;
  transition: background 0.15s ease;
}

.user-entry:hover {
  background: var(--ac-bg-hover);
}

.user-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-size: 13px;
  font-weight: 600;
  background: var(--ac-primary);
}

.user-avatar.role-admin {
  background: var(--ac-role-admin);
}
.user-avatar.role-teacher {
  background: var(--ac-role-teacher);
}
.user-avatar.role-student {
  background: var(--ac-role-student);
}

.user-name {
  font-size: 13.5px;
  color: var(--ac-text-primary);
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media screen and (max-width: 768px) {
  .menu-toggle {
    display: inline-flex;
  }
  .user-name {
    display: none;
  }
}
</style>
