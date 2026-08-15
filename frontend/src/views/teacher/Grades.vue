<template>
  <div class="fade-in">
    <PageHeader
      title="成绩管理"
      subtitle="查看与导出学生成绩，支持 AI 评分与批阅"
      icon="chart"
      icon-bg="accent"
    >
      <el-button type="success" plain :disabled="gradeList.length === 0" @click="handleExport">
        <el-icon><Download /></el-icon>导出成绩
      </el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard
        label="平均分"
        :value="overview.avgScore ?? '--'"
        icon="chart"
        tone="var(--accent-soft)"
        tone-color="var(--accent)"
      />
      <StatCard
        label="最高分"
        :value="overview.maxScore ?? '--'"
        icon="star"
        tone="var(--ok-soft)"
        tone-color="var(--ok)"
      />
      <StatCard
        label="最低分"
        :value="overview.minScore ?? '--'"
        icon="warn"
        tone="var(--info-soft)"
        tone-color="var(--info)"
      />
      <StatCard
        label="待批阅"
        :value="overview.pendingCount ?? 0"
        icon="edit"
        tone="var(--warn-soft)"
        tone-color="var(--warn)"
      />
    </div>

    <!-- 工具栏 -->
    <div class="table-toolbar">
      <div class="toolbar-left">
        <el-select
          v-model="selectedCourseId"
          placeholder="选择课程"
          clearable
          class="course-select"
          @change="loadGradeData"
        >
          <el-option v-for="c in courseList" :key="c.id" :label="c.name" :value="c.id" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-input
          v-model="searchText"
          placeholder="搜索学号/姓名..."
          clearable
          class="search-input"
          :prefix-icon="Search"
          @clear="handleSearch"
          @keyup.enter="handleSearch"
        />
      </div>
    </div>

    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico l1-ico--accent">
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
            <path d="M3 3v18h18M18 17V9M13 17V5M8 17v-3" />
          </svg>
        </span>
        <div>
          <div class="l1-title">成绩总览</div>
          <div class="l1-sub">成绩表格与分布一览，支持按课程筛选与搜索</div>
        </div>
      </div>
      <div class="l1-body">
        <div class="main-content-grid">
          <!-- 成绩表格 -->
          <div class="table-section">
            <div class="content-card content-card--flush">
              <el-table
                v-loading="loading"
                :data="filteredGradeList"
                @row-click="viewStudentDetail"
              >
                <el-table-column prop="studentNo" label="学号" width="110" />
                <el-table-column prop="studentName" label="姓名" width="90">
                  <template #default="{ row }"
                    ><span class="student-name">{{ row.studentName }}</span></template
                  >
                </el-table-column>
                <el-table-column prop="homeworkScore" label="作业成绩" width="95" align="center">
                  <template #default="{ row }">
                    <span
                      v-if="row.homeworkScore != null"
                      :style="{ color: monoScore(row.homeworkScore) }"
                      >{{ row.homeworkScore }}</span
                    >
                    <span v-else class="cell-muted">--</span>
                  </template>
                </el-table-column>
                <el-table-column prop="examScore" label="考试成绩" width="95" align="center">
                  <template #default="{ row }">
                    <span
                      v-if="row.examScore != null"
                      :style="{ color: monoScore(row.examScore) }"
                      >{{ row.examScore }}</span
                    >
                    <span v-else class="cell-muted">--</span>
                  </template>
                </el-table-column>
                <el-table-column prop="totalScore" label="总评" width="80" align="center">
                  <template #default="{ row }">
                    <span v-if="row.totalScore != null" class="cell-strong">{{
                      row.totalScore
                    }}</span>
                    <span v-else class="cell-muted">--</span>
                  </template>
                </el-table-column>
                <el-table-column prop="gradeLevel" label="等级" width="70" align="center">
                  <template #default="{ row }">
                    <el-tag
                      v-if="row.gradeLevel"
                      :type="getGradeTagType(row.gradeLevel)"
                      size="small"
                      >{{ row.gradeLevel }}</el-tag
                    >
                    <span v-else>--</span>
                  </template>
                </el-table-column>
                <el-table-column prop="submitTime" label="最后提交" width="160" />
                <el-table-column label="操作" width="180" fixed="right">
                  <template #default="{ row }">
                    <el-button text type="primary" size="small" @click.stop="viewStudentDetail(row)"
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
                    <el-button
                      v-if="row.needsGrading"
                      text
                      type="success"
                      size="small"
                      @click.stop="aiGrade(row)"
                      >AI评分</el-button
                    >
                  </template>
                </el-table-column>
              </el-table>
              <el-empty
                v-if="!loading && filteredGradeList.length === 0"
                description="暂无成绩数据"
              >
                <template #image><div class="empty-deco">📊</div></template>
              </el-empty>
            </div>
          </div>

          <!-- 成绩分布图 -->
          <div class="chart-section">
            <div class="l1-card">
              <div class="l1-head">
                <div class="l1-title">成绩分布</div>
              </div>
              <div class="l1-body">
                <v-chart :option="distributionOption" class="dist-chart" autoresize />
              </div>
            </div>

            <!-- 待批阅快捷入口 -->
            <div class="l1-card pending-card">
              <div class="l1-head">
                <div class="l1-title">待批阅任务</div>
              </div>
              <div class="l1-body">
                <div v-if="pendingTasks.length > 0" class="pending-list">
                  <div
                    v-for="task in pendingTasks.slice(0, 5)"
                    :key="task.id"
                    class="pending-item"
                    @click="goToGrading(task)"
                  >
                    <span class="pending-name">{{
                      task.taskName || task.title || '未命名任务'
                    }}</span>
                    <el-tag type="warning" size="small">{{ task.pendingCount || 0 }}份</el-tag>
                  </div>
                </div>
                <el-empty v-else description="暂无待批阅任务" :image-size="60" />
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 学生成绩详情弹窗 -->
    <el-dialog v-model="detailDialogVisible" title="学生成绩详情" width="600px">
      <template v-if="currentStudent">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="学号">{{
            currentStudent.studentNo || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="姓名">{{
            currentStudent.studentName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="课程">{{
            currentStudent.courseName || selectedCourseName
          }}</el-descriptions-item>
          <el-descriptions-item label="总评">
            <span class="score-big">{{ currentStudent.totalScore || '--' }}</span>
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-block">
          <h4 class="detail-h4">各项明细</h4>
          <el-table :data="currentStudent.details || []" size="small">
            <el-table-column prop="taskName" label="任务/考试" />
            <el-table-column prop="type" label="类型" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.type === 'exam' ? 'danger' : 'info'">{{
                  row.type === 'exam' ? '考试' : '作业'
                }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="score" label="得分" width="70" align="center" />
            <el-table-column prop="fullScore" label="满分" width="70" align="center" />
            <el-table-column prop="status" label="状态" width="80" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 'graded' ? 'success' : 'warning'">{{
                  row.status === 'graded' ? '已批阅' : '待批阅'
                }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="submitTime" label="提交时间" />
          </el-table>
          <el-empty
            v-if="!currentStudent.details?.length"
            description="暂无明细数据"
            :image-size="50"
          />
        </div>
      </template>
    </el-dialog>

    <!-- 批阅对话框 -->
    <el-dialog v-model="gradeDialogVisible" title="批阅作业" width="480px">
      <el-form v-if="gradingStudent" label-width="80px">
        <el-form-item label="学生">
          <span>{{ gradingStudent.studentName }} ({{ gradingStudent.studentNo }})</span>
        </el-form-item>
        <el-form-item label="任务">
          <span>{{ gradingStudent.taskName || currentTaskName }}</span>
        </el-form-item>
        <el-form-item label="分数" required>
          <el-input-number
            v-model="gradeForm.score"
            :min="0"
            :max="gradeForm.fullScore || 100"
            class="w-100"
          />
          / {{ gradeForm.fullScore || 100 }}
        </el-form-item>
        <el-form-item label="评语">
          <el-input
            v-model="gradeForm.comment"
            type="textarea"
            :rows="3"
            placeholder="输入评语（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="gradeDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="gradingLoading" @click="submitGrade"
          >确认评分</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Download } from '@element-plus/icons-vue'
import submissionApi from '@/api/submission'
import examApi from '@/api/exam'
import courseApi from '@/api/course'
import taskApi from '@/api/task'
import * as XLSX from 'xlsx'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'
import { getChartTheme, monoScore } from '@/utils/chartTheme'

const loading = ref(false)
const searchText = ref('')
const selectedCourseId = ref(null)
const courseList = ref([])
const selectedCourseName = ref('')

// 总览统计
const overview = ref({
  avgScore: null,
  maxScore: null,
  minScore: null,
  pendingCount: 0,
})

// 成绩列表
const gradeList = ref([])

// 待批阅任务
const pendingTasks = ref([])

// 详情弹窗
const detailDialogVisible = ref(false)
const currentStudent = ref(null)

// 批阅弹窗
const gradeDialogVisible = ref(false)
const gradingStudent = ref(null)
const currentTaskName = ref('')
const gradingLoading = ref(false)
const gradeForm = ref({
  score: 0,
  comment: '',
  fullScore: 100,
})

// 过滤后的成绩列表
const filteredGradeList = computed(() => {
  if (!searchText.value) return gradeList.value
  const kw = searchText.value.toLowerCase()
  return gradeList.value.filter(
    (s) =>
      (s.studentName || '').toLowerCase().includes(kw) ||
      (s.studentNo || '').toLowerCase().includes(kw),
  )
})

onMounted(async () => {
  await loadCourseOptions()
  await loadGradeData()
})

// 加载课程下拉
async function loadCourseOptions() {
  try {
    const res = await courseApi.getMyCourses()
    const data = res.data?.list || res.data || res || []
    courseList.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.error('加载课程失败:', e)
  }
}

// 加载成绩数据（增强：多源聚合 + 待批改 API）
async function loadGradeData() {
  loading.value = true
  try {
    const params = {}
    if (selectedCourseId.value) params.courseId = selectedCourseId.value

    const teacherId = undefined // auth.userId (如 store 中有)

    // 并行加载：统计 + 考试会话 + 作业提交 + 待批改任务列表
    const [statsRes, sessionRes, submitRes, pendingRes] = await Promise.allSettled([
      import('@/api/analytics').then((m) => m.default.getDashboardStats(params)),
      examApi.getMyExamSessions(),
      submissionApi.getMySubmissions({ role: 'teacher', courseId: selectedCourseId.value }),
      taskApi.getPendingGrading(teacherId),
    ])

    // 处理总览统计
    if (statsRes.status === 'fulfilled') {
      const data = statsRes.value.data?.data || statsRes.value.data || {}
      overview.value = {
        avgScore: data.avgScore != null ? Math.round(data.avgScore * 10) / 10 : null,
        maxScore: data.maxScore ?? null,
        minScore: data.minScore ?? null,
        pendingCount: data.pendingCount ?? data.pendingGradeCount ?? 0,
      }
    }

    // 处理考试成绩
    const sessionData =
      sessionRes.status === 'fulfilled'
        ? sessionRes.value.data?.list || sessionRes.value.data || []
        : []
    // 处理作业提交
    const submitData =
      submitRes.status === 'fulfilled'
        ? submitRes.value.data?.list || submitRes.value.data || []
        : []

    // 合并成绩数据
    const studentMap = new Map()

    for (const s of sessionData) {
      const key = s.studentId || s.userId
      if (!studentMap.has(key)) {
        studentMap.set(key, {
          id: key,
          studentNo: s.studentNo || s.userNo || '-',
          studentName: s.studentName || s.userName || '未知',
          homeworkScore: null,
          examScore: s.score ?? null,
          totalScore: s.score ?? null,
          gradeLevel: getGradeLevel(s.score),
          needsGrading: !s.isGraded && s.status !== 'graded',
          submitTime: s.submitTime || s.completedAt || '',
          details: [],
          courseName: s.courseName || '',
        })
      } else {
        const existing = studentMap.get(key)
        existing.examScore = s.score ?? existing.examScore
        existing.totalScore = Math.round(((existing.totalScore + (s.score || 0)) / 2) * 10) / 10
        existing.gradeLevel = getGradeLevel(existing.totalScore)
      }
    }

    for (const sub of submitData) {
      const key = sub.studentId || sub.userId
      if (!studentMap.has(key)) {
        studentMap.set(key, {
          id: key,
          studentNo: sub.studentNo || sub.userNo || '-',
          studentName: sub.studentName || sub.userName || '未知',
          homeworkScore: sub.score ?? null,
          examScore: null,
          totalScore: sub.score ?? null,
          gradeLevel: getGradeLevel(sub.score),
          needsGrading: sub.status !== 'graded' && sub.status !== 'reviewed',
          submitTime: sub.submittedAt || sub.createdAt || '',
          details: [],
          courseName: sub.courseName || '',
        })
      } else {
        const existing = studentMap.get(key)
        existing.homeworkScore = sub.score ?? existing.homeworkScore
        existing.totalScore =
          existing.examScore != null && sub.score != null
            ? Math.round((existing.examScore * 0.5 + sub.score * 0.5) * 10) / 10
            : (existing.examScore ?? sub.score ?? null)
        existing.gradeLevel = getGradeLevel(existing.totalScore)
      }
    }

    gradeList.value = Array.from(studentMap.values())

    // 增强：从 taskApi 获取待批阅任务（优先于从提交记录推导）
    if (pendingRes.status === 'fulfilled') {
      const pData = pendingRes.value.data?.list || pendingRes.value.data || pendingRes.value || []
      const pendingList = Array.isArray(pData) ? pData : []
      if (pendingList.length > 0) {
        pendingTasks.value = pendingList.map((t) => ({
          id: t.id,
          taskName: t.title || t.name || t.taskName || '未命名任务',
          pendingCount: t.pendingCount ?? t.count ?? 1,
        }))
        // 更新待批阅计数
        overview.value.pendingCount = pendingList.length
      }
    }

    // 回退：如果没有待批改任务数据，从提交记录推导
    if (pendingTasks.value.length === 0) {
      pendingTasks.value = (
        submitData.filter((s) => s.status !== 'graded' && s.status !== 'reviewed') || []
      ).map((s) => ({
        id: s.id,
        taskName: s.taskName || s.title || '未命名任务',
        pendingCount: 1,
      }))
    }

    // 更新分布图
    updateDistribution(gradeList.value.map((g) => g.totalScore))

    // 更新选中的课程名
    if (selectedCourseId.value) {
      const found = courseList.value.find((c) => c.id === selectedCourseId.value)
      selectedCourseName.value = found?.name || ''
    }
  } catch (e) {
    console.error('加载成绩数据失败:', e)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  // 搜索由 computed 自动处理
}

function handleExport() {
  const data = filteredGradeList.value
  if (data.length === 0) {
    ElMessage.warning('暂无可导出的成绩数据')
    return
  }
  try {
    const exportData = data.map((item, idx) => ({
      序号: idx + 1,
      学号: item.studentNo || '-',
      姓名: item.studentName || '-',
      作业成绩: item.homeworkScore ?? '-',
      考试成绩: item.examScore ?? '-',
      总评成绩: item.totalScore ?? '-',
      评级: item.gradeLevel || '-',
      提交时间: item.submitTime || '-',
    }))
    const ws = XLSX.utils.json_to_sheet(exportData)
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '学生成绩')
    XLSX.writeFile(
      wb,
      `学生成绩_${new Date().toLocaleDateString('zh-CN').replace(/\//g, '-')}.xlsx`,
    )
    ElMessage.success('导出成功')
  } catch (e) {
    console.error('导出失败:', e)
    ElMessage.error('导出失败')
  }
}

// 查看学生详情（增强：从已有成绩数据构建，不再使用模拟数据）
function viewStudentDetail(row) {
  const details = []
  if (row.homeworkScore != null) {
    details.push({
      taskName: '平时作业',
      type: 'homework',
      score: row.homeworkScore,
      fullScore: 100,
      status: row.needsGrading ? 'pending' : 'graded',
      submitTime: row.submitTime,
    })
  }
  if (row.examScore != null) {
    details.push({
      taskName: '期末考试',
      type: 'exam',
      score: row.examScore,
      fullScore: 100,
      status: 'graded',
      submitTime: row.submitTime,
    })
  }

  currentStudent.value = {
    ...row,
    details,
  }
  detailDialogVisible.value = true
}

// 打开批阅对话框
function openGradeDialog(row) {
  gradingStudent.value = row
  gradeForm.value = { score: 0, comment: '', fullScore: 100 }
  gradeDialogVisible.value = true
}

// AI评分（增强：使用 submissionApi.aiGrade）
async function aiGrade(row) {
  try {
    await ElMessageBox.confirm(`确定使用AI自动评分「${row.studentName}」的作业？`, 'AI评分确认')
    await submissionApi.aiGrade(row.id)
    ElMessage.success('AI评分完成')
    loadGradeData()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || 'AI评分失败')
  }
}

// 提交评分
async function submitGrade() {
  if (gradingStudent.value?.id == null) return
  gradingLoading.value = true
  try {
    await submissionApi.gradeSubmission(gradingStudent.value.id, {
      score: gradeForm.value.score,
      comment: gradeForm.value.comment,
    })
    ElMessage.success('评分成功')
    gradeDialogVisible.value = false
    loadGradeData()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '评分失败')
  } finally {
    gradingLoading.value = false
  }
}

// 跳转到批阅页面
function goToGrading(task) {
  ElMessage.info(`跳转到「${task.taskName}」的批阅页面`)
}

// 辅助函数：根据分数获取等级
function getGradeLevel(score) {
  if (score == null) return ''
  if (score >= 90) return 'A'
  if (score >= 80) return 'B'
  if (score >= 70) return 'C'
  if (score >= 60) return 'D'
  return 'F'
}

// 等级标签颜色
function getGradeTagType(level) {
  const map = { A: 'success', B: 'primary', C: 'warning', D: 'danger', F: 'info' }
  return map[level] || 'info'
}

// 更新成绩分布图
function buildDistributionOption() {
  const t = getChartTheme()
  const bandScores = [95, 85, 75, 65, 50]
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, formatter: '{b}: {c}人' },
    grid: { left: 50, right: 20, bottom: 30, top: 20 },
    xAxis: {
      type: 'category',
      data: ['90-100', '80-89', '70-79', '60-69', '60以下'],
      axisLine: { lineStyle: { color: t.axis } },
      axisLabel: { color: t.text, fontSize: 12 },
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      splitLine: { lineStyle: { color: t.split, type: 'dashed' } },
      axisLabel: { color: t.text, fontSize: 12 },
    },
    series: [
      {
        type: 'bar',
        data: [0, 0, 0, 0, 0],
        barWidth: '50%',
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: (params) => monoScore(bandScores[params.dataIndex] ?? 0),
        },
        label: { show: true, position: 'top', fontSize: 11, color: t.text },
      },
    ],
  }
}

const distributionOption = ref(buildDistributionOption())

function updateDistribution(scores) {
  const buckets = [0, 0, 0, 0, 0]
  for (const s of scores) {
    if (s == null) continue
    if (s >= 90) buckets[0]++
    else if (s >= 80) buckets[1]++
    else if (s >= 70) buckets[2]++
    else if (s >= 60) buckets[3]++
    else buckets[4]++
  }
  distributionOption.value.series[0].data = buckets
}
</script>

<style scoped>
.l1-ico--accent {
  background: var(--accent-soft);
  color: var(--accent);
}

.empty-deco {
  font-size: 38px;
  filter: grayscale(0.2);
}

.main-content-grid {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: var(--ac-spacing-md);
}

.table-section {
  min-width: 0;
}

.chart-section {
  display: flex;
  flex-direction: column;
  gap: var(--ac-spacing-md);
}

.pending-card {
  flex-shrink: 0;
}

.pending-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pending-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: var(--ac-bg);
  border-radius: var(--ac-radius-sm);
  cursor: pointer;
  transition: all var(--ac-transition-fast);
  border-left: 3px solid transparent;
}

.pending-item:hover {
  background: var(--ac-bg-hover);
  border-left-color: var(--ac-warning);
}

.pending-name {
  font-size: 13px;
  color: var(--ac-text-secondary);
  font-weight: 500;
}

.course-select {
  width: 200px;
}
.search-input {
  width: 220px;
}
.content-card--flush {
  padding: 0;
  overflow: hidden;
}
.student-name {
  font-weight: 500;
}
.cell-muted {
  color: var(--ac-text-muted);
}
.cell-strong {
  font-weight: 600;
  color: var(--ac-text-primary);
}
.dist-chart {
  height: 320px;
}
.score-big {
  font-weight: 700;
  font-size: 18px;
  color: var(--ac-primary);
}
.detail-block {
  margin-top: 20px;
}
.detail-h4 {
  font-size: 14px;
  color: var(--ac-text-primary);
  margin-bottom: 12px;
}
.w-100 {
  width: 100%;
}

@media (max-width: 1024px) {
  .main-content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
