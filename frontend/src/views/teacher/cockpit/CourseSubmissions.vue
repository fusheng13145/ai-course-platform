<template>
  <div>
    <PageHeader compact title="作业提交" subtitle="按任务查看提交记录并进行批改" icon="clipboard" />
    <el-alert
      v-if="taskNotice"
      class="task-notice"
      :title="taskNotice"
      type="warning"
      :closable="false"
      show-icon
    />
    <!-- 左右分割视图 -->
    <div v-loading="loading" class="split-view">
      <!-- 左侧任务选择 -->
      <aside class="split-aside">
        <div class="sa-head">
          <span class="strong small">任务列表</span>
          <span class="tag">{{ tasks.length }} 个</span>
        </div>
        <div class="sa-body">
          <div v-if="tasks.length" class="task-list">
            <div
              v-for="t in tasks"
              :key="t.id"
              class="task-item"
              :class="{ 'is-active': t.id === selectedTaskId }"
              @click="selectTask(t.id)"
            >
              <div class="flex between mb-8">
                <span class="strong task-title">{{ t.title }}</span
                ><span class="tag" :class="getTaskTypeClass(t.taskType)">{{
                  getTaskTypeLabel(t.taskType)
                }}</span>
              </div>
              <div class="flex between small muted">
                <span class="tag" :class="t.isPublished ? 'green' : ''">{{
                  t.isPublished ? '已发布' : '草稿'
                }}</span
                ><span v-if="t.dueDate">截止 {{ t.dueDate }}</span>
              </div>
            </div>
          </div>
          <div v-else class="empty-state"><div class="e-title">暂无任务</div></div>
        </div>
      </aside>

      <!-- 右侧提交列表 + 批改 -->
      <div class="split-main">
        <section v-loading="submissionsLoading" class="l1-card">
          <div class="l1-head">
            <span class="l1-ico"
              ><svg
                viewBox="0 0 24 24"
                width="17"
                height="17"
                fill="none"
                stroke="currentColor"
                stroke-width="1.9"
                stroke-linecap="round"
                stroke-linejoin="round"
              >
                <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z" /></svg
            ></span>
            <div>
              <div class="l1-title">提交记录</div>
              <div v-if="selectedTask" class="l1-sub">任务：{{ selectedTask.title }}</div>
            </div>
          </div>
          <div class="l1-body flush">
            <div v-if="submissions.length" class="tbl-wrap">
              <table class="tbl">
                <thead>
                  <tr>
                    <th>学生</th>
                    <th>状态</th>
                    <th>分数</th>
                    <th>提交时间</th>
                    <th>内容</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="row in submissions" :key="row.id">
                    <td class="cell-main">
                      {{ row.studentName || row.studentNo || `学生 #${row.studentId ?? row.id}` }}
                    </td>
                    <td>
                      <span class="tag" :class="getSubmissionStatusClass(row.status)">{{
                        getSubmissionStatusLabel(row.status)
                      }}</span>
                    </td>
                    <td class="num strong">{{ row.score != null ? row.score : '—' }}</td>
                    <td class="muted small">{{ row.submittedAt || '—' }}</td>
                    <td class="small muted cell-ellipsis">
                      {{
                        row.content ||
                        (row.fileUrls?.length ? `附件 ${row.fileUrls.length} 个` : '—')
                      }}
                    </td>
                    <td>
                      <div class="row-ops">
                        <button
                          type="button"
                          class="op-link"
                          :disabled="saving"
                          @click="openGrade(row)"
                        >
                          批改
                        </button>
                        <button
                          type="button"
                          class="op-link"
                          :disabled="row._ai || saving"
                          @click="aiGrade(row)"
                        >
                          {{ row._ai ? 'AI 评分中…' : 'AI 评分' }}
                        </button>
                        <button
                          type="button"
                          class="op-link danger"
                          :disabled="saving"
                          @click="openReturn(row)"
                        >
                          退回
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <el-pagination
              v-if="submissionTotal > submissionPageSize"
              v-model:current-page="submissionPage"
              v-model:page-size="submissionPageSize"
              class="submission-pagination"
              background
              layout="total, prev, pager, next, sizes"
              :total="submissionTotal"
              :page-sizes="[10, 20, 50]"
              @current-change="loadSubmissions"
              @size-change="handleSubmissionPageSizeChange"
            />
            <el-alert
              v-if="submissionNotice"
              class="submission-notice"
              :title="submissionNotice"
              type="error"
              :closable="false"
              show-icon
            />
            <EmptyState
              v-else-if="!submissionsLoading && selectedTask"
              title="暂无提交记录"
              icon="list"
            />
            <EmptyState v-else-if="!selectedTask" title="请选择任务查看提交记录" icon="list" />
          </div>
        </section>
      </div>
    </div>

    <!-- 批改弹窗（内嵌打分 + 评语） -->
    <Modal v-if="gradeVisible" narrow @close="closeGrade">
      <template #title>批改作业</template>
      <template #sub
        >学生：<b>{{ gradingRow?.studentName }}</b></template
      >
      <div>
        <div class="l3-block">
          <div class="strong small mb-8">{{ gradingRow?.studentName }} 的提交内容</div>
          <span v-if="gradingRow?.content">{{ gradingRow?.content }}</span>
          <div v-if="gradingRow?.fileUrls?.length" class="attachment-list">
            <span class="muted">附件：</span>
            <template v-for="(fileUrl, index) in gradingRow.fileUrls" :key="`${fileUrl}-${index}`">
              <a
                v-if="getSafeAttachmentUrl(fileUrl)"
                :href="getSafeAttachmentUrl(fileUrl)"
                target="_blank"
                rel="noopener noreferrer"
                class="attachment-link"
                >附件 {{ index + 1 }}</a
              >
              <span v-else class="tag red">附件 {{ index + 1 }}（链接无效）</span>
            </template>
          </div>
          <span v-if="!gradingRow?.content && !gradingRow?.fileUrls?.length" class="muted"
            >无内容</span
          >
        </div>
        <div class="form-grid">
          <div class="field">
            <label>评分（0-{{ gradingMaxScore }}）</label>
            <input
              v-model.number="gradeForm.score"
              class="input"
              type="number"
              min="0"
              :max="gradingMaxScore"
              step="0.1"
            />
          </div>
          <div class="field span-2">
            <label>评语</label
            ><textarea
              v-model="gradeForm.feedback"
              class="textarea"
              placeholder="指出亮点与错误之处…"
              maxlength="5000"
              :rows="3"
            ></textarea>
          </div>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-plain" :disabled="saving" @click="closeGrade">取消</button>
        <button class="btn btn-primary" :disabled="saving || !gradingRow" @click="saveGrade">
          提交批改
        </button>
      </template>
    </Modal>

    <!-- 退回弹窗 -->
    <Modal v-if="returnVisible" narrow @close="closeReturn">
      <template #title>退回重做</template>
      <div class="form-grid">
        <div class="field span-2">
          <label>退回原因</label
          ><textarea
            v-model="returnForm.reason"
            class="textarea"
            placeholder="请说明退回原因"
            maxlength="1000"
            :rows="3"
          ></textarea>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-plain" :disabled="saving" @click="closeReturn">取消</button>
        <button class="btn btn-primary btn-warn" :disabled="saving" @click="saveReturn">
          确认退回
        </button>
      </template>
    </Modal>
  </div>
</template>

<script setup lang="ts">
import logger from '@/utils/logger'
import { pickList, readTotal, unwrap } from '@/utils/response'
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import taskApi from '@/api/task'
import submissionApi from '@/api/submission'
import Modal from '@/components/ui/Modal.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import PageHeader from '@/components/PageHeader.vue'

const route = useRoute()
const courseId = computed(() => Number(route.params.id))

const loading = ref(false)
const submissionsLoading = ref(false)
const saving = ref(false)
const taskNotice = ref('')
const submissionNotice = ref('')

const tasks = ref([])
const selectedTaskId = ref(null)
const selectedTask = computed(() => tasks.value.find((t) => t.id === selectedTaskId.value) || null)

const submissions = ref([])
const submissionPage = ref(1)
const submissionPageSize = ref(20)
const submissionTotal = ref(0)

const gradeVisible = ref(false)
const gradingRow = ref(null)
const gradeForm = reactive<{ score: number | string | null; feedback: string }>({
  score: null,
  feedback: '',
})
const gradingMaxScore = computed(() => {
  const value = Number(gradingRow.value?.maxScore ?? selectedTask.value?.maxScore)
  return Number.isFinite(value) && value >= 0 ? value : 1000
})

const returnVisible = ref(false)
const returnRow = ref(null)
const returnForm = reactive({ reason: '' })

let taskLoadSequence = 0
let submissionLoadSequence = 0

async function loadTasks() {
  const sequence = ++taskLoadSequence
  const currentCourseId = courseId.value
  if (!Number.isInteger(currentCourseId) || currentCourseId <= 0) {
    tasks.value = []
    selectedTaskId.value = null
    submissions.value = []
    submissionTotal.value = 0
    taskNotice.value = '课程信息无效，无法加载任务。'
    submissionNotice.value = ''
    submissionsLoading.value = false
    loading.value = false
    return
  }

  loading.value = true
  taskNotice.value = ''
  try {
    const response = await taskApi.getAllTasks({ courseId: currentCourseId })
    if (sequence !== taskLoadSequence || currentCourseId !== courseId.value) return
    tasks.value = Array.isArray(response) ? response : []
    const requestedTaskId = route.query.taskId == null ? null : String(route.query.taskId)
    let requestedTask = requestedTaskId
      ? tasks.value.find((task) => String(task.id) === requestedTaskId)
      : null
    if (requestedTaskId && !requestedTask) {
      const requestedId = Number(requestedTaskId)
      if (Number.isInteger(requestedId) && requestedId > 0) {
        try {
          const taskResponse = await taskApi.getTaskById(requestedId)
          if (sequence !== taskLoadSequence || currentCourseId !== courseId.value) return
          const candidate = unwrap(taskResponse)
          if (candidate && String(candidate.courseId) === String(currentCourseId)) {
            requestedTask = candidate
            tasks.value = [...tasks.value, candidate]
          }
        } catch (error) {
          logger.warn('读取指定任务失败', error)
        }
      }
      if (!requestedTask) {
        if (sequence !== taskLoadSequence) return
        taskNotice.value = '指定的待批阅任务已不存在，或不属于当前课程；请从左侧重新选择任务。'
      }
    }

    const previouslySelected = tasks.value.find(
      (task) => String(task.id) === String(selectedTaskId.value),
    )
    const nextTask = requestedTaskId ? requestedTask : previouslySelected || tasks.value[0] || null
    if (nextTask) selectTask(nextTask.id)
    else {
      selectedTaskId.value = null
      submissions.value = []
      submissionTotal.value = 0
      submissionNotice.value = ''
      submissionsLoading.value = false
      submissionLoadSequence++
    }
  } catch (error) {
    if (sequence === taskLoadSequence) {
      logger.error('加载任务列表失败', error)
      tasks.value = []
      selectedTaskId.value = null
      submissions.value = []
      taskNotice.value = '任务列表加载失败，请刷新页面重试。'
      submissionNotice.value = ''
      submissionsLoading.value = false
      submissionLoadSequence++
    }
  } finally {
    if (sequence === taskLoadSequence) loading.value = false
  }
}

function selectTask(id) {
  const task = tasks.value.find((item) => String(item.id) === String(id))
  if (!task) return
  selectedTaskId.value = id
  submissionPage.value = 1
  submissionTotal.value = 0
  loadSubmissions()
}

async function loadSubmissions() {
  const taskId = selectedTaskId.value
  const sequence = ++submissionLoadSequence
  if (taskId == null) {
    submissions.value = []
    submissionTotal.value = 0
    submissionNotice.value = ''
    submissionsLoading.value = false
    return
  }

  submissions.value = []
  submissionNotice.value = ''
  submissionsLoading.value = true
  try {
    const response = await submissionApi.getTaskSubmissions(taskId, {
      page: submissionPage.value,
      size: submissionPageSize.value,
    })
    if (sequence !== submissionLoadSequence || taskId !== selectedTaskId.value) return
    const payload = unwrap(response)
    const rows = pickList(payload)
    submissions.value = rows.map((row) => ({
      ...row,
      fileUrls: normalizeFileUrls(row.fileUrls),
    }))
    submissionTotal.value = readTotal(payload, rows.length)
  } catch (error) {
    if (sequence === submissionLoadSequence && taskId === selectedTaskId.value) {
      logger.error('加载提交记录失败', error)
      submissionTotal.value = 0
      submissionNotice.value = '提交记录加载失败，请重试或切换任务。'
    }
  } finally {
    if (sequence === submissionLoadSequence) submissionsLoading.value = false
  }
}

function handleSubmissionPageSizeChange() {
  submissionPage.value = 1
  loadSubmissions()
}

function normalizeFileUrls(value) {
  if (Array.isArray(value)) return value.filter((item) => typeof item === 'string' && item.trim())
  if (typeof value !== 'string' || !value.trim()) return []
  const raw = value.trim()
  try {
    const parsed = JSON.parse(raw)
    if (Array.isArray(parsed))
      return parsed.filter((item) => typeof item === 'string' && item.trim())
  } catch {
    // Legacy submissions store comma-separated links rather than a JSON array.
  }
  return raw
    .split(',')
    .map((url) => url.trim())
    .filter(Boolean)
}

function getSafeAttachmentUrl(value) {
  if (typeof value !== 'string' || !value.trim()) return ''
  try {
    const url = new URL(value.trim(), window.location.origin)
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : ''
  } catch {
    return ''
  }
}

function getTaskTypeLabel(value) {
  const labels = {
    ASSIGNMENT: '作业',
    HOMEWORK: '作业',
    QUIZ: '测验',
    EXAM: '考试',
    PROJECT: '项目',
  }
  const normalized = String(value || '').toUpperCase()
  return labels[normalized] || value || '任务'
}

function getTaskTypeClass(value) {
  return ['ASSIGNMENT', 'HOMEWORK', '作业'].includes(String(value || '').toUpperCase())
    ? 'green'
    : 'blue'
}

function getSubmissionStatusLabel(value) {
  const labels = {
    GRADED: '已批改',
    RETURNED: '已退回',
    SUBMITTED: '待批改',
    RESUBMITTED: '重新提交待批改',
  }
  const normalized = String(value || '').toUpperCase()
  return labels[normalized] || '状态未知'
}

function getSubmissionStatusClass(value) {
  const normalized = String(value || '').toUpperCase()
  if (normalized === 'GRADED') return 'green'
  if (normalized === 'RETURNED') return 'red'
  if (['SUBMITTED', 'RESUBMITTED'].includes(normalized)) return 'amber'
  return ''
}

function resetGrade() {
  gradingRow.value = null
  gradeForm.score = null
  gradeForm.feedback = ''
}
function openGrade(row) {
  gradingRow.value = row
  gradeForm.score = row.score != null ? Number(row.score) : null
  gradeForm.feedback = row.feedback || ''
  gradeVisible.value = true
}

// 修复：模板多语句表达式与 prettier(semi:false) 冲突会产生 Vue 编译器无法解析的换行格式，
// 提取为方法（关闭批改/退回弹窗时同步重置表单）
function closeGrade() {
  gradeVisible.value = false
  resetGrade()
}
function closeReturn() {
  returnVisible.value = false
  resetReturn()
}

async function saveGrade() {
  if (gradeForm.score == null || gradeForm.score === '') {
    ElMessage.warning('请填写分数')
    return
  }
  const score = Number(gradeForm.score)
  if (!gradingRow.value || !Number.isFinite(score)) {
    ElMessage.warning('请输入有效分数')
    return
  }
  if (score < 0 || score > gradingMaxScore.value) {
    ElMessage.warning(`分数需在 0 至 ${gradingMaxScore.value} 之间`)
    return
  }
  saving.value = true
  try {
    await submissionApi.gradeSubmission(gradingRow.value.id, {
      score,
      feedback: gradeForm.feedback,
    })
    ElMessage.success('批改已提交')
    closeGrade()
    await loadSubmissions()
  } catch (error) {
    logger.error('批改失败', error)
    ElMessage.error('提交批改失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

async function aiGrade(row) {
  row._ai = true
  try {
    await submissionApi.aiGrade(row.id)
    ElMessage.success('AI 评分完成')
    await loadSubmissions()
  } catch (e) {
    logger.error('AI 评分失败', e)
    ElMessage.error('AI 评分失败，请稍后重试')
  } finally {
    row._ai = false
  }
}

function resetReturn() {
  returnRow.value = null
  returnForm.reason = ''
}
function openReturn(row) {
  returnRow.value = row
  returnForm.reason = ''
  returnVisible.value = true
}
async function saveReturn() {
  if (!returnRow.value) return
  if (!returnForm.reason.trim()) {
    ElMessage.warning('请填写退回原因')
    return
  }
  saving.value = true
  try {
    await submissionApi.returnSubmission(returnRow.value?.id, { reason: returnForm.reason })
    ElMessage.success('已退回')
    closeReturn()
    await loadSubmissions()
  } catch (error) {
    logger.error('退回失败', error)
    ElMessage.error('退回失败，请稍后重试')
  } finally {
    saving.value = false
  }
}

onMounted(loadTasks)
watch(
  () => [route.params.id, route.query.taskId],
  () => {
    selectedTaskId.value = null
    tasks.value = []
    submissions.value = []
    submissionPage.value = 1
    submissionTotal.value = 0
    taskNotice.value = ''
    submissionNotice.value = ''
    submissionLoadSequence++
    closeGrade()
    closeReturn()
    loadTasks()
  },
)
</script>
<style scoped>
.task-notice,
.submission-notice {
  margin-bottom: 12px;
}
.submission-pagination {
  justify-content: flex-end;
  padding: 12px 16px;
}
.attachment-list {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
}
.attachment-link {
  color: var(--accent);
  text-decoration: underline;
  text-underline-offset: 2px;
}
.task-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.task-item {
  padding: 12px 14px;
  border: 1px solid var(--line);
  border-radius: var(--r-l3);
  cursor: pointer;
  transition: all 0.15s;
}
.task-item:hover {
  border-color: var(--line-strong);
}
.task-item.is-active {
  border-color: var(--accent);
  background: var(--accent-softer);
}

/* ====== 提取的内联样式 ====== */
.task-title {
  font-size: 13px;
}
.split-main .l1-card {
  margin-bottom: 0;
}
.cell-ellipsis {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tbl .row-ops {
  opacity: 1;
}
.row-ops .op-link {
  border: 0;
  background: transparent;
  font-family: inherit;
}
.row-ops .op-link:disabled {
  cursor: wait;
  opacity: 0.55;
}
.l3-block {
  background: var(--surface);
  border: 1px dashed var(--line-2);
  border-radius: 8px;
  padding: 12px 14px;
  font-size: 12.5px;
  color: var(--ink-2);
  line-height: 1.8;
  margin-bottom: 14px;
}
.btn-warn {
  background: var(--warn);
  box-shadow: 0 2px 8px rgba(181, 121, 8, 0.32);
}
</style>
