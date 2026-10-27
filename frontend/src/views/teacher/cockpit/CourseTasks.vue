<template>
  <div class="course-tasks fade-in">
    <div v-loading="loading">
      <PageHeader compact title="课程任务" subtitle="任务发布、截止日期与状态管理" icon="list">
        <el-button type="primary" :icon="Plus" @click="openCreate()">新建任务</el-button>
      </PageHeader>

      <!-- 加载失败持久提示（不自动消失） -->
      <el-alert
        v-if="loadError"
        :title="loadError"
        type="error"
        :closable="false"
        class="load-error-alert"
      />

      <div class="l1-card animate-in d1">
        <el-table v-if="tasks.length" :data="pagedTasks" size="small" empty-text="暂无任务">
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip>
            <template #default="{ row }">{{ row.title }}</template>
          </el-table-column>
          <el-table-column label="类型" width="96">
            <template #default="{ row }">
              <el-tag size="small" :type="taskTypeTag(row.taskType)" effect="light">{{
                row.taskType
              }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="发布状态" width="100">
            <template #default="{ row }">
              <el-tag size="small" :type="row.isPublished ? 'success' : 'info'" effect="light">
                {{ row.isPublished ? '已发布' : '草稿' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="截止日期" min-width="120">
            <template #default="{ row }">{{ row.dueDate || '—' }}</template>
          </el-table-column>
          <el-table-column label="关联章节" min-width="110">
            <template #default="{ row }">{{
              row.chapterId ? '章节 #' + row.chapterId : '未关联'
            }}</template>
          </el-table-column>
          <el-table-column label="操作" width="260" fixed="right">
            <template #default="{ row }">
              <el-button
                link
                :type="row.isPublished ? 'warning' : 'success'"
                :icon="row.isPublished ? 'Close' : 'Upload'"
                @click="togglePublish(row)"
              >
                {{ row.isPublished ? '取消发布' : '发布' }}
              </el-button>
              <el-button link type="primary" :icon="Edit" @click="openEdit(row)">编辑</el-button>
              <el-button link type="warning" :icon="Calendar" @click="openExtend(row)"
                >延长</el-button
              >
              <el-button link type="danger" :icon="Delete" @click="remove(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <EmptyState
          v-else-if="!loading"
          :title="loadError ? '任务列表未能加载，请查看上方提示后重试' : '暂无任务，点击右上角新建'"
          icon="list"
        />

        <div v-if="tasks.length" class="pager">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            :total="tasks.length"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            background
            @current-change="page = $event"
            @size-change="size = $event"
          />
        </div>
      </div>
    </div>

    <!-- 新建 / 编辑对话框 -->
    <Modal
      v-model="dialogVisible"
      :title="dialogMode === 'create' ? '新建任务' : '编辑任务'"
      width="520px"
      @closed="resetForm"
    >
      <el-form :model="form" label-width="88px">
        <el-form-item label="标题">
          <el-input
            v-model="form.title"
            placeholder="请输入任务标题"
            maxlength="80"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.taskType" placeholder="选择任务类型" class="w-100">
            <el-option v-for="t in taskTypes" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述">
          <el-input
            v-model="form.description"
            type="textarea"
            :rows="3"
            placeholder="任务说明（可选）"
          />
        </el-form-item>
        <el-form-item label="截止日期">
          <el-date-picker
            v-model="form.dueDate"
            type="datetime"
            placeholder="选择截止时间"
            class="w-100"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="关联章节">
          <el-input-number
            v-model="form.chapterId"
            :min="0"
            :controls="false"
            placeholder="可选"
            class="w-160"
          />
          <span class="hint">留空表示不绑定章节</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">{{
          dialogMode === 'create' ? '创建' : '保存'
        }}</el-button>
      </template>
    </Modal>

    <!-- 延长截止对话框 -->
    <Modal v-model="extendVisible" title="延长截止日期" width="460px" @closed="resetExtend">
      <el-form :model="extendForm" label-width="88px">
        <el-form-item label="新截止">
          <el-date-picker
            v-model="extendForm.newDueDate"
            type="datetime"
            placeholder="选择新的截止时间"
            class="w-100"
            value-format="YYYY-MM-DD HH:mm:ss"
          />
        </el-form-item>
        <el-form-item label="原因">
          <el-input
            v-model="extendForm.reason"
            type="textarea"
            :rows="3"
            placeholder="延长截止的原因（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="extendVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveExtend">确定延长</el-button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import taskApi from '@/api/task'
import EmptyState from '@/components/ui/EmptyState.vue'
import { confirmDanger } from '@/utils/dangerConfirm'
import PageHeader from '@/components/PageHeader.vue'
import { Plus, Edit, Delete, Calendar } from '@element-plus/icons-vue'
import Modal from '@/components/ui/Modal.vue'

const route = useRoute()
const courseId = computed(() => Number(route.params.id))

const loading = ref(false)
const loadError = ref('')
const saving = ref(false)
const tasks = ref([])

const page = ref(1)
const size = ref(10)
const pagedTasks = computed(() => {
  const start = (page.value - 1) * size.value
  return tasks.value.slice(start, start + size.value)
})

const taskTypes = ['视频', '测验', '作业', '文档', '讨论']

const dialogVisible = ref(false)
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）
const dialogMode = ref('create')
const editingId = ref(null)
const form = reactive({
  title: '',
  taskType: '作业',
  description: '',
  dueDate: '',
  chapterId: null,
})

const extendVisible = ref(false)
// D2-7：打开即聚焦首个可聚焦控件（WAI-ARIA APG「焦点入框」惯例）
const extendId = ref(null)
const extendForm = reactive({ newDueDate: '', reason: '' })

function taskTypeTag(type) {
  return (
    { 视频: 'info', 测验: 'warning', 作业: 'success', 文档: 'primary', 讨论: 'danger' }[type] ||
    'info'
  )
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    tasks.value = await taskApi.getAllTasks({ courseId: courseId.value })
    if (page.value > 1 && (page.value - 1) * size.value >= tasks.value.length) page.value = 1
  } catch (e) {
    logger.error('加载任务列表失败', e)
    tasks.value = []
    loadError.value = '任务列表加载失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

function resetForm() {
  form.title = ''
  form.taskType = '作业'
  form.description = ''
  form.dueDate = ''
  form.chapterId = null
  editingId.value = null
}
function openCreate() {
  dialogMode.value = 'create'
  resetForm()
  dialogVisible.value = true
}
function openEdit(row) {
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.title = row.title || ''
  form.taskType = row.taskType || '作业'
  form.description = row.description || ''
  form.dueDate = row.dueDate || ''
  form.chapterId = row.chapterId || null
  dialogVisible.value = true
}

async function save() {
  if (!form.title.trim()) {
    ElMessage.warning('请填写任务标题')
    return
  }
  saving.value = true
  try {
    const payload = {
      courseId: courseId.value,
      title: form.title.trim(),
      taskType: form.taskType,
      description: form.description,
      dueDate: form.dueDate || null,
      chapterId: form.chapterId || null,
    }
    if (dialogMode.value === 'create') {
      await taskApi.createTask(payload)
      ElMessage.success('任务已创建')
    } else {
      await taskApi.updateTask(editingId.value, payload)
      ElMessage.success('任务已更新')
    }
    dialogVisible.value = false
    await load()
  } catch (e) {
    logger.error('保存任务失败', e)
  } finally {
    saving.value = false
  }
}

async function togglePublish(row) {
  try {
    await taskApi.publishTask(row.id)
    ElMessage.success(row.isPublished ? '已取消发布' : '已发布')
    await load()
  } catch (e) {
    logger.error('切换发布状态失败', e)
  }
}

function openExtend(row) {
  extendId.value = row.id
  extendForm.newDueDate = ''
  extendForm.reason = ''
  extendVisible.value = true
}
function resetExtend() {
  extendId.value = null
  extendForm.newDueDate = ''
  extendForm.reason = ''
}
async function saveExtend() {
  if (!extendForm.newDueDate) {
    ElMessage.warning('请选择新的截止日期')
    return
  }
  saving.value = true
  try {
    await taskApi.extendDeadline(extendId.value, {
      newDueDate: extendForm.newDueDate,
      reason: extendForm.reason,
    })
    ElMessage.success('截止日期已延长')
    extendVisible.value = false
    await load()
  } catch (e) {
    logger.error('延长截止失败', e)
  } finally {
    saving.value = false
  }
}

async function remove(row) {
  const ok = await confirmDanger({
    verb: '删除',
    target: '任务',
    subject: row.title,
    impact: '将一并移除该任务的附件、提交记录与学生成绩，且无法恢复',
  })
  if (!ok) return
  try {
    await taskApi.deleteTask(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    logger.error('删除任务失败', e)
    ElMessage.error('删除失败')
  }
}

onMounted(load)
watch(() => route.params.id, load)
</script>

<style scoped>
.load-error-alert {
  margin-bottom: var(--ac-spacing-md);
}

.hint {
  margin-left: 10px;
  font-size: 12px;
  color: var(--ac-text-muted);
}

.w-100 {
  width: 100%;
}
.w-160 {
  width: 160px;
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--ac-spacing-md);
}
</style>
