<template>
  <div class="content-card">
    <div class="card-title">
      <el-icon><Sunny /></el-icon>学习行为热力图（近 30 天）
    </div>
    <p class="sub-label">按星期 × 时段统计平台学习活跃度（记录数）</p>
    <div class="heatmap">
      <div class="heat-col-head"></div>
      <div v-for="slot in timeSlots" :key="slot" class="heat-col-head">{{ slot }}</div>
      <template v-for="d in dayOrder" :key="d">
        <div class="heat-row-head">{{ dayLabel(d) }}</div>
        <div
          v-for="slot in [0, 1, 2, 3]"
          :key="slot"
          class="heat-cell"
          :style="cellStyle(d, slot)"
          :title="`${dayLabel(d)} ${timeSlots[slot]}：${heatValue(d, slot)} 条`"
        ></div>
      </template>
    </div>
    <div class="heat-legend">
      <span>低</span>
      <span v-for="n in 5" :key="n" class="legend-cell" :style="legendStyle(n - 1)"></span>
      <span>高</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import { Sunny } from '@element-plus/icons-vue'

/**
 * 教学监管 — 学习行为热力图卡
 * <p>星期 × 时段活跃度矩阵（色阶映射记录量）。</p>
 */
defineProps({
  /** 时段标签数组 */
  timeSlots: { type: Array as PropType<(string | number)[]>, required: true },
  /** 星期顺序（MON..SAT, SUN） */
  dayOrder: { type: Array as PropType<string[]>, required: true },
  /** 星期标签函数 */
  dayLabel: { type: Function, required: true },
  /** 单元样式（色阶）函数 */
  cellStyle: { type: Function, required: true },
  /** 单元值函数 */
  heatValue: { type: Function, required: true },
  /** 图例色块样式函数 */
  legendStyle: { type: Function, required: true },
})
</script>

<style scoped>
.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 18px;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 14px;
}
.card-title .el-icon {
  color: var(--ac-primary, var(--info));
}
.sub-label {
  font-size: 13px;
  color: var(--ac-text-muted);
  margin-bottom: 10px;
}

.heatmap {
  display: grid;
  grid-template-columns: 52px repeat(4, 1fr);
  gap: 6px;
  margin-top: 6px;
}
.heat-col-head {
  font-size: 11px;
  color: var(--ac-text-muted);
  text-align: center;
  padding-bottom: 2px;
}
.heat-row-head {
  font-size: 12px;
  color: var(--ac-text-secondary, var(--ink-2));
  display: flex;
  align-items: center;
}
.heat-cell {
  height: 38px;
  border-radius: 6px;
  transition: transform 0.15s ease;
}
.heat-cell:hover {
  transform: scale(1.06);
}
.heat-legend {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 12px;
  font-size: 11px;
  color: var(--ac-text-muted);
}
.legend-cell {
  width: 20px;
  height: 12px;
  border-radius: 3px;
}
</style>
