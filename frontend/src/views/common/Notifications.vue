<template>
  <div>
    <div class="table-toolbar">
      <div class="toolbar-left"><el-button type="primary" @click="markAllRead">全部标为已读</el-button></div>
    </div>
    <div class="content-card">
      <div v-for="n in notifications" :key="n.id" :style="{ padding: '16px', borderBottom: '1px solid #f0f0f0', background: n.read ? '#fff' : '#f5f7fa' }">
        <div style="display:flex;justify-content:space-between;align-items:center">
          <div>
            <span v-if="!n.read" style="display:inline-block;width:8px;height:8px;background:#409eff;border-radius:50%;margin-right:8px"></span>
            <span style="font-weight:600">{{ n.title }}</span>
          </div>
          <div>
            <el-button v-if="!n.read" text type="primary" size="small" @click="markRead(n)">标为已读</el-button>
            <el-button text type="danger" size="small">删除</el-button>
          </div>
        </div>
        <p style="margin:8px 0 0;color:#606266;font-size:13px">{{ n.content }}</p>
        <p style="margin:4px 0 0;color:#c0c4cc;font-size:12px">{{ n.time }}</p>
      </div>
    </div>
  </div>
</template>
<script setup>
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const notifications = ref([
  { id: 1, title: "作业批改通知", content: "您的Python作业已被批改，得分85分", time: "2026-06-05 14:30", read: false },
  { id: 2, title: "新课程上线", content: "《深度学习入门》课程已上线，欢迎选修", time: "2026-06-04 10:00", read: false },
  { id: 3, title: "系统维护通知", content: "系统将于本周六凌晨2:00-4:00进行维护", time: "2026-06-03 09:00", read: true }
])

function markRead(n) { n.read = true }
function markAllRead() { notifications.value.forEach(n => n.read = true); ElMessage.success('已全部标为已读') }
</script>