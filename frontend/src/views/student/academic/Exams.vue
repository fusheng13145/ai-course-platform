<template>
  <div class="fade-in">
    <!-- ===== 考试列表视图 ===== -->
    <SeExamList
      v-if="viewMode === 'list'"
      v-model:keyword="searchKeyword"
      v-model:status-filter="statusFilter"
      :exams="filteredExams"
      :loading="listLoading"
      :total-exams="totalExams"
      :available-exams="availableExams"
      :completed-exams="completedExams"
      :get-exam-status-type="getExamStatusType"
      :get-exam-status-label="getExamStatusLabel"
      :format-time="formatTime"
      @enter="enterExam"
    />

    <!-- ===== 答题视图（全屏模式）===== -->
    <SeExamRunner
      v-else-if="viewMode === 'exam'"
      :paper="currentExamPaper"
      :questions="questions"
      :questions-loading="questionsLoading"
      :current-index="currentIndex"
      :current-question="currentQuestion"
      :answers="answers"
      :multi-answers="multiAnswers"
      :text-answers="textAnswers"
      :default-score="defaultScore"
      :remaining-minutes="remainingMinutes"
      :countdown-display="countdownDisplay"
      :get-question-type-name="getQuestionTypeName"
      :get-question-type-tag="getQuestionTypeTag"
      :parse-options="parseOptions"
      :is-question-answered="isQuestionAnswered"
      @exit="confirmExitExam"
      @submit="confirmSubmit"
      @navigate="goToQuestion"
      @prev="prevQuestion"
      @next="nextQuestion"
    />

    <!-- ===== 成绩结果视图 ===== -->
    <SeExamResult
      v-else-if="viewMode === 'result'"
      :exam-result="examResult"
      :paper="currentExamPaper"
      :result-message="resultMessage"
      :get-result-class="getResultClass"
      @back="backToList"
      @detail="viewDetailResult"
    />
  </div>
</template>

<script setup>
import { useStudentExams } from '@/composables/useStudentExams'
import SeExamList from './components/SeExamList.vue'
import SeExamRunner from './components/SeExamRunner.vue'
import SeExamResult from './components/SeExamResult.vue'

/**
 * 学生考试页（页面壳）
 * <p>三视图（列表/答题/结果）分发与数据流编排，全部业务逻辑委托 useStudentExams。</p>
 */
const {
  viewMode,
  searchKeyword,
  statusFilter,
  listLoading,
  filteredExams,
  totalExams,
  availableExams,
  completedExams,
  currentExamPaper,
  questions,
  questionsLoading,
  currentIndex,
  currentQuestion,
  answers,
  multiAnswers,
  textAnswers,
  defaultScore,
  remainingMinutes,
  countdownDisplay,
  examResult,
  resultMessage,
  formatTime,
  getExamStatusType,
  getExamStatusLabel,
  enterExam,
  goToQuestion,
  prevQuestion,
  nextQuestion,
  getQuestionTypeName,
  getQuestionTypeTag,
  parseOptions,
  isQuestionAnswered,
  confirmSubmit,
  confirmExitExam,
  backToList,
  viewDetailResult,
  getResultClass,
} = useStudentExams()
</script>
