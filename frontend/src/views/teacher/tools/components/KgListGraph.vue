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
        <v-chart v-if="hasGraph" :option="graphOption" autoresize class="kg-chart" />
        <el-empty v-else description="暂无图谱数据" :image-size="64" />
      </div>
      <p class="graph-hint">
        节点按力导向实时运动并自动收敛：可拖拽单个节点改变布局、按住空白处平移画布；大小代表重要度，颜色代表难度。
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Files, Plus, Refresh, Share } from '@element-plus/icons-vue'

/**
 * 知识图谱：知识点列表 + 力导向图谱
 * <p>左侧知识点列表（难度标签/重要度/编辑删除），右侧 ECharts 力导向图谱
 * （节点可拖拽、画布可缩放平移、按难度配色、按重要度定大小）；操作经事件上抛父组件。</p>
 */
defineProps({
  points: { type: Array, default: () => [] },
  pointLoading: { type: Boolean, default: false },
  diffType: { type: Function, required: true },
  diffText: { type: Function, required: true },
  graphLoading: { type: Boolean, default: false },
  graphOption: { type: Object, default: () => ({}) },
  hasGraph: { type: Boolean, default: false },
})

const emit = defineEmits(['create', 'edit', 'remove', 'refresh'])
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
  height: 520px;
}
.kg-chart {
  width: 100%;
  height: 100%;
}
.graph-hint {
  margin: 8px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--ac-text-muted);
  text-align: center;
}

@media (max-width: 1100px) {
  .kg-grid {
    grid-template-columns: 1fr;
  }
}
</style>
