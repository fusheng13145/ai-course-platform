<template>
  <div class="model-banner" :class="bannerClass">
    <el-icon class="banner-icon"><component :is="bannerIcon" /></el-icon>
    <span>{{ bannerText }}</span>
  </div>
</template>

<script setup>
/**
 * 知识库模型状态横幅
 * <p>按 llmReady 三态（未知/已配置/检索态）展示检测结果与回退说明。</p>
 */
defineProps({
  /** 横幅样式类（banner--warn/ok/unknown） */
  bannerClass: { type: String, required: true },
  /** 状态图标组件引用 */
  bannerIcon: { type: [Object, Function], required: true },
  /** 状态说明文本 */
  bannerText: { type: String, required: true },
})
</script>

<style scoped>
.model-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: var(--ac-radius-md);
  font-size: 13px;
  margin-bottom: var(--ac-spacing-lg);
  border: 1px solid var(--ac-border);
}
.model-banner .banner-icon {
  font-size: 16px;
}
.banner--warn {
  background: var(--warn-soft);
  border-color: var(--warn);
  color: var(--warn);
}
.banner--ok {
  background: var(--ok-soft);
  border-color: var(--ok);
  color: var(--ok);
}
.banner--unknown {
  background: var(--ac-bg-elevated);
  color: var(--ac-text-muted);
}
</style>
