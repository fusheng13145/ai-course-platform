<template>
  <div v-loading="loading" class="fade-in">
    <PageHeader title="学习日历" subtitle="记录每日学习轨迹，热力图呈现学习强度" icon="calendar" icon-bg="accent" />

    <div class="stat-grid animate-in d1">
      <StatCard label="本月学习天数" :value="stats[0].value" icon="calendar" tone="var(--accent-soft)" tone-color="var(--accent)" />
      <StatCard label="总学习时长" :value="stats[1].value" icon="clock" tone="var(--info-soft)" tone-color="var(--info)" />
      <StatCard label="完成课程" :value="stats[2].value" icon="check" tone="var(--ok-soft)" tone-color="var(--ok)" />
      <StatCard label="最高连续" :value="stats[3].value" icon="target" tone="var(--warn-soft)" tone-color="var(--warn)" />
    </div>

    <section class="l1-card animate-in d2">
      <div class="l1-head">
        <span class="l1-ico">
          <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M8 2v4M16 2v4M3 8h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z"/></svg>
        </span>
        <div><div class="l1-title">学习日历</div><div class="l1-sub">点击日期查看当日学习详情</div></div>
      </div>
      <div class="l1-body">
    <!-- 今日学习概要 -->
    <div class="content-card today-summary">
      <h4 class="section-subtitle">📅 今日学习概要</h4>
      <div v-if="todaySummary.studyMinutes > 0 || todaySummary.completedCourses > 0" class="today-content">
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
      <div v-else class="empty-state">
        <p>🌱 今天还没有学习记录，开始今天的学习吧！</p>
      </div>
    </div>

    <!-- 学习日历 -->
    <div class="content-card calendar-card">
      <div class="calendar-header">
        <h3 class="section-title">{{ currentMonth }} 学习日历</h3>
        <div class="month-nav">
          <el-button :icon="ArrowLeft" circle size="small" @click="prevMonth" />
          <el-button :icon="ArrowRight" circle size="small" @click="nextMonth" />
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
      <div v-if="!loading && studyDaysMap.size === 0" class="empty-state calendar-empty">
        <p>📭 本月暂无学习记录</p>
        <p class="empty-hint">坚持每天学习，日历会越来越丰富哦～</p>
      </div>

      <!-- 热力图图例 -->
      <div v-if="studyDaysMap.size > 0" class="heatmap-legend">
        <span class="legend-label">学习强度：</span>
        <div class="legend-colors">
          <span class="legend-item"><span class="color-box level-1"></span>少</span>
          <span class="legend-item"><span class="color-box level-2"></span>中</span>
          <span class="legend-item"><span class="color-box level-3"></span>多</span>
          <span class="legend-item"><span class="color-box level-4"></span>高</span>
        </div>
      </div>
    </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import dayjs from 'dayjs'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import analyticsApi from '@/api/analytics'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'

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

// 热力图色阶：accent 令牌浅→深（accent-softer → accent-soft → accent-line → accent）
function getHeatmapStyle(dayStr) {
  const info = getDayStudyInfo(dayStr)
  if (!info.hasStudy) return {}

  let bgColor = 'var(--accent-softer)' // 默认最浅
  if (info.minutes >= 120) {
    bgColor = 'var(--accent)' // 高强度
  } else if (info.minutes >= 60) {
    bgColor = 'var(--accent-line)' // 中高强度
  } else if (info.minutes >= 30) {
    bgColor = 'var(--accent-soft)' // 中等强度
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
    const res = await analyticsApi.getTrendData(userId)
    const data = res?.data || res || {}

    // 处理学习天数数据 - 兼容多种格式
    const rawStudyDays = data.studyDays || data.days || []
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
  margin-bottom: var(--ac-spacing-md);
  background: linear-gradient(135deg, var(--accent-softer) 0%, var(--accent-soft) 100%);
  border-left: 4px solid var(--accent);
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
  background: var(--line-2);
}

/* 日历卡片 */
.calendar-card {
  margin-bottom: var(--ac-spacing-md);
}

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
  color: var(--ink-3);
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
  border-top: 1px solid var(--line);
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

.color-box.level-1 { background: var(--accent-softer); border: 1px solid var(--line); }
.color-box.level-2 { background: var(--accent-soft); }
.color-box.level-3 { background: var(--accent-line); }
.color-box.level-4 { background: var(--accent); }
</style>
