<template>
  <div class="l1-body">
    <!-- 今日学习概要 -->
    <div class="content-card today-summary">
      <h4 class="section-subtitle">📅 今日学习概要</h4>
      <div
        v-if="todaySummary.studyMinutes > 0 || todaySummary.completedCourses > 0"
        class="today-content"
      >
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
          <el-button :icon="ArrowLeft" circle size="small" @click="goPrevMonth" />
          <el-button :icon="ArrowRight" circle size="small" @click="goNextMonth" />
        </div>
      </div>

      <el-calendar v-model="innerDate">
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
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import { ref, computed, watch } from 'vue'
import dayjs from 'dayjs'
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue'

/**
 * 学习日历页 — 日历面板（今日概要 + el-calendar 热力图 + 图例）。
 * <p>拆分子组件：消费 studyDaysMap/todaySummary，内部承载日期渲染与热力图色阶；
 * 月份切换通过 emit 通知父组件重载月度数据。</p>
 */
const props = defineProps({
  /** 当前选中日期（父组件持有，用于驱动月度数据加载） */
  currentDate: { type: Date, default: () => new Date() },
  /** 每日学习数据 Map（key: 'YYYY-MM-DD'，value: { minutes, durationText }） */
  studyDaysMap: {
    type: Map as PropType<Map<string, { minutes?: number; durationText?: string }>>,
    default: () => new Map(),
  },
  /** 今日概要（studyMinutes/completedCourses/progressPercent） */
  todaySummary: { type: Object, default: () => ({}) },
  /** 加载态（控制空状态提示显示） */
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['prev-month', 'next-month'])

const currentMonth = computed(() => dayjs(props.currentDate).format('YYYY年MM月'))

// el-calendar 需要受控 v-model；点击日期仅影响本地高亮（与原行为一致），
// 月份切换由父组件 currentDate 同步回写
const innerDate = ref(props.currentDate)
watch(
  () => props.currentDate,
  (v) => {
    innerDate.value = v
  },
)

function goPrevMonth() {
  emit('prev-month')
}

function goNextMonth() {
  emit('next-month')
}

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
  const info = props.studyDaysMap.get(dayStr) || {}
  return {
    hasStudy: !!info.minutes && info.minutes > 0,
    durationText: info.durationText || '',
    minutes: info.minutes || 0,
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
    borderRadius: '4px',
  }
}
</script>

<style scoped>
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

.color-box.level-1 {
  background: var(--accent-softer);
  border: 1px solid var(--line);
}
.color-box.level-2 {
  background: var(--accent-soft);
}
.color-box.level-3 {
  background: var(--accent-line);
}
.color-box.level-4 {
  background: var(--accent);
}
</style>
