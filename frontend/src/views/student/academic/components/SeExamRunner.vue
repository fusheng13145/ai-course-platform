<template>
  <div class="exam-container">
    <!-- 顶部栏：倒计时 + 导航 -->
    <div class="exam-topbar">
      <div class="topbar-left">
        <el-button text @click="emit('exit')">
          <el-icon><ArrowLeft /></el-icon> 退出考试
        </el-button>
        <h2 class="exam-paper-title">{{ paper?.title || '试卷' }}</h2>
      </div>
      <div class="topbar-center">
        <!-- 题目导航 -->
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

    <!-- 题目区域 -->
    <div v-loading="questionsLoading" class="exam-body">
      <div v-if="currentQuestion" class="question-area">
        <!-- 题号和类型 -->
        <div class="question-label-row">
          <el-tag :type="getQuestionTypeTag(currentQuestion.type)" size="small">
            第 {{ currentIndex + 1 }} 题 · {{ getQuestionTypeName(currentQuestion.type) }}
          </el-tag>
          <span class="question-score">（{{ currentQuestion.score || defaultScore }} 分）</span>
        </div>

        <!-- 题干内容 -->
        <div class="question-content">
          {{ currentQuestion.content || currentQuestion.questionContent }}
        </div>

        <!-- 单选题 -->
        <div v-if="currentQuestion.type === 'SINGLE_CHOICE'" class="answer-area">
          <el-radio-group v-model="answers[currentIndex]" class="radio-group-vertical">
            <el-radio
              v-for="(opt, optIdx) in parseOptions(currentQuestion.options)"
              :key="optIdx"
              :value="optIdx"
              class="option-radio"
            >
              <span class="option-label">{{ ['A', 'B', 'C', 'D', 'E', 'F'][optIdx] }}.</span>
              {{ opt }}
            </el-radio>
          </el-radio-group>
        </div>

        <!-- 多选题 -->
        <div v-else-if="currentQuestion.type === 'MULTIPLE_CHOICE'" class="answer-area">
          <el-checkbox-group v-model="multiAnswers[currentIndex]" class="checkbox-group-vertical">
            <el-checkbox
              v-for="(opt, optIdx) in parseOptions(currentQuestion.options)"
              :key="optIdx"
              :label="optIdx"
              class="option-checkbox"
            >
              <span class="option-label">{{ ['A', 'B', 'C', 'D', 'E', 'F'][optIdx] }}.</span>
              {{ opt }}
            </el-checkbox>
          </el-checkbox-group>
        </div>

        <!-- 判断题 -->
        <div v-else-if="currentQuestion.type === 'TRUE_FALSE'" class="answer-area">
          <div class="judge-buttons">
            <el-button
              :type="answers[currentIndex] === true ? 'primary' : ''"
              size="large"
              class="judge-btn"
              @click="answers[currentIndex] = true"
            >
              <el-icon><CircleCheck /></el-icon> 正确
            </el-button>
            <el-button
              :type="answers[currentIndex] === false ? 'danger' : ''"
              size="large"
              class="judge-btn"
              @click="answers[currentIndex] = false"
            >
              <el-icon><CircleClose /></el-icon> 错误
            </el-button>
          </div>
        </div>

        <!-- 简答题 -->
        <div v-else-if="currentQuestion.type === 'SHORT_ANSWER'" class="answer-area">
          <el-input
            v-model="textAnswers[currentIndex]"
            type="textarea"
            :rows="6"
            placeholder="请输入您的答案..."
            resize="vertical"
          />
        </div>

        <!-- 底部导航按钮 -->
        <div class="question-nav-buttons">
          <el-button :disabled="currentIndex === 0" @click="emit('prev')">
            <el-icon><ArrowLeft /></el-icon> 上一题
          </el-button>
          <span class="question-counter">{{ currentIndex + 1 }} / {{ questions.length }}</span>
          <el-button
            v-if="currentIndex < questions.length - 1"
            type="primary"
            @click="emit('next')"
          >
            下一题<el-icon><ArrowRight /></el-icon>
          </el-button>
          <el-button v-else type="success" @click="emit('submit')"> 提交试卷 </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import {
  ArrowLeft,
  ArrowRight,
  AlarmClock,
  CircleCheck,
  CircleClose,
} from '@element-plus/icons-vue'

/**
 * 学生考试答题视图（全屏）
 * <p>顶部栏（退出/题目导航/倒计时/提交）+ 题目区（单选/多选/判断/简答四种交互 + 底部导航）。
 * 答题数据（answers/multiAnswers/textAnswers）为共享响应式对象经 props 注入（浅层编辑），
 * 导航/提交/退出经事件上抛父组件（useStudentExams）。</p>
 */
defineProps({
  paper: { type: Object, default: null },
  questions: { type: Array, default: () => [] },
  questionsLoading: { type: Boolean, default: false },
  currentIndex: { type: Number, default: 0 },
  currentQuestion: { type: Object, default: null },
  answers: { type: Object, default: () => ({}) },
  multiAnswers: { type: Object, default: () => ({}) },
  textAnswers: { type: Object, default: () => ({}) },
  defaultScore: { type: Number, default: 10 },
  remainingMinutes: { type: Number, default: 0 },
  countdownDisplay: { type: String, default: '00:00' },
  getQuestionTypeName: { type: Function, required: true },
  getQuestionTypeTag: { type: Function, required: true },
  parseOptions: { type: Function, required: true },
  isQuestionAnswered: { type: Function, required: true },
})

const emit = defineEmits(['exit', 'submit', 'navigate', 'prev', 'next'])
</script>

<style scoped>
/* ====== 答题视图样式 ====== */
.exam-container {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 1000;
  background: var(--ac-bg);
  display: flex;
  flex-direction: column;
}

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
  color: #fff;
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

/* ---- 答题主体 ---- */
.exam-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--ac-spacing-xl);
  display: flex;
  justify-content: center;
}

.question-area {
  max-width: 800px;
  width: 100%;
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-xl);
  box-shadow: var(--ac-shadow-sm);
}

.question-label-row {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
  margin-bottom: var(--ac-spacing-md);
}

.question-score {
  font-size: 13px;
  color: var(--ac-text-muted);
}

.question-content {
  font-size: 16px;
  line-height: 1.9;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-lg);
  font-weight: 500;
}

.answer-area {
  margin-bottom: var(--ac-spacing-xl);
}

.radio-group-vertical {
  display: flex;
  flex-direction: column;
  gap: var(--ac-spacing-sm);
}

.option-radio {
  display: flex;
  align-items: flex-start;
  padding: 12px 16px;
  border-radius: var(--ac-radius-sm);
  border: 1px solid var(--ac-border);
  margin-right: 0;
  transition: all var(--ac-transition-fast);
  width: 100%;
  height: auto;
}

.option-radio:hover {
  background: var(--ac-bg-hover);
  border-color: var(--ac-primary-light);
}

.option-radio.is-checked {
  background: var(--ac-primary-bg);
  border-color: var(--ac-primary);
}

.option-label {
  font-weight: 600;
  color: var(--ac-primary);
  margin-right: 8px;
  min-width: 22px;
}

.checkbox-group-vertical {
  display: flex;
  flex-direction: column;
  gap: var(--ac-spacing-sm);
}

.option-checkbox {
  display: flex;
  align-items: flex-start;
  padding: 12px 16px;
  border-radius: var(--ac-radius-sm);
  border: 1px solid var(--ac-border);
  margin-right: 0;
  transition: all var(--ac-transition-fast);
  width: 100%;
  height: auto;
}

.option-checkbox:hover {
  background: var(--ac-bg-hover);
  border-color: var(--ac-secondary-light);
}

.option-checkbox.is-checked {
  background: var(--violet-soft);
  border-color: var(--violet);
}

.judge-buttons {
  display: flex;
  gap: var(--ac-spacing-lg);
  justify-content: center;
  padding: var(--ac-spacing-lg) 0;
}

.judge-btn {
  min-width: 140px;
  height: 52px;
  font-size: 15px;
  border-radius: var(--ac-radius-md);
}

/* ---- 题目导航按钮 ---- */
.question-nav-buttons {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: var(--ac-spacing-lg);
  border-top: 1px solid var(--ac-border);
}

.question-counter {
  font-size: 14px;
  color: var(--ac-text-muted);
  font-weight: 500;
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
  .question-body {
    padding: var(--ac-spacing-md);
  }
}
</style>
