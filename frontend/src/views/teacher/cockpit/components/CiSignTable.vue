<template>
  <div>
    <div class="head-row">
      <span class="muted">课程签到记录 {{ data.length }} 条</span>
      <el-button type="primary" :icon="Plus" @click="$emit('open-sign')">发起签到</el-button>
    </div>
    <el-table :data="data" size="small" border :empty-text="emptyText">
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column label="状态" width="96" align="center">
        <template #default="{ row }"
          ><el-tag size="small" :type="statusType(row.status)" effect="light">{{
            statusText(row.status)
          }}</el-tag></template
        >
      </el-table-column>
      <el-table-column prop="type" label="方式" width="90" align="center">
        <template #default="{ row }">{{ row.type || '普通' }}</template>
      </el-table-column>
      <el-table-column label="发起时间" min-width="150">
        <template #default="{ row }">{{ row.createTime || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="240" align="center">
        <template #default="{ row }">
          <el-button
            link
            type="success"
            :icon="Promotion"
            :disabled="row.status === 'PUBLISHED' || row.status === 'CLOSED'"
            @click="$emit('publish', row)"
            >发布</el-button
          >
          <el-button
            link
            type="warning"
            :icon="SwitchButton"
            :disabled="row.status === 'CLOSED'"
            @click="$emit('close', row)"
            >结束</el-button
          >
          <el-button link type="primary" :icon="Document" @click="$emit('open-records', row)"
            >查看记录</el-button
          >
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup lang="ts">
import { Plus, Promotion, SwitchButton, Document } from '@element-plus/icons-vue'

defineProps({
  data: { type: Array, default: () => [] },
  /** 空态文案（失败时由页面传入「未能加载」文案，默认保留原语义） */
  emptyText: { type: String, default: '暂无签到' },
})
defineEmits(['open-sign', 'publish', 'close', 'open-records'])

function statusType(s) {
  if (s === 'PUBLISHED' || s === 'OPEN' || s === 'ONGOING') return 'success'
  if (s === 'CLOSED') return 'info'
  return 'warning'
}
function statusText(s) {
  if (s === 'PUBLISHED' || s === 'OPEN' || s === 'ONGOING') return '进行中'
  if (s === 'CLOSED') return '已结束'
  return '草稿'
}
</script>

<style scoped>
.head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--ac-spacing-md);
}
.head-row .muted {
  font-size: 13px;
  color: var(--ac-text-muted);
}
</style>
