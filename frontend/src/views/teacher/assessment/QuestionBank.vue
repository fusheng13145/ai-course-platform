<template>
  <div class="question-bank fade-in">
    <PageHeader
      title="题库管理"
      subtitle="维护课程题目，支持 AI 智能出题与审核流转"
      icon="layers"
      icon-bg="info"
    >
      <el-button @click="openAIDialog"
        ><el-icon><MagicStick /></el-icon> AI 生成</el-button
      >
      <el-button type="primary" @click="openCreateDialog"
        ><el-icon><Plus /></el-icon> 新建题目</el-button
      >
    </PageHeader>

    <div class="stat-grid">
      <StatCard
        label="题目总数"
        :value="total"
        icon="layers"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="待审核"
        :value="countByReview('PENDING')"
        icon="warn"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
      <StatCard
        label="已通过"
        :value="countByReview('APPROVED')"
        icon="checks"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="已驳回"
        :value="countByReview('REJECTED')"
        icon="edit"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
    </div>

    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico" style="background: var(--info-soft); color: var(--info)">
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
              d="m12 2 8.5 4.7L12 11.4 3.5 6.7 12 2zM3.5 12l8.5 4.7 8.5-4.7M3.5 17.3l8.5 4.7 8.5-4.7"
            />
          </svg>
        </span>
        <div>
          <div class="l1-title">题目列表</div>
          <div class="l1-sub">支持题型、难度与关键词多维筛选</div>
        </div>
      </div>
      <div class="l1-body">
        <QbFilterBar
          v-model:course-id="filters.courseId"
          v-model:type="filters.type"
          v-model:difficulty="filters.difficulty"
          v-model:keyword="filters.keyword"
          :course-list="courseList"
          @search="handleFilterChange"
        />

        <QbQuestionTable
          v-model:page="page"
          v-model:page-size="pageSize"
          :question-list="questionList"
          :loading="loading"
          :total="total"
          :get-type-text="getTypeText"
          :get-type-tag="getTypeTag"
          :get-difficulty-text="getDifficultyText"
          :get-difficulty-color="getDifficultyColor"
          :get-review-text="getReviewText"
          :get-review-tag="getReviewTag"
          @edit="openEditDialog"
          @review="openReviewDialog"
          @copy="handleCopy"
          @delete="handleDelete"
          @search="loadQuestions"
        />
      </div>
    </section>

    <!-- 新建/编辑题目对话框 -->
    <QbFormDialog
      ref="formRef"
      v-model="formVisible"
      :is-edit="isEdit"
      :form="form"
      :rules="rules"
      :course-list="courseList"
      :submitting="submitting"
      @save="submitForm"
    />

    <!-- 审核对话框 -->
    <QbReviewDialog
      v-model="reviewVisible"
      :review-form="reviewForm"
      :reviewing="reviewing"
      @submit="submitReview"
    />

    <!-- AI 生成对话框 -->
    <QbAiDialog
      v-model="aiVisible"
      :ai-form="aiForm"
      :course-list="courseList"
      :ai-generating="aiGenerating"
      @generate="generateByAI"
    />
  </div>
</template>

<script setup lang="ts">
import { Plus, MagicStick } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import { useQuestionBank } from '@/composables/useQuestionBank'
import QbFilterBar from './components/QbFilterBar.vue'
import QbQuestionTable from './components/QbQuestionTable.vue'
import QbFormDialog from './components/QbFormDialog.vue'
import QbReviewDialog from './components/QbReviewDialog.vue'
import QbAiDialog from './components/QbAiDialog.vue'

/**
 * 教师题库管理页（页面壳）
 * <p>统计卡 + 筛选/列表编排 + 三对话框装配，业务逻辑委托 useQuestionBank。</p>
 */
const {
  loading,
  questionList,
  courseList,
  page,
  pageSize,
  total,
  filters,
  formVisible,
  isEdit,
  submitting,
  formRef,
  form,
  rules,
  reviewVisible,
  reviewing,
  reviewForm,
  aiVisible,
  aiGenerating,
  aiForm,
  countByReview,
  handleFilterChange,
  openCreateDialog,
  openEditDialog,
  submitForm,
  handleDelete,
  handleCopy,
  openReviewDialog,
  submitReview,
  openAIDialog,
  generateByAI,
  loadQuestions,
  getTypeText,
  getTypeTag,
  getDifficultyText,
  getDifficultyColor,
  getReviewText,
  getReviewTag,
} = useQuestionBank()
</script>

<style scoped>
.question-bank {
  padding: 4px;
}
</style>
