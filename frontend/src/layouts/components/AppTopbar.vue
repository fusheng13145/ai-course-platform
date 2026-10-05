<template>
  <!-- eslint-disable vue/no-v-html -->
  <!-- v-html 仅用于内部 SVG 图标常量（受控输入），无需净化 -->
  <header class="topbar">
    <button
      class="t-icon-btn"
      title="折叠侧边栏"
      @click="emit('toggle-sidebar')"
      v-html="iconHtml('menu', 17)"
    ></button>

    <div class="crumbs">
      <span class="crumb link" @click="goHome"
        ><span v-html="iconHtml('dashboard', 13)"></span> {{ roleMeta.label }}</span
      >
      <template v-for="(crumb, ci) in breadcrumbs" :key="ci">
        <span class="sep" v-html="iconHtml('chevronR', 12)"></span>
        <span
          class="crumb"
          :class="{
            current: ci === breadcrumbs.length - 1,
            link: ci < breadcrumbs.length - 1 && crumb.path,
          }"
          @click="crumb.path ? router.push(crumb.path) : undefined"
          >{{ crumb.title }}</span
        >
      </template>
    </div>

    <div class="spacer"></div>

    <AtbGlobalSearch :role="role" />

    <AtbNotification :role="role" :notification-path="notificationPath" />

    <!-- 主题切换 -->
    <ThemeSwitcher />

    <AtbUserMenu :role="role" :profile-path="profilePath" />
  </header>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ThemeSwitcher from '@/components/ThemeSwitcher.vue'
import AtbGlobalSearch from './topbar/AtbGlobalSearch.vue'
import AtbNotification from './topbar/AtbNotification.vue'
import AtbUserMenu from './topbar/AtbUserMenu.vue'
import { ROLE_META } from '../menus'
import { iconHtml } from '../icons'

/**
 * 应用顶栏
 * <p>折叠按钮与面包屑内聚于此；全局搜索（防抖 + 结果面板）、通知（未读徽标/列表/已读）、
 * 用户菜单（个人中心/退出）分别下沉到 topbar 子组件。</p>
 */
const props = defineProps({
  role: { type: String, required: true },
  notificationPath: { type: String, default: '' },
  profilePath: { type: String, default: '' },
})

const emit = defineEmits(['toggle-sidebar'])

const route = useRoute()
const router = useRouter()

const roleMeta = computed(() => ROLE_META[props.role] || ROLE_META.STUDENT)

/* ---- 面包屑 ---- */
const breadcrumbs = computed(() => {
  const matched = route.matched.filter((r) => r.meta?.title)
  return matched.map((r) => ({ title: r.meta.breadcrumb || r.meta.title, path: r.path || '' }))
})

function goHome() {
  router.push(`/${props.role.toLowerCase()}/dashboard`)
}
</script>

<style scoped>
.topbar {
  height: var(--topbar-h);
  background: var(--surface);
  border-bottom: 1px solid var(--line);
  box-shadow:
    0 1px 0 rgba(24, 32, 48, 0.02),
    0 6px 18px rgba(24, 32, 48, 0.04);
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 0 20px;
  z-index: var(--z-topbar);
  flex-shrink: 0;
}
.t-icon-btn {
  width: 34px;
  height: 34px;
  border: none;
  background: transparent;
  border-radius: 8px;
  cursor: pointer;
  display: grid;
  place-items: center;
  color: var(--ink-2);
  position: relative;
  transition:
    background 0.15s,
    color 0.15s;
}
.t-icon-btn:hover {
  background: var(--surface-3);
  color: var(--ink);
}
.crumbs {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--ink-3);
  min-width: 0;
  overflow: hidden;
  flex-wrap: nowrap;
}
.crumb {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}
.crumb.link {
  cursor: pointer;
  color: var(--info);
}
.crumb.link:hover {
  text-decoration: underline;
}
.crumb.current {
  color: var(--ink);
  font-weight: 600;
}
.sep {
  color: var(--ink-4);
}
.spacer {
  flex: 1;
}

@media (max-width: 768px) {
  .crumbs {
    flex: 1;
    min-width: 0;
    overflow: hidden;
  }
  /* 窄屏仅保留当前页名，避免整段被裁切 */
  .crumbs .crumb:not(.current),
  .crumbs .sep {
    display: none;
  }
}
</style>
