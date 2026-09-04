<template>
  <div class="stats-row">
    <div class="stat-card">
      <div class="stat-icon stat-icon--blue">
        <svg viewBox="0 0 24 24" fill="none" width="20" height="20">
          <path
            d="M22 12A10 10 0 1 1 12 2"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
          />
          <path
            d="M22 12A10 10 0 1 1 2 12"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            opacity="0.3"
          />
          <path
            d="M12 6v6l4 2"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </div>
      <div class="stat-info">
        <span class="stat-val">{{ overallProgress }}%</span>
        <span class="stat-lbl">总体进度</span>
      </div>
    </div>
    <div class="stat-card">
      <div class="stat-icon stat-icon--green">
        <Collection :size="20" />
      </div>
      <div class="stat-info">
        <span class="stat-val">{{ completedChapters }}/{{ totalChapters }}</span>
        <span class="stat-lbl">已完成章节</span>
      </div>
    </div>
    <div class="stat-card">
      <div class="stat-icon stat-icon--orange">
        <Document :size="20" />
      </div>
      <div class="stat-info">
        <span class="stat-val">{{ totalTasks }}</span>
        <span class="stat-lbl">任务点总数</span>
      </div>
    </div>
    <div class="stat-card">
      <div class="stat-icon stat-icon--purple">
        <svg viewBox="0 0 24 24" fill="none" width="20" height="20">
          <path
            d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
          />
          <rect x="8" y="2" width="8" height="4" rx="1" stroke="currentColor" stroke-width="2" />
          <path
            d="M9 14l2 2 4-4"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </div>
      <div class="stat-info">
        <span class="stat-val">{{ avgSubmissionRate }}%</span>
        <span class="stat-lbl">平均提交率</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Collection, Document } from '@element-plus/icons-vue'

defineProps({
  overallProgress: { type: [Number, String], default: 0 },
  completedChapters: { type: [Number, String], default: 0 },
  totalChapters: { type: [Number, String], default: 0 },
  totalTasks: { type: [Number, String], default: 0 },
  avgSubmissionRate: { type: [Number, String], default: 0 },
})
</script>

<style scoped>
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 22px;
}
.stat-card {
  background: var(--ac-bg);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-sm);
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  transition: box-shadow 0.2s;
}
.stat-card:hover {
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
}
.stat-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
}
.stat-icon--blue {
  background: linear-gradient(135deg, #3b82f6, #60a5fa);
}
.stat-icon--green {
  background: linear-gradient(135deg, #10b981, #34d399);
}
.stat-icon--orange {
  background: linear-gradient(135deg, #f59e0b, #fbbf24);
}
.stat-icon--purple {
  background: linear-gradient(135deg, #8b5cf6, #a78bfa);
}
.stat-info {
  flex: 1;
  min-width: 0;
}
.stat-val {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: var(--ac-text-primary);
  line-height: 1.2;
}
.stat-lbl {
  display: block;
  font-size: 12px;
  color: var(--ac-text-secondary);
  margin-top: 2px;
}
@media (max-width: 768px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
