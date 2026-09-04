<template>
  <div class="content-card">
    <div class="card-title">
      <el-icon><Bell /></el-icon>学习预警（全校掉队聚合）
      <el-select
        :model-value="riskDim"
        size="small"
        class="dim-sel"
        @update:model-value="$emit('update:riskDim', $event)"
        @change="$emit('load-risk')"
      >
        <el-option label="全校" value="school" />
        <el-option label="按课程" value="course" />
        <el-option label="按院系" value="department" />
        <el-option label="按专业" value="major" />
        <el-option label="按班级" value="class" />
      </el-select>
    </div>
    <div class="warn-rules">
      <div class="rule-item">
        <el-icon><Warning /></el-icon>课程进度 &lt; 40% 标记为低进度预警
      </div>
      <div class="rule-item">
        <el-icon><Warning /></el-icon>课程成绩 &lt; 60 分标记为成绩预警
      </div>
    </div>

    <div v-if="riskTotal > 0" class="risk-summary">
      <div class="rs-item">
        <span class="rs-val">{{ riskTotal }}</span
        ><span class="rs-lbl">学生选课人次</span>
      </div>
      <div class="rs-item danger">
        <span class="rs-val">{{ riskCount }}</span
        ><span class="rs-lbl">掉队预警人次</span>
      </div>
      <div class="rs-item">
        <span class="rs-val">{{ riskRate }}%</span><span class="rs-lbl">预警占比</span>
      </div>
    </div>

    <div v-if="riskGroups.length" class="risk-groups">
      <div v-for="g in riskGroups" :key="g.key" class="rg-row">
        <span class="rg-name">{{ g.label }}</span>
        <div class="rg-bar">
          <div class="rg-fill" :style="{ width: Math.min(100, g.atRiskRate) + '%' }"></div>
        </div>
        <span class="rg-cnt">{{ g.atRisk }} / {{ g.total }}（{{ g.atRiskRate }}%）</span>
      </div>
    </div>

    <div v-loading="riskLoading" class="risk-table">
      <div class="rt-head">
        <span class="c-name">学生</span>
        <span class="c-course">课程</span>
        <span class="c-prog">进度</span>
        <span class="c-score">成绩</span>
        <span class="c-level">等级</span>
      </div>
      <div v-for="(d, i) in riskDetails" :key="i" class="rt-row">
        <span class="c-name">{{ d.studentName }}</span>
        <span class="c-course">{{ d.courseName || '—' }}</span>
        <span class="c-prog">{{ d.progress }}%</span>
        <span class="c-score">{{ d.finalScore ?? '—' }}</span>
        <span class="c-level">
          <el-tag size="small" :type="d.level === 'HIGH' ? 'danger' : 'warning'" effect="dark">{{
            d.level === 'HIGH' ? '高危' : '中危'
          }}</el-tag>
        </span>
      </div>
      <el-empty
        v-if="!riskDetails.length"
        description="当前维度下无掉队预警学生"
        :image-size="60"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { Bell, Warning } from '@element-plus/icons-vue'

/**
 * 教学监管 — 学习预警卡
 * <p>维度下钻选择 + 预警规则说明 + 汇总/分组占比 + 预警明细表格。</p>
 */
defineProps({
  /** 预警维度（v-model：school/course/department/major/class） */
  riskDim: { type: String, default: 'school' },
  /** 预警加载态 */
  riskLoading: { type: Boolean, default: false },
  /** 选课总人次 */
  riskTotal: { type: Number, default: 0 },
  /** 掉队预警人次 */
  riskCount: { type: Number, default: 0 },
  /** 预警占比 */
  riskRate: { type: Number, default: 0 },
  /** 分组预警（label/atRisk/total/atRiskRate） */
  riskGroups: { type: Array, required: true },
  /** 预警明细（studentName/courseName/progress/finalScore/level） */
  riskDetails: { type: Array, required: true },
})

defineEmits(['update:riskDim', 'load-risk'])
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
.dim-sel {
  margin-left: auto;
  width: 130px;
}

.warn-rules {
  display: flex;
  gap: 24px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.rule-item {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--ac-text-secondary, #475569);
}
.rule-item .el-icon {
  color: #b8841e;
}

.risk-summary {
  display: flex;
  gap: 18px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.rs-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 18px;
  border-radius: 10px;
  background: rgba(127, 127, 127, 0.06);
  border: 1px solid var(--ac-border);
}
.rs-item.danger {
  background: rgba(176, 80, 69, 0.08);
  border-color: rgba(176, 80, 69, 0.3);
}
.rs-val {
  font-size: 24px;
  font-weight: 700;
  color: var(--ac-text-primary);
}
.rs-item.danger .rs-val {
  color: #b05045;
}
.rs-lbl {
  font-size: 12px;
  color: var(--ac-text-muted);
}

.risk-groups {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}
.rg-row {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
}
.rg-name {
  width: 110px;
  color: var(--ac-text-secondary, #475569);
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rg-bar {
  flex: 1;
  height: 8px;
  background: rgba(127, 127, 127, 0.1);
  border-radius: 4px;
  overflow: hidden;
}
.rg-fill {
  height: 100%;
  background: linear-gradient(90deg, #f59e0b, #b05045);
  border-radius: 4px;
  transition: width 0.4s ease;
}
.rg-cnt {
  width: 150px;
  text-align: right;
  color: var(--ac-text-primary);
  flex-shrink: 0;
}

.risk-table {
  border: 1px solid var(--ac-border);
  border-radius: 8px;
  overflow: hidden;
}
.rt-head,
.rt-row {
  display: grid;
  grid-template-columns: 1.2fr 1.4fr 0.8fr 0.8fr 0.8fr;
  gap: 10px;
  padding: 10px 12px;
  font-size: 13px;
  align-items: center;
}
.rt-head {
  background: var(--ac-bg-base, #fff);
  color: var(--ac-text-muted);
  font-weight: 600;
  border-bottom: 1px solid var(--ac-border);
}
.rt-row {
  border-bottom: 1px solid var(--ac-border);
}
.rt-row:last-child {
  border-bottom: none;
}
.c-name {
  color: var(--ac-text-primary);
  font-weight: 500;
}
.c-course,
.c-prog,
.c-score {
  color: var(--ac-text-secondary, #475569);
}
</style>
