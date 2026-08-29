<template>
  <div class="table-section animate-in d2">
    <div class="section-header">
      <span class="chart-title">作业完成情况</span>
    </div>
    <div class="content-card table-card">
      <el-table v-loading="loading" :data="completionTable">
        <el-table-column prop="courseName" label="课程名称" min-width="140" />
        <el-table-column prop="taskName" label="任务/作业" min-width="140" />
        <el-table-column prop="submissionRate" label="提交率" width="90" align="center">
          <template #default="{ row }">
            <span :style="{ color: monoScore(row.submissionRate) }"
              >{{ row.submissionRate }}%</span
            >
          </template>
        </el-table-column>
        <el-table-column prop="avgScore" label="平均分" width="80" align="center" />
        <el-table-column prop="maxScore" label="最高分" width="80" align="center">
          <template #default="{ row }"
            ><span class="score-max">{{ row.maxScore }}</span></template
          >
        </el-table-column>
        <el-table-column prop="minScore" label="最低分" width="80" align="center">
          <template #default="{ row }"
            ><span class="score-min">{{ row.minScore }}</span></template
          >
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && completionTable.length === 0" description="暂无数据" />
    </div>
  </div>
</template>

<script setup>
import { monoScore } from '@/utils/chartTheme'

defineProps({
  loading: { type: Boolean, default: false },
  completionTable: { type: Array, default: () => [] },
})
</script>

<style scoped>
.table-section {
  margin-top: var(--ac-spacing-md);
}
.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ac-spacing-sm);
}
.section-header .chart-title {
  margin-bottom: 0;
}
.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  letter-spacing: -0.2px;
}
.table-card {
  padding: 0;
  overflow: hidden;
}
.score-max {
  color: var(--ac-success);
}
.score-min {
  color: var(--ac-danger);
}
</style>
