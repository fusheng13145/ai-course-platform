<template>
  <div class="fade-in">
    <!-- 顶部：时间范围选择器 -->
    <PageHeader title="教学数据分析" subtitle="课程成绩趋势、作业提交与学生活跃度总览" icon="chart">
      <div class="time-selector">
        <el-radio-group v-model="timeRange" size="default" @change="handleTimeChange">
          <el-radio-button value="week">近7天</el-radio-button>
          <el-radio-button value="month">近30天</el-radio-button>
          <el-radio-button value="semester">本学期</el-radio-button>
        </el-radio-group>
      </div>
    </PageHeader>

    <!-- 统计卡片 -->
    <div class="stat-grid animate-in">
      <StatCard
        label="学生总数"
        :value="overview.studentCount || '--'"
        icon="users"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="平均成绩"
        :value="overview.avgScore != null ? overview.avgScore : '--'"
        icon="chart"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="作业提交率"
        :value="overview.submissionRate != null ? overview.submissionRate + '%' : '--'"
        icon="edit"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
      <StatCard
        label="出勤率"
        :value="overview.attendanceRate != null ? overview.attendanceRate + '%' : '--'"
        icon="calendar"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
    </div>

    <!-- 图表区域：成绩趋势 + 成绩分布 -->
    <div class="chart-row">
      <div class="chart-card">
        <div class="chart-title">成绩趋势</div>
        <v-chart :option="trendOption" class="chart-h300" autoresize />
      </div>
      <div class="chart-card">
        <div class="chart-title">成绩分布</div>
        <v-chart :option="distributionOption" class="chart-h300" autoresize />
      </div>
    </div>

    <!-- 图表区域：课程对比雷达图 + 学生活跃度排行 -->
    <div class="chart-row">
      <div class="chart-card">
        <div class="chart-title">各课程对比</div>
        <v-chart :option="radarOption" class="chart-h320" autoresize />
      </div>
      <div class="chart-card">
        <div class="chart-title">学生活跃度排行</div>
        <v-chart :option="activityRankOption" class="chart-h320" autoresize />
      </div>
    </div>

    <!-- 作业完成情况表格 -->
    <TyaCompletionTable :loading="loading" :completion-table="completionTable" />
  </div>
</template>

<script setup lang="ts">
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import TyaCompletionTable from './components/TyaCompletionTable.vue'
import { useTeacherAnalytics } from '@/composables/useTeacherAnalytics'

const {
  loading,
  timeRange,
  overview,
  trendOption,
  distributionOption,
  radarOption,
  activityRankOption,
  completionTable,
  handleTimeChange,
} = useTeacherAnalytics()
</script>

<style scoped>
.time-selector :deep(.el-radio-button__inner) {
  border-color: var(--ac-border);
  color: var(--ac-text-secondary);
  font-size: 13px;
}

.time-selector :deep(.el-radio-button__original-radio:checked + .el-radio-button__inner) {
  background-color: var(--ac-primary);
  border-color: var(--ac-primary);
  box-shadow: none;
}

.chart-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-md);
}

.chart-card {
  padding: var(--ac-spacing-lg);
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  transition: all var(--ac-transition-normal);
}

.chart-card:hover {
  box-shadow: var(--ac-shadow-md);
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-md);
  letter-spacing: -0.2px;
}

.chart-h300 {
  height: 300px;
}
.chart-h320 {
  height: 320px;
}

@media (max-width: 1024px) {
  .chart-row {
    grid-template-columns: 1fr;
  }
}
</style>
