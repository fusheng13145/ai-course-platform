﻿﻿﻿<template>
  <div class="fade-in exam-management">
    <!-- 页面头部 -->
    <div class="page-header">
      <h2 class="page-title">考试管理</h2>
      <p class="page-desc">全校考试监管与异常处理</p>
    </div>

    <!-- 统计概览卡片 -->
    <div class="stats-row">
      <div class="stat-mini">
        <span class="stat-label">总试卷数</span>
        <span class="stat-num">{{ overview.totalPapers || '--' }}</span>
      </div>
      <div class="stat-mini">
        <span class="stat-label">进行中</span>
        <span class="stat-num active">{{ overview.activeExams || 0 }}</span>
      </div>
      <div class="stat-mini">
        <span class="stat-label">待审核</span>
        <span class="stat-num warning">{{ overview.pendingReview || 0 }}</span>
      </div>
      <div class="stat-mini">
        <span class="stat-label">作弊嫌疑</span>
        <span class="stat-num danger">{{ overview.cheatingCases || 0 }}</span>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="table-toolbar">
      <div class="toolbar-left">
        <el-select v-model="filters.status" placeholder="状态筛选" clearable style="width:130px" @change="fetchExamList">
          <el-option label="全部" value="" />
          <el-option label="草稿" value="DRAFT" />
          <el-option label="待审核" value="PENDING" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已结束" value="ENDED" />
        </el-select>
        <el-input v-model="filters.keyword" placeholder="搜索试卷名称/课程" clearable style="width:220px" @keyup.enter="fetchExamList" />
        <el-button type="primary" @click="fetchExamList"><el-icon><Search /></el-icon>搜索</el-button>
      </div>
      <div class="toolbar-right">
        <el-button @click="showActiveSessions = true">
          <el-icon><Timer /></el-icon>进行中考试
        </el-button>
      </div>
    </div>

    <!-- 试卷列表 -->
    <div class="content-card">
      <el-table :data="examList" style="width:100%" v-loading="loading">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="试卷名称" min-width="200" show-overflow-tooltip />
        <el-table-column prop="courseName" label="所属课程" min-width="150" show-overflow-tooltip />
        <el-table-column prop="teacherName" label="创建教师" width="120" />
        <el-table-column prop="totalScore" label="总分" width="70" align="center" />
        <el-table-column prop="duration" label="时长(分钟)" width="100" align="center" />
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small" effect="light">
              {{ getStatusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="viewDetail(row)">详情</el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              text type="success"
              size="small"
              @click="handleApprove(row)"
            >通过</el-button>
            <el-button
              v-if="row.status === 'PENDING'"
              text type="danger"
              size="small"
              @click="handleReject(row)"
            >拒绝</el-button>
            <el-button text type="warning" size="small" @click="viewStatistics(row)">统计</el-button>
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
          @size-change="fetchExamList"
          @current-change="fetchExamList"
        />
      </div>
    </div>

    <!-- 进行中考试对话框 -->
    <el-dialog v-model="showActiveSessions" title="进行中的考试" width="800px" destroy-on-close>
      <el-table :data="activeSessions" v-loading="loadingSessions" max-height="400">
        <el-table-column prop="paperTitle" label="试卷名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="courseName" label="课程" width="140" />
        <el-table-column prop="studentCount" label="参考人数" width="90" align="center" />
        <el-table-column prop="startTime" label="开始时间" width="160" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button text type="danger" size="small" @click="forceSubmit(row)">强制交卷</el-button>
            <el-button text type="primary" size="small" @click="viewCheating(row)">作弊检测</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 作弊检测日志对话框 -->
    <el-dialog v-model="showCheatingDialog" title="作弊检测记录" width="700px" destroy-on-close>
      <div v-if="cheatingLogs.length > 0">
        <div v-for="(log, idx) in cheatingLogs" :key="idx" class="cheating-item">
          <div class="cheating-header">
            <el-tag type="danger" size="small" effect="dark">异常</el-tag>
            <span class="student-name">{{ log.studentName || log.studentId }}</span>
            <span class="cheating-time">{{ log.detectedAt || log.time }}</span>
          </div>
          <div class="cheating-content">
            <p><strong>异常类型：</strong>{{ log.type || '未知' }}</p>
            <p><strong>详细描述：</strong>{{ log.description || log.detail }}</p>
            <p><strong>风险等级：</strong>
              <el-rate :model-value="getRiskLevel(log.riskLevel)" disabled :colors="['#8BA888', '#CBB58A', '#C48080']" />
            </p>
          </div>
        </div>
      </div>
      <el-empty v-else description="暂无作弊检测记录" />
    </el-dialog>

    <!-- 试卷统计对话框 -->
    <el-dialog v-model="showStatsDialog" title="试卷统计分析" width="650px" destroy-on-close>
      <div v-if="statistics" class="stats-detail">
        <div class="stats-grid">
          <div class="stats-item">
            <span class="label">平均分</span>
            <span class="value primary">{{ statistics.avgScore || '--' }}</span>
          </div>
          <div class="stats-item">
            <span class="label">最高分</span>
            <span class="value success">{{ statistics.maxScore || '--' }}</span>
          </div>
          <div class="stats-item">
            <span class="label">最低分</span>
            <span class="value danger">{{ statistics.minScore || '--' }}</span>
          </div>
          <div class="stats-item">
            <span class="label">参考率</span>
            <span class="value warning">{{ statistics.attendanceRate ? (statistics.attendanceRate * 100).toFixed(1) + '%' : '--' }}</span>
          </div>
          <div class="stats-item">
            <span class="label">及格率</span>
            <span class="value success">{{ statistics.passRate ? (statistics.passRate * 100).toFixed(1) + '%' : '--' }}</span>
          </div>
          <div class="stats-item">
            <span class="label">作弊嫌疑数</span>
            <span class="value danger">{{ statistics.cheatingSuspects || 0 }}</span>
          </div>
        </div>

        <!-- 成绩分布图 -->
        <div class="chart-section">
          <div class="section-title">成绩分布</div>
          <v-chart :option="gradeChartOption" style="height: 250px" autoresize />
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { Search, Timer } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import examApi from '@/api/exam'

// 加载状态
const loading = ref(false)
const loadingSessions = ref(false)

// 筛选条件
const filters = reactive({
  status: '',
  keyword: ''
})

// 分页
const pagination = reactive({
  page: 1,
  size: 10,
  total: 0
})

// 数据列表
const examList = ref([])
const activeSessions = ref([])

// 概览数据
const overview = reactive({
  totalPapers: 0,
  activeExams: 0,
  pendingReview: 0,
  cheatingCases: 0
})

// 对话框状态
const showActiveSessions = ref(false)
const showCheatingDialog = ref(false)
const showStatsDialog = ref(false)

// 作弊检测日志
const cheatingLogs = ref([])

// 试卷统计数据
const statistics = ref(null)

// 成绩分布图表配置
const gradeChartOption = ref({
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(255, 252, 248, 0.95)',
    borderColor: '#DDD6CE'
  },
  grid: { left: 50, right: 20, bottom: 30, top: 20 },
  xAxis: {
    type: 'category',
    data: ['0-59', '60-69', '70-79', '80-89', '90-100'],
    axisLine: { lineStyle: { color: '#DDD6CE' } },
    axisLabel: { color: '#928B83' }
  },
  yAxis: { type: 'value', splitLine: { lineStyle: { color: '#DDD6CE', type: 'dashed' } }, axisLabel: { color: '#928B83' } },
  series: [{
    type: 'bar',
    barWidth: '45%',
    itemStyle: {
      borderRadius: [6, 6, 0, 0],
      color: {
        type: 'linear',
        x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [
          { offset: 0, color: '#7A8BA5' },
          { offset: 1, color: 'rgba(122, 139, 165, 0.3)' }
        ]
      }
    },
    data: []
  }]
})

/**
 * 获取试卷列表
 */
async function fetchExamList() {
  loading.value = true
  try {
    const params = {
      page: pagination.page - 1,
      size: pagination.size,
      all: true // 获取全部试卷（管理员视角）
    }

    if (filters.status) params.status = filters.status
    if (filters.keyword) params.keyword = filters.keyword

    const res = await examApi.getExamPaperList(params)
    const rawData = res.data?.records || res.data?.list || res.data || res

    const data = Array.isArray(rawData) ? rawData : (rawData?.list ? rawData.list : [])
    examList.value = Array.isArray(data) ? data : []
    pagination.total = rawData?.total ?? rawData?.count ?? data.length

    // 更新概览数据
    overview.totalPapers = pagination.total
  } catch (error) {
    console.error('加载试卷列表失败:', error)
    ElMessage.error('加载试卷列表失败')
  } finally {
    loading.value = false
  }
}

/**
 * 获取进行中的考试
 */
async function fetchActiveSessions() {
  loadingSessions.value = true
  try {
    const res = await examApi.getActiveExams()
    const data = res.data?.list || res.data || res || []
    activeSessions.value = Array.isArray(data) ? data : []
    overview.activeExams = activeSessions.value.length
  } catch (error) {
    console.warn('获取进行中考试失败:', error)
    activeSessions.value = []
  } finally {
    loadingSessions.value = false
  }
}

/**
 * 获取状态对应的标签类型
 */
function getStatusType(status) {
  const map = {
    DRAFT: 'info',
    PENDING: 'warning',
    PUBLISHED: 'success',
    ENDED: ''
  }
  return map[status] || 'info'
}

/**
 * 获取状态文本
 */
function getStatusText(status) {
  const map = {
    DRAFT: '草稿',
    PENDING: '待审核',
    PUBLISHED: '已发布',
    ENDED: '已结束'
  }
  return map[status] || status
}

/**
 * 审核通过
 */
async function handleApprove(row) {
  try {
    await ElMessageBox.confirm(`确定通过试卷「${row.title}」的审核？`, '确认操作', {
      confirmButtonText: '确定通过',
      cancelButtonText: '取消',
      type: 'info'
    })

    await request.patch(`/exam-papers/${row.id}/approve`)
    ElMessage.success('审核通过')
    fetchExamList()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

/**
 * 审核拒绝
 */
async function handleReject(row) {
  try {
    const { value: reason } = await ElMessageBox.prompt(
      `请输入拒绝试卷「${row.title}」的原因：`,
      '拒绝原因',
      {
        confirmButtonText: '确定拒绝',
        cancelButtonText: '取消',
        inputPattern: /\S+/,
        inputErrorMessage: '请输入拒绝原因'
      }
    )

    await request.post(`/exam-papers/${row.id}/reject`, { reason })
    ElMessage.success('已拒绝该试卷')
    fetchExamList()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

/**
 * 强制交卷
 */
async function forceSubmit(session) {
  try {
    await ElMessageBox.confirm(
      `确定强制结束「${session.paperTitle}」的考试？\n此操作将使所有未交卷的学生强制提交！`,
      '危险操作',
      {
        confirmButtonText: '确定强制交卷',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )

    await examApi.forceSubmit(session.id)
    ElMessage.success('已强制交卷')
    fetchActiveSessions()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('操作失败')
    }
  }
}

/**
 * 查看作弊检测
 */
async function viewCheating(session) {
  showCheatingDialog.value = true
  try {
    const res = await examApi.getCheatingDetect(session.id)
    const data = res.data?.list || res.data || res || []
    cheatingLogs.value = Array.isArray(data) ? data : []

    // 更新作弊嫌疑数
    overview.cheatingCases = cheatingLogs.value.length
  } catch (error) {
    console.warn('获取作弊检测失败:', error)
    cheatingLogs.value = []
  }
}

/**
 * 获取风险等级（用于评分组件）
 */
function getRiskLevel(level) {
  if (!level) return 1
  if (typeof level === 'string') {
    const map = { LOW: 1, MEDIUM: 2, HIGH: 3 }
    return map[level.toUpperCase()] || 1
  }
  return Math.min(Math.max(level, 1), 3)
}

/**
 * 查看试卷详情
 */
function viewDetail(row) {
  // 可以跳转到详情页或打开对话框
  ElMessage.info(`查看试卷: ${row.title}`)
}

/**
 * 查看试卷统计
 */
async function viewStatistics(row) {
  showStatsDialog.value = true
  try {
    const res = await examApi.getExamStatistics(row.id)
    statistics.value = res.data || res

    // 更新成绩分布图
    if (statistics.value?.gradeDistribution) {
      gradeChartOption.value.series[0].data = statistics.value.gradeDistribution
    } else if (statistics.value?.distribution) {
      gradeChartOption.value.series[0].data = statistics.value.distribution
    }
  } catch (error) {
    console.warn('获取统计数据失败:', error)
    statistics.value = null
    gradeChartOption.value.series[0].data = []
  }
}

// 初始化
onMounted(() => {
  fetchExamList()
  fetchActiveSessions()
})
</script>

<style scoped>
.exam-management {
  max-width: 1400px;
  margin: 0 auto;
}

/* 页面头部 */
.page-header {
  margin-bottom: var(--ac-spacing-lg);
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

/* 统计概览行 */
.stats-row {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-lg);
}

.stat-mini {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-md);
  border: 1px solid var(--ac-border);
  padding: 16px 20px;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: all var(--ac-transition-normal);
}

.stat-mini:hover {
  box-shadow: var(--ac-shadow-md);
  transform: translateY(-2px);
}

.stat-label {
  font-size: 13px;
  color: var(--ac-text-muted);
  font-weight: 500;
}

.stat-num {
  font-size: 26px;
  font-weight: 700;
  color: var(--ac-text-primary);
}

.stat-num.active {
  color: var(--ac-primary);
}

.stat-num.warning {
  color: var(--ac-warning);
}

.stat-num.danger {
  color: var(--ac-danger);
}

/* 内容卡片 */
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

/* 作弊检测项 */
.cheating-item {
  padding: 16px;
  margin-bottom: 12px;
  background: var(--ac-bg);
  border-radius: var(--ac-radius-md);
  border-left: 3px solid var(--ac-danger);
}

.cheating-item:last-child {
  margin-bottom: 0;
}

.cheating-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}

.student-name {
  font-weight: 600;
  color: var(--ac-text-primary);
}

.cheating-time {
  margin-left: auto;
  font-size: 12px;
  color: var(--ac-text-muted);
}

.cheating-content p {
  font-size: 13px;
  color: var(--ac-text-secondary);
  margin-bottom: 4px;
  line-height: 1.6;
}

.cheating-content p:last-child {
  margin-bottom: 0;
}

/* 统计详情 */
.stats-detail {
  padding: 0;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stats-item {
  background: var(--ac-bg);
  border-radius: var(--ac-radius-sm);
  padding: 18px;
  text-align: center;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stats-item .label {
  font-size: 13px;
  color: var(--ac-text-muted);
}

.stats-item .value {
  font-size: 24px;
  font-weight: 700;
  color: var(--ac-text-primary);
}

.stats-item .value.primary { color: var(--ac-primary); }
.stats-item .value.success { color: var(--ac-success); }
.stats-item .value.warning { color: var(--ac-warning); }
.stats-item .value.danger { color: var(--ac-danger); }

.chart-section {
  background: var(--ac-bg);
  border-radius: var(--ac-radius-md);
  padding: 20px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 12px;
}

/* 响应式 */
@media (max-width: 768px) {
  .stats-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
