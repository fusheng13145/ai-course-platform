<template>
  <div class="progress-section">
    <div class="section-header">
      <h3 class="section-title">教学进度总览</h3>
    </div>
    <div class="progress-overview">
      <div class="ring-chart-wrapper">
        <div class="ring-chart">
          <svg viewBox="0 0 120 120" width="120" height="120">
            <circle
              cx="60"
              cy="60"
              r="52"
              fill="none"
              stroke="var(--ac-border, var(--line))"
              stroke-width="10"
            />
            <circle
              cx="60"
              cy="60"
              r="52"
              fill="none"
              stroke="var(--ac-primary, var(--info))"
              stroke-width="10"
              stroke-linecap="round"
              :stroke-dasharray="circumference"
              :stroke-dashoffset="dashOffset"
              transform="rotate(-90 60 60)"
              style="transition: stroke-dashoffset 0.8s ease"
            />
          </svg>
          <div class="ring-center">
            <span class="ring-percent">{{ overallProgress }}%</span>
            <span class="ring-sublabel">已完成</span>
          </div>
        </div>
        <div class="ring-legend">
          <div class="legend-item">
            <span class="legend-dot legend-dot--done"></span>
            <span>已完成章节</span>
            <strong>{{ completedChapters }}</strong>
          </div>
          <div class="legend-item">
            <span class="legend-dot legend-dot--pending"></span>
            <span>未完成章节</span>
            <strong>{{ totalChapters - completedChapters }}</strong>
          </div>
          <div class="legend-item">
            <span class="legend-dot legend-dot--total"></span>
            <span>计划总章节</span>
            <strong>{{ totalChapters }}</strong>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps({
  overallProgress: { type: [Number, String], default: 0 },
  completedChapters: { type: [Number, String], default: 0 },
  totalChapters: { type: [Number, String], default: 0 },
  circumference: { type: Number, default: 0 },
  dashOffset: { type: Number, default: 0 },
})
</script>

<style scoped>
.progress-section {
  margin-bottom: 22px;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0;
}
.progress-overview {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  padding: 20px 24px;
}
.ring-chart-wrapper {
  display: flex;
  align-items: center;
  gap: 40px;
}
.ring-chart {
  position: relative;
  flex-shrink: 0;
}
.ring-center {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.ring-percent {
  font-size: 26px;
  font-weight: 700;
  color: var(--ac-text-primary);
  line-height: 1;
}
.ring-sublabel {
  font-size: 11px;
  color: var(--ac-text-secondary);
  margin-top: 3px;
}
.ring-legend {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--ac-text-secondary);
}
.legend-item strong {
  margin-left: auto;
  color: var(--ac-text-primary);
  min-width: 24px;
  text-align: right;
}
.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}
.legend-dot--done {
  background: var(--ac-primary, var(--info));
}
.legend-dot--pending {
  background: var(--ac-border, var(--line));
}
.legend-dot--total {
  background: var(--ac-text-tertiary, var(--ink-3));
}
@media (max-width: 768px) {
  .ring-chart-wrapper {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
