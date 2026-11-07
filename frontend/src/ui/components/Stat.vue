<template>
  <article class="stat" :class="metric.tone ? `stat-${metric.tone}` : ''">
    <div class="stat-top">
      <span class="stat-label">{{ metric.label }}</span>
      <Icon :name="metric.icon || 'chart'" />
    </div>
    <div class="stat-main">
      <div class="stat-value">
        {{ metric.value }}<small v-if="metric.unit">{{ metric.unit }}</small>
      </div>
    </div>
    <div class="stat-note">{{ metric.note || '' }}</div>
  </article>
</template>

<script setup lang="ts">
import Icon from './Icon.vue'
import type { Metric } from '../nav'
import type { Role } from '../nav'

defineProps<{ metric: Metric; role: Role }>()
</script>
