﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿﻿<template>
  <div class="fade-in">
    <!-- 统计卡片区域 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--ac-primary-bg); color: var(--ac-primary)">
          <el-icon :size="24"><Reading /></el-icon>
        </div>
        <div class="stat-info">
          <h3>{{ stats.courseCount }}</h3>
          <p>在学课程</p>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: rgba(139,168,136,0.15); color: var(--ac-success)">
          <el-icon :size="24"><TrophyBase /></el-icon>
        </div>
        <div class="stat-info">
          <h3>{{ stats.avgScore }}</h3>
          <p>平均分</p>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: rgba(203,181,138,0.18); color: var(--ac-warning)">
          <el-icon :size="24"><Calendar /></el-icon>
        </div>
        <div class="stat-info">
          <h3>{{ stats.streakDays }}</h3>
          <p>连续学习天数</p>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: rgba(196,128,128,0.15); color: var(--ac-danger)">
          <el-icon :size="24"><WarningFilled /></el-icon>
        </div>
        <div class="stat-info">
          <h3>{{ stats.todoCount }}</h3>
          <p>待办事项</p>
        </div>
      </div>
    </div>

    <!-- 图表与进度区域 -->
    <div class="dashboard-grid">
      <!-- 学习趋势折线图 -->
      <div class="chart-card">
        <div class="chart-header">
          <span class="chart-title">学习趋势</span>
          <el-radio-group v-model="trendType" size="small" @change="loadTrendData">
            <el-radio-button value="week">本周</el-radio-button>
            <el-radio-button value="month">本月</el-radio-button>
          </el-radio-group>
        </div>
        <v-chart v-loading="chartLoading" :option="lineOption" style="height: 300px" autoresize />
      </div>

      <!-- 课程进度列表 -->
      <div class="chart-card progress-card">
        <div class="chart-header">
          <span class="chart-title">最近学习的课程</span>
          <el-button text type="primary" size="small" @click="$router.push('/student/courses')">
            查看全部
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
        <div v-loading="courseLoading" class="progress-list">
          <div v-for="c in recentCourses" :key="c.id" class="progress-item" @click="$router.push(`/student/courses/${c.id}`)">
            <div class="progress-header">
              <div class="course-name">
                <el-icon class="course-icon"><Document /></el-icon>
                <span>{{ c.name }}</span>
              </div>
              <span class="progress-percent">{{ c.progress }}%</span>
            </div>
            <el-progress
              :percentage="c.progress"
              :stroke-width="8"
              :show-text="false"
              :color="getProgressColor(c.progress)"
            />
          </div>
          <el-empty v-if="!courseLoading && recentCourses.length === 0" description="暂无课程进度" :image-size="80" />
        </div>
      </div>
    </div>

    <!-- 学习时长柱状图 -->
    <div class="chart-card bar-card">
      <div class="chart-header">
        <span class="chart-title">本周学习时长（分钟）</span>
      </div>
      <v-chart v-loading="barLoading" :option="barOption" style="height: 260px" autoresize />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { Reading, TrophyBase, Calendar, WarningFilled, Document, ArrowRight } from '@element-plus/icons-vue'
import analyticsApi from '@/api/analytics'
import enrollmentApi from '@/api/enrollment'
import courseApi from '@/api/course'
import learningRecordApi from '@/api/learningRecord'

const auth = useAuthStore()

/* ---- 数据状态 ---- */
const stats = ref({ courseCount: 0, avgScore: 0, streakDays: 0, todoCount: 0 })
const recentCourses = ref([])
const trendType = ref('week')
const chartLoading = ref(false)
const courseLoading = ref(false)
const barLoading = ref(false)

/* ---- 莫兰迪色系图表配色 ---- */
const morandiColors = ['#7A8BA5', '#B89A8A', '#8BA888', '#CBB58A']

/* ---- 折线图配置：学习趋势 ---- */
const lineOption = ref({
  tooltip: { trigger: 'axis', backgroundColor: '#FFFCF8', borderColor: '#DDD6CE', textStyle: { color: '#3D3835' } },
  grid: { left: 48, right: 20, bottom: 30, top: 20 },
  xAxis: { type: 'category', data: [], axisLine: { lineStyle: { color: '#DDD6CE' } }, axisLabel: { color: '#928B83', fontSize: 12 } },
  yAxis: { type: 'value', axisLine: { show: false }, splitLine: { lineStyle: { color: '#DDD6CE', type: 'dashed' } }, axisLabel: { color: '#928B83', fontSize: 12 } },
  series: [
    {
      name: '学习时长',
      type: 'line',
      data: [],
      smooth: true,
      symbol: 'circle',
      symbolSize: 8,
      lineStyle: { width: 2.5, color: morandiColors[0] },
      itemStyle: { color: morandiColors[0] },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [{ offset: 0, color: 'rgba(122,139,165,0.25)' }, { offset: 1, color: 'rgba(122,139,165,0.02)' }]
        }
      }
    }
  ]
})

/* ---- 柱状图配置：每日学习时长 ---- */
const barOption = ref({
  tooltip: { trigger: 'axis', backgroundColor: '#FFFCF8', borderColor: '#DDD6CE', textStyle: { color: '#3D3835' } },
  grid: { left: 48, right: 20, bottom: 30, top: 20 },
  xAxis: {
    type: 'category',
    data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
    axisLine: { lineStyle: { color: '#DDD6CE' } },
    axisLabel: { color: '#928B83', fontSize: 12 }
  },
  yAxis: {
    type: 'value',
    axisLine: { show: false },
    splitLine: { lineStyle: { color: '#DDD6CE', type: 'dashed' } },
    axisLabel: { color: '#928B83', fontSize: 12 }
  },
  series: [{
    type: 'bar',
    data: [0, 0, 0, 0, 0, 0, 0],
    itemStyle: {
      borderRadius: [6, 6, 0, 0],
      color: {
        type: 'linear',
        x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [{ offset: 0, color: morandiColors[0] }, { offset: 1, color: 'rgba(122,139,165,0.4)' }]
      }
    },
    barWidth: '40%'
  }]
})

/* ---- 根据进度返回颜色 ---- */
function getProgressColor(progress) {
  if (progress >= 80) return '#8BA888'
  if (progress >= 50) return '#7A8BA5'
  if (progress >= 30) return '#CBB58A'
  return '#C48080'
}

/* ---- 加载学习统计（增强） ---- */
async function loadLearningStats() {
  try {
    const res = await learningRecordApi.getLearningStats()
    const data = res.data || res || {}
    // 用学习统计补充/覆盖统计数据
    if (data.totalStudyTime != null && stats.value.streakDays === 0) {
      stats.value.streakDays = data.consecutiveDays ?? data.streakDays ?? stats.value.streakDays
    }
    if (data.todoCount != null) {
      stats.value.todoCount = data.todoCount
    }
  } catch (e) {
    console.warn('加载学习统计失败（非关键）:', e)
  }
}

/* ---- 加载统计数据 ---- */
async function loadStats() {
  try {
    const userId = auth.userId
    if (!userId) return
    const res = await analyticsApi.getStudentAnalytics(userId)
    const data = res.data || res || {}
    stats.value = {
      courseCount: data.enrolledCourses ?? data.courseCount ?? 0,
      avgScore: data.averageScore != null ? Math.round(data.averageScore) : (data.avgScore ?? 0),
      streakDays: data.streakDays ?? data.consecutiveDays ?? 0,
      todoCount: data.pendingTasks ?? data.todoCount ?? 0
    }
  } catch (e) {
    console.error('加载统计数据失败:', e)
    // 确保显示默认值而非空白
    stats.value = { courseCount: 0, avgScore: 0, streakDays: 0, todoCount: 0 }
  }
}

/* ---- 加载趋势数据 ---- */
async function loadTrendData() {
  chartLoading.value = true
  barLoading.value = true
  try {
    const userId = auth.userId
    const res = await analyticsApi.getTrendData({ days: 7 })
    const data = res.data || res || {}
    const trendList = Array.isArray(data) ? data : (data.list ?? data.trends ?? data.dailyStats ?? [])

    if (trendType.value === 'week') {
      // 本周数据：取最近7天
      const labels = trendList.length > 0
        ? trendList.slice(-7).map(item => item.date || item.label || '')
        : ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
      const values = trendList.length > 0
        ? trendList.slice(-7).map(item => item.duration ?? item.studyTime ?? item.value ?? 0)
        : [0, 0, 0, 0, 0, 0, 0]
      lineOption.value.xAxis.data = labels
      lineOption.value.series[0].data = values
      // 同步更新柱状图
      barOption.value.xAxis.data = labels
      barOption.value.series[0].data = values
    } else {
      // 本月数据
      if (trendList.length > 0) {
        lineOption.value.xAxis.data = trendList.map(item => item.date || item.label || '')
        lineOption.value.series[0].data = trendList.map(item => item.duration ?? item.studyTime ?? item.value ?? 0)
      } else {
        // 无数据时显示空图表（不显示模拟数据）
        lineOption.value.xAxis.data = []
        lineOption.value.series[0].data = []
      }
    }
  } catch (e) {
    console.error('加载趋势数据失败:', e)
    lineOption.value.series[0].data = []
    barOption.value.series[0].data = []
  } finally {
    chartLoading.value = false
    barLoading.value = false
  }
}

/* ---- 加载课程进度 ---- */
async function loadCourseProgress() {
  courseLoading.value = true
  try {
    const [enrollRes, courseRes] = await Promise.all([
      enrollmentApi.getMyEnrollments(),
      courseApi.getCourseList()
    ])
    const enrollData = enrollRes.data?.list || enrollRes.data || enrollRes || []
    const courseList = courseRes.data?.list || courseRes.data || courseRes || []
    const courseMap = {}
    courseList.forEach(c => { courseMap[c.id] = c.name })

    // 取最近学习的5门课程，按进度或最近活动排序
    recentCourses.value = enrollData
      .slice(0, 5)
      .map(e => ({
        id: e.courseId,
        name: courseMap[e.courseId] || `课程 #${e.courseId}`,
        progress: e.progress ? Math.round(Number(e.progress)) : 0,
        lastActive: e.lastActiveAt || e.updatedAt
      }))
  } catch (e) {
    console.error('加载课程进度失败:', e)
  } finally {
    courseLoading.value = false
  }
}

/* ---- 初始化加载 ---- */
onMounted(async () => {
  await Promise.all([loadStats(), loadTrendData(), loadCourseProgress(), loadLearningStats()])
})
</script>

<style scoped>
/* ---- 统计卡片区域 ---- */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-lg);
}

.stat-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: 20px 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  transition: all var(--ac-transition-normal);
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--ac-shadow-md);
}

.stat-icon {
  width: 52px;
  height: 52px;
  border-radius: var(--ac-radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  flex-shrink: 0;
}

.stat-info h3 {
  font-size: 26px;
  font-weight: 700;
  color: var(--ac-text-primary);
  margin: 0;
  line-height: 1.2;
}

.stat-info p {
  font-size: 13px;
  color: var(--ac-text-muted);
  margin-top: 4px;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-md);
}

.chart-card {
  padding: var(--ac-spacing-lg);
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
  border: 1px solid var(--ac-border);
  transition: all var(--ac-transition-normal);
}

.chart-card:hover {
  box-shadow: var(--ac-shadow-md);
}

.bar-card {
  margin-top: var(--ac-spacing-md);
}

.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ac-spacing-md);
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
}

.progress-list {
  max-height: 340px;
  overflow-y: auto;
}

.progress-item {
  padding: var(--ac-spacing-sm) 0;
  cursor: pointer;
  border-radius: var(--ac-radius-sm);
  transition: background var(--ac-transition-fast);
  margin-bottom: var(--ac-spacing-xs);
}

.progress-item:hover {
  background: var(--ac-bg-hover);
}

.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.course-name {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--ac-text-primary);
}

.course-icon {
  color: var(--ac-text-muted);
  font-size: 16px;
}

.progress-percent {
  font-size: 13px;
  font-weight: 600;
  color: var(--ac-primary);
}

@media (max-width: 900px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }
  .stat-cards {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 600px) {
  .stat-cards {
    grid-template-columns: 1fr;
  }
}
</style>
