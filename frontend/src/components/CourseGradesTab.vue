<template>
  <div class="grades-tab">
    <!-- 统计卡 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--ac-primary-bg); color: var(--ac-primary)">
          <svg viewBox="0 0 24 24" fill="none" width="18" height="18">
            <path
              d="M3 19V5M7 19v-8M11 19V9M15 19v-6M19 19V4"
              stroke="currentColor"
              stroke-width="1.6"
              stroke-linecap="round"
            />
            <path d="M2 20h20" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
          </svg>
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ overview.avgScore ?? '--' }}</span
          ><span class="stat-lbl">平均分</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--ac-success-bg); color: var(--ac-success)">
          <Top :size="18" />
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ overview.maxScore ?? '--' }}</span
          ><span class="stat-lbl">最高分</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--ac-danger-bg); color: var(--ac-danger)">
          <Bottom :size="18" />
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ overview.minScore ?? '--' }}</span
          ><span class="stat-lbl">最低分</span>
        </div>
      </div>
      <div class="stat-card">
        <div class="stat-icon" style="background: var(--ac-warning-bg); color: var(--ac-warning)">
          <EditPen :size="18" />
        </div>
        <div class="stat-info">
          <span class="stat-val">{{ overview.pendingCount ?? '--' }}</span
          ><span class="stat-lbl">待批阅</span>
        </div>
      </div>
    </div>

    <!-- 工具栏 -->
    <div class="tab-toolbar">
      <el-button
        type="success"
        plain
        size="small"
        :disabled="gradeList.length === 0"
        @click="handleExport"
      >
        <el-icon><Download :size="14" /></el-icon>导出成绩
      </el-button>
      <el-input
        v-model="searchText"
        placeholder="搜索学号/姓名..."
        clearable
        style="width: 200px"
        :prefix-icon="Search"
      />
    </div>

    <!-- 主区域 -->
    <div class="main-grid">
      <!-- 成绩表格 -->
      <div class="table-area">
        <el-table
          v-loading="loading"
          :data="filteredGrades"
          style="width: 100%"
          :header-cell-style="{
            background: 'var(--ac-bg)',
            color: 'var(--ac-text-secondary)',
            fontWeight: 600,
          }"
          size="default"
          @row-click="viewDetail"
        >
          <el-table-column prop="studentNo" label="学号" width="100" />
          <el-table-column prop="studentName" label="姓名" width="85">
            <template #default="{ row }"
              ><span style="font-weight: 500">{{ row.studentName }}</span></template
            >
          </el-table-column>
          <el-table-column prop="homeworkScore" label="作业成绩" width="90" align="center">
            <template #default="{ row }">
              <span
                v-if="row.homeworkScore != null"
                :style="{ color: getScoreColor(row.homeworkScore) }"
                >{{ row.homeworkScore }}</span
              >
              <span v-else style="color: var(--ac-text-muted)">--</span>
            </template>
          </el-table-column>
          <el-table-column prop="examScore" label="考试成绩" width="90" align="center">
            <template #default="{ row }">
              <span v-if="row.examScore != null" :style="{ color: getScoreColor(row.examScore) }">{{
                row.examScore
              }}</span>
              <span v-else style="color: var(--ac-text-muted)">--</span>
            </template>
          </el-table-column>
          <el-table-column prop="totalScore" label="总评" width="70" align="center">
            <template #default="{ row }">
              <span
                v-if="row.totalScore != null"
                style="font-weight: 600; color: var(--ac-text-primary)"
                >{{ row.totalScore }}</span
              >
              <span v-else style="color: var(--ac-text-muted)">--</span>
            </template>
          </el-table-column>
          <el-table-column prop="gradeLevel" label="等级" width="60" align="center">
            <template #default="{ row }"
              ><el-tag v-if="row.gradeLevel" :type="getGradeTagType(row.gradeLevel)" size="small">{{
                row.gradeLevel
              }}</el-tag
              ><span v-else>--</span></template
            >
          </el-table-column>
          <el-table-column label="操作" width="140" align="center">
            <template #default="{ row }">
              <div class="action-row">
                <el-button text type="primary" size="small" @click.stop="viewDetail(row)"
                  >详情</el-button
                >
                <el-button
                  v-if="row.needsGrading"
                  text
                  type="warning"
                  size="small"
                  @click.stop="openGradeDialog(row)"
                  >批阅</el-button
                >
              </div>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!loading && filteredGrades.length === 0" description="暂无成绩数据" />
      </div>

      <!-- 右侧：分布图 + 待批阅 -->
      <div class="side-panel">
        <div class="chart-card">
          <div class="chart-title">成绩分布</div>
          <v-chart :option="distOption" style="height: 240px" autoresize />
        </div>
        <div class="chart-card pending-card">
          <div class="chart-title">待批阅任务</div>
          <div v-if="pendingTasks.length > 0" class="pending-list">
            <div v-for="t in pendingTasks.slice(0, 5)" :key="t.id" class="pending-item">
              <span>{{ t.taskName || t.title || '未命名' }}</span>
              <el-tag type="warning" size="small">{{ t.pendingCount || 0 }}份</el-tag>
            </div>
          </div>
          <el-empty v-else description="暂无待批阅" :image-size="50" />
        </div>
      </div>
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="学生成绩详情" width="560px" append-to-body>
      <template v-if="currentStudent">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="学号">{{
            currentStudent.studentNo || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{
            currentStudent.studentName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="总评">
            <span style="font-weight: 700; font-size: 18px; color: var(--ac-primary)">{{
              currentStudent.totalScore || '--'
            }}</span>
          </el-descriptions-item>
        </el-descriptions>
        <el-table
          :data="currentStudent.details || []"
          size="small"
          style="width: 100%; margin-top: 16px"
        >
          <el-table-column prop="taskName" label="任务/考试" />
          <el-table-column prop="type" label="类型" width="80" align="center">
            <template #default="{ row }"
              ><el-tag size="small" :type="row.type === 'exam' ? 'primary' : 'info'">{{
                row.type === 'exam' ? '考试' : '作业'
              }}</el-tag></template
            >
          </el-table-column>
          <el-table-column prop="score" label="得分" width="70" align="center" />
          <el-table-column prop="status" label="状态" width="80" align="center">
            <template #default="{ row }"
              ><el-tag size="small" :type="row.status === 'graded' ? 'success' : 'warning'">{{
                row.status === 'graded' ? '已批阅' : '待批阅'
              }}</el-tag></template
            >
          </el-table-column>
        </el-table>
      </template>
    </el-dialog>

    <!-- 批阅弹窗 -->
    <el-dialog v-model="gradeVisible" title="批阅作业" width="440px" append-to-body>
      <el-form v-if="gradingStudent" label-width="72px">
        <el-form-item label="学生"
          ><span
            >{{ gradingStudent.studentName }} ({{ gradingStudent.studentNo }})</span
          ></el-form-item
        >
        <el-form-item label="分数">
          <el-input-number
            v-model="gradeForm.score"
            :min="0"
            :max="gradeForm.fullScore || 100"
            style="width: 100%"
          />
          / {{ gradeForm.fullScore || 100 }}
        </el-form-item>
        <el-form-item label="评语"
          ><el-input v-model="gradeForm.comment" type="textarea" :rows="2" placeholder="可选"
        /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="gradeVisible = false">取消</el-button>
        <el-button type="primary" :loading="gradingLoading" @click="submitGrade"
          >确认评分</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Download, Top, Bottom, EditPen } from '@element-plus/icons-vue'
import submissionApi from '@/api/submission'
import examApi from '@/api/exam'
import taskApi from '@/api/task'
import { getChartTheme, getMonoSeries, monoScore } from '@/utils/chartTheme'

const props = defineProps({ courseId: { type: [Number, String], required: true } })
const loading = ref(false)
const searchText = ref('')
const gradeList = ref([])
const pendingTasks = ref([])
const overview = ref({ avgScore: null, maxScore: null, minScore: null, pendingCount: 0 })

// 弹窗
const detailVisible = ref(false),
  currentStudent = ref(null)
const gradeVisible = ref(false),
  gradingStudent = ref(null),
  gradingLoading = ref(false)
const gradeForm = ref({ score: 0, comment: '', fullScore: 100 })

const filteredGrades = computed(() => {
  if (!searchText.value) return gradeList.value
  const kw = searchText.value.toLowerCase()
  return gradeList.value.filter(
    (s) =>
      (s.studentName || '').toLowerCase().includes(kw) ||
      (s.studentNo || '').toLowerCase().includes(kw),
  )
})

const distOption = ref({
  tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}: {c}人' },
  grid: { left: 50, right: 20, bottom: 30, top: 20 },
  xAxis: {
    type: 'category',
    data: ['90-100', '80-89', '70-79', '60-69', '60以下'],
    axisLine: { lineStyle: { color: getChartTheme().axis } },
    axisLabel: { color: getChartTheme().subText, fontSize: 12 },
  },
  yAxis: {
    type: 'value',
    axisLine: { show: false },
    splitLine: { lineStyle: { color: getChartTheme().axis, type: 'dashed' } },
    axisLabel: { color: getChartTheme().subText, fontSize: 12 },
  },
  series: [
    {
      type: 'bar',
      data: [0, 0, 0, 0, 0],
      barWidth: '50%',
      itemStyle: {
        borderRadius: [6, 6, 0, 0],
        color: (p) => getMonoSeries()[p.dataIndex] || getMonoSeries()[0],
      },
      label: { show: true, position: 'top', fontSize: 11, color: getChartTheme().subText },
    },
  ],
})

onMounted(loadData)
watch(() => props.courseId, loadData)

async function loadData() {
  loading.value = true
  try {
    const cid = Number(props.courseId)
    const [statsRes, sessionRes, submitRes, pendingRes] = await Promise.allSettled([
      import('@/api/analytics').then((m) => m.default.getDashboardStats({ courseId: cid })),
      examApi.getMyExamSessions(),
      submissionApi.getMySubmissions({ role: 'teacher', courseId: cid }),
      taskApi.getPendingGrading(undefined),
    ])

    if (statsRes.status === 'fulfilled') {
      const d = statsRes.value?.data?.data || statsRes.value?.data || {}
      overview.value = {
        avgScore: d.avgScore != null ? Math.round(d.avgScore * 10) / 10 : null,
        maxScore: d.maxScore ?? null,
        minScore: d.minScore ?? null,
        pendingCount: d.pendingCount ?? 0,
      }
    }

    const sessionData =
      sessionRes.status === 'fulfilled'
        ? sessionRes.value.data?.list || sessionRes.value.data || []
        : []
    const submitData =
      submitRes.status === 'fulfilled'
        ? submitRes.value.data?.list || submitRes.value.data || []
        : []

    const sm = new Map()
    for (const s of sessionData) {
      const k = s.studentId || s.userId
      if (!sm.has(k))
        sm.set(k, {
          id: k,
          studentNo: s.studentNo || s.userNo || '-',
          studentName: s.studentName || s.userName || '未知',
          homeworkScore: null,
          examScore: s.score ?? null,
          totalScore: s.score ?? null,
          gradeLevel: getGL(s.score),
          needsGrading: !s.isGraded && s.status !== 'graded',
          submitTime: s.submitTime || '',
          details: [],
        })
      else {
        const e = sm.get(k)
        e.examScore = s.score ?? e.examScore
        e.totalScore = Math.round(((e.totalScore + (s.score || 0)) / 2) * 10) / 10
        e.gradeLevel = getGL(e.totalScore)
      }
    }
    for (const s of submitData) {
      const k = s.studentId || s.userId
      if (!sm.has(k))
        sm.set(k, {
          id: k,
          studentNo: s.studentNo || s.userNo || '-',
          studentName: s.studentName || s.userName || '未知',
          homeworkScore: s.score ?? null,
          examScore: null,
          totalScore: s.score ?? null,
          gradeLevel: getGL(s.score),
          needsGrading: s.status !== 'graded' && s.status !== 'reviewed',
          submitTime: s.submittedAt || s.createdAt || '',
          details: [],
        })
      else {
        const e = sm.get(k)
        e.homeworkScore = s.score ?? e.homeworkScore
        e.totalScore =
          e.examScore != null && s.score != null
            ? Math.round((e.examScore * 0.5 + s.score * 0.5) * 10) / 10
            : (e.examScore ?? s.score ?? null)
        e.gradeLevel = getGL(e.totalScore)
      }
    }
    gradeList.value = Array.from(sm.values())

    if (pendingRes.status === 'fulfilled') {
      const pd = pendingRes.value.data?.list || pendingRes.value.data || pendingRes.value || []
      pendingTasks.value = (Array.isArray(pd) ? pd : []).map((t) => ({
        id: t.id,
        taskName: t.title || t.name || t.taskName || '未命名',
        pendingCount: t.pendingCount ?? t.count ?? 1,
      }))
      if (pendingTasks.value.length) overview.value.pendingCount = pendingTasks.value.length
    }
    if (!pendingTasks.value.length) {
      pendingTasks.value = (
        submitData.filter((s) => s.status !== 'graded' && s.status !== 'reviewed') || []
      ).map((s) => ({ id: s.id, taskName: s.taskName || s.title || '未命名', pendingCount: 1 }))
    }

    updateDist(gradeList.value.map((g) => g.totalScore))
  } catch (e) {
    logger.warn('[CourseGradesTab]', e.message || e)
  } finally {
    loading.value = false
  }
}

function updateDist(scores) {
  const b = [0, 0, 0, 0, 0]
  for (const s of scores) {
    if (s == null) continue
    if (s >= 90) b[0]++
    else if (s >= 80) b[1]++
    else if (s >= 70) b[2]++
    else if (s >= 60) b[3]++
    else b[4]++
  }
  distOption.value.series[0].data = b
}

function getGL(score) {
  if (score == null) return ''
  if (score >= 90) return 'A'
  if (score >= 80) return 'B'
  if (score >= 70) return 'C'
  if (score >= 60) return 'D'
  return 'F'
}
function getGradeTagType(l) {
  const m = { A: 'success', B: 'primary', C: 'warning', D: 'danger', F: 'info' }
  return m[l] || 'info'
}
function getScoreColor(score) {
  return monoScore(score)
}

function viewDetail(row) {
  const d = []
  if (row.homeworkScore != null)
    d.push({
      taskName: '平时作业',
      type: 'homework',
      score: row.homeworkScore,
      fullScore: 100,
      status: row.needsGrading ? 'pending' : 'graded',
    })
  if (row.examScore != null)
    d.push({
      taskName: '期末考试',
      type: 'exam',
      score: row.examScore,
      fullScore: 100,
      status: 'graded',
    })
  currentStudent.value = { ...row, details: d }
  detailVisible.value = true
}

function openGradeDialog(row) {
  gradingStudent.value = row
  gradeForm.value = { score: 0, comment: '', fullScore: 100 }
  gradeVisible.value = true
}

async function submitGrade() {
  if (!gradingStudent.value?.id) return
  gradingLoading.value = true
  try {
    await submissionApi.gradeSubmission(gradingStudent.value.id, {
      score: gradeForm.value.score,
      comment: gradeForm.value.comment,
    })
    ElMessage.success('评分成功')
    gradeVisible.value = false
    await loadData()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '评分失败')
  } finally {
    gradingLoading.value = false
  }
}

function handleExport() {
  ElMessage.info('正在导出成绩Excel...')
  logger.log('export:', filteredGrades.value)
}
</script>

<style scoped>
.grades-tab {
  padding: 4px 0;
}
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 14px;
}
.stat-card {
  background: var(--ac-bg);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-sm);
  padding: 10px 12px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.stat-icon {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.stat-info {
  flex: 1;
  min-width: 0;
}
.stat-val {
  display: block;
  font-size: 18px;
  font-weight: 700;
  color: var(--ac-text-primary);
  line-height: 1.2;
}
.stat-lbl {
  display: block;
  font-size: 11px;
  color: var(--ac-text-secondary);
}
.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
  gap: 8px;
  flex-wrap: wrap;
}
.main-grid {
  display: grid;
  grid-template-columns: 1fr 300px;
  gap: 14px;
}
.table-area {
  min-width: 0;
}
.side-panel {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.chart-card {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  padding: 14px;
}
.pending-card {
  flex-shrink: 0;
}
.chart-title {
  font-size: 13.5px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: 10px;
}
.pending-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.pending-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--ac-bg);
  border-radius: var(--ac-radius-sm);
  font-size: 12.5px;
  color: var(--ac-text-secondary);
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
  border-left: 3px solid transparent;
}
.pending-item:hover {
  background: var(--ac-bg-hover);
  border-left-color: var(--ac-warning);
}
.action-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  flex-wrap: nowrap;
}
@media (max-width: 1024px) {
  .main-grid {
    grid-template-columns: 1fr;
  }
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
