<template>
  <div class="fade-in">
    <!-- 加载失败提示：失败时列表被置空，仅剩"暂无考试"会让学生误判为"当前没有可参加的考试" -->
    <el-alert
      v-if="viewMode === 'list' && loadError"
      class="load-error"
      :title="loadError"
      type="error"
      :closable="false"
      show-icon
    />
    <!-- ===== 考试列表视图 ===== -->
    <SeExamList
      v-if="viewMode === 'list'"
      v-model:keyword="searchKeyword"
      v-model:status-filter="statusFilter"
      :exams="filteredExams"
      :loading="listLoading"
      :empty-text="loadError ? '考试列表未能加载，请查看上方提示后重试' : '暂无考试'"
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
      :submitting="submitting"
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

<script setup lang="ts">
import { useRoute } from 'vue-router'
import { useStudentExams } from '@/composables/useStudentExams'
import SeExamList from './components/SeExamList.vue'
import SeExamRunner from './components/SeExamRunner.vue'
import SeExamResult from './components/SeExamResult.vue'

/**
 * 学生考试页（页面壳）
 * <p>三视图（列表/答题/结果）分发与数据流编排，全部业务逻辑委托 useStudentExams。
 * 挂在课程路由 /student/courses/:id/exams 下时仅展示本课程考试。</p>
 */
const route = useRoute()
const {
  viewMode,
  searchKeyword,
  statusFilter,
  courseIdFilter,
  listLoading,
  filteredExams,
  totalExams,
  availableExams,
  completedExams,
  currentExamPaper,
  loadError,
  questions,
  questionsLoading,
  submitting,
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

// 课程上下文必须在 **setup 体内**、useStudentExams() 之后立刻赋值，不能放进本组件的 onMounted：
// composable 内部注册的 onMounted(loadExamList) 先于这里执行，若此时 courseIdFilter 仍是 null，
// 首次加载就会落到全局分页列表（第 1 页 10 条）→ 课程过滤形同虚设（详见 loadExamList 注释）。
const routeCourseId = route.params.id || route.query.courseId
if (routeCourseId) courseIdFilter.value = Number(routeCourseId)
</script>
