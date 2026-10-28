<script setup>
defineProps({
  title: { type: String, default: '暂无内容' },
  desc: { type: String, default: '' },
  icon: { type: String, default: 'archive' },
  /**
   * 尺寸（D2-3 统一规范）：
   * - default：页面级空态（表格 #empty 插槽、整页无数据）
   * - compact：面板 / 抽屉 / 图表区内嵌空态（更小 padding 与图标）
   */
  size: { type: String, default: 'default' },
})
const ICONS = {
  archive: 'M21 8v13H3V8M1 3h22v5H1zM10 12h4',
  search: 'M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM21 21l-4.35-4.35',
  bell: 'M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9M10.3 21a1.94 1.94 0 0 0 3.4 0',
  list: 'M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01',
  layers: 'm12 2 8.5 4.7L12 11.4 3.5 6.7 12 2zM3.5 12l8.5 4.7 8.5-4.7M3.5 17.3l8.5 4.7 8.5-4.7',
  folder:
    'M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2z',
  upload: 'M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4M17 8l-5-5-5 5M12 3v12',
  checks: 'M18 6 7 17l-5-5M22 10 11 21l-2.5-2.5',
  users:
    'M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM23 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75',
  message:
    'M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z',
  alert:
    'M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0zM12 9v4M12 17h.01',
  book: 'M4 19.5A2.5 2.5 0 0 1 6.5 17H20M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z',
  chart: 'M18 20V10M12 20V4M6 20v-6',
}
</script>
<template>
  <div class="empty-state" :class="`is-${size}`">
    <div class="e-ico">
      <svg
        viewBox="0 0 24 24"
        width="28"
        height="28"
        fill="none"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <path :d="ICONS[icon] || ICONS.archive" />
      </svg>
    </div>
    <div class="e-title">{{ title }}</div>
    <div v-if="desc" class="e-desc">{{ desc }}</div>
    <div v-if="$slots.default" class="e-action"><slot /></div>
  </div>
</template>
<style scoped>
.empty-state {
  padding: 46px 20px;
  text-align: center;
  color: var(--ink-3);
}
.e-ico {
  width: 62px;
  height: 62px;
  border-radius: var(--r-l1);
  margin: 0 auto 12px;
  background: var(--surface-3);
  display: grid;
  place-items: center;
  color: var(--ink-4);
}
.e-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-2);
  margin-bottom: 4px;
}
.e-desc {
  font-size: 12.5px;
}
/* 空态 CTA（可选默认插槽，如「新建成绩方案」按钮） */
.e-action {
  margin-top: 12px;
}

/* compact：面板 / 抽屉 / 图表区内嵌空态（D2-3 规范） */
.empty-state.is-compact {
  padding: 22px 16px;
}
.empty-state.is-compact .e-ico {
  width: 44px;
  height: 44px;
  border-radius: var(--r-l2);
  margin-bottom: 8px;
}
.empty-state.is-compact .e-ico svg {
  width: 20px;
  height: 20px;
}
.empty-state.is-compact .e-title {
  font-size: 13px;
  margin-bottom: 2px;
}
.empty-state.is-compact .e-desc {
  font-size: 12px;
}
</style>
