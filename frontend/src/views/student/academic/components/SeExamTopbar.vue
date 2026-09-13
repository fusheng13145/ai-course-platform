<template>
  <div class="exam-topbar">
    <div class="topbar-left">
      <el-button text @click="emit('exit')">
        <el-icon><ArrowLeft /></el-icon> 退出考试
      </el-button>
      <h2 class="exam-paper-title">{{ paper?.title || '试卷' }}</h2>
    </div>
    <div class="topbar-center">
      <div class="question-nav">
        <button
          v-for="(q, idx) in questions"
          :key="q.id"
          class="nav-dot"
          :class="{
            active: currentIndex === idx,
            answered: isQuestionAnswered(idx),
            current: currentIndex === idx,
          }"
          @click="emit('navigate', idx)"
        >
          {{ idx + 1 }}
        </button>
      </div>
    </div>
    <div class="topbar-right">
      <div class="countdown-box" :class="{ warning: remainingMinutes <= 5 }">
        <el-icon><AlarmClock /></el-icon>
        <span class="countdown-text">{{ countdownDisplay }}</span>
      </div>
      <el-button type="success" @click="emit('submit')">提交试卷</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft, AlarmClock } from '@element-plus/icons-vue'

/**
 * 考试答题视图顶部栏（纯展示 + 透传）。
 * 退出/提交经事件上抛，题目导航 dot 经 navigate(idx) 上抛。
 */
defineProps({
  paper: { type: Object, default: null },
  questions: { type: Array, default: () => [] },
  currentIndex: { type: Number, default: 0 },
  remainingMinutes: { type: Number, default: 0 },
  countdownDisplay: { type: String, default: '00:00' },
  isQuestionAnswered: { type: Function, required: true },
})

const emit = defineEmits(['exit', 'submit', 'navigate'])
</script>

<style scoped>
/* ====== 答题视图顶部栏样式 ====== */
.exam-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 24px;
  background: var(--ac-bg-elevated);
  border-bottom: 1px solid var(--ac-border);
  box-shadow: var(--ac-shadow-sm);
  gap: var(--ac-spacing-md);
  flex-shrink: 0;
}

.topbar-left {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-md);
  min-width: 200px;
}

.exam-paper-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 300px;
}

.topbar-center {
  flex: 1;
  display: flex;
  justify-content: center;
}

.question-nav {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  justify-content: center;
  max-width: 600px;
}

.nav-dot {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  border: 1px solid var(--ac-border);
  background: var(--ac-bg-elevated);
  color: var(--ac-text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--ac-transition-fast);
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-dot:hover {
  border-color: var(--ac-primary);
  color: var(--ac-primary);
}
.nav-dot.active {
  background: var(--ac-primary);
  color: var(--surface);
  border-color: var(--ac-primary);
}
.nav-dot.answered:not(.active) {
  background: var(--ok-soft);
  border-color: var(--ok);
  color: var(--ok);
}
.nav-dot.current {
  box-shadow: 0 0 0 2px var(--ac-primary-light);
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-md);
  min-width: 200px;
  justify-content: flex-end;
}

.countdown-box {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: var(--ac-radius-sm);
  background: var(--ac-bg);
  font-weight: 600;
  color: var(--ac-text-primary);
  font-size: 16px;
  font-variant-numeric: tabular-nums;
}

.countdown-box.warning {
  background: var(--accent-soft);
  color: var(--accent);
  animation: blink 1s infinite;
}

@keyframes blink {
  50% {
    opacity: 0.7;
  }
}

.countdown-text {
  letter-spacing: 1px;
}

@media (max-width: 900px) {
  .exam-topbar {
    flex-wrap: wrap;
    padding: 10px 16px;
  }
  .topbar-left,
  .topbar-right {
    min-width: auto;
    flex: 1;
  }
  .exam-paper-title {
    max-width: 150px;
    font-size: 14px;
  }
}
</style>
