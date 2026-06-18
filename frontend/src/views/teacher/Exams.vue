﻿﻿﻿﻿﻿﻿﻿<template>
  <div class="exams-page">
    <!-- 工具栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>创建试卷
        </el-button>
        <el-select v-model="statusFilter" placeholder="状态筛选" clearable style="width:130px;margin-left:8px" @change="loadExamList">
          <el-option label="全部" value="" />
          <el-option label="草稿" value="DRAFT" />
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已结束" value="ENDED" />
        </el-select>
      </div>
      <div class="toolbar-right">
        <el-input v-model="searchText" placeholder="搜索试卷标题..." clearable style="width:240px" @keyup.enter="loadExamList" />
        <el-button @click="loadExamList">搜索</el-button>
      </div>
    </div>

    <!-- 试卷列表 -->
    <div class="content-card">
      <el-table :data="examList" style="width:100%" v-loading="loading" stripe>
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="title" label="试卷标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }"><span class="title-cell">{{ row.title }}</span></template>
        </el-table-column>
        <el-table-column prop="courseName" label="所属课程" min-width="140" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small" effect="light">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="时长(分)" width="90" align="center" />
        <el-table-column prop="totalScore" label="总分" width="80" align="center" />
        <el-table-column prop="attemptCount" label="参考人数" width="100" align="center" />
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="操作" width="280" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewDetail(row)">详情</el-button>
            <el-button link type="primary" size="small" @click="openEditDialog(row)" v-if="row.status === 'DRAFT'">编辑</el-button>
            <el-button link type="success" size="small" @click="handlePublish(row)" v-if="row.status === 'DRAFT'">发布</el-button>
            <el-button link type="warning" size="small" @click="handleCopy(row)">复制</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)" v-if="row.status === 'DRAFT'">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && examList.length === 0" description="暂无试卷数据" />
      <div class="pagination-wrap" v-if="total > pageSize">
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :total="total" layout="total, prev, pager, next" @current-change="loadExamList" />
      </div>
    </div>

    <!-- 创建/编辑试卷对话框 -->
    <el-dialog v-model="formDialogVisible" :title="isEdit ? '编辑试卷' : '创建试卷'" width="760px" destroy-on-close>
      <el-form ref="formRef" :model="examForm" :rules="formRules" label-width="100px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="试卷标题" prop="title">
              <el-input v-model="examForm.title" placeholder="输入试卷标题" maxlength="80" show-word-limit />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属课程" prop="courseId">
              <el-select v-model="examForm.courseId" placeholder="选择课程" style="width:100%" filterable>
                <el-option v-for="c in courseOptions" :key="c.id" :label="c.name" :value="c.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="说明">
          <el-input v-model="examForm.description" type="textarea" :rows="2" placeholder="试卷说明（可选）" maxlength="200" show-word-limit />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="考试时长" prop="duration">
              <el-input-number v-model="examForm.duration" :min="10" :max="300" :step="5" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="总分" prop="totalScore">
              <el-input-number v-model="examForm.totalScore" :min="1" :max="500" :step="5" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最大尝试次数">
              <el-input-number v-model="examForm.maxAttempts" :min="1" :max="10" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="开始时间">
              <el-date-picker v-model="examForm.startTime" type="datetime" placeholder="选择开始时间" style="width:100%" value-format="YYYY-MM-DD HH:mm:ss" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="截止时间">
              <el-date-picker v-model="examForm.endTime" type="datetime" placeholder="选择截止时间" style="width:100%" value-format="YYYY-MM-DD HH:mm:ss" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-divider content-position="left">题目管理</el-divider>
        <div class="questions-header">
          <span class="question-stat">已选 {{ selectedQuestions.length }} 道题，共 {{ totalSelectedScore }} 分</span>
          <el-button size="small" type="primary" plain @click="questionPickerVisible = true">从题库选题</el-button>
        </div>
        <el-table :data="selectedQuestions" style="width:100%" border v-if="selectedQuestions.length > 0" size="small">
          <el-table-column type="index" label="序号" width="60" />
          <el-table-column prop="content" label="题目内容" min-width="280" show-overflow-tooltip>
            <template #default="{ row }"><span>{{ row.content || row.stem || row.title }}</span></template>
          </el-table-column>
          <el-table-column prop="type" label="题型" width="90" align="center">
            <template #default="{ row }"><el-tag size="small" type="info">{{ getQuestionTypeName(row.type) }}</el-tag></template>
          </el-table-column>
          <el-table-column label="分值" width="130" align="center">
            <template #default="{ row }">
              <el-input-number v-model="row.score" :min="1" :max="50" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="操作" width="70" align="center">
            <template #default="{ $index }">
              <el-button link type="danger" size="small" @click="removeQuestion($index)">移除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="尚未添加题目，请从题库中选择" :image-size="60" />
        <el-divider content-position="left">设置</el-divider>
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="打乱题目">
              <el-switch v-model="examForm.shuffleQuestions" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="打乱选项">
              <el-switch v-model="examForm.shuffleOptions" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="显示分数">
              <el-switch v-model="examForm.showScore" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">{{ isEdit ? '保存修改' : '创建试卷' }}</el-button>
      </template>
    </el-dialog>

    <!-- 题库选题弹窗 -->
    <el-dialog v-model="questionPickerVisible" title="从题库选择题目" width="820px" append-to-body>
      <div class="search-form">
        <el-input v-model="questionSearch" placeholder="搜索题目内容..." clearable style="width:260px" />
        <el-select v-model="questionTypeFilter" placeholder="题型筛选" clearable style="width:140px">
          <el-option label="单选题" value="SINGLE_CHOICE" />
          <el-option label="多选题" value="MULTIPLE_CHOICE" />
          <el-option label="判断题" value="TRUE_FALSE" />
          <el-option label="填空题" value="FILL_BLANK" />
          <el-option label="简答题" value="ESSAY" />
        </el-select>
        <el-button type="primary" @click="loadQuestionBank">搜索</el-button>
      </div>
      <el-table :data="questionBank" ref="questionTableRef" style="width:100%" max-height="360" v-loading="questionLoading" stripe @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" :selectable="row => !isQuestionSelected(row.id)" />
        <el-table-column prop="content" label="题目内容" min-width="300" show-overflow-tooltip />
        <el-table-column prop="type" label="题型" width="95" align="center">
          <template #default="{ row }"><el-tag size="small" type="info">{{ getQuestionTypeName(row.type) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="difficulty" label="难度" width="75" align="center">
          <template #default="{ row }">
            <span :style="{ color: getDifficultyColor(row.difficulty) }">{{ getDifficultyText(row.difficulty) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="addSingleQuestion(row)" :disabled="isQuestionSelected(row.id)">添加</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="questionPickerVisible = false">关闭</el-button>
        <el-button type="primary" @click="confirmAddQuestions">批量添加选中 ({{ tempSelectedQuestions.length }})</el-button>
      </template>
    </el-dialog>

    <!-- 试卷详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="试卷详情" width="720px">
      <template v-if="currentExam">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="试卷标题">{{ currentExam.title }}</el-descriptions-item>
          <el-descriptions-item label="所属课程">{{ currentExam.courseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(currentExam.status)" size="small" effect="light">{{ getStatusText(currentExam.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="总分/时长">{{ currentExam.totalScore }}分 / {{ currentExam.duration }}分钟</el-descriptions-item>
          <el-descriptions-item label="参考人数">{{ currentExam.attemptCount || 0 }}人</el-descriptions-item>
          <el-descriptions-item label="平均分">{{ currentExam.avgScore != null ? currentExam.avgScore : '-' }}</el-descriptions-item>
          <el-descriptions-item label="最高分">{{ currentExam.maxScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="最低分">{{ currentExam.minScore ?? '-' }}</el-descriptions-item>
        </el-descriptions>

        <div class="detail-section">
          <h4 class="detail-title">题目预览</h4>
          <el-table :data="examDetailQuestions" style="width:100%" v-if="examDetailQuestions.length > 0" size="small" border>
            <el-table-column type="index" label="序号" width="60" />
            <el-table-column prop="content" label="题目内容" min-width="300" show-overflow-tooltip>
              <template #default="{ row }">{{ row.content || row.stem || row.title }}</template>
            </el-table-column>
            <el-table-column prop="type" label="题型" width="95" align="center">
              <template #default="{ row }"><el-tag size="small" type="info">{{ getQuestionTypeName(row.type) }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="score" label="分值" width="80" align="center" />
          </el-table>
          <el-empty v-else description="暂无题目数据" :image-size="60" />
        </div>
      </template>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-button type="success" @click="handlePublish(currentExam)" v-if="currentExam?.status === 'DRAFT'">发布试卷</el-button>
        <el-button type="warning" @click="handleCopy(currentExam)">复制试卷</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import examApi from '@/api/exam'
import questionApi from '@/api/question'
import courseApi from '@/api/course'

const loading = ref(false)
const examList = ref([])
const searchText = ref('')
const statusFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)

const formDialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref(null)
const courseOptions = ref([])

const examForm = reactive({
  title: '',
  courseId: null,
  description: '',
  duration: 90,
  totalScore: 100,
  startTime: '',
  endTime: '',
  shuffleQuestions: false,
  shuffleOptions: false,
  showScore: true,
  maxAttempts: 1
})

const formRules = {
  title: [{ required: true, message: '请输入试卷标题', trigger: 'blur' }],
  courseId: [{ required: true, message: '请选择所属课程', trigger: 'change' }],
  duration: [{ required: true, message: '请设置考试时长', trigger: 'blur' }],
  totalScore: [{ required: true, message: '请设置总分', trigger: 'blur' }]
}

const selectedQuestions = ref([])
const totalSelectedScore = computed(() => selectedQuestions.value.reduce((sum, q) => sum + (q.score || 0), 0))

const questionPickerVisible = ref(false)
const questionLoading = ref(false)
const questionBank = ref([])
const questionSearch = ref('')
const questionTypeFilter = ref('')
const tempSelectedQuestions = ref([])
const questionTableRef = ref(null)

const detailDialogVisible = ref(false)
const currentExam = ref(null)
const examDetailQuestions = ref([])

onMounted(() => {
  loadExamList()
  loadCourseOptions()
})

async function loadExamList() {
  loading.value = true
  try {
    const params = {
      page: currentPage.value,
      size: pageSize.value,
      keyword: searchText.value || undefined,
      status: statusFilter.value || undefined
    }
    const res = await examApi.getExamPaperList(params)
    const raw = res.data?.records || res.data?.list || res.data || res
    if (Array.isArray(raw)) {
      examList.value = raw
      total.value = raw.length
    } else {
      examList.value = Array.isArray(raw.list) ? raw.list : (Array.isArray(raw.records) ? raw.records : [])
      total.value = raw.total ?? raw.count ?? examList.value.length
    }
  } catch (e) {
    console.error('加载试卷列表失败:', e)
    ElMessage.error('加载试卷列表失败')
  } finally {
    loading.value = false
  }
}

async function loadCourseOptions() {
  try {
    const res = await courseApi.getMyCourses()
    const raw = res.data || res
    if (Array.isArray(raw)) {
      courseOptions.value = raw
    } else {
      courseOptions.value = Array.isArray(raw.list) ? raw.list : (Array.isArray(raw.records) ? raw.records : [])
    }
  } catch (e) {
    console.error('加载课程失败:', e)
  }
}

function openCreateDialog() {
  isEdit.value = false
  editingId.value = null
  Object.assign(examForm, {
    title: '', courseId: null, description: '', duration: 90, totalScore: 100,
    startTime: '', endTime: '', shuffleQuestions: false, shuffleOptions: false, showScore: true, maxAttempts: 1
  })
  selectedQuestions.value = []
  formDialogVisible.value = true
}

function openEditDialog(row) {
  isEdit.value = true
  editingId.value = row.id
  Object.assign(examForm, {
    title: row.title || '',
    courseId: row.courseId || null,
    description: row.description || '',
    duration: row.duration || 90,
    totalScore: row.totalScore || 100,
    startTime: row.startTime || '',
    endTime: row.endTime || '',
    shuffleQuestions: !!row.shuffleQuestions,
    shuffleOptions: !!row.shuffleOptions,
    showScore: row.showScore !== false,
    maxAttempts: row.maxAttempts || 1
  })
  selectedQuestions.value = row.questions ? row.questions.map(q => ({ ...q })) : []
  formDialogVisible.value = true
}

async function submitForm() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  if (selectedQuestions.value.length === 0) {
    ElMessage.warning('请至少添加一道题目')
    return
  }
  submitting.value = true
  try {
    const payload = {
      ...examForm,
      questions: selectedQuestions.value.map(q => ({
        questionId: q.id,
        score: q.score || q.defaultScore || 5,
        order: selectedQuestions.value.indexOf(q) + 1
      }))
    }
    if (isEdit.value) {
      await examApi.updateExamPaper(editingId.value, payload)
      ElMessage.success('更新成功')
    } else {
      await examApi.createExamPaper(payload)
      ElMessage.success('创建成功')
    }
    formDialogVisible.value = false
    loadExamList()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  } finally {
    submitting.value = false
  }
}

async function loadQuestionBank() {
  questionLoading.value = true
  try {
    const params = { size: 100 }
    if (questionSearch.value) params.keyword = questionSearch.value
    if (questionTypeFilter.value) params.type = questionTypeFilter.value

    let res
    if (questionApi.searchQuestions) {
      try {
        res = await questionApi.searchQuestions(params)
      } catch (_) {
        res = await questionApi.getQuestionList(params)
      }
    } else {
      res = await questionApi.getQuestionList(params)
    }
    const raw = res.data || res
    if (Array.isArray(raw)) {
      questionBank.value = raw
    } else {
      questionBank.value = Array.isArray(raw.list) ? raw.list : (Array.isArray(raw.records) ? raw.records : [])
    }
  } catch (e) {
    console.error('加载题库失败:', e)
    questionBank.value = []
  } finally {
    questionLoading.value = false
  }
}

function handleSelectionChange(rows) {
  tempSelectedQuestions.value = rows
}

function isQuestionSelected(id) {
  return selectedQuestions.value.some(q => q.id === id)
}

function addSingleQuestion(q) {
  if (isQuestionSelected(q.id)) return
  selectedQuestions.value.push({ ...q, score: q.defaultScore || q.score || 5 })
  ElMessage.success('已添加题目')
}

function removeQuestion(idx) {
  selectedQuestions.value.splice(idx, 1)
}

function confirmAddQuestions() {
  if (tempSelectedQuestions.value.length === 0) {
    ElMessage.info('请先在题库中勾选题目')
    return
  }
  let added = 0
  for (const q of tempSelectedQuestions.value) {
    if (!isQuestionSelected(q.id)) {
      selectedQuestions.value.push({ ...q, score: q.defaultScore || q.score || 5 })
      added++
    }
  }
  tempSelectedQuestions.value = []
  questionPickerVisible.value = false
  if (added > 0) ElMessage.success(`已添加 ${added} 道题目`)
  else ElMessage.info('所选题目均已存在')
}

async function viewDetail(row) {
  currentExam.value = row
  examDetailQuestions.value = row.questions ? row.questions : []
  detailDialogVisible.value = true
  if (!examDetailQuestions.value.length) {
    try {
      const res = await examApi.getExamQuestions(row.id)
      const data = res.data || res
      examDetailQuestions.value = Array.isArray(data) ? data : []
    } catch (e) {
      console.error('加载题目详情失败:', e)
    }
  }
}

async function handlePublish(row) {
  try {
    await ElMessageBox.confirm(`确定发布试卷「${row.title}」？`, '发布确认', { type: 'info' })
    await examApi.publishExam(row.id)
    ElMessage.success('发布成功')
    loadExamList()
    if (detailDialogVisible.value) detailDialogVisible.value = false
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e.response?.data?.message || '发布失败')
  }
}

async function handleCopy(row) {
  try {
    await ElMessageBox.confirm(`确定复制试卷「${row.title}」？`, '复制确认', { type: 'info' })
    await examApi.copyExam(row.id, { title: `${row.title} (副本)` })
    ElMessage.success('复制成功')
    loadExamList()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e.response?.data?.message || '复制失败')
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除试卷「${row.title}」？此操作不可恢复`, '删除确认', { type: 'warning' })
    await examApi.deleteExamPaper(row.id)
    ElMessage.success('删除成功')
    loadExamList()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error('删除失败')
  }
}

function getStatusType(status) {
  const map = { DRAFT: 'info', PUBLISHED: 'success', ENDED: 'danger' }
  return map[status] || 'info'
}

function getStatusText(status) {
  const map = { DRAFT: '草稿', PUBLISHED: '已发布', ENDED: '已结束' }
  return map[status] || status
}

function getQuestionTypeName(type) {
  const map = { SINGLE_CHOICE: '单选', MULTIPLE_CHOICE: '多选', TRUE_FALSE: '判断', FILL_BLANK: '填空', ESSAY: '简答' }
  return map[type] || type || '未知'
}

function getDifficultyColor(diff) {
  const map = { EASY: '#67C23A', MEDIUM: '#E6A23C', HARD: '#F56C6C' }
  return map[diff] || '#909399'
}

function getDifficultyText(diff) {
  const map = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }
  return map[diff] || diff || '-'
}
</script>

<style scoped>
.exams-page {
  padding: 16px;
  background: var(--ac-bg);
  min-height: calc(100vh - 40px);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: var(--ac-bg-elevated);
  border-radius: 8px;
  margin-bottom: 16px;
}

.toolbar-left, .toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.content-card {
  background: var(--ac-bg-elevated);
  border-radius: 8px;
  padding: 16px;
}

.title-cell {
  font-weight: 500;
  color: var(--ac-text-primary);
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  padding-top: 16px;
}

.questions-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 4px 12px;
}

.question-stat {
  font-size: 13px;
  color: var(--ac-text-secondary);
}

.search-form {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  align-items: center;
}

.detail-section {
  margin-top: 20px;
}

.detail-title {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 12px;
  color: var(--ac-text-primary);
}
</style>
