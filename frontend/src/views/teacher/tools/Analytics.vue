<template>
  <div class="fade-in">
    <!-- 顶部：刷新入口 -->
    <PageHeader
      title="教学数据分析"
      subtitle="课程进度、真实成绩统计与近 7 天作业提交趋势"
      icon="chart"
    >
      <div class="time-selector">
        <el-button :loading="loading" @click="loadAnalyticsData">刷新数据</el-button>
      </div>
    </PageHeader>

    <el-alert
      v-if="loadNotice"
      class="load-notice"
      :title="loadNotice"
      type="warning"
      :closable="false"
      show-icon
    />

    <!-- 统计卡片 -->
    <div class="stat-grid animate-in">
      <StatCard
        label="课程总数"
        :value="overview.courseCount ?? '--'"
        icon="book"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="选课人次"
        :value="overview.studentCount ?? '--'"
        icon="users"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="平均作业得分"
        :value="overview.avgScore ?? '--'"
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
        label="待批阅提交"
        :value="overview.pendingGrading ?? '--'"
        icon="edit"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
    </div>

    <!-- 图表区域：真实提交趋势 + 学生作业均分分布 -->
    <div class="chart-row">
      <div v-loading="loading" class="chart-card">
        <div class="chart-title">近 7 天作业提交量</div>
        <v-chart v-if="hasTrendData" :option="trendOption" class="chart-h300" autoresize />
        <el-empty v-else description="暂无提交趋势数据" :image-size="60" />
      </div>
      <div v-loading="loading" class="chart-card">
        <div class="chart-title">学生作业均分区间</div>
        <v-chart
          v-if="hasDistributionData"
          :option="distributionOption"
          class="chart-h300"
          autoresize
        />
        <el-empty v-else description="暂无已评分作业" :image-size="60" />
      </div>
    </div>

    <!-- 图表区域：真实课程进度 + 学生任务完成数 -->
    <div class="chart-row">
      <div v-loading="loading" class="chart-card">
        <div class="chart-title">课程进度与任务完成率</div>
        <v-chart v-if="hasCourseComparison" :option="radarOption" class="chart-h320" autoresize />
        <el-empty v-else description="暂无课程进度数据" :image-size="60" />
      </div>
      <div v-loading="loading" class="chart-card">
        <div class="chart-title">学生任务完成数排行</div>
        <v-chart
          v-if="hasActivityRank"
          :option="activityRankOption"
          class="chart-h320"
          autoresize
        />
        <el-empty v-else description="暂无任务完成记录" :image-size="60" />
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
  loadNotice,
  overview,
  trendOption,
  distributionOption,
  radarOption,
  activityRankOption,
  completionTable,
  hasTrendData,
  hasDistributionData,
  hasCourseComparison,
  hasActivityRank,
  loadAnalyticsData,
} = useTeacherAnalytics()
</script>

<style scoped>
.load-notice {
  margin-bottom: var(--ac-spacing-md);
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
