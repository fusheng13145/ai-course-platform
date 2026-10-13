<template>
  <!-- 左侧：知识图谱可视化 -->
  <div class="graph-panel">
    <div class="panel-header">
      <h3 class="panel-title">知识图谱</h3>
      <span class="node-count">{{ filteredNodeCount }} 个知识点</span>
    </div>
    <div v-loading="graphLoading" class="graph-container">
      <v-chart
        v-if="!graphLoading && graphOption.series?.[0]?.data?.length > 0"
        :option="graphOption"
        class="graph-chart"
        autoresize
        @click="emit('node-click', $event)"
      />
      <el-empty
        v-else-if="
          !graphLoading &&
          (!graphOption.series?.[0]?.data || graphOption.series[0].data.length === 0)
        "
        :description="emptyText"
        :image-size="100"
      >
        <template #image><div class="empty-deco">🕸️</div></template>
      </el-empty>
    </div>
    <!-- 图例说明 -->
    <div class="graph-legend">
      <span class="legend-item"><i class="legend-dot"></i> 前置关系（虚线）</span>
      <span class="legend-item"><i class="legend-dot solid"></i> 关联关系（实线）</span>
    </div>
  </div>

  <!-- 右侧：详情面板 -->
  <div class="detail-panel">
    <div class="panel-header">
      <h3 class="panel-title">知识点详情</h3>
    </div>

    <div v-if="selectedNode" class="detail-content">
      <div class="detail-name-row">
        <el-tag :type="getDifficultyTagType(selectedNode.difficultyLevel)" size="small">
          {{ getDifficultyLabel(selectedNode.difficultyLevel) }}
        </el-tag>
        <h4 class="detail-name">{{ selectedNode.name }}</h4>
      </div>
      <p class="detail-desc">{{ selectedNode.description || '暂无描述' }}</p>

      <!-- 掌握度进度条 -->
      <div class="mastery-section">
        <div class="mastery-header">
          <span class="mastery-label">掌握度</span>
          <span class="mastery-value">{{ selectedNode.mastery ?? 0 }}%</span>
        </div>
        <el-progress
          :percentage="selectedNode.mastery ?? 0"
          :stroke-width="10"
          :color="getMasteryColor(selectedNode.mastery)"
          :show-text="false"
        />
      </div>

      <!-- 前置知识点 -->
      <div v-if="prerequisiteNodes.length > 0" class="relation-section">
        <h5 class="relation-title">
          <el-icon><ArrowLeft /></el-icon> 前置知识点
        </h5>
        <div class="relation-list">
          <span
            v-for="node in prerequisiteNodes"
            :key="node.id"
            class="relation-tag"
            @click="emit('select', node.id)"
          >
            {{ node.name }}
          </span>
        </div>
      </div>

      <!-- 后续知识点 -->
      <div v-if="subsequentNodes.length > 0" class="relation-section">
        <h5 class="relation-title">
          后续知识点<el-icon><ArrowRight /></el-icon>
        </h5>
        <div class="relation-list">
          <span
            v-for="node in subsequentNodes"
            :key="node.id"
            class="relation-tag subsequent"
            @click="emit('select', node.id)"
          >
            {{ node.name }}
          </span>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="detail-actions">
        <el-button type="primary" size="small" @click="emit('learn')">
          <el-icon><Reading /></el-icon> 开始学习
        </el-button>
      </div>
    </div>

    <div v-else class="empty-detail">
      <el-icon :size="40" color="var(--ac-text-muted)"><Share /></el-icon>
      <p>点击图谱中的节点<br />查看知识点详情</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft, ArrowRight, Reading, Share } from '@element-plus/icons-vue'

/**
 * 学生知识图谱：图谱可视化 + 节点详情面板
 * <p>左侧力导向图（难度着色/路径高亮/图例），右侧选中节点详情
 * （掌握度进度、前置/后续知识点跳转、开始学习）。</p>
 */
defineProps({
  graphLoading: { type: Boolean, default: false },
  graphOption: { type: Object, required: true },
  filteredNodeCount: { type: Number, default: 0 },
  selectedNode: { type: Object, default: null },
  prerequisiteNodes: { type: Array, default: () => [] },
  subsequentNodes: { type: Array, default: () => [] },
  getDifficultyTagType: { type: Function, required: true },
  getDifficultyLabel: { type: Function, required: true },
  getMasteryColor: { type: Function, required: true },
  /** 图谱空态文案（失败时由页面传入「未能加载」文案，默认保留原语义） */
  emptyText: { type: String, default: '请选择课程查看知识图谱' },
})

const emit = defineEmits(['node-click', 'select', 'learn'])
</script>

<style scoped>
.graph-panel,
.detail-panel {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  overflow: hidden;
}
.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: var(--ac-spacing-md) var(--ac-spacing-lg);
  border-bottom: 1px solid var(--ac-border);
}
.panel-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0;
}
.node-count {
  font-size: 12px;
  color: var(--ac-text-muted);
  background: var(--ac-bg);
  padding: 2px 10px;
  border-radius: 999px;
}
.graph-container {
  padding: var(--ac-spacing-md);
  min-height: 520px;
}
.graph-chart {
  width: 100%;
  height: 520px;
}
.graph-legend {
  display: flex;
  justify-content: center;
  gap: var(--ac-spacing-lg);
  padding: var(--ac-spacing-sm) var(--ac-spacing-lg) var(--ac-spacing-md);
  border-top: 1px solid var(--ac-border);
}
.legend-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--ac-text-muted);
}
.legend-dot {
  display: inline-block;
  width: 16px;
  height: 3px;
  border-radius: 2px;
  background: var(--info);
}
.legend-dot.solid {
  background: var(--warn);
}
.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}

/* 详情面板 */
.detail-content {
  padding: var(--ac-spacing-lg);
}
.detail-name-row {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
  margin-bottom: var(--ac-spacing-sm);
}
.detail-name {
  font-size: 17px;
  font-weight: 700;
  color: var(--ac-text-primary);
  margin: 0;
}
.detail-desc {
  font-size: 13px;
  color: var(--ac-text-secondary);
  line-height: 1.7;
  margin-bottom: var(--ac-spacing-md);
}
.mastery-section {
  margin-bottom: var(--ac-spacing-lg);
}
.mastery-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}
.mastery-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--ac-text-secondary);
}
.mastery-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-primary);
}
.relation-section {
  margin-bottom: var(--ac-spacing-md);
}
.relation-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--ac-text-secondary);
  margin: 0 0 var(--ac-spacing-sm);
  display: flex;
  align-items: center;
  gap: 4px;
}
.relation-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.relation-tag {
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 12px;
  background: var(--ac-primary-bg);
  color: var(--ac-primary-dark);
  cursor: pointer;
  transition: all var(--ac-transition-fast);
  border: 1px solid transparent;
}
.relation-tag:hover {
  background: var(--ac-primary-light);
  color: var(--surface);
  border-color: var(--ac-primary-light);
}
.relation-tag.subsequent {
  background: var(--violet-soft);
  color: var(--violet);
}
.relation-tag.subsequent:hover {
  background: var(--violet);
  color: var(--surface);
}
.detail-actions {
  margin-top: var(--ac-spacing-lg);
  padding-top: var(--ac-spacing-md);
  border-top: 1px solid var(--ac-border);
}
.empty-detail {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 400px;
  color: var(--ac-text-muted);
  text-align: center;
}
.empty-detail p {
  margin-top: var(--ac-spacing-sm);
  font-size: 14px;
  line-height: 1.6;
}
</style>
