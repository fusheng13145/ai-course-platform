<template>
  <div class="app">
    <!-- ============ 侧边栏（深色，手风琴二级菜单）============ -->
    <aside class="sidebar" :class="{ mini: sidebarMini }">
      <div class="brand">
        <span class="brand-mark">{{ roleMeta.brandMark }}</span>
        <div>
          <div class="brand-name">{{ roleMeta.brandName }}</div>
          <div class="brand-sub">{{ roleMeta.brandSub }}</div>
        </div>
      </div>

      <nav class="side-nav">
        <template v-for="(sec, si) in navSections" :key="si">
          <div class="nav-section-label">{{ sec.label }}</div>
          <template v-for="item in sec.items" :key="item.key">
            <!-- 一级菜单项 -->
            <div
              class="nav-item"
              :class="{
                active: isActive(item),
                expanded: isExpanded(item.key),
              }"
              @click="handleNavClick(item)"
            >
              <!-- 内部图标常量（受控），无需净化 -->
              <!-- eslint-disable-next-line vue/no-v-html -->
              <span class="nav-ico" v-html="iconHtml(item.icon, 17)"></span>
              <span class="nav-txt">{{ item.text }}</span>
              <span v-if="item.badge != null" class="nav-badge">{{ item.badge }}</span>
              <!-- 内部图标常量（受控），无需净化 -->
              <!-- eslint-disable-next-line vue/no-v-html -->
              <span v-if="item.children" class="nav-arrow" v-html="iconHtml('chevronR', 13)"></span>
            </div>
            <!-- 二级手风琴子菜单 -->
            <div v-if="item.children" ref="subMenuRefs" class="nav-sub">
              <div class="nav-sub-inner">
                <div
                  v-for="child in item.children"
                  :key="child.key"
                  class="nav-sub-item"
                  :class="{ active: isActive(child) }"
                  @click="handleNavChildClick(child, item)"
                >
                  <span class="dot"></span>
                  {{ child.text }}
                </div>
              </div>
            </div>
          </template>
        </template>
      </nav>

      <div class="side-user" @click="showUserMenu">
        <span class="avatar lg" :style="avatarStyle">{{ avatarInitial }}</span>
        <div class="u-meta">
          <div class="u-name">{{ auth.userName }}</div>
          <div class="u-role">{{ roleMeta.label }}</div>
        </div>
      </div>
    </aside>

    <!-- ============ 主区 ============ -->
    <div class="main">
      <header class="topbar">
        <!-- 内部图标常量（受控），无需净化 -->
        <!-- eslint-disable-next-line vue/no-v-html -->
        <button class="t-icon-btn" title="折叠侧边栏" @click="sidebarMini = !sidebarMini" v-html="iconHtml('menu', 17)"></button>

        <div class="crumbs">
          <!-- 内部图标常量（受控），无需净化 -->
          <!-- eslint-disable-next-line vue/no-v-html -->
          <span class="crumb link" @click="goHome"><span v-html="iconHtml('dashboard', 13)"></span> {{ roleMeta.label }}</span>
          <template v-for="(crumb, ci) in breadcrumbs" :key="ci">
            <!-- 内部图标常量（受控），无需净化 -->
            <!-- eslint-disable-next-line vue/no-v-html -->
            <span class="sep" v-html="iconHtml('chevronR', 12)"></span>
            <span class="crumb" :class="{ current: ci === breadcrumbs.length - 1, link: ci < breadcrumbs.length - 1 && crumb.path }" @click="crumb.path ? router.push(crumb.path) : undefined">{{ crumb.title }}</span>
          </template>
        </div>

        <div class="spacer"></div>

        <!-- 通知按钮 -->
        <el-popover placement="bottom-end" :width="340" trigger="click" @show="loadNotifications">
          <template #reference>
            <button class="t-icon-btn" title="通知">
              <!-- 内部图标常量（受控），无需净化 -->
              <!-- eslint-disable-next-line vue/no-v-html -->
              <span v-html="iconHtml('bell', 17)"></span>
              <span v-if="unreadCount" class="n-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
            </button>
          </template>
          <div class="notify-panel">
            <div class="notify-head">
              <span class="notify-title">通知</span>
              <el-button text size="small" :disabled="!unreadCount" @click="handleReadAll">全部已读</el-button>
            </div>
            <div v-loading="notifyLoading" class="notify-list">
              <div v-if="!notifyLoading && !notifications.length" class="empty-state" style="padding:24px">
                <div class="e-title">暂无通知</div>
              </div>
              <div v-for="n in notifications" :key="n.id" class="notify-item" :class="{ unread: !n.isRead }" @click="handleRead(n)">
                <span v-if="!n.isRead" class="notify-dot"></span>
                <div class="notify-body">
                  <div class="notify-item-title">{{ n.title }}</div>
                  <div class="notify-item-content">{{ n.content }}</div>
                  <div class="notify-item-time">{{ formatTime(n.createdAt) }}</div>
                </div>
              </div>
            </div>
            <div class="notify-foot">
              <el-button text size="small" @click="goNotificationCenter">查看全部通知</el-button>
            </div>
          </div>
        </el-popover>

        <!-- 主题切换 -->
        <ThemeSwitcher />

        <!-- 用户头像下拉 -->
        <button class="user-btn" @click="showUserMenu">
          <span class="avatar" :style="avatarStyle">{{ avatarInitial }}</span>
          <span class="user-name-text">{{ auth.userName }}</span>
        </button>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" :key="route.path" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { useAuthStore } from '@/stores/auth'
import { unwrap } from '@/utils/response'
import notificationApi from '@/api/notification'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'
import { ROLE_META, ADMIN_SECTIONS, TEACHER_SECTIONS, STUDENT_SECTIONS } from './menus'

const props = defineProps({
  role: { type: String, required: true },
  notificationPath: { type: String, default: '' },
  profilePath: { type: String, default: '' },
})

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

/* ---------- 角色元数据 ---------- */
const roleMeta = computed(() => ROLE_META[props.role] || ROLE_META.STUDENT)

/* ---------- 导航分区 ---------- */
const SECTIONS_MAP = { ADMIN: ADMIN_SECTIONS, TEACHER: TEACHER_SECTIONS, STUDENT: STUDENT_SECTIONS }
const navSections = computed(() => SECTIONS_MAP[props.role] || STUDENT_SECTIONS)

/* ---------- 图标（内联 SVG）---------- */
const ICONS = {
  dashboard: 'M3 3h8v10H3zM13 3h8v6h-8zM13 11h8v10h-8zM3 15h8v6H3z',
  users: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75',
  user: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
  book: 'M4 19.5A2.5 2.5 0 0 1 6.5 17H20M4 19.5A2.5 2.5 0 0 0 6.5 22H20V2H6.5A2.5 2.5 0 0 0 4 4.5v15z',
  calendar: 'M8 2v4M16 2v4M3 8h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z',
  chart: 'M3 3v18h18M18 17V9M13 17V5M8 17v-3',
  bell: 'M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.94 1.94 0 0 0 3.4 0',
  settings: 'M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6',
  sliders: 'M4 6h10M18 6h2M4 12h4M12 12h8M4 18h13M20 18h0M14 4v4M8 10v4M17 16v4',
  chevronR: 'm9 18 6-6-6-6',
  chevronD: 'm6 9 6 6 6-6',
  arrowL: 'M19 12H5M12 19l-7-7 7-7',
  plus: 'M12 5v14M5 12h14',
  search: 'M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM21 21l-4.35-4.35',
  more: 'M12 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2zM19 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2zM5 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2z',
  file: 'M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2zM14 2v6h6',
  folder: 'M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2z',
  video: 'm22 8-6 4 6 4V8zM14 6H4a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2z',
  play: 'm6 4 14 8-14 8V4z',
  x: 'M18 6 6 18M6 6l12 12',
  check: 'M20 6 9 17l-5-5',
  checks: 'M18 6 7 17l-5-5M22 10 11 21l-2.5-2.5',
  edit: 'M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z',
  trash: 'M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2',
  eye: 'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
  send: 'm22 2-7 20-4-9-9-4zM22 2 11 13',
  filter: 'M22 3H2l8 9.46V19l4 2v-8.54L22 3z',
  download: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3',
  upload: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12',
  clock: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 6v6l4 2',
  star: 'm12 2 3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z',
  warn: 'M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0zM12 9v4M12 17h.01',
  logout: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9',
  menu: 'M4 6h16M4 12h16M4 18h16',
  lock: 'M19 11H5a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7a2 2 0 0 0-2-2zM7 11V7a5 5 0 0 1 10 0v4',
  key: 'm21 2-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0 3 3L22 7l-3-3m-3.5 3.5L19 4',
  layers: 'm12 2 8.5 4.7L12 11.4 3.5 6.7 12 2zM3.5 12l8.5 4.7 8.5-4.7M3.5 17.3l8.5 4.7 8.5-4.7',
  grid: 'M3 3h7v7H3zM14 3h7v7h-7zM14 14h7v7h-7zM3 14h7v7H3z',
  list: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',
  megaphone: 'm3 11 18-5v12L3 13v-2zM11.6 16.8a3 3 0 1 1-5.8-1.6',
  shield: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z',
  monitor: 'M8 21h8M12 17v4M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z',
  clipboard: 'M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2M15 2H9a1 1 0 0 0-1 1v2a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1V3a1 1 0 0 0-1-1z',
  award: 'M12 15a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM8.21 13.89 7 23l5-3 5 3-1.21-9.12',
  message: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z',
  refresh: 'M3 12a9 9 0 0 1 15.36-6.36L21 8M21 3v5h-5M21 12a9 9 0 0 1-15.36 6.36L3 16M3 21v-5h5',
  dot: 'M12 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2z',
  building: 'M6 22V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v18zM2 22h20M9 6h1M14 6h1M9 10h1M14 10h1M9 14h1M14 14h1M10 22v-3h4v3',
  grad: 'm22 9-10-5L2 9l10 5 10-5zM6 11.5V16c0 1.66 2.69 3 6 3s6-1.34 6-3v-4.5M22 9v5',
  pin: 'M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0zM12 13a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
  link: 'M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71',
  archive: 'M21 8v13H3V8M1 3h22v5H1zM10 12h4',
  doc: 'M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2zM14 2v6h6M16 13H8M16 17H8M10 9H8',
  log: 'M21 12a9 9 0 0 1-9 9m9-9a9 9 0 0 0-9-9m9 9H3m9 9a9 9 0 0 1-9-9m9 9c2.21 0 4-4.03 4-9s-1.79-9-4-9m0 18c-2.21 0-4-4.03-4-9s1.79-9 4-9m-9 9a9 9 0 0 1 9-9',
}
function iconHtml(name, size = 16) {
  const d = ICONS[name] || ICONS.dot
  return `<svg viewBox="0 0 24 24" width="${size}" height="${size}" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="${d}"/></svg>`
}

/* ---------- 侧边栏状态 ---------- */
const sidebarMini = ref(false)
const expandedKeys = ref(new Set())

function isActive(item) {
  if (item.key === 'my' && route.path.startsWith(`/${props.role.toLowerCase()}/courses`)) return true
  if (item.key === 'hall' && route.path.startsWith(`/${props.role.toLowerCase()}/courses`)) return true
  return route.path === item.path || (item.path && route.path.startsWith(item.path + '/'))
}

function isExpanded(key) {
  return expandedKeys.value.has(key)
}

function handleNavClick(item) {
  if (item.children) {
    const s = new Set(expandedKeys.value)
    if (s.has(item.key)) s.delete(item.key); else s.add(item.key)
    expandedKeys.value = s
  } else if (item.path) {
    router.push(item.path)
  }
}

function handleNavChildClick(child, parent) {
  const s = new Set(expandedKeys.value)
  s.add(parent.key)
  expandedKeys.value = s
  if (child.path) router.push(child.path)
}

/* ---------- 头像 ---------- */
const avatarInitial = computed(() => (auth.userName || 'U').slice(-2))
const avatarStyle = computed(() => {
  const idx = ([...(auth.userName || 'U')] || []).reduce((s, c) => s + c.charCodeAt(0), 0) % 6
  const gradients = [
    'linear-gradient(135deg,#c13b30,#8f2a21)', 'linear-gradient(135deg,#2563a8,#1a4a80)',
    'linear-gradient(135deg,#2e7d4f,#1f5c39)', 'linear-gradient(135deg,#b57908,#8a5d06)',
    'linear-gradient(135deg,#6d4fa1,#4f3877)', 'linear-gradient(135deg,#0f766e,#0a544f)',
  ]
  return { background: gradients[idx] }
})

/* ---------- 面包屑 ---------- */
const breadcrumbs = computed(() => {
  const matched = route.matched.filter(r => r.meta?.title)
  return matched.map(r => ({ title: r.meta.breadcrumb || r.meta.title, path: r.path || '' }))
})

/* ---------- 用户菜单 ---------- */
function showUserMenu() {
  const items = [
    { label: '个人中心', icon: 'user', action: () => router.push(props.profilePath || `/${props.role.toLowerCase()}/profile`) },
    { label: '退出登录', icon: 'logout', danger: true, action: () => auth.logout().then(() => router.push('/login')) },
  ]
  const menu = document.createElement('div')
  menu.className = 'pop-menu'
  menu.style.left = 'auto'
  menu.style.right = '16px'
  menu.style.top = '52px'
  items.forEach(it => {
    const el = document.createElement('div')
    el.className = `menu-item ${it.danger ? 'danger' : ''}`
    el.innerHTML = `<span style="display:grid;place-items:center;width:15px;height:15px">${iconHtml(it.icon, 14)}</span> ${it.label}`
    el.onclick = () => { menu.remove(); it.action() }
    menu.appendChild(el)
  })
  document.body.appendChild(menu)
  setTimeout(() => {
    const off = (e) => { if (!menu.contains(e.target)) { menu.remove(); document.removeEventListener('mousedown', off) } }
    document.addEventListener('mousedown', off)
  })
}

function goHome() {
  router.push(`/${props.role.toLowerCase()}/dashboard`)
}

/* ---------- 通知 ---------- */
const unreadCount = ref(0)
const notifications = ref([])
const notifyLoading = ref(false)
let pollTimer = null

async function refreshUnreadCount() {
  try {
    const res = await notificationApi.getUnreadCount()
    unreadCount.value = Number(unwrap(res)) || 0
  } catch { /* 静默 */ }
}

async function loadNotifications() {
  notifyLoading.value = true
  try {
    const res = await notificationApi.getNotificationList({ page: 1, size: 5 })
    const data = unwrap(res)
    notifications.value = data?.records || data?.list || []
  } catch { notifications.value = [] }
  finally { notifyLoading.value = false }
}

async function handleRead(n) {
  if (!n.isRead) {
    try {
      await notificationApi.markAsRead(n.id)
      n.isRead = true
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch {
      /* 静默失败：已读标记失败不影响跳转通知中心的主流程 */
    }
  }
  goNotificationCenter()
}

async function handleReadAll() {
  try {
    await notificationApi.markAllAsRead()
    unreadCount.value = 0
    notifications.value.forEach(n => n.isRead = true)
    ElMessage.success('已全部标记为已读')
  } catch { ElMessage.error('操作失败') }
}

function goNotificationCenter() {
  router.push(props.notificationPath || `/${props.role.toLowerCase()}/notifications`)
}

function formatTime(t) { return t ? dayjs(t).format('MM-DD HH:mm') : '' }

onMounted(() => {
  refreshUnreadCount()
  pollTimer = setInterval(refreshUnreadCount, 60 * 1000)
})
onBeforeUnmount(() => clearInterval(pollTimer))
</script>

<style scoped>
/* 布局 */
.app { display: flex; height: 100vh; }
.main { flex: 1; display: flex; flex-direction: column; min-width: 0; }

/* 侧边栏 */
.sidebar {
  width: var(--sidebar-w); background: linear-gradient(178deg, var(--sidebar-bg-start), var(--sidebar-bg-end));
  color: var(--sidebar-text); display: flex; flex-direction: column; flex-shrink: 0;
  z-index: var(--z-sidebar); transition: width .28s var(--ease-out);
  box-shadow: 1px 0 0 rgba(0,0,0,.18);
}
.sidebar.mini { width: var(--sidebar-w-mini); }
.brand {
  height: var(--topbar-h); display: flex; align-items: center; gap: 10px;
  padding: 0 16px; border-bottom: 1px solid var(--sidebar-divider);
  flex-shrink: 0; white-space: nowrap; overflow: hidden;
}
.sidebar.mini .brand-name, .sidebar.mini .brand-sub { display: none; }
.sidebar.mini .brand { justify-content: center; padding: 0; }
.side-nav {
  flex: 1; overflow-y: auto; padding: 12px 10px 20px;
}
.side-nav::-webkit-scrollbar { width: 6px; }
.side-nav::-webkit-scrollbar-thumb { background: rgba(255,255,255,.14); border-radius: 6px; }
.nav-section-label {
  font-size: 11px; color: var(--sidebar-text-dim); padding: 14px 10px 6px; letter-spacing: 1px;
  white-space: nowrap; overflow: hidden;
}
.sidebar.mini .nav-section-label { text-align: center; padding: 14px 0 6px; font-size: 0; }
.sidebar.mini .nav-section-label::after { content: "···"; font-size: 11px; }
.nav-item {
  display: flex; align-items: center; gap: 10px;
  padding: 9px 10px; border-radius: 8px; cursor: pointer;
  color: #b9c1cf; font-size: 13.5px; position: relative;
  transition: background .18s, color .18s; white-space: nowrap; user-select: none;
}
.nav-item:hover { background: var(--sidebar-hover-bg); color: var(--sidebar-text-hover); }
.nav-item.active { background: var(--sidebar-active-bg); color: var(--sidebar-active-text); box-shadow: 0 4px 14px rgba(193,59,48,.35); }
.nav-ico { width: 18px; height: 18px; flex-shrink: 0; display: grid; place-items: center; }
.nav-txt { flex: 1; overflow: hidden; text-overflow: ellipsis; }
.nav-arrow { transition: transform .25s var(--ease-out); opacity: .55; }
.nav-item.expanded .nav-arrow { transform: rotate(90deg); }
.nav-badge { font-size: 10px; background: rgba(255,255,255,.14); border-radius: 999px; padding: 1px 7px; line-height: 16px; }
.nav-item.active .nav-badge { background: rgba(255,255,255,.22); }
.sidebar.mini .nav-txt, .sidebar.mini .nav-arrow, .sidebar.mini .nav-badge { display: none; }
.sidebar.mini .nav-item { justify-content: center; padding: 10px 0; }
.nav-sub { overflow: hidden; max-height: 0; transition: max-height .3s var(--ease-out); }
.nav-item.expanded + .nav-sub { max-height: 600px; }
.nav-sub-inner {
  margin: 2px 0 6px 17px; padding-left: 12px;
  border-left: 1px solid rgba(255,255,255,.1); display: flex; flex-direction: column; gap: 1px;
}
.nav-sub-item {
  padding: 7px 10px; border-radius: 6px; font-size: 13px; color: var(--sidebar-sub-text);
  cursor: pointer; transition: all .16s; white-space: nowrap; user-select: none; display: flex; align-items: center; gap: 8px;
}
.nav-sub-item:hover { color: var(--sidebar-text-hover); background: var(--sidebar-hover-bg); }
.nav-sub-item.active { color: var(--sidebar-sub-active); background: rgba(193,59,48,.16); font-weight: 500; }
.dot { width: 4px; height: 4px; border-radius: 50%; background: currentColor; opacity: .6; flex-shrink: 0; }
.sidebar.mini .nav-sub { display: none; }
.side-user {
  padding: 12px; border-top: 1px solid var(--sidebar-divider);
  display: flex; align-items: center; gap: 10px; cursor: pointer; white-space: nowrap; overflow: hidden;
}
.side-user:hover { background: var(--sidebar-hover-bg); }
.side-user .u-meta { overflow: hidden; }
.side-user .u-name { font-size: 13px; color: var(--sidebar-text-hover); }
.side-user .u-role { font-size: 11px; color: var(--sidebar-text-dim); }
.sidebar.mini .side-user { justify-content: center; }
.sidebar.mini .side-user .u-meta { display: none; }

/* 顶栏 */
.topbar {
  height: var(--topbar-h); background: var(--surface);
  border-bottom: 1px solid var(--line);
  box-shadow: 0 1px 0 rgba(24,32,48,.02), 0 6px 18px rgba(24,32,48,.04);
  display: flex; align-items: center; gap: 14px; padding: 0 20px;
  z-index: var(--z-topbar); flex-shrink: 0;
}
.crumbs { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--ink-3); min-width: 0; }
.crumb { white-space: nowrap; }
.crumb.link { cursor: pointer; color: var(--info); }
.crumb.link:hover { text-decoration: underline; }
.crumb.current { color: var(--ink); font-weight: 600; }
.sep { color: var(--ink-4); }
.spacer { flex: 1; }
.user-btn {
  display: flex; align-items: center; gap: 8px; cursor: pointer; padding: 4px 8px; border-radius: 8px;
  border: none; background: transparent; transition: background .15s;
}
.user-btn:hover { background: var(--surface-3); }
.user-name-text { font-size: 13.5px; color: var(--ink); max-width: 96px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.n-badge {
  position: absolute; top: 3px; right: 3px; min-width: 14px; height: 14px;
  background: var(--accent); color: #fff; font-size: 9px; border-radius: 999px;
  display: grid; place-items: center; padding: 0 4px; border: 2px solid var(--surface);
}
.content {
  flex: 1; overflow-y: auto; padding: 20px 24px 48px; scroll-behavior: smooth;
  overscroll-behavior: contain;
  background:
    radial-gradient(1100px 520px at 88% -8%, rgba(193,59,48,.05), transparent 60%),
    radial-gradient(900px 460px at -6% 4%, rgba(37,99,168,.045), transparent 58%),
    var(--bg);
  background-attachment: local;
}

/* 通知 */
.notify-panel { display: flex; flex-direction: column; max-height: 420px; }
.notify-head { display: flex; align-items: center; justify-content: space-between; padding-bottom: 8px; border-bottom: 1px solid var(--line); }
.notify-title { font-size: 14px; font-weight: 600; color: var(--ink); }
.notify-list { flex: 1; overflow-y: auto; min-height: 80px; }
.notify-item { display: flex; gap: 8px; padding: 10px 6px; border-radius: 6px; cursor: pointer; transition: background .15s; }
.notify-item:hover { background: var(--surface-3); }
.notify-dot { width: 7px; height: 7px; border-radius: 50%; background: var(--accent); margin-top: 7px; flex-shrink: 0; }
.notify-body { min-width: 0; }
.notify-item-title { font-size: 13px; font-weight: 600; color: var(--ink); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.notify-item-content { font-size: 12px; color: var(--ink-2); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.notify-item-time { font-size: 11px; color: var(--ink-3); margin-top: 2px; }
.notify-foot { padding-top: 8px; border-top: 1px solid var(--line); text-align: center; }

@media (max-width: 768px) {
  .sidebar { position: fixed; left: 0; top: 0; bottom: 0; z-index: 100; transform: translateX(-100%); transition: transform .28s var(--ease-out); }
  .sidebar.mini { width: var(--sidebar-w); transform: translateX(0); }
  .user-name-text { display: none; }
}

/* 路由过渡：轻快淡入上浮，克制不抢注意力 */
.page-enter-active {
  transition: opacity .22s var(--ease-out), transform .22s var(--ease-out);
}
.page-leave-active {
  transition: opacity .12s ease;
}
.page-enter-from { opacity: 0; transform: translateY(8px); }
.page-leave-to { opacity: 0; }
@media (prefers-reduced-motion: reduce) {
  .page-enter-active, .page-leave-active { transition: none; }
  .page-enter-from { opacity: 0; transform: none; }
}
</style>
