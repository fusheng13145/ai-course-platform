<template>
  <div class="fade-in" v-loading="loading">
    <!-- 今日学习概要 -->
    <div class="content-card today-summary" style="margin-bottom:var(--ac-spacing-md)">
      <h4 class="section-subtitle">📅 今日学习概要</h4>
      <div class="today-content" v-if="todaySummary.studyMinutes > 0 || todaySummary.completedCourses > 0">
        <div class="today-item">
          <span class="today-label">今日学习时长</span>
          <span class="today-value">{{ formatDuration(todaySummary.studyMinutes) }}</span>
        </div>
        <div class="today-divider"></div>
        <div class="today-item">
          <span class="today-label">完成课程数</span>
          <span class="today-value">{{ todaySummary.completedCourses }} 门</span>
        </div>
        <div class="today-divider"></div>
        <div class="today-item">
          <span class="today-label">学习进度</span>
          <span class="today-value">{{ todaySummary.progressPercent }}%</span>
        </div>
      </div>
      <div class="empty-state" v-else>
        <p>🌱 今天还没有学习记录，开始今天的学习吧！</p>
      </div>
    </div>

    <!-- 学习日历 -->
    <div class="content-card calendar-card" style="margin-bottom:var(--ac-spacing-md)">
      <div class="calendar-header">
        <h3 class="section-title">{{ currentMonth }} 学习日历</h3>
        <div class="month-nav">
          <el-button @click="prevMonth" :icon="ArrowLeft" circle size="small" />
          <el-button @click="nextMonth" :icon="ArrowRight" circle size="small" />
        </div>
      </div>

      <el-calendar v-model="currentDate">
        <template #date-cell="{ data }">
          <div
            :class="{ 'has-study': getDayStudyInfo(data.day).hasStudy }"
            :style="getHeatmapStyle(data.day)"
            class="calendar-cell"
          >
            <span class="day-number">{{ data.day.split('-')[2] }}</span>
            <div v-if="getDayStudyInfo(data.day).hasStudy" class="study-indicator">
              <span class="study-duration">{{ getDayStudyInfo(data.day).durationText }}</span>
            </div>
          </div>
        </template>
      </el-calendar>

      <!-- 空状态提示 -->
      <div class="empty-state calendar-empty" v-if="!loading && studyDaysMap.size === 0">
        <p>📭 本月暂无学习记录</p>
        <p class="empty-hint">坚持每天学习，日历会越来越丰富哦～</p>
      </div>

      <!-- 热力图图例 -->
      <div class="heatmap-legend" v-if="studyDaysMap.size > 0">
        <span class="legend-label">学习强度：</span>
        <div class="legend-colors">
          <span class="legend-item"><span class="color-box level-1"></span>少</span>
          <span class="legend-item"><span class="color-box level-2"></span>中</span>
          <span class="legend-item"><span class="color-box level-3"></span>多</span>
          <span class="legend-item"><span class="color-box level-4"></span>高</span>
        </div>
      </div>
    </div>

    <!-- 学习统计卡片 -->
    <div class="content-card stats-section">
      <h4 class="section-subtitle">📊 学习统计</h4>
      <el-row :gutter="16">
        <el-col :xs="12" :sm="12" :md="6" v-for="(stat, index) in stats" :key="stat.label">
          <div class="stat-card" :class="'stat-card-' + (index + 1)">
            <div class="stat-icon">{{ stat.icon }}</div>
            <div class="stat-info">
              <div class="stat-value">{{ stat.value }}</div>
              <div class="stat-label">{{ stat.label }}</div>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import dayjs from 'dayjs'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import analyticsApi from '@/api/analytics'

const auth = useAuthStore()
const loading = ref(false)
const currentDate = ref(new Date())
const currentMonth = computed(() => dayjs(currentDate.value).format('YYYY年MM月'))

// 使用 Map 存储每日学习数据，支持热力图
const studyDaysMap = ref(new Map())

// 今日概要数据
const todaySummary = ref({
  studyMinutes: 0,
  completedCourses: 0,
  progressPercent: 0
})

// 统计数据 - 带图标和容错
const stats = ref([
  { icon: "📅", label: "本月学习天数", value: 0 },
  { icon: "⏱️", label: "总学习时长", value: "0h" },
  { icon: "✅", label: "完成课程", value: 0 },
  { icon: "🔥", label: "最高连续", value: 0 }
])

// 格式化时长
function formatDuration(minutes) {
  const mins = Number(minutes) || 0
  if (mins === 0) return '0分钟'
  if (mins < 60) return `${mins}分钟`
  const hours = Math.floor(mins / 60)
  const remainMins = mins % 60
  return remainMins > 0 ? `${hours}小时${remainMins}分钟` : `${hours}小时`
}

// 获取某天的学习信息
function getDayStudyInfo(dayStr) {
  const info = studyDaysMap.value.get(dayStr) || {}
  return {
    hasStudy: !!info.minutes && info.minutes > 0,
    durationText: info.durationText || '',
    minutes: info.minutes || 0
  }
}

// 根据学习时长获取热力图颜色
function getHeatmapStyle(dayStr) {
  const info = getDayStudyInfo(dayStr)
  if (!info.hasStudy) return {}

  // 莫兰迪色系热力图：浅→深
  let bgColor = '#f0ebe3' // 默认最浅
  if (info.minutes >= 120) {
    bgColor = '#7a8ba5' // 高强度
  } else if (info.minutes >= 60) {
    bgColor = '#b89a8a' // 中高强度
  } else if (info.minutes >= 30) {
    bgColor = '#d4c4b0' // 中等强度
  }

  return {
    backgroundColor: bgColor,
    borderRadius: '4px'
  }
}

// 加载月度数据
async function loadMonthData() {
  const userId = auth.userId
  if (!userId) return

  loading.value = true
  try {
    const now = new Date()
    const res = await analyticsApi.getCalendarData({ year: now.getFullYear(), month: now.getMonth() + 1 })
    const data = res?.data || res || {}

    // 处理学习天数数据 - 兼容多种格式
    const rawStudyDays = data || []
    const newMap = new Map()

    // 支持数组格式：['2024-01-15', ...] 或对象格式：{'2024-01-15': {minutes: 60}, ...}
    if (Array.isArray(rawStudyDays)) {
      rawStudyDays.forEach(day => {
        if (typeof day === 'string') {
          newMap.set(day, { minutes: 30, durationText: '30m' })
        } else if (typeof day === 'object' && day !== null) {
          const date = day.date || day.day || ''
          const minutes = Number(day.minutes || day.duration || 30)
          newMap.set(date, {
            minutes: minutes,
            durationText: minutes >= 60 ? `${Math.floor(minutes/60)}h` : `${minutes}m`
          })
        }
      })
    } else if (typeof rawStudyDays === 'object' && rawStudyDays !== null) {
      Object.entries(rawStudyDays).forEach(([date, info]) => {
        const minutes = typeof info === 'number' ? info : (info?.minutes || 30)
        newMap.set(date, {
          minutes: minutes,
          durationText: minutes >= 60 ? `${Math.floor(minutes/60)}h` : `${minutes}m`
        })
      })
    }

    studyDaysMap.value = newMap

    // 更新统计数据 - 全部带 fallback
    stats.value = [
      {
        icon: "📅",
        label: "本月学习天数",
        value: data.monthlyDays || data.monthDays || newMap.size || 0
      },
      {
        icon: "⏱️",
        label: "总学习时长",
        value: formatDuration(data.totalHours ? data.totalHours * 60 : data.totalMinutes || 0)
      },
      {
        icon: "✅",
        label: "完成课程",
        value: data.completedCourses || data.courses || 0
      },
      {
        icon: "🔥",
        label: "最高连续",
        value: data.maxStreak || data.streak || 0
      }
    ]

    // 更新今日概要
    const todayStr = dayjs().format('YYYY-MM-DD')
    const todayInfo = newMap.get(todayStr) || data.today || {}
    todaySummary.value = {
      studyMinutes: todayInfo.minutes || todayInfo.studyMinutes || 0,
      completedCourses: todayInfo.completedCourses || todayInfo.courses || 0,
      progressPercent: todayInfo.progress || todayInfo.progressPercent || 0
    }
  } catch (e) {
    console.error('Failed to load calendar data:', e)
  } finally {
    loading.value = false
  }
}

// 监听月份变化，重新加载数据
watch(currentDate, () => {
  loadMonthData()
})

onMounted(() => {
  loadMonthData()
})

function prevMonth() {
  currentDate.value = dayjs(currentDate.value).subtract(1, 'month').toDate()
}

function nextMonth() {
  currentDate.value = dayjs(currentDate.value).add(1, 'month').toDate()
}
</script>

<style scoped>
/* 入场动画 */
.fade-in {
  animation: fadeIn 0.5s ease-in;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

/* 今日概要面板 */
.today-summary {
  background: linear-gradient(135deg, #f5f0eb 0%, #e8e0d8 100%);
  border-left: 4px solid var(--ac-primary);
}

.today-content {
  display: flex;
  align-items: center;
  justify-content: space-around;
  padding: 16px 0;
  gap: 16px;
  flex-wrap: wrap;
}

.today-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.today-label {
  font-size: 13px;
  color: var(--ac-text-secondary);
}

.today-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--ac-primary);
}

.today-divider {
  width: 1px;
  height: 40px;
  background: rgba(122, 139, 165, 0.2);
}

/* 日历卡片 */
.calendar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.section-title {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--ac-text-primary);
}

.section-subtitle {
  margin: 0 0 var(--ac-spacing-md);
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
}

.month-nav {
  display: flex;
  gap: 8px;
}

/* 日历单元格 */
.calendar-cell {
  padding: 4px;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  transition: all 0.2s ease;
}

.calendar-cell:hover {
  transform: scale(1.05);
}

.day-number {
  font-size: 14px;
  font-weight: 500;
  line-height: 1.4;
}

.has-study .day-number {
  font-weight: 700;
  color: var(--ac-text-primary);
}

.study-indicator {
  margin-top: 2px;
}

.study-duration {
  font-size: 10px;
  color: #7a8ba5;
  font-weight: 600;
  line-height: 1;
}

/* 空状态 */
.empty-state {
  text-align: center;
  padding: 24px;
  color: var(--ac-text-secondary);
}

.empty-state p {
  margin: 4px 0;
}

.empty-hint {
  font-size: 13px;
  color: var(--ac-text-muted);
}

/* 热力图图例 */
.heatmap-legend {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid rgba(122, 139, 165, 0.1);
}

.legend-label {
  font-size: 13px;
  color: var(--ac-text-secondary);
}

.legend-colors {
  display: flex;
  gap: 12px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--ac-text-muted);
}

.color-box {
  width: 14px;
  height: 14px;
  border-radius: 3px;
  display: inline-block;
}

.color-box.level-1 { background-color: #f0ebe3; border: 1px solid #e0d9d0; }
.color-box.level-2 { background-color: #d4c4b0; }
.color-box.level-3 { background-color: #b89a8a; }
.color-box.level-4 { background-color: #7a8ba5; }

/* 莫兰迪风格统计卡片 */
.stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px;
  border-radius: var(--ac-radius-lg);
  margin-bottom: 16px;
  transition: all 0.3s ease;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
}

.stat-card-1 {
  background: linear-gradient(135deg, #f5f0eb 0%, #ebe4dc 100%);
}

.stat-card-2 {
  background: linear-gradient(135deg, #e8e4ea 0%, #ddd6df 100%);
}

.stat-card-3 {
  background: linear-gradient(135deg, #e4e8ed 0%, #d9dde4 100%);
}

.stat-card-4 {
  background: linear-gradient(135deg, #ede8e4 0%, #e2dbd5 100%);
}

.stat-icon {
  font-size: 28px;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 12px;
  flex-shrink: 0;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--ac-text-primary);
  line-height: 1.2;
}

.stat-label {
  font-size: 13px;
  color: var(--ac-text-secondary);
  margin-top: 4px;
}

.stats-section {
  background: var(--ac-bg-base);
}
</style>
