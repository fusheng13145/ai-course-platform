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
            <!-- 状态筛选 chips（待批阅 = SUBMITTED + RESUBMITTED） -->
            <div v-if="submissions.length" class="filter-chips">
              <button
                v-for="opt in SUBMISSION_FILTER_OPTIONS"
                :key="opt.value"
                type="button"
                class="chip"
                :class="{ 'is-active': statusFilter === opt.value }"
                @click="statusFilter = opt.value"
              >
                {{ opt.label }}
                <span v-if="opt.value === 'PENDING' && pendingCount" class="chip-badge">{{
                  pendingCount
                }}</span>
              </button>
            </div>
            <div v-if="filteredSubmissions.length" class="tbl-wrap">
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
                  <tr v-for="row in filteredSubmissions" :key="row.id">
                    <td class="cell-main">
                      {{ row.studentName || row.studentNo || `学生 #${row.studentId ?? row.id}` }}
                    </td>
                    <td>
                      <span class="tag" :class="submissionStatusView(row.status).tagClass">{{
                        submissionStatusView(row.status).label
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
                          :disabled="gradeModal.visible"
                          @click="openGrade(row)"
                        >
                          批改
                        </button>
                        <button
                          type="button"
                          class="op-link danger"
                          :disabled="returnModal.visible"
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
              v-else-if="!submissionsLoading && selectedTask && submissions.length"
              title="当前筛选下暂无提交记录"
              icon="list"
              size="compact"
            />
            <EmptyState
              v-else-if="!submissionsLoading && selectedTask && !submissions.length"
              title="暂无提交记录"
              icon="list"
            />
            <EmptyState v-else-if="!selectedTask" title="请选择任务查看提交记录" icon="list" />
          </div>
        </section>
      </div>
    </div>

    <!-- 批改弹窗（内嵌打分 + 评语 + AI 预批建议区） -->
    <Modal v-if="gradeModal.visible" narrow @close="closeAll">
      <template #title>批改作业</template>
      <template #sub
        >学生：<b>{{ gradeModal.row?.studentName }}</b></template
      >
      <div>
        <div class="l3-block">
          <div class="strong small mb-8">{{ gradeModal.row?.studentName }} 的提交内容</div>
          <span v-if="gradeModal.row?.content">{{ gradeModal.row?.content }}</span>
          <div v-if="gradeModal.row?.fileUrls?.length" class="attachment-list">
            <span class="muted">附件：</span>
            <template
              v-for="(fileUrl, index) in gradeModal.row.fileUrls"
              :key="`${fileUrl}-${index}`"
            >
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
          <span v-if="!gradeModal.row?.content && !gradeModal.row?.fileUrls?.length" class="muted"
            >无内容</span
          >
        </div>

        <!-- AI 预批建议区：预批 → 建议展示 → 采纳回填 → 教师确认提交（不改分，最终走 /grade） -->
        <div class="ai-block">
          <div class="ai-head">
            <span class="strong small">AI 预批建议</span>
            <button
              type="button"
              class="op-link"
              :disabled="gradeModal.aiLoading || !gradeRowHasContent"
              @click="runAiPreview"
            >
              {{ gradeModal.aiLoading ? 'AI 预批中…' : 'AI 预批' }}
            </button>
          </div>
          <div v-if="!gradeRowHasContent" class="small muted ai-empty">
            该提交无文本或附件内容，无法进行 AI 预批
          </div>
          <div v-else-if="gradeModal.aiResult" class="ai-result">
            <div class="ai-line">
              <span class="muted small">建议分：</span>
              <b>{{ gradeModal.aiResult.suggestedScore ?? '—' }}</b>
              <span class="muted small">/ {{ gradeModal.maxScore }}</span>
            </div>
            <div v-if="gradeModal.aiResult.highlights" class="ai-line small">
              <span class="muted">亮点：</span>{{ gradeModal.aiResult.highlights }}
            </div>
            <div v-if="gradeModal.aiResult.suggestions" class="ai-line small">
              <span class="muted">改进：</span>{{ gradeModal.aiResult.suggestions }}
            </div>
            <div v-if="gradeModal.aiResult.feedback" class="ai-line small">
              <span class="muted">AI 评语：</span>{{ gradeModal.aiResult.feedback }}
            </div>
            <button type="button" class="btn btn-plain btn-sm" @click="applyAiSuggestion">
              采纳建议（回填后可修改）
            </button>
          </div>
          <div v-else class="small muted ai-empty">
            点击「AI 预批」获取建议分与评语，采纳后仍需人工确认提交
          </div>
        </div>

        <div class="form-grid">
          <div class="field">
            <label>评分（0-{{ gradeModal.maxScore }}）</label>
            <input
              v-model.number="gradeModal.form.score"
              class="input"
              type="number"
              min="0"
              :max="gradeModal.maxScore"
              step="0.1"
            />
          </div>
          <div class="field span-2">
            <label>评语</label
            ><textarea
              v-model="gradeModal.form.feedback"
              class="textarea"
              placeholder="指出亮点与错误之处…"
              maxlength="5000"
              :rows="3"
            ></textarea>
          </div>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-plain" @click="closeAll">取消</button>
        <button class="btn btn-primary" :disabled="!gradeModal.row" @click="submitGrade">
          提交批改
        </button>
      </template>
    </Modal>

    <!-- 退回弹窗 -->
    <Modal v-if="returnModal.visible" narrow @close="closeAll">
      <template #title>退回重做</template>
      <div class="form-grid">
        <div class="field span-2">
          <label>退回原因</label
          ><textarea
            v-model="returnModal.form.reason"
            class="textarea"
            placeholder="请说明退回原因"
            maxlength="1000"
            :rows="3"
          ></textarea>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-plain" @click="closeAll">取消</button>
        <button class="btn btn-primary btn-warn" @click="submitReturn">确认退回</button>
      </template>
    </Modal>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { pickList, readTotal, unwrap } from '@/utils/response'
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import taskApi from '@/api/task'
import submissionApi from '@/api/submission'
import {
  useSubmissionGrading,
  SUBMISSION_FILTER_OPTIONS,
  submissionStatusView,
} from '@/composables/useSubmissionGrading'
import Modal from '@/components/ui/Modal.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import PageHeader from '@/components/PageHeader.vue'

const route = useRoute()
const courseId = computed(() => Number(route.params.id))

const loading = ref(false)
const submissionsLoading = ref(false)
const taskNotice = ref('')
const submissionNotice = ref('')

const tasks = ref([])
const selectedTaskId = ref(null)
const selectedTask = computed(() => tasks.value.find((t) => t.id === selectedTaskId.value) || null)

const submissions = ref([])
const submissionPage = ref(1)
const submissionPageSize = ref(20)
const submissionTotal = ref(0)

// 批阅域逻辑（状态筛选 / 批改流 / 退回流）收敛到 useSubmissionGrading
const {
  statusFilter,
  filteredSubmissions,
  pendingCount,
  gradeModal,
  gradeRowHasContent,
  returnModal,
  openGrade,
  runAiPreview,
  applyAiSuggestion,
  submitGrade,
  openReturn,
  submitReturn,
  closeAll,
} = useSubmissionGrading({
  courseId,
  submissions,
  onGraded: loadSubmissions,
})

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
    const payload = unwrap(
      await submissionApi.getTaskSubmissions(taskId, {
        page: submissionPage.value,
        size: submissionPageSize.value,
      }),
    )
    if (sequence !== submissionLoadSequence || taskId !== selectedTaskId.value) return
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
    statusFilter.value = 'ALL'
    submissionLoadSequence++
    closeAll()
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

/* ====== 状态筛选 chips ====== */
.filter-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 12px 16px 0;
}
.chip {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  border: 1px solid var(--line);
  background: var(--surface);
  color: var(--ink-2);
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 12.5px;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.15s;
}
.chip:hover {
  border-color: var(--line-strong);
}
.chip.is-active {
  border-color: var(--accent);
  color: var(--accent);
  background: var(--accent-softer);
}
.chip-badge {
  min-width: 18px;
  text-align: center;
  border-radius: 999px;
  background: var(--warn, #b57908);
  color: #fff;
  font-size: 11px;
  padding: 0 5px;
  line-height: 18px;
}

/* ====== AI 预批建议区 ====== */
.ai-block {
  border: 1px solid var(--line-2);
  border-radius: 8px;
  padding: 10px 12px;
  margin-bottom: 14px;
}
.ai-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.ai-empty {
  line-height: 1.7;
}
.ai-result {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.ai-line {
  line-height: 1.7;
}
.ai-block .btn-sm {
  align-self: flex-start;
  margin-top: 8px;
  font-size: 12.5px;
  padding: 4px 12px;
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
