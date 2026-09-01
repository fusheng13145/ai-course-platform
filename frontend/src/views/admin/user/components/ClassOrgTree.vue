<template>
  <div class="org-panel content-card">
    <div class="org-panel-head">
      <span class="org-title">组织维度</span>
      <el-button text type="primary" size="small" @click="emit('reset')">重置</el-button>
    </div>
    <el-tree
      ref="treeRef"
      :data="orgTree"
      :props="{ label: 'label', children: 'children' }"
      node-key="key"
      highlight-current
      :expand-on-click-node="false"
      default-expand-all
      @node-click="emit('node-click', $event)"
    >
      <template #default="{ data }">
        <span class="org-node">
          <el-icon v-if="data.type === 'root'"><School /></el-icon>
          <el-icon v-else-if="data.type === 'major'"><Collection /></el-icon>
          <el-icon v-else><UserFilled /></el-icon>
          <span class="org-label">{{ data.label }}</span>
          <span v-if="data.count != null" class="org-count">{{ data.count }}</span>
        </span>
      </template>
    </el-tree>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { School, Collection, UserFilled } from '@element-plus/icons-vue'

/**
 * 组织维度树（专业 → 年级）
 * <p>展示班级按专业/年级的组织结构；节点点击经 node-click 事件上抛（root 由父组件重置）。</p>
 */
defineProps({
  /** 组织树数据（root/major/grade 三层节点） */
  orgTree: { type: Array, required: true },
})

const emit = defineEmits(['node-click', 'reset'])

// 暴露 setCurrentKey 供父组件（useClassrooms.resetOrgFilter）取消高亮
const treeRef = ref(null)
defineExpose({ setCurrentKey: (key) => treeRef.value?.setCurrentKey(key) })
</script>

<style scoped>
.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 16px;
}
.org-panel {
  position: sticky;
  top: 12px;
}
.org-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.org-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
}
.org-node {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
  font-size: 13px;
}
.org-label {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.org-count {
  font-size: 12px;
  color: var(--ac-text-muted);
  background: var(--ac-bg-hover);
  border-radius: 10px;
  padding: 0 8px;
  min-width: 20px;
  text-align: center;
}

@media (max-width: 900px) {
  .org-panel {
    position: static;
  }
}
</style>
