<template>
  <div class="task-panel">
    <div class="panel-toolbar">
      <el-button type="primary" plain @click="showCreateDialog">
        <el-icon><Plus /></el-icon> 创建作业
      </el-button>
      <el-select v-model="taskFilter" class="filter-select" placeholder="筛选状态">
        <el-option label="全部" value="" />
        <el-option label="未发布" value="DRAFT" />
        <el-option label="进行中" value="ACTIVE" />
        <el-option label="已截止" value="CLOSED" />
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
      <el-table v-loading="loading" :data="filteredTasks" style="width: 100%">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="作业标题" min-width="200" />
        <el-table-column label="类型" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="getTaskTypeTag(row.taskType)">
              {{ getTaskTypeName(row.taskType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="分值" width="80">
          <template #default="{ row }"> {{ row.maxScore || 0 }} 分 </template>
        </el-table-column>
        <el-table-column label="开始时间" width="160">
          <template #default="{ row }">
            {{ formatDateTime(row.startDate) }}
          </template>
        </el-table-column>
        <el-table-column label="截止时间" width="160">
          <template #default="{ row }">
            <span :class="{ 'text-danger': isOverdue(row.dueDate) }">
              {{ formatDateTime(row.dueDate) }}
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
            <el-button text type="primary" @click="editTask(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT'" text type="success" @click="publishTask(row)"
              >发布</el-button
            >
            <el-button
              v-else-if="row.status === 'ACTIVE'"
              text
              type="warning"
              @click="closeTask(row)"
              >截止</el-button
            >
            <el-button text type="danger" @click="deleteTask(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <EmptyState
        v-if="!loading && filteredTasks.length === 0"
        :title="loadError ? '作业列表未能加载，请查看上方提示后重试' : '暂无作业'"
        icon="list"
      />
    </div>

    <el-dialog
      ref="dialogRef"
    v-model="taskDialogVisible"
      :title="editingTask ? '编辑作业' : '创建作业'"
      width="600px"
      append-to-body
    >
      <el-form ref="formRef" :model="taskForm" :rules="rules" label-width="100px" scroll-to-error>
        <el-form-item label="作业标题" prop="title">
          <el-input v-model="taskForm.title" placeholder="输入作业标题" />
        </el-form-item>
        <el-form-item label="作业类型">
          <el-select v-model="taskForm.taskType" style="width: 100%">
            <el-option label="普通作业" value="ASSIGNMENT" />
            <el-option label="测验" value="QUIZ" />
            <el-option label="小组作业" value="GROUP" />
            <el-option label="论文" value="PAPER" />
          </el-select>
        </el-form-item>
        <el-form-item label="作业描述">
          <el-input
            v-model="taskForm.description"
            type="textarea"
            :rows="4"
            placeholder="作业描述"
          />
        </el-form-item>
        <el-form-item label="作业要求">
          <el-input
            v-model="taskForm.requirements"
            type="textarea"
            :rows="3"
            placeholder="作业要求"
          />
        </el-form-item>
        <el-form-item label="满分分值">
          <el-input-number v-model="taskForm.maxScore" :min="0" :max="100" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker
            v-model="taskForm.startDate"
            type="datetime"
            placeholder="选择开始时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="截止时间">
          <el-date-picker
            v-model="taskForm.dueDate"
            type="datetime"
            placeholder="选择截止时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="提交格式">
          <el-select v-model="taskForm.submissionFormat" style="width: 100%">
            <el-option label="在线文本" value="TEXT" />
            <el-option label="文件上传" value="FILE" />
            <el-option label="在线文本+文件" value="BOTH" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="taskDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveTask">保存</el-button>
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
import { validateFormFields } from '@/composables/useFormSubmit'
import taskApi from '@/api/task'
import { confirmDanger } from '@/utils/dangerConfirm'
import { useDialogFocus } from '@/composables/useDialogFocus'

const props = defineProps({
  courseId: { type: Number, required: true },
})

const emit = defineEmits(['taskUpdated'])

const tasks = ref([])
const loading = ref(false)
const loadError = ref('')
const taskFilter = ref('')
const taskDialogVisible = ref(false)
const dialogRef = ref(null)
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）
useDialogFocus(dialogRef, taskDialogVisible)
const editingTask = ref(null)
const saving = ref(false)

const taskForm = ref({
  title: '',
  taskType: 'ASSIGNMENT',
  description: '',
  requirements: '',
  maxScore: 100,
  startDate: null,
  dueDate: null,
  submissionFormat: 'TEXT',
})
const formRef = ref(null)
// D2-4：必填规则声明（原为手写 if 校验），blur 内联错误 + 提交时首错滚动定位
const rules = {
  title: [{ required: true, message: '请输入作业标题', trigger: 'blur' }],
}

const filteredTasks = computed(() => {
  if (!taskFilter.value) return tasks.value
  return tasks.value.filter((t) => t.status === taskFilter.value)
})

function getTaskTypeTag(type) {
  const map = { ASSIGNMENT: 'primary', QUIZ: 'success', GROUP: 'warning', PAPER: 'danger' }
  return map[type] || 'info'
}

function getTaskTypeName(type) {
  const map = { ASSIGNMENT: '普通作业', QUIZ: '测验', GROUP: '小组作业', PAPER: '论文' }
  return map[type] || '其他'
}

function getStatusTag(status) {
  const map = { DRAFT: 'info', ACTIVE: 'success', CLOSED: 'danger' }
  return map[status] || 'info'
}

function getStatusName(status) {
  const map = { DRAFT: '未发布', ACTIVE: '进行中', CLOSED: '已截止' }
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

async function loadTasks() {
  loading.value = true
  loadError.value = ''
  try {
    const res = await taskApi.listByCourse(props.courseId)
    const data = res.data?.records || res.data?.list || res.data || res || []
    tasks.value = Array.isArray(data) ? data : []
  } catch (e) {
    logger.error('加载作业失败:', e)
    tasks.value = []
    loadError.value = '作业列表加载失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

function showCreateDialog() {
  editingTask.value = null
  taskForm.value = {
    title: '',
    taskType: 'ASSIGNMENT',
    description: '',
    requirements: '',
    maxScore: 100,
    startDate: null,
    dueDate: null,
    submissionFormat: 'TEXT',
  }
  taskDialogVisible.value = true
}

function editTask(row) {
  editingTask.value = row
  taskForm.value = {
    title: row.title || '',
    taskType: row.taskType || 'ASSIGNMENT',
    description: row.description || '',
    requirements: row.requirements || '',
    maxScore: row.maxScore || 100,
    startDate: row.startDate ? new Date(row.startDate) : null,
    dueDate: row.dueDate ? new Date(row.dueDate) : null,
    submissionFormat: row.submissionFormat || 'TEXT',
  }
  taskDialogVisible.value = true
}

async function saveTask() {
  // validate 先行（真环境内联错误 + scroll-to-error）；桩/无表单环境由快检兜底
  if (!(await validateFormFields(formRef, ['title']))) return
  if (!taskForm.value.title) {
    ElMessage.warning('请输入作业标题')
    return
  }
  saving.value = true
  try {
    const data = { ...taskForm.value, courseId: props.courseId }
    if (editingTask.value) {
      await taskApi.updateTask(editingTask.value.id, data)
      ElMessage.success('更新成功')
    } else {
      await taskApi.createTask(data)
      ElMessage.success('创建成功')
    }
    taskDialogVisible.value = false
    await loadTasks()
    emit('taskUpdated')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function publishTask(row) {
  try {
    await taskApi.publishTask(row.id)
    row.status = 'ACTIVE'
    ElMessage.success('发布成功')
    emit('taskUpdated')
  } catch {
    ElMessage.error('操作失败')
  }
}

async function closeTask(row) {
  try {
    // D2-2：明确后果（学生无法再提交）与不可重新开启
    const ok = await confirmDanger({
      verb: '截止',
      target: '作业',
      subject: row.title,
      impact: '学生将无法再提交，已提交的作业不受影响',
      irreversible: false,
      note: '截止后暂不支持重新开启',
      confirmText: '确定截止',
    })
    if (!ok) return
    row.status = 'CLOSED'
    await taskApi.updateTask(row.id, { status: 'CLOSED' })
    ElMessage.success('已截止')
    emit('taskUpdated')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('操作失败')
  }
}

async function deleteTask(row) {
  try {
    // D2-2：明确连带影响（学生提交记录一并移除）与恢复路径
    const ok = await confirmDanger({
      verb: '删除',
      target: '作业',
      subject: row.title,
      impact: '将一并移除该作业下学生已提交的记录',
    })
    if (!ok) return
    await taskApi.deleteTask(row.id)
    tasks.value = tasks.value.filter((t) => t.id !== row.id)
    ElMessage.success('删除成功')
    emit('taskUpdated')
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败')
  }
}

watch(
  () => props.courseId,
  () => {
    loadTasks()
  },
  { immediate: true },
)
</script>

<style scoped>
.task-panel {
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

.text-danger {
  color: var(--ac-text-primary);
  font-weight: 600;
}
</style>
