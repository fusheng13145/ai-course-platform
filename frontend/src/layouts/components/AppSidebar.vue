<template>
  <!-- eslint-disable vue/no-v-html -->
  <!-- v-html 仅用于内部 SVG 图标常量（受控输入），无需净化 -->
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
            :class="{ active: isActive(item), expanded: isExpanded(item.key) }"
            @click="handleNavClick(item)"
          >
            <span class="nav-ico" v-html="iconHtml(item.icon, 17)"></span>
            <span class="nav-txt">{{ item.text }}</span>
            <span v-if="item.badge != null" class="nav-badge">{{ item.badge }}</span>
            <span v-if="item.children" class="nav-arrow" v-html="iconHtml('chevronR', 13)"></span>
          </div>
          <!-- 二级手风琴子菜单 -->
          <div v-if="item.children" class="nav-sub">
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

    <div class="side-user" @click="emit('user-menu')">
      <span class="avatar lg" :style="avatarStyle">{{ avatarInitial }}</span>
      <div class="u-meta">
        <div class="u-name">{{ auth.userName }}</div>
        <div class="u-role">{{ roleMeta.label }}</div>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ROLE_META, ADMIN_SECTIONS, TEACHER_SECTIONS, STUDENT_SECTIONS } from '../menus'
import { iconHtml } from '../icons'

/**
 * 应用侧边栏（深色手风琴二级菜单 + mini 折叠模式）
 * <p>品牌区、按角色渲染的导航分区（一级项 + 二级手风琴）、底部用户区；
 * 展开状态与导航高亮/跳转逻辑内聚于此，mini 模式由父组件 prop 控制。</p>
 */
const props = defineProps({
  /** 当前角色（ADMIN/TEACHER/STUDENT），决定导航与品牌元数据 */
  role: { type: String, required: true },
  /** mini 折叠模式 */
  sidebarMini: { type: Boolean, default: false },
})

const emit = defineEmits(['user-menu'])

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const roleMeta = computed(() => ROLE_META[props.role] || ROLE_META.STUDENT)
const SECTIONS_MAP = { ADMIN: ADMIN_SECTIONS, TEACHER: TEACHER_SECTIONS, STUDENT: STUDENT_SECTIONS }
const navSections = computed(() => SECTIONS_MAP[props.role] || STUDENT_SECTIONS)

const expandedKeys = ref(new Set())

function isActive(item) {
  if (item.key === 'my' && route.path.startsWith(`/${props.role.toLowerCase()}/courses`))
    return true
  if (item.key === 'hall' && route.path.startsWith(`/${props.role.toLowerCase()}/courses`))
    return true
  return route.path === item.path || (item.path && route.path.startsWith(item.path + '/'))
}

function isExpanded(key) {
  return expandedKeys.value.has(key)
}

function handleNavClick(item) {
  if (item.children) {
    const s = new Set(expandedKeys.value)
    if (s.has(item.key)) s.delete(item.key)
    else s.add(item.key)
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

const avatarInitial = computed(() => (auth.userName || 'U').slice(-2))
const avatarStyle = computed(() => {
  const idx = ([...(auth.userName || 'U')] || []).reduce((s, c) => s + c.charCodeAt(0), 0) % 6
  const gradients = [
    'linear-gradient(135deg,#c13b30,#8f2a21)',
    'linear-gradient(135deg,#2563a8,#1a4a80)',
    'linear-gradient(135deg,#2e7d4f,#1f5c39)',
    'linear-gradient(135deg,#b57908,#8a5d06)',
    'linear-gradient(135deg,#6d4fa1,#4f3877)',
    'linear-gradient(135deg,#0f766e,#0a544f)',
  ]
  return { background: gradients[idx] }
})
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-w);
  background: linear-gradient(178deg, var(--sidebar-bg-start), var(--sidebar-bg-end));
  color: var(--sidebar-text);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  z-index: var(--z-sidebar);
  transition: width 0.28s var(--ease-out);
  box-shadow: 1px 0 0 rgba(0, 0, 0, 0.18);
}
.sidebar.mini {
  width: var(--sidebar-w-mini);
}
.brand {
  height: var(--topbar-h);
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid var(--sidebar-divider);
  flex-shrink: 0;
  white-space: nowrap;
  overflow: hidden;
}
.sidebar.mini .brand-name,
.sidebar.mini .brand-sub {
  display: none;
}
.sidebar.mini .brand {
  justify-content: center;
  padding: 0;
}
.side-nav {
  flex: 1;
  overflow-y: auto;
  padding: 12px 10px 20px;
}
.side-nav::-webkit-scrollbar {
  width: 6px;
}
.side-nav::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.14);
  border-radius: 6px;
}
.nav-section-label {
  font-size: 11px;
  color: var(--sidebar-text-dim);
  padding: 14px 10px 6px;
  letter-spacing: 1px;
  white-space: nowrap;
  overflow: hidden;
}
.sidebar.mini .nav-section-label {
  text-align: center;
  padding: 14px 0 6px;
  font-size: 0;
}
.sidebar.mini .nav-section-label::after {
  content: '···';
  font-size: 11px;
}
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 10px;
  border-radius: 8px;
  cursor: pointer;
  color: #b9c1cf;
  font-size: 13.5px;
  position: relative;
  transition:
    background 0.18s,
    color 0.18s;
  white-space: nowrap;
  user-select: none;
}
.nav-item:hover {
  background: var(--sidebar-hover-bg);
  color: var(--sidebar-text-hover);
}
.nav-item.active {
  background: var(--sidebar-active-bg);
  color: var(--sidebar-active-text);
  box-shadow: 0 4px 14px rgba(193, 59, 48, 0.35);
}
.nav-ico {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
}
.nav-txt {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
}
.nav-arrow {
  transition: transform 0.25s var(--ease-out);
  opacity: 0.55;
}
.nav-item.expanded .nav-arrow {
  transform: rotate(90deg);
}
.nav-badge {
  font-size: 10px;
  background: rgba(255, 255, 255, 0.14);
  border-radius: 999px;
  padding: 1px 7px;
  line-height: 16px;
}
.nav-item.active .nav-badge {
  background: rgba(255, 255, 255, 0.22);
}
.sidebar.mini .nav-txt,
.sidebar.mini .nav-arrow,
.sidebar.mini .nav-badge {
  display: none;
}
.sidebar.mini .nav-item {
  justify-content: center;
  padding: 10px 0;
}
.nav-sub {
  overflow: hidden;
  max-height: 0;
  transition: max-height 0.3s var(--ease-out);
}
.nav-item.expanded + .nav-sub {
  max-height: 600px;
}
.nav-sub-inner {
  margin: 2px 0 6px 17px;
  padding-left: 12px;
  border-left: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.nav-sub-item {
  padding: 7px 10px;
  border-radius: 6px;
  font-size: 13px;
  color: var(--sidebar-sub-text);
  cursor: pointer;
  transition: all 0.16s;
  white-space: nowrap;
  user-select: none;
  display: flex;
  align-items: center;
  gap: 8px;
}
.nav-sub-item:hover {
  color: var(--sidebar-text-hover);
  background: var(--sidebar-hover-bg);
}
.nav-sub-item.active {
  color: var(--sidebar-sub-active);
  background: rgba(193, 59, 48, 0.16);
  font-weight: 500;
}
.dot {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: currentColor;
  opacity: 0.6;
  flex-shrink: 0;
}
.sidebar.mini .nav-sub {
  display: none;
}
.side-user {
  padding: 12px;
  border-top: 1px solid var(--sidebar-divider);
  display: flex;
  align-items: center;
  gap: 10px;
  cursor: pointer;
  white-space: nowrap;
  overflow: hidden;
}
.side-user:hover {
  background: var(--sidebar-hover-bg);
}
.side-user .u-meta {
  overflow: hidden;
}
.side-user .u-name {
  font-size: 13px;
  color: var(--sidebar-text-hover);
}
.side-user .u-role {
  font-size: 11px;
  color: var(--sidebar-text-dim);
}
.sidebar.mini .side-user {
  justify-content: center;
}
.sidebar.mini .side-user .u-meta {
  display: none;
}
.avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
  font-size: 14px;
  flex-shrink: 0;
}
.avatar.lg {
  width: 38px;
  height: 38px;
}

@media (max-width: 768px) {
  .sidebar {
    position: fixed;
    left: 0;
    top: 0;
    bottom: 0;
    z-index: 100;
    transform: translateX(-100%);
    transition: transform 0.28s var(--ease-out);
  }
  .sidebar.mini {
    width: var(--sidebar-w);
    transform: translateX(0);
  }
}
</style>
