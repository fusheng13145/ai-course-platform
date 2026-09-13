<template>
  <!-- 用户头像下拉 -->
  <button class="user-btn" @click="showUserMenu">
    <span class="avatar" :style="avatarStyle">{{ avatarInitial }}</span>
    <span class="user-name-text">{{ auth.userName }}</span>
  </button>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { iconHtml } from '../../icons'

const props = defineProps({
  role: { type: String, required: true },
  profilePath: { type: String, default: '' },
})

const router = useRouter()
const auth = useAuthStore()

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

/* ---- 用户菜单（动态 DOM 弹出） ---- */
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
