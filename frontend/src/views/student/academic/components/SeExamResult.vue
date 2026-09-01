<template>
  <div class="result-container">
    <div class="result-card">
      <div class="result-icon-wrapper" :class="getResultClass()">
        <el-icon :size="64"><TrophyBase /></el-icon>
      </div>
      <h2 class="result-title">考试完成</h2>
      <div class="score-display">
        <span class="score-number">{{ examResult.totalScore ?? '--' }}</span>
        <span class="score-total">/ {{ paper?.totalScore || 100 }}</span>
      </div>
      <p class="result-message">{{ resultMessage }}</p>
      <div class="result-actions">
        <el-button type="primary" @click="emit('back')">返回考试列表</el-button>
        <el-button v-if="examResult.id" @click="emit('detail')">查看详情</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { TrophyBase } from '@element-plus/icons-vue'

/**
 * 学生考试结果视图
 * <p>成绩结果卡片：成绩图标（按分数档位着色）+ 得分展示 + 结果提示 + 操作（返回/详情）。</p>
 */
defineProps({
  examResult: { type: Object, default: () => ({}) },
  paper: { type: Object, default: null },
  resultMessage: { type: String, default: '' },
  getResultClass: { type: Function, required: true },
})

const emit = defineEmits(['back', 'detail'])
</script>

<style scoped>
.result-container {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 400px;
}

.result-card {
  text-align: center;
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-xl);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-xl) var(--ac-spacing-xxl);
  max-width: 480px;
  box-shadow: var(--ac-shadow-lg);
}

.result-icon-wrapper {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto var(--ac-spacing-lg);
}

.result-icon-wrapper.excellent {
  background: var(--ok-soft);
  color: var(--ok);
}
.result-icon-wrapper.good {
  background: var(--accent-soft);
  color: var(--accent);
}
.result-icon-wrapper.pass {
  background: var(--warn-soft);
  color: var(--warn);
}
.result-icon-wrapper.fail {
  background: var(--accent-soft);
  color: var(--accent);
}

.result-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
  margin: 0 0 var(--ac-spacing-md);
}

.score-display {
  margin-bottom: var(--ac-spacing-md);
}

.score-number {
  font-size: 56px;
  font-weight: 700;
  color: var(--ac-primary);
  line-height: 1;
}

.score-total {
  font-size: 20px;
  color: var(--ac-text-muted);
  font-weight: 500;
}

.result-message {
  font-size: 14px;
  color: var(--ac-text-secondary);
  margin: 0 0 var(--ac-spacing-lg);
  line-height: 1.6;
}

.result-actions {
  display: flex;
  gap: var(--ac-spacing-md);
  justify-content: center;
}
</style>
