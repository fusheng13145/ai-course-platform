<template>
  <div class="wq-toolbar">
    <div class="toolbar-left">
      <!-- 知识点分类筛选标签 -->
      <div class="filter-tags">
        <span
          v-for="kp in knowledgePointTags"
          :key="kp.id"
          class="filter-tag"
          :class="{ active: selectedKpId === kp.id }"
          @click="$emit('update:selectedKpId', selectedKpId === kp.id ? null : kp.id)"
        >
          {{ kp.name }} ({{ kp.count }})
        </span>
        <span v-if="knowledgePointTags.length === 0" class="filter-tag-hint">暂无分类数据</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
defineProps({
  /** 知识点标签（id/name/count） */
  knowledgePointTags: {
    type: Array as PropType<{ id: number | string; name?: string; count?: number }[]>,
    default: () => [],
  },
  selectedKpId: { type: [Number, String], default: null },
})
defineEmits(['update:selectedKpId'])
</script>

<style scoped>
.wq-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ac-spacing-md);
  gap: var(--ac-spacing-md);
  flex-wrap: wrap;
}
.toolbar-left {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
  flex-wrap: wrap;
  flex: 1;
  min-width: 0;
}
.filter-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  overflow-x: auto;
  max-width: 100%;
}
.filter-tag {
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  background: var(--ac-bg);
  color: var(--ac-text-secondary);
  cursor: pointer;
  transition: all var(--ac-transition-fast);
  border: 1px solid transparent;
  white-space: nowrap;
}
.filter-tag:hover {
  background: var(--ac-bg-hover);
}
.filter-tag.active {
  background: var(--ac-primary-bg);
  color: var(--ac-primary);
  border-color: var(--ac-primary-light);
  font-weight: 500;
}
.filter-tag-hint {
  font-size: 12px;
  color: var(--ac-text-muted);
}
</style>
