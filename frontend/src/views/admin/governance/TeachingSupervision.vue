<template>
  <div class="fade-in teaching-supervision">
    <PageHeader
      title="教学运行监管"
      subtitle="开课运行、学习行为与异常监控的整体视图"
      icon="monitor"
      icon-bg="accent"
    >
      <el-button :loading="loading" @click="loadAll">
        <el-icon><Refresh /></el-icon>刷新
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard
        label="课程总数"
        :value="overview.totalCourses ?? '—'"
        icon="layers"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="活跃课程"
        :value="overview.activeCourses ?? '—'"
        icon="play"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="在校学生"
        :value="overview.roleDistribution?.student ?? '—'"
        icon="users"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="授课教师"
        :value="overview.roleDistribution?.teacher ?? '—'"
        icon="user"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="选课总人次"
        :value="overview.totalEnrollments ?? '—'"
        icon="list"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
    </div>

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
          <div class="l1-title">教学运行全景</div>
          <div class="l1-sub">开课统计、学习行为热力与风险预警</div>
        </div>
      </div>
      <div class="l1-body">
        <div class="grid-2">
          <!-- 开课统计：分类分布 + 热门课程 -->
          <TsvCourseStatsCard
            :category-distribution="categoryDistribution"
            :category-keys="categoryKeys"
            :top-courses="topCourses"
            :bar-width="barWidth"
          />

          <!-- 学习行为热力图 -->
          <TsvHeatmapCard
            :time-slots="timeSlots"
            :day-order="dayOrder"
            :day-label="dayLabel"
            :cell-style="cellStyle"
            :heat-value="heatValue"
            :legend-style="legendStyle"
          />
        </div>

        <!-- AI 异常监控 -->
        <div style="margin-top: var(--ac-spacing-lg)">
          <TsvAiMonitorCard :ai-monitor="aiMonitor" :ai-alerts="aiAlerts" :alert-type="alertType" />
        </div>

        <!-- 学习预警（全校聚合 + 维度下钻） -->
        <div style="margin-top: var(--ac-spacing-lg)">
          <TsvRiskCard
            v-model:risk-dim="riskDim"
            :risk-loading="riskLoading"
            :risk-total="riskTotal"
            :risk-count="riskCount"
            :risk-rate="riskRate"
            :risk-groups="riskGroups"
            :risk-details="riskDetails"
            @load-risk="loadRisk"
          />
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { Refresh } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import { useTeachingSupervision } from '@/composables/useTeachingSupervision'
import TsvCourseStatsCard from './components/TsvCourseStatsCard.vue'
import TsvHeatmapCard from './components/TsvHeatmapCard.vue'
import TsvAiMonitorCard from './components/TsvAiMonitorCard.vue'
import TsvRiskCard from './components/TsvRiskCard.vue'

/**
 * 教学运行监管页（页面壳）
 * <p>统计卡 + 全景区块（开课统计/热力/AI 监控/学习预警）编排，业务逻辑委托 useTeachingSupervision。</p>
 */
const {
  loading,
  overview,
  aiMonitor,
  timeSlots,
  dayOrder,
  categoryDistribution,
  categoryKeys,
  topCourses,
  aiAlerts,
  riskDim,
  riskLoading,
  riskTotal,
  riskCount,
  riskRate,
  riskGroups,
  riskDetails,
  loadRisk,
  loadAll,
  dayLabel,
  barWidth,
  heatValue,
  cellStyle,
  legendStyle,
  alertType,
} = useTeachingSupervision()
</script>

<style scoped>
.teaching-supervision {
  max-width: 1400px;
  margin: 0 auto;
}

.grid-2 {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--ac-spacing-lg);
}

@media (max-width: 960px) {
  .grid-2 {
    grid-template-columns: 1fr;
  }
}
</style>
