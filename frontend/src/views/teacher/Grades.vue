<template>
  <div class="fade-in">
    <PageHeader
      title="成绩管理"
      subtitle="查看课程学情与正式总评；作业批阅按具体任务进入提交记录"
      icon="chart"
      icon-bg="accent"
    >
      <el-button
        type="success"
        plain
        :disabled="filteredGradeList.length === 0"
        @click="handleExport"
      >
        <el-icon><Download /></el-icon>导出成绩
      </el-button>
      <el-button :loading="loading" @click="refresh">刷新</el-button>
    </PageHeader>

    <el-alert
      v-if="loadNotice"
      class="load-notice"
      :title="loadNotice"
      type="warning"
      :closable="false"
      show-icon
    />

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
        label="待批阅任务"
        :value="overview.pendingCount ?? '--'"
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
          placeholder="搜索学号/姓名/课程..."
          clearable
          class="search-input"
          :prefix-icon="Search"
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
              :empty-description="searchText.trim() ? '没有匹配的学生' : '暂无可显示的学生成绩'"
              @detail="viewStudentDetail"
            />
          </div>

          <!-- 成绩分布 + 待批阅 -->
          <TgDistributionPanel
            :distribution-option="distributionOption"
            :has-distribution="hasDistribution"
            :loading="loading"
            :pending-tasks="pendingTasks"
            :pending-unavailable="pendingUnavailable"
            @go-grading="goToGrading"
          />
        </div>
      </div>
    </section>

    <!-- 学生成绩详情弹窗 -->
    <CgtDetailDialog
      v-model="detailDialogVisible"
      :current-student="currentStudent"
      :selected-course-name="selectedCourseName"
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
import CgtDetailDialog from '@/components/course/CgtDetailDialog.vue'

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
  loadNotice,
  pendingTasks,
  pendingUnavailable,
  detailDialogVisible,
  currentStudent,
  filteredGradeList,
  distributionOption,
  hasDistribution,
  refresh,
  loadGradeData,
  handleExport,
  viewStudentDetail,
  goToGrading,
  getGradeTagType,
} = useTeacherGrades()
</script>

<style scoped>
.l1-ico--accent {
  background: var(--accent-soft);
  color: var(--accent);
}

.load-notice {
  margin-bottom: var(--ac-spacing-md);
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
