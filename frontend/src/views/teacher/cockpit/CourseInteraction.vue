<template>
  <div class="course-interaction fade-in">
    <div v-loading="loading">
      <el-tabs v-model="activeTab" class="it-tabs">
        <!-- 签到 -->
        <el-tab-pane label="签到" name="signin">
          <div class="head-row">
            <span class="muted">课程签到记录 {{ signIns.length }} 条</span>
            <el-button type="primary" :icon="Plus" @click="openSignInDialog">发起签到</el-button>
          </div>
          <el-table :data="signIns" size="small" border empty-text="暂无签到">
            <el-table-column prop="title" label="标题" min-width="160" />
            <el-table-column label="状态" width="96" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="statusType(row.status)" effect="light">{{
                  statusText(row.status)
                }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="type" label="方式" width="90" align="center">
              <template #default="{ row }">{{ row.type || '普通' }}</template>
            </el-table-column>
            <el-table-column label="发起时间" min-width="150">
              <template #default="{ row }">{{ row.createTime || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" min-width="240" align="center">
              <template #default="{ row }">
                <el-button
                  link
                  type="success"
                  :icon="Promotion"
                  :disabled="row.status === 'PUBLISHED' || row.status === 'CLOSED'"
                  @click="publish(row)"
                  >发布</el-button
                >
                <el-button
                  link
                  type="warning"
                  :icon="SwitchButton"
                  :disabled="row.status === 'CLOSED'"
                  @click="closeSignIn(row)"
                  >结束</el-button
                >
                <el-button link type="primary" :icon="Document" @click="openRecords(row)"
                  >查看记录</el-button
                >
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- 投票 -->
        <el-tab-pane label="投票" name="vote">
          <div class="head-row">
            <span class="muted">课程投票记录 {{ votes.length }} 条</span>
            <el-button type="primary" :icon="Plus" @click="openVoteDialog">发起投票</el-button>
          </div>
          <el-table :data="votes" size="small" border empty-text="暂无投票">
            <el-table-column prop="title" label="标题" min-width="180" />
            <el-table-column label="类型" width="100" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="row.multiple ? 'warning' : 'info'" effect="light">{{
                  row.multiple ? '多选' : '单选'
                }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="96" align="center">
              <template #default="{ row }">
                <el-tag size="small" :type="statusType(row.status)" effect="light">{{
                  statusText(row.status)
                }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="发起时间" min-width="150">
              <template #default="{ row }">{{ row.createTime || '—' }}</template>
            </el-table-column>
            <el-table-column label="操作" min-width="200" align="center">
              <template #default="{ row }">
                <el-button
                  link
                  type="warning"
                  :icon="SwitchButton"
                  :disabled="row.status === 'CLOSED'"
                  @click="closeVote(row)"
                  >结束</el-button
                >
                <el-button link type="primary" :icon="DataAnalysis" @click="openVoteResult(row)"
                  >查看结果</el-button
                >
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 发起签到 -->
    <el-dialog v-model="signInVisible" title="发起签到" width="440px">
      <el-form :model="signInForm" label-width="84px">
        <el-form-item label="签到标题" required>
          <el-input v-model="signInForm.title" placeholder="如：第3章课堂签到" maxlength="40" />
        </el-form-item>
        <el-form-item label="时长(分钟)">
          <el-input-number v-model="signInForm.duration" :min="1" :max="120" :step="5" />
        </el-form-item>
        <el-form-item label="签到方式">
          <el-select v-model="signInForm.type" placeholder="选择方式" style="width: 100%">
            <el-option label="普通签到" value="NORMAL" />
            <el-option label="二维码签到" value="QR" />
            <el-option label="手势签到" value="GESTURE" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="signInVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createSignIn">发起</el-button>
      </template>
    </el-dialog>

    <!-- 签到记录 -->
    <el-dialog v-model="recordsVisible" title="签到记录" width="520px">
      <el-table :data="records" size="small" border max-height="420" empty-text="暂无记录">
        <el-table-column prop="studentName" label="学生" min-width="120" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="row.status === 'SIGNED' ? 'success' : 'info'"
              effect="light"
            >
              {{ row.status === 'SIGNED' ? '已签到' : row.status || '未签' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="时间" min-width="150">
          <template #default="{ row }">{{ row.time || '—' }}</template>
        </el-table-column>
        <el-table-column label="标记" width="100" align="center">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :icon="Check"
              :disabled="row.status === 'SIGNED'"
              @click="mark(row)"
              >标记到</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 发起投票 -->
    <el-dialog v-model="voteVisible" title="发起投票" width="480px">
      <el-form :model="voteForm" label-width="84px">
        <el-form-item label="投票标题" required>
          <el-input v-model="voteForm.title" placeholder="如：最喜欢的教学方式" maxlength="40" />
        </el-form-item>
        <el-form-item label="选项">
          <div class="opt-list">
            <div v-for="(o, i) in voteForm.options" :key="i" class="opt-row">
              <el-input v-model="voteForm.options[i]" placeholder="选项内容" />
              <el-button
                :icon="Delete"
                circle
                plain
                type="danger"
                @click="voteForm.options.splice(i, 1)"
              />
            </div>
            <el-button :icon="Plus" plain @click="voteForm.options.push('')">添加选项</el-button>
          </div>
        </el-form-item>
        <el-form-item label="是否多选">
          <el-switch v-model="voteForm.multiple" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="voteVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="createVote">发起</el-button>
      </template>
    </el-dialog>

    <!-- 投票结果 -->
    <el-dialog v-model="resultVisible" title="投票结果" width="480px">
      <div v-if="voteResult" class="result-wrap">
        <div class="result-total">总票数：{{ voteResult.total ?? 0 }}</div>
        <div v-for="(o, i) in voteResult.options" :key="i" class="result-item">
          <div class="result-head">
            <span>{{ o.text }}</span
            ><b>{{ o.count }} 票</b>
          </div>
          <el-progress
            :percentage="pctOf(o.count, voteResult.total)"
            :stroke-width="12"
            :show-text="false"
          />
        </div>
      </div>
      <el-empty v-else description="暂无结果" />
    </el-dialog>
  </div>
</template>

<script setup>
import logger from '@/utils/logger'
import { unwrap } from '@/utils/response'
import { ref, reactive, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import interactionApi from '@/api/interaction'
import {
  Plus,
  Promotion,
  SwitchButton,
  Document,
  DataAnalysis,
  Delete,
  Check,
} from '@element-plus/icons-vue'

const route = useRoute()
const courseId = route.params.id

const loading = ref(false)
const saving = ref(false)
const activeTab = ref('signin')

const signIns = ref([])
const votes = ref([])

/* 签到对话框 */
const signInVisible = ref(false)
const signInForm = reactive({ title: '', duration: 10, type: 'NORMAL' })

/* 签到记录 */
const recordsVisible = ref(false)
const records = ref([])
const currentSignInId = ref(null)

/* 投票对话框 */
const voteVisible = ref(false)
const voteForm = reactive({ title: '', options: [''], multiple: false })

/* 投票结果 */
const resultVisible = ref(false)
const voteResult = ref(null)

function statusType(s) {
  if (s === 'PUBLISHED' || s === 'OPEN' || s === 'ONGOING') return 'success'
  if (s === 'CLOSED') return 'info'
  return 'warning'
}
function statusText(s) {
  if (s === 'PUBLISHED' || s === 'OPEN' || s === 'ONGOING') return '进行中'
  if (s === 'CLOSED') return '已结束'
  return '草稿'
}
function pctOf(v, total) {
  const t = Number(total) || 0
  if (!t) return 0
  return Math.round(((Number(v) || 0) / t) * 100)
}

async function loadSignInList() {
  try {
    const r = await interactionApi.getSignInList(courseId)
    const body = unwrap(r)
    signIns.value = body?.records || body?.list || body || []
  } catch (e) {
    logger.error('加载签到列表失败', e)
  }
}
async function loadVoteList() {
  try {
    const r = await interactionApi.getVoteList(courseId)
    const body = unwrap(r)
    votes.value = body?.records || body?.list || body || []
  } catch (e) {
    logger.error('加载投票列表失败', e)
  }
}

function openSignInDialog() {
  signInForm.title = ''
  signInForm.duration = 10
  signInForm.type = 'NORMAL'
  signInVisible.value = true
}
async function createSignIn() {
  if (!signInForm.title.trim()) {
    ElMessage.warning('请填写签到标题')
    return
  }
  saving.value = true
  try {
    const payload = {
      courseId,
      title: signInForm.title.trim(),
      duration: signInForm.duration,
      type: signInForm.type,
    }
    await interactionApi.createSignIn(payload)
    ElMessage.success('签到已发起')
    signInVisible.value = false
    await loadSignInList()
  } catch (e) {
    logger.error('发起签到失败', e)
  } finally {
    saving.value = false
  }
}
async function publish(row) {
  try {
    await interactionApi.publishSignIn(row.id)
    ElMessage.success('已发布签到')
    await loadSignInList()
  } catch (e) {
    logger.error('发布签到失败', e)
  }
}
async function closeSignIn(row) {
  try {
    await ElMessageBox.confirm('确定结束该签到？', '提示', { type: 'warning' })
    await interactionApi.closeSignIn(row.id)
    ElMessage.success('签到已结束')
    await loadSignInList()
  } catch (e) {
    if (e !== 'cancel' && e?.toLowerCase?.() !== 'cancel') logger.error('结束签到失败', e)
  }
}
async function openRecords(row) {
  currentSignInId.value = row.id
  recordsVisible.value = true
  records.value = []
  try {
    const r = await interactionApi.getSignInRecords(row.id)
    const body = unwrap(r)
    records.value = body?.records || body?.list || body || []
  } catch (e) {
    logger.error('加载签到记录失败', e)
  }
}
async function mark(row) {
  if (!currentSignInId.value) return
  try {
    await interactionApi.markSignIn(currentSignInId.value, {
      studentId: row.studentId,
      status: 'SIGNED',
    })
    ElMessage.success('已标记签到')
    await openRecords({ id: currentSignInId.value })
  } catch (e) {
    logger.error('标记签到失败', e)
  }
}

function openVoteDialog() {
  voteForm.title = ''
  voteForm.options = ['']
  voteForm.multiple = false
  voteVisible.value = true
}
async function createVote() {
  if (!voteForm.title.trim()) {
    ElMessage.warning('请填写投票标题')
    return
  }
  const opts = voteForm.options.map((o) => o.trim()).filter(Boolean)
  if (opts.length < 2) {
    ElMessage.warning('请至少填写两个有效选项')
    return
  }
  saving.value = true
  try {
    const payload = {
      courseId,
      title: voteForm.title.trim(),
      options: opts,
      multiple: voteForm.multiple,
    }
    await interactionApi.createVote(payload)
    ElMessage.success('投票已发起')
    voteVisible.value = false
    await loadVoteList()
  } catch (e) {
    logger.error('发起投票失败', e)
  } finally {
    saving.value = false
  }
}
async function closeVote(row) {
  try {
    await ElMessageBox.confirm('确定结束该投票？', '提示', { type: 'warning' })
    await interactionApi.closeVote(row.id)
    ElMessage.success('投票已结束')
    await loadVoteList()
  } catch (e) {
    if (e !== 'cancel' && e?.toLowerCase?.() !== 'cancel') logger.error('结束投票失败', e)
  }
}
async function openVoteResult(row) {
  resultVisible.value = true
  voteResult.value = null
  try {
    const r = await interactionApi.getVoteResult(row.id)
    const body = unwrap(r)
    voteResult.value = body || { options: [], total: 0 }
  } catch (e) {
    logger.error('加载投票结果失败', e)
  }
}

async function load() {
  loading.value = true
  try {
    await Promise.all([loadSignInList(), loadVoteList()])
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => route.params.id, load)
</script>

<style scoped>
.it-tabs {
  background: var(--ac-bg-elevated);
  border: 1px solid var(--ac-border);
  border-radius: var(--ac-radius-lg);
  padding: var(--ac-spacing-md) var(--ac-spacing-lg);
  box-shadow: var(--ac-shadow-sm);
}
.head-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--ac-spacing-md);
}
.head-row .muted {
  font-size: 13px;
  color: var(--ac-text-muted);
}

.opt-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 100%;
}
.opt-row {
  display: flex;
  align-items: center;
  gap: 10px;
}
.opt-row .el-input {
  flex: 1;
}

.result-wrap {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.result-total {
  font-size: 13px;
  color: var(--ac-text-secondary);
}
.result-item {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
  color: var(--ac-text-primary);
}
.result-head b {
  color: var(--ac-primary);
}
</style>
