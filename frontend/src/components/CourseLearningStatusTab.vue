<template>
  <div class="learning-status-tab">
    <!-- 统计卡 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-icon stat-icon--blue"><User :size="20" /></div>
        <div class="stat-info">
          <span class="stat-val">{{ overview.activeStudents ?? '-' }}</span>
          <span class="stat-lbl">活跃学生</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--green">
          <svg viewBox="0 0 24 24" fill="none" width="20" height="20">
            <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" />
            <path d="M12 6v6l3.5 2" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ overview.avgOnlineHours ?? '-' }}h</span>
          <span class="stat-lbl">人均在线时长</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--orange">
          <svg viewBox="0 0 24 24" fill="none" width="20" height="20">
            <path d="M18 20V10M12 20V4M6 20v-6" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ overview.avgEngagement ?? '-' }}%</span>
          <span class="stat-lbl">平均参与度</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--purple">
          <svg viewBox="0 0 24 24" fill="none" width="20" height="20">
            <path d="M9 12l2 2 4-4" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
            <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" />
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ overview.avgCompletion ?? '-' }}%</span>
          <span class="stat-lbl">平均学习完成率</span>
        </div>
      </div>
    </div>

    <!-- 学习趋势图 -->
    <div class="chart-section">
      <div class="chart-row">
        <div class="chart-card">
          <div class="chart-title">近14天学习活跃度趋势</div>
          <v-chart :option="activityTrendOption" style="height: 260px" autoresize />
        </div>
        <div class="chart-card">
          <div class="chart-title">学生参与度分布</div>
          <v-chart :option="engagementDistOption" style="height: 260px" autoresize />
        </div>
      </div>
    </div>

    <!-- 学生学习详情表 -->
    <div class="table-section">
      <div class="section-header">
        <h3 class="section-title">学生学习详情</h3>
        <el-input
          v-model="searchKeyword"
          placeholder="搜索学生姓名"
          clearable
          style="width: 200px"
          size="small"
        />
      </div>
      <div v-loading="loading" class="content-card">
        <el-table :data="filteredStudents" style="width: 100%">
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="studentName" label="姓名" min-width="110" />
          <el-table-column prop="studentId" label="学号" width="120" />
          <el-table-column label="在线时长" width="110" sortable prop="onlineHours">
            <template #default="{ row }">
              <span class="metric-value">{{ row.onlineHours != null ? row.onlineHours + 'h' : '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="参与度" width="130" sortable prop="engagement">
            <template #default="{ row }">
              <div v-if="row.engagement != null" class="engagement-cell">
                <el-progress
                  :percentage="row.engagement"
                  :stroke-width="6"
                  :show-text="false"
                  style="width: 70px"
                  :color="getEngagementColor(row.engagement)"
                />
                <span class="engagement-text">{{ row.engagement }}%</span>
              </div>
              <span v-else class="metric-value">-</span>
            </template>
          </el-table-column>
          <el-table-column label="学习完成率" width="140" sortable prop="completionRate">
            <template #default="{ row }">
              <div v-if="row.completionRate != null" class="engagement-cell">
                <el-progress
                  :percentage="Math.round(row.completionRate * 100)"
                  :stroke-width="6"
                  :show-text="false"
                  style="width: 70px"
                />
                <span class="engagement-text">{{ Math.round(row.completionRate * 100) }}%</span>
              </div>
              <span v-else class="metric-value">-</span>
            </template>
          </el-table-column>
          <el-table-column label="提交作业数" width="110" align="center" sortable prop="submissionCount">
            <template #default="{ row }">
              <span class="metric-value">{{ row.submissionCount ?? '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="最近活跃" width="140" sortable prop="lastActiveTime">
            <template #default="{ row }">
              <span class="time-text">{{ formatRelative(row.lastActiveTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag
                :type="getStatusTag(row)"
                size="small"
                effect="plain"
              >
                {{ getStatusLabel(row) }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && filteredStudents.length === 0" description="暂无学生数据" />
      </div>
    </div>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { ref, computed, onMounted, watch } from 'vue'
import { User } from '@element-plus/icons-vue'
import teacherApi from '@/api/teacher'
import courseApi from '@/api/course'
import { getChartTheme } from '@/utils/chartTheme'

const props = defineProps({
  courseId: { type: [Number, String], required: true },
})

const loading = ref(false)
const searchKeyword = ref('')

const overview = ref({
  activeStudents: 0,
  avgOnlineHours: 0,
  avgEngagement: 0,
  avgCompletion: 0,
})

const students = ref([])

const activityTrendOption = ref({
  tooltip: { trigger: 'axis' },
  legend: {
    data: ['活跃学生数', '提交作业数'],
    top: 0,
    textStyle: { color: getChartTheme().subText, fontSize: 12 },
  },
  grid: { left: 45, right: 20, bottom: 25, top: 40 },
  xAxis: {
    type: 'category',
    data: [],
    axisLine: { lineStyle: { color: getChartTheme().axis } },
    axisLabel: { color: getChartTheme().subText, fontSize: 11 },
  },
  yAxis: {
    type: 'value',
    axisLine: { show: false },
    splitLine: { lineStyle: { color: getChartTheme().axis, type: 'dashed' } },
    axisLabel: { color: getChartTheme().subText, fontSize: 11 },
  },
  series: [
    {
      name: '活跃学生数',
      type: 'line',
      smooth: true,
      symbol: 'circle',
      symbolSize: 6,
      data: [],
      lineStyle: { width: 2.5, color: '#3b82f6' },
      itemStyle: { color: '#3b82f6' },
      areaStyle: { color: 'rgba(59,130,246,0.08)' },
    },
    {
      name: '提交作业数',
      type: 'bar',
      barWidth: 16,
      data: [],
      itemStyle: { color: '#f59e0b', borderRadius: [4, 4, 0, 0] },
    },
  ],
})

const engagementDistOption = ref({
  tooltip: { trigger: 'item', formatter: '{b}: {c}人 ({d}%)' },
  legend: {
    orient: 'vertical',
    right: 10,
    top: 'center',
    textStyle: { color: getChartTheme().subText, fontSize: 12 },
  },
  series: [
    {
      type: 'pie',
      radius: ['55%', '78%'],
      center: ['38%', '50%'],
      avoidLabelOverlap: false,
      itemStyle: { borderRadius: 6, borderColor: getChartTheme().panel || '#fff', borderWidth: 3 },
      label: { show: false },
      emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
      data: [],
    },
  ],
})

const filteredStudents = computed(() => {
  if (!searchKeyword.value) return students.value
  const kw = searchKeyword.value.toLowerCase()
  return students.value.filter(
    (s) =>
      (s.studentName && s.studentName.toLowerCase().includes(kw)) ||
      (s.studentId && s.studentId.toLowerCase().includes(kw)),
  )
})

function getEngagementColor(val) {
  if (val >= 80) return '#10b981'
  if (val >= 50) return '#f59e0b'
  return '#ef4444'
}

function getStatusTag(row) {
  const eng = row.engagement
  if (eng == null) return 'info'
  if (eng >= 70) return 'success'
  if (eng >= 40) return 'warning'
  return 'danger'
}

function getStatusLabel(row) {
  const eng = row.engagement
  if (eng == null) return '-'
  if (eng >= 70) return '活跃'
  if (eng >= 40) return '一般'
  return '预警'
}

function formatRelative(dateStr) {
  if (!dateStr) return '-'
  const d = new Date(dateStr)
  const now = new Date()
  const diff = now - d
  const minutes = Math.floor(diff / 60000)
  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes} 分钟前`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours} 小时前`
  const days = Math.floor(hours / 24)
  if (days < 7) return `${days} 天前`
  return d.toLocaleDateString('zh-CN')
}

async function loadData() {
  loading.value = true
  try {
    const cid = Number(props.courseId)

    const [classRes, studentsRes] = await Promise.allSettled([
      teacherApi.getClassAnalytics(cid),
      courseApi.getStudents(cid),
    ])

    // 班级级分析数据
    if (classRes.status === 'fulfilled') {
      const d = classRes.value?.data || classRes.value || {}
      overview.value = {
        activeStudents: d.activeStudents ?? d.activeStudentCount ?? 0,
        avgOnlineHours: d.avgOnlineHours != null ? Math.round(d.avgOnlineHours * 10) / 10 : 0,
        avgEngagement: d.avgEngagement != null ? Math.round(d.avgEngagement * 100) : 0,
        avgCompletion: d.avgCompletion != null ? Math.round(d.avgCompletion * 100) : 0,
      }

      // 趋势数据
      if (d.trendDays?.length) {
        activityTrendOption.value.xAxis.data = d.trendDays
        activityTrendOption.value.series[0].data = d.activeStudentTrend || []
        activityTrendOption.value.series[1].data = d.submissionTrend || []
      }

      // 参与度分布
      if (d.engagementBuckets?.length) {
        engagementDistOption.value.series[0].data = d.engagementBuckets.map((b) => ({
          name: b.label,
          value: b.count,
          itemStyle: { color: b.color },
        }))
      }
    }

    // 学生数据
    if (studentsRes.status === 'fulfilled') {
      const raw = studentsRes.value?.data?.records || studentsRes.value?.data?.list || studentsRes.value?.data || studentsRes.value || []
      const list = Array.isArray(raw) ? raw : []
      students.value = list.map((s) => ({
        ...s,
        studentName: s.studentName || s.name || s.username || '-',
        studentId: s.studentId || s.studentNo || s.id || '-',
        onlineHours: s.onlineHours ?? s.totalOnlineHours ?? null,
        engagement: s.engagement ?? s.engagementScore ?? null,
        completionRate: s.completionRate ?? s.progress ?? null,
        submissionCount: s.submissionCount ?? s.submittedAssignments ?? null,
        lastActiveTime: s.lastActiveTime || s.lastLoginTime || null,
      }))

      // 参与度分布：班级级接口未提供 engagementBuckets 时，用学生真实参与度兜底聚合。
      // 缺失参与度字段的学生不参与统计，避免以伪造数据渲染业务图表。
      if (engagementDistOption.value.series[0].data.length === 0) {
        const buckets = { '高(>80%)': 0, '中(50-80%)': 0, '低(<50%)': 0 }
        let withEngagement = 0
        students.value.forEach((s) => {
          if (s.engagement == null) return
          withEngagement++
          if (s.engagement >= 80) buckets['高(>80%)']++
          else if (s.engagement >= 50) buckets['中(50-80%)']++
          else buckets['低(<50%)']++
        })
        if (withEngagement > 0) {
          engagementDistOption.value.series[0].data = [
            { name: '高(>80%)', value: buckets['高(>80%)'], itemStyle: { color: '#10b981' } },
            { name: '中(50-80%)', value: buckets['中(50-80%)'], itemStyle: { color: '#f59e0b' } },
            { name: '低(<50%)', value: buckets['低(<50%)'], itemStyle: { color: '#ef4444' } },
          ]
        }
      }
    }
  } catch (e) {
    logger.warn('[CourseLearningStatusTab] loadData 失败:', e.message || e)
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
watch(() => props.courseId, loadData)
</script>

<style scoped>
.learning-status-tab {
  padding: 4px 0;
}

.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 22px;
}
.stat-card {
  background: var(--ac-bg);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-sm);
  padding: 14px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  transition: box-shadow 0.2s;
}
.stat-card:hover { box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04); }
.stat-icon {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #fff;
}
.stat-icon--blue { background: linear-gradient(135deg, #3b82f6, #60a5fa); }
.stat-icon--green { background: linear-gradient(135deg, #10b981, #34d399); }
.stat-icon--orange { background: linear-gradient(135deg, #f59e0b, #fbbf24); }
.stat-icon--purple { background: linear-gradient(135deg, #8b5cf6, #a78bfa); }
.stat-info { flex: 1; min-width: 0; }
.stat-val { display: block; font-size: 20px; font-weight: 700; color: var(--ac-text-primary); line-height: 1.2; }
.stat-lbl { display: block; font-size: 12px; color: var(--ac-text-secondary); margin-top: 2px; }

.chart-section { margin-bottom: 22px; }
.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}
.chart-card {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  padding: 16px;
}
.chart-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 8px;
}

.table-section { margin-bottom: 10px; }
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin: 0;
}

.engagement-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.engagement-text {
  font-size: 12px;
  color: var(--ac-text-secondary);
  min-width: 32px;
}
.metric-value {
  font-weight: 600;
  color: var(--ac-text-primary);
}
.time-text {
  font-size: 12.5px;
  color: var(--ac-text-secondary);
}

@media (max-width: 768px) {
  .stats-row { grid-template-columns: repeat(2, 1fr); }
  .chart-row { grid-template-columns: 1fr; }
}
</style>
