﻿<template>
  <div class="fade-in grade-management">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">成绩管理</h2>
        <p class="page-desc">全校成绩查询、分析与导出</p>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="exportGrades">
          <el-icon><Download /></el-icon>导出报表
        </el-button>
      </div>
    </div>

    <!-- 成绩概览卡片 -->
    <div class="overview-cards">
      <div v-for="(item, idx) in overviewStats" :key="idx" class="overview-card">
        <div class="overview-icon" :style="{ background: item.bg }">
          <el-icon :size="22"><component :is="item.icon" /></el-icon>
        </div>
        <div class="overview-info">
          <span class="overview-value">{{ item.value }}</span>
          <span class="overview-label">{{ item.label }}</span>
        </div>
      </div>
    </div>

    <!-- 筛选工具栏 -->
    <div class="filter-bar">
      <div class="filter-left">
        <el-select v-model="filters.courseId" placeholder="选择课程" clearable style="width:180px" @change="fetchGrades">
          <el-option
            v-for="course in courseList"
            :key="course.id"
            :label="course.title || course.name"
            :value="course.id"
          />
        </el-select>
        <el-select v-model="filters.semester" placeholder="学期" clearable style="width:140px" @change="fetchGrades">
          <el-option label="2026春季" value="2026-SPRING" />
          <el-option label="2025秋季" value="2025-FALL" />
          <el-option label="2025春季" value="2025-SPRING" />
        </el-select>
        <el-input v-model="filters.keyword" placeholder="搜索学生姓名/学号" clearable style="width:200px" @keyup.enter="fetchGrades" />
        <el-button type="primary" @click="fetchGrades"><el-icon><Search /></el-icon>搜索</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
      <div class="filter-right">
        <el-checkbox v-model="showAbnormalOnly" @change="fetchGrades">仅显示异常</el-checkbox>
      </div>
    </div>

    <!-- 主内容区域 -->
    <div class="main-content">
      <!-- 左侧：成绩列表 -->
      <div class="grade-list-section">
        <div class="content-card">
          <el-table :data="gradeList" style="width:100%" v-loading="loading">
            <el-table-column prop="studentName" label="学生姓名" width="110" />
            <el-table-column prop="studentId" label="学号" width="120" />
            <el-table-column prop="courseName" label="课程名称" min-width="150" show-overflow-tooltip />
            <el-table-column prop="examTitle" label="考试名称" min-width="140" show-overflow-tooltip />
            <el-table-column prop="score" label="成绩" width="80" align="center">
              <template #default="{ row }">
                <span
                  class="score-badge"
                  :class="getScoreClass(row.score)"
                >{{ row.score ?? '--' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="rank" label="排名" width="70" align="center" />
            <el-table-column prop="submitTime" label="提交时间" width="160" />
            <el-table-column label="状态" width="90" align="center">
              <template #default="{ row }">
                <el-tag
                  v-if="row.isAbnormal"
                  type="danger"
                  size="small"
                  effect="light"
                >异常</el-tag>
                <span v-else class="text-muted">正常</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="viewGradeDetail(row)">详情</el-button>
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页 -->
          <div class="pagination-wrapper">
            <el-pagination
              v-model:current-page="pagination.page"
              v-model:page-size="pagination.size"
              :total="pagination.total"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              background
              @size-change="fetchGrades"
              @current-change="fetchGrades"
            />
          </div>
        </div>
      </div>

      <!-- 右侧：统计分析面板 -->
      <div class="analysis-panel">
        <!-- 成绩分布图 -->
        <div class="panel-card">
          <div class="panel-title">成绩分布</div>
          <v-chart :option="distributionOption" style="height: 220px" autoresize />
        </div>

        <!-- 各课程对比 -->
        <div class="panel-card">
          <div class="panel-title">各课程平均分对比</div>
          <v-chart :option="courseCompareOption" style="height: 260px" autoresize />
        </div>

        <!-- 异常成绩预警 -->
        <div class="panel-card abnormal-card">
          <div class="panel-title">
            <el-icon><Warning /></el-icon>异常成绩预警
          </div>
          <div v-if="abnormalGrades.length > 0" class="abnormal-list">
            <div
              v-for="(item, idx) in abnormalGrades.slice(0, 5)"
              :key="idx"
              class="abnormal-item"
            >
              <div class="abnormal-info">
                <span class="name">{{ item.studentName }}</span>
                <span class="score" :class="item.score < 60 ? 'low' : 'high'">{{ item.score }}分</span>
              </div>
              <span class="course">{{ item.courseName }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无异常成绩" :image-size="50" />
        </div>
      </div>
    </div>

    <!-- 成绩详情对话框 -->
    <el-dialog v-model="showDetailDialog" title="成绩详情" width="600px" destroy-on-close>
      <div v-if="selectedGrade" class="grade-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="学生姓名">{{ selectedGrade.studentName }}</el-descriptions-item>
          <el-descriptions-item label="学号">{{ selectedGrade.studentId }}</el-descriptions-item>
          <el-descriptions-item label="课程名称">{{ selectedGrade.courseName }}</el-descriptions-item>
          <el-descriptions-item label="考试名称">{{ selectedGrade.examTitle }}</el-descriptions-item>
          <el-descriptions-item label="得分">
            <span class="score-badge large" :class="getScoreClass(selectedGrade.score)">
              {{ selectedGrade.score }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="排名">{{ selectedGrade.rank || '--' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间" :span="2">{{ selectedGrade.submitTime }}</el-descriptions-item>
        </el-descriptions>

        <!-- 答题明细（如果有） -->
        <div v-if="selectedGrade.details && selectedGrade.details.length > 0" class="answer-section">
          <h4>答题明细</h4>
          <el-table :data="selectedGrade.details" size="small" max-height="250">
            <el-table-column prop="questionNum" label="题号" width="70" />
            <el-table-column prop="questionContent" label="题目内容" show-overflow-tooltip />
            <el-table-column prop="userAnswer" label="作答" width="120" />
            <el-table-column prop="correctAnswer" label="正确答案" width="120" />
            <el-table-column prop="points" label="得分" width="70" align="center">
              <template #default="{ row }">
                <el-tag :type="row.points > 0 ? 'success' : 'danger'" size="small">
                  {{ row.points }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, markRaw } from 'vue'
import { Download, Search, Warning, DataAnalysis, TrendCharts, TrophyBase } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import examApi from '@/api/exam'
import courseApi from '@/api/course'
import userApi from '@/api/user'
import * as XLSX from 'xlsx'

// 加载状态
const loading = ref(false)

// 筛选条件
const filters = reactive({
  courseId: '',
  semester: '',
  keyword: ''
})

const showAbnormalOnly = ref(false)

// 分页
const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

// 数据
const gradeList = ref([])
const courseList = ref([])
const abnormalGrades = ref([])

// 概览统计数据
const overviewStats = ref([
  { icon: markRaw(DataAnalysis), label: '平均分', value: '--', bg: 'rgba(122, 139, 165, 0.12)' },
  { icon: markRaw(TrendCharts), label: '标准差', value: '--', bg: 'rgba(139, 168, 136, 0.12)' },
  { icon: markRaw(TrophyBase), label: '及格率', value: '--', bg: 'rgba(203, 181, 138, 0.12)' },
  { icon: markRaw(Warning), label: '异常数', value: 0, bg: 'rgba(196, 128, 128, 0.12)' }
])

// 对话框
const showDetailDialog = ref(false)
const selectedGrade = ref(null)

// 成绩分布图配置
const distributionOption = ref({
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(255, 252, 248, 0.95)',
    borderColor: '#DDD6CE'
  },
  grid: { left: 45, right: 15, bottom: 25, top: 15 },
  xAxis: {
    type: 'category',
    data: ['0-59', '60-69', '70-79', '80-89', '90-100'],
    axisLine: { lineStyle: { color: '#DDD6CE' } },
    axisLabel: { fontSize: 11, color: '#928B83' }
  },
  yAxis: { type: 'value', splitLine: { lineStyle: { color: '#DDD6CE', type: 'dashed' } }, axisLabel: { fontSize: 11, color: '#928B83' } },
  series: [{
    type: 'bar',
    barWidth: '55%',
    itemStyle: {
      borderRadius: [4, 4, 0, 0],
      color: (params) => {
        const colors = ['#C48080', '#CBB58A', '#B89A8A', '#8BA888', '#7A8BA5']
        return colors[params.dataIndex] || '#7A8BA5'
      }
    },
    data: []
  }]
})

// 各课程平均分对比配置
const courseCompareOption = ref({
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(255, 252, 248, 0.95)',
    borderColor: '#DDD6CE',
    formatter: '{b}: {c}分'
  },
  grid: { left: 100, right: 20, bottom: 20, top: 10 },
  xAxis: { type: 'value', min: 0, max: 100, splitLine: { lineStyle: { color: '#DDD6CE', type: 'dashed' } }, axisLabel: { color: '#928B83', fontSize: 11 } },
  yAxis: {
    type: 'category',
    data: [],
    inverse: true,
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: '#5E574F', fontSize: 11, width: 90, overflow: 'truncate' }
  },
  series: [{
    type: 'bar',
    barWidth: '65%',
    itemStyle: {
      borderRadius: [0, 4, 4, 0],
      color: {
        type: 'linear',
        x: 0, y: 0, x2: 1, y2: 0,
        colorStops: [
          { offset: 0, color: '#7A8BA5' },
          { offset: 1, color: '#B89A8A' }
        ]
      }
    },
    label: { show: true, position: 'right', color: '#5E574F', fontSize: 11 },
    data: []
  }]
})

/**
 * 获取课程列表
 */
async function fetchCourses() {
  try {
    const res = await courseApi.getCourseList({ page: 0, size: 100 })
    const data = res.data?.records || res.data?.list || res.data || res || []
    courseList.value = Array.isArray(data) ? data : []
  } catch (error) {
    console.warn('获取课程列表失败:', error)
    courseList.value = []
  }
}

/**
 * 获取成绩列表
 */
async function fetchGrades() {
  loading.value = true
  try {
    // 使用考试API获取成绩数据（管理员视角）
    const params = {
      page: pagination.page - 1,
      size: pagination.size
    }

    if (filters.courseId) params.courseId = filters.courseId
    if (filters.semester) params.semester = filters.semester
    if (filters.keyword) params.keyword = filters.keyword

    const res = await examApi.getMyExamSessions(params)
    let rawData = res.data?.records || res.data?.list || res.data || res || []
    let data = Array.isArray(rawData) ? rawData : (rawData?.records ?? rawData?.grades ?? rawData?.items ?? [])

    if (!Array.isArray(data)) {
      // 如果返回的是对象，尝试提取数组
      data = data.records || data.grades || data.items || []
    }

    // 过滤异常成绩
    if (showAbnormalOnly.value) {
      data = data.filter(item => item.isAbnormal || item.score < 40 || item.score === 100)
    }

    gradeList.value = data
    pagination.total = Array.isArray(data) ? data.length : 0

    // 更新统计数据
    updateOverviewStats(data)
    updateCharts(data)
    extractAbnormal(data)
  } catch (error) {
    console.error('获取成绩列表失败:', error)
    gradeList.value = []
  } finally {
    loading.value = false
  }
}

/**
 * 更新概览统计
 */
function updateOverviewStats(data) {
  if (!Array.isArray(data) || data.length === 0) return

  const scores = data.map(g => g.score).filter(s => s !== null && s !== undefined && !isNaN(s))
  if (scores.length === 0) return

  const avg = (scores.reduce((a, b) => a + b, 0) / scores.length).toFixed(1)
  const variance = scores.reduce((sum, score) => sum + Math.pow(score - avg, 2), 0) / scores.length
  const stdDev = Math.sqrt(variance).toFixed(1)
  const passCount = scores.filter(s => s >= 60).length
  const passRate = ((passCount / scores.length) * 100).toFixed(1)

  overviewStats.value[0].value = avg
  overviewStats.value[1].value = stdDev
  overviewStats.value[2].value = passRate + '%'
  overviewStats.value[3].value = abnormalGrades.value.length
}

/**
 * 更新图表数据
 */
function updateCharts(data) {
  if (!Array.isArray(data)) return

  const scores = data.map(g => g.score).filter(s => s !== null && s !== undefined && !isNaN(s))

  // 成绩分布
  const dist = [0, 0, 0, 0, 0]
  scores.forEach(s => {
    if (s < 60) dist[0]++
    else if (s < 70) dist[1]++
    else if (s < 80) dist[2]++
    else if (s < 90) dist[3]++
    else dist[4]++
  })
  distributionOption.value.series[0].data = dist

  // 按课程分组计算平均分
  const courseMap = new Map()
  data.forEach(item => {
    const name = item.courseName || '未知课程'
    if (!courseMap.has(name)) {
      courseMap.set(name, [])
    }
    if (item.score != null && !isNaN(item.score)) {
      courseMap.get(name).push(item.score)
    }
  })

  const courseAvgs = Array.from(courseMap.entries())
    .map(([name, scores]) => ({
      name,
      avg: (scores.reduce((a, b) => a + b, 0) / scores.length).toFixed(1)
    }))
    .sort((a, b) => a.avg - b.avg)

  courseCompareOption.value.yAxis.data = courseAvgs.map(c => c.name)
  courseCompareOption.value.series[0].data = courseAvgs.map(c => parseFloat(c.avg))
}

/**
 * 提取异常成绩
 */
function extractAbnormal(data) {
  if (!Array.isArray(data)) {
    abnormalGrades.value = []
    return
  }

  abnormalGrades.value = data.filter(item =>
    item.isAbnormal ||
    (item.score != null && (item.score < 45 || item.score >= 98))
  )
}

/**
 * 获取成绩样式类名
 */
function getScoreClass(score) {
  if (score == null || isNaN(score)) return ''
  if (score >= 90) return 'excellent'
  if (score >= 80) return 'good'
  if (score >= 70) return 'medium'
  if (score >= 60) return 'pass'
  return 'fail'
}

/**
 * 重置筛选条件
 */
function resetFilters() {
  filters.courseId = ''
  filters.semester = ''
  filters.keyword = ''
  showAbnormalOnly.value = false
  fetchGrades()
}

/**
 * 导出成绩报表（客户端XLSX导出）
 */
function exportGrades() {
  if (gradeList.value.length === 0) {
    ElMessage.warning('暂无可导出的成绩数据')
    return
  }
  try {
    // 从已加载的成绩数据构建导出表
    const exportData = gradeList.value.map((item, idx) => ({
      '序号': idx + 1,
      '学生姓名': item.studentName || item.fullName || '-',
      '课程名称': item.courseName || '-',
      '成绩': item.score ?? '-',
      '总分': item.totalScore ?? item.total ?? '-',
      '提交时间': item.submitTime || item.createdAt || '-',
      '状态': item.status || '-',
    }))
    const ws = XLSX.utils.json_to_sheet(exportData)
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '成绩报表')
    XLSX.writeFile(wb, `成绩报表_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}.xlsx`)
    ElMessage.success('报表已下载')
  } catch (e) {
    console.error('导出成绩失败:', e)
    ElMessage.error('导出失败，请稍后重试')
  }
}

/**
 * 查看成绩详情
 */
function viewGradeDetail(row) {
  selectedGrade.value = row
  showDetailDialog.value = true
}

// 初始化
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

/* 页面头部 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--ac-spacing-lg);
  gap: var(--ac-spacing-md);
  flex-wrap: wrap;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--ac-text-primary);
  letter-spacing: -0.3px;
}

.page-desc {
  font-size: 13px;
  color: var(--ac-text-muted);
  margin-top: 4px;
}

/* 概览卡片 */
.overview-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-lg);
}

.overview-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 18px 20px;
  display: flex;
  align-items: center;
  gap: 14px;
  transition: all var(--ac-transition-normal);
}

.overview-card:hover {
  box-shadow: var(--ac-shadow-md);
  transform: translateY(-2px);
}

.overview-icon {
  width: 46px;
  height: 46px;
  border-radius: var(--ac-radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--ac-primary);
  flex-shrink: 0;
}

.overview-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.overview-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--ac-text-primary);
  line-height: 1.2;
}

.overview-label {
  font-size: 13px;
  color: var(--ac-text-muted);
}

/* 筛选栏 */
.filter-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ac-spacing-md);
  padding: 16px 20px;
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  gap: var(--ac-spacing-md);
  flex-wrap: wrap;
}

.filter-left {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
  flex-wrap: wrap;
}

.filter-right {
  flex-shrink: 0;
}

/* 主内容布局 */
.main-content {
  display: grid;
  grid-template-columns: 1fr 340px;
  gap: var(--ac-spacing-md);
}

.content-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
}

/* 分页 */
.pagination-wrapper {
  margin-top: var(--ac-spacing-md);
  display: flex;
  justify-content: flex-end;
}

/* 成绩徽章 */
.score-badge {
  display: inline-block;
  padding: 3px 10px;
  border-radius: 6px;
  font-weight: 600;
  font-size: 13px;
}

.score-badge.excellent { background: rgba(139, 168, 136, 0.15); color: #5E7A5B; }
.score-badge.good { background: rgba(122, 139, 165, 0.15); color: #5E7089; }
.score-badge.medium { background: rgba(184, 154, 138, 0.15); color: #8A7845; }
.score-badge.pass { background: rgba(203, 181, 138, 0.15); color: #8A7845; }
.score-badge.fail { background: rgba(196, 128, 128, 0.15); color: #A05A5A; }
.score-badge.large { font-size: 20px; padding: 6px 16px; }

.text-muted {
  color: var(--ac-text-muted);
  font-size: 13px;
}

/* 分析面板 */
.analysis-panel {
  display: flex;
  flex-direction: column;
  gap: var(--ac-spacing-md);
}

.panel-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 18px;
}

.panel-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 6px;
}

/* 异常成绩卡片 */
.abnormal-card .panel-title {
  color: var(--ac-danger);
}

.abnormal-list {
  max-height: 200px;
  overflow-y: auto;
}

.abnormal-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--ac-border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

.abnormal-item:last-child {
  border-bottom: none;
}

.abnormal-info {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.abnormal-info .name {
  font-weight: 500;
  color: var(--ac-text-primary);
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.abnormal-info .score {
  font-weight: 700;
  font-size: 13px;
  padding: 2px 8px;
  border-radius: 4px;
}

.abnormal-info .score.low {
  color: var(--ac-danger);
  background: rgba(196, 128, 128, 0.12);
}

.abnormal-info .score.high {
  color: var(--ac-warning);
  background: rgba(203, 181, 138, 0.12);
}

.abnormal-item .course {
  font-size: 12px;
  color: var(--ac-text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 120px;
}

/* 详情对话框 */
.grade-detail h4 {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 16px 0 12px;
}

.answer-section {
  margin-top: 16px;
}

/* 响应式 */
@media (max-width: 1024px) {
  .main-content {
    grid-template-columns: 1fr;
  }

  .analysis-panel {
    order: -1;
    display: grid;
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .overview-cards {
    grid-template-columns: repeat(2, 1fr);
  }

  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }

  .filter-left {
    justify-content: center;
  }

  .analysis-panel {
    grid-template-columns: 1fr;
  }
}
</style>
