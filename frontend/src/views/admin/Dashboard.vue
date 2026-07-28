﻿<template>
  <div class="fade-in admin-dashboard">
    <!-- 统计卡片区域 -->
    <div class="stat-cards">
      <div class="stat-card" v-for="(item, index) in statCards" :key="index">
        <div class="stat-icon" :style="{ background: item.color }">
          <el-icon :size="24"><component :is="item.icon" /></el-icon>
        </div>
        <div class="stat-info">
          <h3>{{ item.value }}</h3>
          <p>{{ item.label }}</p>
        </div>
        <div class="stat-trend" :class="item.trend > 0 ? 'up' : 'down'" v-if="item.trend !== undefined">
          <el-icon :size="14"><Top v-if="item.trend > 0" /><Bottom v-else /></el-icon>
          {{ Math.abs(item.trend) }}%
        </div>
      </div>
    </div>

    <!-- 主内容区域 -->
    <div class="dashboard-grid">
      <!-- 用户增长趋势图 -->
      <div class="chart-card full-width">
        <div class="chart-header">
          <span class="chart-title">用户增长趋势</span>
          <div class="time-switcher">
            <el-radio-group v-model="trendRange" size="small" @change="fetchTrendData">
              <el-radio-button value="7">近7天</el-radio-button>
              <el-radio-button value="30">近30天</el-radio-button>
            </el-radio-group>
          </div>
        </div>
        <v-chart :option="trendOption" style="height: 320px" autoresize />
      </div>

      <!-- 课程分类分布 + 快捷操作 -->
      <div class="chart-card">
        <div class="chart-title">课程分类分布</div>
        <v-chart :option="pieOption" style="height: 300px" autoresize />
      </div>

      <!-- 系统动态日志 -->
      <div class="chart-card">
        <div class="chart-header">
          <span class="chart-title">系统动态</span>
          <el-button text type="primary" size="small" @click="refreshLogs">刷新</el-button>
        </div>
        <div class="log-list">
          <div v-for="log in recentLogs" :key="log.id" class="log-item">
            <el-tag
              size="small"
              :type="getLogType(log.level)"
              effect="light"
              class="log-tag"
            >
              {{ log.level }}
            </el-tag>
            <span class="log-message">{{ log.message }}</span>
            <span class="log-time">{{ formatTime(log.createdAt || log.time) }}</span>
          </div>
          <el-empty v-if="recentLogs.length === 0" description="暂无系统动态" :image-size="60" />
        </div>
      </div>

      <!-- 快捷操作面板 -->
      <div class="quick-actions card-base">
        <div class="card-title">快捷操作</div>
        <div class="action-grid">
          <router-link
            v-for="action in quickActions"
            :key="action.path"
            :to="action.path"
            class="action-item"
          >
            <div class="action-icon" :style="{ background: action.bg }">
              <el-icon :size="20"><component :is="action.icon" /></el-icon>
            </div>
            <span>{{ action.label }}</span>
          </router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, markRaw } from 'vue'
import {
  User,
  Reading,
  Document,
  TrophyBase,
  Top,
  Bottom,
  Monitor,
  Plus,
  Setting,
  Bell,
  DataLine
} from '@element-plus/icons-vue'
import analyticsApi from '@/api/analytics'
import systemApi from '@/api/system'
import loginLogApi from '@/api/loginLog'

// 统计数据
const stats = reactive({
  userCount: 0,
  courseCount: 0,
  examCount: 0,
  submissionCount: 0,
  activeUsers: 0,
  completionRate: 0
})

// 统计卡片配置
const statCards = ref([
  {
    icon: markRaw(User),
    label: '用户总数',
    value: 0,
    color: '#7A8BA5',
    trend: 12.5
  },
  {
    icon: markRaw(Reading),
    label: '课程总数',
    value: 0,
    color: '#8BA888',
    trend: 8.2
  },
  {
    icon: markRaw(Document),
    label: '考试总数',
    value: 0,
    color: '#CBB58A',
    trend: -2.1
  },
  {
    icon: markRaw(TrophyBase),
    label: '提交总数',
    value: 0,
    color: '#C48080',
    trend: 15.6
  }
])

// 趋势图时间范围
const trendRange = ref('7')

// 趋势图配置
const trendOption = ref({
  tooltip: {
    trigger: 'axis',
    backgroundColor: 'rgba(255, 252, 248, 0.95)',
    borderColor: '#DDD6CE',
    textStyle: { color: '#3D3835' }
  },
  grid: { left: 50, right: 20, bottom: 30, top: 20 },
  xAxis: {
    type: 'category',
    data: [],
    axisLine: { lineStyle: { color: '#DDD6CE' } },
    axisLabel: { color: '#928B83', fontSize: 12 }
  },
  yAxis: {
    type: 'value',
    axisLine: { show: false },
    axisTick: { show: false },
    splitLine: { lineStyle: { color: '#DDD6CE', type: 'dashed' } },
    axisLabel: { color: '#928B83' }
  },
  series: [
    {
      data: [],
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      lineStyle: { width: 2.5, color: '#7A8BA5' },
      areaStyle: {
        color: {
          type: 'linear',
          x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(122, 139, 165, 0.25)' },
            { offset: 1, color: 'rgba(122, 139, 165, 0.02)' }
          ]
        }
      },
      itemStyle: { color: '#7A8BA5' }
    }
  ]
})

// 饼图配置（课程分布）
const pieOption = ref({
  tooltip: {
    trigger: 'item',
    formatter: '{b}: {c} ({d}%)',
    backgroundColor: 'rgba(255, 252, 248, 0.95)',
    borderColor: '#DDD6CE'
  },
  legend: {
    orient: 'vertical',
    right: 10,
    top: 'center',
    textStyle: { color: '#5E574F', fontSize: 12 }
  },
  series: [{
    type: 'pie',
    radius: ['45%', '70%'],
    center: ['40%', '50%'],
    avoidLabelOverlap: false,
    itemStyle: {
      borderRadius: 6,
      borderColor: '#fff',
      borderWidth: 2
    },
    label: { show: false },
    emphasis: {
      label: { show: true, fontSize: 14, fontWeight: 'bold' }
    },
    data: []
  }],
  color: ['#7A8BA5', '#B89A8A', '#8BA888', '#CBB58A', '#C48080']
})

// 系统日志
const recentLogs = ref([])

// 快捷操作列表
const quickActions = [
  { icon: markRaw(Plus), label: '添加用户', path: '/admin/users', bg: 'rgba(122, 139, 165, 0.12)' },
  { icon: markRaw(Document), label: '考试管理', path: '/admin/exams', bg: 'rgba(139, 168, 136, 0.12)' },
  { icon: markRaw(Bell), label: '发送通知', path: '/admin/notifications', bg: 'rgba(184, 154, 138, 0.12)' },
  { icon: markRaw(Setting), label: '系统设置', path: '/admin/system', bg: 'rgba(203, 181, 138, 0.12)' },
  { icon: markRaw(DataLine), label: '数据分析', path: '/admin/analytics', bg: 'rgba(196, 128, 128, 0.12)' },
  { icon: markRaw(Monitor), label: '题库管理', path: '/admin/questions', bg: 'rgba(122, 139, 165, 0.08)' }
]

/**
 * 获取仪表盘统计数据
 */
async function fetchDashboardStats() {
  try {
    const res = await analyticsApi.getDashboardStats()
    const data = res.data || res

    // 更新统计卡片数值
    statCards.value[0].value = formatNumber(data.userCount ?? data.totalUsers ?? 0)
    stats.userCount = data.userCount ?? data.totalUsers ?? 0

    statCards.value[1].value = formatNumber(data.courseCount ?? data.totalCourses ?? 0)
    stats.courseCount = data.courseCount ?? data.totalCourses ?? 0

    statCards.value[2].value = formatNumber(data.examCount ?? data.totalExams ?? 0)
    stats.examCount = data.examCount ?? data.totalExams ?? 0

    statCards.value[3].value = formatNumber(data.submissionCount ?? 0)
    stats.submissionCount = data.submissionCount ?? 0

    // 更新课程分布饼图
    if (data.categoryDistribution && Object.keys(data.categoryDistribution).length > 0) {
      pieOption.value.series[0].data = Object.entries(data.categoryDistribution).map(([name, value]) => ({
        name,
        value: Number(value) || 0
      }))
    } else {
      pieOption.value.series[0].data = []
    }
  } catch (error) {
    console.warn('加载仪表盘数据失败，使用默认值:', error)
    setMockData()
  }
}

/**
 * 获取趋势数据（增强：优先使用登录日志趋势 API）
 */
async function fetchTrendData() {
  try {
    const days = parseInt(trendRange.value) || 7
    // 优先从登录日志 API 获取用户增长趋势
    const res = await loginLogApi.getTrends({ days })
    const data = res.data || res

    if (Array.isArray(data)) {
      trendOption.value.xAxis.data = data.map(item => item.date || item.label || item.day)
      trendOption.value.series[0].data = data.map(item => item.count || item.userCount || item.value || 0)
    } else if (data.dates && data.values) {
      trendOption.value.xAxis.data = data.dates
      trendOption.value.series[0].data = data.values
    } else if (data.list && Array.isArray(data.list)) {
      trendOption.value.xAxis.data = data.list.map(item => item.date || item.label)
      trendOption.value.series[0].data = data.list.map(item => item.count || item.value || 0)
    }
  } catch (error) {
    // 登录日志 API 不可用时，回退到 analytics API
    try {
      const res = await analyticsApi.getTrendData({ days: parseInt(trendRange.value) || 7 })
      const data = res.data || res
      if (Array.isArray(data)) {
        trendOption.value.xAxis.data = data.map(item => item.date || item.label)
        trendOption.value.series[0].data = data.map(item => item.count || item.value || 0)
      }
    } catch (e2) {
      console.warn('加载趋势数据失败（所有源均不可用）:', e2)
      trendOption.value.xAxis.data = []
      trendOption.value.series[0].data = []
    }
  }
}

/**
 * 获取系统日志
 */
async function fetchSystemLogs() {
  try {
    const res = await systemApi.getSystemLogs({ limit: 8 })
    const data = res.data?.list || res.data || res || []
    recentLogs.value = Array.isArray(data) ? data.slice(0, 8) : []
  } catch (error) {
    console.warn('加载系统日志失败:', error)
    recentLogs.value = []
  }
}

/**
 * 刷新日志
 */
function refreshLogs() {
  fetchSystemLogs()
}

/**
 * 格式化数字显示
 */
function formatNumber(num) {
  if (num >= 10000) return (num / 10000).toFixed(1) + 'w'
  if (num >= 1000) return (num / 1000).toFixed(1) + 'k'
  return num.toString()
}

/**
 * 格式化时间
 */
function formatTime(time) {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now - date

  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'

  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' }) +
    ' ' + date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

/**
 * 根据日志级别获取标签类型
 */
function getLogType(level) {
  const levelMap = {
    ERROR: 'danger',
    WARN: 'warning',
    INFO: '',
    DEBUG: 'info'
  }
  return levelMap[level] || 'info'
}

/**
 * 设置空数据（API失败时的后备，不再使用硬编码模拟数据）
 */
function setMockData() {
  statCards.value[0].value = '0'
  statCards.value[1].value = '0'
  statCards.value[2].value = '0'
  statCards.value[3].value = '0'

  pieOption.value.series[0].data = []
}

// 页面加载时获取数据
onMounted(() => {
  fetchDashboardStats()
  fetchTrendData()
  fetchSystemLogs()
})
</script>

<style scoped>
.admin-dashboard {
  max-width: 1400px;
  margin: 0 auto;
}

/* 统计卡片区域 */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-lg);
}

.stat-card {
  padding: 22px 24px;
  display: flex;
  align-items: center;
  gap: 16px;
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  transition: all var(--ac-transition-normal);
  position: relative;
  overflow: hidden;
}

.stat-card::before {
  content: '';
  position: absolute;
  left: 0;
  top: 0;
  width: 4px;
  height: 100%;
  opacity: 0;
  transition: opacity var(--ac-transition-normal);
}

.stat-card:hover {
  transform: translateY(-3px);
  box-shadow: var(--ac-shadow-lg);
  border-color: transparent;
}

.stat-card:hover::before {
  opacity: 1;
}

.stat-icon {
  width: 52px;
  height: 52px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--ac-radius-md);
  flex-shrink: 0;
  color: #fff;
}

.stat-info h3 {
  font-size: 26px;
  font-weight: 700;
  color: var(--ac-text-primary);
  line-height: 1.2;
}

.stat-info p {
  font-size: 13px;
  color: var(--ac-text-muted);
  margin-top: 4px;
}

.stat-trend {
  margin-left: auto;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 8px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  gap: 2px;
}

.stat-trend.up {
  color: var(--ac-success);
  background: rgba(139, 168, 136, 0.12);
}

.stat-trend.down {
  color: var(--ac-danger);
  background: rgba(196, 128, 128, 0.12);
}

/* 主内容网格布局 */
.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--ac-spacing-md);
}

.chart-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  transition: all var(--ac-transition-normal);
}

.chart-card:hover {
  box-shadow: var(--ac-shadow-md);
}

.chart-card.full-width {
  grid-column: 1 / -1;
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

.time-switcher {
  display: flex;
  gap: 8px;
}

/* 日志列表 */
.log-list {
  max-height: 300px;
  overflow-y: auto;
}

.log-item {
  padding: 10px 0;
  border-bottom: 1px solid var(--ac-border);
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  transition: background var(--ac-transition-fast);
}

.log-item:hover {
  background: var(--ac-bg);
  margin: 0 calc(-1 * var(--ac-spacing-lg));
  padding-left: var(--ac-spacing-lg);
  padding-right: var(--ac-spacing-lg);
}

.log-item:last-child {
  border-bottom: none;
}

.log-tag {
  flex-shrink: 0;
  font-size: 11px;
}

.log-message {
  flex: 1;
  color: var(--ac-text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.log-time {
  color: var(--ac-text-muted);
  font-size: 12px;
  flex-shrink: 0;
}

/* 快捷操作面板 */
.quick-actions {
  grid-column: 1 / -1;
}

.card-base {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-md);
}

.action-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(120px, 1fr));
  gap: var(--ac-spacing-sm);
}

.action-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 18px 12px;
  border-radius: var(--ac-radius-md);
  background: var(--ac-bg);
  border: 1px solid var(--ac-border);
  transition: all var(--ac-transition-normal);
  text-decoration: none;
  color: var(--ac-text-secondary);
  font-size: 13px;
  font-weight: 500;
}

.action-item:hover {
  transform: translateY(-2px);
  box-shadow: var(--ac-shadow-md);
  border-color: var(--ac-primary-light);
  color: var(--ac-primary);
}

.action-icon {
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: var(--ac-radius-sm);
  color: var(--ac-primary);
}

/* 响应式适配 */
@media (max-width: 1024px) {
  .dashboard-grid {
    grid-template-columns: 1fr;
  }

  .chart-card.full-width,
  .quick-actions {
    grid-column: 1;
  }
}

@media (max-width: 768px) {
  .stat-cards {
    grid-template-columns: repeat(2, 1fr);
  }

  .action-grid {
    grid-template-columns: repeat(3, 1fr);
  }
}
</style>
