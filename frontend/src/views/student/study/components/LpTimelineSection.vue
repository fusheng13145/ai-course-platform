<template>
  <div class="timeline-section">
    <h3 class="section-label">学习进度</h3>
    <el-timeline v-if="pathNodes.length > 0">
      <el-timeline-item
        v-for="(node, index) in pathNodes"
        :key="node.id"
        :timestamp="node.completedAt || node.time || ''"
        placement="top"
        :size="getNodeSize(node)"
        :type="getNodeType(node)"
        :hollow="!node.completed && !node.current"
      >
        <div class="timeline-node-card" @click="$emit('node-click', node)">
          <div class="node-header">
            <span class="node-index">步骤 {{ index + 1 }}</span>
            <div class="node-status-tags">
              <el-tag v-if="node.completed" type="success" size="small">已完成</el-tag>
              <el-tag v-else-if="node.current" type="warning" size="small">
                进行中
                <span v-if="node.current" class="spinning-dot"></span>
              </el-tag>
              <el-tag v-else type="info" size="small">未开始</el-tag>
            </div>
          </div>
          <h4 class="node-title">{{ node.title || node.description || '学习节点' }}</h4>
          <p class="node-desc">{{ node.content || node.detail || '' }}</p>

          <!-- 进度环形指示器 -->
          <div v-if="node.progress !== undefined" class="node-progress-row">
            <span class="progress-text">完成度 {{ node.progress }}%</span>
          </div>

          <div class="node-actions">
            <el-button
              v-if="!node.completed"
              type="primary"
              size="small"
              @click.stop="$emit('start', node)"
            >
              {{ node.current ? '继续学习' : '开始学习' }}
            </el-button>
            <el-button v-if="node.completed" text size="small" @click.stop="$emit('review', node)">
              复习
            </el-button>
          </div>
        </div>
      </el-timeline-item>
    </el-timeline>
    <el-empty v-else description="该路径暂无学习节点" />
  </div>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'

/** 路径节点行（content/detail 等字段名存在历史分叉，故均可选） */
interface PathNodeRow {
  id?: number | string
  completedAt?: string
  time?: string
  completed?: boolean
  current?: boolean
  title?: string
  description?: string
  content?: string
  detail?: string
  progress?: number
}

/**
 * 学习路径 — Timeline 学习节点
 * <p>步骤时间线（状态/进度/完成度）与开始学习/复习操作。</p>
 */
defineProps({
  /** 路径节点列表 */
  pathNodes: { type: Array as PropType<PathNodeRow[]>, required: true },
  /** 节点尺寸映射（large/normal） */
  getNodeSize: { type: Function, required: true },
  /** 节点类型映射（success/warning/info） */
  getNodeType: { type: Function, required: true },
})

defineEmits(['node-click', 'start', 'review'])
</script>

<style scoped>
.timeline-section {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  margin-bottom: var(--ac-spacing-md);
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

.timeline-node-card {
  background: var(--ac-bg);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-md);
  transition: all var(--ac-transition-fast);
  cursor: default;
}

.timeline-node-card:hover {
  box-shadow: var(--ac-shadow-sm);
  border-left: 3px solid var(--ac-primary);
}

.node-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ac-spacing-xs);
}

.node-index {
  font-size: 12px;
  color: var(--ac-text-muted);
  font-weight: 500;
}

.node-status-tags {
  display: flex;
  gap: 4px;
  align-items: center;
}

.spinning-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--ac-warning);
  animation: spin-pulse 1.5s infinite;
  margin-left: 4px;
}

@keyframes spin-pulse {
  0%,
  100% {
    opacity: 1;
    transform: scale(1);
  }
  50% {
    opacity: 0.5;
    transform: scale(0.8);
  }
}

.node-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0 0 4px;
}

.node-desc {
  font-size: 13px;
  color: var(--ac-text-secondary);
  line-height: 1.6;
  margin: 0 0 var(--ac-spacing-sm);
}

.node-progress-row {
  margin-bottom: var(--ac-spacing-sm);
}

.progress-text {
  font-size: 12px;
  color: var(--ac-primary);
  font-weight: 500;
}

.node-actions {
  display: flex;
  gap: var(--ac-spacing-sm);
}
</style>
