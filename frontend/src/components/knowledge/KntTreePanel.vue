<template>
  <!-- 左侧知识树 -->
  <div class="tree-panel">
    <div class="panel-header">
      <span class="panel-title">知识结构</span>
      <el-button text size="small" type="primary" @click="expandAll">展开全部</el-button>
    </div>
    <div v-loading="treeLoading" class="tree-wrap">
      <el-tree
        ref="treeRef"
        :data="treeData"
        :props="{ label: 'name', children: 'children' }"
        node-key="id"
        default-expand-all
        highlight-current
        :expand-on-click-node="false"
        empty-text="暂无知识点"
        @node-click="handleNodeClick"
      >
        <template #default="{ node, data }">
          <span class="tree-label">
            <span>{{ node.label }}</span>
            <el-tag size="small" :type="getDiffTag(data.difficulty)" style="margin-left: 4px">{{
              getDiffLabel(data.difficulty)
            }}</el-tag>
          </span>
        </template>
      </el-tree>
      <el-empty
        v-if="!treeLoading && !treeData.length"
        description="暂无知识树数据"
        :image-size="50"
      />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  treeData: { type: Array, default: () => [] },
  treeLoading: { type: Boolean, default: false },
})

const treeRef = ref(null)

function getDiffTag(d) {
  return { EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }[d] || 'info'
}
function getDiffLabel(d) {
  return { EASY: '简单', MEDIUM: '中等', HARD: '困难' }[d] || '-'
}
function handleNodeClick() {}

function expandAll() {
  const nodes = treeRef.value?.store?.nodesMap
  if (nodes) Object.values(nodes).forEach((n) => n.expand())
}
</script>

<style scoped>
.tree-panel {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  border-bottom: 1px solid var(--ac-border);
}
.panel-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0;
}
.tree-wrap {
  flex: 1;
  padding: 6px 10px;
  overflow-y: auto;
}
.tree-wrap :deep(.el-tree) {
  background: transparent;
}
.tree-wrap :deep(.el-tree-node__content) {
  height: 36px;
  border-radius: 6px;
  margin: 1px 0;
  transition: background 0.15s;
}
.tree-wrap :deep(.el-tree-node__content:hover) {
  background: var(--ac-bg-hover);
}
.tree-label {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  font-size: 13px;
}
@media (max-width: 1024px) {
  .tree-panel {
    max-height: 260px;
  }
}
</style>
