<template>
  <!-- eslint-disable vue/no-v-html -->
  <!-- v-html 仅用于内部 SVG 图标常量（受控输入），无需净化 -->
  <!-- 通知按钮 -->
  <el-popover placement="bottom-end" :width="340" trigger="click" @show="loadNotifications">
    <template #reference>
      <button class="t-icon-btn" title="通知">
        <span v-html="iconHtml('bell', 17)"></span>
        <span v-if="unreadCount" class="n-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
      </button>
    </template>
    <div class="notify-panel">
      <div class="notify-head">
        <span class="notify-title">通知</span>
        <el-button text size="small" :disabled="!unreadCount" @click="handleReadAll"
          >全部已读</el-button
        >
      </div>
      <div v-loading="notifyLoading" class="notify-list">
        <div
          v-if="!notifyLoading && !notifications.length"
          class="empty-state"
          style="padding: 24px"
        >
          <div class="e-title">暂无通知</div>
        </div>
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
        <el-button text size="small" @click="goNotificationCenter">查看全部通知</el-button>
      </div>
    </div>
  </el-popover>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import dayjs from 'dayjs'
import { unwrap } from '@/utils/response'
import notificationApi from '@/api/notification'
import { iconHtml } from '../../icons'

const props = defineProps({
  role: { type: String, required: true },
  notificationPath: { type: String, default: '' },
})

const router = useRouter()

const unreadCount = ref(0)
const notifications = ref([])
const notifyLoading = ref(false)
let pollTimer = null

async function refreshUnreadCount() {
  try {
    const res = await notificationApi.getUnreadCount()
    unreadCount.value = Number(unwrap(res)) || 0
  } catch {
    /* 静默 */
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
      /* 已读失败不影响跳转主流程 */
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
    ElMessage.error('操作失败')
  }
}

function goNotificationCenter() {
  router.push(props.notificationPath || `/${props.role.toLowerCase()}/notifications`)
}

function formatTime(t) {
  return t ? dayjs(t).format('MM-DD HH:mm') : ''
}

onMounted(() => {
  refreshUnreadCount()
  pollTimer = setInterval(refreshUnreadCount, 60 * 1000)
})
onBeforeUnmount(() => clearInterval(pollTimer))
</script>

<style scoped>
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
.n-badge {
  position: absolute;
  top: 3px;
  right: 3px;
  min-width: 14px;
  height: 14px;
  background: var(--accent);
  color: var(--surface);
  font-size: 9px;
  border-radius: 999px;
  display: grid;
  place-items: center;
  padding: 0 4px;
  border: 2px solid var(--surface);
}

/* 通知 */
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
  border-bottom: 1px solid var(--line);
}
.notify-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink);
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
  transition: background 0.15s;
}
.notify-item:hover {
  background: var(--surface-3);
}
.notify-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--accent);
  margin-top: 7px;
  flex-shrink: 0;
}
.notify-body {
  min-width: 0;
}
.notify-item-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.notify-item-content {
  font-size: 12px;
  color: var(--ink-2);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.notify-item-time {
  font-size: 11px;
  color: var(--ink-3);
  margin-top: 2px;
}
.notify-foot {
  padding-top: 8px;
  border-top: 1px solid var(--line);
  text-align: center;
}
</style>
