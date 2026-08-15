<template>
  <div class="fade-in grade-management">
    <PageHeader title="成绩管理" subtitle="全校成绩查询、分析与导出" icon="chart" icon-bg="accent">
      <el-button type="primary" @click="exportGrades">
        <el-icon><Download /></el-icon>导出报表
      </el-button>
    </PageHeader>

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
        <!-- 筛选工具栏 -->
        <div class="filter-bar">
          <div class="filter-left">
            <el-select
              v-model="filters.courseId"
              placeholder="选择课程"
              clearable
              class="w-180"
              @change="fetchGrades"
            >
              <el-option
                v-for="course in courseList"
                :key="course.id"
                :label="course.title || course.name"
                :value="course.id"
              />
            </el-select>
            <el-select
              v-model="filters.semester"
              placeholder="学期"
              clearable
              class="w-140"
              @change="fetchGrades"
            >
              <el-option label="2026春季" value="2026-SPRING" />
              <el-option label="2025秋季" value="2025-FALL" />
              <el-option label="2025春季" value="2025-SPRING" />
            </el-select>
            <el-input
              v-model="filters.keyword"
              placeholder="搜索学生姓名/学号"
              clearable
              class="w-200"
              @keyup.enter="fetchGrades"
            />
            <el-button type="primary" @click="fetchGrades"
              ><el-icon><Search /></el-icon>搜索</el-button
            >
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
              <el-table v-loading="loading" :data="gradeList" style="width: 100%">
                <el-table-column prop="studentName" label="学生姓名" width="110" />
                <el-table-column prop="studentId" label="学号" width="120" />
                <el-table-column
                  prop="courseName"
                  label="课程名称"
                  min-width="150"
                  show-overflow-tooltip
                />
                <el-table-column
                  prop="examTitle"
                  label="考试名称"
                  min-width="140"
                  show-overflow-tooltip
                />
                <el-table-column prop="score" label="成绩" width="80" align="center">
                  <template #default="{ row }">
                    <span class="score-badge" :class="getScoreClass(row.score)">{{
                      row.score ?? '--'
                    }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="rank" label="排名" width="70" align="center" />
                <el-table-column prop="submitTime" label="提交时间" width="160" />
                <el-table-column label="状态" width="90" align="center">
                  <template #default="{ row }">
                    <el-tag v-if="row.isAbnormal" type="danger" size="small" effect="light"
                      >异常</el-tag
                    >
                    <span v-else class="text-muted">正常</span>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="100" fixed="right">
                  <template #default="{ row }">
                    <el-button text type="primary" size="small" @click="viewGradeDetail(row)"
                      >详情</el-button
                    >
                  </template>
                </el-table-column>
                <template #empty>
                  <el-empty description="暂无成绩数据，请调整筛选条件" :image-size="80" />
                </template>
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
              <v-chart :option="distributionOption" class="chart-sm" autoresize />
            </div>

            <!-- 各课程对比 -->
            <div class="panel-card">
              <div class="panel-title">各课程平均分对比</div>
              <v-chart :option="courseCompareOption" class="chart-md" autoresize />
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
                    <span class="score" :class="item.score < 60 ? 'low' : 'high'"
                      >{{ item.score }}分</span
                    >
                  </div>
                  <span class="course">{{ item.courseName }}</span>
                </div>
              </div>
              <el-empty v-else description="暂无异常成绩" :image-size="50">
                <template #image><div class="empty-deco">⚠️</div></template>
              </el-empty>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 成绩详情对话框 -->
    <el-dialog v-model="showDetailDialog" title="成绩详情" width="600px" destroy-on-close>
      <div v-if="selectedGrade" class="grade-detail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="学生姓名">{{
            selectedGrade.studentName
          }}</el-descriptions-item>
          <el-descriptions-item label="学号">{{ selectedGrade.studentId }}</el-descriptions-item>
          <el-descriptions-item label="课程名称">{{
            selectedGrade.courseName
          }}</el-descriptions-item>
          <el-descriptions-item label="考试名称">{{
            selectedGrade.examTitle
          }}</el-descriptions-item>
          <el-descriptions-item label="得分">
            <span class="score-badge large" :class="getScoreClass(selectedGrade.score)">
              {{ selectedGrade.score }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="排名">{{ selectedGrade.rank || '--' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间" :span="2">{{
            selectedGrade.submitTime
          }}</el-descriptions-item>
        </el-descriptions>

        <!-- 答题明细（如果有） -->
        <div
          v-if="selectedGrade.details && selectedGrade.details.length > 0"
          class="answer-section"
        >
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
import { ref, reactive, onMounted } from 'vue'
import { Download, Search, Warning } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import examApi from '@/api/exam'
import courseApi from '@/api/course'
import * as XLSX from 'xlsx'
import { getChartTheme, getMonoSeries } from '@/utils/chartTheme'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'

// 加载状态
const loading = ref(false)

// 筛选条件
const filters = reactive({
  courseId: '',
  semester: '',
  keyword: '',
})

const showAbnormalOnly = ref(false)

// 分页
const pagination = reactive({
  page: 1,
  size: 10,
  total: 0,
})

// 数据
const gradeList = ref([])
const courseList = ref([])
const abnormalGrades = ref([])

// 概览统计数据
const overviewStats = ref([
  { label: '平均分', value: '--' },
  { label: '标准差', value: '--' },
  { label: '及格率', value: '--' },
  { label: '异常数', value: 0 },
])

// 对话框
const showDetailDialog = ref(false)
const selectedGrade = ref(null)

// 成绩分布图配置
const distTheme = getChartTheme()
const distMono = getMonoSeries()
const distributionOption = ref({
  tooltip: {
    trigger: 'axis',
    backgroundColor: distTheme.tooltipBg,
    borderColor: distTheme.tooltipBorder,
  },
  grid: { left: 45, right: 15, bottom: 25, top: 15 },
  xAxis: {
    type: 'category',
    data: ['0-59', '60-69', '70-79', '80-89', '90-100'],
    axisLine: { lineStyle: { color: distTheme.axis } },
    axisLabel: { fontSize: 11, color: distTheme.subText },
  },
  yAxis: {
    type: 'value',
    splitLine: { lineStyle: { color: distTheme.split, type: 'dashed' } },
    axisLabel: { fontSize: 11, color: distTheme.subText },
  },
  series: [
    {
      type: 'bar',
      barWidth: '55%',
      itemStyle: {
        borderRadius: [4, 4, 0, 0],
        color: (params) => distMono[params.dataIndex % distMono.length],
      },
      data: [],
    },
  ],
})

// 各课程平均分对比配置
const cmpTheme = getChartTheme()
const cmpMono = getMonoSeries()
const courseCompareOption = ref({
  tooltip: {
    trigger: 'axis',
    backgroundColor: cmpTheme.tooltipBg,
    borderColor: cmpTheme.tooltipBorder,
    formatter: '{b}: {c}分',
  },
  grid: { left: 100, right: 20, bottom: 20, top: 10 },
  xAxis: {
    type: 'value',
    min: 0,
    max: 100,
    splitLine: { lineStyle: { color: cmpTheme.split, type: 'dashed' } },
    axisLabel: { color: cmpTheme.subText, fontSize: 11 },
  },
  yAxis: {
    type: 'category',
    data: [],
    inverse: true,
    axisLine: { show: false },
    axisTick: { show: false },
    axisLabel: { color: cmpTheme.text, fontSize: 11, width: 90, overflow: 'truncate' },
  },
  series: [
    {
      type: 'bar',
      barWidth: '65%',
      itemStyle: {
        borderRadius: [0, 4, 4, 0],
        color: {
          type: 'linear',
          x: 0,
          y: 0,
          x2: 1,
          y2: 0,
          colorStops: [
            { offset: 0, color: cmpMono[0] },
            { offset: 1, color: cmpMono[6] },
          ],
        },
      },
      label: { show: true, position: 'right', color: cmpTheme.text, fontSize: 11 },
      data: [],
    },
  ],
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
      size: pagination.size,
    }

    if (filters.courseId) params.courseId = filters.courseId
    if (filters.semester) params.semester = filters.semester
    if (filters.keyword) params.keyword = filters.keyword

    const res = await examApi.getMyExamSessions(params)
    const rawData = res.data?.records || res.data?.list || res.data || res || []
    let data = Array.isArray(rawData)
      ? rawData
      : (rawData?.records ?? rawData?.grades ?? rawData?.items ?? [])

    if (!Array.isArray(data)) {
      // 如果返回的是对象，尝试提取数组
      data = data.records || data.grades || data.items || []
    }

    // 过滤异常成绩
    if (showAbnormalOnly.value) {
      data = data.filter((item) => item.isAbnormal || item.score < 40 || item.score === 100)
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

  const scores = data.map((g) => g.score).filter((s) => s !== null && s !== undefined && !isNaN(s))
  if (scores.length === 0) return

  const avg = (scores.reduce((a, b) => a + b, 0) / scores.length).toFixed(1)
  const variance = scores.reduce((sum, score) => sum + Math.pow(score - avg, 2), 0) / scores.length
  const stdDev = Math.sqrt(variance).toFixed(1)
  const passCount = scores.filter((s) => s >= 60).length
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

  const scores = data.map((g) => g.score).filter((s) => s !== null && s !== undefined && !isNaN(s))

  // 成绩分布
  const dist = [0, 0, 0, 0, 0]
  scores.forEach((s) => {
    if (s < 60) dist[0]++
    else if (s < 70) dist[1]++
    else if (s < 80) dist[2]++
    else if (s < 90) dist[3]++
    else dist[4]++
  })
  distributionOption.value.series[0].data = dist

  // 按课程分组计算平均分
  const courseMap = new Map()
  data.forEach((item) => {
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
      avg: (scores.reduce((a, b) => a + b, 0) / scores.length).toFixed(1),
    }))
    .sort((a, b) => a.avg - b.avg)

  courseCompareOption.value.yAxis.data = courseAvgs.map((c) => c.name)
  courseCompareOption.value.series[0].data = courseAvgs.map((c) => parseFloat(c.avg))
}

/**
 * 提取异常成绩
 */
function extractAbnormal(data) {
  if (!Array.isArray(data)) {
    abnormalGrades.value = []
    return
  }

  abnormalGrades.value = data.filter(
    (item) => item.isAbnormal || (item.score != null && (item.score < 45 || item.score >= 98)),
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
      序号: idx + 1,
      学生姓名: item.studentName || item.fullName || '-',
      课程名称: item.courseName || '-',
      成绩: item.score ?? '-',
      总分: item.totalScore ?? item.total ?? '-',
      提交时间: item.submitTime || item.createdAt || '-',
      状态: item.status || '-',
    }))
    const ws = XLSX.utils.json_to_sheet(exportData)
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '成绩报表')
    XLSX.writeFile(
      wb,
      `成绩报表_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}.xlsx`,
    )
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

.ico-accent {
  background: var(--accent-soft);
  color: var(--accent);
}

.w-180 {
  width: 180px;
}
.w-140 {
  width: 140px;
}
.w-200 {
  width: 200px;
}

.chart-sm {
  height: 220px;
}
.chart-md {
  height: 260px;
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

.score-badge.excellent {
  background: var(--ok-soft);
  color: var(--ok);
}
.score-badge.good {
  background: var(--info-soft);
  color: var(--info);
}
.score-badge.medium {
  background: var(--violet-soft);
  color: var(--violet);
}
.score-badge.pass {
  background: var(--warn-soft);
  color: var(--warn);
}
.score-badge.fail {
  background: var(--accent-soft);
  color: var(--accent);
}
.score-badge.large {
  font-size: 20px;
  padding: 6px 16px;
}

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
  color: var(--accent);
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
  color: var(--accent);
  background: var(--accent-soft);
}

.abnormal-info .score.high {
  color: var(--warn);
  background: var(--warn-soft);
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

.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
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
