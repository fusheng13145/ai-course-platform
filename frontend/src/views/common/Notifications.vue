<template>
  <div>
    <div class="table-toolbar">
      <div class="toolbar-left"><el-button type="primary" @click="markAllRead">全部标为已读</el-button></div>
    </div>
    <div class="content-card">
      <div v-for="n in notifications" :key="n.id" :style="{ padding: '16px', borderBottom: '1px solid #f0f0f0', background: n.isRead ? '#fff' : '#f5f7fa' }">
        <div style="display:flex;justify-content:space-between;align-items:center">
          <div>
            <span v-if="!n.isRead" style="display:inline-block;width:8px;height:8px;background:#409eff;border-radius:50%;margin-right:8px"></span>
            <span style="font-weight:600">{{ n.title }}</span>
          </div>
          <div>
            <el-button v-if="!n.isRead" text type="primary" size="small" @click="markRead(n)">标为已读</el-button>
          </div>
        </div>
        <p style="margin:8px 0 0;color:#606266;font-size:13px">{{ n.content }}</p>
        <p style="margin:4px 0 0;color:#c0c4cc;font-size:12px">{{ n.createdAt }}</p>
      </div>
      <el-empty v-if="notifications.length === 0" description="暂无通知" />
    </div>
  </div>
</template>
<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import notificationApi from '@/api/notification'
import { on as wsOn, off as wsOff } from '@/utils/websocket'

const notifications = ref([])
const unreadCount = ref(0)
let unlisten = null // WebSocket 取消订阅函数

onMounted(async () => {
  // 加载历史通知列表
  try {
    const res = await notificationApi.getNotificationList({ limit: 50 })
    // 后端返回 MyBatis-Plus Page 对象，数据在 .records 中
    const rawData = res.data?.records || res.data?.list || res.data || res
    notifications.value = Array.isArray(rawData) ? rawData : []
    // 统计未读数量
    unreadCount.value = notifications.value.filter(n => !n.isRead).length
  } catch (e) {
    console.error('Failed to load notifications:', e)
    notifications.value = []
  }

  // 监听 WebSocket 实时通知推送
  unlisten = wsOn('notification.new', (data) => {
    // 新通知到达时插入列表顶部
    notifications.value.unshift({
      ...data,
      isRead: false
    })
    unreadCount.value++
    ElMessage.info(`新通知: ${data.title}`)
  })
})

onUnmounted(() => {
  // 组件卸载时取消订阅，避免内存泄漏
  if (unlisten) unlisten()
  wsOff('notification.new')
})

async function markRead(n) {
  try {
    await notificationApi.markAsRead(n.id)
    n.isRead = true
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

async function markAllRead() {
  try {
    for (const n of notifications.value.filter(n => !n.isRead)) {
      await notificationApi.markAsRead(n.id)
      n.isRead = true
    }
    ElMessage.success('已全部标为已读')
  } catch (e) {
    ElMessage.error('操作失败')
  }
}
</script>
