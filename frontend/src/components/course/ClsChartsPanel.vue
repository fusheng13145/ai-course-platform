<template>
  <div class="chart-section">
    <div class="chart-row">
      <div class="chart-card">
        <div class="chart-title">近14天学习活跃度趋势</div>
        <v-chart :option="activityTrendOption" style="height: 260px" autoresize />
      </div>
      <div class="chart-card">
        <div class="chart-title">学生参与度分布</div>
        <v-chart :option="engagementDistOption" style="height: 260px" autoresize />
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  activityTrendOption: { type: Object, default: null },
  engagementDistOption: { type: Object, default: null },
})
</script>

<style scoped>
.chart-section {
  margin-bottom: 22px;
}
.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.chart-card {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  padding: 16px;
}
.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 8px;
}

@media (max-width: 768px) {
  .chart-row {
    grid-template-columns: 1fr;
  }
}
</style>
