<template>
  <div class="stats-row">
    <div class="stat-card">
      <div class="stat-icon stat-icon--blue"><User :size="20" /></div>
      <div class="stat-info">
        <span class="stat-val">{{ overview.activeStudents ?? '-' }}</span>
        <span class="stat-lbl">活跃学生</span>
      </div>
    </div>
    <div class="stat-card">
      <div class="stat-icon stat-icon--green">
        <svg viewBox="0 0 24 24" fill="none" width="20" height="20">
          <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" />
          <path
            d="M12 6v6l3.5 2"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </div>
      <div class="stat-info">
        <span class="stat-val">{{ overview.avgOnlineHours ?? '-' }}h</span>
        <span class="stat-lbl">人均在线时长</span>
      </div>
    </div>
    <div class="stat-card">
      <div class="stat-icon stat-icon--orange">
        <svg viewBox="0 0 24 24" fill="none" width="20" height="20">
          <path
            d="M18 20V10M12 20V4M6 20v-6"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </div>
      <div class="stat-info">
        <span class="stat-val">{{ overview.avgEngagement ?? '-' }}%</span>
        <span class="stat-lbl">平均参与度</span>
      </div>
    </div>
    <div class="stat-card">
      <div class="stat-icon stat-icon--purple">
        <svg viewBox="0 0 24 24" fill="none" width="20" height="20">
          <path
            d="M9 12l2 2 4-4"
            stroke="currentColor"
            stroke-width="2"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
          <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" />
        </svg>
      </div>
      <div class="stat-info">
        <span class="stat-val">{{ overview.avgCompletion ?? '-' }}%</span>
        <span class="stat-lbl">平均学习完成率</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { User } from '@element-plus/icons-vue'

defineProps({
  overview: { type: Object, default: () => ({}) },
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
