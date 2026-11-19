<template>
  <span class="number-badge" :class="{ 'number-badge-lg': size === 32 }">{{ formatted }}</span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(
  defineProps<{
    value: string | number
    size?: 24 | 32
  }>(),
  { size: 24 },
)

const formatted = computed(() => {
  const value = String(props.value)
  return /^\d+$/.test(value) ? value.padStart(2, '0') : value
})
</script>

<style scoped>
.number-badge {
  display: inline-grid;
  place-items: center;
  flex: none;
  width: 24px;
  height: 24px;
  border-radius: var(--r-ctl);
  background: var(--surface-3);
  color: var(--violet);
  font-size: 12px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.number-badge-lg {
  width: 32px;
  height: 32px;
}
</style>
