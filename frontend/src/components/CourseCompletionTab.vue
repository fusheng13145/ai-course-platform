<template>
  <div class="completion-tab">
    <!-- 概览统计卡 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-icon stat-icon--blue"><Document :size="20" /></div>
        <div class="stat-info">
          <span class="stat-val">{{ overview.totalAssignments }}</span>
          <span class="stat-lbl">作业总数</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--green">
          <svg viewBox="0 0 24 24" fill="none" width="20" height="20">
            <path
              d="M9 12l2 2 4-4"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="2" />
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ overview.avgSubmissionRate }}%</span>
          <span class="stat-lbl">平均提交率</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--orange">
          <svg viewBox="0 0 24 24" fill="none" width="20" height="20">
            <path
              d="M12 20V10M18 20V4M6 20v-6"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
            />
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ overview.avgScore }}</span>
          <span class="stat-lbl">平均成绩</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon stat-icon--purple">
          <svg viewBox="0 0 24 24" fill="none" width="20" height="20">
            <path
              d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2"
              stroke="currentColor"
              stroke-width="2"
              stroke-linecap="round"
            />
            <rect x="8" y="2" width="8" height="4" rx="1" stroke="currentColor" stroke-width="2" />
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ overview.totalSubmissions }}</span>
          <span class="stat-lbl">已提交数</span>
        </div>
      </div>
    </div>

    <!-- Tab 切换：按作业 / 按学生 -->
    <el-tabs v-model="viewMode" class="view-tabs">
      <el-tab-pane label="按作业查看" name="assignment">
        <!-- 作业级提交统计 -->
        <div v-loading="loadingAssignments" class="content-card">
          <el-table :data="assignmentStats" style="width: 100%">
            <el-table-column prop="title" label="作业标题" min-width="200" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag
                  :type="
                    row.status === 'ACTIVE'
                      ? 'success'
                      : row.status === 'CLOSED'
                        ? 'danger'
                        : 'info'
                  "
                  size="small"
                >
                  {{
                    row.status === 'ACTIVE'
                      ? '进行中'
                      : row.status === 'CLOSED'
                        ? '已截止'
                        : '未发布'
                  }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="已提交/应提交" width="130" align="center">
              <template #default="{ row }">
                <span class="ratio">{{ row.submittedCount }} / {{ row.totalStudents }}</span>
              </template>
            </el-table-column>
            <el-table-column label="提交率" width="150">
              <template #default="{ row }">
                <div class="completion-cell">
                  <el-progress
                    :percentage="row.submissionRate"
                    :stroke-width="7"
                    :show-text="false"
                    style="width: 80px"
                    :color="getRateColor(row.submissionRate)"
                    :status="row.submissionRate === 100 ? 'success' : undefined"
                  />
                  <span class="completion-text">{{ row.submissionRate }}%</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="未提交" width="80" align="center">
              <template #default="{ row }">
                <span class="pending-count" :class="{ 'zero-pending': row.unsubmittedCount === 0 }">
                  {{ row.unsubmittedCount }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="已批改" width="80" align="center">
              <template #default="{ row }">
                <span>{{ row.gradedCount }} / {{ row.submittedCount }}</span>
              </template>
            </el-table-column>
            <el-table-column label="平均分" width="90" align="center">
              <template #default="{ row }">
                <span class="avg-score">{{ row.avgScore }}</span>
              </template>
            </el-table-column>
            <el-table-column label="截止时间" width="155">
              <template #default="{ row }">
                <span class="time-text">{{ formatDate(row.dueDate) }}</span>
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="!loadingAssignments && assignmentStats.length === 0"
            description="暂无作业"
          />
        </div>

        <!-- 成绩分布图 -->
        <div v-if="assignmentStats.length > 0" class="chart-section">
          <div class="chart-card">
            <div class="chart-title">成绩分布直方图</div>
            <v-chart :option="gradeDistOption" style="height: 260px" autoresize />
          </div>
        </div>
      </el-tab-pane>

      <el-tab-pane label="按学生查看" name="student">
        <div v-loading="loadingStudents" class="content-card">
          <el-table :data="studentStats" style="width: 100%">
            <el-table-column type="index" label="#" width="50" />
            <el-table-column prop="studentName" label="姓名" min-width="110" />
            <el-table-column prop="studentId" label="学号" width="120" />
            <el-table-column label="已提交/总作业" width="130" align="center">
              <template #default="{ row }">
                <span class="ratio">{{ row.submittedCount }} / {{ row.totalAssignments }}</span>
              </template>
            </el-table-column>
            <el-table-column label="提交率" width="150">
              <template #default="{ row }">
                <div class="completion-cell">
                  <el-progress
                    :percentage="row.submissionRate"
                    :stroke-width="7"
                    :show-text="false"
                    style="width: 80px"
                    :color="getRateColor(row.submissionRate)"
                    :status="row.submissionRate === 100 ? 'success' : undefined"
                  />
                  <span class="completion-text">{{ row.submissionRate }}%</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="平均成绩" width="100" align="center" sortable prop="avgScore">
              <template #default="{ row }">
                <span class="avg-score" :class="{ 'low-score': row.avgScore < 60 }">
                  {{ row.avgScore }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="最高分" width="80" align="center">
              <template #default="{ row }">{{ row.maxScore }}</template>
            </el-table-column>
            <el-table-column label="最低分" width="80" align="center">
              <template #default="{ row }">{{ row.minScore }}</template>
            </el-table-column>
            <el-table-column label="提交状态" width="130">
              <template #default="{ row }">
                <el-tag
                  :type="
                    row.submissionRate === 100
                      ? 'success'
                      : row.submissionRate >= 60
                        ? 'warning'
                        : 'danger'
                  "
                  size="small"
                  effect="plain"
                >
                  {{
                    row.submissionRate === 100
                      ? '全部完成'
                      : row.submissionRate >= 60
                        ? '部分完成'
                        : '较多缺失'
                  }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
          <el-empty
            v-if="!loadingStudents && studentStats.length === 0"
            description="暂无学生数据"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { ref, onMounted, watch } from 'vue'
import { Document } from '@element-plus/icons-vue'
import taskApi from '@/api/task'
import submissionApi from '@/api/submission'
import courseApi from '@/api/course'
import { getChartTheme } from '@/utils/chartTheme'

const props = defineProps({
  courseId: { type: [Number, String], required: true },
})

const loadingAssignments = ref(false)
const loadingStudents = ref(false)
const viewMode = ref('assignment')

const overview = ref({
  totalAssignments: 0,
  avgSubmissionRate: 0,
  avgScore: 0,
  totalSubmissions: 0,
})

const assignmentStats = ref([])
const studentStats = ref([])

const gradeDistOption = ref({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
  grid: { left: 45, right: 20, bottom: 25, top: 10 },
  xAxis: {
    type: 'category',
    data: ['0-59', '60-69', '70-79', '80-89', '90-100'],
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
      type: 'bar',
      data: [0, 0, 0, 0, 0],
      barWidth: '40%',
      itemStyle: {
        borderRadius: [6, 6, 0, 0],
        color: (p) => {
          const colors = ['#ef4444', '#f59e0b', '#fbbf24', '#34d399', '#10b981']
          return colors[p.dataIndex] || '#3b82f6'
        },
      },
      label: {
        show: true,
        position: 'top',
        fontSize: 11,
        color: getChartTheme().subText,
      },
    },
  ],
})

function getRateColor(rate) {
  if (rate >= 90) return '#10b981'
  if (rate >= 60) return '#f59e0b'
  return '#ef4444'
}

function formatDate(d) {
  return d ? new Date(d).toLocaleString('zh-CN') : '-'
}

async function loadData() {
  loadingAssignments.value = true
  loadingStudents.value = true

  try {
    const cid = Number(props.courseId)

    // 获取作业列表
    const taskRes = await taskApi.getTaskList({ courseId: cid })
    const tasks = taskRes.data?.records || taskRes.data?.list || taskRes.data || taskRes || []
    const taskList = Array.isArray(tasks) ? tasks : []

    // 获取学生列表
    let studentList = []
    try {
      const stuRes = await courseApi.getStudents(cid)
      const raw = stuRes.data?.records || stuRes.data?.list || stuRes.data || stuRes || []
      studentList = Array.isArray(raw) ? raw : []
    } catch (e) {
      logger.warn('[CourseCompletionTab] 获取学生列表失败:', e.message || e)
    }

    const totalStudents = studentList.length || 1

    // 作业级统计
    const taskStats = []
    let totalSubmissions = 0
    const allScores = []
    let totalRates = 0

    for (const t of taskList) {
      let submissions = []
      try {
        const subRes = await submissionApi.getTaskSubmissions(t.id)
        submissions = subRes.data?.records || subRes.data?.list || subRes.data || subRes || []
        submissions = Array.isArray(submissions) ? submissions : []
      } catch (e) {
        // 静默
      }

      const submittedCount = submissions.length
      const gradeSubs = submissions.filter((s) => s.score != null)
      const gradedCount = gradeSubs.length
      const scores = gradeSubs.map((s) => s.score)
      const avgScore =
        scores.length > 0
          ? Math.round((scores.reduce((a, b) => a + b, 0) / scores.length) * 10) / 10
          : 0
      allScores.push(...scores)

      const submissionRate = Math.round((submittedCount / Math.max(totalStudents, 1)) * 100)
      totalRates += submissionRate
      totalSubmissions += submittedCount

      taskStats.push({
        id: t.id,
        title: t.title || '未命名作业',
        status: t.status || 'DRAFT',
        dueDate: t.dueDate,
        totalStudents,
        submittedCount,
        unsubmittedCount: Math.max(0, totalStudents - submittedCount),
        submissionRate,
        gradedCount,
        avgScore,
      })
    }

    assignmentStats.value = taskStats

    // 概览统计
    const avgSubmissionRate = taskStats.length > 0 ? Math.round(totalRates / taskStats.length) : 0
    const totalAvgScore =
      allScores.length > 0
        ? Math.round((allScores.reduce((a, b) => a + b, 0) / allScores.length) * 10) / 10
        : 0

    overview.value = {
      totalAssignments: taskList.length,
      avgSubmissionRate,
      avgScore: totalAvgScore,
      totalSubmissions,
    }

    // 成绩分布直方图
    if (allScores.length > 0) {
      const buckets = [0, 0, 0, 0, 0]
      allScores.forEach((s) => {
        if (s < 60) buckets[0]++
        else if (s < 70) buckets[1]++
        else if (s < 80) buckets[2]++
        else if (s < 90) buckets[3]++
        else buckets[4]++
      })
      gradeDistOption.value.series[0].data = buckets
    }

    // 学生级统计
    const studentMap = new Map()
    studentList.forEach((s) => {
      const key = s.studentId || s.studentNo || s.id
      studentMap.set(String(key), {
        studentId: s.studentId || s.studentNo || String(s.id),
        studentName: s.studentName || s.name || s.username || '-',
        submittedCount: 0,
        totalAssignments: taskList.length,
        scores: [],
      })
    })

    for (const t of taskList) {
      try {
        const subRes = await submissionApi.getTaskSubmissions(t.id)
        const submissions = subRes.data?.records || subRes.data?.list || subRes.data || subRes || []
        const subs = Array.isArray(submissions) ? submissions : []
        subs.forEach((sub) => {
          const key = String(sub.studentId || sub.userId || sub.submitterId || '')
          const entry = studentMap.get(key)
          if (entry) {
            entry.submittedCount++
            if (sub.score != null) entry.scores.push(sub.score)
          }
        })
      } catch (e) {
        // 静默
      }
    }

    const stuStats = []
    studentMap.forEach((entry) => {
      const rate =
        entry.totalAssignments > 0
          ? Math.round((entry.submittedCount / entry.totalAssignments) * 100)
          : 0
      const sortedScores = [...entry.scores].sort((a, b) => a - b)
      stuStats.push({
        ...entry,
        submissionRate: rate,
        avgScore:
          entry.scores.length > 0
            ? Math.round((entry.scores.reduce((a, b) => a + b, 0) / entry.scores.length) * 10) / 10
            : '-',
        maxScore: sortedScores.length > 0 ? sortedScores[sortedScores.length - 1] : '-',
        minScore: sortedScores.length > 0 ? sortedScores[0] : '-',
      })
    })

    studentStats.value = stuStats
  } catch (e) {
    logger.warn('[CourseCompletionTab] loadData 失败:', e.message || e)
  } finally {
    loadingAssignments.value = false
    loadingStudents.value = false
  }
}

onMounted(loadData)
watch(() => props.courseId, loadData)
</script>

<style scoped>
.completion-tab {
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
.stat-card:hover {
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.04);
}
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
.stat-icon--blue {
  background: linear-gradient(135deg, #3b82f6, #60a5fa);
}
.stat-icon--green {
  background: linear-gradient(135deg, #10b981, #34d399);
}
.stat-icon--orange {
  background: linear-gradient(135deg, #f59e0b, #fbbf24);
}
.stat-icon--purple {
  background: linear-gradient(135deg, #8b5cf6, #a78bfa);
}
.stat-info {
  flex: 1;
  min-width: 0;
}
.stat-val {
  display: block;
  font-size: 20px;
  font-weight: 700;
  color: var(--ac-text-primary);
  line-height: 1.2;
}
.stat-lbl {
  display: block;
  font-size: 12px;
  color: var(--ac-text-secondary);
  margin-top: 2px;
}

.view-tabs {
  margin-top: 4px;
}

.completion-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}
.completion-text {
  font-size: 12px;
  color: var(--ac-text-secondary);
  min-width: 32px;
}
.pending-count {
  font-weight: 600;
  color: var(--ac-danger, #ef4444);
}
.zero-pending {
  color: var(--ac-success, #10b981);
}
.avg-score {
  font-weight: 600;
  color: var(--ac-text-primary);
}
.low-score {
  color: var(--ac-danger, #ef4444);
}
.ratio {
  font-size: 13px;
  color: var(--ac-text-primary);
  font-weight: 500;
}
.time-text {
  font-size: 12.5px;
  color: var(--ac-text-secondary);
}

.chart-section {
  margin-top: 18px;
  margin-bottom: 10px;
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

@media (max-width: 768px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
