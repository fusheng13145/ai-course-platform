<template>
  <div class="grid-2">
    <div class="l1-card">
      <div class="l1-head">
        <div class="l1-title">近 14 天教学活动趋势</div>
      </div>
      <div class="l1-body">
        <v-chart :option="trendOption" class="trend-chart" autoresize />
      </div>
    </div>
    <div class="l1-card">
      <div class="l1-head">
        <div class="l1-title">
          学习风险学生
          <el-tag size="small" type="danger" effect="light" round>{{ atRisk.length }}</el-tag>
        </div>
      </div>
      <div class="l1-body">
        <el-table :data="atRisk" size="small" max-height="300" empty-text="暂无风险学生">
          <el-table-column prop="studentName" label="学生" min-width="90" />
          <el-table-column label="进度" width="72">
            <template #default="{ row }">{{ pct(row.progress) }}</template>
          </el-table-column>
          <el-table-column label="成绩" width="64">
            <template #default="{ row }">{{ row.finalScore ?? '--' }}</template>
          </el-table-column>
          <el-table-column label="等级" width="80">
            <template #default="{ row }">
              <el-tag
                size="small"
                :type="row.level === 'HIGH' ? 'danger' : 'warning'"
                effect="light"
              >
                {{ row.level === 'HIGH' ? '高危' : '关注' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps({
  trendOption: { type: Object, default: () => ({}) },
  atRisk: { type: Array, default: () => [] },
})

function pct(v) {
  if (v == null) return '--'
  return typeof v === 'number' && v <= 1.0001 && v > 0 ? Math.round(v * 100) + '%' : v + '%'
}
</script>

<style scoped>
.grid-2 {
  display: grid;
  grid-template-columns: 1.4fr 1fr;
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-md);
}
.trend-chart {
  height: 300px;
}
@media (max-width: 1100px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>
