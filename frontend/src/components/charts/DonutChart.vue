<script setup>
import { computed } from 'vue'
import { sanitizeHtml } from '@/utils/sanitize'

const props = defineProps({
  data: { type: Array, required: true },
  size: { type: Number, default: 168 },
  thickness: { type: Number, default: 22 },
  centerLabel: { type: String, default: '总计' },
})
const PALETTE = [
  '#c13b30',
  '#2563a8',
  '#2e7d4f',
  '#b57908',
  '#6d4fa1',
  '#0f766e',
  '#c2602e',
  '#4d6a8a',
]
const total = computed(() => props.data.reduce((s, d) => s + d.value, 0) || 1)
const segs = computed(() => {
  const R = (props.size - props.thickness) / 2,
    C = 2 * Math.PI * R,
    cx = props.size / 2
  let acc = 0
  return props.data
    .map((d, i) => {
      const frac = d.value / total.value,
        len = Math.max(frac * C - 2, 1)
      const s = `<circle cx="${cx}" cy="${cx}" r="${R}" fill="none" stroke="${d.color || PALETTE[i % PALETTE.length]}" stroke-width="${props.thickness}" stroke-dasharray="${len} ${C}" stroke-dashoffset="${-acc * C}" transform="rotate(-90 ${cx} ${cx})"><title>${d.label}: ${d.value}</title></circle>`
      acc += frac
      return s
    })
    .join('')
})
// 数据（d.label/centerLabel）拼入 SVG 前整体净化，防止外部数据注入 HTML
const chartSvg = computed(() =>
  sanitizeHtml(
    segs.value +
      `<text x='${props.size / 2}' y='${props.size / 2 - 4}' text-anchor='middle' font-size='20' font-weight='700' fill='var(--ink)'>${total.value}</text><text x='${props.size / 2}' y='${props.size / 2 + 16}' text-anchor='middle' font-size='11' fill='var(--ink-3)'>${props.centerLabel}</text>`,
  ),
)
</script>
<template>
  <div style="display: flex; align-items: center; gap: 18px">
    <!-- 已净化：chartSvg 内含外部数据（label/centerLabel），先经 sanitizeHtml 清洗 -->
    <!-- eslint-disable-next-line vue/no-v-html -->
    <svg
      :viewBox="`0 0 ${size} ${size}`"
      :width="size"
      :height="size"
      style="flex-shrink: 0"
      v-html="chartSvg"
    />
    <div style="display: flex; flex-direction: column; gap: 7px">
      <span
        v-for="(d, i) in data"
        :key="i"
        style="display: flex; align-items: center; gap: 6px; font-size: 11.5px; color: var(--ink-3)"
      >
        <span
          style="width: 8px; height: 8px; border-radius: 3px"
          :style="{ background: d.color || PALETTE[i % PALETTE.length] }"
        ></span>
        {{ d.label }} · <b>{{ d.value }}</b
        >（{{ Math.round((d.value / total) * 100) }}%）
      </span>
    </div>
  </div>
</template>
