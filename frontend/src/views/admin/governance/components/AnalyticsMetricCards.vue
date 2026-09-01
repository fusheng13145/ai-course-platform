<template>
  <div class="stat-grid animate-in d1">
    <StatCard
      v-for="(metric, index) in coreMetrics"
      :key="index"
      :label="metric.label"
      :value="metric.value"
      :icon="metric.icon"
      :tone="metric.tone"
      :tone-color="metric.toneColor"
      :foot="metricFoot(metric)"
    />
  </div>
</template>

<script setup lang="ts">
import StatCard from '@/components/ui/StatCard.vue'

/**
 * 数据分析核心指标卡
 * <p>渲染五张指标卡（总用户/总课程/日活/完成率/增长率），
 * 数据与脚注（涨跌徽标）由父组件（useAnalytics）注入。</p>
 */
defineProps({
  /** 指标数组（label/value/change/icon/tone/toneColor） */
  coreMetrics: { type: Array, required: true },
  /** 指标脚注格式化函数 */
  metricFoot: { type: Function, required: true },
})
</script>
