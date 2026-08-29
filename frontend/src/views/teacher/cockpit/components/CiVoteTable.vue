<template>
  <div>
    <div class="head-row">
      <span class="muted">课程投票记录 {{ data.length }} 条</span>
      <el-button type="primary" :icon="Plus" @click="$emit('open-vote')">发起投票</el-button>
    </div>
    <el-table :data="data" size="small" border empty-text="暂无投票">
      <el-table-column prop="title" label="标题" min-width="180" />
      <el-table-column label="类型" width="100" align="center">
        <template #default="{ row }"
          ><el-tag size="small" :type="row.multiple ? 'warning' : 'info'" effect="light">{{
            row.multiple ? '多选' : '单选'
          }}</el-tag></template
        >
      </el-table-column>
      <el-table-column label="状态" width="96" align="center">
        <template #default="{ row }"
          ><el-tag size="small" :type="statusType(row.status)" effect="light">{{
            statusText(row.status)
          }}</el-tag></template
        >
      </el-table-column>
      <el-table-column label="发起时间" min-width="150">
        <template #default="{ row }">{{ row.createTime || '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="200" align="center">
        <template #default="{ row }">
          <el-button
            link
            type="warning"
            :icon="SwitchButton"
            :disabled="row.status === 'CLOSED'"
            @click="$emit('close', row)"
            >结束</el-button
          >
          <el-button link type="primary" :icon="DataAnalysis" @click="$emit('open-result', row)"
            >查看结果</el-button
          >
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { Plus, SwitchButton, DataAnalysis } from '@element-plus/icons-vue'

defineProps({
  data: { type: Array, default: () => [] },
})
defineEmits(['open-vote', 'close', 'open-result'])

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
