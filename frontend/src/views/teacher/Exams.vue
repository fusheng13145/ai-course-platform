﻿﻿﻿<template>
  <div class="fade-in">
    <!-- 工具栏 -->
    <div class="table-toolbar">
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
        <el-input v-model="searchText" placeholder="搜索试卷标题..." clearable style="width:240px" :prefix-icon="Search" @clear="loadExamList" @keyup.enter="loadExamList" />
        <el-button :icon="Search" @click="loadExamList">搜索</el-button>
      </div>
    </div>

    <!-- 试卷列表 -->
    <div class="content-card">
      <el-table :data="examList" style="width:100%" v-loading="loading"
        :header-cell-style="{ background:'var(--ac-bg)', color:'var(--ac-text-secondary)', fontWeight:600 }">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="试卷标题" min-width="180" show-overflow-tooltip>
          <template #default="{ row }"><span style="font-weight:500;color:var(--ac-text-primary)">{{ row.title }}</span></template>
        </el-table-column>
        <el-table-column prop="courseName" label="所属课程" min-width="120" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" size="small">{{ getStatusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="questionCount" label="题目数" width="80" align="center" />
        <el-table-column prop="totalScore" label="总分" width="70" align="center" />
        <el-table-column prop="duration" label="时长(分)" width="85" align="center" />
        <el-table-column prop="attemptCount" label="参考人数" width="90" align="center" />
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="260" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="viewDetail(row)">详情</el-button>
            <el-button text type="primary" size="small" @click="openEditDialog(row)" v-if="row.status === 'DRAFT'">编辑</el-button>
            <el-button text type="success" size="small" @click="handlePublish(row)" v-if="row.status === 'DRAFT'">发布</el-button>
            <el-button text type="warning" size="small" @click="handleCopy(row)">复制</el-button>
            <el-button text type="danger" size="small" @click="handleDelete(row)" v-if="row.status === 'DRAFT'">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && examList.length === 0" description="暂无试卷数据" />
      <div class="pagination-wrap" v-if="total > pageSize">
        <el-pagination v-model:current-page="currentPage" v-model:page-size="pageSize" :total="total" layout="total, prev, pager, next" @current-change="loadExamList" />
      </div>
    </div>

    <!-- 创建/编辑试卷对话框 -->
    <el-dialog v-model="formDialogVisible" :title="isEdit ? '编辑试卷' : '创建试卷'" width="720px" destroy-on-close>
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
          <el-col :span="12">
            <el-form-item label="考试时长" prop="duration">
              <el-input-number v-model="examForm.duration" :min="10" :max="300" :step="5" /> 分钟
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="总分" prop="totalScore">
              <el-input-number v-model="examForm.totalScore" :min="1" :max="500" :step="5" />
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

        <!-- 题目管理 -->
        <el-divider content-position="left">题目管理</el-divider>
        <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px">
          <span style="font-size:13px;color:var(--ac-text-muted)">已选 {{ selectedQuestions.length }} 道题，共 {{ totalSelectedScore }} 分</span>
          <el-button size="small" type="primary" plain @click="questionPickerVisible = true">从题库选题</el-button>
        </div>
        <div v-if="selectedQuestions.length > 0" class="selected-questions">
          <div v-for="(q, idx) in selectedQuestions" :key="q.id || idx" class="question-item">
            <div class="q-index">{{ idx + 1 }}</div>
            <div class="q-content">
              <span class="q-type-tag" :class="'type-' + (q.type || 'choice')">{{ getQuestionTypeName(q.type) }}</span>
              <span class="q-text">{{ q.content || q.stem || q.title || '未命名题目' }}</span>
            </div>
            <el-input-number v-model="q.score" :min="1" :max="50" size="small" style="width:90px" />
            <el-button text type="danger" size="small" @click="removeQuestion(idx)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
        <el-empty v-else description="尚未添加题目，请从题库中选择" :image-size="60" />

        <!-- 设置 -->
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
        <el-form-item label="最大尝试次数">
          <el-input-number v-model="examForm.maxAttempts" :min="1" :max="10" />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="formDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitting">{{ isEdit ? '保存修改' : '创建试卷' }}</el-button>
      </template>
    </el-dialog>

    <!-- 题库选题弹窗 -->
    <el-dialog v-model="questionPickerVisible" title="从题库选择题目" width="760px" append-to-body>
      <div class="search-form">
        <el-input v-model="questionSearch" placeholder="搜索题目内容..." clearable style="width:260px" :prefix-icon="Search" />
        <el-select v-model="questionTypeFilter" placeholder="题型筛选" clearable style="width:140px">
          <el-option label="单选题" value="SINGLE_CHOICE" />
          <el-option label="多选题" value="MULTIPLE_CHOICE" />
          <el-option label="判断题" value="TRUE_FALSE" />
          <el-option label="填空题" value="FILL_BLANK" />
          <el-option label="简答题" value="ESSAY" />
        </el-select>
        <el-button type="primary" @click="loadQuestionBank">搜索</el-button>
      </div>
      <el-table :data="filteredQuestionBank" style="width:100%" max-height="360" v-loading="questionLoading"
        @selection-change="handleQuestionSelectionChange" :header-cell-style="{ background:'var(--ac-bg)', color:'var(--ac-text-secondary)', fontWeight:600 }">
        <el-table-column type="selection" width="45" :selectable="row => !isQuestionSelected(row.id)" />
        <el-table-column prop="content" label="题目内容" min-width="280" show-overflow-tooltip />
        <el-table-column prop="type" label="题型" width="95" align="center">
          <template #default="{ row }"><el-tag size="small" type="info">{{ getQuestionTypeName(row.type) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="difficulty" label="难度" width="75" align="center">
          <template #default="{ row }">
            <span :style="{ color: getDifficultyColor(row.difficulty) }">{{ getDifficultyText(row.difficulty) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="addSingleQuestion(row)" :disabled="isQuestionSelected(row.id)">添加</el-button>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="questionPickerVisible = false">关闭</el-button>
        <el-button type="primary" @click="confirmAddQuestions">确认添加 ({{ tempSelectedQuestions.length }})</el-button>
      </template>
    </el-dialog>

    <!-- 试卷详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="试卷详情" width="700px">
      <template v-if="currentExam">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="试卷标题">{{ currentExam.title }}</el-descriptions-item>
          <el-descriptions-item label="所属课程">{{ currentExam.courseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusType(currentExam.status)" size="small">{{ getStatusText(currentExam.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="总分/时长">{{ currentExam.totalScore }}分 / {{ currentExam.duration }}分钟</el-descriptions-item>
          <el-descriptions-item label="参考人数">{{ currentExam.attemptCount || 0 }}人</el-descriptions-item>
          <el-descriptions-item label="平均分">{{ currentExam.avgScore != null ? currentExam.avgScore : '-' }}</el-descriptions-item>
          <el-descriptions-item label="最高分">{{ currentExam.maxScore ?? '-' }}</el-descriptions-item>
          <el-descriptions-item label="最低分">{{ currentExam.minScore ?? '-' }}</el-descriptions-item>
        </el-descriptions>

        <!-- 题目预览 -->
        <div style="margin-top:20px">
          <h4 style="margin-bottom:12px;font-size:14px;color:var(--ac-text-primary)">题目预览</h4>
          <div v-if="examDetailQuestions && examDetailQuestions.length > 0">
            <div v-for="(q, i) in examDetailQuestions" :key="i" class="detail-question-item">
              <span class="q-num">{{ i + 1 }}.</span>
              <span class="q-type-badge">{{ getQuestionTypeName(q.type) }}</span>
              <span class="q-stem">{{ q.content || q.stem || q.title }}</span>
              <span class="q-score-val">{{ q.score }}分</span>
            </div>
          </div>
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
import { Search, Plus, Delete } from '@element-plus/icons-vue'
import examApi from '@/api/exam'
import questionApi from '@/api/question'
import courseApi from '@/api/course'

// 列表状态
const loading = ref(false)
const examList = ref([])
const searchText = ref('')
const statusFilter = ref('')
const currentPage = ref(1)
const pageSize = ref(15)
const total = ref(0)

// 表单对话框
const formDialogVisible = ref(false)
const isEdit = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref(null)
const courseOptions = ref([])

// 试题表单
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

// 已选题目
const selectedQuestions = ref([])
const totalSelectedScore = computed(() => selectedQuestions.value.reduce((sum, q) => sum + (q.score || 0), 0))

// 题库选择器
const questionPickerVisible = ref(false)
const questionLoading = ref(false)
const questionBank = ref([])
const questionSearch = ref('')
const questionTypeFilter = ref('')
const tempSelectedQuestions = ref([])

// 详情对话框
const detailDialogVisible = ref(false)
const currentExam = ref(null)
const examDetailQuestions = ref([])

onMounted(() => {
  loadExamList()
  loadCourseOptions()
})

// 加载试卷列表
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
    const rawData = res.data?.records || res.data?.list || res.data || res
    const data = Array.isArray(rawData) ? { list: rawData } : (rawData || {})
    examList.value = Array.isArray(data.list) ? data.list : Array.isArray(rawData) ? rawData : []
    total.value = data.total ?? data.count ?? examList.value.length
  } catch (e) {
    console.error('加载试卷列表失败:', e)
  } finally {
    loading.value = false
  }
}

// 加载课程下拉选项
async function loadCourseOptions() {
  try {
    const res = await courseApi.getMyCourses()
    const data = res.data?.list || res.data || res || []
    courseOptions.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.error('加载课程失败:', e)
  }
}

// 打开创建对话框
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

// 打开编辑对话框
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
    shuffleQuestions: row.shuffleQuestions || false,
    shuffleOptions: row.shuffleOptions || false,
    showScore: row.showScore !== false,
    maxAttempts: row.maxAttempts || 1
  })
  // 加载已有题目
  selectedQuestions.value = row.questions ? row.questions.map(q => ({ ...q })) : []
  formDialogVisible.value = true
}

// 提交表单
async function submitForm() {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
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
  })
}

// 加载题库（增强：使用 searchQuestions API 支持关键词和类型筛选）
async function loadQuestionBank() {
  questionLoading.value = true
  try {
    const params = { size: 100 }
    if (questionSearch.value) params.keyword = questionSearch.value
    if (questionTypeFilter.value) params.type = questionTypeFilter.value

    // 优先使用搜索 API，回退到列表 API
    let res
    if (params.keyword || params.type) {
      res = await questionApi.searchQuestions(params)
    } else {
      res = await questionApi.getQuestionList(params)
    }
    const data = res.data?.list || res.data || res || []
    questionBank.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.error('加载题库失败:', e)
    // 搜索 API 失败时回退到基础列表
    try {
      const res = await questionApi.getQuestionList({ size: 100 })
      const data = res.data?.list || res.data || res || []
      questionBank.value = Array.isArray(data) ? data : []
    } catch (e2) {
      questionBank.value = []
    }
  } finally {
    questionLoading.value = false
  }
}

// 题库表格过滤
const filteredQuestionBank = computed(() => questionBank.value)

function handleQuestionSelectionChange(rows) {
  tempSelectedQuestions.value = rows
}

function isQuestionSelected(id) {
  return selectedQuestions.value.some(q => q.id === id)
}

function addSingleQuestion(q) {
  if (isQuestionSelected(q.id)) return
  selectedQuestions.value.push({ ...q, score: q.defaultScore || 5 })
  ElMessage.success(`已添加: ${q.content?.slice(0, 20)}...`)
}

function removeQuestion(idx) {
  selectedQuestions.value.splice(idx, 1)
}

function confirmAddQuestions() {
  for (const q of tempSelectedQuestions.value) {
    if (!isQuestionSelected(q.id)) {
      selectedQuestions.value.push({ ...q, score: q.defaultScore || 5 })
    }
  }
  tempSelectedQuestions.value = []
  questionPickerVisible.value = false
  ElMessage.success(`已添加 ${tempSelectedQuestions.value.length} 道题目`)
}

// 查看详情
async function viewDetail(row) {
  currentExam.value = row
  detailDialogVisible.value = true
  // 尝试加载题目详情
  examDetailQuestions.value = []
  try {
    const res = await examApi.getExamQuestions(row.id)
    const data = res.data || res || []
    examDetailQuestions.value = Array.isArray(data) ? data : []
  } catch (e) {
    console.error('加载题目详情失败:', e)
  }
}

// 发布
async function handlePublish(row) {
  try {
    await ElMessageBox.confirm(`确定发布试卷「${row.title}」？`, '发布确认', { type: 'info' })
    await examApi.publishExam(row.id)
    ElMessage.success('发布成功')
    loadExamList()
    if (detailDialogVisible.value) detailDialogVisible.value = false
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || '发布失败')
  }
}

// 复制
async function handleCopy(row) {
  try {
    await ElMessageBox.confirm(`确定复制试卷「${row.title}」？`, '复制确认', { type: 'info' })
    await examApi.copyExam(row.id, { title: `${row.title}(副本)` })
    ElMessage.success('复制成功，已创建副本')
    loadExamList()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || '复制失败')
  }
}

// 删除
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除试卷「${row.title}」？此操作不可恢复`, '删除确认', { type: 'warning' })
    await examApi.deleteExamPaper(row.id)
    ElMessage.success('删除成功')
    loadExamList()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

// 辅助函数
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
  const map = { EASY: '#8BA888', MEDIUM: '#CBB58A', HARD: '#C48080' }
  return map[diff] || '#928B83'
}

function getDifficultyText(diff) {
  const map = { EASY: '简单', MEDIUM: '中等', HARD: '困难' }
  return map[diff] || diff || '-'
}
</script>

<style scoped>
.pagination-wrap {
  display: flex;
  justify-content: center;
  padding-top: var(--ac-spacing-md);
}

.selected-questions {
  max-height: 280px;
  overflow-y: auto;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-sm);
  padding: 8px;
}

.question-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-bottom: 1px solid var(--ac-border);
  transition: background var(--ac-transition-fast);
  border-radius: 6px;
  margin: 3px 0;
}

.question-item:last-child {
  border-bottom: none;
}

.question-item:hover {
  background: var(--ac-bg-hover);
}

.q-index {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ac-primary-bg);
  color: var(--ac-primary);
  border-radius: 50%;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.q-content {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.q-type-tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  white-space: nowrap;
  flex-shrink: 0;
}

.type-SINGLE_CHOICE { background: rgba(122,139,165,0.12); color: #7A8BA5; }
.type-MULTIPLE_CHOICE { background: rgba(184,154,138,0.12); color: #B89A8A; }
.type-TRUE_FALSE { background: rgba(139,168,136,0.12); color: #8BA888; }
.type-FILL_BLANK { background: rgba(203,181,138,0.12); color: #CBB58A; }
.type-ESSAY { background: rgba(196,128,128,0.12); color: #C48080; }

.q-text {
  font-size: 13px;
  color: var(--ac-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.detail-question-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--ac-border);
  font-size: 13px;
}

.detail-question-item:last-child {
  border-bottom: none;
}

.q-num {
  font-weight: 600;
  color: var(--ac-primary);
  flex-shrink: 0;
}

.q-type-badge {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background: rgba(122,139,165,0.12);
  color: var(--ac-primary);
  flex-shrink: 0;
}

.q-stem {
  flex: 1;
  color: var(--ac-text-secondary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.q-score-val {
  color: var(--ac-warning);
  font-weight: 500;
  flex-shrink: 0;
}
</style>
