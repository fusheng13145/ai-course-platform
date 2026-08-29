<template>
  <div v-loading="loading" class="content-card">
    <el-table :data="data" style="width: 100%">
      <el-table-column type="index" label="#" width="50" />
      <el-table-column prop="studentName" label="姓名" min-width="110" />
      <el-table-column prop="studentId" label="学号" width="120" />
      <el-table-column label="已提交/总作业" width="130" align="center">
        <template #default="{ row }">
          <span class="ratio">{{ row.submittedCount }} / {{ row.totalAssignments }}</span>
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
      <el-table-column label="平均成绩" width="100" align="center" sortable prop="avgScore">
        <template #default="{ row }">
          <span class="avg-score" :class="{ 'low-score': row.avgScore < 60 }">
            {{ row.avgScore }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="最高分" width="80" align="center">
        <template #default="{ row }">{{ row.maxScore }}</template>
      </el-table-column>
      <el-table-column label="最低分" width="80" align="center">
        <template #default="{ row }">{{ row.minScore }}</template>
      </el-table-column>
      <el-table-column label="提交状态" width="130">
        <template #default="{ row }">
          <el-tag
            :type="
              row.submissionRate === 100
                ? 'success'
                : row.submissionRate >= 60
                  ? 'warning'
                  : 'danger'
            "
            size="small"
            effect="plain"
          >
            {{
              row.submissionRate === 100
                ? '全部完成'
                : row.submissionRate >= 60
                  ? '部分完成'
                  : '较多缺失'
            }}
          </el-tag>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && data.length === 0" description="暂无学生数据" />
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
.avg-score {
  font-weight: 600;
  color: var(--ac-text-primary);
}
.low-score {
  color: var(--ac-danger, #ef4444);
}
.ratio {
  font-size: 13px;
  color: var(--ac-text-primary);
  font-weight: 500;
}
</style>
