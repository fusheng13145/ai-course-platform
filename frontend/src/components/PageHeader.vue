<script setup>
import { computed } from 'vue'
/**
 * 统一页面标题组件 · 云枢教学平台
 * 为所有业务页提供一致、大气的页头：渐变图标块 + 标题 + 副标题 + 右侧操作区。
 * 弱页（裸表堆砌、无标题区）统一套用本组件即可获得统一的视觉锚点。
 */
const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  icon: { type: String, default: 'grid' },
  iconBg: { type: String, default: '' },
  iconColor: { type: String, default: '' },
  // 是否显示标题下方的装饰性渐变分隔线
  rule: { type: Boolean, default: true },
  // 紧凑模式：减少上下留白（用于列表中嵌套的区块标题场景）
  compact: { type: Boolean, default: false },
})

const ICONS = {
  dashboard: 'M3 3h8v10H3zM13 3h8v6h-8zM13 11h8v10h-8zM3 15h8v6H3z',
  users:
    'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75',
  user: 'M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z',
  book: 'M4 19.5A2.5 2.5 0 0 1 6.5 17H20M4 19.5A2.5 2.5 0 0 0 6.5 22H20V2H6.5A2.5 2.5 0 0 0 4 4.5v15z',
  calendar:
    'M8 2v4M16 2v4M3 8h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z',
  chart: 'M3 3v18h18M18 17V9M13 17V5M8 17v-3',
  bell: 'M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.94 1.94 0 0 0 3.4 0',
  settings: 'M4 21v-7M4 10V3M12 21v-9M12 8V3M20 21v-5M20 12V3M1 14h6M9 8h6M17 16h6',
  sliders: 'M4 6h10M18 6h2M4 12h4M12 12h8M4 18h13M20 18h0M14 4v4M8 10v4M17 16v4',
  chevronR: 'm9 18 6-6-6-6',
  plus: 'M12 5v14M5 12h14',
  search: 'M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM21 21l-4.35-4.35',
  more: 'M12 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2zM19 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2zM5 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2z',
  file: 'M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2zM14 2v6h6',
  folder:
    'M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2z',
  video: 'm22 8-6 4 6 4V8zM14 6H4a2 2 0 0 0-2 2v8a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2z',
  play: 'm6 4 14 8-14 8V4z',
  edit: 'M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z',
  trash: 'M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2',
  eye: 'M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7-10-7-10-7zM12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
  send: 'm22 2-7 20-4-9-9-4zM22 2 11 13',
  filter: 'M22 3H2l8 9.46V19l4 2v-8.54L22 3z',
  download: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M7 10l5 5 5-5M12 15V3',
  upload: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12',
  clock: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 6v6l4 2',
  star: 'm12 2 3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z',
  warn: 'M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0zM12 9v4M12 17h.01',
  logout: 'M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9',
  lock: 'M19 11H5a2 2 0 0 0-2 2v7a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7a2 2 0 0 0-2-2zM7 11V7a5 5 0 0 1 10 0v4',
  key: 'm21 2-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0 3 3L22 7l-3-3m-3.5 3.5L19 4',
  layers: 'm12 2 8.5 4.7L12 11.4 3.5 6.7 12 2zM3.5 12l8.5 4.7 8.5-4.7M3.5 17.3l8.5 4.7 8.5-4.7',
  grid: 'M3 3h7v7H3zM14 3h7v7h-7zM14 14h7v7h-7zM3 14h7v7H3z',
  list: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',
  megaphone: 'm3 11 18-5v12L3 13v-2zM11.6 16.8a3 3 0 1 1-5.8-1.6',
  shield: 'M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z',
  monitor: 'M8 21h8M12 17v4M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z',
  clipboard:
    'M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2M15 2H9a1 1 0 0 0-1 1v2a1 1 0 0 0 1 1h6a1 1 0 0 0 1-1V3a1 1 0 0 0-1-1z',
  award: 'M12 15a7 7 0 1 0 0-14 7 7 0 0 0 0 14zM8.21 13.89 7 23l5-3 5 3-1.21-9.12',
  message: 'M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z',
  refresh: 'M3 12a9 9 0 0 1 15.36-6.36L21 8M21 3v5h-5M21 12a9 9 0 0 1-15.36 6.36L3 16M3 21v-5h5',
  dot: 'M12 13a1 1 0 1 0 0-2 1 1 0 0 0 0 2z',
  building:
    'M6 22V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v18zM2 22h20M9 6h1M14 6h1M9 10h1M14 10h1M9 14h1M14 14h1M10 22v-3h4v3',
  grad: 'm22 9-10-5L2 9l10 5 10-5zM6 11.5V16c0 1.66 2.69 3 6 3s6-1.34 6-3v-4.5M22 9v5',
  pin: 'M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0zM12 13a3 3 0 1 0 0-6 3 3 0 0 0 0 6z',
  link: 'M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71',
  archive: 'M21 8v13H3V8M1 3h22v5H1zM10 12h4',
  doc: 'M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2zM14 2v6h6M16 13H8M16 17H8M10 9H8',
  log: 'M21 12a9 9 0 0 1-9 9m9-9a9 9 0 0 0-9-9m9 9H3m9 9a9 9 0 0 1-9-9m9 9c2.21 0 4-4.03 4-9s-1.79-9-4-9m0 18c-2.21 0-4-4.03-4-9s1.79-9 4-9m-9 9a9 9 0 0 1 9-9',
  target:
    'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM12 18a6 6 0 1 0 0-12 6 6 0 0 0 0 12zM12 14a2 2 0 1 0 0-4 2 2 0 0 0 0 4z',
  check: 'M20 6 9 17l-5-5',
  checks: 'M18 6 7 17l-5-5M22 10 11 21l-2.5-2.5',
  brain:
    'M12 5a3 3 0 0 0-3 3 3 3 0 0 0-1 5 3 3 0 0 0 2 4 3 3 0 0 0 5 1 3 3 0 0 0 5-1 3 3 0 0 0 2-4 3 3 0 0 0-1-5 3 3 0 0 0-3-3 3 3 0 0 0-3 0zM9 8a3 3 0 0 1 6 0',
  compass: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20zM16 8l-2 6-6 2 2-6z',
  tag: 'M20.59 13.41 11 3.83A2 2 0 0 0 9.59 3H4a1 1 0 0 0-1 1v5.59A2 2 0 0 0 3.83 11l9.58 9.58a2 2 0 0 0 2.83 0l4.35-4.35a2 2 0 0 0 0-2.82zM7.5 7.5h.01',
}
const ICON_TONES = {
  accent: { bg: 'var(--accent-soft)', color: 'var(--accent)' },
  info: { bg: 'var(--info-soft)', color: 'var(--info)' },
  ok: { bg: 'var(--ok-soft)', color: 'var(--ok)' },
  warn: { bg: 'var(--warn-soft)', color: 'var(--warn)' },
  violet: { bg: 'var(--violet-soft)', color: 'var(--violet)' },
}
const icoStyle = computed(() => {
  const tone =
    ICON_TONES[props.iconBg] ||
    (props.iconBg
      ? { bg: props.iconBg, color: props.iconColor || 'var(--accent)' }
      : ICON_TONES.accent)
  return { background: tone.bg, color: tone.color }
})
function iconSvg() {
  const d = ICONS[props.icon] || ICONS.dot
  return `<svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="${d}"/></svg>`
}
</script>

<template>
  <header class="pg-head" :class="{ compact, 'no-rule': !rule }">
    <div class="pg-left">
      <!-- 内部图标常量（受控），无需净化 -->
      <!-- eslint-disable-next-line vue/no-v-html -->
      <span class="pg-ico" :style="icoStyle" v-html="iconSvg()"></span>
      <div class="pg-text">
        <h1 class="pg-title">{{ title }}</h1>
        <p v-if="subtitle" class="pg-sub">{{ subtitle }}</p>
      </div>
    </div>
    <div class="pg-actions">
      <slot />
    </div>
  </header>
</template>

<style scoped>
.pg-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 14px;
  margin-bottom: 18px;
  border-bottom: 1px solid var(--line);
  position: relative;
}
.pg-head::after {
  content: '';
  position: absolute;
  left: 0;
  bottom: -1px;
  width: 64px;
  height: 3px;
  border-radius: 3px;
  background: linear-gradient(90deg, var(--accent), rgba(193, 59, 48, 0.15));
}
.pg-head.no-rule {
  border-bottom: none;
}
.pg-head.no-rule::after {
  display: none;
}
.pg-head.compact {
  padding-bottom: 10px;
  margin-bottom: 14px;
}

.pg-left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}
.pg-ico {
  width: 46px;
  height: 46px;
  border-radius: 13px;
  flex-shrink: 0;
  display: grid;
  place-items: center;
  position: relative;
  overflow: hidden;
}
.pg-ico::before {
  content: '';
  position: absolute;
  inset: -40% -10% auto auto;
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 255, 255, 0.45), transparent 70%);
  pointer-events: none;
}
.pg-text {
  min-width: 0;
}
.pg-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--ink);
  letter-spacing: 0.2px;
  line-height: 1.3;
  margin: 0;
}
.pg-sub {
  font-size: 13px;
  color: var(--ink-3);
  margin: 3px 0 0;
  line-height: 1.5;
}
.pg-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  flex-wrap: wrap;
  justify-content: flex-end;
}

@media (max-width: 768px) {
  .pg-head {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }
  .pg-actions {
    width: 100%;
  }
  .pg-head.compact {
    flex-direction: row;
  }
}
</style>
