<template>
  <div v-if="currentPath" class="overview-card">
    <div class="overview-grid">
      <div class="overview-item">
        <span class="overview-value">{{ currentPath.name || '学习路径' }}</span>
        <span class="overview-label">当前路径</span>
      </div>
      <div class="overview-item">
        <span class="overview-value">{{ completedNodeCount }}</span>
        <span class="overview-label">已完成节点</span>
      </div>
      <div class="overview-item">
        <span class="overview-value">{{ totalNodeCount }}</span>
        <span class="overview-label">总节点数</span>
      </div>
      <div class="overview-item">
        <span class="overview-value">{{ overallProgress }}%</span>
        <span class="overview-label">整体进度</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 学习路径 — 路径概览卡
 * <p>当前路径/已完成节点/总节点数/整体进度四格摘要。</p>
 */
defineProps({
  /** 当前路径对象 */
  currentPath: { type: Object, default: null },
  /** 已完成节点数 */
  completedNodeCount: { type: Number, default: 0 },
  /** 总节点数 */
  totalNodeCount: { type: Number, default: 0 },
  /** 整体进度（%） */
  overallProgress: { type: Number, default: 0 },
})
</script>

<style scoped>
.overview-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  margin-bottom: var(--ac-spacing-md);
}

.overview-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--ac-spacing-md);
}

.overview-item {
  text-align: center;
}

.overview-value {
  display: block;
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-primary);
  line-height: 1.3;
}

.overview-label {
  font-size: 12px;
  color: var(--ac-text-muted);
}

@media (max-width: 900px) {
  .overview-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
