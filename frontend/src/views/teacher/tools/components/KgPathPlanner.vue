<template>
  <div class="chart-card path-card">
    <div class="chart-title">
      <el-icon><Promotion /></el-icon>学习路径规划
      <el-button
        text
        type="primary"
        size="small"
        class="ml-auto"
        :loading="tPathPlanning"
        :disabled="!tPathSource || !tPathTarget"
        @click="emit('plan')"
      >
        <el-icon><MagicStick /></el-icon>规划
      </el-button>
    </div>
    <div class="path-row">
      <el-select
        :model-value="tPathSource"
        placeholder="起点"
        class="path-sel"
        filterable
        clearable
        @update:model-value="emit('update:source', $event)"
      >
        <el-option v-for="p in points" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-icon class="path-arrow"><Right /></el-icon>
      <el-select
        :model-value="tPathTarget"
        placeholder="目标"
        class="path-sel"
        filterable
        clearable
        @update:model-value="emit('update:target', $event)"
      >
        <el-option v-for="p in points" :key="p.id" :label="p.name" :value="p.id" />
      </el-select>
      <el-input-number
        :model-value="tPathHops"
        :min="1"
        :max="6"
        :controls="false"
        placeholder="跳数"
        class="path-hops"
        @update:model-value="emit('update:hops', $event)"
      />
    </div>

    <div v-if="tPathPlanning" class="path-loading">
      <el-icon class="is-loading"><Loading /></el-icon> 规划中…
    </div>
    <div v-else-if="tPathNotFound" class="path-tip warn">
      <el-icon><WarningFilled /></el-icon><span>{{ tPathError || '未找到从起点到目标的学习路径' }}</span>
    </div>
    <div v-else-if="tPathError" class="path-tip danger">
      <el-icon><CircleCloseFilled /></el-icon><span>{{ tPathError }}</span>
    </div>
    <div v-else-if="tPathPlan && tPathPlan.found" class="path-out">
      <div class="path-meta">
        <el-tag size="small" type="info" effect="light">跳数 {{ tPathPlan.hops }}</el-tag>
        <el-tag
          size="small"
          :type="tPathPlan.mode === 'ENGINE' ? 'success' : 'warning'"
          effect="light"
          >{{ tPathPlan.mode === 'ENGINE' ? '图谱引擎' : '本地 BFS' }}</el-tag
        >
      </div>
      <div class="path-chain">
        <template v-for="(node, idx) in tPathPlan.path" :key="node?.id ?? idx">
          <span class="path-node" :class="{ start: idx === 0, end: idx === tPathPlan.path.length - 1 }"
            >{{ node?.name || '未知' }}</span
          >
          <el-icon v-if="idx < tPathPlan.path.length - 1" class="path-chain-arrow"><Right /></el-icon>
        </template>
      </div>
      <p class="path-note"><el-icon><InfoFilled /></el-icon>{{ tPathPlan.note }}</p>
    </div>
    <div v-else class="path-tip muted">
      <el-icon><Promotion /></el-icon><span>选择起点与目标知识点后点击「规划」</span>
    </div>
  </div>
</template>

<script setup>
import { Promotion, MagicStick, Right, Loading, WarningFilled, CircleCloseFilled, InfoFilled } from '@element-plus/icons-vue'

/**
 * 学习路径规划面板
 * <p>起点/目标/跳数选择 + 规划结果展示（引擎/BFS 模式标注、路径链、节点高亮联动由父处理）。</p>
 */
defineProps({
  points: { type: Array, default: () => [] },
  tPathSource: { type: [Number, String], default: null },
  tPathTarget: { type: [Number, String], default: null },
  tPathHops: { type: [Number, String], default: null },
  tPathPlanning: { type: Boolean, default: false },
  tPathPlan: { type: Object, default: null },
  tPathNotFound: { type: Boolean, default: false },
  tPathError: { type: String, default: '' },
})

const emit = defineEmits(['plan', 'update:source', 'update:target', 'update:hops'])
</script>

<style scoped>
.chart-card {
  padding: var(--ac-spacing-lg);
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
}
.path-card {
  margin-top: var(--ac-spacing-md);
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
.path-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.path-sel {
  flex: 1 1 160px;
  min-width: 140px;
}
.path-arrow {
  color: var(--ac-text-muted);
  font-size: 18px;
}
.path-hops {
  width: 96px;
}
.path-loading {
  margin-top: var(--ac-spacing-md);
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ac-text-secondary);
  font-size: 13px;
}
.path-tip {
  margin-top: var(--ac-spacing-md);
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  padding: var(--ac-spacing-md);
  border-radius: var(--ac-radius-md);
  background: var(--ac-bg-elevated);
}
.path-tip.warn {
  color: var(--ac-warning);
}
.path-tip.danger {
  color: var(--ac-danger);
}
.path-tip.muted {
  color: var(--ac-text-muted);
}
.path-tip span {
  line-height: 1.5;
}
.path-out {
  margin-top: var(--ac-spacing-md);
}
.path-meta {
  display: flex;
  gap: 8px;
  margin-bottom: var(--ac-spacing-md);
}
.path-chain {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: var(--ac-spacing-md);
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
}
.path-node {
  padding: 6px 14px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  background: var(--ac-primary-light);
  color: #fff;
}
.path-node.start {
  background: var(--ac-success);
}
.path-node.end {
  background: var(--ac-warning);
}
.path-chain-arrow {
  color: var(--ac-text-muted);
  font-size: 16px;
}
.path-note {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: var(--ac-spacing-md) 0 0;
  font-size: 12.5px;
  color: var(--ac-text-muted);
}
</style>
