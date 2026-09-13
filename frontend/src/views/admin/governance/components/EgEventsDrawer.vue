<template>
  <el-drawer
    :model-value="modelValue"
    title="违规事件核查"
    size="460px"
    append-to-body
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div v-if="eventsLoading" v-loading="true" style="height: 200px"></div>
    <template v-else>
      <div class="event-table">
        <div v-for="(e, i) in events" :key="i" class="event-row">
          <div class="event-head">
            <el-tag size="small" :type="riskTag(e.severity)" effect="light">{{
              typeText(e.type)
            }}</el-tag>
            <el-tag size="small" :type="riskTag(e.severity)" effect="plain">{{
              e.severity
            }}</el-tag>
            <el-tag v-if="e.status" size="small" :type="statusTag(e.status)" effect="dark">{{
              statusText(e.status)
            }}</el-tag>
            <span class="event-time">{{ formatTime(e.capturedAt, '—') }}</span>
          </div>
          <div v-if="e.detail" class="event-detail">{{ e.detail }}</div>
          <div v-if="e.reviewNote" class="event-review-note">
            <b>复核结论：</b>{{ e.reviewNote }}
          </div>
          <div v-if="canReview(e)" class="event-actions">
            <el-button
              size="small"
              type="success"
              plain
              :loading="reviewingId === e.id && reviewAction === 'REVIEWED'"
              @click="$emit('review', e, 'REVIEWED')"
              >确认违规</el-button
            >
            <el-button
              size="small"
              type="info"
              plain
              :loading="reviewingId === e.id && reviewAction === 'EXEMPTED'"
              @click="$emit('review', e, 'EXEMPTED')"
              >标记豁免</el-button
            >
            <el-link
              v-if="e.evidenceUrl"
              type="primary"
              :href="e.evidenceUrl"
              target="_blank"
              :underline="false"
              >查看证据</el-link
            >
          </div>
        </div>
        <el-empty v-if="!events.length" description="该会话暂无违规事件" :image-size="60" />
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
/**
 * 考试治理 — 违规事件核查抽屉
 * <p>事件列表（类型/严重度/状态/时间/证据链接）+ 确认违规/标记豁免复核操作。</p>
 */
defineProps({
  /** 抽屉显隐（v-model） */
  modelValue: { type: Boolean, default: false },
  /** 事件列表 */
  events: { type: Array, required: true },
  /** 事件加载态 */
  eventsLoading: { type: Boolean, default: false },
  /** 正在复核的事件 id */
  reviewingId: { type: [String, Number], default: null },
  /** 当前复核动作（REVIEWED/EXEMPTED） */
  reviewAction: { type: String, default: '' },
  /** 是否可复核 */
  canReview: { type: Function, required: true },
  /** 风险等级 → 标签色 */
  riskTag: { type: Function, required: true },
  /** 事件类型 → 文本 */
  typeText: { type: Function, required: true },
  /** 状态 → 文本 */
  statusText: { type: Function, required: true },
  /** 状态 → 标签色 */
  statusTag: { type: Function, required: true },
  /** 时间格式化函数 */
  formatTime: { type: Function, required: true },
})

defineEmits(['update:modelValue', 'review'])
</script>

<style scoped>
.event-table {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.event-row {
  border: 1px solid var(--ac-border);
  border-radius: 8px;
  padding: 12px;
}
.event-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.event-time {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-left: auto;
}
.event-detail {
  font-size: 12px;
  color: var(--ac-text-secondary, var(--ink-2));
  margin-top: 8px;
  line-height: 1.5;
}
.event-review-note {
  font-size: 12px;
  color: var(--ac-text-secondary, var(--ink-2));
  margin-top: 8px;
  line-height: 1.5;
}
.event-review-note b {
  color: var(--ac-text-primary);
}
.event-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
}
</style>
