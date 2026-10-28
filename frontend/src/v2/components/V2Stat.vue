<template>
  <article class="stat" :class="metric.tone ? `stat-${metric.tone}` : ''">
    <div class="stat-top">
      <span class="stat-label">{{ metric.label }}</span>
      <V2Icon :name="metric.icon || 'chart'" />
    </div>
    <div class="stat-main">
      <div class="stat-value">
        {{ metric.value }}<small v-if="metric.unit">{{ metric.unit }}</small>
      </div>
      <div v-if="spark.length" class="stat-spark">
        <V2Sparkline :values="spark" :width="66" :height="22" />
      </div>
    </div>
    <div class="stat-note">{{ metric.note || '' }}</div>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import V2Icon from './V2Icon.vue'
import V2Sparkline from './V2Sparkline.vue'
import type { V2Metric } from '../nav'
import { SPARKS } from '../data/demo'
import type { V2Role } from '../nav'

const props = defineProps<{ metric: V2Metric; role: V2Role }>()
const spark = computed(() => SPARKS[props.role]?.[String(props.metric.label)] || [])
</script>
