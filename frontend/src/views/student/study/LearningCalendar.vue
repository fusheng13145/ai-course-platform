<template>
  <div v-loading="loading" class="fade-in">
    <PageHeader
      title="学习日历"
      subtitle="记录每日学习轨迹，热力图呈现学习强度"
      icon="calendar"
      icon-bg="accent"
    />

    <LcStatGrid :stats="stats" />

    <section class="l1-card animate-in d2">
      <div class="l1-head">
        <span class="l1-ico">
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
              d="M8 2v4M16 2v4M3 8h18M5 4h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z"
            />
          </svg>
        </span>
        <div>
          <div class="l1-title">学习日历</div>
          <div class="l1-sub">点击日期查看当日学习详情</div>
        </div>
      </div>

      <LcCalendarPanel
        :current-date="currentDate"
        :study-days-map="studyDaysMap"
        :today-summary="todaySummary"
        :loading="loading"
        @prev-month="prevMonth"
        @next-month="nextMonth"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import dayjs from 'dayjs'
import { useAuthStore } from '@/stores/auth'
import analyticsApi from '@/api/analytics'
import PageHeader from '@/components/PageHeader.vue'
import LcStatGrid from './components/LcStatGrid.vue'
import LcCalendarPanel from './components/LcCalendarPanel.vue'

const auth = useAuthStore()
const loading = ref(false)
const currentDate = ref(new Date())

// 使用 Map 存储每日学习数据，支持热力图
const studyDaysMap = ref(new Map())

// 今日概要数据
const todaySummary = ref({
  studyMinutes: 0,
  completedCourses: 0,
  progressPercent: 0,
})

// 统计数据 - 带图标和容错
const stats = ref([
  { icon: '📅', label: '本月学习天数', value: 0 },
  { icon: '⏱️', label: '总学习时长', value: '0h' },
  { icon: '✅', label: '完成课程', value: 0 },
  { icon: '🔥', label: '最高连续', value: 0 },
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
      rawStudyDays.forEach((day) => {
        if (typeof day === 'string') {
          newMap.set(day, { minutes: 30, durationText: '30m' })
        } else if (typeof day === 'object' && day !== null) {
          const date = day.date || day.day || ''
          const minutes = Number(day.minutes || day.duration || 30)
          newMap.set(date, {
            minutes: minutes,
            durationText: minutes >= 60 ? `${Math.floor(minutes / 60)}h` : `${minutes}m`,
          })
        }
      })
    } else if (typeof rawStudyDays === 'object' && rawStudyDays !== null) {
      Object.entries(rawStudyDays).forEach(([date, info]) => {
        // info 兼容两种形态：纯分钟数（number）或 { minutes } 对象
        const minutes =
          typeof info === 'number' ? info : (info as { minutes?: number } | null)?.minutes || 30
        newMap.set(date, {
          minutes: minutes,
          durationText: minutes >= 60 ? `${Math.floor(minutes / 60)}h` : `${minutes}m`,
        })
      })
    }

    studyDaysMap.value = newMap

    // 更新统计数据 - 全部带 fallback
    stats.value = [
      {
        icon: '📅',
        label: '本月学习天数',
        value: data.monthlyDays || data.monthDays || newMap.size || 0,
      },
      {
        icon: '⏱️',
        label: '总学习时长',
        value: formatDuration(data.totalHours ? data.totalHours * 60 : data.totalMinutes || 0),
      },
      {
        icon: '✅',
        label: '完成课程',
        value: data.completedCourses || data.courses || 0,
      },
      {
        icon: '🔥',
        label: '最高连续',
        value: data.maxStreak || data.streak || 0,
      },
    ]

    // 更新今日概要
    const todayStr = dayjs().format('YYYY-MM-DD')
    const todayInfo = newMap.get(todayStr) || data.today || {}
    todaySummary.value = {
      studyMinutes: todayInfo.minutes || todayInfo.studyMinutes || 0,
      completedCourses: todayInfo.completedCourses || todayInfo.courses || 0,
      progressPercent: todayInfo.progress || todayInfo.progressPercent || 0,
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
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
