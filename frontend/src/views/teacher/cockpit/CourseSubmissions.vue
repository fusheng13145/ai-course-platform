<template>
  <div>
    <PageHeader compact title="作业提交" subtitle="按任务查看提交记录并进行批改" icon="clipboard" />
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
            <div v-for="t in tasks" :key="t.id" class="task-item" :class="{ 'is-active': t.id === selectedTaskId }" @click="selectTask(t.id)">
              <div class="flex between mb-8"><span class="strong task-title">{{ t.title }}</span><span class="tag" :class="t.taskType === '作业' ? 'green' : 'blue'">{{ t.taskType }}</span></div>
              <div class="flex between small muted"><span class="tag" :class="t.isPublished ? 'green' : ''">{{ t.isPublished ? '已发布' : '草稿' }}</span><span v-if="t.dueDate">截止 {{ t.dueDate }}</span></div>
            </div>
          </div>
          <div v-else class="empty-state"><div class="e-title">暂无任务</div></div>
        </div>
      </aside>

      <!-- 右侧提交列表 + 批改 -->
      <div class="split-main">
        <section class="l1-card">
          <div class="l1-head">
            <span class="l1-ico"><svg viewBox="0 0 24 24" width="17" height="17" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round"><path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/></svg></span>
            <div><div class="l1-title">提交记录</div><div v-if="selectedTask" class="l1-sub">任务：{{ selectedTask.title }}</div></div>
          </div>
          <div class="l1-body flush">
            <div v-if="submissions.length" class="tbl-wrap">
              <table class="tbl">
                <thead><tr><th>学生</th><th>状态</th><th>分数</th><th>提交时间</th><th>内容</th><th></th></tr></thead>
                <tbody>
                  <tr v-for="row in submissions" :key="row.id">
                    <td class="cell-main">{{ row.studentName }}</td>
                    <td><span class="tag" :class="row.status === 'GRADED' ? 'green' : 'amber'">{{ row.status === 'GRADED' ? '已批改' : '待批改' }}</span></td>
                    <td class="num strong">{{ row.score != null ? row.score : '—' }}</td>
                    <td class="muted small">{{ row.submittedAt || '—' }}</td>
                    <td class="small muted cell-ellipsis">{{ row.content || (row.fileUrls?.length ? '附件 ' + row.fileUrls.length + ' 个' : '—') }}</td>
                    <td>
                      <div class="row-ops">
                        <span class="op-link" @click="openGrade(row)">批改</span>
                        <span class="op-link" @click="aiGrade(row)">AI 评分</span>
                        <span class="op-link danger" @click="openReturn(row)">退回</span>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
            <el-empty v-else description="暂无提交记录" :image-size="72" />
          </div>
        </section>
      </div>
    </div>

    <!-- 批改弹窗（内嵌打分 + 评语） -->
    <Modal v-if="gradeVisible" narrow @close="gradeVisible = false; resetGrade()">
      <template #title>批改作业</template>
      <template #sub>学生：<b>{{ gradingRow?.studentName }}</b></template>
      <div>
        <div class="l3-block">
          <div class="strong small mb-8">{{ gradingRow?.studentName }} 的提交内容</div>
          <span v-if="gradingRow?.content">{{ gradingRow?.content }}</span>
          <span v-else-if="gradingRow?.fileUrls?.length" class="tag red">附件 {{ gradingRow?.fileUrls.length }} 个</span>
          <span v-else class="muted">无内容</span>
        </div>
        <div class="form-grid">
          <div class="field"><label>评分（0-100）</label><input v-model="gradeForm.score" class="input" type="number" :min="0" :max="100" /></div>
          <div class="field"><label>等级</label><select v-model="gradeForm.level" class="select"><option>优秀</option><option>良好</option><option>合格</option><option>待改进</option></select></div>
          <div class="field span-2"><label>评语</label><textarea v-model="gradeForm.feedback" class="textarea" placeholder="指出亮点与错误之处…" :rows="3"></textarea></div>
        </div>
      </div>
      <template #footer>
        <button class="btn btn-plain" @click="gradeVisible = false">取消</button>
        <button class="btn btn-primary" :disabled="saving" @click="saveGrade">提交批改</button>
      </template>
    </Modal>

    <!-- 退回弹窗 -->
    <Modal v-if="returnVisible" narrow @close="returnVisible = false; resetReturn()">
      <template #title>退回重做</template>
      <div class="form-grid">
        <div class="field span-2"><label>退回原因</label><textarea v-model="returnForm.reason" class="textarea" placeholder="请说明退回原因" :rows="3"></textarea></div>
      </div>
      <template #footer>
        <button class="btn btn-plain" @click="returnVisible = false">取消</button>
        <button class="btn btn-primary btn-warn" :disabled="saving" @click="saveReturn">确认退回</button>
      </template>
    </Modal>
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
import Modal from '@/components/ui/Modal.vue'
import PageHeader from '@/components/PageHeader.vue'

const route = useRoute()
const courseId = route.params.id

const loading = ref(false)
const saving = ref(false)

const tasks = ref([])
const selectedTaskId = ref(null)
const selectedTask = computed(() => tasks.value.find((t) => t.id === selectedTaskId.value) || null)

const submissions = ref([])

const gradeVisible = ref(false)
const gradingRow = ref(null)
const gradeForm = reactive({ score: 0, feedback: '', level: '良好' })

const returnVisible = ref(false)
const returnRow = ref(null)
const returnForm = reactive({ reason: '' })

async function loadTasks() {
  loading.value = true
  try {
    const r = await taskApi.listByCourse(courseId)
    const body = unwrap(r)
    tasks.value = body?.records || body?.list || body || []
    if (tasks.value.length && !selectedTaskId.value) selectTask(tasks.value[0].id)
    else if (selectedTaskId.value) loadSubmissions()
  } catch (e) { logger.error('加载任务列表失败', e) } finally { loading.value = false }
}

function selectTask(id) { selectedTaskId.value = id; loadSubmissions() }

async function loadSubmissions() {
  if (!selectedTaskId.value) return
  try {
    const r = await submissionApi.getTaskSubmissions(selectedTaskId.value, { page: 1, size: 50 })
    const body = unwrap(r)
    submissions.value = body?.records || body?.list || body || []
  } catch (e) { logger.error('加载提交记录失败', e) }
}

function resetGrade() { gradingRow.value = null; gradeForm.score = 0; gradeForm.feedback = ''; gradeForm.level = '良好' }
function openGrade(row) { gradingRow.value = row; gradeForm.score = row.score != null ? Number(row.score) : 0; gradeForm.feedback = row.feedback || ''; gradeVisible.value = true }

async function saveGrade() {
  if (gradeForm.score == null) { ElMessage.warning('请填写分数'); return }
  saving.value = true
  try {
    await submissionApi.gradeSubmission(gradingRow.value.id, { score: gradeForm.score, feedback: gradeForm.feedback })
    ElMessage.success('批改已提交')
    gradeVisible.value = false
    await loadSubmissions()
  } catch (e) { logger.error('批改失败', e) } finally { saving.value = false }
}

async function aiGrade(row) {
  row._ai = true
  try { await submissionApi.aiGrade(row.id); ElMessage.success('AI 评分完成'); await loadSubmissions() }
  catch (e) { logger.error('AI 评分失败', e) } finally { row._ai = false }
}

function resetReturn() { returnRow.value = null; returnForm.reason = '' }
function openReturn(row) { returnRow.value = row; returnForm.reason = ''; returnVisible.value = true }
async function saveReturn() {
  saving.value = true
  try { await submissionApi.returnSubmission(returnRow.value?.id, { reason: returnForm.reason }); ElMessage.success('已退回'); returnVisible.value = false; await loadSubmissions() }
  catch (e) { logger.error('退回失败', e) } finally { saving.value = false }
}

onMounted(loadTasks)
watch(() => route.params.id, () => { selectedTaskId.value = null; loadTasks() })
</script>
<style scoped>
.task-list { display: flex; flex-direction: column; gap: 8px; }
.task-item { padding: 12px 14px; border: 1px solid var(--line); border-radius: var(--r-l3); cursor: pointer; transition: all .15s; }
.task-item:hover { border-color: var(--line-strong); }
.task-item.is-active { border-color: var(--accent); background: var(--accent-softer); }

/* ====== 提取的内联样式 ====== */
.task-title { font-size: 13px; }
.split-main .l1-card { margin-bottom: 0; }
.cell-ellipsis { max-width: 160px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
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
