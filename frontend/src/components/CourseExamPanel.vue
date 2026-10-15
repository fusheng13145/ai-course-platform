<template>
  <div class="exam-panel">
    <div class="panel-toolbar">
      <el-button type="primary" plain @click="showCreateDialog">
        <el-icon><Plus /></el-icon> 创建试卷
      </el-button>
      <el-select v-model="examFilter" class="filter-select" placeholder="筛选状态">
        <el-option label="全部" value="" />
        <el-option label="草稿" value="DRAFT" />
        <el-option label="已发布" value="PUBLISHED" />
        <el-option label="已结束" value="CLOSED" />
      </el-select>
    </div>

    <div class="content-card">
      <!-- 加载失败持久提示（不自动消失） -->
      <el-alert
        v-if="loadError"
        :title="loadError"
        type="error"
        :closable="false"
        class="load-error-alert"
      />
      <el-table v-loading="loading" :data="filteredExams" style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="试卷标题" min-width="200" />
        <el-table-column label="总分" width="80">
          <template #default="{ row }"> {{ row.totalScore || 0 }} 分 </template>
        </el-table-column>
        <el-table-column label="题数" width="80">
          <template #default="{ row }"> {{ row.questionCount || 0 }} 道 </template>
        </el-table-column>
        <el-table-column label="时长" width="80">
          <template #default="{ row }"> {{ row.duration || 0 }} 分钟 </template>
        </el-table-column>
        <el-table-column label="开始时间" width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.startTime) }}
          </template>
        </el-table-column>
        <el-table-column label="结束时间" width="160">
          <template #default="{ row }">
            <span :class="{ 'text-danger': isOverdue(row.endTime) }">
              {{ formatDateTime(row.endTime) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)" size="small">
              {{ getStatusName(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="240" align="center">
          <template #default="{ row }">
            <el-button text type="primary" @click="editExam(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" text type="success" @click="publishExam(row)"
              >发布</el-button
            >
            <el-button
              v-else-if="row.status === 'PUBLISHED'"
              text
              type="warning"
              @click="closeExam(row)"
              >结束</el-button
            >
            <el-button text type="danger" @click="deleteExam(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <EmptyState
        v-if="!loading && filteredExams.length === 0"
        :title="loadError ? '试卷列表未能加载，请查看上方提示后重试' : '暂无试卷'"
        icon="checks"
      />
    </div>

    <el-dialog
      v-model="examDialogVisible"
      :title="editingExam ? '编辑试卷' : '创建试卷'"
      width="700px"
      append-to-body
    >
      <el-form ref="formRef" :model="examForm" :rules="rules" label-width="120px" scroll-to-error>
        <el-form-item label="试卷标题" prop="title">
          <el-input v-model="examForm.title" placeholder="输入试卷标题" />
        </el-form-item>
        <el-form-item label="试卷描述">
          <el-input
            v-model="examForm.description"
            type="textarea"
            :rows="3"
            placeholder="试卷描述"
          />
        </el-form-item>
        <el-form-item label="考试须知">
          <el-input
            v-model="examForm.instructions"
            type="textarea"
            :rows="3"
            placeholder="考试须知"
          />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="考试时长(分钟)">
            <el-input-number v-model="examForm.duration" :min="10" :max="300" />
          </el-form-item>
          <el-form-item label="试卷总分">
            <el-input-number v-model="examForm.totalScore" :min="0" :max="100" />
          </el-form-item>
          <el-form-item label="题目数量">
            <el-input-number v-model="examForm.questionCount" :min="0" :max="100" />
          </el-form-item>
        </div>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="examForm.startTime"
            type="datetime"
            placeholder="选择开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker
            v-model="examForm.endTime"
            type="datetime"
            placeholder="选择结束时间"
            style="width: 100%"
          />
        </el-form-item>
        <div class="form-row">
          <el-form-item label="打乱题目顺序">
            <el-switch v-model="examForm.shuffleQuestions" />
          </el-form-item>
          <el-form-item label="打乱选项顺序">
            <el-switch v-model="examForm.shuffleOptions" />
          </el-form-item>
          <el-form-item label="展示分数">
            <el-switch v-model="examForm.showScore" />
          </el-form-item>
        </div>
        <div class="form-row">
          <el-form-item label="展示答案">
            <el-switch v-model="examForm.showCorrectAnswer" />
          </el-form-item>
          <el-form-item label="允许回顾">
            <el-switch v-model="examForm.allowReview" />
          </el-form-item>
        </div>
        <el-form-item label="最大作答次数">
          <el-input-number v-model="examForm.maxAttempts" :min="1" :max="10" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="examDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveExam">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import examApi from '@/api/exam'
import { confirmDanger } from '@/utils/dangerConfirm'
import { validateFormFields } from '@/composables/useFormSubmit'

const props = defineProps({
  courseId: { type: Number, required: true },
})

const emit = defineEmits(['examUpdated'])

const exams = ref([])
const loading = ref(false)
const loadError = ref('')
const examFilter = ref('')
const examDialogVisible = ref(false)
const editingExam = ref(null)
const saving = ref(false)
const formRef = ref(null)
// D2-4：必填规则声明（原为手写 if 校验），blur 内联错误 + 提交时首错滚动定位
const rules = {
  title: [{ required: true, message: '请输入试卷标题', trigger: 'blur' }],
}

const examForm = ref({
  title: '',
  description: '',
  instructions: '',
  duration: 120,
  totalScore: 100,
  questionCount: 0,
  startTime: null,
  endTime: null,
  shuffleQuestions: false,
  shuffleOptions: false,
  showScore: true,
  showCorrectAnswer: false,
  allowReview: true,
  maxAttempts: 1,
})

const filteredExams = computed(() => {
  if (!examFilter.value) return exams.value
  return exams.value.filter((e) => e.status === examFilter.value)
})

function getStatusTag(status) {
  const map = { DRAFT: 'info', PUBLISHED: 'success', CLOSED: 'danger' }
  return map[status] || 'info'
}

function getStatusName(status) {
  const map = { DRAFT: '草稿', PUBLISHED: '已发布', CLOSED: '已结束' }
  return map[status] || status
}

function isOverdue(date) {
  if (!date) return false
  return new Date(date) < new Date()
}

function formatDateTime(date) {
  if (!date) return '-'
  return new Date(date).toLocaleString('zh-CN')
}

async function loadExams() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await examApi.listByCourse(props.courseId)
    const data = res.data?.records || res.data?.list || res.data || res || []
    exams.value = Array.isArray(data) ? data : []
  } catch (e) {
    logger.error('加载试卷失败:', e)
    exams.value = []
    loadError.value = '试卷列表加载失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

function showCreateDialog() {
  editingExam.value = null
  examForm.value = {
    title: '',
    description: '',
    instructions: '',
    duration: 120,
    totalScore: 100,
    questionCount: 0,
    startTime: null,
    endTime: null,
    shuffleQuestions: false,
    shuffleOptions: false,
    showScore: true,
    showCorrectAnswer: false,
    allowReview: true,
    maxAttempts: 1,
  }
  examDialogVisible.value = true
}

function editExam(row) {
  editingExam.value = row
  examForm.value = {
    title: row.title || '',
    description: row.description || '',
    instructions: row.instructions || '',
    duration: row.duration || 120,
    totalScore: row.totalScore || 100,
    questionCount: row.questionCount || 0,
    startTime: row.startTime ? new Date(row.startTime) : null,
    endTime: row.endTime ? new Date(row.endTime) : null,
    shuffleQuestions: row.shuffleQuestions || false,
    shuffleOptions: row.shuffleOptions || false,
    showScore: row.showScore !== false,
    showCorrectAnswer: row.showCorrectAnswer || false,
    allowReview: row.allowReview !== false,
    maxAttempts: row.maxAttempts || 1,
  }
  examDialogVisible.value = true
}

async function saveExam() {
  // validate 先行（真环境内联错误 + scroll-to-error）；桩/无表单环境由快检兜底
  if (!(await validateFormFields(formRef, ['title']))) return
  if (!examForm.value.title) {
    ElMessage.warning('请输入试卷标题')
    return
  }
  saving.value = true
  try {
    const data = { ...examForm.value, courseId: props.courseId }
    if (editingExam.value) {
      await examApi.updateExamPaper(editingExam.value.id, data)
      ElMessage.success('更新成功')
    } else {
      await examApi.createExamPaper(data)
      ElMessage.success('创建成功')
    }
    examDialogVisible.value = false
    await loadExams()
    emit('examUpdated')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function publishExam(row) {
  try {
    await examApi.publishExam(row.id)
    row.status = 'PUBLISHED'
    ElMessage.success('发布成功')
    emit('examUpdated')
  } catch {
    ElMessage.error('操作失败')
  }
}

async function closeExam(row) {
  try {
    // D2-2：明确后果（学生无法继续作答）与不可重新开启
    const ok = await confirmDanger({
      verb: '结束',
      target: '考试',
      subject: row.title,
      impact: '学生将无法继续作答',
      irreversible: false,
      note: '结束后暂不支持重新开启',
      confirmText: '确定结束',
    })
    if (!ok) return
    row.status = 'CLOSED'
    await examApi.updateExamPaper(row.id, { status: 'CLOSED' })
    ElMessage.success('已结束')
    emit('examUpdated')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

async function deleteExam(row) {
  try {
    // D2-2：明确连带影响（学生作答与成绩明细一并移除）与恢复路径
    const ok = await confirmDanger({
      verb: '删除',
      target: '试卷',
      subject: row.title,
      impact: '将一并移除该试卷关联的学生作答记录与成绩明细',
    })
    if (!ok) return
    await examApi.deleteExamPaper(row.id)
    exams.value = exams.value.filter((e) => e.id !== row.id)
    ElMessage.success('删除成功')
    emit('examUpdated')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

watch(
  () => props.courseId,
  () => {
    loadExams()
  },
  { immediate: true },
)
</script>

<style scoped>
.exam-panel {
  padding: 0;
}

.load-error-alert {
  margin-bottom: 16px;
}

.panel-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.filter-select {
  width: 150px;
}

.form-row {
  display: flex;
  gap: 16px;
}

.form-row .el-form-item {
  flex: 1;
}

.text-danger {
  color: var(--ac-text-primary);
  font-weight: 600;
}
</style>
