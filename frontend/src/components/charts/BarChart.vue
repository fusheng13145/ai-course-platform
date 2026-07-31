<script setup>
const props = defineProps({
  data: { type: Array, required: true }, // [{label, value, color?}]
  w: { type: Number, default: 560 },
  h: { type: Number, default: 220 },
  horizontal: { type: Boolean, default: false },
})
import { computed } from 'vue'
const max = computed(() => Math.max(...props.data.map(d => d.value), 1))
const PALETTE = ['#c13b30', '#2563a8', '#2e7d4f', '#b57908', '#6d4fa1', '#0f766e', '#c2602e', '#4d6a8a']
</script>
<template>
  <svg :viewBox="`0 0 ${w} ${data.length > 6 ? data.length * 34 + 8 : h}`" width="100%" style="display:block">
    <template v-if="horizontal">
      <template v-for="(d, i) in data" :key="i">
        <text :x="0" :y="i * 34 + 26" font-size="11" fill="#4b5565">{{ d.label }}</text>
        <rect :x="92" :y="i * 34 + 4" :width="Math.max(d.value / max * (w - 150), 2)" :height="24" rx="5" :fill="d.color || PALETTE[0]" opacity=".88"><title>{{ d.label }}: {{ d.value }}</title></rect>
        <text :x="98 + Math.max(d.value / max * (w - 150), 2)" :y="i * 34 + 20" font-size="11" fill="#1f2733" font-weight="600">{{ d.value }}</text>
      </template>
    </template>
    <template v-else>
      <template v-for="(d, i) in data" :key="i">
        <rect :rx="6" :opacity="0.9" :x="24 + i * ((w - 40) / data.length) + ((w - 40) / data.length - Math.min(46, (w - 40) / data.length - 14)) / 2" :y="h - 26 - Math.max(d.value / max * (h - 44), 2)" :width="Math.min(46, (w - 40) / data.length - 14)" :height="Math.max(d.value / max * (h - 44), 2)" :fill="d.color || PALETTE[i % PALETTE.length]">
          <title>{{ d.label }}: {{ d.value }}</title>
        </rect>
        <text :x="24 + i * ((w - 40) / data.length) + ((w - 40) / data.length) / 2" :y="h - 26 - Math.max(d.value / max * (h - 44), 2) - 6" text-anchor="middle" font-size="10.5" fill="#4b5565" font-weight="600">{{ d.value }}</text>
        <text :x="24 + i * ((w - 40) / data.length) + ((w - 40) / data.length) / 2" :y="h - 8" text-anchor="middle" font-size="10.5" fill="#8b94a3">{{ d.label }}</text>
      </template>
      <line v-for="f in [0.25,0.5,0.75,1]" :key="f" :x1="20" :x2="w - 16" :y1="h - 26 - f * (h - 44)" :y2="h - 26 - f * (h - 44)" stroke="#e3e6ec" stroke-dasharray="3 4" />
    </template>
  </svg>
</template>
