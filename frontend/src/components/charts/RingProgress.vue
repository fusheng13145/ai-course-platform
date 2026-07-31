<script setup>
import { computed } from 'vue'
const props = defineProps({ pct: { type: Number, required: true }, size: { type: Number, default: 74 }, color: { type: String, default: 'var(--accent)' }, label: { type: String, default: '' } })
const config = computed(() => {
  const R = (props.size - 9) / 2, C = 2 * Math.PI * R, cx = props.size / 2
  return { R, C, cx }
})
</script>
<template>
  <svg :viewBox="`0 0 ${size} ${size}`" :width="size" :height="size">
    <circle :cx="config.cx" :cy="config.cx" :r="config.R" fill="none" stroke="var(--surface-3)" stroke-width="7" />
    <circle
:cx="config.cx" :cy="config.cx" :r="config.R" fill="none" :stroke="color" stroke-width="7" stroke-linecap="round"
      :stroke-dasharray="pct / 100 * config.C + ' ' + config.C" :transform="`rotate(-90 ${config.cx} ${config.cx})`" />
    <text :x="config.cx" :y="config.cx + 1" text-anchor="middle" font-size="15" font-weight="700" fill="var(--ink)">{{ pct }}%</text>
    <text v-if="label" :x="config.cx" :y="config.cx + 15" text-anchor="middle" font-size="8.5" fill="var(--ink-3)">{{ label }}</text>
  </svg>
</template>
