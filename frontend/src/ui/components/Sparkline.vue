<template>
  <svg :viewBox="`0 0 ${width} ${height}`" preserveAspectRatio="none" aria-hidden="true">
    <polyline
      :points="points"
      fill="none"
      stroke="currentColor"
      stroke-width="2"
      vector-effect="non-scaling-stroke"
    />
    <polyline
      :points="`${points} ${width},${height} 0,${height}`"
      fill="currentColor"
      opacity=".08"
      stroke="none"
    />
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{ values: number[]; width?: number; height?: number }>(), {
  width: 66,
  height: 22,
})

const points = computed(() => {
  const values = props.values?.length ? props.values : [0, 0]
  const max = Math.max(...values)
  const min = Math.min(...values)
  const span = max - min || 1
  return values
    .map((value, index) => {
      const x = (index / Math.max(1, values.length - 1)) * (props.width - 8) + 4
      const y = props.height - 6 - ((value - min) / span) * (props.height - 14)
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
})
</script>
