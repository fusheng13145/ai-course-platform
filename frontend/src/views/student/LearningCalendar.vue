<template>
  <div>
    <div class="content-card" style="margin-bottom:var(--ac-spacing-md)">
      <div class="calendar-header">
        <h3 class="section-title">{{ currentMonth }} 学习日历</h3>
        <div class="month-nav">
          <el-button @click="prevMonth">〈</el-button>
          <el-button @click="nextMonth">〉</el-button>
        </div>
      </div>
      <el-calendar v-model="currentDate">
        <template #date-cell="{ data }">
          <div :class="{ 'has-study': studyDays.includes(data.day) }" class="calendar-cell">
            {{ data.day.split('-')[2] }}
            <div v-if="studyDays.includes(data.day)" class="study-dot">●</div>
          </div>
        </template>
      </el-calendar>
    </div>
    <div class="content-card">
      <h4 class="section-subtitle">学习统计</h4>
      <el-row :gutter="16">
        <el-col :span="6" v-for="s in stats" :key="s.label">
          <div class="stat-box">
            <div class="stat-value">{{ s.value }}</div>
            <div class="stat-label">{{ s.label }}</div>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import dayjs from 'dayjs'
import { useAuthStore } from '@/stores/auth'
import analyticsApi from '@/api/analytics'

const auth = useAuthStore()
const currentDate = ref(new Date())
const currentMonth = computed(() => dayjs(currentDate.value).format('YYYY年MM月'))
const studyDays = ref([])
const stats = ref([
  { label: "本月学习天数", value: 0 },
  { label: "总学习时长", value: "0h" },
  { label: "完成课程", value: 0 },
  { label: "最高连续", value: 0 }
])

onMounted(async () => {
  try {
    const userId = auth.userId
    if (userId) {
      const res = await analyticsApi.getTrendData(userId)
      const data = res.data || res || {}
      if (data.studyDays) studyDays.value = data.studyDays
      stats.value = [
        { label: "本月学习天数", value: data.monthlyDays || 0 },
        { label: "总学习时长", value: (data.totalHours || 0) + 'h' },
        { label: "完成课程", value: data.completedCourses || 0 },
        { label: "最高连续", value: data.maxStreak || 0 }
      ]
    }
  } catch (e) {
    console.error('Failed to load calendar data:', e)
  }
})

function prevMonth() { currentDate.value = dayjs(currentDate.value).subtract(1, 'month').toDate() }
function nextMonth() { currentDate.value = dayjs(currentDate.value).add(1, 'month').toDate() }
</script>

<style scoped>
.calendar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.section-title {
  margin: 0;
  font-size: 16px;
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

.calendar-cell {
  padding: 4px;
}

.has-study {
  font-weight: 600;
}

.study-dot {
  font-size: 10px;
  color: var(--ac-primary);
  line-height: 1;
}

.stat-box {
  text-align: center;
  padding: 16px;
  background: var(--ac-primary-bg);
  border-radius: var(--ac-radius-md);
}

.stat-value {
  font-size: 24px;
  font-weight: 600;
  color: var(--ac-primary);
}

.stat-label {
  font-size: 13px;
  color: var(--ac-text-secondary);
  margin-top: 4px;
}
</style>