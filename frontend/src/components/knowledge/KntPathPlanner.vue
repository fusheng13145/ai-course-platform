<template>
  <div :class="title ? 'chart-card path-card' : 'path-planner'">
    <div v-if="title" class="chart-title">
      <el-icon><Promotion /></el-icon>{{ title }}
      <el-button
        text
        type="primary"
        size="small"
        class="ml-auto"
        :loading="pathPlanning"
        :disabled="!pathSource || !pathTarget"
        @click="emit('plan')"
      >
        <el-icon><MagicStick /></el-icon>规划
      </el-button>
    </div>

    <div class="path-inputs">
      <el-select
        :model-value="pathSource"
        placeholder="起点知识点"
        class="path-select"
        filterable
        clearable
        @update:model-value="emit('update:source', $event)"
      >
        <el-option
          v-for="o in points"
          :key="o.value ?? o.id"
          :label="o.label ?? o.name"
          :value="o.value ?? o.id"
        />
      </el-select>
      <el-icon class="path-arrow"><Right /></el-icon>
      <el-select
        :model-value="pathTarget"
        placeholder="目标知识点"
        class="path-select"
        filterable
        clearable
        @update:model-value="emit('update:target', $event)"
      >
        <el-option
          v-for="o in points"
          :key="o.value ?? o.id"
          :label="o.label ?? o.name"
          :value="o.value ?? o.id"
        />
      </el-select>
      <el-input-number
        :model-value="pathMaxHops"
        :min="1"
        :max="5"
        :controls="false"
        placeholder="跳数（留空不限）"
        class="path-hops"
        @update:model-value="emit('update:hops', $event)"
      />
      <el-button
        v-if="!title"
        type="primary"
        :loading="pathPlanning"
        :disabled="!pathSource || !pathTarget"
        @click="emit('plan')"
      >
        <el-icon><MagicStick /></el-icon>规划路径
      </el-button>
    </div>

    <div v-if="pathPlanning" class="path-loading">
      <el-icon class="is-loading"><Loading /></el-icon> 正在计算最优路径…
    </div>

    <div v-else-if="pathNotFound" class="path-empty">
      <el-icon :size="28" color="var(--ac-warning)"><WarningFilled /></el-icon>
      <p>{{ pathError || '未找到从起点到目标的学习路径，请调整起点/终点或增大跳数' }}</p>
    </div>

    <div v-else-if="pathError" class="path-empty">
      <el-icon :size="28" color="var(--ac-danger)"><CircleCloseFilled /></el-icon>
      <p>{{ pathError }}</p>
    </div>

    <div v-else-if="pathPlan && pathPlan.found" class="path-result">
      <div class="path-meta">
        <el-tag size="small" type="info" effect="light">跳数 {{ pathPlan.hops }}</el-tag>
        <el-tag
          size="small"
          :type="pathPlan.mode === 'ENGINE' ? 'success' : 'warning'"
          effect="light"
        >
          {{ pathPlan.mode === 'ENGINE' ? '图谱引擎计算' : '本地 BFS 兜底' }}
        </el-tag>
      </div>
      <div class="path-chain">
        <template v-for="(node, idx) in pathPlan.path" :key="node?.id ?? idx">
          <span
            class="path-node"
            :class="{ start: idx === 0, end: idx === pathPlan.path.length - 1 }"
            >{{ node?.name || '未知' }}</span
          >
          <el-icon v-if="idx < pathPlan.path.length - 1" class="path-chain-arrow"
            ><Right
          /></el-icon>
        </template>
      </div>
      <p class="path-note">
        <el-icon><InfoFilled /></el-icon>{{ pathPlan.note }}
      </p>
      <p v-if="!title" class="path-hint">已在上方图谱中高亮该路径</p>
    </div>

    <div v-else class="path-empty muted">
      <el-icon :size="28" color="var(--ac-text-muted)"><Promotion /></el-icon>
      <p>选择起点与目标知识点后点击「{{ title ? '规划' : '规划路径' }}」</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  Promotion,
  MagicStick,
  Right,
  Loading,
  WarningFilled,
  CircleCloseFilled,
  InfoFilled,
} from '@element-plus/icons-vue'

/**
 * 学习路径规划面板（知识图谱域共享实现）
 * <p>学生课程图谱页与教师知识图谱页共用；差异以 props 表达：
 * `title`（非空则渲染卡片标题栏与标题栏按钮，为空则为裸面板 + 内联按钮）、
 * `points`（同时兼容 `{value,label}` 与 `{id,name}` 两种选项形状）。</p>
 */
defineProps({
  /** 候选知识点选项（{value,label} 或 {id,name}） */
  points: { type: Array, default: () => [] },
  /** 起点知识点 */
  pathSource: { type: [Number, String], default: null },
  /** 目标知识点 */
  pathTarget: { type: [Number, String], default: null },
  /** 最大跳数 */
  pathMaxHops: { type: [Number, String], default: null },
  /** 规划中 */
  pathPlanning: { type: Boolean, default: false },
  /** 规划结果 */
  pathPlan: { type: Object, default: null },
  /** 未找到路径 */
  pathNotFound: { type: Boolean, default: false },
  /** 错误文案 */
  pathError: { type: String, default: '' },
  /** 卡片标题（非空时渲染标题栏） */
  title: { type: String, default: '' },
})

const emit = defineEmits(['plan', 'update:source', 'update:target', 'update:hops'])
</script>

<style scoped>
.path-planner {
  padding: var(--ac-spacing-sm) 0;
}
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
.path-inputs {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.path-select {
  width: 220px;
}
.path-arrow {
  color: var(--ac-text-muted);
  font-size: 18px;
}
.path-hops {
  width: 132px;
}
.path-loading {
  margin-top: var(--ac-spacing-md);
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--ac-text-secondary);
  font-size: 13px;
}
.path-empty {
  margin-top: var(--ac-spacing-md);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: var(--ac-spacing-lg);
  color: var(--ac-text-secondary);
  text-align: center;
}
.path-empty.muted {
  color: var(--ac-text-muted);
}
.path-empty p {
  margin: 0;
  font-size: 13.5px;
  line-height: 1.6;
  max-width: 440px;
}
.path-result {
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
  color: var(--surface);
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
.path-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--ac-text-muted);
}
</style>
