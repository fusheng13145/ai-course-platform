<template>
  <div class="content-card">
    <div class="card-title">
      <el-icon><DataAnalysis /></el-icon>会话风险详情
    </div>
    <div v-if="current" class="detail">
      <div class="detail-row">
        <span class="d-label">监考会话</span
        ><span class="d-val">试卷 #{{ current.examPaperId }} / 学生 #{{ current.studentId }}</span>
      </div>
      <div class="detail-row">
        <span class="d-label">风险评分</span>
        <span class="d-val"
          ><b :class="'score-' + (current.riskLevel || 'LOW')">{{
            current.riskScore ?? 0
          }}</b></span
        >
      </div>
      <div class="detail-row">
        <span class="d-label">风险等级</span>
        <span class="d-val">
          <el-tag size="small" :type="riskTag(current.riskLevel)" effect="dark">{{
            levelText(current.riskLevel)
          }}</el-tag>
          <el-tag
            v-if="current.status === 'FLAGGED'"
            size="small"
            type="danger"
            effect="plain"
            style="margin-left: 6px"
            >已标记</el-tag
          >
        </span>
      </div>
      <div class="detail-row">
        <span class="d-label">事件分布</span>
        <span class="d-val type-chips">
          <span v-for="(c, t) in current.typeCounts" :key="t" class="type-chip"
            >{{ typeText(t) }} ×{{ c }}</span
          >
          <span v-if="!current.typeCounts || !Object.keys(current.typeCounts).length" class="muted"
            >无</span
          >
        </span>
      </div>
      <el-button class="view-events" type="primary" plain @click="$emit('open-events', current)">
        <el-icon><Document /></el-icon>查看违规事件（{{ current.eventCount ?? 0 }}）
      </el-button>
    </div>
    <el-empty v-else description="请选择左侧监考会话查看风险详情" :image-size="70" />
  </div>
</template>

<script setup lang="ts">
import { DataAnalysis, Document } from '@element-plus/icons-vue'

/**
 * 考试治理 — 会话风险详情（右栏）
 * <p>会话信息/风险评分（按等级着色）/风险等级/事件分布 + 查看违规事件入口。</p>
 */
defineProps({
  /** 当前选中会话（含风险字段） */
  current: { type: Object, default: null },
  /** 风险等级 → 标签色 */
  riskTag: { type: Function, required: true },
  /** 风险等级 → 文本 */
  levelText: { type: Function, required: true },
  /** 事件类型 → 文本 */
  typeText: { type: Function, required: true },
})

defineEmits(['open-events'])
</script>

<style scoped>
.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 18px;
}
.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 14px;
}
.card-title .el-icon {
  color: var(--ac-primary, var(--info));
}

.detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.detail-row {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
}
.d-label {
  width: 84px;
  color: var(--ac-text-muted);
  flex-shrink: 0;
}
.d-val {
  color: var(--ac-text-primary);
}
.score-HIGH {
  color: var(--accent-strong);
}
.score-MEDIUM {
  color: var(--warn);
}
.score-LOW {
  color: var(--ok);
}
.type-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.type-chip {
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 12px;
  background: rgba(127, 127, 127, 0.1);
  color: var(--ac-text-secondary, var(--ink-2));
}
.muted {
  color: var(--ac-text-muted);
}
.view-events {
  margin-top: 6px;
  align-self: flex-start;
}
</style>
