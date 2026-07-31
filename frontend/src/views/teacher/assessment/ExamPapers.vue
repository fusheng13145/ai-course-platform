<template>
  <div class="exam-papers fade-in">
    <PageHeader title="试卷管理" subtitle="创建、发布与统计您的考试试卷" icon="doc" icon-bg="warn">
      <el-button type="primary" @click="openCreateDialog"><el-icon><Plus /></el-icon> 新建试卷</el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard label="试卷总数" :value="total" icon="layers" tone="var(--accent-soft)" tone-color="var(--accent)" />
      <StatCard label="已发布" :value="countBy('PUBLISHED')" icon="checks" tone="var(--ok-soft)" tone-color="var(--ok)" />
      <StatCard label="草稿" :value="countBy('DRAFT')" icon="edit" tone="var(--warn-soft)" tone-color="var(--warn)" />
      <StatCard label="已结束" :value="countBy('ENDED')" icon="clock" tone="var(--info-soft)" tone-color="var(--info)" />
    </div>

    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico" style="background:var(--warn-soft);color:var(--warn)">
          <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M14.5 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V7.5L14.5 2zM14 2v6h6"/></svg>
        </span>
        <div><div class="l1-title">试卷列表</div><div class="l1-sub">按课程筛选，支持题目查看与成绩统计</div></div>
      </div>
      <div class="l1-body">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-select v-model="filters.courseId" placeholder="全部课程" clearable filterable style="width: 200px" @change="handleFilterChange">
              <el-option v-for="c in courseList" :key="c.id" :label="c.name || c.title" :value="c.id" />
            </el-select>
            <el-button type="primary" @click="handleFilterChange"><el-icon><Search /></el-icon>查询</el-button>
          </div>
        </div>

        <div v-loading="loading" class="table-area">
          <el-table :data="examList" style="width: 100%">
            <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip>
              <template #default="{ row }"><span class="title-cell">{{ row.title }}</span></template>
            </el-table-column>
            <el-table-column prop="description" label="描述" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">{{ row.description || '—' }}</template>
            </el-table-column>
            <el-table-column label="时长(分)" width="90" align="center">
              <template #default="{ row }">{{ row.duration ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="总分" width="80" align="center">
              <template #default="{ row }">{{ row.totalScore ?? '—' }}</template>
            </el-table-column>
            <el-table-column label="题量" width="80" align="center">
              <template #default="{ row }">{{ row.questionCount ?? (row.questions ? row.questions.length : 0) }}</template>
            </el-table-column>
            <el-table-column prop="courseName" label="所属课程" min-width="140" show-overflow-tooltip />
            <el-table-column label="状态" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="getStatusType(row.status)" effect="light">{{ getStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="300" fixed="right" align="center">
              <template #default="{ row }">
                <div class="action-row">
                  <el-button link type="primary" size="small" @click="openQuestionsDialog(row)">题目</el-button>
                  <el-button link type="info" size="small" @click="openStatsDialog(row)">统计</el-button>
                  <el-button v-if="row.status === 'DRAFT'" link type="primary" size="small" @click="openEditDialog(row)">编辑</el-button>
                  <el-button v-if="row.status === 'DRAFT'" link type="success" size="small" @click="handlePublish(row)">发布</el-button>
                  <el-button v-if="row.status === 'DRAFT'" link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!loading && !examList.length" class="empty-wrap">
            <el-empty description="暂无试卷，点击右上角「新建试卷」开始创建">
              <template #image><div class="empty-deco">📝</div></template>
            </el-empty>
          </div>
          <div v-if="total > pageSize" class="pagination-wrap">
            <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" @size-change="loadExams" @current-change="loadExams" />
          </div>
        </div>
      </div>
    </section>

    <!-- 新建/编辑试卷对话框 -->
    <el-dialog v-model="formVisible" :title="isEdit ? '编辑试卷' : '新建试卷'" width="640px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-form-item label="所属课程" prop="courseId">
          <el-select v-model="form.courseId" filterable clearable placeholder="选择课程" style="width: 100%">
            <el-option v-for="c in courseList" :key="c.id" :label="c.name || c.title" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="试卷标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入试卷标题" maxlength="80" show-word-limit />
        </el-form-item>
        <el-form-item label="说明">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="试卷说明（可选）" maxlength="200" show-word-limit />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="考试时长(分钟)" prop="duration">
              <el-input-number v-model="form.duration" :min="10" :max="300" :step="5" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="总分" prop="totalScore">
              <el-input-number v-model="form.totalScore" :min="1" :max="500" :step="5" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">{{ isEdit ? '保存修改' : '创建试卷' }}</el-button>
      </template>
    </el-dialog>

    <!-- 查看题目对话框 -->
    <el-dialog v-model="questionsVisible" title="试卷题目" width="720px" append-to-body>
      <template v-if="currentExam">
        <div class="dialog-head">
          <span class="dialog-title">{{ currentExam.title }}</span>
          <el-tag size="small" :type="getStatusType(currentExam.status)" effect="light">{{ getStatusText(currentExam.status) }}</el-tag>
        </div>
        <el-table :data="examQuestions" size="small" border max-height="380">
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column label="题目" min-width="320" show-overflow-tooltip>
            <template #default="{ row }">{{ row.content || row.stem || row.title || '—' }}</template>
          </el-table-column>
          <el-table-column label="题型" width="90" align="center">
            <template #default="{ row }"><el-tag size="small" type="info">{{ getQuestionTypeName(row.type) }}</el-tag></template>
          </el-table-column>
          <el-table-column prop="score" label="分值" width="80" align="center" />
        </el-table>
        <el-empty v-if="!examQuestions.length" description="暂无题目" />
      </template>
    </el-dialog>

    <!-- 统计对话框 -->
    <el-dialog v-model="statsVisible" v-loading="statsLoading" title="试卷统计" width="560px" append-to-body>
      <template v-if="examStats">
        <div class="stats-grid">
          <div class="stats-item"><span class="label">参考人数</span><span class="value primary">{{ examStats.attemptCount ?? examStats.participantCount ?? '—' }}</span></div>
          <div class="stats-item"><span class="label">平均分</span><span class="value success">{{ examStats.avgScore ?? '—' }}</span></div>
          <div class="stats-item"><span class="label">最高分</span><span class="value warning">{{ examStats.maxScore ?? '—' }}</span></div>
          <div class="stats-item"><span class="label">最低分</span><span class="value danger">{{ examStats.minScore ?? '—' }}</span></div>
          <div class="stats-item"><span class="label">及格率</span><span class="value">{{ formatRate(examStats.passRate) }}</span></div>
          <div class="stats-item"><span class="label">提交数</span><span class="value">{{ examStats.submitCount ?? '—' }}</span></div>
        </div>
        <el-empty v-if="!hasStats(examStats)" description="暂无统计数据" />
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search } from '@element-plus/icons-vue'
import examApi from '@/api/exam'
import courseApi from '@/api/course'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'

const loading = ref(false)
const examList = ref([])
const courseList = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const filters = reactive({ courseId: '' })

const formVisible = ref(false)
const isEdit = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref(null)
const form = reactive({ courseId: '', title: '', description: '', duration: 90, totalScore: 100 })
const rules = {
  courseId: [{ required: true, message: '请选择所属课程', trigger: 'change' }],
  title: [{ required: true, message: '请输入试卷标题', trigger: 'blur' }],
  duration: [{ required: true, message: '请设置考试时长', trigger: 'blur' }],
  totalScore: [{ required: true, message: '请设置总分', trigger: 'blur' }],
}

const questionsVisible = ref(false)
const currentExam = ref(null)
const examQuestions = ref([])

const statsVisible = ref(false)
const statsLoading = ref(false)
const examStats = ref(null)

function countBy(status) { return examList.value.filter((e) => e.status === status).length }

onMounted(() => {
  loadCourses()
  loadExams()
})

async function loadCourses() {
  try {
    const r = await courseApi.getMyCourses()
    const body = unwrap(r)
    const list = body?.records || body?.list || body || []
    courseList.value = Array.isArray(list) ? list : []
  } catch (e) {
    logger.error('加载课程失败', e)
  }
}

async function loadExams() {
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize.value }
    if (filters.courseId) params.courseId = filters.courseId
    const r = await examApi.getExamPaperList(params)
    const body = unwrap(r)
    const list = body?.records || body?.list || body || []
    examList.value = Array.isArray(list) ? list : []
    total.value = body?.total ?? body?.count ?? examList.value.length
  } catch (e) {
    logger.error('加载试卷列表失败', e)
    ElMessage.error('加载试卷列表失败')
  } finally {
    loading.value = false
  }
}

function handleFilterChange() {
  page.value = 1
  loadExams()
}

function resetForm() {
  Object.assign(form, {
    courseId: filters.courseId || '',
    title: '',
    description: '',
    duration: 90,
    totalScore: 100,
  })
}

function openCreateDialog() {
  isEdit.value = false
  editingId.value = null
  resetForm()
  formVisible.value = true
}

function openEditDialog(row) {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(form, {
    courseId: row.courseId || filters.courseId || '',
    title: row.title || '',
    description: row.description || '',
    duration: row.duration || 90,
    totalScore: row.totalScore || 100,
  })
  formVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const payload = {
      courseId: form.courseId,
      title: form.title,
      description: form.description,
      duration: form.duration,
      totalScore: form.totalScore,
    }
    if (isEdit.value) {
      await examApi.updateExam(editingId.value, payload)
      ElMessage.success('试卷已更新')
    } else {
      await examApi.createExam(payload)
      ElMessage.success('试卷已创建')
    }
    formVisible.value = false
    loadExams()
  } catch (e) {
    logger.error('保存试卷失败', e)
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

async function handlePublish(row) {
  try {
    await ElMessageBox.confirm(`确定发布试卷「${row.title}」？`, '发布确认', { type: 'info' })
    await examApi.publishExam(row.id)
    ElMessage.success('试卷已发布')
    loadExams()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.response?.data?.message || '发布失败')
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除试卷「${row.title}」？此操作不可恢复`, '删除确认', { type: 'warning' })
    await examApi.deleteExam(row.id)
    ElMessage.success('试卷已删除')
    loadExams()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error('删除失败')
  }
}

async function openQuestionsDialog(row) {
  currentExam.value = row
  examQuestions.value = row.questions ? row.questions : []
  questionsVisible.value = true
  if (!examQuestions.value.length) {
    try {
      const r = await examApi.getExamQuestions(row.id)
      const body = unwrap(r)
      examQuestions.value = Array.isArray(body) ? body : body?.list || body?.records || []
    } catch (e) {
      logger.error('加载题目失败', e)
      examQuestions.value = []
    }
  }
}

async function openStatsDialog(row) {
  statsVisible.value = true
  statsLoading.value = true
  examStats.value = null
  try {
    const r = await examApi.getExamStatistics(row.id)
    const body = unwrap(r)
    examStats.value = body && typeof body === 'object' ? body : null
  } catch (e) {
    logger.error('加载统计失败', e)
    examStats.value = null
  } finally {
    statsLoading.value = false
  }
}

function hasStats(s) {
  return s && (s.attemptCount != null || s.avgScore != null || s.maxScore != null || s.minScore != null || s.passRate != null)
}
function formatRate(v) {
  if (v == null) return '—'
  return typeof v === 'number' && v <= 1.0001 && v > 0 ? Math.round(v * 100) + '%' : v + '%'
}
function getStatusType(s) {
  const m = { DRAFT: 'info', PUBLISHED: 'success', ENDED: 'danger' }
  return m[s] || 'info'
}
function getStatusText(s) {
  const m = { DRAFT: '草稿', PUBLISHED: '已发布', ENDED: '已结束' }
  return m[s] || s || '草稿'
}
function getQuestionTypeName(t) {
  const m = { SINGLE_CHOICE: '单选', MULTIPLE_CHOICE: '多选', TRUE_FALSE: '判断', FILL_BLANK: '填空', SHORT_ANSWER: '简答', ESSAY: '简答', CODING: '编程' }
  return m[t] || t || '未知'
}
</script>

<style scoped>
.exam-papers { padding: 4px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; padding-bottom: 14px; margin-bottom: 6px; border-bottom: 1px solid var(--line); }
.toolbar-left { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.table-area { min-height: 200px; }
.title-cell { font-weight: 500; color: var(--ink); }
.action-row { display: flex; align-items: center; justify-content: center; gap: 2px; flex-wrap: nowrap; }
.empty-wrap { padding: 30px 0; }
.empty-deco { font-size: 38px; filter: grayscale(.2); }
.pagination-wrap { display: flex; justify-content: flex-end; padding-top: 16px; }
.dialog-head { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.dialog-title { font-size: 15px; font-weight: 600; color: var(--ink); }
.stats-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 14px; }
.stats-item { background: var(--surface-2); border: 1px solid var(--line); border-radius: var(--r-l3); padding: 16px; text-align: center; display: flex; flex-direction: column; gap: 6px; }
.stats-item .label { font-size: 13px; color: var(--ink-3); }
.stats-item .value { font-size: 22px; font-weight: 700; color: var(--ink); }
.stats-item .value.primary { color: var(--accent); }
.stats-item .value.success { color: var(--ok); }
.stats-item .value.warning { color: var(--warn); }
.stats-item .value.danger { color: var(--accent); }
</style>
