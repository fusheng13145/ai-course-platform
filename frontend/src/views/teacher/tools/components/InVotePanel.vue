<template>
  <div>
    <div class="toolbar">
      <el-button type="primary" @click="$emit('open-vote')">
        <el-icon><Plus /></el-icon>发起投票
      </el-button>
      <el-button :disabled="voteLoading" @click="$emit('refresh')">
        <el-icon><Refresh /></el-icon>刷新
      </el-button>
    </div>

    <div v-loading="voteLoading" class="chart-card">
      <div class="chart-title">
        <el-icon><Histogram /></el-icon>投票列表
        <el-tag size="small" type="info" effect="light" round>{{ voteList.length }}</el-tag>
      </div>
      <el-table v-if="voteList.length" :data="voteList" size="small" empty-text="暂无投票">
        <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
        <el-table-column label="类型" width="90">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.multiple ? 'warning' : 'primary'"
              effect="light"
            >
              {{ row.multiple ? '多选' : '单选' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.status === 'CLOSED' ? 'info' : 'success'"
              effect="light"
            >
              {{ row.status === 'CLOSED' ? '已结束' : '进行中' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="$emit('open-result', row)"
              >结果</el-button
            >
            <el-button
              text
              type="warning"
              size="small"
              :disabled="row.status === 'CLOSED'"
              @click="$emit('close', row)"
              >结束</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else description="暂无投票，点击「发起投票」" :image-size="70" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { Plus, Refresh, Histogram } from '@element-plus/icons-vue'

defineProps({
  voteList: { type: Array, default: () => [] },
  voteLoading: { type: Boolean, default: false },
})
defineEmits(['open-vote', 'refresh', 'open-result', 'close'])
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
