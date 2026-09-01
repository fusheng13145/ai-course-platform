<template>
  <div class="fade-in analytics-page">
    <PageHeader title="数据分析中心" subtitle="平台运营数据总览与趋势分析" icon="chart">
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        range-separator="至"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        size="default"
        :shortcuts="dateShortcuts"
        class="date-range"
        @change="handleDateChange"
      />
      <el-button type="primary" @click="exportReport">
        <el-icon><Download /></el-icon>
        导出报告
      </el-button>
    </PageHeader>

    <div v-loading="loading">
      <AnalyticsMetricCards :core-metrics="coreMetrics" :metric-foot="metricFoot" />
      <AnalyticsCharts
        :user-trend-option="userTrendOption"
        :category-pie-option="categoryPieOption"
        :heatmap-option="heatmapOption"
        :heatmap-colors="heatmapColors"
        :role-activity-option="roleActivityOption"
        :grade-dist-option="gradeDistOption"
        :top-courses-option="topCoursesOption"
        :has-user-trend="hasUserTrend"
        :has-category="hasCategory"
        :has-heatmap="hasHeatmap"
        :has-role-activity="hasRoleActivity"
        :has-grade-dist="hasGradeDist"
        :has-top-courses="hasTopCourses"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { Download } from '@element-plus/icons-vue'
import { useAnalytics } from '@/composables/useAnalytics'
import PageHeader from '@/components/PageHeader.vue'
import AnalyticsMetricCards from './components/AnalyticsMetricCards.vue'
import AnalyticsCharts from './components/AnalyticsCharts.vue'

/**
 * 数据分析中心页面（管理员）
 * <p>页面壳：页头（日期范围 + 导出报告）+ 核心指标卡 + 六类图表区域；
 * 全部数据、图表配置与拉取逻辑由 useAnalytics 组合式函数承载。</p>
 */
const {
  dateRange,
  loading,
  hasUserTrend,
  hasCategory,
  hasHeatmap,
  hasRoleActivity,
  hasGradeDist,
  hasTopCourses,
  dateShortcuts,
  coreMetrics,
  metricFoot,
  userTrendOption,
  categoryPieOption,
  heatmapColors,
  heatmapOption,
  roleActivityOption,
  gradeDistOption,
  topCoursesOption,
  fetchAnalyticsData,
  handleDateChange,
  exportReport,
} = useAnalytics()

onMounted(fetchAnalyticsData)
</script>

<style scoped>
.analytics-page {
  max-width: 1440px;
  margin: 0 auto;
}
.date-range {
  width: 280px;
}
</style>
