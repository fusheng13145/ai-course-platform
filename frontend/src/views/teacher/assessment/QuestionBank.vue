<template>
  <div class="question-bank fade-in">
    <PageHeader title="题库管理" subtitle="维护课程题目，支持 AI 智能出题与审核流转" icon="layers" icon-bg="info">
      <el-button @click="openAIDialog"><el-icon><MagicStick /></el-icon> AI 生成</el-button>
      <el-button type="primary" @click="openCreateDialog"><el-icon><Plus /></el-icon> 新建题目</el-button>
    </PageHeader>

    <div class="stat-grid">
      <StatCard label="题目总数" :value="total" icon="layers" tone="var(--accent-soft)" tone-color="var(--accent)" />
      <StatCard label="待审核" :value="countByReview('PENDING')" icon="warn" tone="var(--warn-soft)" tone-color="var(--warn)" />
      <StatCard label="已通过" :value="countByReview('APPROVED')" icon="checks" tone="var(--ok-soft)" tone-color="var(--ok)" />
      <StatCard label="已驳回" :value="countByReview('REJECTED')" icon="edit" tone="var(--accent-soft)" tone-color="var(--accent)" />
    </div>

    <section class="l1-card">
      <div class="l1-head">
        <span class="l1-ico" style="background:var(--info-soft);color:var(--info)">
          <svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="m12 2 8.5 4.7L12 11.4 3.5 6.7 12 2zM3.5 12l8.5 4.7 8.5-4.7M3.5 17.3l8.5 4.7 8.5-4.7"/></svg>
        </span>
        <div><div class="l1-title">题目列表</div><div class="l1-sub">支持题型、难度与关键词多维筛选</div></div>
      </div>
      <div class="l1-body">
        <div class="toolbar">
          <div class="toolbar-left">
            <el-select v-model="filters.courseId" placeholder="全部课程" clearable filterable style="width: 200px" @change="handleFilterChange">
              <el-option v-for="c in courseList" :key="c.id" :label="c.name || c.title" :value="c.id" />
            </el-select>
            <el-select v-model="filters.type" placeholder="题型" clearable style="width: 140px" @change="handleFilterChange">
              <el-option label="全部" value="" />
              <el-option label="单选题" value="SINGLE_CHOICE" />
              <el-option label="多选题" value="MULTIPLE_CHOICE" />
              <el-option label="判断题" value="TRUE_FALSE" />
              <el-option label="填空题" value="FILL_BLANK" />
              <el-option label="简答题" value="SHORT_ANSWER" />
              <el-option label="编程题" value="CODING" />
            </el-select>
            <el-select v-model="filters.difficulty" placeholder="难度" clearable style="width: 130px" @change="handleFilterChange">
              <el-option label="全部" value="" />
              <el-option label="简单" value="EASY" />
              <el-option label="中等" value="MEDIUM" />
              <el-option label="困难" value="HARD" />
            </el-select>
            <el-input v-model="filters.keyword" placeholder="搜索题干..." clearable style="width: 240px" @keyup.enter="handleFilterChange" @clear="handleFilterChange">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-button type="primary" @click="handleFilterChange"><el-icon><Search /></el-icon>查询</el-button>
          </div>
        </div>

        <div v-loading="loading" class="table-area">
          <el-table :data="questionList" style="width: 100%">
            <el-table-column label="题干" min-width="320" show-overflow-tooltip>
              <template #default="{ row }"><span class="stem-cell">{{ row.content || row.stem || '—' }}</span></template>
            </el-table-column>
            <el-table-column label="类型" width="96" align="center">
              <template #default="{ row }">
                <el-tag size="small" effect="light" :type="getTypeTag(row.type)">{{ getTypeText(row.type) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="难度" width="100" align="center">
              <template #default="{ row }"><span :style="{ color: getDifficultyColor(row.difficulty), fontWeight: 600 }">{{ getDifficultyText(row.difficulty) }}</span></template>
            </el-table-column>
            <el-table-column prop="courseName" label="所属课程" min-width="140" show-overflow-tooltip />
            <el-table-column label="审核" width="110" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="getReviewTag(row.reviewStatus)" effect="light">{{ getReviewText(row.reviewStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="240" fixed="right" align="center">
              <template #default="{ row }">
                <div class="action-row">
                  <el-button link type="primary" size="small" @click="openEditDialog(row)">编辑</el-button>
                  <el-button link type="warning" size="small" @click="openReviewDialog(row)">审核</el-button>
                  <el-button link type="success" size="small" @click="handleCopy(row)">复制</el-button>
                  <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
          <div v-if="!loading && !questionList.length" class="empty-wrap">
            <el-empty description="题库暂无题目，点击右上角「新建题目」或「AI 生成」">
              <template #image><div class="empty-deco">📚</div></template>
            </el-empty>
          </div>
          <div v-if="total > pageSize" class="pagination-wrap">
            <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" @size-change="loadQuestions" @current-change="loadQuestions" />
          </div>
        </div>
      </div>
    </section>

    <!-- 新建/编辑题目对话框 -->
    <el-dialog v-model="formVisible" :title="isEdit ? '编辑题目' : '新建题目'" width="720px" append-to-body destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="题型" prop="type">
              <el-select v-model="form.type" style="width: 100%">
                <el-option label="单选题" value="SINGLE_CHOICE" />
                <el-option label="多选题" value="MULTIPLE_CHOICE" />
                <el-option label="判断题" value="TRUE_FALSE" />
                <el-option label="填空题" value="FILL_BLANK" />
                <el-option label="简答题" value="SHORT_ANSWER" />
                <el-option label="编程题" value="CODING" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="难度" prop="difficulty">
              <el-select v-model="form.difficulty" style="width: 100%">
                <el-option label="简单" value="EASY" />
                <el-option label="中等" value="MEDIUM" />
                <el-option label="困难" value="HARD" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="所属课程" prop="courseId">
          <el-select v-model="form.courseId" filterable clearable placeholder="选择课程（可选）" style="width: 100%">
            <el-option v-for="c in courseList" :key="c.id" :label="c.name || c.title" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="题干" prop="content">
          <el-input v-model="form.content" type="textarea" :rows="3" placeholder="请输入题干内容" />
        </el-form-item>
        <el-form-item label="参考答案" prop="answer">
          <el-input v-model="form.answer" type="textarea" :rows="2" placeholder="请输入参考答案" />
        </el-form-item>
        <el-form-item label="解析">
          <el-input v-model="form.explanation" type="textarea" :rows="2" placeholder="可选：题目解析" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">{{ isEdit ? '保存修改' : '创建题目' }}</el-button>
      </template>
    </el-dialog>

    <!-- 审核对话框 -->
    <el-dialog v-model="reviewVisible" title="题目审核" width="520px" append-to-body destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="审核结果">
          <el-radio-group v-model="reviewForm.status">
            <el-radio value="APPROVED">通过</el-radio>
            <el-radio value="REJECTED">驳回</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见">
          <el-input v-model="reviewForm.comment" type="textarea" :rows="3" placeholder="可选：填写审核意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="reviewVisible = false">取消</el-button>
        <el-button type="primary" :loading="reviewing" @click="submitReview">提交审核</el-button>
      </template>
    </el-dialog>

    <!-- AI 生成对话框 -->
    <el-dialog v-model="aiVisible" title="AI 智能出题" width="600px" append-to-body destroy-on-close>
      <el-alert title="AI 将依据课程、题型、难度与知识点自动生成题目" type="info" :closable="false" show-icon style="margin-bottom: 18px" />
      <el-form label-position="top">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="所属课程">
              <el-select v-model="aiForm.courseId" filterable clearable placeholder="选择课程（可选）" style="width: 100%">
                <el-option v-for="c in courseList" :key="c.id" :label="c.name || c.title" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="题型">
              <el-select v-model="aiForm.type" style="width: 100%">
                <el-option label="单选题" value="SINGLE_CHOICE" />
                <el-option label="多选题" value="MULTIPLE_CHOICE" />
                <el-option label="判断题" value="TRUE_FALSE" />
                <el-option label="填空题" value="FILL_BLANK" />
                <el-option label="简答题" value="SHORT_ANSWER" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="难度">
              <el-select v-model="aiForm.difficulty" style="width: 100%">
                <el-option label="简单" value="EASY" />
                <el-option label="中等" value="MEDIUM" />
                <el-option label="困难" value="HARD" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="知识点">
              <el-select v-model="aiForm.knowledgePoints" multiple filterable allow-create default-first-option placeholder="输入知识点后回车" style="width: 100%"></el-select>
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="aiVisible = false">取消</el-button>
        <el-button type="primary" :loading="aiGenerating" @click="generateByAI"><el-icon><MagicStick /></el-icon>开始生成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Search, MagicStick } from '@element-plus/icons-vue'
import questionApi from '@/api/question'
import courseApi from '@/api/course'
import PageHeader from '@/components/PageHeader.vue'
import StatCard from '@/components/ui/StatCard.vue'

const loading = ref(false)
const questionList = ref([])
const courseList = ref([])
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const filters = reactive({ courseId: '', type: '', difficulty: '', keyword: '' })

const formVisible = ref(false)
const isEdit = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref(null)
const form = reactive({
  type: 'SINGLE_CHOICE',
  difficulty: 'MEDIUM',
  courseId: '',
  content: '',
  answer: '',
  explanation: '',
})
const rules = {
  type: [{ required: true, message: '请选择题型', trigger: 'change' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
  content: [{ required: true, message: '请输入题干', trigger: 'blur' }],
}

const reviewVisible = ref(false)
const reviewing = ref(false)
const reviewRow = ref(null)
const reviewForm = reactive({ status: 'APPROVED', comment: '' })

const aiVisible = ref(false)
const aiGenerating = ref(false)
const aiForm = reactive({
  courseId: '',
  type: 'SINGLE_CHOICE',
  difficulty: 'MEDIUM',
  knowledgePoints: [],
})

onMounted(() => {
  loadCourses()
  loadQuestions()
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

async function loadQuestions() {
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize.value }
    if (filters.courseId) params.courseId = filters.courseId
    if (filters.type) params.type = filters.type
    if (filters.difficulty) params.difficulty = filters.difficulty
    if (filters.keyword) params.keyword = filters.keyword

    const r = await questionApi.getQuestionList(params)
    const body = unwrap(r)
    const list = body?.records || body?.list || body || []
    questionList.value = Array.isArray(list) ? list : []
    total.value = body?.total ?? body?.count ?? questionList.value.length
  } catch (e) {
    logger.error('加载题目列表失败', e)
    ElMessage.error('加载题目列表失败')
  } finally {
    loading.value = false
  }
}

function countByReview(status) {
  return questionList.value.filter((q) => (q.reviewStatus || 'PENDING') === status).length
}

function handleFilterChange() {
  page.value = 1
  loadQuestions()
}

function resetForm() {
  Object.assign(form, {
    type: 'SINGLE_CHOICE',
    difficulty: 'MEDIUM',
    courseId: filters.courseId || '',
    content: '',
    answer: '',
    explanation: '',
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
    type: row.type || 'SINGLE_CHOICE',
    difficulty: row.difficulty || 'MEDIUM',
    courseId: row.courseId || filters.courseId || '',
    content: row.content || row.stem || '',
    answer: row.answer || '',
    explanation: row.explanation || row.analysis || '',
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
      type: form.type,
      difficulty: form.difficulty,
      courseId: form.courseId || undefined,
      content: form.content,
      answer: form.answer,
      explanation: form.explanation,
    }
    if (isEdit.value) {
      await questionApi.updateQuestion(editingId.value, payload)
      ElMessage.success('题目已更新')
    } else {
      await questionApi.createQuestion(payload)
      ElMessage.success('题目已创建')
    }
    formVisible.value = false
    loadQuestions()
  } catch (e) {
    logger.error('保存题目失败', e)
    ElMessage.error(e?.response?.data?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除该题目？此操作不可恢复`, '删除确认', { type: 'warning' })
    await questionApi.deleteQuestion(row.id)
    ElMessage.success('题目已删除')
    loadQuestions()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error('删除失败')
  }
}

async function handleCopy(row) {
  try {
    await ElMessageBox.confirm(`确定复制该题目？`, '复制确认', { type: 'info' })
    await questionApi.copyQuestion(row.id)
    ElMessage.success('题目已复制')
    loadQuestions()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error('复制失败')
  }
}

function openReviewDialog(row) {
  reviewRow.value = row
  reviewForm.status = 'APPROVED'
  reviewForm.comment = ''
  reviewVisible.value = true
}

async function submitReview() {
  if (!reviewRow.value) return
  reviewing.value = true
  try {
    await questionApi.reviewQuestion(reviewRow.value.id, {
      status: reviewForm.status,
      comment: reviewForm.comment,
    })
    ElMessage.success('审核已提交')
    reviewVisible.value = false
    loadQuestions()
  } catch (e) {
    logger.error('审核失败', e)
    ElMessage.error(e?.response?.data?.message || '审核失败')
  } finally {
    reviewing.value = false
  }
}

function openAIDialog() {
  aiForm.courseId = filters.courseId || ''
  aiForm.type = filters.type || 'SINGLE_CHOICE'
  aiForm.difficulty = filters.difficulty || 'MEDIUM'
  aiForm.knowledgePoints = []
  aiVisible.value = true
}

async function generateByAI() {
  aiGenerating.value = true
  try {
    const payload = {
      courseId: aiForm.courseId || undefined,
      type: aiForm.type,
      difficulty: aiForm.difficulty,
      knowledgePointNames: aiForm.knowledgePoints || [],
    }
    const r = await questionApi.generateAndSaveByAI(payload)
    const body = unwrap(r)
    const generated = body?.questions || body?.list || body || []
    if (Array.isArray(generated) && generated.length) {
      ElMessage.success(`AI 成功生成 ${generated.length} 道题目`)
      aiVisible.value = false
      loadQuestions()
    } else {
      ElMessage.warning('未生成有效题目')
    }
  } catch (e) {
    logger.error('AI 生成题目失败', e)
    ElMessage.error(e?.response?.data?.message || 'AI 生成失败')
  } finally {
    aiGenerating.value = false
  }
}

function getTypeText(type) {
  const m = {
    SINGLE_CHOICE: '单选',
    MULTIPLE_CHOICE: '多选',
    TRUE_FALSE: '判断',
    FILL_BLANK: '填空',
    SHORT_ANSWER: '简答',
    ESSAY: '简答',
    CODING: '编程',
  }
  return m[type] || type || '未知'
}
function getTypeTag(type) {
  const m = {
    SINGLE_CHOICE: '',
    MULTIPLE_CHOICE: 'success',
    TRUE_FALSE: 'warning',
    FILL_BLANK: 'info',
    SHORT_ANSWER: '',
    ESSAY: '',
    CODING: 'danger',
  }
  return m[type] || ''
}
function getDifficultyText(d) {
  const m = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }
  return m[d] || d || '-'
}
function getDifficultyColor(d) {
  const m = { EASY: 'var(--ac-success)', MEDIUM: 'var(--ac-warning)', HARD: 'var(--ac-danger)' }
  return m[d] || 'var(--ac-text-muted)'
}
function getReviewText(s) {
  const m = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回' }
  return m[s] || '待审核'
}
function getReviewTag(s) {
  const m = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger' }
  return m[s] || 'info'
}
</script>

<style scoped>
.question-bank { padding: 4px; }
.toolbar { display: flex; justify-content: space-between; align-items: center; gap: 12px; flex-wrap: wrap; padding-bottom: 14px; margin-bottom: 6px; border-bottom: 1px solid var(--line); }
.toolbar-left { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.table-area { min-height: 200px; }
.stem-cell { color: var(--ac-text-primary); font-size: 13px; }
.action-row { display: flex; align-items: center; justify-content: center; gap: 2px; flex-wrap: nowrap; }
.empty-wrap { padding: 30px 0; }
.empty-deco { font-size: 38px; filter: grayscale(.2); }
.pagination-wrap { display: flex; justify-content: flex-end; padding-top: 16px; }
</style>
