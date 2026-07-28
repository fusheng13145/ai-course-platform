<template>
  <div class="page-header">
    <div v-if="icon" class="page-header__icon">
      <el-icon :size="22"><component :is="icon" /></el-icon>
    </div>
    <div class="page-header__main">
      <h1 class="page-header__title">{{ title }}</h1>
      <p v-if="subtitle" class="page-header__subtitle">{{ subtitle }}</p>
    </div>
    <div v-if="$slots.actions" class="page-header__actions">
      <slot name="actions" />
    </div>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  icon: { type: [String, Object], default: '' },
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: var(--ac-spacing-lg);
  padding-bottom: var(--ac-spacing-md);
  border-bottom: 1px solid var(--ac-border);
}

.page-header__icon {
  width: 46px;
  height: 46px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--ac-radius-md);
  background: var(--ac-primary-bg);
  color: var(--ac-primary);
  border: 1px solid var(--ac-primary-bg);
}

.page-header__main {
  flex: 1;
  min-width: 0;
}

.page-header__title {
  font-size: 20px;
  font-weight: 700;
  color: var(--ac-text-primary);
  margin: 0;
  letter-spacing: -0.3px;
  line-height: 1.2;
}

.page-header__subtitle {
  font-size: 13px;
  color: var(--ac-text-secondary);
  margin: 4px 0 0;
}

.page-header__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

/* 在超小屏下允许操作区换行到下一行 */
@media (max-width: 640px) {
  .page-header {
    flex-wrap: wrap;
  }
  .page-header__actions {
    width: 100%;
  }
  .page-header__actions :deep(.el-button) {
    flex: 1;
  }
}
</style>
