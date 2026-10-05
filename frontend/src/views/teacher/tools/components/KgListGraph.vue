<template>
  <div class="kg-grid">
    <!-- 知识点列表 -->
    <div class="chart-card">
      <div class="chart-title">
        <el-icon><Files /></el-icon>知识点
        <el-tag size="small" type="info" effect="light" round>{{ points.length }}</el-tag>
        <el-button text type="primary" size="small" class="ml-auto" @click="emit('create')">
          <el-icon><Plus /></el-icon>新建
        </el-button>
      </div>
      <div v-loading="pointLoading" class="kp-list">
        <div v-for="p in points" :key="p.id" class="kp-item">
          <div class="kp-head">
            <span class="kp-name">{{ p.name }}</span>
            <el-tag size="small" :type="diffType(p.difficulty)" effect="light">{{
              diffText(p.difficulty)
            }}</el-tag>
          </div>
          <p class="kp-desc">{{ p.description || '暂无描述' }}</p>
          <div class="kp-foot">
            <el-rate v-model="p.importance" disabled :max="3" size="small" />
            <div class="kp-actions">
              <el-button text type="primary" size="small" @click="emit('edit', p)">编辑</el-button>
              <el-button text type="danger" size="small" @click="emit('remove', p)">删除</el-button>
            </div>
          </div>
        </div>
        <el-empty
          v-if="!pointLoading && points.length === 0"
          description="暂无知识点"
          :image-size="64"
        />
      </div>
    </div>

    <!-- 图谱可视化（SVG 环形布局） -->
    <div class="chart-card">
      <div class="chart-title">
        <el-icon><Share /></el-icon>图谱可视化
        <el-button
          text
          type="primary"
          size="small"
          class="ml-auto"
          :loading="graphLoading"
          @click="emit('refresh')"
        >
          <el-icon><Refresh /></el-icon>刷新
        </el-button>
      </div>
      <div v-loading="graphLoading" class="graph-wrap">
        <svg v-if="graphNodes.length" :viewBox="`0 0 ${graphSize} ${graphSize}`" class="kg-svg">
          <line
            v-for="(l, i) in graphLinks"
            v-show="nodePos[l.source] && nodePos[l.target]"
            :key="'l' + i"
            :x1="nodePos[l.source]?.x"
            :y1="nodePos[l.source]?.y"
            :x2="nodePos[l.target]?.x"
            :y2="nodePos[l.target]?.y"
            class="kg-link"
          />
          <g v-for="n in graphNodes" :key="n.id">
            <title>{{ n.name }}</title>
            <circle
              :cx="nodePos[n.id]?.x"
              :cy="nodePos[n.id]?.y"
              :r="n.r || 12"
              :fill="nodeColor(n)"
              class="kg-node"
            />
            <text
              v-if="n.pathHL || (n.importance ?? 0) >= 10"
              :x="nodePos[n.id]?.x"
              :y="(nodePos[n.id]?.y || 0) - (n.r || 12) - 5"
              class="kg-label"
            >
              {{ n.name }}
            </text>
          </g>
        </svg>
        <el-empty v-else description="暂无图谱数据" :image-size="64" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Files, Plus, Refresh, Share } from '@element-plus/icons-vue'

/**
 * 知识图谱：知识点列表 + SVG 图谱
 * <p>左侧知识点列表（难度标签/重要度/编辑删除），右侧环形布局 SVG 图谱
 * （节点按 core/pathHL 着色）；操作经事件上抛父组件。</p>
 */
defineProps({
  points: { type: Array, default: () => [] },
  pointLoading: { type: Boolean, default: false },
  diffType: { type: Function, required: true },
  diffText: { type: Function, required: true },
  graphLoading: { type: Boolean, default: false },
  graphSize: { type: Number, default: 640 },
  graphNodes: { type: Array, default: () => [] },
  graphLinks: { type: Array, default: () => [] },
  nodePos: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['create', 'edit', 'remove', 'refresh'])

/** 节点配色：路径高亮优先，否则按难度（后端已归一为大写 EASY/MEDIUM/HARD） */
function nodeColor(n) {
  if (n.pathHL) return 'var(--ac-warning)'
  const d = (n.difficultyLevel || n.difficulty || '').toUpperCase()
  if (d === 'HARD') return 'var(--ac-danger)'
  if (d === 'EASY') return 'var(--ac-success)'
  return 'var(--ac-primary)'
}
</script>

<style scoped>
.kg-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--ac-spacing-md);
}
.chart-card {
  padding: var(--ac-spacing-lg);
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
}
.chart-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-md);
}
.ml-auto {
  margin-left: auto;
}

.kp-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 460px;
  overflow-y: auto;
}
.kp-item {
  padding: 12px 14px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-surface-2);
}
.kp-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.kp-name {
  font-weight: 600;
  color: var(--ac-text-primary);
  font-size: 14px;
}
.kp-desc {
  margin: 6px 0 10px;
  font-size: 12.5px;
  color: var(--ac-text-muted);
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.kp-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.kp-actions {
  display: flex;
  gap: 4px;
}

.graph-wrap {
  width: 100%;
  min-height: 520px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.kg-svg {
  width: 100%;
  max-width: 640px;
  height: auto;
}
.kg-link {
  stroke: var(--ac-border-strong);
  stroke-width: 1;
  stroke-opacity: 0.28;
}
.kg-node {
  stroke: var(--ac-on-primary);
  stroke-width: 1.5;
  transition: fill 0.2s ease;
}
.kg-label {
  font-size: 12px;
  font-weight: 600;
  fill: var(--ac-text-secondary);
  text-anchor: middle;
  paint-order: stroke;
  stroke: var(--ac-bg-elevated);
  stroke-width: 3px;
}

@media (max-width: 1100px) {
  .kg-grid {
    grid-template-columns: 1fr;
  }
}
</style>
