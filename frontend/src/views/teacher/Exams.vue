<template>
  <div class="fade-in">
    <PageHeader title="试卷管理" subtitle="创建、发布与管理考试试卷" icon="doc" icon-bg="warn">
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>创建试卷
      </el-button>
    </PageHeader>

    <!-- 概览统计 -->
    <div class="stat-grid">
      <StatCard
        label="试卷总数"
        :value="total"
        icon="layers"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="本页条数"
        :value="examList.length"
        icon="list"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="本页草稿"
        :value="draftCount"
        icon="edit"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
      <StatCard
        label="本页已发布"
        :value="publishedCount"
        icon="checks"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
    </div>

    <!-- 工具栏 + 试卷列表 -->
    <ExamListPanel
      :exam-list="examList"
      :loading="loading"
      :search-text="searchText"
      :status-filter="statusFilter"
      :current-page="currentPage"
      :page-size="pageSize"
      :total="total"
      :get-status-type="getStatusType"
      :get-status-text="getStatusText"
      @update:search="searchText = $event"
      @update:status="statusFilter = $event"
      @search="loadExamList"
      @page-change="currentPage = $event"
      @detail="viewDetail"
      @edit="openEditDialog"
      @publish="handlePublish"
      @copy="handleCopy"
      @delete="handleDelete"
    />

    <!-- 创建/编辑试卷对话框 -->
    <ExamFormDialog
      ref="formRef"
      v-model="formDialogVisible"
      :is-edit="isEdit"
      :submitting="submitting"
      :exam-form="examForm"
      :form-rules="formRules"
      :course-options="courseOptions"
      :selected-questions="selectedQuestions"
      :total-selected-score="totalSelectedScore"
      :get-question-type-name="getQuestionTypeName"
      @open-picker="questionPickerVisible = true"
      @remove-question="removeQuestion"
      @submit="submitForm"
    />

    <!-- 题库选题弹窗 -->
    <QuestionPickerDialog
      v-model="questionPickerVisible"
      :question-loading="questionLoading"
      :filtered-question-bank="filteredQuestionBank"
      :question-search="questionSearch"
      :question-type-filter="questionTypeFilter"
      :temp-selected-questions="tempSelectedQuestions"
      :is-question-selected="isQuestionSelected"
      :get-question-type-name="getQuestionTypeName"
      :get-difficulty-color="getDifficultyColor"
      :get-difficulty-text="getDifficultyText"
      @update:search="questionSearch = $event"
      @update:type="questionTypeFilter = $event"
      @search="loadQuestionBank"
      @selection-change="handleQuestionSelectionChange"
      @add-single="addSingleQuestion"
      @confirm="confirmAddQuestions"
    />

    <!-- 试卷详情对话框 -->
    <ExamDetailDialog
      v-model="detailDialogVisible"
      :current-exam="currentExam"
      :exam-detail-questions="examDetailQuestions"
      :get-status-type="getStatusType"
      :get-status-text="getStatusText"
      :get-question-type-name="getQuestionTypeName"
      @publish="handlePublish"
      @copy="handleCopy"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { Plus } from '@element-plus/icons-vue'
import { useTeacherExams } from '@/composables/useTeacherExams'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import ExamListPanel from './components/ExamListPanel.vue'
import ExamFormDialog from './components/ExamFormDialog.vue'
import QuestionPickerDialog from './components/QuestionPickerDialog.vue'
import ExamDetailDialog from './components/ExamDetailDialog.vue'

/**
 * 试卷管理页面（教师）
 * <p>页面壳：概览统计 + 列表/工具栏 + 创建编辑/题库选题/详情三个对话框编排；
 * 全部数据与业务逻辑由 useTeacherExams 承载。</p>
 */
const {
  loading,
  examList,
  searchText,
  statusFilter,
  currentPage,
  pageSize,
  total,
  formDialogVisible,
  isEdit,
  submitting,
  formRef,
  courseOptions,
  examForm,
  formRules,
  selectedQuestions,
  totalSelectedScore,
  draftCount,
  publishedCount,
  questionPickerVisible,
  questionLoading,
  questionSearch,
  questionTypeFilter,
  tempSelectedQuestions,
  filteredQuestionBank,
  detailDialogVisible,
  currentExam,
  examDetailQuestions,
  loadExamList,
  loadCourseOptions,
  openCreateDialog,
  openEditDialog,
  submitForm,
  loadQuestionBank,
  handleQuestionSelectionChange,
  isQuestionSelected,
  addSingleQuestion,
  removeQuestion,
  confirmAddQuestions,
  viewDetail,
  handlePublish,
  handleCopy,
  handleDelete,
  getStatusType,
  getStatusText,
  getQuestionTypeName,
  getDifficultyColor,
  getDifficultyText,
} = useTeacherExams()

onMounted(() => {
  loadExamList()
  loadCourseOptions()
})
</script>
