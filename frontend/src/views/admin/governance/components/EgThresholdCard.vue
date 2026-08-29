<template>
  <div class="content-card">
    <div class="card-title">
      <el-icon><Operation /></el-icon>风险判定规则（管理员可调控）
      <el-button
        v-if="isAdmin"
        size="small"
        type="primary"
        plain
        class="save-thr"
        :loading="thrSaving"
        @click="$emit('save')"
      >
        <el-icon><Check /></el-icon>保存阈值
      </el-button>
    </div>
    <div class="rules">
      <div class="rule">
        <b>评分</b> = Σ 事件严重度（LOW ×{{ thresholds.weightLow }} / MEDIUM ×{{
          thresholds.weightMedium
        }}
        / HIGH ×{{ thresholds.weightHigh }}）
      </div>
      <div class="rule">
        <b>等级</b>：评分 ≥ {{ thresholds.high }} → HIGH；≥ {{ thresholds.medium }} → MEDIUM；其余
        LOW
      </div>
      <div class="rule"><b>自动处置</b>：达到 HIGH 时监考会话状态自动置为 FLAGGED</div>
    </div>
    <div v-if="isAdmin" class="thr-form">
      <div class="thr-item">
        <label>高风险阈值 (HIGH)</label>
        <el-input-number
          v-model="thresholds.high"
          :min="1"
          :max="50"
          controls-position="right"
        />
      </div>
      <div class="thr-item">
        <label>中风险阈值 (MEDIUM)</label>
        <el-input-number
          v-model="thresholds.medium"
          :min="1"
          :max="50"
          controls-position="right"
        />
      </div>
      <div class="thr-item">
        <label>LOW 事件权重</label>
        <el-input-number
          v-model="thresholds.weightLow"
          :min="0"
          :max="10"
          controls-position="right"
        />
      </div>
      <div class="thr-item">
        <label>MEDIUM 事件权重</label>
        <el-input-number
          v-model="thresholds.weightMedium"
          :min="0"
          :max="10"
          controls-position="right"
        />
      </div>
      <div class="thr-item">
        <label>HIGH 事件权重</label>
        <el-input-number
          v-model="thresholds.weightHigh"
          :min="0"
          :max="10"
          controls-position="right"
        />
      </div>
    </div>
    <el-alert
      v-else
      type="info"
      :closable="false"
      title="仅管理员可修改阈值，当前为只读视图"
      class="thr-readonly"
    />
  </div>
</template>

<script setup>
import { Operation, Check } from '@element-plus/icons-vue'

/**
 * 考试治理 — 风险判定规则卡
 * <p>规则说明 + 管理员阈值调节表单（共享 thresholds 对象编辑，嵌套属性符合 shallowOnly 规范）。</p>
 */
defineProps({
  /** 是否管理员（控制编辑/保存） */
  isAdmin: { type: Boolean, default: false },
  /** 阈值对象（共享引用：high/medium/weightLow/weightMedium/weightHigh） */
  thresholds: { type: Object, required: true },
  /** 保存加载态 */
  thrSaving: { type: Boolean, default: false },
})

defineEmits(['save'])
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
.card-title .save-thr {
  margin-left: auto;
}

.rules {
  display: flex;
  flex-direction: column;
  gap: 8px;
  font-size: 13px;
  color: var(--ac-text-secondary, #475569);
}
.rule b {
  color: var(--ac-text-primary);
}

.thr-form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: 12px 18px;
  margin-top: 14px;
}
.thr-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.thr-item label {
  font-size: 12px;
  color: var(--ac-text-muted);
}
.thr-readonly {
  margin-top: 14px;
}
</style>
