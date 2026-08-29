<template>
  <div class="stat-grid animate-in">
    <StatCard
      label="知识点总数"
      :value="overview.totalPoints || 0"
      icon="tag"
      tone="var(--accent-soft)"
      tone-color="var(--accent)"
    />
    <StatCard
      label="关系总数"
      :value="overview.totalRelations || 0"
      icon="link"
      tone="var(--ok-soft)"
      tone-color="var(--ok)"
    />
    <StatCard
      label="核心知识点"
      :value="overview.corePoints || 0"
      icon="star"
      tone="var(--warn-soft)"
      tone-color="var(--warn)"
    />
    <StatCard
      label="覆盖度"
      :value="overview.coverageRate ? pct(overview.coverageRate) : '0%'"
      icon="target"
      tone="var(--info-soft)"
      tone-color="var(--info)"
    />
  </div>
</template>

<script setup>
import StatCard from '@/components/ui/StatCard.vue'

/**
 * 知识图谱概览统计
 * <p>知识点总数 / 关系总数 / 核心知识点 / 覆盖度四项指标。</p>
 */
defineProps({
  overview: { type: Object, default: () => ({}) },
  pct: { type: Function, required: true },
})
</script>
