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
      <div v-if="spark.length" class="stat-spark">
        <Sparkline :values="spark" :width="66" :height="22" />
      </div>
    </div>
    <div class="stat-note">{{ metric.note || '' }}</div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import Icon from './Icon.vue'
import Sparkline from './Sparkline.vue'
import type { Metric } from '../nav'
import { SPARKS } from '../data/demo'
import type { Role } from '../nav'

const props = defineProps<{ metric: Metric; role: Role }>()
const spark = computed(() => SPARKS[props.role]?.[String(props.metric.label)] || [])
</script>
