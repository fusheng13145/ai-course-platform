<template>
  <div class="content-card session-list-card">
    <div class="card-title">
      <el-icon><VideoCamera /></el-icon>监考会话
    </div>
    <div class="session-list">
      <div
        v-for="s in sessions"
        :key="s.id"
        class="session-item"
        :class="{ active: selectedId === s.id }"
        @click="$emit('select', s)"
      >
        <div class="session-main">
          <span class="session-id">试卷 #{{ s.examPaperId }}</span>
          <span class="session-stu">学生 #{{ s.studentId }}</span>
        </div>
        <div class="session-sub">
          <span class="session-time">{{ formatTime(s.startTime, '—') }}</span>
          <el-tag v-if="s.riskLevel" size="small" :type="riskTag(s.riskLevel)" effect="light">
            {{ levelText(s.riskLevel) }} · {{ s.riskScore }}
          </el-tag>
        </div>
      </div>
      <el-empty
        v-if="!sessions.length"
        :description="loading ? '加载中…' : '当前无进行中的监考会话'"
        :image-size="70"
      />
    </div>
  </div>
</template>

<script setup>
import { VideoCamera } from '@element-plus/icons-vue'

/**
 * 考试治理 — 监考会话列表（左栏）
 * <p>会话（试卷/学生/时间）+ 风险等级标签，点击选择。</p>
 */
defineProps({
  /** 会话列表（含 enrich 后的风险字段） */
  sessions: { type: Array, required: true },
  /** 当前选中会话 id */
  selectedId: { type: [String, Number], default: null },
  /** 加载态（空态文案） */
  loading: { type: Boolean, default: false },
  /** 时间格式化函数 */
  formatTime: { type: Function, required: true },
  /** 风险等级 → 标签色 */
  riskTag: { type: Function, required: true },
  /** 风险等级 → 文本 */
  levelText: { type: Function, required: true },
})

defineEmits(['select'])
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
  color: var(--ac-primary, #3b82f6);
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 560px;
  overflow-y: auto;
}
.session-item {
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--ac-border);
  cursor: pointer;
  transition: all 0.15s ease;
  background: var(--ac-bg-base, #fff);
}
.session-item:hover {
  border-color: var(--ac-primary, #3b82f6);
}
.session-item.active {
  border-color: var(--ac-primary, #3b82f6);
  background: color-mix(in srgb, var(--ac-primary, #3b82f6) 8%, var(--ac-bg-elevated));
}
.session-main {
  display: flex;
  align-items: center;
  gap: 8px;
}
.session-id {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
}
.session-stu {
  font-size: 12px;
  color: var(--ac-text-muted);
}
.session-sub {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}
.session-time {
  font-size: 12px;
  color: var(--ac-text-muted);
}
</style>
