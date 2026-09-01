<template>
  <div class="path-planner">
    <div class="path-inputs">
      <el-select
        :model-value="pathSource"
        placeholder="起点知识点"
        class="path-select"
        filterable
        clearable
        @update:model-value="emit('update:source', $event)"
      >
        <el-option v-for="o in allPointOptions" :key="o.value" :label="o.label" :value="o.value" />
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
        <el-option v-for="o in allPointOptions" :key="o.value" :label="o.label" :value="o.value" />
      </el-select>
      <el-input-number
        :model-value="pathMaxHops"
        :min="1"
        :max="6"
        :controls="false"
        placeholder="跳数"
        class="path-hops"
        @update:model-value="emit('update:hops', $event)"
      />
      <el-button
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
        <el-tag size="small" :type="pathPlan.mode === 'ENGINE' ? 'success' : 'warning'" effect="light">
          {{ pathPlan.mode === 'ENGINE' ? '图谱引擎计算' : '本地 BFS 兜底' }}
        </el-tag>
      </div>
      <div class="path-chain">
        <template v-for="(node, idx) in pathPlan.path" :key="node?.id ?? idx">
          <span class="path-node" :class="{ start: idx === 0, end: idx === pathPlan.path.length - 1 }"
            >{{ node?.name || '未知' }}</span
          >
          <el-icon v-if="idx < pathPlan.path.length - 1" class="path-chain-arrow"><Right /></el-icon>
        </template>
      </div>
      <p class="path-note"><el-icon><InfoFilled /></el-icon>{{ pathPlan.note }}</p>
      <p class="path-hint">已在上方图谱中高亮该路径</p>
    </div>

    <div v-else-if="!pathSource || !pathTarget" class="path-empty muted">
      <el-icon :size="28" color="var(--ac-text-muted)"><Promotion /></el-icon>
      <p>选择起点与目标知识点后点击「规划路径」</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { Right, MagicStick, Loading, WarningFilled, CircleCloseFilled, InfoFilled, Promotion } from '@element-plus/icons-vue'

/**
 * 学生学习路径规划面板
 * <p>起点/目标/跳数选择 + 规划结果（引擎/BFS 模式标注、路径链展示）；
 * 状态经 props 注入、操作经事件上抛（useStudentKnowledgeGraph）。</p>
 */
defineProps({
  allPointOptions: { type: Array, default: () => [] },
  pathSource: { type: [Number, String], default: null },
  pathTarget: { type: [Number, String], default: null },
  pathMaxHops: { type: [Number, String], default: null },
  pathPlanning: { type: Boolean, default: false },
  pathPlan: { type: Object, default: null },
  pathNotFound: { type: Boolean, default: false },
  pathError: { type: String, default: '' },
})

const emit = defineEmits(['plan', 'update:source', 'update:target', 'update:hops'])
</script>

<style scoped>
.path-planner {
  padding: var(--ac-spacing-sm) 0;
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
.path-hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--ac-text-muted);
}
</style>
