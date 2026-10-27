<template>
  <div class="table-section animate-in d2">
    <div class="section-header">
      <span class="chart-title">各课程完成概况</span>
    </div>
    <div class="content-card table-card">
      <el-table v-loading="loading" :data="completionTable">
        <el-table-column prop="name" label="课程名称" min-width="140" />
        <el-table-column prop="studentCount" label="选课人数" width="90" align="center">
          <template #default="{ row }">{{ row.studentCount ?? '--' }}</template>
        </el-table-column>
        <el-table-column prop="avgProgress" label="平均进度" width="100" align="center">
          <template #default="{ row }">
            <span v-if="row.avgProgress != null" :style="{ color: monoScore(row.avgProgress) }">
              {{ row.avgProgress }}%
            </span>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column prop="completionRate" label="完成率" width="90" align="center">
          <template #default="{ row }"
            ><span v-if="row.completionRate != null">{{ row.completionRate }}%</span
            ><span v-else>--</span></template
          >
        </el-table-column>
        <el-table-column prop="pendingGrading" label="待批阅提交" width="110" align="center">
          <template #default="{ row }">{{ row.pendingGrading ?? '--' }}</template>
        </el-table-column>
      </el-table>
      <EmptyState
        v-if="!loading && completionTable.length === 0"
        title="暂无课程完成数据"
        icon="chart"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { monoScore } from '@/utils/chartTheme'
import EmptyState from '@/components/ui/EmptyState.vue'

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
</style>
