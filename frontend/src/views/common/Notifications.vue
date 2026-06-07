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
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import notificationApi from '@/api/notification'

const notifications = ref([])

onMounted(async () => {
  try {
    const res = await notificationApi.getNotificationList({ limit: 50 })
    notifications.value = res.data?.list || res.data || res || []
  } catch (e) {
    console.error('Failed to load notifications:', e)
  }
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
