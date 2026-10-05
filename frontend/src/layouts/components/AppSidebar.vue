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
          <div v-if="item.children" class="nav-sub" :class="{ open: isExpanded(item.key) }">
            <div class="nav-sub-inner">
              <template v-for="child in item.children" :key="child.key">
                <div
                  class="nav-sub-item"
                  :class="{
                    active: !child.children && isActive(child),
                    expanded: child.children && isExpanded(child.key),
                  }"
                  @click="handleNavChildClick(child, item)"
                >
                  <span v-if="!child.children" class="dot"></span>
                  <span class="nav-sub-txt">{{ child.text }}</span>
                  <span
                    v-if="child.children"
                    class="nav-arrow"
                    v-html="iconHtml('chevronR', 11)"
                  ></span>
                </div>
                <!-- 三级：课程下的功能页签（作业/考试/成绩/知识图谱…） -->
                <div
                  v-if="child.children"
                  class="nav-sub nav-sub-3"
                  :class="{ open: isExpanded(child.key) }"
                >
                  <div class="nav-sub-inner nav-sub-inner-3">
                    <div
                      v-for="leaf in child.children"
                      :key="leaf.key"
                      class="nav-sub-item nav-leaf"
                      :class="{ active: isActive(leaf) }"
                      @click="router.push(leaf.path)"
                    >
                      <span class="dot"></span>
                      <span class="nav-sub-txt">{{ leaf.text }}</span>
                    </div>
                  </div>
                </div>
              </template>
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
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import courseApi from '@/api/course'
import studentApi from '@/api/student'
import { unwrap } from '@/utils/response'
import {
  ROLE_META,
  ADMIN_SECTIONS,
  TEACHER_SECTIONS,
  STUDENT_SECTIONS,
  buildCourseCatalogSection,
} from '../menus'
import { iconHtml } from '../icons'

/**
 * 应用侧边栏（深色手风琴三级菜单 + mini 折叠模式）
 * <p>品牌区、按角色渲染的导航分区（一级项 + 二级子菜单 + 课程目录三级页签）、底部用户区；
 * 「课程目录」按当前登录者的授课/选课列表动态构建，进入课程子路由时自动展开祖先节点；
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
const STATIC_MAP = { ADMIN: ADMIN_SECTIONS, TEACHER: TEACHER_SECTIONS, STUDENT: STUDENT_SECTIONS }

/* ---- 动态课程目录 ---- */
const courses = ref([])

async function loadCourses() {
  if (props.role === 'ADMIN') return
  try {
    const res =
      props.role === 'TEACHER'
        ? await courseApi.getMyCourses({ page: 1, size: 200 })
        : await studentApi.getMyCourses({ page: 1, size: 200 })
    const body = unwrap(res) || {}
    courses.value = body.records || body.list || (Array.isArray(body) ? body : [])
  } catch (e) {
    console.warn('[sidebar] 课程列表加载失败', e)
  }
}

onMounted(loadCourses)

const navSections = computed(() => {
  const base = STATIC_MAP[props.role] || STUDENT_SECTIONS
  if (props.role === 'ADMIN') return base
  return [...buildCourseCatalogSection(props.role, courses.value), ...base]
})

const expandedKeys = ref(new Set())

function isActive(item) {
  if (item.key === 'course-catalog') {
    return new RegExp(`^/${props.role.toLowerCase()}/courses/\\d+`).test(route.path)
  }
  // 「课程总览」仅列表页精确高亮，避免 /{role}/courses/:id/* 前缀误亮
  if (item.key === 'course-catalog-all') return route.path === item.path
  return route.path === item.path || (item.path && route.path.startsWith(item.path + '/'))
}

function isExpanded(key) {
  return expandedKeys.value.has(key)
}

function toggleExpand(key) {
  const s = new Set(expandedKeys.value)
  if (s.has(key)) s.delete(key)
  else s.add(key)
  expandedKeys.value = s
}

function handleNavClick(item) {
  if (item.children) toggleExpand(item.key)
  else if (item.path) router.push(item.path)
}

function handleNavChildClick(child) {
  if (child.children) {
    toggleExpand(child.key)
    return
  }
  if (child.path) router.push(child.path)
}

/* 进入某课程的功能页（/role/courses/:id/xxx）时自动展开「课程目录」与该课程节点 */
watch(
  () => route.path,
  (p) => {
    const m = p.match(/^\/(?:teacher|student)\/courses\/(\d+)/)
    if (m) {
      const s = new Set(expandedKeys.value)
      s.add('course-catalog')
      s.add(`course-${m[1]}`)
      expandedKeys.value = s
    }
  },
  { immediate: true },
)

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
  color: var(--ink-3);
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
.nav-item.expanded .nav-arrow,
.nav-sub-item.expanded .nav-arrow {
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
  display: grid;
  grid-template-rows: 0fr;
  transition: grid-template-rows 0.3s var(--ease-out);
}
.nav-sub.open {
  grid-template-rows: 1fr;
}
.nav-sub-inner {
  overflow: hidden;
  min-height: 0;
  margin: 0 0 0 17px;
  padding-left: 12px;
  border-left: 1px solid rgba(255, 255, 255, 0.1);
  display: flex;
  flex-direction: column;
  gap: 1px;
}
.nav-sub.open > .nav-sub-inner {
  margin-top: 2px;
  margin-bottom: 6px;
}
.nav-sub-inner-3 {
  margin-left: 14px;
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
.nav-sub-txt {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
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
.nav-sub-item.expanded {
  color: var(--sidebar-text-hover);
}
.nav-leaf {
  font-size: 12.5px;
  padding: 6px 10px;
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
  color: var(--surface);
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
