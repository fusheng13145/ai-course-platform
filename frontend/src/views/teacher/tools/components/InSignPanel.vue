<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="$emit('open-sign')">
        <el-icon><Plus /></el-icon>发起签到
      </el-button>
      <el-button :disabled="signLoading" @click="$emit('refresh')">
        <el-icon><Refresh /></el-icon>刷新
      </el-button>
    </div>

    <div v-loading="signLoading" class="chart-card">
      <div class="chart-title">
        <el-icon><Pointer /></el-icon>签到记录
        <el-tag size="small" type="info" effect="light" round>{{ signList.length }}</el-tag>
      </div>
      <el-table v-if="signList.length" :data="signList" size="small" empty-text="暂无签到">
        <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="signStatusType(row.status)" effect="light">{{
              signStatusText(row.status)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="signedCount" label="已签" width="80" align="center" />
        <el-table-column prop="totalCount" label="应到" width="80" align="center" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="$emit('open-records', row)"
              >记录</el-button
            >
            <el-button
              text
              type="success"
              size="small"
              :disabled="row.status !== 'DRAFT'"
              @click="$emit('publish', row)"
              >发布</el-button
            >
            <el-button
              text
              type="warning"
              size="small"
              :disabled="row.status === 'CLOSED'"
              @click="$emit('close', row)"
              >关闭</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else :description="emptyText" :image-size="70" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { Plus, Refresh, Pointer } from '@element-plus/icons-vue'

defineProps({
  signList: { type: Array, default: () => [] },
  signLoading: { type: Boolean, default: false },
  /** 空态文案（失败时由页面传入「未能加载」文案，默认保留原语义） */
  emptyText: { type: String, default: '暂无签到，点击「发起签到」' },
})
defineEmits(['open-sign', 'refresh', 'open-records', 'publish', 'close'])

function signStatusType(s) {
  return s === 'PUBLISHED' ? 'success' : s === 'CLOSED' ? 'info' : 'warning'
}
function signStatusText(s) {
  return s === 'PUBLISHED' ? '进行中' : s === 'CLOSED' ? '已关闭' : '草稿'
}
</script>

<style scoped>
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: var(--ac-spacing-md);
}
.chart-card {
  padding: var(--ac-spacing-lg);
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
}
.chart-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-md);
}
</style>
