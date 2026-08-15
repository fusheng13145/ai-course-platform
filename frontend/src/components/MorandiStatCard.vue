<template>
  <div class="morandi-stat-card" :class="[`morandi-stat-card--${color}`]">
    <div class="stat-card__icon">
      <el-icon :size="24"><component :is="icon" /></el-icon>
    </div>
    <div class="stat-card__body">
      <div class="stat-card__title">{{ title }}</div>
      <div class="stat-card__value">{{ value }}</div>
      <div class="stat-card__footer">
        <span v-if="subtitle" class="stat-card__subtitle">{{ subtitle }}</span>
        <span
          v-if="trend !== undefined && trend !== null"
          class="stat-card__trend"
          :class="{ 'is-up': trend > 0, 'is-down': trend < 0 }"
        >
          <el-icon :size="12"><Top v-if="trend > 0" /><Bottom v-else /></el-icon>
          {{ Math.abs(trend) }}%
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Top, Bottom } from '@element-plus/icons-vue'

defineProps({
  icon: { type: [String, Object], default: 'DataLine' },
  title: { type: String, default: '' },
  value: { type: [String, Number], default: '' },
  subtitle: { type: String, default: '' },
  trend: { type: Number, default: undefined },
  color: {
    type: String,
    default: 'default',
    validator: (v) => ['default', 'success', 'warning', 'danger'].includes(v),
  },
})
</script>

<style scoped>
.morandi-stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px;
  background: var(--ac-bg-elevated);
  border-radius: 12px;
  border: 1px solid var(--ac-border);
  transition:
    box-shadow 0.3s ease,
    transform 0.2s ease;
}

.morandi-stat-card:hover {
  box-shadow: 0 4px 16px rgba(122, 139, 165, 0.1);
  transform: translateY(-2px);
}

.stat-card__icon {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-card__body {
  flex: 1;
  min-width: 0;
}

.stat-card__title {
  font-size: 13px;
  color: var(--ac-text-muted);
  margin-bottom: 6px;
  line-height: 1.4;
}

.stat-card__value {
  font-size: 26px;
  font-weight: 700;
  color: var(--ac-text-primary);
  line-height: 1.2;
  letter-spacing: -0.5px;
}

.stat-card__footer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}

.stat-card__subtitle {
  font-size: 12px;
  color: var(--ac-text-muted);
}

.stat-card__trend {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  font-weight: 500;
  padding: 2px 6px;
  border-radius: 4px;
}

/* 颜色变体 */
.morandi-stat-card--default .stat-card__icon {
  background: rgba(122, 139, 165, 0.12);
  color: #7a8ba5;
}
.morandi-stat-card--default .stat-card__value {
  color: #7a8ba5;
}

.morandi-stat-card--success .stat-card__icon {
  background: rgba(139, 168, 136, 0.12);
  color: #8ba888;
}
.morandi-stat-card--success .stat-card__value {
  color: #8ba888;
}
.morandi-stat-card--success .stat-card__trend.is-up {
  color: #8ba888;
  background: rgba(139, 168, 136, 0.1);
}

.morandi-stat-card--warning .stat-card__icon {
  background: rgba(203, 181, 138, 0.12);
  color: #cbb58a;
}
.morandi-stat-card--warning .stat-card__value {
  color: #cbb58a;
}
.morandi-stat-card--warning .stat-card__trend.is-down {
  color: #cbb58a;
  background: rgba(203, 181, 138, 0.1);
}

.morandi-stat-card--danger .stat-card__icon {
  background: rgba(196, 128, 128, 0.12);
  color: #c48080;
}
.morandi-stat-card--danger .stat-card__value {
  color: #c48080;
}
.morandi-stat-card--danger .stat-card__trend.is-down {
  color: #c48080;
  background: rgba(196, 128, 128, 0.1);
}
</style>
