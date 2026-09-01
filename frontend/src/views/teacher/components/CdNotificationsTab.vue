<template>
  <div class="tab-card">
    <div class="tab-toolbar">
      <el-button type="primary" plain @click="emit('send')"><Plus :size="14" /> 发送通知</el-button>
    </div>
    <el-table v-loading="notificationsLoading" :data="notifications">
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column prop="content" label="内容" min-width="280" show-overflow-tooltip />
      <el-table-column prop="createdAt" label="发送时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column width="80" align="right">
        <template #default="{ row }">
          <el-button text type="danger" size="small" @click="emit('delete', row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!notificationsLoading && !notifications.length" description="暂无通知" />
  </div>
</template>

<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue'

/**
 * 通知管理 Tab
 * <p>课程通知列表（标题/内容/发送时间）+ 发送/删除入口（经事件上抛）。</p>
 */
defineProps({
  notifications: { type: Array, default: () => [] },
  notificationsLoading: { type: Boolean, default: false },
  formatDateTime: { type: Function, required: true },
})

const emit = defineEmits(['send', 'delete'])
</script>

<style scoped>
.tab-card {
  padding: 8px 4px;
}
.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}
</style>
