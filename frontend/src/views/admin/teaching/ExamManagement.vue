<template>
  <div class="fade-in exam-management">
    <PageHeader title="考试管理" subtitle="全校考试监管与异常处理" icon="doc" icon-bg="accent">
      <el-button @click="showActiveSessions = true">
        <el-icon><Timer /></el-icon>进行中考试
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard
        label="总试卷数"
        :value="overview.totalPapers || 0"
        icon="doc"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="进行中"
        :value="overview.activeExams || 0"
        icon="play"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="待审核"
        :value="overview.pendingReview || 0"
        icon="warn"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
      <StatCard
        label="作弊嫌疑"
        :value="overview.cheatingCases || 0"
        icon="shield"
        tone="var(--info-soft)"
        tone-color="var(--info)"
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
            <path
              d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2zM14 2v6h6M16 13H8M16 17H8M10 9H8"
            />
          </svg>
        </span>
        <div>
          <div class="l1-title">试卷列表</div>
          <div class="l1-sub">筛选、审核试卷并查看统计分析</div>
        </div>
      </div>
      <div class="l1-body">
        <EmFilterBar
          v-model:status="filters.status"
          v-model:keyword="filters.keyword"
          @search="fetchExamList"
        />
        <EmExamTable
          v-model:page="pagination.page"
          v-model:size="pagination.size"
          :exam-list="examList"
          :loading="loading"
          :total="pagination.total"
          :get-status-type="getStatusType"
          :get-status-text="getStatusText"
          @detail="viewDetail"
          @approve="handleApprove"
          @reject="handleReject"
          @stats="viewStatistics"
          @search="fetchExamList"
        />
      </div>
    </section>

    <!-- 进行中考试对话框 -->
    <EmActiveSessionsDialog
      v-model="showActiveSessions"
      :active-sessions="activeSessions"
      :loading-sessions="loadingSessions"
      @force-submit="forceSubmit"
      @view-cheating="viewCheating"
    />

    <!-- 作弊检测日志对话框 -->
    <EmCheatingDialog
      v-model="showCheatingDialog"
      :cheating-logs="cheatingLogs"
      :get-risk-level="getRiskLevel"
    />

    <!-- 试卷统计对话框 -->
    <EmStatsDialog
      v-model="showStatsDialog"
      :statistics="statistics"
      :grade-chart-option="gradeChartOption"
    />
  </div>
</template>

<script setup lang="ts">
import { Timer } from '@element-plus/icons-vue'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import { useExamManagement } from '@/composables/useExamManagement'
import EmFilterBar from './components/EmFilterBar.vue'
import EmExamTable from './components/EmExamTable.vue'
import EmActiveSessionsDialog from './components/EmActiveSessionsDialog.vue'
import EmCheatingDialog from './components/EmCheatingDialog.vue'
import EmStatsDialog from './components/EmStatsDialog.vue'

/**
 * 全校考试管理页（页面壳）
 * <p>统计卡 + 试卷列表区块（筛选/表格/分页）+ 三对话框装配，业务逻辑委托 useExamManagement。</p>
 */
const {
  loading,
  loadingSessions,
  filters,
  pagination,
  examList,
  activeSessions,
  overview,
  showActiveSessions,
  showCheatingDialog,
  showStatsDialog,
  cheatingLogs,
  statistics,
  gradeChartOption,
  fetchExamList,
  getStatusType,
  getStatusText,
  handleApprove,
  handleReject,
  forceSubmit,
  viewCheating,
  getRiskLevel,
  viewDetail,
  viewStatistics,
} = useExamManagement()
</script>

<style scoped>
.exam-management {
  max-width: 1400px;
  margin: 0 auto;
}
</style>
