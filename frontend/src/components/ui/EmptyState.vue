<script setup>
defineProps({ title: { type: String, default: '暂无内容' }, desc: { type: String, default: '' }, icon: { type: String, default: 'archive' } })
const ICONS = {
  archive: 'M21 8v13H3V8M1 3h22v5H1zM10 12h4',
  search: 'M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM21 21l-4.35-4.35',
  bell: 'M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.94 1.94 0 0 0 3.4 0',
  list: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',
  layers: 'm12 2 8.5 4.7L12 11.4 3.5 6.7 12 2zM3.5 12l8.5 4.7 8.5-4.7M3.5 17.3l8.5 4.7 8.5-4.7',
  folder: 'M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2z',
  upload: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12',
  checks: 'M18 6 7 17l-5-5M22 10 11 21l-2.5-2.5',
}
</script>
<template>
  <div class="empty-state">
    <div class="e-ico">
      <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
        <path :d="ICONS[icon] || ICONS.archive" />
      </svg>
    </div>
    <div class="e-title">{{ title }}</div>
    <div v-if="desc" class="e-desc">{{ desc }}</div>
  </div>
</template>
<style scoped>
.empty-state { padding: 46px 20px; text-align: center; color: var(--ink-3); }
.e-ico { width: 62px; height: 62px; border-radius: 18px; margin: 0 auto 12px; background: var(--surface-3); display: grid; place-items: center; color: var(--ink-4); }
.e-title { font-size: 14px; font-weight: 600; color: var(--ink-2); margin-bottom: 4px; }
.e-desc { font-size: 12.5px; }
</style>
