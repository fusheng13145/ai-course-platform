<template>
  <div class="grades-tab">
    <!-- 统计卡 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--ac-primary-bg); color: var(--ac-primary)">
          <svg viewBox="0 0 24 24" fill="none" width="18" height="18">
            <path
              d="M3 19V5M7 19v-8M11 19V9M15 19v-6M19 19V4"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
            />
            <path d="M2 20h20" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ overview.avgScore ?? '--' }}</span
          ><span class="stat-lbl">平均分</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--ac-success-bg); color: var(--ac-success)">
          <Top :size="18" />
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ overview.maxScore ?? '--' }}</span
          ><span class="stat-lbl">最高分</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--ac-danger-bg); color: var(--ac-danger)">
          <Bottom :size="18" />
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ overview.minScore ?? '--' }}</span
          ><span class="stat-lbl">最低分</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--ac-warning-bg); color: var(--ac-warning)">
          <EditPen :size="18" />
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ overview.pendingCount ?? '--' }}</span
          ><span class="stat-lbl">待批阅任务</span>
        </div>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="tab-toolbar">
      <el-button
        type="success"
        plain
        size="small"
        :disabled="gradeList.length === 0"
        @click="handleExport"
      >
        <el-icon><Download :size="14" /></el-icon>导出成绩
      </el-button>
      <el-button text size="small" :loading="loading" @click="loadData">
        <el-icon><Refresh /></el-icon>刷新
      </el-button>
      <el-input
        v-model="searchText"
        placeholder="搜索学号/姓名..."
        clearable
        style="width: 200px"
        :prefix-icon="Search"
      />
    </div>

    <el-alert
      v-if="loadNotice"
      class="load-notice"
      :title="loadNotice"
      type="warning"
      :closable="false"
      show-icon
    />

    <!-- 主区域 -->
    <div class="main-grid">
      <CgtGradeTable
        :loading="loading"
        :filtered-grades="filteredGrades"
        @view-detail="viewDetail"
      />
      <CgtSidePanel
        :dist-option="distOption"
        :pending-tasks="pendingTasks"
        :pending-loading="loading"
        :pending-unavailable="pendingUnavailable"
        @grade-task="handleGradeTask"
      />
    </div>

    <!-- 详情弹窗 -->
    <CgtDetailDialog v-model="detailVisible" :current-student="currentStudent" />
  </div>
</template>

<script setup>
import { toRef } from 'vue'
import { useRouter } from 'vue-router'
import { Search, Download, Top, Bottom, EditPen, Refresh } from '@element-plus/icons-vue'
import CgtGradeTable from './course/CgtGradeTable.vue'
import CgtSidePanel from './course/CgtSidePanel.vue'
import CgtDetailDialog from './course/CgtDetailDialog.vue'
import { useCourseGradesTab } from '@/composables/useCourseGradesTab'

const props = defineProps({ courseId: { type: [Number, String], required: true } })
const courseIdRef = toRef(props, 'courseId')

const {
  loading,
  searchText,
  gradeList,
  pendingTasks,
  pendingUnavailable,
  loadNotice,
  overview,
  detailVisible,
  currentStudent,
  filteredGrades,
  distOption,
  loadData,
  viewDetail,
  handleExport,
} = useCourseGradesTab(courseIdRef)

// 待批任务下钻：作业批改页已支持 ?taskId= 直达选中任务（CourseSubmissions.loadTasks）
const router = useRouter()
function handleGradeTask(task) {
  if (!task || task.id == null) return
  router.push({
    path: `/teacher/courses/${props.courseId}/submissions`,
    query: { taskId: String(task.id) },
  })
}
</script>

<style scoped>
.grades-tab {
  padding: 4px 0;
}
.load-notice {
  margin-bottom: 14px;
}
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 14px;
}
.stat-card {
  background: var(--ac-bg);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-sm);
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.stat-icon {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-info {
  flex: 1;
  min-width: 0;
}
.stat-val {
  display: block;
  font-size: 18px;
  font-weight: 700;
  color: var(--ac-text-primary);
  line-height: 1.2;
}
.stat-lbl {
  display: block;
  font-size: 11px;
  color: var(--ac-text-secondary);
}
.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  gap: 8px;
  flex-wrap: wrap;
}
.main-grid {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 14px;
}
@media (max-width: 1024px) {
  .main-grid {
    grid-template-columns: 1fr;
  }
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
