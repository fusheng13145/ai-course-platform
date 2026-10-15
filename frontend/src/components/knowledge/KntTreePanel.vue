<template>
  <!-- 左侧知识树 -->
  <div class="tree-panel">
    <div class="panel-header">
      <span class="panel-title">知识结构</span>
      <el-button text size="small" type="primary" @click="handleExpand">展开全部</el-button>
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
        :empty-text="emptyText"
        @node-click="$emit('node-click', $event)"
      >
        <template #default="{ node, data }">
          <span class="tree-label">
            <span class="node-name">{{ node.label }}</span>
            <el-tag size="small" :type="getDifficultyTagType(data.difficulty)" class="tree-tag">{{
              getDifficultyLabel(data.difficulty)
            }}</el-tag>
          </span>
        </template>
      </el-tree>
      <EmptyState
        v-if="!treeLoading && !treeData.length"
        title="暂无知识树数据"
        icon="layers"
        size="compact"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import EmptyState from '@/components/ui/EmptyState.vue'

/**
 * 知识结构树面板（知识图谱域共享实现）
 * <p>学生课程知识页与教师知识管理页共用；难度标签映射可经 props 注入（缺省内置），
 * 「展开全部」内部执行 `expandAll` 并同时向父组件广播 `expand`（父组件可经 ref 调用）。</p>
 */
defineProps({
  /** 知识树数据 */
  treeData: { type: Array, default: () => [] },
  /** 加载态 */
  treeLoading: { type: Boolean, default: false },
  /** 树为空时的提示文案 */
  emptyText: { type: String, default: '暂无知识点' },
  /** 难度标签类型映射（缺省内置；须内联——defineProps 不可引用组件局部变量） */
  getDifficultyTagType: {
    type: Function,
    default: (d: string) => ({ EASY: 'success', MEDIUM: 'warning', HARD: 'danger' })[d] || 'info',
  },
  /** 难度文案映射（缺省内置；同理内联） */
  getDifficultyLabel: {
    type: Function,
    default: (d: string) => ({ EASY: '简单', MEDIUM: '中等', HARD: '困难' })[d] || '-',
  },
})

const emit = defineEmits(['expand', 'node-click'])
const treeRef = ref(null)

function expandAll() {
  const nodes = treeRef.value?.store?.nodesMap
  if (nodes) Object.values(nodes).forEach((n) => n.expand())
}

function handleExpand() {
  expandAll()
  emit('expand')
}

defineExpose({ expandAll })
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
.tree-wrap :deep(.is-current > .el-tree-node__content) {
  background: var(--ac-primary-bg);
  font-weight: 600;
}
.tree-label {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
  font-size: 13px;
}
.node-name {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.tree-tag {
  margin-left: 6px;
}
@media (max-width: 1024px) {
  .tree-panel {
    max-height: 260px;
  }
}
</style>
