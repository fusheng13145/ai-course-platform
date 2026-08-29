<template>
  <div class="fade-in grade-management">
    <PageHeader title="成绩管理" subtitle="全校成绩查询、分析与导出" icon="chart" icon-bg="accent">
      <el-button type="primary" @click="exportGrades">
        <el-icon><Download /></el-icon>导出报表
      </el-button>
    </PageHeader>

    <!-- 概览卡片：平均分 / 标准差 / 及格率 / 异常数 -->
    <div class="stat-grid animate-in d1">
      <StatCard
        label="平均分"
        :value="overviewStats[0].value"
        icon="chart"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="标准差"
        :value="overviewStats[1].value"
        icon="list"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="及格率"
        :value="overviewStats[2].value"
        icon="check"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="异常数"
        :value="overviewStats[3].value"
        icon="warn"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
    </div>

    <section class="l1-card animate-in d2">
      <div class="l1-head">
        <span class="l1-ico ico-accent">
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
          <div class="l1-sub">按课程与学期筛选，查看分布与异常预警</div>
        </div>
      </div>
      <div class="l1-body">
        <GradeFilterBar
          v-model:show-abnormal-only="showAbnormalOnly"
          :filters="filters"
          :course-list="courseList"
          @search="fetchGrades"
          @reset="resetFilters"
        />

        <!-- 主内容区域：左侧列表 + 右侧统计分析 -->
        <div class="main-content">
          <GradeTable
            :grade-list="gradeList"
            :loading="loading"
            :pagination="pagination"
            :get-score-class="getScoreClass"
            @search="fetchGrades"
            @view="viewGradeDetail"
          />
          <GradeAnalyticsPanel
            :distribution-option="distributionOption"
            :course-compare-option="courseCompareOption"
            :abnormal-grades="abnormalGrades"
          />
        </div>
      </div>
    </section>

    <GradeDetailDialog
      v-model="showDetailDialog"
      :grade="selectedGrade"
      :get-score-class="getScoreClass"
    />
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { Download } from '@element-plus/icons-vue'
import { useGrades } from '@/composables/useGrades'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import GradeFilterBar from './components/GradeFilterBar.vue'
import GradeTable from './components/GradeTable.vue'
import GradeAnalyticsPanel from './components/GradeAnalyticsPanel.vue'
import GradeDetailDialog from './components/GradeDetailDialog.vue'

/**
 * 成绩管理页面（管理员视角）
 * <p>页面壳：页头（导出报表）+ 四项概览卡片 + 成绩总览区（筛选栏 / 列表 / 统计分析面板）
 * + 详情对话框；全部数据与统计逻辑由 useGrades 组合式函数承载。</p>
 */
const {
  loading,
  filters,
  showAbnormalOnly,
  pagination,
  gradeList,
  courseList,
  abnormalGrades,
  overviewStats,
  showDetailDialog,
  selectedGrade,
  distributionOption,
  courseCompareOption,
  fetchCourses,
  fetchGrades,
  getScoreClass,
  resetFilters,
  exportGrades,
  viewGradeDetail,
} = useGrades()

onMounted(() => {
  fetchCourses()
  fetchGrades()
})
</script>

<style scoped>
.grade-management {
  max-width: 1440px;
  margin: 0 auto;
}

.ico-accent {
  background: var(--accent-soft);
  color: var(--accent);
}

.main-content {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: var(--ac-spacing-md);
}

@media (max-width: 1024px) {
  .main-content {
    grid-template-columns: 1fr;
  }
}
</style>
