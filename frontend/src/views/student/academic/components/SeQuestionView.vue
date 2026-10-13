<template>
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
      <el-radio-group
        v-model="answers[currentIndex]"
        class="radio-group-vertical"
        :disabled="submitting"
      >
        <el-radio
          v-for="(opt, optIdx) in parseOptions(currentQuestion.options)"
          :key="optIdx"
          :value="optionLabel(optIdx)"
          class="option-radio"
        >
          <span class="option-label">{{ optionLabel(optIdx) }}.</span>
          {{ opt }}
        </el-radio>
      </el-radio-group>
    </div>

    <!-- 多选题 -->
    <div v-else-if="currentQuestion.type === 'MULTIPLE_CHOICE'" class="answer-area">
      <el-checkbox-group
        v-model="multiAnswers[currentIndex]"
        class="checkbox-group-vertical"
        :disabled="submitting"
      >
        <el-checkbox
          v-for="(opt, optIdx) in parseOptions(currentQuestion.options)"
          :key="optIdx"
          :label="optionLabel(optIdx)"
          class="option-checkbox"
        >
          <span class="option-label">{{ optionLabel(optIdx) }}.</span>
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
          :disabled="submitting"
          @click="answers[currentIndex] = true"
        >
          <el-icon><CircleCheck /></el-icon> 正确
        </el-button>
        <el-button
          :type="answers[currentIndex] === false ? 'danger' : ''"
          size="large"
          class="judge-btn"
          :disabled="submitting"
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
        :disabled="submitting"
      />
    </div>

    <!-- 底部导航按钮 -->
    <div class="question-nav-buttons">
      <el-button :disabled="submitting || currentIndex === 0" @click="emit('prev')">
        <el-icon><ArrowLeft /></el-icon> 上一题
      </el-button>
      <span class="question-counter">{{ currentIndex + 1 }} / {{ questions.length }}</span>
      <el-button
        v-if="currentIndex < questions.length - 1"
        type="primary"
        :disabled="submitting"
        @click="emit('next')"
      >
        下一题<el-icon><ArrowRight /></el-icon>
      </el-button>
      <el-button v-else type="success" :loading="submitting" @click="emit('submit')">
        {{ submitting ? '正在交卷' : '提交试卷' }}
      </el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft, ArrowRight, CircleCheck, CircleClose } from '@element-plus/icons-vue'

/**
 * 考试答题视图题目区（纯展示 + 透传）。
 * 题干渲染 + 单选/多选/判断/简答四种交互 + 底部上一题/下一题/提交。
 * 答题数据（answers/multiAnswers/textAnswers）为共享响应式对象经 props 注入（浅层编辑），
 * 上一题/下一题/提交经事件上抛父组件（useStudentExams）。
 */
defineProps({
  currentIndex: { type: Number, default: 0 },
  currentQuestion: { type: Object, default: null },
  questions: { type: Array, default: () => [] },
  answers: { type: Object, default: () => ({}) },
  multiAnswers: { type: Object, default: () => ({}) },
  textAnswers: { type: Object, default: () => ({}) },
  defaultScore: { type: Number, default: 10 },
  getQuestionTypeName: { type: Function, required: true },
  getQuestionTypeTag: { type: Function, required: true },
  parseOptions: { type: Function, required: true },
  isQuestionAnswered: { type: Function, required: true },
  submitting: { type: Boolean, default: false },
})

const emit = defineEmits(['prev', 'next', 'submit'])

/**
 * 选项字母（A/B/C…），同时用于展示与**作为提交值**。
 *
 * <p>必须是字母而不是下标：题库 `questions.correct_answers` 按字母存
 * （命题端 useQuestions 用 OPTION_LABELS 拼装、V1/V5/V6 种子与单测同为字母），
 * 而自动阅卷对单选题做的是 `studentAnswer.equalsIgnoreCase(correctAnswer)`。
 * 原先这里绑的是数组下标（0/1/2…），于是「学生明明选了 A、上报的是 0、
 * 与 A 永不相等」——客观题自动阅卷恒判错、得分恒 0，且交卷不可撤销。
 * 判断题不受影响（boolean 的字符串形态与种子存储的 true/false 一致）。</p>
 */
function optionLabel(idx: number): string {
  return idx < 26 ? String.fromCharCode(65 + idx) : String(idx)
}
</script>

<style scoped>
/* ====== 题目区样式 ====== */
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
  .question-area {
    padding: var(--ac-spacing-md);
  }
}
</style>
