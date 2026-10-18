<template>
  <div class="exam-container">
    <!-- 顶部栏：倒计时 + 题目导航 + 提交 -->
    <SeExamTopbar
      :paper="paper"
      :questions="questions"
      :current-index="currentIndex"
      :remaining-minutes="remainingMinutes"
      :countdown-display="countdownDisplay"
      :submitting="submitting"
      :is-question-answered="isQuestionAnswered"
      @exit="emit('exit')"
      @submit="emit('submit')"
      @navigate="emit('navigate', $event)"
    />

    <!-- 题目区域（滚动容器 + 加载态） -->
    <div v-loading="questionsLoading" class="exam-body">
      <SeQuestionView
        :current-index="currentIndex"
        :current-question="currentQuestion"
        :questions="questions"
        :answers="answers"
        :multi-answers="multiAnswers"
        :text-answers="textAnswers"
        :default-score="defaultScore"
        :get-question-type-name="getQuestionTypeName"
        :get-question-type-tag="getQuestionTypeTag"
        :parse-options="parseOptions"
        :is-question-answered="isQuestionAnswered"
        :submitting="submitting"
        @prev="emit('prev')"
        @next="emit('next')"
        @submit="emit('submit')"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import SeExamTopbar from './SeExamTopbar.vue'
import SeQuestionView from './SeQuestionView.vue'

/**
 * 学生考试答题视图（全屏）壳组件。
 * <p>布局容器 + 子组件编排：顶部栏（SeExamTopbar）+ 题目区（SeQuestionView）。
 * 答题数据（answers/multiAnswers/textAnswers）与辅助函数经 props 注入子组件（浅层编辑），
 * 导航/提交/退出经事件上抛父组件（useStudentExams）。</p>
 */
defineProps({
  paper: { type: Object, default: null },
  questions: { type: Array, default: () => [] },
  questionsLoading: { type: Boolean, default: false },
  submitting: { type: Boolean, default: false },
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
/* ====== 答题视图外层容器 ====== */
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

/* ---- 答题主体滚动容器 ---- */
.exam-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--ac-spacing-xl);
  display: flex;
  justify-content: center;
}
</style>
