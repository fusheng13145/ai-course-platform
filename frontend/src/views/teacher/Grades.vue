<template>
  <div class="fade-in">
    <PageHeader
      title="成绩管理"
      subtitle="查看与导出学生成绩，支持 AI 评分与批阅"
      icon="chart"
      icon-bg="accent"
    >
      <el-button type="success" plain :disabled="gradeList.length === 0" @click="handleExport">
        <el-icon><Download /></el-icon>导出成绩
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard
        label="平均分"
        :value="overview.avgScore ?? '--'"
        icon="chart"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="最高分"
        :value="overview.maxScore ?? '--'"
        icon="star"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="最低分"
        :value="overview.minScore ?? '--'"
        icon="warn"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="待批阅"
        :value="overview.pendingCount ?? 0"
        icon="edit"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
    </div>

    <!-- 工具栏 -->
    <div class="table-toolbar">
      <div class="toolbar-left">
        <el-select
          v-model="selectedCourseId"
          placeholder="选择课程"
          clearable
          class="course-select"
          @change="loadGradeData"
        >
          <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-input
          v-model="searchText"
          placeholder="搜索学号/姓名..."
          clearable
          class="search-input"
          :prefix-icon="Search"
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        />
      </div>
    </div>

    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico l1-ico--accent">
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
            <path d="M3 3v18h18M18 17V9M13 17V5M8 17v-3" />
          </svg>
        </span>
        <div>
          <div class="l1-title">成绩总览</div>
          <div class="l1-sub">成绩表格与分布一览，支持按课程筛选与搜索</div>
        </div>
      </div>
      <div class="l1-body">
        <div class="main-content-grid">
          <!-- 成绩表格 -->
          <div class="table-section">
            <TgGradeTable
              :loading="loading"
              :grade-list="filteredGradeList"
              :get-grade-tag-type="getGradeTagType"
              @detail="viewStudentDetail"
              @grade="openGradeDialog"
              @ai-grade="aiGrade"
            />
          </div>

          <!-- 成绩分布 + 待批阅 -->
          <TgDistributionPanel
            :distribution-option="distributionOption"
            :pending-tasks="pendingTasks"
            @go-grading="goToGrading"
          />
        </div>
      </div>
    </section>

    <!-- 学生成绩详情弹窗 -->
    <TgDetailDialog
      v-model="detailDialogVisible"
      :current-student="currentStudent"
      :selected-course-name="selectedCourseName"
    />

    <!-- 批阅对话框 -->
    <TgGradeDialog
      v-model="gradeDialogVisible"
      :grading-student="gradingStudent"
      :grade-form="gradeForm"
      :grading-loading="gradingLoading"
      :current-task-name="currentTaskName"
      @submit="submitGrade"
    />
  </div>
</template>

<script setup lang="ts">
import { Search, Download } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import { useTeacherGrades } from '@/composables/useTeacherGrades'
import TgGradeTable from './components/TgGradeTable.vue'
import TgDistributionPanel from './components/TgDistributionPanel.vue'
import TgDetailDialog from './components/TgDetailDialog.vue'
import TgGradeDialog from './components/TgGradeDialog.vue'

/**
 * 教师成绩管理页（页面壳）
 * <p>头部统计 + 工具栏 + 表格/分布双栏编排 + 详情与批阅弹窗装配，业务逻辑委托 useTeacherGrades。</p>
 */
const {
  loading,
  searchText,
  selectedCourseId,
  courseList,
  selectedCourseName,
  overview,
  gradeList,
  pendingTasks,
  detailDialogVisible,
  currentStudent,
  gradeDialogVisible,
  gradingStudent,
  currentTaskName,
  gradingLoading,
  gradeForm,
  filteredGradeList,
  distributionOption,
  loadGradeData,
  handleSearch,
  handleExport,
  viewStudentDetail,
  openGradeDialog,
  aiGrade,
  submitGrade,
  goToGrading,
  getGradeTagType,
} = useTeacherGrades()
</script>

<style scoped>
.l1-ico--accent {
  background: var(--accent-soft);
  color: var(--accent);
}

.main-content-grid {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: var(--ac-spacing-md);
}

.table-section {
  min-width: 0;
}

.course-select {
  width: 200px;
}

.search-input {
  width: 220px;
}

@media (max-width: 1024px) {
  .main-content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
