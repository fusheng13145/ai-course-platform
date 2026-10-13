<template>
  <div class="side-panel">
    <div class="chart-card">
      <div class="chart-title">成绩分布</div>
      <v-chart :option="distOption" style="height: 240px" autoresize />
    </div>
    <div class="chart-card pending-card">
      <div class="chart-title">待批阅任务</div>
      <div v-if="pendingTasks.length > 0" class="pending-list">
        <div v-for="t in pendingTasks.slice(0, 5)" :key="t.id" class="pending-item">
          <span>{{ t.taskName || t.title || '未命名' }}</span>
          <el-tag type="warning" size="small">
            {{ t.pendingCount == null ? '待批阅' : `${t.pendingCount}份` }}
          </el-tag>
        </div>
      </div>
      <el-empty
        v-else
        :description="
          pendingLoading
            ? '正在加载待批阅任务'
            : pendingUnavailable
              ? '待批阅数据暂不可用'
              : '暂无待批阅'
        "
        :image-size="50"
      />
    </div>
  </div>
</template>

<script setup>
defineProps({
  distOption: { type: Object, default: () => ({}) },
  pendingTasks: { type: Array, default: () => [] },
  pendingLoading: { type: Boolean, default: false },
  pendingUnavailable: { type: Boolean, default: false },
})
</script>

<style scoped>
.side-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.chart-card {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  padding: 14px;
}
.pending-card {
  flex-shrink: 0;
}
.chart-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 10px;
}
.pending-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.pending-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--ac-bg);
  border-radius: var(--ac-radius-sm);
  font-size: 12.5px;
  color: var(--ac-text-secondary);
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  border-left: 3px solid transparent;
}
.pending-item:hover {
  background: var(--ac-bg-hover);
  border-left-color: var(--ac-warning);
}
</style>
