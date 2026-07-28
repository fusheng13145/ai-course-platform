<template>
  <div class="course-question-bank fade-in">
    <div v-loading="loading">
      <!-- 筛选区 -->
      <div class="chart-card">
        <div class="toolbar">
          <el-select
            v-model="filters.type"
            placeholder="题目类型"
            clearable
            style="width: 150px"
            @change="onFilter"
          >
            <el-option v-for="o in TYPE_OPTS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-select
            v-model="filters.difficulty"
            placeholder="难度"
            clearable
            style="width: 140px"
            @change="onFilter"
          >
            <el-option v-for="o in DIFF_OPTS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-input
            v-model="filters.keyword"
            placeholder="搜索题干关键词"
            clearable
            style="width: 240px"
            @keyup.enter="onFilter"
            @clear="onFilter"
          />
          <el-button type="primary" :icon="Search" @click="onFilter">查询</el-button>
          <el-button :icon="RefreshLeft" @click="resetFilter">重置</el-button>
          <span class="spacer" />
          <el-button type="primary" :icon="Plus" @click="openCreate">新建题目</el-button>
          <el-button :icon="MagicStick" @click="openAIDialog">AI 生成</el-button>
        </div>
      </div>

      <!-- 题目表格 -->
      <div class="chart-card">
        <div class="chart-title">
          题库题目
          <el-tag size="small" type="info" effect="light" round>{{ total }}</el-tag>
        </div>
        <el-table :data="list" size="small" empty-text="暂无题目" @row-dblclick="openEdit">
          <el-table-column label="题干" min-width="280">
            <template #default="{ row }">
              <span class="stem-cell">{{ row.stem || row.title || '（无题干）' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="类型" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="typeTag(row.type)" effect="light">{{
                typeLabel(row.type)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="难度" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="diffTag(row.difficulty)" effect="light">{{
                diffLabel(row.difficulty)
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="审核" width="82">
            <template #default="{ row }">
              <el-tag size="small" :type="row.reviewed ? 'success' : 'warning'" effect="light">
                {{ row.reviewed ? '已审核' : '待审核' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280" fixed="right">
            <template #default="{ row }">
              <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
              <el-button
                v-if="!row.reviewed"
                link
                type="success"
                :icon="Select"
                @click="review(row, true)"
                >通过</el-button
              >
              <el-button
                v-if="!row.reviewed"
                link
                type="warning"
                :icon="Close"
                @click="review(row, false)"
                >驳回</el-button
              >
              <el-button link type="info" :icon="CopyDocument" @click="copy(row)">复制</el-button>
              <el-button link type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            :total="total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            background
            @current-change="load"
            @size-change="onSizeChange"
          />
        </div>
      </div>
    </div>

    <!-- 新建 / 编辑 对话框 -->
    <el-dialog
      v-model="dialog.visible"
      :title="dialog.editing ? '编辑题目' : '新建题目'"
      width="560px"
      @closed="resetForm"
    >
      <el-form :model="form" label-width="80px">
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width: 100%" :disabled="dialog.editing">
            <el-option v-for="o in TYPE_OPTS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="form.difficulty" style="width: 100%">
            <el-option v-for="o in DIFF_OPTS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="题干">
          <el-input v-model="form.stem" type="textarea" :rows="3" placeholder="请输入题干" />
        </el-form-item>
        <el-form-item label="答案">
          <el-input v-model="form.answer" type="textarea" :rows="2" placeholder="请输入标准答案" />
        </el-form-item>
        <el-form-item label="解析">
          <el-input
            v-model="form.analysis"
            type="textarea"
            :rows="2"
            placeholder="请输入答案解析"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="dialog.saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- AI 生成 对话框 -->
    <el-dialog v-model="ai.visible" title="AI 智能生成题目" width="520px" @closed="resetAIForm">
      <el-form :model="ai.form" label-width="80px">
        <el-form-item label="类型">
          <el-select v-model="ai.form.type" style="width: 100%">
            <el-option v-for="o in TYPE_OPTS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="ai.form.difficulty" style="width: 100%">
            <el-option v-for="o in DIFF_OPTS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="知识点">
          <el-input
            v-model="kpText"
            type="textarea"
            :rows="2"
            placeholder="多个知识点用逗号分隔，可留空"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="ai.visible = false">取消</el-button>
        <el-button type="primary" :loading="ai.loading" :icon="MagicStick" @click="aiGenerate"
          >生成</el-button
        >
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import questionApi from '@/api/question'
import {
  Search,
  RefreshLeft,
  Plus,
  MagicStick,
  Edit,
  Select,
  Close,
  CopyDocument,
  Delete,
} from '@element-plus/icons-vue'

const route = useRoute()
const courseId = route.params.id

const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(10)

const TYPE_OPTS = [
  { value: 'SINGLE', label: '单选题' },
  { value: 'MULTIPLE', label: '多选题' },
  { value: 'JUDGE', label: '判断题' },
  { value: 'FILL', label: '填空题' },
  { value: 'ESSAY', label: '简答题' },
  { value: 'CODE', label: '编程题' },
]
const DIFF_OPTS = [
  { value: 'EASY', label: '简单' },
  { value: 'MEDIUM', label: '中等' },
  { value: 'HARD', label: '困难' },
]

const filters = reactive({ type: '', difficulty: '', keyword: '' })

function typeLabel(t) {
  return TYPE_OPTS.find((o) => o.value === t)?.label || t || '未知'
}
function diffLabel(d) {
  return DIFF_OPTS.find((o) => o.value === d)?.label || d || '未知'
}
function typeTag(t) {
  return (
    {
      SINGLE: 'primary',
      MULTIPLE: 'success',
      JUDGE: 'warning',
      FILL: 'info',
      ESSAY: '',
      CODE: 'danger',
    }[t] || 'info'
  )
}
function diffTag(d) {
  return { EASY: 'success', MEDIUM: 'warning', HARD: 'danger' }[d] || 'info'
}

async function load() {
  loading.value = true
  try {
    const r = await questionApi.getQuestionList({
      courseId,
      page: page.value,
      size: size.value,
      type: filters.type || undefined,
      difficulty: filters.difficulty || undefined,
      keyword: filters.keyword || undefined,
    })
    const body = unwrap(r)
    list.value = body?.records || body?.list || body || []
    total.value = body?.total ?? body?.totalElements ?? list.value.length
  } catch (e) {
    logger.error('加载题库失败', e)
  } finally {
    loading.value = false
  }
}

function onFilter() {
  page.value = 1
  load()
}
function onSizeChange() {
  page.value = 1
  load()
}
function resetFilter() {
  filters.type = ''
  filters.difficulty = ''
  filters.keyword = ''
  page.value = 1
  load()
}

// 新建 / 编辑
const dialog = reactive({ visible: false, editing: false, saving: false })
const form = reactive({
  id: null,
  type: 'SINGLE',
  difficulty: 'MEDIUM',
  stem: '',
  answer: '',
  analysis: '',
})

function resetForm() {
  form.id = null
  form.type = 'SINGLE'
  form.difficulty = 'MEDIUM'
  form.stem = ''
  form.answer = ''
  form.analysis = ''
  dialog.editing = false
}
function openCreate() {
  resetForm()
  dialog.editing = false
  dialog.visible = true
}
function openEdit(row) {
  resetForm()
  Object.assign(form, {
    id: row.id,
    type: row.type,
    difficulty: row.difficulty,
    stem: row.stem || row.title || '',
    answer: row.answer || '',
    analysis: row.analysis || '',
  })
  dialog.editing = true
  dialog.visible = true
}
async function save() {
  if (!form.stem.trim()) {
    ElMessage.warning('请输入题干')
    return
  }
  dialog.saving = true
  try {
    if (dialog.editing) {
      await questionApi.updateQuestion(form.id, {
        courseId,
        type: form.type,
        difficulty: form.difficulty,
        stem: form.stem,
        answer: form.answer,
        analysis: form.analysis,
      })
      ElMessage.success('题目已更新')
    } else {
      await questionApi.createQuestion({
        courseId,
        type: form.type,
        difficulty: form.difficulty,
        stem: form.stem,
        answer: form.answer,
        analysis: form.analysis,
      })
      ElMessage.success('题目已创建')
    }
    dialog.visible = false
    load()
  } catch (e) {
    logger.error('保存题目失败', e)
  } finally {
    dialog.saving = false
  }
}

async function review(row, approved) {
  try {
    await questionApi.reviewQuestion(row.id, { approved })
    ElMessage.success(approved ? '已审核通过' : '已驳回')
    load()
  } catch (e) {
    logger.error('审核题目失败', e)
  }
}

async function copy(row) {
  try {
    await questionApi.copyQuestion(row.id)
    ElMessage.success('已复制题目')
    load()
  } catch (e) {
    logger.error('复制题目失败', e)
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除题目「${row.stem || row.title || ''}」？此操作不可恢复。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消',
      },
    )
    await questionApi.deleteQuestion(row.id)
    ElMessage.success('题目已删除')
    load()
  } catch (e) {
    if (e !== 'cancel' && e?.toLowerCase) logger.error('删除题目失败', e)
  }
}

// AI 生成
const ai = reactive({ visible: false, loading: false })
const aiForm = reactive({ type: 'SINGLE', difficulty: 'MEDIUM' })
const kpText = ref('')
function resetAIForm() {
  aiForm.type = 'SINGLE'
  aiForm.difficulty = 'MEDIUM'
  kpText.value = ''
}
function openAIDialog() {
  resetAIForm()
  ai.visible = true
}
async function aiGenerate() {
  ai.loading = true
  try {
    const knowledgePointNames = kpText.value
      .split(/[,，]/)
      .map((s) => s.trim())
      .filter(Boolean)
    await questionApi.generateAndSaveByAI({
      courseId,
      type: aiForm.type,
      difficulty: aiForm.difficulty,
      knowledgePointNames,
    })
    ElMessage.success('AI 已生成题目')
    ai.visible = false
    load()
  } catch (e) {
    logger.error('AI 生成题目失败', e)
  } finally {
    ai.loading = false
  }
}

onMounted(load)
watch(
  () => route.params.id,
  () => {
    page.value = 1
    load()
  },
)
</script>

<style scoped>
.fade-in {
  animation: acFadeIn 0.3s ease;
}
@keyframes acFadeIn {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

.chart-card {
  padding: var(--ac-spacing-lg);
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
  margin-bottom: var(--ac-spacing-md);
}
.chart-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-md);
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}
.toolbar .spacer {
  flex: 1;
}

.stem-cell {
  color: var(--ac-text-primary);
  font-size: 13.5px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--ac-spacing-md);
}
</style>
