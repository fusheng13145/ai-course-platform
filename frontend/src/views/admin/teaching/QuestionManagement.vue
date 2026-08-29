<template>
  <div class="fade-in question-management">
    <PageHeader title="题库管理" subtitle="题目库维护与AI智能出题" icon="doc" icon-bg="accent">
      <el-button type="primary" plain @click="showAIDialog = true">
        <el-icon><MagicStick /></el-icon>AI 智能出题
      </el-button>
      <el-button type="primary" @click="openCreateDialog">
        <el-icon><Plus /></el-icon>添加题目
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard
        label="总题目数"
        :value="questionStats[0].value"
        icon="doc"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="今日新增"
        :value="questionStats[1].value"
        icon="clock"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="待审核"
        :value="questionStats[2].value"
        icon="warn"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
      <StatCard
        label="易错题数"
        :value="questionStats[3].value"
        icon="chart"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
    </div>

    <!-- 筛选工具栏 + 题目列表 -->
    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico" style="background: var(--accent-soft); color: var(--accent)">
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
            <path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01" />
          </svg>
        </span>
        <div>
          <div class="l1-title">题目列表</div>
          <div class="l1-sub">筛选题型与难度，维护题目库</div>
        </div>
      </div>
      <div class="l1-body">
        <QmFilterBar
          v-model:keyword="filters.keyword"
          v-model:type="filters.type"
          v-model:difficulty="filters.difficulty"
          v-model:course-id="filters.courseId"
          :course-list="courseList"
          @search="fetchQuestions"
          @error-prone="viewErrorProne"
          @export="handleExport"
          @import="handleImport"
        />
        <QmQuestionTable
          :question-list="questionList"
          :loading="loading"
          :page="pagination.page"
          :size="pagination.size"
          :total="pagination.total"
          :get-type-tag-type="getTypeTagType"
          :get-type-text="getTypeText"
          :get-difficulty-value="getDifficultyValue"
          :get-accuracy-class="getAccuracyClass"
          @edit="editQuestion"
          @stats="viewStats"
          @delete="deleteQuestion"
          @update:page="pagination.page = $event"
          @update:size="pagination.size = $event"
          @search="fetchQuestions"
        />
      </div>
    </section>

    <!-- 创建/编辑题目对话框 -->
    <QmEditDialog
      ref="questionFormRef"
      v-model="showEditDialog"
      :is-editing="isEditing"
      :form="questionForm"
      :rules="questionRules"
      :course-list="courseList"
      :option-labels="OPTION_LABELS"
      :saving="saving"
      :is-choice-type="isChoiceType"
      @save="saveQuestion"
      @add-option="addOption"
      @remove-option="removeOption"
    />

    <!-- AI智能出题对话框 -->
    <QmAiDialog
      ref="aiFormRef"
      v-model="showAIDialog"
      :form="aiForm"
      :rules="aiRules"
      :course-list="courseList"
      :generating="aiGenerating"
      @generate="generateByAI"
    />

    <!-- 题目统计对话框 -->
    <QmStatsDialog v-model="showStatsDialog" :stats="currentStats" />
  </div>
</template>

<script setup>
import { MagicStick, Plus } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import { useQuestions, OPTION_LABELS } from '@/composables/useQuestions'
import QmFilterBar from './components/QmFilterBar.vue'
import QmQuestionTable from './components/QmQuestionTable.vue'
import QmEditDialog from './components/QmEditDialog.vue'
import QmAiDialog from './components/QmAiDialog.vue'
import QmStatsDialog from './components/QmStatsDialog.vue'

/**
 * 题库管理页（页面壳）
 * <p>头部统计 + 筛选/列表编排 + 三对话框装配，业务逻辑委托 useQuestions。</p>
 */
const {
  loading,
  saving,
  aiGenerating,
  questionStats,
  filters,
  pagination,
  questionList,
  courseList,
  showEditDialog,
  showAIDialog,
  showStatsDialog,
  isEditing,
  currentStats,
  questionFormRef,
  questionForm,
  questionRules,
  aiFormRef,
  aiForm,
  aiRules,
  fetchQuestions,
  openCreateDialog,
  editQuestion,
  saveQuestion,
  deleteQuestion,
  addOption,
  removeOption,
  generateByAI,
  viewStats,
  viewErrorProne,
  handleExport,
  handleImport,
  isChoiceType,
  getTypeText,
  getTypeTagType,
  getDifficultyValue,
  getAccuracyClass,
} = useQuestions()
</script>

<style scoped>
.question-management {
  max-width: 1440px;
  margin: 0 auto;
}
</style>
