<template>
  <div class="stat-grid animate-in d1">
    <StatCard
      v-for="(s, i) in stats"
      :key="s.label"
      :label="s.label"
      :value="s.value"
      :icon="s.icon"
      :tone="tones[i % tones.length].tone"
      :tone-color="tones[i % tones.length].toneColor"
    />
  </div>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import StatCard from '@/components/ui/StatCard.vue'

/**
 * 学习日历页 — 顶部统计卡区（4 项：学习天数/总时长/完成课程/最高连续）。
 * <p>拆分子组件：与日历面板解耦，仅消费 stats 数组做纯展示。</p>
 */
defineProps({
  stats: {
    type: Array as PropType<{ label: string; value: string | number; icon?: string }[]>,
    default: () => [],
  },
})

// 每个统计卡的色调（与拆分前页面硬编码一致）
const tones = [
  { tone: 'var(--accent-soft)', toneColor: 'var(--accent)' },
  { tone: 'var(--info-soft)', toneColor: 'var(--info)' },
  { tone: 'var(--ok-soft)', toneColor: 'var(--ok)' },
  { tone: 'var(--warn-soft)', toneColor: 'var(--warn)' },
]
</script>
