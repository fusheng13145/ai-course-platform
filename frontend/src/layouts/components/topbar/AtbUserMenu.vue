<template>
  <!-- 用户头像下拉 -->
  <button class="user-btn" @click="openMenuFrom('.user-btn')">
    <span class="avatar" :style="avatarStyle">{{ avatarInitial }}</span>
    <span class="user-name-text">{{ auth.userName }}</span>
  </button>

  <!-- 与侧栏底部用户区同一份 items；顶栏在头像下方展开 -->
  <UserMenu
    v-if="menuOpen"
    :items="menuItems"
    :anchor-rect="menuRect"
    @closed="menuOpen = false"
  />
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import UserMenu from '@/components/UserMenu.vue'
import { reopenOnboarding, USER_MENU_ITEMS } from '@/components/userMenuItems'

const props = defineProps({
  role: { type: String, required: true },
  profilePath: { type: String, default: '' },
})

const router = useRouter()
const auth = useAuthStore()

const avatarInitial = computed(() => (auth.userName || 'U').slice(-2))
const avatarStyle = computed(() => {
  const idx = [...(auth.userName || 'U')].reduce((s, c) => s + c.charCodeAt(0), 0) % 6
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

/* ---- 用户菜单（单一来源 UserMenu，跟随本组件的头像按钮） ---- */
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
    onGuide: reopenOnboarding,
  }),
)
</script>

<style scoped>
.user-btn {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 8px;
  border: none;
  background: transparent;
  transition: background 0.15s;
}
.user-btn:hover {
  background: var(--surface-3);
}
.user-name-text {
  font-size: 13.5px;
  color: var(--ink);
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--surface);
  font-weight: 600;
  font-size: 13px;
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .user-name-text {
    display: none;
  }
}
</style>
