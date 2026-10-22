<template>
  <div class="app">
    <!-- 侧边栏（深色，手风琴二级菜单 + mini 折叠） -->
    <AppSidebar
      :role="role"
      :sidebar-mini="sidebarMini"
      @user-menu="openMenuFrom('.side-user')"
    />

    <!-- 主区 -->
    <div class="main">
      <AppTopbar
        :role="role"
        :notification-path="notificationPath"
        :profile-path="profilePath"
        @toggle-sidebar="sidebarMini = !sidebarMini"
      />

      <main ref="contentRef" class="content" :aria-busy="navigating">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" :key="viewKey" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>

  <!-- 新用户引导（首次进入自动展示，三端共用；用户菜单可重新唤起）。
       身份由宿主以 props 给（组件不再自己 useAuthStore，避免同一份取值两条路）：
       role 在这里是 "ADMIN"/"TEACHER"/"STUDENT"，steps 配置按小写角色索引。 -->
  <OnboardingGuide ref="guideRef" :user-id="auth.userId" :role="role.toLowerCase()" />

  <!-- 键盘快捷键说明（D2-7 第八批：按 ? 打开/关闭） -->
  <KeyboardShortcutsDialog v-model="shortcutsHelpVisible" />

  <!-- 用户菜单：跟随侧栏底部用户区的位置弹出，与顶栏头像共用同一份 items -->
  <UserMenu
    v-if="menuOpen"
    :items="menuItems"
    :anchor-rect="menuRect"
    @closed="menuOpen = false"
  />
</template>

<script setup>
import { ref, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { announce } from '@/composables/useAnnouncer'
import {
  useGlobalHotkeys,
  hasBlockingOverlay,
  FOCUS_GLOBAL_SEARCH_EVENT,
} from '@/composables/useKeyboardShortcuts'
import OnboardingGuide from '@/components/OnboardingGuide.vue'
import UserMenu from '@/components/UserMenu.vue'
import { REOPEN_ONBOARDING_EVENT, USER_MENU_ITEMS } from '@/components/userMenuItems'
import KeyboardShortcutsDialog from './components/KeyboardShortcutsDialog.vue'
import AppSidebar from './components/AppSidebar.vue'
import AppTopbar from './components/AppTopbar.vue'

/**
 * 应用布局骨架
 * <p>整体壳：深色侧边栏（手风琴菜单/mini 折叠）+ 顶栏（搜索/通知/主题/用户菜单）
 * + 内容区（路由过渡）；共享侧边栏折叠状态，用户菜单由 UserMenu 组件按触发物位置弹出。</p>
 */
const props = defineProps({
  role: { type: String, required: true },
  notificationPath: { type: String, default: '' },
  profilePath: { type: String, default: '' },
})

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

// D2-7：导航期间主内容区标记 aria-busy（读屏不朗读中间态），结束后清除
const navigating = ref(false)
const unregisterBefore = router.beforeEach?.(() => {
  navigating.value = true
})
const unregisterAfter = router.afterEach?.(() => {
  navigating.value = false
})
onBeforeUnmount(() => {
  unregisterBefore?.()
  unregisterAfter?.()
})

// D2-7：SPA 路由切换对读屏无声——进入新页面后播报页面标题（meta.title）
watch(
  () => route.fullPath || route.path,
  async () => {
    await nextTick()
    const matched = route.matched || []
    const title = matched[matched.length - 1]?.meta?.title
    if (title) announce(`已进入${title}页面`)
  },
  { immediate: true },
)

/**
 * 本层视图的重建判据：只用「本层路由记录 + 该记录声明的参数值」，不用 route.path。
 * 按 path 打 key 会把下层页签也算进去，切一次课程页签就整体重建课程壳（接口重来 +
 * 内容区先清空再淡入），而淡入依赖 rAF——窗口被遮挡时 rAF 冻结，页面就卡在离场帧。
 */
const viewKey = computed(() => {
  const rec = route.matched[1]
  if (!rec) return route.path
  return rec.path.replace(/:[^/]+/g, (m) => String(route.params[m.slice(1)] ?? ''))
})

// 壳层复用后不再靠重建把滚动归零，切页时显式回到顶部（.content 才是滚动容器）
const contentRef = ref(null)
watch(
  () => route.path,
  () => contentRef.value?.scrollTo({ top: 0 }),
)

const sidebarMini = ref(false)
const guideRef = ref(null)

/* ---- D2-7 第八批：全局快捷键（详见 useKeyboardShortcuts 原语） ---- */
const shortcutsHelpVisible = ref(false)

/** 聚焦顶栏全局搜索：派发事件由 AtbGlobalSearch 监听（跨组件解耦，不传 ref） */
function focusGlobalSearch() {
  window.dispatchEvent(new CustomEvent(FOCUS_GLOBAL_SEARCH_EVENT))
}

useGlobalHotkeys([
  // Ctrl/⌘+K 与 /：聚焦全局搜索；输入态与弹层打开时不触发（原语默认守卫）
  { key: 'k', mod: true, handler: focusGlobalSearch },
  { key: '/', handler: focusGlobalSearch },
  {
    // ?：打开/关闭快捷键说明。allowWhenOverlayOpen 放行给处理器自行判断——
    // 已打开时按 ? 关闭（toggle）；有阻塞遮罩在场时不叠加。
    // 这里判的是 hasBlockingOverlay 而不是 hasOpenOverlay：后者只认 .el-overlay，
    // 引导层渲染的是 .ob-overlay，用它的结果是说明面板直接盖在引导上面。
    key: '?',
    handler: () => {
      if (shortcutsHelpVisible.value) {
        shortcutsHelpVisible.value = false
        return
      }
      if (typeof document !== 'undefined' && hasBlockingOverlay()) return
      shortcutsHelpVisible.value = true
    },
    allowWhenOverlayOpen: true,
  },
])

/* ---- 用户菜单（与顶栏头像同一份 items） ---- */
const menuOpen = ref(false)
const menuRect = ref({ top: 0, left: 0 })
function openMenuFrom(selector) {
  const el = document.querySelector(selector)
  if (!el) return
  const r = el.getBoundingClientRect()
  menuRect.value = { top: r.top, left: r.left, bottom: r.bottom }
  menuOpen.value = true
}
const menuItems = computed(() =>
  USER_MENU_ITEMS({
    router,
    role: props.role,
    profilePath: props.profilePath,
    logout: () => auth.logout(),
    onGuide: () => guideRef.value?.resume(),
  }),
)

// 顶栏用户区在 AppTopbar 之下，拿不到引导实例：重开请求经窗口事件送到这唯一的 ref
const reopenGuide = () => guideRef.value?.resume()
onMounted(() => window.addEventListener(REOPEN_ONBOARDING_EVENT, reopenGuide))
onBeforeUnmount(() => window.removeEventListener(REOPEN_ONBOARDING_EVENT, reopenGuide))
</script>

<style scoped>
/* 布局 */
.app {
  display: flex;
  height: 100vh;
}
.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

/* 内容区 */
.content {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px 48px;
  scroll-behavior: smooth;
  overscroll-behavior: contain;
  background:
    radial-gradient(1100px 520px at 88% -8%, rgba(193, 59, 48, 0.05), transparent 60%),
    radial-gradient(900px 460px at -6% 4%, rgba(37, 99, 168, 0.045), transparent 58%), var(--bg);
  background-attachment: local;
}

/* 路由过渡：轻快淡入上浮 */
.page-enter-active {
  transition:
    opacity 0.22s var(--ease-out),
    transform 0.22s var(--ease-out);
}
.page-leave-active {
  transition: opacity 0.12s ease;
}
.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-leave-to {
  opacity: 0;
}
@media (prefers-reduced-motion: reduce) {
  .page-enter-active,
  .page-leave-active {
    transition: none;
  }
  .page-enter-from {
    opacity: 0;
    transform: none;
  }
}
</style>
