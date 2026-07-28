<template>
  <div class="course-submissions fade-in">
    <div v-loading="loading">
      <div class="section-bar">
        <div class="chart-title">
          <el-icon><Document /></el-icon> 作业批改
          <el-tag size="small" type="info" effect="light" round>{{ tasks.length }} 个任务</el-tag>
        </div>
      </div>

      <div class="layout">
        <!-- 左侧任务选择 -->
        <div class="task-pane chart-card">
          <div class="pane-title">任务列表</div>
          <div v-if="tasks.length" class="task-list">
            <div
              v-for="t in tasks"
              :key="t.id"
              class="task-item"
              :class="{ 'is-active': t.id === selectedTaskId }"
              @click="selectTask(t.id)"
            >
              <div class="task-item-head">
                <span class="task-item-title">{{ t.title }}</span>
                <el-tag size="small" :type="taskTypeTag(t.taskType)" effect="light">{{
                  t.taskType
                }}</el-tag>
              </div>
              <div class="task-item-meta">
                <el-tag size="small" :type="t.isPublished ? 'success' : 'info'" effect="light">
                  {{ t.isPublished ? '已发布' : '草稿' }}
                </el-tag>
                <span v-if="t.dueDate" class="due">截止 {{ t.dueDate }}</span>
              </div>
            </div>
          </div>
          <el-empty v-else description="暂无任务" :image-size="64" />
        </div>

        <!-- 右侧提交列表 -->
        <div class="sub-pane chart-card">
          <div class="pane-title">
            提交记录
            <el-tag v-if="selectedTask" size="small" type="primary" effect="light" round>{{
              selectedTask.title
            }}</el-tag>
          </div>

          <el-table
            v-loading="subLoading"
            :data="submissions"
            size="small"
            empty-text="该任务暂无提交"
            style="width: 100%"
          >
            <el-table-column prop="studentName" label="学生" min-width="100" />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag
                  size="small"
                  :type="row.status === 'GRADED' ? 'success' : 'warning'"
                  effect="light"
                >
                  {{ row.status === 'GRADED' ? '已批改' : '待批改' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="分数" width="80">
              <template #default="{ row }">{{ row.score != null ? row.score : '—' }}</template>
            </el-table-column>
            <el-table-column label="提交时间" min-width="140">
              <template #default="{ row }">{{ row.submittedAt || '—' }}</template>
            </el-table-column>
            <el-table-column label="内容预览" min-width="180" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.content">{{ row.content }}</span>
                <span v-else-if="row.fileUrls && row.fileUrls.length" class="file-tag"
                  >附件 {{ row.fileUrls.length }} 个</span
                >
                <span v-else class="muted">无</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="240" fixed="right">
              <template #default="{ row }">
                <el-button link type="primary" :icon="EditPen" @click="openGrade(row)"
                  >批改</el-button
                >
                <el-button
                  link
                  type="success"
                  :icon="MagicStick"
                  :loading="row._ai"
                  @click="aiGrade(row)"
                  >AI 评分</el-button
                >
                <el-button link type="warning" :icon="RefreshLeft" @click="openReturn(row)"
                  >退回</el-button
                >
              </template>
            </el-table-column>
          </el-table>

          <div class="pager">
            <el-pagination
              v-model:current-page="subPage"
              v-model:page-size="subSize"
              :total="subTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              background
              @current-change="loadSubmissions"
              @size-change="onSubSizeChange"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 批改对话框 -->
    <el-dialog v-model="gradeVisible" title="批改作业" width="460px" @closed="resetGrade">
      <div v-if="gradingRow" class="grade-target">
        学生：<b>{{ gradingRow.studentName }}</b>
      </div>
      <el-form :model="gradeForm" label-width="72px">
        <el-form-item label="分数">
          <el-input-number v-model="gradeForm.score" :min="0" :max="100" :precision="1" />
        </el-form-item>
        <el-form-item label="评语">
          <el-input
            v-model="gradeForm.feedback"
            type="textarea"
            :rows="4"
            placeholder="给学生的反馈（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="gradeVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveGrade">提交批改</el-button>
      </template>
    </el-dialog>

    <!-- 退回对话框 -->
    <el-dialog v-model="returnVisible" title="退回重做" width="440px" @closed="resetReturn">
      <el-form :model="returnForm" label-width="72px">
        <el-form-item label="退回原因">
          <el-input
            v-model="returnForm.reason"
            type="textarea"
            :rows="4"
            placeholder="请说明退回原因（可选）"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="returnVisible = false">取消</el-button>
        <el-button type="warning" :loading="saving" @click="saveReturn">确认退回</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import taskApi from '@/api/task'
import submissionApi from '@/api/submission'
import { EditPen, MagicStick, RefreshLeft, Document } from '@element-plus/icons-vue'

const route = useRoute()
const courseId = route.params.id

const loading = ref(false)
const subLoading = ref(false)
const saving = ref(false)

const tasks = ref([])
const selectedTaskId = ref(null)
const selectedTask = computed(() => tasks.value.find((t) => t.id === selectedTaskId.value) || null)

const submissions = ref([])
const subPage = ref(1)
const subSize = ref(10)
const subTotal = ref(0)

const gradeVisible = ref(false)
const gradingRow = ref(null)
const gradeForm = reactive({ score: 0, feedback: '' })

const returnVisible = ref(false)
const returnRow = ref(null)
const returnForm = reactive({ reason: '' })

function taskTypeTag(type) {
  return (
    { 视频: 'info', 测验: 'warning', 作业: 'success', 文档: 'primary', 讨论: 'danger' }[type] ||
    'info'
  )
}

async function loadTasks() {
  loading.value = true
  try {
    const r = await taskApi.listByCourse(courseId)
    const body = unwrap(r)
    tasks.value = body?.records || body?.list || body || []
    if (tasks.value.length && !selectedTaskId.value) {
      selectTask(tasks.value[0].id)
    } else if (selectedTaskId.value) {
      loadSubmissions()
    }
  } catch (e) {
    logger.error('加载任务列表失败', e)
  } finally {
    loading.value = false
  }
}

function selectTask(id) {
  selectedTaskId.value = id
  subPage.value = 1
  loadSubmissions()
}

async function loadSubmissions() {
  if (!selectedTaskId.value) return
  subLoading.value = true
  try {
    const r = await submissionApi.getTaskSubmissions(selectedTaskId.value, {
      page: subPage.value,
      size: subSize.value,
    })
    const body = unwrap(r)
    submissions.value = body?.records || body?.list || body || []
    subTotal.value = body?.total ?? submissions.value.length
  } catch (e) {
    logger.error('加载提交记录失败', e)
  } finally {
    subLoading.value = false
  }
}
function onSubSizeChange(s) {
  subSize.value = s
  subPage.value = 1
  loadSubmissions()
}

function resetGrade() {
  gradingRow.value = null
  gradeForm.score = 0
  gradeForm.feedback = ''
}
function openGrade(row) {
  gradingRow.value = row
  gradeForm.score = row.score != null ? Number(row.score) : 0
  gradeForm.feedback = row.feedback || ''
  gradeVisible.value = true
}
async function saveGrade() {
  if (gradeForm.score == null) {
    ElMessage.warning('请填写分数')
    return
  }
  saving.value = true
  try {
    await submissionApi.gradeSubmission(gradingRow.value.id, {
      score: gradeForm.score,
      feedback: gradeForm.feedback,
    })
    ElMessage.success('批改已提交')
    gradeVisible.value = false
    await loadSubmissions()
  } catch (e) {
    logger.error('批改失败', e)
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
  saving.value = true
  try {
    await submissionApi.returnSubmission(returnRow.value.id, { reason: returnForm.reason })
    ElMessage.success('已退回')
    returnVisible.value = false
    await loadSubmissions()
  } catch (e) {
    logger.error('退回失败', e)
  } finally {
    saving.value = false
  }
}

onMounted(loadTasks)
watch(
  () => route.params.id,
  () => {
    selectedTaskId.value = null
    loadTasks()
  },
)
</script>

<style scoped>
.section-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--ac-spacing-md);
}
.chart-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 15px;
  font-weight: 600;
  color: var(--ac-text-primary);
}
.chart-title .el-tag {
  margin-left: 2px;
}

.layout {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: var(--ac-spacing-md);
  align-items: start;
}
.chart-card {
  padding: var(--ac-spacing-lg);
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
}

.pane-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--ac-text-primary);
  margin-bottom: var(--ac-spacing-md);
}

.task-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.task-item {
  padding: 12px 14px;
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-md);
  background: var(--ac-surface-2);
  cursor: pointer;
  transition:
    border-color var(--ac-transition-fast),
    background var(--ac-transition-fast);
}
.task-item:hover {
  border-color: var(--ac-border-strong);
}
.task-item.is-active {
  border-color: var(--ac-primary);
  background: var(--ac-primary-bg);
}
.task-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 8px;
}
.task-item-title {
  font-weight: 600;
  color: var(--ac-text-primary);
  font-size: 13.5px;
}
.task-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}
.due {
  font-size: 11.5px;
  color: var(--ac-text-muted);
}

.file-tag {
  font-size: 12px;
  color: var(--ac-info);
}
.muted {
  color: var(--ac-text-muted);
}

.pager {
  display: flex;
  justify-content: flex-end;
  margin-top: var(--ac-spacing-md);
}
.grade-target {
  font-size: 13px;
  color: var(--ac-text-secondary);
  margin-bottom: 12px;
}
.grade-target b {
  color: var(--ac-text-primary);
}

@media (max-width: 1100px) {
  .layout {
    grid-template-columns: 1fr;
  }
}
</style>
