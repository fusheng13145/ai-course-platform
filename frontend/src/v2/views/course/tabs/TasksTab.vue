<template>
  <section class="tasks">
    <div class="course-tab-bar">
      <div>
        <p class="card-kicker">COURSE / TASKS</p>
        <h2>
          课程任务 <span class="chip chip-mute">{{ total }} 项</span>
        </h2>
      </div>
      <div class="course-tab-actions">
        <button class="btn btn-ghost" :disabled="loading" @click="load">刷新</button>
        <button class="btn btn-primary" @click="openCreate">新建任务</button>
      </div>
    </div>

    <div v-if="loadError" class="callout">
      <span>ERROR / 加载失败</span>
      <p>{{ loadError }}</p>
    </div>

    <section class="card table-card">
      <div v-if="pagedTasks.length" class="table-wrap">
        <table>
          <thead>
            <tr>
              <th>标题</th>
              <th class="w-96">类型</th>
              <th class="w-96">发布状态</th>
              <th class="w-220">截止日期</th>
              <th class="w-140">关联章节</th>
              <th class="w-260">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, index) in pagedTasks" :key="rowKey(row, index)">
              <td>
                <b class="t-title">{{ row.title }}</b>
              </td>
              <td>
                <span class="chip chip-info">{{ row.taskType || '—' }}</span>
              </td>
              <td>
                <span class="chip" :class="row.isPublished ? 'chip-ok' : 'chip-mute'">
                  {{ row.isPublished ? '已发布' : '草稿' }}
                </span>
              </td>
              <td>{{ fmtDue(row.dueDate) }}</td>
              <td>{{ chapterLabel(row.chapterId) }}</td>
              <td>
                <div class="row-actions">
                  <button class="text-btn" @click="togglePublish(row)">
                    {{ row.isPublished ? '取消发布' : '发布' }}
                  </button>
                  <button class="text-btn" @click="openEdit(row)">编辑</button>
                  <button class="text-btn" @click="openExtend(row)">延长</button>
                  <button class="text-btn danger" @click="askRemove(row)">删除</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div v-else-if="loading" class="empty-state compact"><b>正在加载任务…</b></div>
      <div v-else class="empty-state compact">
        <span class="e-ico"><V2Icon name="check" /></span>
        <b>{{ loadError ? '任务列表未能加载' : '暂无任务' }}</b>
        <p>{{ loadError ? '请查看上方提示后重试。' : '点击右上角「新建任务」发布第一个任务。' }}</p>
      </div>

      <footer v-if="tasks.length" class="card-foot">
        <span>第 {{ page }} / {{ pageCount }} 页 · 共 {{ total }} 项</span>
        <div class="v2-pager">
          <select v-model.number="size" class="input page-size" aria-label="每页条数">
            <option :value="10">10 / 页</option>
            <option :value="20">20 / 页</option>
            <option :value="50">50 / 页</option>
          </select>
          <button class="btn btn-sm" :disabled="page <= 1" @click="go(-1)">上一页</button>
          <button class="btn btn-sm" :disabled="page >= pageCount" @click="go(1)">下一页</button>
        </div>
      </footer>
    </section>

    <!-- 新建 / 编辑 -->
    <V2Modal
      v-model:open="dialogOpen"
      :title="editingId ? '编辑任务' : '新建任务'"
      kicker="TASK / FORM"
      width="600px"
    >
      <div class="form-grid">
        <label class="field span-2">
          <span>标题 *</span>
          <input v-model="form.title" class="input" maxlength="80" placeholder="请输入任务标题" />
        </label>
        <label class="field">
          <span>类型</span>
          <select v-model="form.taskType" class="input">
            <option v-for="type in TASK_TYPES" :key="type" :value="type">{{ type }}</option>
          </select>
        </label>
        <label class="field">
          <span>截止日期</span>
          <input v-model="form.dueDate" class="input" type="datetime-local" />
        </label>
        <label class="field span-2">
          <span>关联章节</span>
          <select v-model="form.chapterId" class="input">
            <option :value="null">未关联</option>
            <option v-for="chapter in chapters" :key="chapter.id" :value="chapter.id">
              {{ chapter.title || `章节 #${chapter.id}` }}
            </option>
          </select>
        </label>
        <label class="field span-2">
          <span>描述</span>
          <textarea
            v-model="form.description"
            class="textarea"
            rows="3"
            placeholder="任务说明（可选）"
          ></textarea>
        </label>
      </div>
      <p v-if="formError" class="form-error">{{ formError }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="dialogOpen = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : editingId ? '保存' : '创建' }}
        </button>
      </template>
    </V2Modal>

    <!-- 延长截止 -->
    <V2Modal v-model:open="extendOpen" title="延长截止日期" kicker="TASK / EXTEND" width="460px">
      <div class="form-grid">
        <label class="field span-2">
          <span>新截止时间 *</span>
          <input v-model="extendForm.newDueDate" class="input" type="datetime-local" />
        </label>
        <label class="field span-2">
          <span>原因</span>
          <textarea
            v-model="extendForm.reason"
            class="textarea"
            rows="3"
            placeholder="延长截止的原因（可选）"
          ></textarea>
        </label>
      </div>
      <p v-if="extendError" class="form-error">{{ extendError }}</p>
      <template #footer>
        <button class="btn btn-ghost" @click="extendOpen = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="saveExtend">确定延长</button>
      </template>
    </V2Modal>

    <!-- 删除确认 -->
    <V2Modal v-model:open="confirm.open" title="删除任务" kicker="CONFIRM / DELETE" width="460px">
      <p class="modal-text">{{ confirmText }}</p>
      <p class="modal-hint">将一并移除该任务的附件、提交记录与学生成绩，且无法恢复。</p>
      <template #footer>
        <button class="btn btn-ghost" @click="confirm.open = false">取消</button>
        <button class="btn btn-danger" :disabled="confirm.busy" @click="runRemove">确定删除</button>
      </template>
    </V2Modal>
  </section>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import V2Modal from '../../../components/V2Modal.vue'
import V2Icon from '../../../components/V2Icon.vue'
import { useV2Ui } from '../../../composables/ui'
import taskApi from '@/api/task'
import chapterApi from '@/api/chapter'
import { unwrap } from '@/utils/response'
import logger from '@/utils/logger'

interface TaskRow {
  id: number
  title?: string
  taskType?: string
  description?: string
  dueDate?: string
  chapterId?: number | null
  isPublished?: boolean
  [key: string]: unknown
}

const TASK_TYPES = ['视频', '测验', '作业', '文档', '讨论']

const props = defineProps<{ courseId: number }>()
const { toast } = useV2Ui()

const tasks = ref<TaskRow[]>([])
const chapters = ref<{ id: number; title?: string }[]>([])
const loading = ref(false)
const loadError = ref('')
const page = ref(1)
const size = ref(10)

const dialogOpen = ref(false)
const dialogMode = ref<'create' | 'edit'>('create')
const editingId = ref<number | null>(null)
const saving = ref(false)
const formError = ref('')
const form = reactive({
  title: '',
  taskType: '作业',
  description: '',
  dueDate: '',
  chapterId: null as number | null,
})

const extendOpen = ref(false)
const extendId = ref<number | null>(null)
const extendError = ref('')
const extendForm = reactive({ newDueDate: '', reason: '' })

const confirm = reactive({ open: false, row: null as TaskRow | null, busy: false })

const total = computed(() => tasks.value.length)
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / size.value)))
const pagedTasks = computed(() =>
  tasks.value.slice((page.value - 1) * size.value, page.value * size.value),
)
const confirmText = computed(() => `确定删除任务「${confirm.row?.title || ''}」？`)

function rowKey(row: { id?: number }, index: number): string {
  return String(row.id ?? index)
}

function chapterLabel(chapterId?: number | null): string {
  if (!chapterId) return '未关联'
  const chapter = chapters.value.find((item) => Number(item.id) === Number(chapterId))
  return chapter?.title ? chapter.title : `章节 #${chapterId}`
}

/** datetime-local 给的是 YYYY-MM-DDTHH:mm，后端契约要 YYYY-MM-DD HH:mm:ss */
function toBackendDate(value: string): string | null {
  if (!value) return null
  const normalized = value.replace('T', ' ')
  return normalized.length === 16 ? `${normalized}:00` : normalized
}

/** extend-deadline 走 LocalDateTime.parse，只收 ISO-8601（空格格式会被判 400） */
function toIsoDateTime(value: string): string | null {
  if (!value) return null
  return value.length === 16 ? `${value}:00` : value
}

function fmtDue(value?: string): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 16)
}

async function load() {
  if (!props.courseId) return
  loading.value = true
  loadError.value = ''
  try {
    const response = await taskApi.getAllTasks({ courseId: props.courseId })
    tasks.value = Array.isArray(response) ? (response as TaskRow[]) : []
    if (page.value > pageCount.value) page.value = 1
  } catch (error) {
    logger.error('加载任务列表失败', error)
    tasks.value = []
    loadError.value = '任务列表加载失败，请检查网络后重试'
  } finally {
    loading.value = false
  }
}

async function loadChapters() {
  if (!props.courseId) return
  try {
    const body = unwrap(await chapterApi.getChapters(props.courseId))
    const data = body as {
      list?: { id: number; title?: string }[]
      records?: { id: number; title?: string }[]
    }
    const rows = Array.isArray(body) ? body : data?.list || data?.records || []
    chapters.value = rows as { id: number; title?: string }[]
  } catch (error) {
    logger.error('加载章节列表失败', error)
    chapters.value = []
  }
}

function go(step: number) {
  page.value = Math.min(pageCount.value, Math.max(1, page.value + step))
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
  formError.value = ''
  dialogMode.value = 'create'
  resetForm()
  dialogOpen.value = true
}

function openEdit(row: TaskRow) {
  formError.value = ''
  dialogMode.value = 'edit'
  editingId.value = row.id
  form.title = row.title || ''
  form.taskType = row.taskType || '作业'
  form.description = row.description || ''
  form.dueDate = (row.dueDate || '').slice(0, 16).replace(' ', 'T')
  form.chapterId = row.chapterId || null
  dialogOpen.value = true
}

async function save() {
  if (!form.title.trim()) {
    formError.value = '请填写任务标题'
    return
  }
  saving.value = true
  try {
    const payload = {
      courseId: props.courseId,
      title: form.title.trim(),
      taskType: form.taskType,
      description: form.description,
      dueDate: toBackendDate(form.dueDate),
      chapterId: form.chapterId || null,
    }
    if (dialogMode.value === 'create') {
      await taskApi.createTask(payload)
      toast('任务已创建', 'ok')
    } else {
      await taskApi.updateTask(editingId.value as number, payload)
      toast('任务已更新', 'ok')
    }
    dialogOpen.value = false
    await load()
  } catch (error) {
    formError.value = '保存失败，请稍后重试'
    logger.error('保存任务失败', error)
  } finally {
    saving.value = false
  }
}

async function togglePublish(row: TaskRow) {
  try {
    await taskApi.publishTask(row.id)
    toast(row.isPublished ? '已取消发布' : '已发布', 'ok')
    await load()
  } catch (error) {
    toast('切换发布状态失败', 'danger')
    logger.error('切换发布状态失败', error)
  }
}

function openExtend(row: TaskRow) {
  extendError.value = ''
  extendId.value = row.id
  extendForm.newDueDate = ''
  extendForm.reason = ''
  extendOpen.value = true
}

async function saveExtend() {
  if (!extendForm.newDueDate) {
    extendError.value = '请选择新的截止日期'
    return
  }
  saving.value = true
  try {
    await taskApi.extendDeadline(extendId.value as number, {
      newDueDate: toIsoDateTime(extendForm.newDueDate) as string,
      reason: extendForm.reason,
    })
    toast('截止日期已延长', 'ok')
    extendOpen.value = false
    await load()
  } catch (error) {
    extendError.value = '延长失败，请稍后重试'
    logger.error('延长截止失败', error)
  } finally {
    saving.value = false
  }
}

function askRemove(row: TaskRow) {
  confirm.row = row
  confirm.open = true
}

async function runRemove() {
  if (!confirm.row) return
  confirm.busy = true
  try {
    await taskApi.deleteTask(confirm.row.id)
    toast('已删除', 'ok')
    confirm.open = false
    confirm.row = null
    await load()
  } catch (error) {
    toast('删除失败', 'danger')
    logger.error('删除任务失败', error)
  } finally {
    confirm.busy = false
  }
}

watch(
  () => props.courseId,
  () => {
    page.value = 1
    void load()
    void loadChapters()
  },
  { immediate: true },
)
</script>

<style scoped>
.tasks {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.t-title {
  display: block;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 550;
}
.page-size {
  height: 30px;
  width: auto;
  font-size: 11px;
}
</style>
