<template>
  <div class="analysis-panel">
    <!-- 成绩分布图 -->
    <div class="panel-card">
      <div class="panel-title">成绩分布</div>
      <v-chart :option="distributionOption" class="chart-sm" autoresize />
    </div>

    <!-- 各课程平均分对比 -->
    <div class="panel-card">
      <div class="panel-title">各课程平均分对比</div>
      <v-chart :option="courseCompareOption" class="chart-md" autoresize />
    </div>

    <!-- 异常成绩预警 -->
    <div class="panel-card abnormal-card">
      <div class="panel-title">
        <el-icon><Warning /></el-icon>异常成绩预警
      </div>
      <div v-if="abnormalGrades.length > 0" class="abnormal-list">
        <div
          v-for="(item, idx) in abnormalGrades.slice(0, 5)"
          :key="idx"
          class="abnormal-item"
        >
          <div class="abnormal-info">
            <span class="name">{{ item.studentName }}</span>
            <span class="score" :class="item.score < 60 ? 'low' : 'high'"
              >{{ item.score }}分</span
            >
          </div>
          <span class="course">{{ item.courseName }}</span>
        </div>
      </div>
      <el-empty v-else description="暂无异常成绩" :image-size="50">
        <template #image><div class="empty-deco">⚠️</div></template>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
import { Warning } from '@element-plus/icons-vue'

/**
 * 成绩管理统计分析面板
 * <p>成绩分布（五档柱状图）、各课程平均分对比（横向条形图）、
 * 异常成绩预警列表（取前 5 条，低于 60 分标红）。</p>
 */
defineProps({
  /** 成绩分布图配置 */
  distributionOption: { type: Object, required: true },
  /** 课程平均分对比图配置 */
  courseCompareOption: { type: Object, required: true },
  /** 异常成绩列表 */
  abnormalGrades: { type: Array, default: () => [] },
})
</script>

<style scoped>
.chart-sm {
  height: 220px;
}
.chart-md {
  height: 260px;
}

.analysis-panel {
  display: flex;
  flex-direction: column;
  gap: var(--ac-spacing-md);
}
.panel-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 18px;
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 异常成绩卡片 */
.abnormal-card .panel-title {
  color: var(--accent);
}
.abnormal-list {
  max-height: 200px;
  overflow-y: auto;
}
.abnormal-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--ac-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}
.abnormal-item:last-child {
  border-bottom: none;
}
.abnormal-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.abnormal-info .name {
  font-weight: 500;
  color: var(--ac-text-primary);
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.abnormal-info .score {
  font-weight: 700;
  font-size: 13px;
  padding: 2px 8px;
  border-radius: 4px;
}
.abnormal-info .score.low {
  color: var(--accent);
  background: var(--accent-soft);
}
.abnormal-info .score.high {
  color: var(--warn);
  background: var(--warn-soft);
}
.abnormal-item .course {
  font-size: 12px;
  color: var(--ac-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 120px;
}

.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}

@media (max-width: 1024px) {
  .analysis-panel {
    order: -1;
    display: grid;
    grid-template-columns: repeat(2, 1fr);
  }
}
@media (max-width: 768px) {
  .analysis-panel {
    grid-template-columns: 1fr;
  }
}
</style>
