<template>
  <!-- eslint-disable vue/no-v-html -->
  <!-- v-html 仅用于内部 SVG 图标常量（受控输入），无需净化 -->
  <div class="app">
    <!-- 侧边栏（深色，手风琴二级菜单 + mini 折叠） -->
    <AppSidebar :role="role" :sidebar-mini="sidebarMini" @user-menu="showUserMenu" />

    <!-- 主区 -->
    <div class="main">
      <AppTopbar
        :role="role"
        :notification-path="notificationPath"
        :profile-path="profilePath"
        @toggle-sidebar="sidebarMini = !sidebarMini"
      />

      <main ref="contentRef" class="content">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" :key="viewKey" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>

  <!-- 新用户引导（首次登录自动展示，三端共用） -->
  <OnboardingGuide />
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { iconHtml } from './icons'
import OnboardingGuide from '@/components/OnboardingGuide.vue'
import AppSidebar from './components/AppSidebar.vue'
import AppTopbar from './components/AppTopbar.vue'

/**
 * 应用布局骨架
 * <p>整体壳：深色侧边栏（手风琴菜单/mini 折叠）+ 顶栏（搜索/通知/主题/用户菜单）
 * + 内容区（路由过渡）；共享侧边栏折叠状态，用户菜单（个人中心/退出）经动态 DOM 弹出。</p>
 */
const props = defineProps({
  role: { type: String, required: true },
  notificationPath: { type: String, default: '' },
  profilePath: { type: String, default: '' },
})

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

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

/* ---- 用户菜单（动态 DOM 弹出，侧边栏底部用户区共用） ---- */
function showUserMenu() {
  const items = [
    {
      label: '个人中心',
      icon: 'user',
      action: () => router.push(props.profilePath || `/${props.role.toLowerCase()}/profile`),
    },
    {
      label: '退出登录',
      icon: 'logout',
      danger: true,
      action: () => auth.logout().then(() => router.push('/login')),
    },
  ]
  const menu = document.createElement('div')
  menu.className = 'pop-menu'
  menu.style.left = 'auto'
  menu.style.right = '16px'
  menu.style.top = '52px'
  items.forEach((it) => {
    const el = document.createElement('div')
    el.className = `menu-item ${it.danger ? 'danger' : ''}`
    el.innerHTML = `<span style="display:grid;place-items:center;width:15px;height:15px">${iconHtml(it.icon, 14)}</span> ${it.label}`
    el.onclick = () => {
      menu.remove()
      it.action()
    }
    menu.appendChild(el)
  })
  document.body.appendChild(menu)
  setTimeout(() => {
    const off = (e) => {
      if (!menu.contains(e.target)) {
        menu.remove()
        document.removeEventListener('mousedown', off)
      }
    }
    document.addEventListener('mousedown', off)
  })
}
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

<style>
/* 用户菜单（动态 DOM，非 scoped） */
.pop-menu {
  position: fixed;
  z-index: 3000;
  background: var(--surface);
  border: 1px solid var(--line, rgba(0, 0, 0, 0.08));
  border-radius: 10px;
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.16);
  padding: 6px;
  min-width: 148px;
}
.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
  color: var(--ink);
  cursor: pointer;
  transition: background 0.15s;
}
.menu-item:hover {
  background: var(--surface-3, rgba(0, 0, 0, 0.05));
}
.menu-item.danger {
  color: var(--accent-strong);
}
.menu-item.danger:hover {
  background: rgba(176, 80, 69, 0.08);
}
</style>
