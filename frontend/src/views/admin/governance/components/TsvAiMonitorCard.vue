<template>
  <div class="content-card">
    <div class="card-title">
      <el-icon><WarnTriangleFilled /></el-icon>AI 异常监控
      <span class="card-meta"
        >近 50 条登录 / 操作日志 · 登录 {{ aiMonitor.loginCount ?? 0 }} · 操作
        {{ aiMonitor.operationCount ?? 0 }}</span
      >
    </div>
    <div v-if="aiAlerts.length" class="alert-list">
      <div v-for="(a, i) in aiAlerts" :key="i" class="alert-row" :class="'lv-' + a.level">
        <el-tag size="small" :type="alertType(a.level)" effect="dark">{{ a.level }}</el-tag>
        <div class="alert-body">
          <div class="alert-title">{{ a.title }}</div>
          <div v-if="a.detail" class="alert-detail">{{ a.detail }}</div>
        </div>
      </div>
    </div>
    <el-empty
      v-else
      :description="aiMonitor.error ? 'AI 分析暂不可用' : '未检测到异常'"
      :image-size="70"
    />
  </div>
</template>

<script setup>
import { WarnTriangleFilled } from '@element-plus/icons-vue'

/**
 * 教学监管 — AI 异常监控卡
 * <p>登录/操作日志计数 + 异常告警列表（按风险等级着色）。</p>
 */
defineProps({
  /** AI 监控汇总（loginCount/operationCount/alerts/error） */
  aiMonitor: { type: Object, required: true },
  /** 告警列表 */
  aiAlerts: { type: Array, required: true },
  /** 等级 → 标签色函数 */
  alertType: { type: Function, required: true },
})
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
.card-meta {
  font-size: 12px;
  color: var(--ac-text-muted);
  font-weight: 400;
  margin-left: auto;
}

.alert-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.alert-row {
  display: flex;
  gap: 10px;
  align-items: flex-start;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid var(--ac-border);
  background: var(--ac-bg-base, #fff);
}
.alert-row.lv-HIGH {
  border-color: rgba(176, 80, 69, 0.4);
  background: rgba(176, 80, 69, 0.06);
}
.alert-row.lv-MEDIUM {
  border-color: rgba(180, 140, 20, 0.35);
  background: rgba(180, 140, 20, 0.06);
}
.alert-body {
  flex: 1;
}
.alert-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
}
.alert-detail {
  font-size: 12px;
  color: var(--ac-text-muted);
  margin-top: 3px;
  line-height: 1.5;
}
</style>
