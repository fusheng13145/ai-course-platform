<template>
  <div class="chart-section">
    <!-- 成绩分布图 -->
    <div class="l1-card">
      <div class="l1-head">
        <div class="l1-title">成绩分布</div>
      </div>
      <div class="l1-body">
        <v-chart :option="distributionOption" class="dist-chart" autoresize />
      </div>
    </div>

    <!-- 待批阅快捷入口 -->
    <div class="l1-card pending-card">
      <div class="l1-head">
        <div class="l1-title">待批阅任务</div>
      </div>
      <div class="l1-body">
        <div v-if="pendingTasks.length > 0" class="pending-list">
          <div
            v-for="task in pendingTasks.slice(0, 5)"
            :key="task.id"
            class="pending-item"
            @click="$emit('go-grading', task)"
          >
            <span class="pending-name">{{ task.taskName || task.title || '未命名任务' }}</span>
            <el-tag type="warning" size="small">{{ task.pendingCount || 0 }}份</el-tag>
          </div>
        </div>
        <el-empty v-else description="暂无待批阅任务" :image-size="60" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 教师成绩分布面板
 * <p>分数段柱状分布图 + 待批阅任务快捷入口列表。</p>
 */
defineProps({
  /** ECharts 分布图配置（由 composable 构建并更新） */
  distributionOption: { type: Object, required: true },
  /** 待批阅任务列表 */
  pendingTasks: { type: Array, required: true },
})

defineEmits(['go-grading'])
</script>

<style scoped>
.chart-section {
  display: flex;
  flex-direction: column;
  gap: var(--ac-spacing-md);
}

.pending-card {
  flex-shrink: 0;
}

.pending-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pending-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: var(--ac-bg);
  border-radius: var(--ac-radius-sm);
  cursor: pointer;
  transition: all var(--ac-transition-fast);
  border-left: 3px solid transparent;
}

.pending-item:hover {
  background: var(--ac-bg-hover);
  border-left-color: var(--ac-warning);
}

.pending-name {
  font-size: 13px;
  color: var(--ac-text-secondary);
  font-weight: 500;
}

.dist-chart {
  height: 320px;
}
</style>
