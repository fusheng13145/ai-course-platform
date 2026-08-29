<template>
  <div class="completion-tab">
    <!-- 概览统计卡 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-icon stat-icon--blue"><Document :size="20" /></div>
        <div class="stat-info">
          <span class="stat-val">{{ overview.totalAssignments }}</span>
          <span class="stat-lbl">作业总数</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--green">
          <svg viewBox="0 0 24 24" fill="none" width="20" height="20">
            <path
              d="M9 12l2 2 4-4"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" />
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ overview.avgSubmissionRate }}%</span>
          <span class="stat-lbl">平均提交率</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--orange">
          <svg viewBox="0 0 24 24" fill="none" width="20" height="20">
            <path
              d="M12 20V10M18 20V4M6 20v-6"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
            />
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ overview.avgScore }}</span>
          <span class="stat-lbl">平均成绩</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--purple">
          <svg viewBox="0 0 24 24" fill="none" width="20" height="20">
            <path
              d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
            />
            <rect x="8" y="2" width="8" height="4" rx="1" stroke="currentColor" stroke-width="2" />
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ overview.totalSubmissions }}</span>
          <span class="stat-lbl">已提交数</span>
        </div>
      </div>
    </div>

    <!-- Tab 切换：按作业 / 按学生 -->
    <el-tabs v-model="viewMode" class="view-tabs">
      <el-tab-pane label="按作业查看" name="assignment">
        <CctAssignmentTable :loading="loadingAssignments" :data="assignmentStats" />

        <!-- 成绩分布图 -->
        <div v-if="assignmentStats.length > 0" class="chart-section">
          <div class="chart-card">
            <div class="chart-title">成绩分布直方图</div>
            <v-chart :option="gradeDistOption" style="height: 260px" autoresize />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="按学生查看" name="student">
        <CctStudentTable :loading="loadingStudents" :data="studentStats" />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { toRef } from 'vue'
import { Document } from '@element-plus/icons-vue'
import CctAssignmentTable from './course/CctAssignmentTable.vue'
import CctStudentTable from './course/CctStudentTable.vue'
import { useCourseCompletionTab } from '@/composables/useCourseCompletionTab'

const props = defineProps({
  courseId: { type: [Number, String], required: true },
})
const courseIdRef = toRef(props, 'courseId')

const {
  loadingAssignments,
  loadingStudents,
  viewMode,
  overview,
  assignmentStats,
  studentStats,
  gradeDistOption,
} = useCourseCompletionTab(courseIdRef)
</script>

<style scoped>
.completion-tab {
  padding: 4px 0;
}
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 22px;
}
.stat-card {
  background: var(--ac-bg);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-sm);
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  transition: box-shadow 0.2s;
}
.stat-card:hover {
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
}
.stat-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
}
.stat-icon--blue {
  background: linear-gradient(135deg, #3b82f6, #60a5fa);
}
.stat-icon--green {
  background: linear-gradient(135deg, #10b981, #34d399);
}
.stat-icon--orange {
  background: linear-gradient(135deg, #f59e0b, #fbbf24);
}
.stat-icon--purple {
  background: linear-gradient(135deg, #8b5cf6, #a78bfa);
}
.stat-info {
  flex: 1;
  min-width: 0;
}
.stat-val {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: var(--ac-text-primary);
  line-height: 1.2;
}
.stat-lbl {
  display: block;
  font-size: 12px;
  color: var(--ac-text-secondary);
  margin-top: 2px;
}
.view-tabs {
  margin-top: 4px;
}
.chart-section {
  margin-top: 18px;
  margin-bottom: 10px;
}
.chart-card {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  padding: 16px;
}
.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 8px;
}

@media (max-width: 768px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
