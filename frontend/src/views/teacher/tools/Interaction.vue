<template>
  <div class="interaction-page fade-in">
    <!-- 顶部课程选择器 -->
    <div class="page-head">
      <div class="page-title">
        <el-icon :size="20" class="ttl-icon"><ChatLineSquare /></el-icon>
        <div>
          <h3>课堂互动</h3>
          <p>发起签到与投票，实时掌握课堂参与情况</p>
        </div>
      </div>
      <el-select
        v-model="courseId"
        placeholder="请选择课程"
        filterable
        clearable
        style="width: 240px"
        :loading="courseLoading"
        @change="onCourseChange"
      >
        <el-option v-for="c in courseOptions" :key="c.id" :label="c.name" :value="c.id" />
      </el-select>
    </div>

    <div v-loading="loading">
      <el-alert v-if="!courseId" title="请选择课程" type="info" :closable="false" show-icon>
        <template #default>选择一门课程后，即可发起签到与投票互动。</template>
      </el-alert>

      <template v-else>
        <el-tabs v-model="activeTab" class="inter-tabs">
          <!-- 签到 -->
          <el-tab-pane label="签到" name="sign">
            <div class="toolbar">
              <el-button type="primary" @click="openSignInDialog">
                <el-icon><Plus /></el-icon>发起签到
              </el-button>
              <el-button :disabled="signLoading" @click="loadSignInList">
                <el-icon><Refresh /></el-icon>刷新
              </el-button>
            </div>

            <div v-loading="signLoading" class="chart-card">
              <div class="chart-title">
                <el-icon><Pointer /></el-icon>签到记录
                <el-tag size="small" type="info" effect="light" round>{{ signList.length }}</el-tag>
              </div>
              <el-table v-if="signList.length" :data="signList" size="small" empty-text="暂无签到">
                <el-table-column prop="title" label="标题" min-width="160" show-overflow-tooltip />
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag size="small" :type="signStatusType(row.status)" effect="light">{{
                      signStatusText(row.status)
                    }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="signedCount" label="已签" width="80" align="center" />
                <el-table-column prop="totalCount" label="应到" width="80" align="center" />
                <el-table-column label="操作" width="260" fixed="right">
                  <template #default="{ row }">
                    <el-button text type="primary" size="small" @click="openRecords(row)"
                      >记录</el-button
                    >
                    <el-button
                      text
                      type="success"
                      size="small"
                      :disabled="row.status !== 'DRAFT'"
                      @click="handlePublish(row)"
                      >发布</el-button
                    >
                    <el-button
                      text
                      type="warning"
                      size="small"
                      :disabled="row.status === 'CLOSED'"
                      @click="handleCloseSignIn(row)"
                      >关闭</el-button
                    >
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="暂无签到，点击「发起签到」" :image-size="70" />
            </div>
          </el-tab-pane>

          <!-- 投票 -->
          <el-tab-pane label="投票" name="vote">
            <div class="toolbar">
              <el-button type="primary" @click="openVoteDialog">
                <el-icon><Plus /></el-icon>发起投票
              </el-button>
              <el-button :disabled="voteLoading" @click="loadVoteList">
                <el-icon><Refresh /></el-icon>刷新
              </el-button>
            </div>

            <div v-loading="voteLoading" class="chart-card">
              <div class="chart-title">
                <el-icon><Histogram /></el-icon>投票列表
                <el-tag size="small" type="info" effect="light" round>{{ voteList.length }}</el-tag>
              </div>
              <el-table v-if="voteList.length" :data="voteList" size="small" empty-text="暂无投票">
                <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
                <el-table-column label="类型" width="90">
                  <template #default="{ row }">
                    <el-tag
                      size="small"
                      :type="row.multiple ? 'warning' : 'primary'"
                      effect="light"
                    >
                      {{ row.multiple ? '多选' : '单选' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag
                      size="small"
                      :type="row.status === 'CLOSED' ? 'info' : 'success'"
                      effect="light"
                    >
                      {{ row.status === 'CLOSED' ? '已结束' : '进行中' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="200" fixed="right">
                  <template #default="{ row }">
                    <el-button text type="primary" size="small" @click="openResult(row)"
                      >结果</el-button
                    >
                    <el-button
                      text
                      type="warning"
                      size="small"
                      :disabled="row.status === 'CLOSED'"
                      @click="handleCloseVote(row)"
                      >结束</el-button
                    >
                  </template>
                </el-table-column>
              </el-table>
              <el-empty v-else description="暂无投票，点击「发起投票」" :image-size="70" />
            </div>
          </el-tab-pane>
        </el-tabs>
      </template>
    </div>

    <!-- 发起签到 -->
    <el-dialog v-model="signDialog" title="发起签到" width="460px" append-to-body>
      <el-form :model="signForm" label-width="80px">
        <el-form-item label="签到标题" required>
          <el-input
            v-model="signForm.title"
            placeholder="如：第3章课堂签到"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="时长(秒)">
          <el-input-number v-model="signForm.duration" :min="30" :max="3600" :step="30" />
        </el-form-item>
        <el-form-item label="签到类型">
          <el-select v-model="signForm.type" style="width: 100%">
            <el-option label="普通签到" value="NORMAL" />
            <el-option label="二维码签到" value="QR" />
            <el-option label="定位签到" value="LOCATION" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="signDialog = false">取消</el-button>
        <el-button type="primary" :loading="signSubmitting" @click="submitSignIn"
          >发起并发布</el-button
        >
      </template>
    </el-dialog>

    <!-- 签到记录 -->
    <el-dialog v-model="recordsDialog" title="签到记录" width="640px" append-to-body>
      <div class="records-head">
        <span class="rh-title">{{ currentSign?.title }}</span>
        <el-button text type="primary" size="small" @click="openMarkForm">
          <el-icon><EditPen /></el-icon>手动标记
        </el-button>
      </div>
      <el-table
        v-loading="recordLoading"
        :data="recordList"
        size="small"
        max-height="320"
        empty-text="暂无记录"
      >
        <el-table-column prop="studentName" label="学生" min-width="120" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="recordStatusType(row.status)" effect="light">{{
              recordStatusText(row.status)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="signTime" label="签到时间" min-width="160" />
      </el-table>

      <div v-if="markVisible" class="mark-form">
        <el-divider>手动标记</el-divider>
        <el-form :inline="true" :model="markForm">
          <el-form-item label="学生">
            <el-select v-model="markForm.studentId" placeholder="选择学生" style="width: 160px">
              <el-option
                v-for="r in recordList"
                :key="r.studentId"
                :label="r.studentName"
                :value="r.studentId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="markForm.status" style="width: 130px">
              <el-option label="已签到" value="SIGNED" />
              <el-option label="迟到" value="LATE" />
              <el-option label="缺勤" value="ABSENT" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="markSubmitting" @click="submitMark">确认</el-button>
          </el-form-item>
        </el-form>
      </div>
    </el-dialog>

    <!-- 发起投票 -->
    <el-dialog v-model="voteDialog" title="发起投票" width="520px" append-to-body>
      <el-form :model="voteForm" label-width="80px">
        <el-form-item label="投票标题" required>
          <el-input
            v-model="voteForm.title"
            placeholder="如：你最感兴趣的方向"
            maxlength="50"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="选项" required>
          <div v-for="(opt, i) in voteForm.options" :key="i" class="opt-row">
            <el-input v-model="voteForm.options[i]" placeholder="选项内容" style="flex: 1" />
            <el-button
              text
              type="danger"
              :disabled="voteForm.options.length <= 2"
              @click="voteForm.options.splice(i, 1)"
            >
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
          <el-button text type="primary" @click="voteForm.options.push('')">
            <el-icon><Plus /></el-icon>添加选项
          </el-button>
        </el-form-item>
        <el-form-item label="多选">
          <el-switch v-model="voteForm.multiple" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="voteDialog = false">取消</el-button>
        <el-button type="primary" :loading="voteSubmitting" @click="submitVote">发起</el-button>
      </template>
    </el-dialog>

    <!-- 投票结果 -->
    <el-dialog v-model="resultDialog" title="投票结果" width="520px" append-to-body>
      <div v-if="voteResult" class="result-wrap">
        <div class="result-title">{{ voteResult.title }}</div>
        <div v-for="(item, i) in voteResult.options || []" :key="i" class="result-item">
          <div class="ri-head">
            <span>{{ item.optionText || item.text }}</span>
            <span class="ri-num">{{ item.count || 0 }} 票</span>
          </div>
          <div class="ri-bar">
            <div class="ri-fill" :style="{ width: resultPct(item.count) }"></div>
          </div>
        </div>
        <div class="result-total">总票数：{{ voteResult.total || 0 }}</div>
      </div>
      <el-empty v-else description="暂无结果" :image-size="60" />
    </el-dialog>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import courseApi from '@/api/course'
import interactionApi from '@/api/interaction'
import {
  ChatLineSquare,
  Plus,
  Refresh,
  Pointer,
  Histogram,
  EditPen,
  Delete,
} from '@element-plus/icons-vue'

const courseLoading = ref(false)
const courseOptions = ref([])
const courseId = ref('')
const loading = ref(false)
const activeTab = ref('sign')

/* ---------- 课程选择器 ---------- */
async function loadCourses() {
  courseLoading.value = true
  try {
    const r = await courseApi.getMyCourses()
    const body = unwrap(r)
    const list = body?.records || body?.list || body || []
    courseOptions.value = Array.isArray(list) ? list : []
    if (courseOptions.value.length && !courseId.value) courseId.value = courseOptions.value[0].id
  } catch (e) {
    logger.error('加载课程列表失败', e)
  } finally {
    courseLoading.value = false
  }
}

function onCourseChange() {
  signList.value = []
  voteList.value = []
  if (courseId.value) {
    loadSignInList()
    loadVoteList()
  }
}

/* ---------- 签到 ---------- */
const signLoading = ref(false)
const signList = ref([])
const signDialog = ref(false)
const signSubmitting = ref(false)
const signForm = ref({ title: '', duration: 300, type: 'NORMAL' })

async function loadSignInList() {
  if (!courseId.value) return
  signLoading.value = true
  try {
    const r = await interactionApi.getSignInList(courseId.value)
    const body = unwrap(r)
    const list = body?.records || body?.list || body || []
    signList.value = Array.isArray(list) ? list : []
  } catch (e) {
    logger.error('加载签到列表失败', e)
  } finally {
    signLoading.value = false
  }
}

function openSignInDialog() {
  signForm.value = { title: '', duration: 300, type: 'NORMAL' }
  signDialog.value = true
}

async function submitSignIn() {
  if (!signForm.value.title.trim()) return ElMessage.warning('请输入签到标题')
  if (!courseId.value) return ElMessage.warning('请先选择课程')
  signSubmitting.value = true
  try {
    const r = await interactionApi.createSignIn({
      courseId: courseId.value,
      title: signForm.value.title.trim(),
      duration: signForm.value.duration,
      type: signForm.value.type,
    })
    const obj = unwrap(r)
    const id = obj?.id
    if (id) await interactionApi.publishSignIn(id)
    ElMessage.success('签到已发起并发布')
    signDialog.value = false
    loadSignInList()
  } catch (e) {
    logger.error('发起签到失败', e)
  } finally {
    signSubmitting.value = false
  }
}

async function handlePublish(row) {
  try {
    await interactionApi.publishSignIn(row.id)
    ElMessage.success('已发布')
    loadSignInList()
  } catch (e) {
    logger.error('发布签到失败', e)
  }
}

async function handleCloseSignIn(row) {
  try {
    await ElMessageBox.confirm(`确认关闭签到「${row.title}」？`, '提示', { type: 'warning' })
    await interactionApi.closeSignIn(row.id)
    ElMessage.success('已关闭')
    loadSignInList()
  } catch (e) {
    if (e !== 'cancel' && e?.type !== 'cancel') logger.error('关闭签到失败', e)
  }
}

function signStatusType(s) {
  return s === 'PUBLISHED' ? 'success' : s === 'CLOSED' ? 'info' : 'warning'
}
function signStatusText(s) {
  return s === 'PUBLISHED' ? '进行中' : s === 'CLOSED' ? '已关闭' : '草稿'
}

/* 签到记录 */
const recordsDialog = ref(false)
const recordLoading = ref(false)
const recordList = ref([])
const currentSign = ref(null)
const markVisible = ref(false)
const markSubmitting = ref(false)
const markForm = ref({ studentId: '', status: 'SIGNED' })

async function openRecords(row) {
  currentSign.value = row
  markVisible.value = false
  recordsDialog.value = true
  recordLoading.value = true
  try {
    const r = await interactionApi.getSignInRecords(row.id)
    const body = unwrap(r)
    const list = body?.records || body?.list || body || []
    recordList.value = Array.isArray(list) ? list : []
  } catch (e) {
    logger.error('加载签到记录失败', e)
  } finally {
    recordLoading.value = false
  }
}

function openMarkForm() {
  markForm.value = { studentId: '', status: 'SIGNED' }
  markVisible.value = true
}

async function submitMark() {
  if (!markForm.value.studentId) return ElMessage.warning('请选择学生')
  if (!currentSign.value) return
  markSubmitting.value = true
  try {
    await interactionApi.markSignIn(currentSign.value.id, {
      studentId: markForm.value.studentId,
      status: markForm.value.status,
    })
    ElMessage.success('已标记')
    markVisible.value = false
    openRecords(currentSign.value)
  } catch (e) {
    logger.error('手动标记失败', e)
  } finally {
    markSubmitting.value = false
  }
}

function recordStatusType(s) {
  return s === 'SIGNED' ? 'success' : s === 'LATE' ? 'warning' : s === 'ABSENT' ? 'danger' : 'info'
}
function recordStatusText(s) {
  return s === 'SIGNED' ? '已签到' : s === 'LATE' ? '迟到' : s === 'ABSENT' ? '缺勤' : '未签'
}

/* ---------- 投票 ---------- */
const voteLoading = ref(false)
const voteList = ref([])
const voteDialog = ref(false)
const voteSubmitting = ref(false)
const voteForm = ref({ title: '', options: ['', ''], multiple: false })

async function loadVoteList() {
  if (!courseId.value) return
  voteLoading.value = true
  try {
    const r = await interactionApi.getVoteList(courseId.value)
    const body = unwrap(r)
    const list = body?.records || body?.list || body || []
    voteList.value = Array.isArray(list) ? list : []
  } catch (e) {
    logger.error('加载投票列表失败', e)
  } finally {
    voteLoading.value = false
  }
}

function openVoteDialog() {
  voteForm.value = { title: '', options: ['', ''], multiple: false }
  voteDialog.value = true
}

async function submitVote() {
  if (!voteForm.value.title.trim()) return ElMessage.warning('请输入投票标题')
  const opts = voteForm.value.options.map((o) => o.trim()).filter(Boolean)
  if (opts.length < 2) return ElMessage.warning('至少填写 2 个选项')
  if (!courseId.value) return ElMessage.warning('请先选择课程')
  voteSubmitting.value = true
  try {
    await interactionApi.createVote({
      courseId: courseId.value,
      title: voteForm.value.title.trim(),
      options: opts,
      multiple: voteForm.value.multiple,
    })
    ElMessage.success('投票已发起')
    voteDialog.value = false
    loadVoteList()
  } catch (e) {
    logger.error('发起投票失败', e)
  } finally {
    voteSubmitting.value = false
  }
}

async function handleCloseVote(row) {
  try {
    await ElMessageBox.confirm(`确认结束投票「${row.title}」？`, '提示', { type: 'warning' })
    await interactionApi.closeVote(row.id)
    ElMessage.success('已结束')
    loadVoteList()
  } catch (e) {
    if (e !== 'cancel' && e?.type !== 'cancel') logger.error('结束投票失败', e)
  }
}

/* 投票结果 */
const resultDialog = ref(false)
const voteResult = ref(null)
async function openResult(row) {
  resultDialog.value = true
  voteResult.value = null
  try {
    const r = await interactionApi.getVoteResult(row.id)
    const body = unwrap(r)
    voteResult.value = body?.result || body || null
  } catch (e) {
    logger.error('加载投票结果失败', e)
  }
}
function resultPct(count) {
  const total = voteResult.value?.total || 0
  if (!total) return '0%'
  return Math.min(100, Math.round(((Number(count) || 0) / total) * 100)) + '%'
}

onMounted(() => {
  loadCourses()
})
</script>

<style scoped>
.page-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: var(--ac-spacing-lg);
  flex-wrap: wrap;
}
.page-title {
  display: flex;
  align-items: center;
  gap: 12px;
}
.ttl-icon {
  color: var(--ac-primary);
}
.page-title h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 640;
  color: var(--ac-text-primary);
}
.page-title p {
  margin: 2px 0 0;
  font-size: 12.5px;
  color: var(--ac-text-muted);
}

.inter-tabs :deep(.el-tabs__header) {
  margin-bottom: var(--ac-spacing-md);
}
.toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: var(--ac-spacing-md);
}

.chart-card {
  padding: var(--ac-spacing-lg);
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  box-shadow: var(--ac-shadow-sm);
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

.opt-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.records-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.rh-title {
  font-weight: 600;
  color: var(--ac-text-primary);
}
.mark-form {
  margin-top: 4px;
}

.result-wrap {
  padding: 4px 0;
}
.result-title {
  font-size: 15px;
  font-weight: 620;
  color: var(--ac-text-primary);
  margin-bottom: 14px;
}
.result-item {
  margin-bottom: 14px;
}
.ri-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--ac-text-secondary);
  margin-bottom: 6px;
}
.ri-num {
  color: var(--ac-primary);
  font-weight: 600;
}
.ri-bar {
  height: 10px;
  border-radius: 999px;
  background: var(--ac-surface-2);
  overflow: hidden;
}
.ri-fill {
  height: 100%;
  background: linear-gradient(135deg, var(--ac-primary), var(--ac-primary-light));
  border-radius: 999px;
  transition: width 0.4s ease;
}
.result-total {
  margin-top: 6px;
  font-size: 12.5px;
  color: var(--ac-text-muted);
}

@media (max-width: 1100px) {
  .page-head {
    flex-direction: column;
    align-items: stretch;
  }
}
</style>
