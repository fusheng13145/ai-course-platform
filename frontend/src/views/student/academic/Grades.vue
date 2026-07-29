﻿<template>
  <div class="fade-in">
    <!-- 顶部统计卡片 -->
    <div class="stat-cards">
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--ac-primary-bg); color: var(--ac-primary)">
          <el-icon :size="24"><TrophyBase /></el-icon>
        </div>
        <div class="stat-info"><h3>{{ avgScore }}</h3><p>平均分</p></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: rgba(139,168,136,0.15); color: var(--ac-success)">
          <el-icon :size="24"><TrendCharts /></el-icon>
        </div>
        <div class="stat-info"><h3>{{ rankDisplay }}</h3><p>班级排名</p></div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: rgba(203,181,138,0.18); color: var(--ac-warning)">
          <el-icon :size="24"><Document /></el-icon>
        </div>
        <div class="stat-info"><h3>{{ list.length }}</h3><p>已完成考试</p></div>
      </div>
    </div>

    <!-- 图表区域：雷达图 + 趋势图 -->
    <div class="charts-row">
      <!-- 成绩分布雷达图 -->
      <div class="chart-card">
        <div class="chart-header">
          <span class="chart-title">成绩维度分析</span>
        </div>
        <v-chart v-if="!loading && groupedByCourse && Object.keys(groupedByCourse).length >= 3" :option="radarOption" style="height: 320px" autoresize />
        <el-empty v-else-if="!loading && (!groupedByCourse || Object.keys(groupedByCourse).length < 3)" description="选课不足（至少3门），暂无法生成多维对比分析" :image-size="80" />
        <div v-else style="height: 320px; display: flex; align-items: center; justify-content: center;">
          <span style="color: var(--ac-text-muted)">加载中...</span>
        </div>
      </div>

      <!-- 成绩趋势折线图 -->
      <div class="chart-card">
        <div class="chart-header">
          <span class="chart-title">历次考试成绩趋势</span>
        </div>
        <v-chart v-if="!loading && list.length > 0" :option="trendOption" style="height: 320px" autoresize />
        <el-empty v-else-if="!loading && list.length === 0" description="暂无考试记录" :image-size="80" />
      </div>
    </div>

    <!-- 各课程详细成绩卡片 -->
    <div class="grades-section">
      <h3 class="section-title">课程成绩详情</h3>
      <div class="course-grade-grid">
        <div
          v-for="(group, courseName) in groupedByCourse"
          :key="courseName"
          class="grade-course-card"
        >
          <div class="card-header">
            <h4 class="course-name">{{ courseName }}</h4>
            <el-tag :type="getGradeLevelType(group.avgScore)" size="small">
              {{ getGradeLevelLabel(group.avgScore) }}
            </el-tag>
          </div>

          <!-- 该课程的平均分 -->
          <div class="score-highlight">
            <span class="score-big">{{ group.avgScore }}</span>
            <span class="score-max">/ {{ group.maxTotal || 100 }}</span>
          </div>

          <!-- 排名信息 -->
          <div class="rank-info" v-if="group.rank">
            <span class="rank-label">排名：</span>
            <span class="rank-value">{{ group.rank }} / {{ group.totalStudents || '--' }}</span>
          </div>

          <!-- 成绩列表 -->
          <div class="exam-list-mini">
            <div v-for="exam in group.exams" :key="exam.id" class="exam-mini-item">
              <span class="mini-name">{{ exam.examTitle || '考试' }}</span>
              <el-tag
                :type="getGradeLevelTag(exam.score)"
                size="small"
              >
                {{ exam.score }} 分
              </el-tag>
              <span class="mini-time">{{ formatTime(exam.submitTime) }}</span>
            </div>
          </div>
        </div>
      </div>

      <el-empty v-if="!loading && Object.keys(groupedByCourse).length === 0" description="暂无成绩记录" :image-size="100" />

      <!-- 完整表格（折叠） -->
      <div class="table-section" v-if="list.length > 0">
        <el-button text @click="showTable = !showTable" style="margin-bottom: var(--ac-spacing-sm)">
          {{ showTable ? '收起' : '展开' }}完整成绩表
          <el-icon><ArrowDown :class="{ rotated: showTable }" /></el-icon>
        </el-button>
        <transition name="expand-fade">
          <el-table
            v-if="showTable"
            :data="list"
            stripe
            style="width: 100%"
            v-loading="loading"
            row-class-name="grade-table-row"
          >
            <el-table-column prop="examTitle" label="考试名称" min-width="160" />
            <el-table-column prop="courseName" label="课程" width="140" />
            <el-table-column prop="score" label="成绩" width="90" align="center">
              <template #default="{ row }">
                <el-tag :type="getGradeLevelTag(row.score)" size="small">{{ row.score }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="totalScore" label="总分" width="70" align="center" />
            <el-table-column prop="submitTime" label="提交时间" width="160">
              <template #default="{ row }">{{ formatTime(row.submitTime) }}</template>
            </el-table-column>
            <el-table-column label="等级" width="80" align="center">
              <template #default="{ row }">
                <span class="level-text" :class="'level-' + getGradeClass(row.score)">
                  {{ getGradeLevelLabel(row.score) }}
                </span>
              </template>
            </el-table-column>
          </el-table>
        </transition>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { TrophyBase, TrendCharts, Document, ArrowDown } from '@element-plus/icons-vue'
import examApi from '@/api/exam'
import courseApi from '@/api/course'

/* ---- 数据状态 ---- */
const loading = ref(false)
const list = ref([])
const showTable = ref(false)
const courseMap = ref({})
const examMap = ref({})

/* ---- 计算属性 ---- */
const avgScore = computed(() => {
  if (list.value.length === 0) return 0
  const sum = list.value.reduce((s, r) => s + (Number(r.score) || 0), 0)
  return Math.round(sum / list.value.length)
})

const rankDisplay = computed(() => {
  if (list.value.length === 0) return '--'
  return '--'
})

/** 按课程分组 */
const groupedByCourse = computed(() => {
  const groups = {}
  list.value.forEach(item => {
    const name = item.courseName || '未知课程'
    if (!groups[name]) {
      groups[name] = { exams: [], scores: [], maxTotal: item.totalScore }
    }
    groups[name].exams.push(item)
    groups[name].scores.push(Number(item.score) || 0)
    if (item.totalScore && (!groups[name].maxTotal || item.totalScore > groups[name].maxTotal)) {
      groups[name].maxTotal = item.totalScore
    }
  })

  // 添加计算字段
  Object.keys(groups).forEach(name => {
    const g = groups[name]
    g.avgScore = Math.round(g.scores.reduce((a, b) => a + b, 0) / g.scores.length)
    g.rank = null // 排名需要后端支持
    g.totalStudents = null
  })
  return groups
})

/* ---- 雷达图配置 ---- */
const morandiColors = ['#7A8BA5', '#B89A8A', '#8BA888', '#CBB58A', '#9B8AA5']

const radarOption = computed(() => {
  // 从各课程平均分构建维度数据
  const dimensions = []
  const values = []

  const sortedCourses = Object.entries(groupedByCourse.value)
    .sort((a, b) => b[1].avgScore - a[1].avgScore)
    .slice(0, 6)

  sortedCourses.forEach(([name, g], idx) => {
    dimensions.push({ name: name.length > 6 ? name.substring(0, 6) + '..' : name, max: 100 })
    values.push(g.avgScore)
  })

  return {
    tooltip: {
      trigger: 'item',
      backgroundColor: '#FFFCF8',
      borderColor: '#DDD6CE',
      textStyle: { color: '#3D3835', fontSize: 13 }
    },
    legend: {
      data: ['我的成绩'],
      bottom: 0,
      textStyle: { fontSize: 12, color: '#928B83' }
    },
    radar: {
      indicator: dimensions,
      shape: 'polygon',
      splitNumber: 4,
      axisName: {
        color: '#5E574F',
        fontSize: 11
      },
      splitLine: {
        lineStyle: { color: '#DDD6CE' }
      },
      splitArea: {
        areaStyle: {
          color: ['rgba(122,139,165,0.04)', 'rgba(122,139,165,0.08)', 'rgba(122,139,165,0.04)', 'rgba(122,139,165,0.02)']
        }
      },
      axisLine: {
        lineStyle: { color: '#DDD6CE' }
      }
    },
    series: [{
      type: 'radar',
      data: [{
        value: values,
        name: '我的成绩',
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: {
          width: 2,
          color: morandiColors[0]
        },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0, y: 0, x2: 0, y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(122,139,165,0.35)' },
              { offset: 1, color: 'rgba(122,139,165,0.05)' }
            ]
          }
        },
        itemStyle: { color: morandiColors[0] }
      }]
    }]
  }
})

/* ---- 趋势图配置 ---- */
const trendOption = computed(() => ({
  tooltip: {
    trigger: 'axis',
    backgroundColor: '#FFFCF8',
    borderColor: '#DDD6CE',
    textStyle: { color: '#3D3835' }
  },
  grid: { left: 48, right: 20, bottom: 36, top: 20 },
  xAxis: {
    type: 'category',
    data: list.value.map((_, i) => `第${i + 1}次`),
    axisLine: { lineStyle: { color: '#DDD6CE' } },
    axisLabel: { color: '#928B83', fontSize: 11 }
  },
  yAxis: {
    type: 'value',
    min: 0,
    max: 100,
    axisLine: { show: false },
    splitLine: { lineStyle: { color: '#DDD6CE', type: 'dashed' } },
    axisLabel: { color: '#928B83', fontSize: 11 }
  },
  series: [{
    name: '成绩',
    type: 'line',
    data: list.value.map(r => Number(r.score) || 0),
    smooth: true,
    symbol: 'circle',
    symbolSize: 8,
    lineStyle: { width: 2.5, color: morandiColors[0] },
    itemStyle: { color: morandiColors[0] },
    areaStyle: {
      color: {
        type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
        colorStops: [{ offset: 0, color: 'rgba(122,139,165,0.25)' }, { offset: 1, color: 'rgba(122,139,165,0.02)' }]
      }
    },
    markLine: {
      silent: true,
      lineStyle: { color: '#C48080', type: 'dashed', width: 1 },
      data: [{ yAxis: 60, label: { formatter: '及格线', color: '#928B83', fontSize: 11 } }]
    }
  }]
}))

/* ---- 辅助方法 ---- */
function getGradeLevelTag(score) {
  if (score >= 90) return 'success'
  if (score >= 75) return '' // primary
  if (score >= 60) return 'warning'
  return 'danger'
}

function getGradeLevelType(score) {
  if (score >= 90) return 'success'
  if (score >= 75) return ''
  if (score >= 60) return 'warning'
  return 'danger'
}

function getGradeLevelLabel(score) {
  if (score >= 90) return '优秀'
  if (score >= 75) return '良好'
  if (score >= 60) return '及格'
  return '不及格'
}

function getGradeClass(score) {
  if (score >= 90) return 'excellent'
  if (score >= 75) return 'good'
  if (score >= 60) return 'pass'
  return 'fail'
}

function formatTime(timeStr) {
  if (!timeStr) return '--'
  return timeStr.replace('T', ' ').substring(0, 16)
}

function viewDetail(row) {
  ElMessage.info('成绩详情：' + (row.examTitle || ''))
}

/* ---- 加载数据 ---- */
onMounted(async () => {
  loading.value = true
  try {
    const [courseRes, examRes, sessionRes] = await Promise.all([
      courseApi.getCourseList(),
      examApi.getExamPaperList(),
      examApi.getMyExamSessions()
    ])

    const courses = courseRes.data?.records || courseRes.data?.list || courseRes.data || courseRes || []
    courses.forEach(c => { courseMap.value[c.id] = c.name })

    const exams = examRes.data?.list || examRes.data || examRes || []
    exams.forEach(e => {
      examMap.value[e.id] = { title: e.title, courseId: e.courseId, totalScore: e.totalScore || 100 }
    })

    const sessions = sessionRes.data || sessionRes || []
    list.value = (Array.isArray(sessions) ? sessions : [])
      .filter(s => s.status === 'SUBMITTED' || s.status === 'GRADED')
      .map(s => {
        const exam = examMap.value[s.examPaperId] || {}
        return {
          id: s.id,
          examTitle: exam.title || '考试',
          courseName: courseMap.value[exam.courseId] || '未知课程',
          score: s.totalScore || 0,
          totalScore: exam.totalScore || 100,
          submitTime: s.submitTime || s.createdAt || ''
        }
      })
      .sort((a, b) => new Date(b.submitTime) - new Date(a.submitTime))
  } catch (e) {
    console.error('加载成绩失败:', e)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.charts-row {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: var(--ac-spacing-md);
  margin-bottom: var(--ac-spacing-lg);
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

.chart-header {
  margin-bottom: var(--ac-spacing-md);
}

.chart-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
}

/* ---- 课程成绩卡片 ---- */
.grades-section {
  margin-top: var(--ac-spacing-lg);
}

.section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0 0 var(--ac-spacing-md);
  padding-left: 12px;
  border-left: 3px solid var(--ac-primary);
}

.course-grade-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: var(--ac-spacing-md);
}

.grade-course-card {
  background: var(--ac-bg-elevated);
  border-radius: var(--ac-radius-lg);
  border: 1px solid var(--ac-border);
  padding: var(--ac-spacing-lg);
  transition: all var(--ac-transition-normal);
}

.grade-course-card:hover {
  transform: translateY(-2px);
  box-shadow: var(--ac-shadow-md);
  border-left: 4px solid var(--ac-primary);
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--ac-spacing-sm);
}

.course-name {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0;
}

.score-highlight {
  display: flex;
  align-items: baseline;
  gap: 4px;
  margin-bottom: var(--ac-spacing-xs);
}

.score-big {
  font-size: 32px;
  font-weight: 700;
  color: var(--ac-primary);
  line-height: 1;
}

.score-max {
  font-size: 16px;
  color: var(--ac-text-muted);
  font-weight: 500;
}

.rank-info {
  font-size: 13px;
  color: var(--ac-text-secondary);
  margin-bottom: var(--ac-spacing-md);
}

.rank-label {
  color: var(--ac-text-muted);
}

.rank-value {
  font-weight: 600;
  color: var(--ac-text-primary);
}

.exam-list-mini {
  border-top: 1px solid var(--ac-border);
  padding-top: var(--ac-spacing-sm);
}

.exam-mini-item {
  display: flex;
  align-items: center;
  gap: var(--ac-spacing-sm);
  padding: 6px 0;
  font-size: 13px;
}

.mini-name {
  flex: 1;
  color: var(--ac-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mini-time {
  font-size: 11px;
  color: var(--ac-text-muted);
  white-space: nowrap;
}

/* ---- 表格区域 ---- */
.table-section {
  margin-top: var(--ac-spacing-lg);
}

.table-section .el-icon {
  transition: transform var(--ac-transition-fast);
}

.table-section .el-icon.rotated {
  transform: rotate(180deg);
}

.level-text {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 999px;
}

.level-excellent { background: rgba(139,168,136,0.15); color: #5E7A5B; }
.level-good { background: var(--ac-primary-bg); color: var(--ac-primary-dark); }
.level-pass { background: rgba(203,181,138,0.18); color: #8A7845; }
.level-fail { background: rgba(196,128,128,0.15); color: #A05A5A; }

.expand-fade-enter-active { transition: all 0.25s ease; }
.expand-fade-leave-active { transition: all 0.15s ease; }
.expand-fade-enter-from, .expand-fade-leave-to { opacity: 0; max-height: 0; overflow: hidden; }

@media (max-width: 900px) {
  .charts-row { grid-template-columns: 1fr; }
  .course-grade-grid { grid-template-columns: 1fr; }
}
</style>
