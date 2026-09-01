<template>
  <el-dialog
    :model-value="modelValue"
    title="试卷统计分析"
    width="650px"
    destroy-on-close
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-if="statistics" class="stats-detail">
      <div class="stats-grid">
        <div class="stats-item">
          <span class="label">平均分</span>
          <span class="value primary">{{ statistics.avgScore || '--' }}</span>
        </div>
        <div class="stats-item">
          <span class="label">最高分</span>
          <span class="value success">{{ statistics.maxScore || '--' }}</span>
        </div>
        <div class="stats-item">
          <span class="label">最低分</span>
          <span class="value danger">{{ statistics.minScore || '--' }}</span>
        </div>
        <div class="stats-item">
          <span class="label">参考率</span>
          <span class="value warning">{{
            statistics.attendanceRate ? (statistics.attendanceRate * 100).toFixed(1) + '%' : '--'
          }}</span>
        </div>
        <div class="stats-item">
          <span class="label">及格率</span>
          <span class="value success">{{
            statistics.passRate ? (statistics.passRate * 100).toFixed(1) + '%' : '--'
          }}</span>
        </div>
        <div class="stats-item">
          <span class="label">作弊嫌疑数</span>
          <span class="value danger">{{ statistics.cheatingSuspects || 0 }}</span>
        </div>
      </div>

      <!-- 成绩分布图 -->
      <div class="chart-section">
        <div class="section-title">成绩分布</div>
        <v-chart :option="gradeChartOption" style="height: 250px" autoresize />
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 试卷统计对话框
 * <p>平均/最高/最低分、参考率/及格率、作弊嫌疑数 + 成绩分布柱状图。</p>
 */
defineProps({
  /** 弹窗显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 统计数据对象 */
  statistics: { type: Object, default: null },
  /** ECharts 分布图配置 */
  gradeChartOption: { type: Object, required: true },
})

defineEmits(['update:modelValue'])
</script>

<style scoped>
.stats-detail {
  padding: 0;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stats-item {
  background: var(--ac-bg);
  border-radius: var(--ac-radius-sm);
  padding: 18px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stats-item .label {
  font-size: 13px;
  color: var(--ac-text-muted);
}

.stats-item .value {
  font-size: 24px;
  font-weight: 700;
  color: var(--ac-text-primary);
}

.stats-item .value.primary {
  color: var(--ac-primary);
}
.stats-item .value.success {
  color: var(--ac-success);
}
.stats-item .value.warning {
  color: var(--ac-warning);
}
.stats-item .value.danger {
  color: var(--ac-danger);
}

.chart-section {
  background: var(--ac-bg);
  border-radius: var(--ac-radius-md);
  padding: 20px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 12px;
}

@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
