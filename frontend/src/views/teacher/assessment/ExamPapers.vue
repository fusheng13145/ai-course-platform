<template>
  <div class="exam-papers fade-in">
    <PageHeader title="试卷管理" subtitle="创建、发布与统计您的考试试卷" icon="doc" icon-bg="warn">
      <el-button type="primary" @click="openCreateDialog"
        ><el-icon><Plus /></el-icon> 新建试卷</el-button
      >
    </PageHeader>

    <div class="stat-grid">
      <StatCard
        label="试卷总数"
        :value="total"
        icon="layers"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="已发布"
        :value="countBy('PUBLISHED')"
        icon="checks"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="草稿"
        :value="countBy('DRAFT')"
        icon="edit"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
      <StatCard
        label="已结束"
        :value="countBy('ENDED')"
        icon="clock"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
    </div>

    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico" style="background: var(--warn-soft); color: var(--warn)">
          <svg
            viewBox="0 0 24 24"
            width="17"
            height="17"
            fill="none"
            stroke="currentColor"
            stroke-width="1.9"
            stroke-linecap="round"
            stroke-linejoin="round"
          >
            <path
              d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2zM14 2v6h6"
            />
          </svg>
        </span>
        <div>
          <div class="l1-title">试卷列表</div>
          <div class="l1-sub">按课程筛选，支持题目查看与成绩统计</div>
        </div>
      </div>
      <div class="l1-body">
        <EpExamTable
          :loading="loading"
          :exam-list="examList"
          :course-list="courseList"
          :filters="filters"
          :total="total"
          :page="page"
          :page-size="pageSize"
          @update:course-id="filters.courseId = $event"
          @query="handleFilterChange"
          @update:page="page = $event"
          @update:page-size="pageSize = $event"
          @questions="openQuestionsDialog"
          @stats="openStatsDialog"
          @edit="openEditDialog"
          @publish="handlePublish"
          @delete="handleDelete"
        />
      </div>
    </section>

    <!-- 新建/编辑试卷对话框 -->
    <EpFormDialog
      v-model="formVisible"
      :is-edit="isEdit"
      :submitting="submitting"
      :form="form"
      :rules="rules"
      :course-list="courseList"
      @submit="submitForm"
    />

    <!-- 查看题目对话框 -->
    <EpQuestionsDialog
      v-model="questionsVisible"
      :current-exam="currentExam"
      :exam-questions="examQuestions"
    />

    <!-- 统计对话框 -->
    <EpStatsDialog v-model="statsVisible" :loading="statsLoading" :stats="examStats" />
  </div>
</template>

<script setup>
import { Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import EpExamTable from './components/EpExamTable.vue'
import EpFormDialog from './components/EpFormDialog.vue'
import EpQuestionsDialog from './components/EpQuestionsDialog.vue'
import EpStatsDialog from './components/EpStatsDialog.vue'
import { useExamPapers } from '@/composables/useExamPapers'

const {
  loading,
  examList,
  courseList,
  page,
  pageSize,
  total,
  filters,
  formVisible,
  isEdit,
  submitting,
  form,
  rules,
  questionsVisible,
  currentExam,
  examQuestions,
  statsVisible,
  statsLoading,
  examStats,
  countBy,
  handleFilterChange,
  openCreateDialog,
  openEditDialog,
  submitForm,
  handlePublish,
  handleDelete,
  openQuestionsDialog,
  openStatsDialog,
} = useExamPapers()
</script>

<style scoped>
.exam-papers {
  padding: 4px;
}
</style>
