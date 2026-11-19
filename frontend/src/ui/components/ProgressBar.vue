<template>
  <div class="progress-field">
    <div class="progress-meta">
      <span>{{ label }}</span>
      <b>{{ normalized }}%</b>
    </div>
    <div
      class="progress-track"
      role="progressbar"
      :aria-label="label"
      :aria-valuenow="normalized"
      aria-valuemin="0"
      aria-valuemax="100"
    >
      <i :style="{ width: `${normalized}%` }"></i>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  value: number
  label: string
}>()

const normalized = computed(() => Math.min(100, Math.max(0, Math.round(props.value))))
</script>

<style scoped>
.progress-field {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}
.progress-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  color: var(--ink-3);
  font-size: 12px;
}
.progress-meta b {
  color: var(--ink);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}
.progress-track {
  height: 8px;
  overflow: hidden;
  border-radius: var(--r-ctl);
  background: var(--surface-3);
}
.progress-track i {
  display: block;
  height: 100%;
  border-radius: var(--r-ctl);
  background: var(--accent);
}
</style>
