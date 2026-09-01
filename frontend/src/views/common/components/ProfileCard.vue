<template>
  <div class="pc-card">
    <div class="pc-header">
      <div class="pc-title">
        <el-icon><component :is="icon" /></el-icon> {{ title }}
      </div>
      <slot name="action" />
    </div>
    <slot />
  </div>
</template>

<script setup lang="ts">
/**
 * 个人中心通用卡片
 * <p>统一承载个人中心各区块的卡片外观（标题行 + 右上操作区 + 内容区），
 * 消除各子组件间 .pc-card 样式的重复定义。</p>
 */
defineProps({
  /** 标题区图标组件 */
  icon: { type: [Object, Function], default: null },
  /** 卡片标题 */
  title: { type: String, default: '' },
})
</script>

<style scoped>
.pc-card {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
  padding: var(--ac-spacing-lg);
  transition:
    box-shadow var(--ac-transition-normal),
    transform var(--ac-transition-normal);
}
.pc-card:hover {
  box-shadow: var(--ac-shadow-md);
}
.pc-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--ac-spacing-md);
}
.pc-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
}
.pc-title .el-icon {
  color: var(--ac-primary);
}
/* 注：.pc-form / .pc-submit 作用于插槽内容，由使用方子组件自带的 scoped 样式提供 */
</style>
