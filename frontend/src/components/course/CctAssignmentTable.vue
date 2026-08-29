<template>
  <div v-loading="loading" class="content-card">
    <el-table :data="data" style="width: 100%">
      <el-table-column prop="title" label="作业标题" min-width="200" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag
            :type="
              row.status === 'ACTIVE'
                ? 'success'
                : row.status === 'CLOSED'
                  ? 'danger'
                  : 'info'
            "
            size="small"
          >
            {{
              row.status === 'ACTIVE'
                ? '进行中'
                : row.status === 'CLOSED'
                  ? '已截止'
                  : '未发布'
            }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="已提交/应提交" width="130" align="center">
        <template #default="{ row }">
          <span class="ratio">{{ row.submittedCount }} / {{ row.totalStudents }}</span>
        </template>
      </el-table-column>
      <el-table-column label="提交率" width="150">
        <template #default="{ row }">
          <div class="completion-cell">
            <el-progress
              :percentage="row.submissionRate"
              :stroke-width="7"
              :show-text="false"
              style="width: 80px"
              :color="getRateColor(row.submissionRate)"
              :status="row.submissionRate === 100 ? 'success' : undefined"
            />
            <span class="completion-text">{{ row.submissionRate }}%</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="未提交" width="80" align="center">
        <template #default="{ row }">
          <span class="pending-count" :class="{ 'zero-pending': row.unsubmittedCount === 0 }">
            {{ row.unsubmittedCount }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="已批改" width="80" align="center">
        <template #default="{ row }">
          <span>{{ row.gradedCount }} / {{ row.submittedCount }}</span>
        </template>
      </el-table-column>
      <el-table-column label="平均分" width="90" align="center">
        <template #default="{ row }">
          <span class="avg-score">{{ row.avgScore }}</span>
        </template>
      </el-table-column>
      <el-table-column label="截止时间" width="155">
        <template #default="{ row }">
          <span class="time-text">{{ formatDate(row.dueDate) }}</span>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && data.length === 0" description="暂无作业" />
  </div>
</template>

<script setup>
defineProps({
  loading: { type: Boolean, default: false },
  data: { type: Array, default: () => [] },
})

function getRateColor(rate) {
  if (rate >= 90) return '#10b981'
  if (rate >= 60) return '#f59e0b'
  return '#ef4444'
}
function formatDate(d) {
  return d ? new Date(d).toLocaleString('zh-CN') : '-'
}
</script>

<style scoped>
.content-card {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  padding: 14px;
}
.completion-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.completion-text {
  font-size: 12px;
  color: var(--ac-text-secondary);
  min-width: 32px;
}
.pending-count {
  font-weight: 600;
  color: var(--ac-danger, #ef4444);
}
.zero-pending {
  color: var(--ac-success, #10b981);
}
.avg-score {
  font-weight: 600;
  color: var(--ac-text-primary);
}
.ratio {
  font-size: 13px;
  color: var(--ac-text-primary);
  font-weight: 500;
}
.time-text {
  font-size: 12.5px;
  color: var(--ac-text-secondary);
}
</style>
