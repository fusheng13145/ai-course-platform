<template>
  <div class="chart-section">
    <!-- 成绩分布图 -->
    <div class="l1-card">
      <div class="l1-head">
        <div class="l1-title">成绩分布</div>
      </div>
      <div v-loading="loading" class="l1-body">
        <v-chart
          v-if="hasDistribution"
          :option="distributionOption"
          class="dist-chart"
          autoresize
        />
        <EmptyState v-else-if="!loading" title="暂无可统计的总评成绩" icon="chart" size="compact" />
      </div>
    </div>

    <!-- 待批阅快捷入口 -->
    <div class="l1-card pending-card">
      <div class="l1-head">
        <div class="l1-title">待批阅任务</div>
      </div>
      <div class="l1-body">
        <div v-if="pendingTasks.length > 0" class="pending-list">
          <button
            v-for="task in pendingTasks"
            :key="task.id"
            type="button"
            class="pending-item"
            @click="$emit('go-grading', task)"
          >
            <span class="pending-name">{{ task.taskName || task.title || '未命名任务' }}</span>
            <el-tag type="warning" size="small">
              {{ task.pendingCount == null ? '待批阅' : `${task.pendingCount}份` }}
            </el-tag>
          </button>
        </div>
        <EmptyState
          v-else
          :title="
            loading
              ? '正在加载待批阅任务'
              : pendingUnavailable
                ? '待批阅数据暂不可用'
                : '暂无待批阅任务'
          "
          icon="list"
          size="compact"
        />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import EmptyState from '@/components/ui/EmptyState.vue'

/** 待批阅任务行（taskName/title 存在历史分叉，故均可选） */
interface PendingTaskRow {
  id?: number | string
  taskName?: string
  title?: string
  pendingCount?: number | null
}

/**
 * 教师成绩分布面板
 * <p>分数段柱状分布图 + 待批阅任务快捷入口列表。</p>
 */
defineProps({
  /** ECharts 分布图配置（由 composable 构建并更新） */
  distributionOption: { type: Object, required: true },
  hasDistribution: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
  pendingUnavailable: { type: Boolean, default: false },
  /** 待批阅任务列表 */
  pendingTasks: { type: Array as PropType<PendingTaskRow[]>, required: true },
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
  max-height: 260px;
  overflow-y: auto;
  padding-right: 4px;
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
  border-top: 0;
  border-right: 0;
  border-bottom: 0;
  width: 100%;
  text-align: left;
  font: inherit;
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
