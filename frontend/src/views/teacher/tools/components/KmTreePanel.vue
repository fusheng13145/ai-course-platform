<template>
  <div class="tree-panel">
    <div class="panel-header">
      <span class="chart-title">知识结构</span>
      <el-button text size="small" type="primary" @click="emit('expand')">展开全部</el-button>
    </div>
    <div v-loading="treeLoading" class="tree-container">
      <el-tree
        ref="treeRef"
        :data="knowledgeTree"
        :props="{ label: 'name', children: 'children' }"
        node-key="id"
        default-expand-all
        highlight-current
        :expand-on-click-node="false"
        empty-text="请先选择课程加载知识点"
        @node-click="emit('node-click', $event)"
      >
        <template #default="{ node, data }">
          <span class="tree-node-label">
            <span class="node-name">{{ node.label }}</span>
            <el-tag size="small" :type="getDifficultyTagType(data.difficulty)" class="tree-tag">{{
              getDifficultyLabel(data.difficulty)
            }}</el-tag>
          </span>
        </template>
      </el-tree>
      <el-empty
        v-if="!treeLoading && (!knowledgeTree || knowledgeTree.length === 0)"
        description="暂无知识树数据"
        :image-size="60"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

/**
 * 知识管理左侧知识树
 * <p>展示课程知识点层级（名称 + 难度标签）；节点点击与展开全部经事件上抛。</p>
 */
defineProps({
  /** 知识树数据 */
  knowledgeTree: { type: Array, default: () => [] },
  /** 加载态 */
  treeLoading: { type: Boolean, default: false },
  getDifficultyTagType: { type: Function, required: true },
  getDifficultyLabel: { type: Function, required: true },
})

const emit = defineEmits(['expand', 'node-click'])

// 暴露 expandAll 所需节点访问能力（父组件经 ref 调用）
const treeRef = ref(null)
defineExpose({
  expandAll: () => {
    const nodes = treeRef.value?.store?.nodesMap
    if (nodes) Object.values(nodes).forEach((node) => node.expand())
  },
})
</script>

<style scoped>
.tree-panel {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--ac-spacing-md) var(--ac-spacing-lg);
  border-bottom: 1px solid var(--ac-border);
}
.tree-container {
  flex: 1;
  padding: var(--ac-spacing-sm) var(--ac-spacing-md);
  overflow-y: auto;
}
.tree-container :deep(.el-tree) {
  background: transparent;
}
.tree-container :deep(.el-tree-node__content) {
  height: 38px;
  border-radius: var(--ac-radius-sm);
  margin: 2px 0;
  transition: background var(--ac-transition-fast);
}
.tree-container :deep(.el-tree-node__content:hover) {
  background: var(--ac-bg-hover);
}
.tree-container :deep(.is-current > .el-tree-node__content) {
  background: var(--ac-primary-bg);
  font-weight: 600;
}
.tree-node-label {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}
.node-name {
  font-size: 13px;
  color: var(--ac-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tree-tag {
  margin-left: 6px;
}
</style>
