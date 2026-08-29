<template>
  <div class="path-selector">
    <h3 class="section-label">我的学习路径</h3>
    <div
      v-for="(p, idx) in paths"
      :key="p.id"
      class="path-card"
      :class="{ active: selectedPathId === p.id }"
      @click="$emit('select', p.id)"
    >
      <div class="path-card-header">
        <span class="path-name">{{ p.name || `学习路径 ${idx + 1}` }}</span>
        <el-tag size="small" :type="p.status === 'ACTIVE' ? 'success' : 'info'">
          {{
            p.status === 'ACTIVE'
              ? '进行中'
              : p.status === 'COMPLETED'
                ? '已完成'
                : '未开始'
          }}
        </el-tag>
      </div>
      <div class="path-meta">
        <span>{{ p.totalStages || 0 }} 个节点</span>
        <span>进度 {{ calculatePathProgress(p) }}%</span>
      </div>
      <el-progress
        :percentage="calculatePathProgress(p)"
        :stroke-width="6"
        :show-text="false"
        style="margin-top: 8px"
      />
    </div>
  </div>
</template>

<script setup>
/**
 * 学习路径 — 路径选择器（左栏）
 * <p>我的学习路径卡片列表（名称/状态/节点数/进度）。</p>
 */
defineProps({
  /** 路径列表 */
  paths: { type: Array, required: true },
  /** 当前选中路径 id */
  selectedPathId: { type: [String, Number], default: null },
  /** 路径进度计算函数 */
  calculatePathProgress: { type: Function, required: true },
})

defineEmits(['select'])
</script>

<style scoped>
.path-selector {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  height: fit-content;
  max-height: calc(100vh - 180px);
  overflow-y: auto;
}

.section-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0 0 var(--ac-spacing-md);
  display: flex;
  align-items: center;
  gap: 6px;
}

.path-card {
  padding: var(--ac-spacing-md);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  cursor: pointer;
  transition: all var(--ac-transition-normal);
  margin-bottom: var(--ac-spacing-sm);
  background: var(--ac-bg);
}

.path-card:hover {
  transform: translateY(-1px);
  box-shadow: var(--ac-shadow-sm);
}

.path-card.active {
  border-color: var(--ac-primary);
  background: var(--ac-primary-bg);
}

.path-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.path-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
}

.path-meta {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: var(--ac-text-muted);
}
</style>
